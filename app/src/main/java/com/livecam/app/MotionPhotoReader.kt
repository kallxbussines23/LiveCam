package com.livecam.app

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream

object MotionPhotoReader {
    fun extractVideo(context: Context, uri: Uri, destination: File): Boolean {
        return try {
            val length = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            if (length <= 0L) return false

            val prefix = ByteArray(minOf(512 * 1024L, length).toInt())
            context.contentResolver.openInputStream(uri)?.use { input ->
                var read = 0
                while (read < prefix.size) {
                    val count = input.read(prefix, read, prefix.size - read)
                    if (count <= 0) break
                    read += count
                }
                val header = String(prefix, 0, read, Charsets.ISO_8859_1)
                val match = Regex("""MicroVideoOffset=["'](\\d+)["']""").find(header) ?: return false
                val videoLength = match.groupValues[1].toLongOrNull() ?: return false
                if (videoLength <= 0L || videoLength >= length) return false
                val start = length - videoLength
                var skipped = 0L
                while (skipped < start) {
                    val amount = input.skip(start - skipped)
                    if (amount <= 0L) {
                        if (input.read() == -1) return false
                        skipped++
                    } else skipped += amount
                }
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var remaining = videoLength
                    while (remaining > 0L) {
                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        remaining -= count
                    }
                    remaining == 0L
                }
            } ?: false
        } catch (_: Exception) {
            destination.delete()
            false
        }
    }
}
