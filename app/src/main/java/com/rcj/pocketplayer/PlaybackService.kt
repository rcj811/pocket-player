package com.rcj.pocketplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONArray
import org.json.JSONObject

class PlaybackService : MediaSessionService() {

    companion object {
        const val CHANNEL_ID = "playing"
    }

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val main = Handler(Looper.getMainLooper())
    private val ytRetries = HashMap<String, Int>()
    private var consecutiveErrors = 0
    private var connectivity: ConnectivityManager? = null

    override fun onCreate() {
        super.onCreate()
        setupNotification()

        // 跑步不卡頓：音樂盡量整首先緩衝（最多 10 分鐘），但總量限制 40MB，
        // 避免 MP4 影片把記憶體吃光而閃退
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 600_000, 1_500, 3_000)
            .setTargetBufferBytes(40 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(MediaCache.playerFactory(this))
                    .setLoadErrorHandlingPolicy(PatientRetryPolicy())
            )
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true, // 運動 App 語音播報時自動降低音量
            )
            .setHandleAudioBecomingNoisy(true) // 拔耳機 / 藍牙斷線自動暫停
            .setWakeMode(C.WAKE_MODE_NETWORK)  // 關螢幕時保持 CPU 與 Wi-Fi
            .build()
        player.addListener(listener)

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(callback)
            .build()

        try {
            QueueStore.restore(this, player)
        } catch (e: Exception) {
            QueueStore.clear(this)
        }

        connectivity = getSystemService(ConnectivityManager::class.java)
        try {
            connectivity?.registerDefaultNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
    }

    /**
     * 通知頻道設為「預設」重要性（但無聲、不震動），
     * 這樣狀態列會一直顯示音符圖示，看得出隨身聽正在播放。
     * （Media3 預設是靜音通知，很多手機不會在狀態列顯示圖示）
     */
    private fun setupNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(CHANNEL_ID, "播放中", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "顯示正在播放的歌曲與控制按鈕"
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }
        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.channel_playing)
            .build()
        provider.setSmallIcon(R.drawable.ic_music)
        setMediaNotificationProvider(provider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 從最近使用滑掉 App：還在播就繼續，沒在播才結束
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        try {
            connectivity?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
        QueueStore.save(this, player)
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    // ---------------- 斷網自動續播 ----------------

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            main.postDelayed({
                if (player.playerError != null && player.playWhenReady && player.mediaItemCount > 0) {
                    consecutiveErrors = 0
                    player.prepare()
                }
            }, 1_000)
        }
    }

    // ---------------- 播放事件 ----------------

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                consecutiveErrors = 0
                player.currentMediaItem?.let { ytRetries.remove(it.mediaId) }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            QueueStore.save(this@PlaybackService, player)
            precacheUpcoming()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                QueueStore.save(this@PlaybackService, player)
                precacheUpcoming()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) QueueStore.save(this@PlaybackService, player)
        }

        override fun onPlayerError(error: PlaybackException) {
            val item = player.currentMediaItem ?: return
            val index = player.currentMediaItemIndex
            val position = player.currentPosition
            val ytId = Media.ytIdOf(item)

            // 1) 串流網址過期（403 等）：重新解析後從原位置繼續
            if (ytId != null && !isNetworkDown(error)) {
                val n = ytRetries[item.mediaId] ?: 0
                if (n < 2) {
                    ytRetries[item.mediaId] = n + 1
                    YouTube.invalidate(ytId)
                    player.seekTo(index, position)
                    player.prepare()
                    return
                }
            }

            // 2) 沒網路：等網路回來（networkCallback 會自動 prepare）
            if (isNetworkDown(error)) return

            // 3) 這首真的不能播：跳下一首（避免整個清單都壞時無限循環）
            consecutiveErrors++
            if (consecutiveErrors < player.mediaItemCount && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
            }
        }
    }

    private fun isNetworkDown(error: PlaybackException): Boolean {
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        ) return true
        val net = connectivity?.activeNetwork
        return net == null
    }

    /** 預先下載接下來 2 首到快取，就算跑到沒訊號的地方也不會斷。 */
    private fun precacheUpcoming() {
        // 延後到事件處理完再算，且任何錯誤都不能讓播放器崩潰
        main.post {
            try {
                val count = player.mediaItemCount
                val cur = player.currentMediaItemIndex
                if (count == 0 || cur !in 0 until count) return@post
                val ids = ArrayList<String>()
                Media.ytIdOf(player.getMediaItemAt(cur))?.let { ids += it }
                for (k in 1..2) {
                    val i = if (player.repeatMode == Player.REPEAT_MODE_ALL) (cur + k) % count else cur + k
                    if (i in 0 until count && i != cur) Media.ytIdOf(player.getMediaItemAt(i))?.let { ids += it }
                }
                if (ids.isNotEmpty()) MediaCache.precache(this, ids)
            } catch (_: Exception) {
            }
        }
    }

    // ---------------- 從畫面加入的歌曲 ----------------

    private val callback = object : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val fixed = mediaItems.map { m ->
                val uri = m.requestMetadata.mediaUri ?: m.localConfiguration?.uri
                if (uri == null) m
                else m.buildUpon()
                    .setUri(uri)
                    .setCustomCacheKey(if (m.mediaId.startsWith(Media.YT_PREFIX)) m.mediaId else null)
                    .build()
            }.toMutableList()
            return Futures.immediateFuture(fixed)
        }
    }
}

/** 網路斷線時不放棄：每 3 秒重試，直到網路恢復繼續播。 */
private class PatientRetryPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val e = loadErrorInfo.exception
        if (e is UnplayableException) return C.TIME_UNSET
        if (e is HttpDataSource.InvalidResponseCodeException && e.responseCode in 400..499) return C.TIME_UNSET
        if (e.cause is UnplayableException) return C.TIME_UNSET
        // 檔案格式錯誤等致命錯誤沿用預設判斷；其餘（斷網、逾時）一律慢慢重試
        if (super.getRetryDelayMsFor(loadErrorInfo) == C.TIME_UNSET) return C.TIME_UNSET
        return 3_000
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = Int.MAX_VALUE
}

/** 記住播放清單與位置，下次開 App 接著聽。 */
object QueueStore {
    private const val PREFS = "queue"

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    fun save(context: Context, player: Player) {
        val arr = JSONArray()
        for (i in 0 until player.mediaItemCount) {
            val m = player.getMediaItemAt(i)
            val uri = m.localConfiguration?.uri ?: m.requestMetadata.mediaUri ?: continue
            arr.put(
                JSONObject()
                    .put("id", m.mediaId)
                    .put("uri", uri.toString())
                    .put("t", m.mediaMetadata.title?.toString() ?: "")
                    .put("a", m.mediaMetadata.artist?.toString() ?: "")
                    .put("art", m.mediaMetadata.artworkUri?.toString() ?: "")
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("items", arr.toString())
            .putInt("index", player.currentMediaItemIndex)
            .putLong("pos", player.currentPosition)
            .putBoolean("shuffle", player.shuffleModeEnabled)
            .putInt("repeat", player.repeatMode)
            .apply()
    }

    fun restore(context: Context, player: Player) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = p.getString("items", null) ?: return
        val items = try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val art = o.optString("art").takeIf { it.isNotEmpty() }?.let(Uri::parse)
                Media.item(
                    id, Uri.parse(o.getString("uri")), o.optString("t"), o.optString("a"), art,
                    cacheKey = if (id.startsWith(Media.YT_PREFIX)) id else null,
                )
            }
        } catch (_: Exception) {
            return
        }
        if (items.isEmpty()) return
        val index = p.getInt("index", 0).coerceIn(0, items.size - 1)
        player.setMediaItems(items, index, p.getLong("pos", 0L))
        player.shuffleModeEnabled = p.getBoolean("shuffle", false)
        player.repeatMode = p.getInt("repeat", Player.REPEAT_MODE_OFF)
    }
}
