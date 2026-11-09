package com.amar.gallery.util

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.amar.gallery.data.tdlib.TdClientManager
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CopyOnWriteArrayList

private const val TAG = "AmarStream"

@OptIn(UnstableApi::class)
class GrowingFileDataSource(
    private val tdManager: TdClientManager,
    private val fileId: Int,
    private val totalSize: Long
) : DataSource {

    class Factory(
        private val tdManager: TdClientManager,
        private val fileId: Int,
        private val totalSize: Long
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return GrowingFileDataSource(tdManager, fileId, totalSize)
        }
    }

    private val listeners = CopyOnWriteArrayList<TransferListener>()
    private var randomAccessFile: RandomAccessFile? = null
    private var currentOpenedPath: String? = null
    private var currentDataSpec: DataSpec? = null
    private var currentPosition: Long = 0L
    private var bytesRemaining: Long = 0L

    override fun addTransferListener(transferListener: TransferListener) {
        listeners.add(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        this.currentDataSpec = dataSpec
        this.currentPosition = dataSpec.position

        listeners.forEach { it.onTransferInitializing(this, dataSpec, true) }

        this.bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else if (totalSize > 0 && totalSize > currentPosition) {
            totalSize - currentPosition
        } else {
            C.LENGTH_UNSET.toLong()
        }

        Log.d(TAG, "Streaming open() fileId=$fileId offset=$currentPosition length=${dataSpec.length}")

        // Ensure TDLib downloads starting from currentPosition at priority 32 if not already in range
        val availableAhead = tdManager.getAvailableEnd(fileId, currentPosition) - currentPosition
        if (availableAhead < 256 * 1024L && !tdManager.isFileCompleted(fileId)) {
            tdManager.downloadFileOffset(fileId, offset = currentPosition, limit = 0L, priority = 32)
        }

        listeners.forEach { it.onTransferStart(this, dataSpec, true) }
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            minOf(length.toLong(), bytesRemaining).toInt()
        } else {
            length
        }

        val startWait = System.currentTimeMillis()
        var lastRequestTime = startWait
        val timeoutMs = 30000L // 30s timeout

        while (System.currentTimeMillis() - startWait < timeoutMs) {
            val isComplete = tdManager.isFileCompleted(fileId)
            val filePath = tdManager.getFileLocalPath(fileId)
                ?: tdManager.getFileProgress(fileId)?.path
                ?: currentOpenedPath

            if (!filePath.isNullOrEmpty()) {
                val file = File(filePath)
                if (file.exists() && file.canRead()) {
                    if (randomAccessFile == null || currentOpenedPath != filePath) {
                        try {
                            randomAccessFile?.close()
                            randomAccessFile = RandomAccessFile(file, "r")
                            currentOpenedPath = filePath
                        } catch (e: Exception) {
                            Log.w(TAG, "RAF open error: ${e.message}")
                        }
                    }

                    val raf = randomAccessFile
                    if (raf != null) {
                        val fileLen = file.length()
                        val availableEnd: Long = if (isComplete) {
                            maxOf(fileLen, totalSize)
                        } else {
                            tdManager.getAvailableEnd(fileId, currentPosition)
                        }

                        if (availableEnd > currentPosition) {
                            val chunk = minOf(bytesToRead.toLong(), availableEnd - currentPosition).toInt()
                            if (chunk > 0) {
                                try {
                                    raf.seek(currentPosition)
                                    val readCount = raf.read(buffer, offset, chunk)
                                    if (readCount > 0) {
                                        currentPosition += readCount
                                        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                                            bytesRemaining -= readCount
                                        }
                                        currentDataSpec?.let { spec ->
                                            listeners.forEach { it.onBytesTransferred(this, spec, true, readCount) }
                                        }

                                        // Keep download moving forward if buffer ahead is below 512KB
                                        val ahead = availableEnd - currentPosition
                                        if (ahead < 512 * 1024L && !isComplete) {
                                            val now = System.currentTimeMillis()
                                            if (now - lastRequestTime > 1000) {
                                                lastRequestTime = now
                                                tdManager.downloadFileOffset(fileId, offset = currentPosition, limit = 0L, priority = 32)
                                            }
                                        }

                                        return readCount
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "RAF read error: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }

            // Check if file is completely downloaded and reached end
            if (isComplete) {
                val raf = randomAccessFile
                if (raf != null && currentPosition >= raf.length()) {
                    return C.RESULT_END_OF_INPUT
                }
            }

            // Periodically refresh request if still waiting for data chunk
            val now = System.currentTimeMillis()
            if (now - lastRequestTime > 1200) {
                lastRequestTime = now
                tdManager.downloadFileOffset(fileId, offset = currentPosition, limit = 0L, priority = 32)
            }

            try {
                Thread.sleep(20)
            } catch (_: InterruptedException) {
                return C.RESULT_END_OF_INPUT
            }
        }

        Log.w(TAG, "Timeout waiting for stream chunk at position $currentPosition")
        return C.RESULT_END_OF_INPUT
    }

    override fun getUri(): Uri? = currentDataSpec?.uri

    override fun close() {
        try {
            randomAccessFile?.close()
        } catch (_: Exception) {}
        randomAccessFile = null
        currentOpenedPath = null
        currentDataSpec?.let { spec ->
            listeners.forEach { it.onTransferEnd(this, spec, true) }
            currentDataSpec = null
        }
    }
}
