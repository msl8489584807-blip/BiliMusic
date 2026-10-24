package com.bilimusic.player.data

import java.io.InputStream
import java.io.ByteArrayOutputStream

fun InputStream.readBounded(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count == -1) break
        require(output.size() + count <= limit) { "文件过大，最多 ${limit / 1_000_000} MB" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
