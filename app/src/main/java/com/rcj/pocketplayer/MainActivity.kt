package com.rcj.pocketplayer

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 畫面用的播放狀態（由 MediaController 同步過來）。 */
class PlayerState {
    var controller by mutableStateOf<MediaController?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var buffering by mutableStateOf(false); private set
    var meta by mutableStateOf(MediaMetadata.EMPTY); private set
    var queue by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var index by mutableIntStateOf(-1); private set
    var durationMs by mutableLongStateOf(0L); private set
    var shuffle by mutableStateOf(false); private set
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF); private set
    var hasVideo by mutableStateOf(false); private set
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var cacheTick by mutableIntStateOf(0)

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    fun attach(c: MediaController) {
        controller = c
        c.addListener(listener)
        refresh()
    }

    fun detach() {
        controller?.removeListener(listener)
        controller = null
    }

    fun refresh() {
        val c = controller ?: return
        isPlaying = c.isPlaying
        buffering = c.playbackState == Player.STATE_BUFFERING && c.playWhenReady
        meta = c.mediaMetadata
        index = c.currentMediaItemIndex
        queue = List(c.mediaItemCount) { c.getMediaItemAt(it) }
        durationMs = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        shuffle = c.shuffleModeEnabled
        repeat = c.repeatMode
        hasVideo = c.videoSize.width > 0 && c.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)
    }
}

enum class DurationFilter(val label: String, val range: LongRange?) {
    ANY("不限長度", null),
    SHORT("5 分內", 1L..300L),
    MID("5–15 分", 301L..900L),
    LONG("15 分以上・串燒", 901L..Long.MAX_VALUE),
}

data class Preset(val label: String, val query: String, val kind: YouTube.Kind, val duration: DurationFilter)

val runningPresets = listOf(
    Preset("🏃 跑步歌單", "跑步 音樂", YouTube.Kind.PLAYLISTS, DurationFilter.ANY),
    Preset("超慢跑 180 BPM", "180 BPM 超慢跑", YouTube.Kind.VIDEOS, DurationFilter.ANY),
    Preset("160 BPM", "160 BPM running music", YouTube.Kind.VIDEOS, DurationFilter.LONG),
    Preset("170 BPM", "170 BPM running music", YouTube.Kind.VIDEOS, DurationFilter.LONG),
    Preset("180 BPM", "180 BPM running music", YouTube.Kind.VIDEOS, DurationFilter.LONG),
    Preset("💪 Workout Mix", "workout motivation music mix", YouTube.Kind.VIDEOS, DurationFilter.LONG),
    Preset("華語熱門", "華語 熱門 歌曲", YouTube.Kind.SONGS, DurationFilter.ANY),
    Preset("動感 EDM", "EDM running", YouTube.Kind.SONGS, DurationFilter.ANY),
    Preset("Lo-fi 緩和", "lofi chill", YouTube.Kind.PLAYLISTS, DurationFilter.ANY),
)

class SearchState {
    var query by mutableStateOf("")
    var kind by mutableStateOf(YouTube.Kind.SONGS)
    var duration by mutableStateOf(DurationFilter.ANY)
    var results by mutableStateOf<List<YouTube.Result>>(emptyList())
    var loading by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var searched by mutableStateOf(false)
    var page: YouTube.SearchPage? = null

    /** 展開中的專輯 / 歌單 */
    var openList by mutableStateOf<YouTube.Result?>(null)
    var tracks by mutableStateOf<List<YouTube.Result>>(emptyList())
    var tracksLoading by mutableStateOf(false)

    /** 試聽中的歌（光棒） */
    var previewId by mutableStateOf<String?>(null)
    var previewLoading by mutableStateOf(false)

    fun visible(): List<YouTube.Result> {
        val r = duration.range ?: return results
        return results.filter { it.isPlaylist || it.durationSec <= 0 || it.durationSec in r }
    }
}

class MainActivity : ComponentActivity() {

    val player = PlayerState()
    val search = SearchState()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pendingIntent: Intent? = null
    private var searchJob: Job? = null

    /** 首次使用的「一鍵開啟」對話框 */
    var showWelcome by mutableStateOf(false)
    /** 有新版時的更新對話框 */
    var update by mutableStateOf<Updater.Release?>(null)

    // 通知權限要完再接著開省電白名單，一次點完
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        openBatterySettings()
    }

    val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) addLocal(uris, persist = true)
    }

    /** 選資料夾 → 掃描 → 詢問要不要加入 */
    var folderOffer by mutableStateOf<LocalScan.Offer?>(null)
    var scanning by mutableStateOf(false)
    val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) scanFolder(tree)
    }

    /** 一鍵清除確認框 */
    var confirmClear by mutableStateOf(false)
    /** 播放通知被關閉（狀態列不會有圖示） */
    var notifOff by mutableStateOf(false)

    /** 上次閃退的錯誤內容（顯示給使用者回報） */
    var crashLog by mutableStateOf<String?>(null)

    // 畫面在背景時（例如正在選檔案）先暫存，連上播放器後再加入，不會打斷正在播的歌
    private val pendingItems = ArrayList<MediaItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingIntent = intent
        showWelcome = !allPermissionsReady()
        App.crashFile(this).takeIf { it.exists() }?.let { f ->
            crashLog = try { f.readText() } catch (_: Exception) { "（無法讀取錯誤內容）" }
        }
        setContent { AppRoot(this) }
        checkForUpdate()
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationState()
        if (showWelcome && allPermissionsReady()) {
            showWelcome = false
            player.message = "背景播放已開啟 ✓ 關螢幕也會繼續播"
        }
    }

    // ---------------- 首次使用 / 權限 ----------------

    private fun needsNotificationPermission() = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun ignoringBattery() =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    fun allPermissionsReady() = !needsNotificationPermission() && ignoringBattery()

    /** 歡迎畫面的「一鍵開啟」：通知權限 → 背景播放（不受省電限制）。 */
    fun grantAll() {
        showWelcome = false
        if (needsNotificationPermission()) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else openBatterySettings()
    }

    // ---------------- 更新檢查 ----------------

    fun checkForUpdate(manual: Boolean = false) {
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { Updater.checkNewer(this@MainActivity) }
            if (r != null) update = r
            else if (manual) player.message = "已是最新版本 ${Updater.currentVersion(this@MainActivity)} ✓"
        }
    }

    val versionText: String by lazy { Updater.currentVersion(this) }

    fun startUpdate() {
        val r = update ?: return
        if (!Updater.ensureInstallPermission(this)) {
            player.message = "請允許「安裝不明應用程式」，再按一次更新"
            return
        }
        update = null
        Updater.downloadAndInstall(this, r)
        player.message = "下載新版中，完成後會自動開啟安裝畫面"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingIntent = intent
        handlePendingIntent()
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = try {
                future.get()
            } catch (e: Exception) {
                return@addListener
            }
            player.attach(c)
            if (pendingItems.isNotEmpty()) {
                val items = ArrayList(pendingItems)
                pendingItems.clear()
                appendItems(items)
            }
            handlePendingIntent()
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        stopPreview()
        player.detach()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    private fun handlePendingIntent() {
        if (player.controller == null) return
        val i = pendingIntent ?: return
        pendingIntent = null
        when (i.action) {
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)?.let { addFromText(it) }
            Intent.ACTION_VIEW -> i.data?.let { addLocal(listOf(it), persist = false) }
        }
    }

    // ---------------- 加入歌曲 ----------------

    fun addFromText(text: String) {
        when (val t = Links.parse(text)) {
            is Links.Target.YtVideo -> loadAndAdd("解析中…") { listOf(YouTube.loadVideo(t.id)) }
            is Links.Target.YtPlaylist -> loadAndAdd("讀取播放清單…") { YouTube.loadPlaylist(t.id) }
            is Links.Target.Direct -> {
                val name = Uri.parse(t.url).lastPathSegment ?: t.url
                appendItems(listOf(Media.item("url:${t.url}", Uri.parse(t.url), name, "網路串流", null)))
            }
            Links.Target.None -> player.message = "找不到可用的網址"
        }
    }

    // ---------------- 試聽（不加入清單） ----------------

    private var previewPlayer: ExoPlayer? = null

    private fun previewPlayer(): ExoPlayer = previewPlayer ?: ExoPlayer.Builder(this)
        .setMediaSourceFactory(DefaultMediaSourceFactory(MediaCache.playerFactory(this)))
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            true,
        )
        .build()
        .also { p ->
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    search.previewLoading = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_ENDED) search.previewId = null
                }

                override fun onPlayerError(error: PlaybackException) {
                    search.previewId = null
                    search.previewLoading = false
                    player.message = "這首無法試聽"
                }
            })
            previewPlayer = p
        }

    /** 點一下試聽，再點一下停止。 */
    fun togglePreview(r: YouTube.Result) {
        if (search.previewId == r.id) {
            stopPreview()
            return
        }
        player.controller?.pause()
        val p = previewPlayer()
        p.setMediaItem(Media.youtube(r.id, r.title, r.subtitle))
        p.prepare()
        p.play()
        search.previewId = r.id
        search.previewLoading = true
    }

    fun stopPreview() {
        previewPlayer?.run {
            stop()
            clearMediaItems()
        }
        search.previewId = null
        search.previewLoading = false
    }

    override fun onDestroy() {
        previewPlayer?.release()
        previewPlayer = null
        super.onDestroy()
    }

    // ---------------- 專輯 / 歌單展開 ----------------

    fun openList(r: YouTube.Result) {
        search.openList = r
        search.tracks = emptyList()
        search.tracksLoading = true
        lifecycleScope.launch {
            try {
                search.tracks = withContext(Dispatchers.IO) { YouTube.playlistTracks(r.id) }
            } catch (e: Exception) {
                player.message = "無法讀取：${e.message ?: e.javaClass.simpleName}"
            } finally {
                search.tracksLoading = false
            }
        }
    }

    fun closeList() {
        search.openList = null
        search.tracks = emptyList()
    }

    fun addAllTracks() {
        val list = search.tracks
        if (list.isEmpty()) return
        appendItems(list.map { Media.youtube(it.id, it.title, it.subtitle) })
    }

    fun addResult(r: YouTube.Result, playNow: Boolean = false) {
        if (r.isPlaylist) {
            loadAndAdd("讀取歌單…", playNow) { YouTube.loadPlaylist(r.id) }
        } else {
            val item = Media.youtube(r.id, r.title, r.subtitle)
            if (playNow) playNext(item) else {
                appendItems(listOf(item))
            }
        }
    }

    private fun loadAndAdd(busyText: String, playNow: Boolean = false, load: () -> List<MediaItem>) {
        if (player.busy) return
        player.busy = true
        player.message = busyText
        lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { load() }
                if (items.isEmpty()) {
                    player.message = "沒有可加入的歌曲"
                } else {
                    appendItems(items, forcePlay = playNow)
                }
            } catch (e: Exception) {
                player.message = "無法讀取：${e.message ?: e.javaClass.simpleName}"
            } finally {
                player.busy = false
            }
        }
    }

    /** 加到清單尾端；若目前沒在播放就直接從新加入的歌開始播。 */
    private fun appendItems(items: List<MediaItem>, forcePlay: Boolean = false) {
        val c = player.controller
        if (c == null) {
            pendingItems += items
            return
        }
        val start = c.mediaItemCount
        c.addMediaItems(items)
        if (forcePlay || !c.isPlaying) {
            c.seekTo(start, 0)
            c.prepare()
            c.play()
        }
        player.message = if (items.size == 1) "已加入：${items[0].mediaMetadata.title}" else "已加入 ${items.size} 首"
    }

    /** 插到目前這首後面並立刻播放。 */
    private fun playNext(item: MediaItem) {
        val c = player.controller ?: return
        val pos = if (c.mediaItemCount == 0) 0 else c.currentMediaItemIndex + 1
        c.addMediaItem(pos, item)
        c.seekTo(pos, 0)
        c.prepare()
        c.play()
        player.message = "正在播放：${item.mediaMetadata.title}"
    }

    private fun addLocal(uris: List<Uri>, persist: Boolean) {
        val items = uris.map { uri ->
            if (persist) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                }
            }
            val name = displayName(uri)
            val type = contentResolver.getType(uri) ?: ""
            Media.item(
                "local:$uri", uri,
                name.substringBeforeLast('.').ifBlank { name },
                if (type.startsWith("video")) "本機影片" else "本機音樂",
                null,
            )
        }
        appendItems(items)
    }

    private fun scanFolder(tree: Uri) {
        try {
            contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        scanning = true
        lifecycleScope.launch {
            try {
                val offer = withContext(Dispatchers.IO) { LocalScan.scan(this@MainActivity, tree) }
                if (offer.files.isEmpty()) player.message = "「${offer.folderName}」裡沒有找到音樂或影片"
                else folderOffer = offer
            } catch (e: Exception) {
                player.message = "無法讀取資料夾：${e.message ?: e.javaClass.simpleName}"
            } finally {
                scanning = false
            }
        }
    }

    fun acceptFolder(onlyAudio: Boolean) {
        val offer = folderOffer ?: return
        folderOffer = null
        val files = if (onlyAudio) offer.files.filter { !it.isVideo } else offer.files
        appendItems(files.map { f ->
            Media.item(
                "local:${f.uri}", f.uri,
                f.name.substringBeforeLast('.').ifBlank { f.name },
                if (f.isVideo) "本機影片・${offer.folderName}" else "本機音樂・${offer.folderName}",
                null,
            )
        })
    }

    fun dismissCrash() {
        crashLog = null
        App.crashFile(this).delete()
    }

    private fun displayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { return it }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment ?: "未命名"
    }

    // ---------------- 播放控制 ----------------

    fun togglePlay() {
        stopPreview()
        val c = player.controller ?: return
        if (c.isPlaying) {
            c.pause()
            return
        }
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
        c.play()
    }

    fun playAt(i: Int) {
        stopPreview()
        val c = player.controller ?: return
        c.seekTo(i, 0)
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun next() = player.controller?.seekToNext() ?: Unit
    fun prev() = player.controller?.seekToPrevious() ?: Unit
    fun seekTo(ms: Long) = player.controller?.seekTo(ms) ?: Unit
    fun remove(i: Int) = player.controller?.removeMediaItem(i) ?: Unit

    fun toggleShuffle() {
        val c = player.controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = player.controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun refreshNotificationState() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = nm.getNotificationChannel(PlaybackService.CHANNEL_ID)
        notifOff = needsNotificationPermission() || !nm.areNotificationsEnabled() ||
            (ch != null && ch.importance < NotificationManager.IMPORTANCE_DEFAULT)
    }

    /** 直接打開「播放中」通知的設定頁 */
    fun openNotificationSettings() {
        if (needsNotificationPermission()) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val nm = getSystemService(NotificationManager::class.java)
        val intent = if (nm.areNotificationsEnabled() && nm.getNotificationChannel(PlaybackService.CHANNEL_ID) != null) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, PlaybackService.CHANNEL_ID)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    fun clearQueue() {
        player.controller?.stop()
        player.controller?.clearMediaItems()
        player.message = "已清空播放清單"
    }

    /** 跑步前先把整個清單下載到快取，沒訊號也能聽。 */
    fun downloadAll() {
        val ids = player.queue.mapNotNull { Media.ytIdOf(it) }
        if (ids.isEmpty()) {
            player.message = "清單中沒有 YouTube 歌曲"
            return
        }
        player.message = "開始離線下載 ${ids.size} 首（背景進行）"
        MediaCache.precache(this, ids) { done, total ->
            runOnUiThread {
                player.cacheTick++
                if (total == 0) player.message = "全部歌曲都已可離線播放 ✓"
                else if (done == total) player.message = "離線下載完成 ✓（$total 首）"
            }
        }
    }

    fun isOffline(item: MediaItem): Boolean {
        val id = Media.ytIdOf(item) ?: return item.mediaId.startsWith("local:")
        return MediaCache.isFullyCached(this, id)
    }

    fun openBatterySettings() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            player.message = "已允許背景播放，不會被系統省電關掉 ✓"
            return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    // ---------------- 搜尋 ----------------

    fun runSearch() {
        val q = search.query.trim()
        if (q.isEmpty()) return
        // 貼上的是網址就直接加入
        if (Links.parse(q) != Links.Target.None && Links.findUrl(q) != null) {
            addFromText(q)
            return
        }
        searchJob?.cancel()
        search.loading = true
        search.error = null
        search.searched = true
        val kind = search.kind
        searchJob = lifecycleScope.launch {
            try {
                val page = withContext(Dispatchers.IO) { YouTube.search(q, kind) }
                search.page = page
                search.results = page.results
            } catch (e: Exception) {
                search.results = emptyList()
                search.error = "搜尋失敗：${e.message ?: e.javaClass.simpleName}"
            } finally {
                search.loading = false
            }
        }
    }

    fun searchMore() {
        val prev = search.page ?: return
        if (prev.next == null || search.loadingMore || search.loading) return
        search.loadingMore = true
        lifecycleScope.launch {
            try {
                val more = withContext(Dispatchers.IO) { YouTube.searchMore(prev) }
                search.page = more
                val seen = search.results.map { it.id }.toHashSet()
                search.results = search.results + more.results.filter { it.id !in seen }
            } catch (_: Exception) {
            } finally {
                search.loadingMore = false
            }
        }
    }

    fun applyPreset(p: Preset) {
        search.query = p.query
        search.kind = p.kind
        search.duration = p.duration
        runSearch()
    }
}
