package com.amar.gallery.data.tdlib

class FileRangeTracker {
    private val ranges = mutableListOf<LongRange>()
    @Volatile private var isCompleted = false
    @Volatile private var totalFileSize = 0L

    @Synchronized
    fun markCompleted(size: Long) {
        isCompleted = true
        totalFileSize = size
        ranges.clear()
        ranges.add(0L until size.coerceAtLeast(1L))
    }

    @Synchronized
    fun addRange(start: Long, endExclusive: Long) {
        if (isCompleted) return
        if (endExclusive <= start) return

        var curStart = start
        var curEnd = endExclusive
        val remaining = mutableListOf<LongRange>()

        for (r in ranges) {
            val rStart = r.first
            val rEnd = r.last + 1
            // Check if overlapping or directly contiguous
            if (maxOf(curStart, rStart) <= minOf(curEnd, rEnd)) {
                curStart = minOf(curStart, rStart)
                curEnd = maxOf(curEnd, rEnd)
            } else {
                remaining.add(r)
            }
        }
        remaining.add(curStart until curEnd)
        remaining.sortBy { it.first }
        ranges.clear()
        ranges.addAll(remaining)
    }

    @Synchronized
    fun getAvailableEnd(position: Long): Long {
        if (isCompleted) return totalFileSize
        for (r in ranges) {
            val rStart = r.first
            val rEnd = r.last + 1
            if (position in rStart until rEnd) {
                return rEnd
            }
        }
        return 0L
    }

    @Synchronized
    fun isRangeAvailable(start: Long, length: Long): Boolean {
        if (isCompleted) return true
        val targetEnd = start + length
        for (r in ranges) {
            val rStart = r.first
            val rEnd = r.last + 1
            if (start >= rStart && targetEnd <= rEnd) {
                return true
            }
        }
        return false
    }

    fun isCompleted(): Boolean = isCompleted
}
