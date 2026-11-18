package com.rcj.pocketplayer

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.util.Collections
import java.util.concurrent.Executors

/**
 * 播放快取：YouTube 音訊邊播邊存（最多 1.5GB，自動淘汰最舊的）。
 * 播過或預載過的歌，之後斷網也能直接播放。
 */
object MediaCache {
    private const val TAG = "MediaCache"
    private const val MAX_BYTES = 1536L * 1024 * 1024

    @Volatile private var cache: SimpleCache? = null
    private val precacheExecutor = Executors.newSingleThreadExecutor()
    private val pending = Collections.synchronizedSet(HashSet<String>())

    fun get(context: Context): SimpleCache =
        cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(context.applicationContext.cacheDir, "media"),
                LeastRecentlyUsedCacheEvictor(MAX_BYTES),
                StandaloneDatabaseProvider(context.applicationContext),
            ).also { cache = it }
        }

    private fun httpFactory() = DefaultHttpDataSource.Factory()
        .setUserAgent(DownloaderImpl.USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)

    /** yt://video/ID → 真正的串流網址（只有快取沒有時才會呼叫）。 */
    private val ytResolver = ResolvingDataSource.Resolver { spec ->
        if (spec.uri.scheme == "yt") {
            val id = spec.uri.lastPathSegment ?: throw UnplayableException("錯誤的影片 ID")
            spec.withUri(Uri.parse(YouTube.streamUrl(id)))
        } else spec
    }

    /** 網路來源：快取 → 解析 → HTTP。 */
    fun remoteFactory(context: Context): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(get(context))
            .setUpstreamDataSourceFactory(ResolvingDataSource.Factory(httpFactory(), ytResolver))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** 本機檔案不進快取，網路來源走快取。 */
    fun playerFactory(context: Context): DataSource.Factory {
        val local = DefaultDataSource.Factory(context.applicationContext)
        val remote = remoteFactory(context)
        return DataSource.Factory { RoutingDataSource(local.createDataSource(), remote.createDataSource()) }
    }

    fun isFullyCached(context: Context, ytId: String): Boolean {
        val c = get(context)
        val key = Media.ytCacheKey(ytId)
        val len = ContentMetadata.getContentLength(c.getContentMetadata(key))
        return len > 0 && c.getCachedBytes(key, 0, len) >= len
    }

    /** 在背景把歌曲完整下載到快取（離線播放 / 預載下一首）。 */
    fun precache(context: Context, ytIds: List<String>, onEach: ((done: Int, total: Int) -> Unit)? = null) {
        val app = context.applicationContext
        val todo = ytIds.distinct().filter { !isFullyCached(app, it) && pending.add(it) }
        var done = 0
        for (id in todo) {
            precacheExecutor.execute {
                try {
                    val spec = DataSpec.Builder()
                        .setUri(Media.ytUri(id))
                        .setKey(Media.ytCacheKey(id))
                        .build()
                    CacheWriter(remoteFactory(app).createDataSource(), spec, null, null).cache()
                } catch (e: Exception) {
                    Log.w(TAG, "precache $id failed: $e")
                } finally {
                    pending.remove(id)
                    done++
                    onEach?.invoke(done, todo.size)
                }
            }
        }
        if (todo.isEmpty()) onEach?.invoke(0, 0)
    }
}

/** 依 URI 決定走本機或網路（含快取）來源。 */
private class RoutingDataSource(
    private val local: DataSource,
    private val remote: DataSource,
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        local.addTransferListener(transferListener)
        remote.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val scheme = dataSpec.uri.scheme?.lowercase()
        val ds = if (scheme == "yt" || scheme == "http" || scheme == "https") remote else local
        active = ds
        return ds.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }
}
