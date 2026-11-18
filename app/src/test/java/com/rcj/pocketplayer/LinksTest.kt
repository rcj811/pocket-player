package com.rcj.pocketplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class LinksTest {
    @Test fun watchUrl() =
        assertEquals(Links.Target.YtVideo("dQw4w9WgXcQ"), Links.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10s"))

    @Test fun shortUrlInsideSharedText() =
        assertEquals(Links.Target.YtVideo("dQw4w9WgXcQ"), Links.parse("好聽的歌 https://youtu.be/dQw4w9WgXcQ?si=abc 分享"))

    @Test fun shortsAndMusic() {
        assertEquals(Links.Target.YtVideo("abcdefghijk"), Links.parse("https://youtube.com/shorts/abcdefghijk"))
        assertEquals(Links.Target.YtVideo("abcdefghijk"), Links.parse("https://music.youtube.com/watch?v=abcdefghijk&list=RDAMVM"))
    }

    @Test fun playlist() =
        assertEquals(Links.Target.YtPlaylist("PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG"),
            Links.parse("https://www.youtube.com/playlist?list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG"))

    @Test fun withoutScheme() =
        assertEquals(Links.Target.YtVideo("dQw4w9WgXcQ"), Links.parse("youtu.be/dQw4w9WgXcQ"))

    @Test fun directMp3() =
        assertEquals(Links.Target.Direct("https://example.com/a.mp3"), Links.parse("https://example.com/a.mp3"))

    @Test fun nothing() = assertEquals(Links.Target.None, Links.parse("hello"))

    @Test fun duration() {
        assertEquals("3:05", Links.formatDuration(185_000))
        assertEquals("1:00:01", Links.formatDuration(3_601_000))
    }
}
