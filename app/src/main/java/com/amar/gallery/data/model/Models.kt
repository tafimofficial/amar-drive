package com.amar.gallery.data.model

enum class MediaKind(val code: Int) {
    ALL(0),
    PHOTO(1),
    VIDEO(2),
    ANIMATION(3),
    AUDIO(4),
    FILE(5);

    companion object {
        fun fromCode(code: Int): MediaKind = when (code) {
            1 -> PHOTO
            2 -> VIDEO
            3 -> ANIMATION
            4 -> AUDIO
            5 -> FILE
            else -> ALL
        }
    }
}

data class MediaItem(
    val chatId: Long,
    val messageId: Long,
    val uniqueId: String,
    val fileId: Int,
    val thumbFileId: Int? = null,
    val minithumb: ByteArray? = null,
    val kind: MediaKind = MediaKind.PHOTO,
    val mime: String? = null,
    val fileName: String? = null,
    val sizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val durationSec: Int = 0,
    val date: Long = 0L,
    val caption: String? = null,
    val pinned: Boolean = false,
    val localPath: String? = null,
    val thumbPath: String? = null
) {
    val stableId: Long get() = (chatId.hashCode().toLong() shl 32) or (messageId and 0xFFFFFFFFL)

    val formattedDuration: String
        get() {
            if (durationSec <= 0) return ""
            val mins = durationSec / 60
            val secs = durationSec % 60
            return String.format(java.util.Locale.US, "%d:%02d", mins, secs)
        }
}

data class ChannelItem(
    val chatId: Long,
    val title: String,
    val username: String? = null,
    val photoPath: String? = null,
    val enabled: Boolean = true,
    val isAdmin: Boolean = true,
    val isGroup: Boolean = false,
    val totalMedia: Int = 0,
    val lastSyncAt: Long = 0L
)

sealed class AuthState {
    object Initial : AuthState()
    object WaitPhone : AuthState()
    data class WaitCode(val phoneNumber: String) : AuthState()
    data class WaitPassword(val phoneNumber: String) : AuthState()
    data class WaitQr(val link: String) : AuthState()
    data class Ready(val userFirstName: String, val phoneNumber: String) : AuthState()
    data class Error(val message: String) : AuthState()
    object Closed : AuthState()
}

