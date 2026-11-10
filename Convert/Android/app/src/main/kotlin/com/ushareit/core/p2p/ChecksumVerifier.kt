package com.ushareit.core.p2p

import java.util.zip.CRC32

object ChecksumVerifier {

    external fun computeFastCrc32(buffer: ByteArray, length: Int): Long

    fun calculateCrc32(data: ByteArray): Long {
        return try {
            computeFastCrc32(data, data.size)
        } catch (e: UnsatisfiedLinkError) {
            val crc = CRC32()
            crc.update(data)
            crc.value
        }
    }
}
