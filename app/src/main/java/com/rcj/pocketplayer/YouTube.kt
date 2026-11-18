package com.rcj.pocketplayer

import androidx.media3.common.MediaItem
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory as Q
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** 影片無法播放（下架、地區限制…），重試也沒用。 */
class UnplayableException(message: String, cause: Throwable? = null) : IOException(message, cause)

object YouTube {

    private const val TTL_MS = 3L * 60 * 60 * 1000 // 串流網址約 6 小時過期，保守用 3 小時
    private val urlCache = ConcurrentHashMap<String, Pair<String, Long>>()

    fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    // ---------------- 串流網址解析 ----------------

    /** 取得可播放的音訊網址（在播放器的載入執行緒呼叫，可阻塞）。 */
    fun streamUrl(id: String): String {
        urlCache[id]?.let { (url, t) -> if (System.currentTimeMillis() - t < TTL_MS) return url }
        val info = fetchStreamInfo(id)
        val url = pickBestAudio(info) ?: throw UnplayableException("找不到可播放的音訊")
        urlCache[id] = url to System.currentTimeMillis()
        return url
    }

    fun invalidate(id: String) {
        urlCache.remove(id)
    }

    private fun fetchStreamInfo(id: String): StreamInfo =
        try {
            StreamInfo.getInfo(ServiceList.YouTube, watchUrl(id))
        } catch (e: IOException) {
            throw e // 網路問題：交給播放器重試
        } catch (e: ExtractionException) {
            throw UnplayableException(e.message ?: "無法解析影片", e)
        }

    /** 音質優先：挑位元率最高的原始音軌（通常是 Opus 160kbps 或 AAC 128kbps）。 */
    private fun pickBestAudio(info: StreamInfo): String? {
        val progressive = info.audioStreams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
        }
        val original = progressive.filter { s ->
            val type = s.audioTrackType
            type == null || type.name == "ORIGINAL"
        }
        val best = original.ifEmpty { progressive }
            .maxByOrNull { maxOf(it.averageBitrate, it.bitrate) }
        if (best != null) return best.content

        // 備援：含聲音的一般影片串流
        return info.videoStreams
            .firstOrNull { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            ?.content
    }

    // ---------------- 加入網址 ----------------

    fun loadVideo(id: String): MediaItem {
        val info = fetchStreamInfo(id)
        pickBestAudio(info)?.let { urlCache[id] = it to System.currentTimeMillis() }
        return Media.youtube(id, info.name, info.uploaderName ?: "")
    }

    fun loadPlaylist(listId: String, limit: Int = 300): List<MediaItem> {
        val url = "https://www.youtube.com/playlist?list=$listId"
        val info = PlaylistInfo.getInfo(ServiceList.YouTube, url)
        val items = ArrayList<StreamInfoItem>(info.relatedItems)
        var hasNext = info.hasNextPage()
        var page: Page? = info.nextPage
        while (hasNext && page != null && items.size < limit) {
            val more = PlaylistInfo.getMoreItems(ServiceList.YouTube, url, page)
            items += more.items
            hasNext = more.hasNextPage()
            page = more.nextPage
        }
        return items.take(limit).mapNotNull { si ->
            val vid = Links.videoId(si.url) ?: return@mapNotNull null
            Media.youtube(vid, si.name, si.uploaderName ?: "")
        }
    }

    // ---------------- 搜尋 ----------------

    enum class Kind(val label: String, val filter: String) {
        SONGS("歌曲", Q.MUSIC_SONGS),
        MUSIC_VIDEOS("MV", Q.MUSIC_VIDEOS),
        VIDEOS("影片", Q.VIDEOS),
        PLAYLISTS("歌單", Q.MUSIC_PLAYLISTS),
    }

    data class Result(
        val isPlaylist: Boolean,
        val id: String,
        val title: String,
        val subtitle: String,
        val durationSec: Long,
        val thumb: String?,
    )

    class SearchPage(val results: List<Result>, val next: Page?, val query: SearchQueryHandler)

    fun search(text: String, kind: Kind): SearchPage {
        val q = ServiceList.YouTube.searchQHFactory.fromQuery(text, listOf(kind.filter), "")
        val info = SearchInfo.getInfo(ServiceList.YouTube, q)
        return SearchPage(info.relatedItems.mapNotNull(::toResult), if (info.hasNextPage()) info.nextPage else null, q)
    }

    fun searchMore(prev: SearchPage): SearchPage {
        val page = prev.next ?: return SearchPage(emptyList(), null, prev.query)
        val more = SearchInfo.getMoreItems(ServiceList.YouTube, prev.query, page)
        return SearchPage(more.items.mapNotNull(::toResult), if (more.hasNextPage()) more.nextPage else null, prev.query)
    }

    private fun bestThumb(images: List<Image>): String? = images.maxByOrNull { it.height }?.url

    private fun toResult(item: InfoItem): Result? = when (item) {
        is StreamInfoItem -> Links.videoId(item.url)?.let { id ->
            Result(false, id, item.name, item.uploaderName ?: "", item.duration, bestThumb(item.thumbnails))
        }
        is PlaylistInfoItem -> Links.playlistId(item.url)?.let { id ->
            val count = item.streamCount
            val sub = listOfNotNull(item.uploaderName, if (count > 0) "$count 首" else null).joinToString(" · ")
            Result(true, id, item.name, sub, -1, bestThumb(item.thumbnails))
        }
        else -> null
    }
}
