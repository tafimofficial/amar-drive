package com.amar.gallery

import com.amar.gallery.data.tdlib.FileRangeTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileRangeTrackerTest {

    @Test
    fun testInitialState() {
        val tracker = FileRangeTracker()
        assertFalse(tracker.isCompleted())
        assertEquals(0L, tracker.getAvailableEnd(0L))
        assertEquals(0L, tracker.getAvailableEnd(100L))
    }

    @Test
    fun testSingleRange() {
        val tracker = FileRangeTracker()
        tracker.addRange(0L, 1000L)
        assertEquals(1000L, tracker.getAvailableEnd(0L))
        assertEquals(1000L, tracker.getAvailableEnd(500L))
        assertEquals(1000L, tracker.getAvailableEnd(999L))
        assertEquals(0L, tracker.getAvailableEnd(1000L))
        assertEquals(0L, tracker.getAvailableEnd(1500L))
    }

    @Test
    fun testContiguousMerge() {
        val tracker = FileRangeTracker()
        tracker.addRange(0L, 1000L)
        tracker.addRange(1000L, 2500L)
        assertEquals(2500L, tracker.getAvailableEnd(0L))
        assertEquals(2500L, tracker.getAvailableEnd(1000L))
        assertEquals(2500L, tracker.getAvailableEnd(2499L))
        assertEquals(0L, tracker.getAvailableEnd(2500L))
    }

    @Test
    fun testDisjointRangesWithMoovAtEnd() {
        val tracker = FileRangeTracker()
        // Prefix at start
        tracker.addRange(0L, 131072L)
        // Tail at end (e.g. moov atom)
        tracker.addRange(190000000L, 200000000L)

        // Start chunk is available
        assertEquals(131072L, tracker.getAvailableEnd(0L))
        assertEquals(131072L, tracker.getAvailableEnd(100000L))
        // Gap in middle is NOT available
        assertEquals(0L, tracker.getAvailableEnd(131072L))
        assertEquals(0L, tracker.getAvailableEnd(50000000L))
        // Tail is available
        assertEquals(200000000L, tracker.getAvailableEnd(190000000L))
        assertEquals(200000000L, tracker.getAvailableEnd(195000000L))

        // Bridging the gap
        tracker.addRange(131072L, 50000000L)
        assertEquals(50000000L, tracker.getAvailableEnd(0L))
    }

    @Test
    fun testCompletion() {
        val tracker = FileRangeTracker()
        tracker.markCompleted(500000000L)
        assertTrue(tracker.isCompleted())
        assertEquals(500000000L, tracker.getAvailableEnd(0L))
        assertEquals(500000000L, tracker.getAvailableEnd(100000000L))
        assertEquals(500000000L, tracker.getAvailableEnd(499999999L))
    }
}
