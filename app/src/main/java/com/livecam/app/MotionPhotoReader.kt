package com.livecam.app

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

object MotionPhotoReader {
    private fun getFileLength(context: Context, uri: Uri): Long {
        try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    if (index >= 0) {
                        val size = cursor.getLong(index)
                        if (size > 0L) return size
                    }
                }
            }
        } catch (_: Exception) { }

        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    private fun readXmpOffset(context: Context, uri: Uri): Long? {
        val header = context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = ByteArray(1024 * 1024)
            var count = 0
            while (count < bytes.size) {
                val read = input.read(bytes, count, bytes.size - count)
                if (read <= 0) break
                count += read
            }
            String(bytes, 0, count, Charsets.ISO_8859_1)
        } ?: return null

        return Regex("""MicroVideoOffset=["'](\d+)["']""")
            .find(header)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    fun extractVideo(context: Context, uri: Uri, destination: File): Boolean {
        destination.delete()
        return try {
            val totalLength = getFileLength(context, uri)
            val videoLength = readXmpOffset(context, uri) ?: return false
            if (totalLength <= 0L || videoLength <= 16L || videoLength >= totalLength) return false

            val start = totalLength - videoLength
            val success = context.contentResolver.openInputStream(uri)?.use { input ->
                var skipped = 0L
                while (skipped < start) {
                    val amount = input.skip(start - skipped)
                    if (amount > 0L) {
                        skipped += amount
                    } else {
                        if (input.read() == -1) return@use false
                        skipped++
                    }
                }

                BufferedOutputStream(FileOutputStream(destination)).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var remaining = videoLength
                    while (remaining > 0L) {
                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                        remaining -= count
                    }
                    output.flush()
                    remaining == 0L
                }
            } ?: false

            if (!success || destination.length() != videoLength) {
                destination.delete()
                false
            } else {
                true
            }
        } catch (_: Exception) {
            destination.delete()
            false
        }
    }

    // Extract only the JPEG stream up to its EOI marker. Some platform decoders
    // reject a JPEG when an MP4 payload is appended after the image.
    fun extractJpeg(context: Context, uri: Uri, destination: File): Boolean {
        destination.delete()
        return try {
            val success = context.contentResolver.openInputStream(uri)?.use { raw ->
                BufferedInputStream(raw).use { input ->
                    val first = input.read()
                    val second = input.read()
                    if (first != 0xFF || second != 0xD8) return@use false

                    BufferedOutputStream(FileOutputStream(destination)).use { output ->
                        output.write(first)
                        output.write(second)
                        var previous = second
                        var foundEnd = false
                        var total = 2L
                        while (total < 64L * 1024L * 1024L) {
                            val current = input.read()
                            if (current < 0) break
                            output.write(current)
                            total++
                            if (previous == 0xFF && current == 0xD9) {
                                foundEnd = true
                                break
                            }
                            previous = current
                        }
                        output.flush()
                        foundEnd
                    }
                }
            } ?: false

            if (!success || destination.length() < 4L) {
                destination.delete()
                false
            } else true
        } catch (_: Exception) {
            destination.delete()
            false
        }
    }
}
