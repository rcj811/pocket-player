package com.rcj.pocketplayer

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/** 統一建立 MediaItem，確保 URI、快取鍵、顯示資訊一致。 */
object Media {
    const val YT_PREFIX = "yt:"

    fun ytUri(id: String): Uri = Uri.parse("yt://video/$id")
    fun ytThumb(id: String): Uri = Uri.parse("https://i.ytimg.com/vi/$id/hqdefault.jpg")
    fun ytCacheKey(id: String) = YT_PREFIX + id

    fun item(
        mediaId: String,
        uri: Uri,
        title: String,
        artist: String,
        artwork: Uri?,
        cacheKey: String? = null,
    ): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(artwork)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setCustomCacheKey(cacheKey)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(meta)
            .build()
    }

    fun youtube(id: String, title: String, artist: String): MediaItem =
        item(ytCacheKey(id), ytUri(id), title, artist, ytThumb(id), ytCacheKey(id))

    fun ytIdOf(item: MediaItem): String? =
        item.mediaId.takeIf { it.startsWith(YT_PREFIX) }?.removePrefix(YT_PREFIX)
}
