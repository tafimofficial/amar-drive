package com.amar.gallery.util

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

@OptIn(UnstableApi::class)
class GrowingFileDataSource(
    private val getFilePath: () -> String?,
    private val isDownloadComplete: () -> Boolean,
    private val totalSize: Long
) : DataSource {

    private var randomAccessFile: RandomAccessFile? = null
    private var uri: Uri? = null
    private var currentPosition: Long = 0L
    private var bytesRemaining: Long = 0L

    override fun addTransferListener(transferListener: TransferListener) {}

    override fun open(dataSpec: DataSpec): Long {
        this.uri = dataSpec.uri
        val path = getFilePath() ?: throw IOException("File path not yet allocated")
        val file = File(path)

        // Wait up to 8 seconds for the file to exist on disk and have initial header bytes
        val start = System.currentTimeMillis()
        while ((!file.exists() || file.length() < 1024) && System.currentTimeMillis() - start < 8000) {
            try {
                Thread.sleep(60)
            } catch (_: InterruptedException) {
                break
            }
        }

        if (!file.exists()) {
            throw IOException("File not found on disk: $path")
        }

        randomAccessFile = RandomAccessFile(file, "r")
        currentPosition = dataSpec.position
        randomAccessFile?.seek(currentPosition)

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else if (totalSize > 0) {
            totalSize - currentPosition
        } else {
            C.LENGTH_UNSET.toLong()
        }

        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val raf = randomAccessFile ?: return C.RESULT_END_OF_INPUT

        val start = System.currentTimeMillis()
        while (true) {
            val fileLen = raf.length()
            if (fileLen > currentPosition) {
                val bytesToRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                    minOf(length.toLong(), bytesRemaining, fileLen - currentPosition).toInt()
                } else {
                    minOf(length.toLong(), fileLen - currentPosition).toInt()
                }

                if (bytesToRead > 0) {
                    val bytesRead = raf.read(buffer, offset, bytesToRead)
                    if (bytesRead > 0) {
                        currentPosition += bytesRead
                        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                            bytesRemaining -= bytesRead
                        }
                        return bytesRead
                    }
                }
            }

            // Check if download completed
            if (isDownloadComplete()) {
                val finalLen = raf.length()
                if (finalLen <= currentPosition) {
                    return C.RESULT_END_OF_INPUT
                }
            }

            // Stall timeout: wait up to 15 seconds for more bytes to be downloaded by TDLib
            if (System.currentTimeMillis() - start > 15000) {
                return C.RESULT_END_OF_INPUT
            }

            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                return C.RESULT_END_OF_INPUT
            }
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try {
            randomAccessFile?.close()
        } catch (_: Exception) {}
        randomAccessFile = null
    }
}

