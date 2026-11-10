package com.ushareit.core.p2p

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

data class FileChunk(
    val fileId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val data: ByteArray,
    val crc32: Long
)

class FileChunkProcessor(private val chunkSize: Int = 1024 * 1024) { // 1MB chunk standard

    fun splitFile(file: File, fileId: String, onChunkReady: (FileChunk) -> Unit) {
        val totalChunks = ((file.length() + chunkSize - 1) / chunkSize).toInt()
        val fis = FileInputStream(file)
        val channel = fis.channel
        val buffer = ByteBuffer.allocate(chunkSize)

        var chunkIndex = 0
        while (channel.read(buffer) > 0) {
            buffer.flip()
            val chunkBytes = ByteArray(buffer.remaining())
            buffer.get(chunkBytes)
            val crc = ChecksumVerifier.calculateCrc32(chunkBytes)

            onChunkReady(FileChunk(fileId, chunkIndex, totalChunks, chunkBytes, crc))
            buffer.clear()
            chunkIndex++
        }
        channel.close()
        fis.close()
    }

    fun writeChunk(destFile: File, chunk: FileChunk) {
        val fos = FileOutputStream(destFile, true)
        val channel = fos.channel
        val buffer = ByteBuffer.wrap(chunk.data)
        channel.write(buffer)
        channel.close()
        fos.close()
    }
}

