package com.livecam.app

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

object MotionPhotoWriter {
    fun save(context: Context, jpeg: File, mp4: File, displayName: String): Boolean {
        if (!jpeg.exists() || !mp4.exists() || jpeg.length() < 4L || mp4.length() < 16L) return false
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LiveCam")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                writeCombined(jpeg, mp4, output)
            } ?: throw IllegalStateException("Tidak bisa membuka output foto")
            val ready = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            context.contentResolver.update(uri, ready, null, null)
            true
        } catch (error: Exception) {
            context.contentResolver.delete(uri, null, null)
            false
        } finally {
            jpeg.delete()
            mp4.delete()
        }
    }

    private fun writeCombined(jpeg: File, mp4: File, output: OutputStream) {
        val videoLength = mp4.length()
        val xmp = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"><rdf:Description rdf:about="" xmlns:GCamera="http://ns.google.com/photos/1.0/camera/" GCamera:MotionPhoto="1" GCamera:MotionPhotoVersion="1" GCamera:MicroVideoOffset="$videoLength" GCamera:MicroVideoPresentationTimestampUs="0"/></rdf:RDF></x:xmpmeta>"""
        val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)
        val xmpBytes = xmp.toByteArray(Charsets.UTF_8)
        val payload = xmpHeader + xmpBytes
        val segmentLength = payload.size + 2
        require(segmentLength <= 65535) { "XMP segment terlalu besar" }

        FileInputStream(jpeg).use { input ->
            val first = input.read()
            val second = input.read()
            require(first == 0xFF && second == 0xD8) { "File sumber bukan JPEG valid" }
            output.write(0xFF)
            output.write(0xD8)
            output.write(0xFF)
            output.write(0xE1)
            output.write((segmentLength shr 8) and 0xFF)
            output.write(segmentLength and 0xFF)
            output.write(payload)
            input.copyTo(output)
        }
        FileInputStream(mp4).use { it.copyTo(output) }
        output.flush()
    }
}
