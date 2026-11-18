package com.rcj.pocketplayer

/** 純文字網址解析（不依賴 Android，方便單元測試）。 */
object Links {

    sealed interface Target {
        data class YtVideo(val id: String) : Target
        data class YtPlaylist(val id: String) : Target
        data class Direct(val url: String) : Target
        data object None : Target
    }

    private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val bareYtRegex = Regex("""(?:www\.|m\.|music\.)?(?:youtube\.com|youtu\.be)/[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val videoIdRegex = Regex("""(?:[?&]v=|youtu\.be/|/shorts/|/embed/|/live/|/v/)([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])""")
    private val listIdRegex = Regex("""[?&]list=([A-Za-z0-9_-]{10,})""")

    /** 從分享文字中找出第一個網址（YouTube App 分享時常會夾帶標題文字）。 */
    fun findUrl(text: String): String? {
        val t = text.trim()
        urlRegex.find(t)?.let { return it.value.trimEnd('.', ',', ')', ']', '>') }
        bareYtRegex.find(t)?.let { return "https://" + it.value.trimEnd('.', ',', ')', ']', '>') }
        return null
    }

    fun isYouTube(url: String): Boolean =
        url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true)

    fun videoId(url: String): String? = videoIdRegex.find(url)?.groupValues?.get(1)

    fun playlistId(url: String): String? = listIdRegex.find(url)?.groupValues?.get(1)

    fun parse(text: String): Target {
        val url = findUrl(text) ?: return Target.None
        if (isYouTube(url)) {
            videoId(url)?.let { return Target.YtVideo(it) }
            playlistId(url)?.let { return Target.YtPlaylist(it) }
            return Target.None
        }
        return Target.Direct(url)
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
