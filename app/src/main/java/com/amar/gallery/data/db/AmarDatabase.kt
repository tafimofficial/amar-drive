package com.amar.gallery.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.amar.gallery.data.model.ChannelItem
import com.amar.gallery.data.model.MediaItem
import com.amar.gallery.data.model.MediaKind
import com.amar.gallery.util.NativeEngine

class AmarDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "amar_gallery.db"
        private const val DB_VERSION = 2

        @Volatile
        private var instance: AmarDatabase? = null

        fun getInstance(context: Context): AmarDatabase {
            return instance ?: synchronized(this) {
                instance ?: AmarDatabase(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS channels (
                chat_id INTEGER PRIMARY KEY,
                title TEXT NOT NULL,
                username TEXT,
                photo_path TEXT,
                enabled INTEGER NOT NULL DEFAULT 1,
                is_admin INTEGER NOT NULL DEFAULT 1,
                is_group INTEGER NOT NULL DEFAULT 0,
                total_media INTEGER NOT NULL DEFAULT 0,
                last_sync_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS media (
                chat_id INTEGER NOT NULL,
                message_id INTEGER NOT NULL,
                unique_id TEXT NOT NULL,
                file_id INTEGER NOT NULL,
                thumb_file_id INTEGER,
                minithumb BLOB,
                kind INTEGER NOT NULL,
                mime TEXT,
                filename TEXT,
                size_bytes INTEGER NOT NULL,
                width INTEGER NOT NULL,
                height INTEGER NOT NULL,
                duration_sec INTEGER NOT NULL,
                date INTEGER NOT NULL,
                caption TEXT,
                pinned INTEGER NOT NULL DEFAULT 0,
                local_path TEXT,
                thumb_path TEXT,
                PRIMARY KEY (chat_id, message_id)
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_media_chat_date ON media(chat_id, date DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_media_kind_date ON media(kind, date DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_media_date ON media(date DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_media_unique ON media(unique_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE channels ADD COLUMN is_group INTEGER NOT NULL DEFAULT 0")
            } catch (e: Exception) {
                // column might already exist
            }
        }
    }

    fun upsertChannel(channel: ChannelItem) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("chat_id", channel.chatId)
            put("title", channel.title)
            put("username", channel.username)
            put("photo_path", channel.photoPath)
            put("enabled", if (channel.enabled) 1 else 0)
            put("is_admin", if (channel.isAdmin) 1 else 0)
            put("is_group", if (channel.isGroup) 1 else 0)
            put("total_media", channel.totalMedia)
            put("last_sync_at", channel.lastSyncAt)
        }
        db.insertWithOnConflict("channels", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getChannels(): List<ChannelItem> {
        val list = mutableListOf<ChannelItem>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT chat_id, title, username, photo_path, enabled, is_admin, is_group, total_media, last_sync_at FROM channels ORDER BY title ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    ChannelItem(
                        chatId = it.getLong(0),
                        title = it.getString(1),
                        username = it.getString(2),
                        photoPath = it.getString(3),
                        enabled = it.getInt(4) == 1,
                        isAdmin = it.getInt(5) == 1,
                        isGroup = it.getInt(6) == 1,
                        totalMedia = it.getInt(7),
                        lastSyncAt = it.getLong(8)
                    )
                )
            }
        }
        return list
    }

    fun getChannel(chatId: Long): ChannelItem? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT chat_id, title, username, photo_path, enabled, is_admin, is_group, total_media, last_sync_at FROM channels WHERE chat_id = ?",
            arrayOf(chatId.toString())
        )
        cursor.use {
            if (it.moveToNext()) {
                return ChannelItem(
                    chatId = it.getLong(0),
                    title = it.getString(1),
                    username = it.getString(2),
                    photoPath = it.getString(3),
                    enabled = it.getInt(4) == 1,
                    isAdmin = it.getInt(5) == 1,
                    isGroup = it.getInt(6) == 1,
                    totalMedia = it.getInt(7),
                    lastSyncAt = it.getLong(8)
                )
            }
        }
        return null
    }

    fun setChannelEnabled(chatId: Long, enabled: Boolean) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("enabled", if (enabled) 1 else 0)
        }
        db.update("channels", values, "chat_id = ?", arrayOf(chatId.toString()))
    }

    fun deleteChannel(chatId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("channels", "chat_id = ?", arrayOf(chatId.toString()))
            db.delete("media", "chat_id = ?", arrayOf(chatId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun upsertMedia(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (item in items) {
                val values = ContentValues().apply {
                    put("chat_id", item.chatId)
                    put("message_id", item.messageId)
                    put("unique_id", item.uniqueId)
                    put("file_id", item.fileId)
                    put("thumb_file_id", item.thumbFileId)
                    put("minithumb", item.minithumb)
                    put("kind", item.kind.code)
                    put("mime", item.mime)
                    put("filename", item.fileName)
                    put("size_bytes", item.sizeBytes)
                    put("width", item.width)
                    put("height", item.height)
                    put("duration_sec", item.durationSec)
                    put("date", item.date)
                    put("caption", item.caption)
                    put("pinned", if (item.pinned) 1 else 0)
                    put("local_path", item.localPath)
                    put("thumb_path", item.thumbPath)
                }
                db.insertWithOnConflict("media", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getTimelineMedia(chatId: Long?, kind: MediaKind, limit: Int = 300, offset: Int = 0): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val db = readableDatabase

        val whereClauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (chatId != null && chatId != 0L) {
            whereClauses.add("chat_id = ?")
            args.add(chatId.toString())
        } else {
            // Only enabled channels
            whereClauses.add("chat_id IN (SELECT chat_id FROM channels WHERE enabled = 1)")
        }

        if (kind != MediaKind.ALL) {
            whereClauses.add("kind = ?")
            args.add(kind.code.toString())
        }

        val whereSql = if (whereClauses.isNotEmpty()) "WHERE " + whereClauses.joinToString(" AND ") else ""
        val query = "SELECT chat_id, message_id, unique_id, file_id, thumb_file_id, minithumb, kind, mime, filename, size_bytes, width, height, duration_sec, date, caption, pinned, local_path, thumb_path FROM media $whereSql ORDER BY date DESC LIMIT ? OFFSET ?"
        args.add(limit.toString())
        args.add(offset.toString())

        val cursor = db.rawQuery(query, args.toTypedArray())
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    MediaItem(
                        chatId = it.getLong(0),
                        messageId = it.getLong(1),
                        uniqueId = it.getString(2),
                        fileId = it.getInt(3),
                        thumbFileId = if (it.isNull(4)) null else it.getInt(4),
                        minithumb = it.getBlob(5),
                        kind = MediaKind.fromCode(it.getInt(6)),
                        mime = it.getString(7),
                        fileName = it.getString(8),
                        sizeBytes = it.getLong(9),
                        width = it.getInt(10),
                        height = it.getInt(11),
                        durationSec = it.getInt(12),
                        date = it.getLong(13),
                        caption = it.getString(14),
                        pinned = it.getInt(15) == 1,
                        localPath = it.getString(16),
                        thumbPath = it.getString(17)
                    )
                )
            }
        }
        return list
    }

    fun getFilesMedia(chatId: Long?, limit: Int = 100, offset: Int = 0): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val db = readableDatabase

        val whereClauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (chatId != null && chatId != 0L) {
            whereClauses.add("chat_id = ?")
            args.add(chatId.toString())
        } else {
            whereClauses.add("chat_id IN (SELECT chat_id FROM channels WHERE enabled = 1)")
        }

        // Files and Audio
        whereClauses.add("(kind = ${MediaKind.FILE.code} OR kind = ${MediaKind.AUDIO.code})")

        val whereSql = "WHERE " + whereClauses.joinToString(" AND ")
        val query = "SELECT chat_id, message_id, unique_id, file_id, thumb_file_id, minithumb, kind, mime, filename, size_bytes, width, height, duration_sec, date, caption, pinned, local_path, thumb_path FROM media $whereSql ORDER BY date DESC LIMIT ? OFFSET ?"
        args.add(limit.toString())
        args.add(offset.toString())

        val cursor = db.rawQuery(query, args.toTypedArray())
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    MediaItem(
                        chatId = it.getLong(0),
                        messageId = it.getLong(1),
                        uniqueId = it.getString(2),
                        fileId = it.getInt(3),
                        thumbFileId = if (it.isNull(4)) null else it.getInt(4),
                        minithumb = it.getBlob(5),
                        kind = MediaKind.fromCode(it.getInt(6)),
                        mime = it.getString(7),
                        fileName = it.getString(8),
                        sizeBytes = it.getLong(9),
                        width = it.getInt(10),
                        height = it.getInt(11),
                        durationSec = it.getInt(12),
                        date = it.getLong(13),
                        caption = it.getString(14),
                        pinned = it.getInt(15) == 1,
                        localPath = it.getString(16),
                        thumbPath = it.getString(17)
                    )
                )
            }
        }
        return list
    }

    fun updateLocalPath(fileId: Int, path: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("local_path", path)
        }
        db.update("media", values, "file_id = ?", arrayOf(fileId.toString()))
    }

    fun updateMediaFileId(chatId: Long, messageId: Long, freshFileId: Int, freshThumbFileId: Int? = null) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("file_id", freshFileId)
            if (freshThumbFileId != null && freshThumbFileId > 0) {
                put("thumb_file_id", freshThumbFileId)
            }
        }
        db.update("media", values, "chat_id = ? AND message_id = ?", arrayOf(chatId.toString(), messageId.toString()))
    }

    fun togglePin(chatId: Long, messageId: Long): Boolean {
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT pinned FROM media WHERE chat_id = ? AND message_id = ?", arrayOf(chatId.toString(), messageId.toString()))
        var newPinned = true
        cursor.use {
            if (it.moveToFirst()) {
                newPinned = it.getInt(0) == 0
            }
        }
        val values = ContentValues().apply {
            put("pinned", if (newPinned) 1 else 0)
        }
        db.update("media", values, "chat_id = ? AND message_id = ?", arrayOf(chatId.toString(), messageId.toString()))
        return newPinned
    }

    fun getMediaCount(chatId: Long?, kind: MediaKind = MediaKind.ALL): Int {
        val db = readableDatabase
        val whereClauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (chatId != null && chatId != 0L) {
            whereClauses.add("chat_id = ?")
            args.add(chatId.toString())
        } else {
            whereClauses.add("chat_id IN (SELECT chat_id FROM channels WHERE enabled = 1)")
        }

        if (kind != MediaKind.ALL) {
            whereClauses.add("kind = ?")
            args.add(kind.code.toString())
        }

        val whereSql = if (whereClauses.isNotEmpty()) "WHERE " + whereClauses.joinToString(" AND ") else ""
        val cursor = db.rawQuery("SELECT COUNT(*) FROM media $whereSql", args.toTypedArray())
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }
        return 0
    }

    fun clearAllData() {
        val db = writableDatabase
        db.execSQL("DELETE FROM media")
        db.execSQL("DELETE FROM channels")
    }
}

