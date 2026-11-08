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
import java.io.IOException
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

        // Immediately trigger TDLib download starting at currentPosition at highest streaming priority (32)
        tdManager.downloadFileOffset(fileId, offset = currentPosition, limit = 0L, priority = 32)

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
        val timeoutMs = 25000L // 25s timeout before stopping

        while (System.currentTimeMillis() - startWait < timeoutMs) {
            // 1. Try reading directly from disk if file is available
            val progress = tdManager.getFileProgress(fileId)
            val filePath = progress?.path ?: tdManager.getFileLocalPath(fileId) ?: currentOpenedPath

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
                        val isComplete = progress?.isCompleted == true || tdManager.isFileCompleted(fileId)
                        val prefix = progress?.downloadedPrefixSize ?: 0L

                        // If completed or requested chunk is within downloaded contiguous prefix
                        val available = if (isComplete) fileLen else maxOf(prefix, fileLen)

                        if (available > currentPosition) {
                            val chunk = minOf(bytesToRead.toLong(), available - currentPosition).toInt()
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

            // 2. Fallback to TDLib's ReadFilePart API for partial memory/cache chunks
            val partLimit = minOf(bytesToRead, 65536).toLong()
            val chunkData = tdManager.readFilePartSync(fileId, currentPosition, partLimit, timeoutMs = 300)
            if (chunkData != null && chunkData.isNotEmpty()) {
                val readCount = minOf(bytesToRead, chunkData.size)
                System.arraycopy(chunkData, 0, buffer, offset, readCount)
                currentPosition += readCount
                if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                    bytesRemaining -= readCount
                }
                currentDataSpec?.let { spec ->
                    listeners.forEach { it.onBytesTransferred(this, spec, true, readCount) }
                }
                return readCount
            }

            // 3. Check if download completed and nothing more to read
            if (tdManager.isFileCompleted(fileId)) {
                val raf = randomAccessFile
                if (raf != null && currentPosition >= raf.length()) {
                    return C.RESULT_END_OF_INPUT
                }
            }

            // Keep TDLib download alive from current position
            val elapsed = System.currentTimeMillis() - startWait
            if (elapsed > 1000 && elapsed % 2000 < 60) {
                tdManager.downloadFileOffset(fileId, offset = currentPosition, limit = 0L, priority = 32)
            }

            // Sleep briefly while TDLib fetches next network packet
            try {
                Thread.sleep(35)
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
