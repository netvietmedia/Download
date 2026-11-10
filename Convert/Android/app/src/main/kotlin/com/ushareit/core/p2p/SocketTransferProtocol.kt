package com.ushareit.core.p2p

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

class SocketTransferProtocol {

    companion object {
        const val MAGIC_HEADER = 0x53484152 // "SHAR"
        const val TYPE_HANDSHAKE = 1
        const val TYPE_FILE_META = 2
        const val TYPE_CHUNK_DATA = 3
        const val TYPE_ACK = 4
        const val TYPE_FINISH = 5
    }

    fun sendHandshake(socket: Socket, deviceName: String, deviceId: String) {
        val dos = DataOutputStream(socket.getOutputStream())
        dos.writeInt(MAGIC_HEADER)
        dos.writeInt(TYPE_HANDSHAKE)
        dos.writeUTF(deviceName)
        dos.writeUTF(deviceId)
        dos.flush()
    }

    fun sendFileMetadata(socket: Socket, fileId: String, fileName: String, fileSize: Long, mimeType: String) {
        val dos = DataOutputStream(socket.getOutputStream())
        dos.writeInt(MAGIC_HEADER)
        dos.writeInt(TYPE_FILE_META)
        dos.writeUTF(fileId)
        dos.writeUTF(fileName)
        dos.writeLong(fileSize)
        dos.writeUTF(mimeType)
        dos.flush()
    }

    fun sendChunk(socket: Socket, chunk: FileChunk) {
        val dos = DataOutputStream(socket.getOutputStream())
        dos.writeInt(MAGIC_HEADER)
        dos.writeInt(TYPE_CHUNK_DATA)
        dos.writeUTF(chunk.fileId)
        dos.writeInt(chunk.chunkIndex)
        dos.writeInt(chunk.totalChunks)
        dos.writeLong(chunk.crc32)
        dos.writeInt(chunk.data.size)
        dos.write(chunk.data)
        dos.flush()
    }
}

