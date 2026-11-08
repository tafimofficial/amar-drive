package com.amar.gallery

import com.amar.gallery.data.model.MediaItem
import com.amar.gallery.data.model.MediaKind
import com.amar.gallery.util.NativeEngine
import org.junit.Assert.*
import org.junit.Test

class LogicTests {

    @Test
    fun testMediaClassificationFallback() {
        val photoKind = NativeEngine.getMediaKind("image/jpeg", "vacation.jpg")
        assertEquals(MediaKind.PHOTO.code, photoKind)

        val videoKind = NativeEngine.getMediaKind("video/mp4", "clip.mp4")
        assertEquals(MediaKind.VIDEO.code, videoKind)

        val gifKind = NativeEngine.getMediaKind("image/gif", "funny.gif")
        assertEquals(MediaKind.ANIMATION.code, gifKind)

        val audioKind = NativeEngine.getMediaKind("audio/mpeg", "song.mp3")
        assertEquals(MediaKind.AUDIO.code, audioKind)

        val docKind = NativeEngine.getMediaKind("application/pdf", "receipt.pdf")
        assertEquals(MediaKind.FILE.code, docKind)
    }

    @Test
    fun testMediaDurationFormatting() {
        val itemShort = MediaItem(
            chatId = 123L,
            messageId = 1L,
            uniqueId = "u1",
            fileId = 10,
            durationSec = 65
        )
        assertEquals("1:05", itemShort.formattedDuration)

        val itemLong = MediaItem(
            chatId = 123L,
            messageId = 2L,
            uniqueId = "u2",
            fileId = 11,
            durationSec = 3600
        )
        assertEquals("60:00", itemLong.formattedDuration)
    }

    @Test
    fun testFormatBytesFallback() {
        val zero = NativeEngine.formatBytes(0L)
        assertEquals("0 B", zero)

        val mb = NativeEngine.formatBytes(15 * 1024 * 1024L)
        assertTrue(mb.contains("15.0 MB") || mb.contains("15 MB"))

        val gb = NativeEngine.formatBytes(2 * 1024 * 1024 * 1024L)
        assertTrue(gb.contains("2.00 GB") || gb.contains("2 GB"))
    }

    @Test
    fun testStableIdUniqueness() {
        val itemA = MediaItem(chatId = 1001L, messageId = 50L, uniqueId = "a", fileId = 1)
        val itemB = MediaItem(chatId = 1001L, messageId = 51L, uniqueId = "b", fileId = 2)
        val itemC = MediaItem(chatId = 1002L, messageId = 50L, uniqueId = "c", fileId = 3)

        assertNotEquals(itemA.stableId, itemB.stableId)
        assertNotEquals(itemA.stableId, itemC.stableId)
    }
}

