package com.livecam.app

import android.content.Context
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile

object MotionPhotoReader {
    private fun snapshot(context: Context, uri: Uri): File? {
        val file = File.createTempFile("motion_source_", ".bin", context.cacheDir)
        return try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            } ?: false
            if (copied && file.length() > 32L) file else {
                file.delete()
                null
            }
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    private fun readXmpOffset(file: File): Long? {
        return try {
            FileInputStream(file).use { input ->
                val bytes = ByteArray(1024 * 1024)
                var count = 0
                while (count < bytes.size) {
                    val read = input.read(bytes, count, bytes.size - count)
                    if (read <= 0) break
                    count += read
                }
                val header = String(bytes, 0, count, Charsets.ISO_8859_1)
                Regex("""MicroVideoOffset=["'](\\d+)["']""")
                    .find(header)?.groupValues?.getOrNull(1)?.toLongOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun extractVideo(context: Context, uri: Uri, destination: File): Boolean {
        destination.delete()
        val source = snapshot(context, uri) ?: return false
        return try {
            val videoLength = readXmpOffset(source) ?: return false
            val totalLength = source.length()
            if (videoLength <= 16L || videoLength >= totalLength) return false

            RandomAccessFile(source, "r").use { input ->
                val start = totalLength - videoLength
                input.seek(start + 4L)
                val brand = ByteArray(4)
                if (input.read(brand) != 4 || String(brand, Charsets.US_ASCII) != "ftyp") {
                    return false
                }

                input.seek(start)
                BufferedOutputStream(FileOutputStream(destination)).use { output ->
                    copyRange(input, output, start, totalLength)
                }
            }
            destination.length() == videoLength
        } catch (_: Exception) {
            destination.delete()
            false
        } finally {
            source.delete()
        }
    }

    // Parse JPEG marker segments instead of searching raw bytes for FF D9.
    // EXIF may contain an embedded thumbnail JPEG with its own EOI marker.
    fun extractJpeg(context: Context, uri: Uri, destination: File): Boolean {
        destination.delete()
        val source = snapshot(context, uri) ?: return false
        return try {
            RandomAccessFile(source, "r").use { input ->
                if (input.length() < 4L || input.readUnsignedByte() != 0xFF ||
                    input.readUnsignedByte() != 0xD8) return false

                BufferedOutputStream(FileOutputStream(destination)).use { output ->
                    output.write(0xFF)
                    output.write(0xD8)
                    var copiedUntil = 2L
                    var position = 2L
                    var foundEnd = false

                    while (position < input.length()) {
                        input.seek(position)
                        val markerStart = position
                        if (input.readUnsignedByte() != 0xFF) break

                        var marker = input.readUnsignedByte()
                        while (marker == 0xFF) marker = input.readUnsignedByte()
                        val afterMarker = input.filePointer

                        if (marker == 0xD9) {
                            copyRange(input, output, copiedUntil, input.filePointer)
                            foundEnd = true
                            break
                        }

                        val segmentEnd = if (
                            marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7
                        ) {
                            afterMarker
                        } else {
                            if (afterMarker + 2L > input.length()) break
                            input.seek(afterMarker)
                            val segmentLength = input.readUnsignedShort()
                            if (segmentLength < 2) break
                            afterMarker + segmentLength.toLong()
                        }

                        if (segmentEnd > input.length()) break

                        if (marker == 0xDA) {
                            input.seek(segmentEnd)
                            var endPosition = -1L
                            while (input.filePointer + 1L < input.length()) {
                                val value = input.readUnsignedByte()
                                if (value != 0xFF) continue
                                var next = input.readUnsignedByte()
                                while (next == 0xFF && input.filePointer < input.length()) {
                                    next = input.readUnsignedByte()
                                }
                                if (next == 0xD9) {
                                    endPosition = input.filePointer
                                    break
                                }
                                // 0x00 is byte-stuffed data; restart markers are valid
                                // inside the compressed scan and are not end-of-image.
                            }
                            if (endPosition >= 0L) {
                                copyRange(input, output, copiedUntil, endPosition)
                                foundEnd = true
                            }
                            break
                        } else {
                            copyRange(input, output, copiedUntil, segmentEnd)
                            copiedUntil = segmentEnd
                            position = segmentEnd
                        }
                    }
                    output.flush()
                    foundEnd
                }
            } && destination.length() > 4L
        } catch (_: Exception) {
            false
        } finally {
            source.delete()
            if (!destination.exists() || destination.length() <= 4L) destination.delete()
        }
    }

    private fun copyRange(
        input: RandomAccessFile,
        output: OutputStream,
        start: Long,
        end: Long
    ) {
        input.seek(start)
        var remaining = end - start
        val buffer = ByteArray(32 * 1024)
        while (remaining > 0L) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count <= 0) throw IllegalStateException("File Motion Photo terpotong")
            output.write(buffer, 0, count)
            remaining -= count
        }
    }
}
