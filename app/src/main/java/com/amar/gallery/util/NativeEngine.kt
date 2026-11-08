package com.amar.gallery.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

object NativeEngine {
    private const val TAG = "NativeEngine"
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("amar_core")
            isNativeLoaded = true
            val status = initEngine()
            try { Log.i(TAG, "Native C Core initialized: $status") } catch (_: Throwable) {}
        } catch (t: Throwable) {
            try { Log.e(TAG, "Failed to load amar_core native library", t) } catch (_: Throwable) {}
            isNativeLoaded = false
        }
    }

    external fun initEngine(): String
    external fun fastHash64(bytes: ByteArray, length: Int): Long
    external fun classifyMedia(mime: String?, fileName: String?): Int
    external fun formatDateBucket(epochSeconds: Long): String
    external fun formatFileSize(bytes: Long): String
    external fun processMinithumb(rawBytes: ByteArray): ByteArray?

    fun isLoaded(): Boolean = isNativeLoaded

    /**
     * Decode Telegram MTProto minithumbnail byte array into a Bitmap instantly.
     */
    fun decodeMinithumb(rawBytes: ByteArray?): Bitmap? {
        if (rawBytes == null || rawBytes.isEmpty()) return null
        return try {
            val processed = if (isNativeLoaded) {
                processMinithumb(rawBytes) ?: rawBytes
            } else {
                rawBytes
            }
            BitmapFactory.decodeByteArray(processed, 0, processed.size)
        } catch (t: Throwable) {
            Log.w(TAG, "Error decoding minithumb: ${t.message}")
            null
        }
    }

    /**
     * Compute fast checksum for cache validation.
     */
    fun computeChecksum(data: ByteArray): Long {
        if (!isNativeLoaded || data.isEmpty()) return data.hashCode().toLong()
        return fastHash64(data, data.size)
    }

    /**
     * Classify media into ordinal kind.
     */
    fun getMediaKind(mime: String?, fileName: String?): Int {
        if (!isNativeLoaded) {
            val m = mime?.lowercase().orEmpty()
            val f = fileName?.lowercase().orEmpty()
            return when {
                m.contains("gif") || f.endsWith(".gif") -> 3
                m.startsWith("image/") || f.endsWith(".jpg") || f.endsWith(".jpeg") || f.endsWith(".png") ||
                        f.endsWith(".webp") || f.endsWith(".heic") || f.endsWith(".heif") || f.endsWith(".bmp") ||
                        f.endsWith(".dng") || f.endsWith(".cr2") || f.endsWith(".raw") || f.endsWith(".svg") -> 1
                m.startsWith("video/") || f.endsWith(".mp4") || f.endsWith(".mkv") || f.endsWith(".webm") ||
                        f.endsWith(".mov") || f.endsWith(".avi") || f.endsWith(".3gp") || f.endsWith(".flv") ||
                        f.endsWith(".ts") || f.endsWith(".wmv") || f.endsWith(".m4v") -> 2
                m.startsWith("audio/") || f.endsWith(".mp3") || f.endsWith(".m4a") || f.endsWith(".ogg") ||
                        f.endsWith(".opus") || f.endsWith(".flac") || f.endsWith(".wav") || f.endsWith(".aac") -> 4
                else -> 5
            }
        }
        return classifyMedia(mime, fileName)
    }

    /**
     * Format date header string.
     */
    fun getDateHeader(epochSeconds: Long): String {
        if (!isNativeLoaded) {
            val date = java.util.Date(epochSeconds * 1000)
            val sdf = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
            return sdf.format(date)
        }
        return formatDateBucket(epochSeconds)
    }

    /**
     * Format human-readable file size.
     */
    fun formatBytes(bytes: Long): String {
        if (!isNativeLoaded) {
            if (bytes <= 0) return "0 B"
            val mb = bytes.toDouble() / (1024 * 1024)
            return if (mb >= 1000) String.format(java.util.Locale.US, "%.2f GB", mb / 1024)
            else String.format(java.util.Locale.US, "%.1f MB", mb)
        }
        return formatFileSize(bytes)
    }
}

