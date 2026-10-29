package com.livecam.app

import android.app.AlertDialog
import android.content.ContentUris
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class GalleryActivity : AppCompatActivity() {
    private data class MediaItem(val uri: Uri, val name: String, val video: Boolean, val pairedVideo: Uri? = null)
    private val items = mutableListOf<MediaItem>()
    private var visibleItems = listOf<MediaItem>()
    private lateinit var grid: GridView
    private lateinit var count: TextView
    private var filter = "ALL"

    private fun comicBg(color: Int = Color.WHITE) = GradientDrawable().apply {
        setColor(color); cornerRadius = 18f; setStroke(5, Color.BLACK)
    }

    private fun comicButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 11f; setTextColor(Color.BLACK)
        background = comicBg(Color.rgb(255, 207, 66))
        setPadding(5, 2, 5, 2)
        setOnClickListener { action() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadMedia()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 12, 14, 14)
            setBackgroundColor(Color.rgb(255, 239, 190))
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = "MY COMIC GALLERY ✦"; textSize = 20f
            setTextColor(Color.BLACK); setTypeface(null, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(comicButton("← CAMERA") { finish() })
        root.addView(top)
        root.addView(TextView(this).apply {
            text = "Simpan momen, bukan cuma foto!"
            textSize = 12f; setTextColor(Color.DKGRAY); setPadding(2, 4, 2, 12)
        })
        val filters = LinearLayout(this).apply { gravity = Gravity.CENTER }
        listOf("ALL" to "SEMUA", "PHOTO" to "FOTO", "VIDEO" to "VIDEO", "LIVE" to "LIVE").forEach { pair ->
            filters.addView(comicButton(pair.second) { filter = pair.first; applyFilter() },
                LinearLayout.LayoutParams(0, 46, 1f).apply { setMargins(3, 2, 3, 8) })
        }
        root.addView(filters)
        count = TextView(this).apply { setTextColor(Color.BLACK); textSize = 12f; setPadding(2, 2, 2, 8) }
        root.addView(count)
        grid = GridView(this).apply {
            numColumns = 2; verticalSpacing = 12; horizontalSpacing = 10
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        }
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun loadMedia() {
        items.clear()
        val photos = mutableListOf<MediaItem>()
        val clips = mutableListOf<MediaItem>()
        val imageProjection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageProjection,
            MediaStore.Images.Media.RELATIVE_PATH + "=?", arrayOf("Pictures/LiveCam/"),
            MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idCol))
                photos.add(MediaItem(uri, cursor.getString(nameCol) ?: "Photo", false))
            }
        }
        val videoProjection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
        contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoProjection,
            MediaStore.Video.Media.RELATIVE_PATH + "=?", arrayOf("Movies/LiveCam/"),
            MediaStore.Video.Media.DATE_ADDED + " DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idCol))
                clips.add(MediaItem(uri, cursor.getString(nameCol) ?: "Video", true))
            }
        }
        val clipsByKey = clips.associateBy { momentKey(it.name) }
        photos.forEach { photo -> items.add(photo.copy(pairedVideo = clipsByKey[momentKey(photo.name)]?.uri)) }
        items.addAll(clips)
        applyFilter()
    }

    private fun momentKey(name: String) = name.substringAfter("_", name).substringBeforeLast(".")

    private fun applyFilter() {
        visibleItems = when (filter) {
            "PHOTO" -> items.filter { !it.video }
            "VIDEO" -> items.filter { it.video }
            "LIVE" -> items.filter { !it.video && it.pairedVideo != null }
            else -> items
        }
        count.text = visibleItems.size.toString() + " MEDIA • LIVE PHOTO DITANDAI ✦"
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = visibleItems.size
            override fun getItem(position: Int) = visibleItems[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val item = getItem(position)
                val tile = LinearLayout(this@GalleryActivity).apply {
                    orientation = LinearLayout.VERTICAL; background = comicBg()
                    setPadding(6, 6, 6, 6)
                }
                val thumb = ImageView(this@GalleryActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(-1, 132)
                    try { setImageBitmap(contentResolver.loadThumbnail(item.uri, android.util.Size(360, 360), null)) }
                    catch (_: Exception) { setBackgroundColor(Color.LTGRAY) }
                }
                tile.addView(thumb)
                tile.addView(TextView(this@GalleryActivity).apply {
                    text = (if (item.video) "▶ " else if (item.pairedVideo != null) "✦ LIVE  " else "▣  ") + item.name
                    textSize = 10f; setTextColor(Color.BLACK); setPadding(4, 7, 4, 5)
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                })
                tile.setOnClickListener { openViewer(item) }
                return tile
            }
        }
    }

    private fun openViewer(item: MediaItem) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(14, 14, 14, 14)
            setBackgroundColor(Color.rgb(255, 239, 190))
        }
        val preview = ImageView(this).apply {
            adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER
            try { setImageBitmap(contentResolver.loadThumbnail(item.uri, android.util.Size(900, 900), null)) }
            catch (_: Exception) {}
        }
        val video = VideoView(this).apply { visibility = View.GONE }
        panel.addView(preview, LinearLayout.LayoutParams(-1, 250))
        panel.addView(TextView(this).apply {
            text = if (item.pairedVideo != null) "✦ LIVE MOMENT — tekan PLAY untuk melihat gerakannya" else item.name
            setTextColor(Color.BLACK); setPadding(0, 10, 0, 10)
        })
        panel.addView(video, LinearLayout.LayoutParams(-1, 220))
        val dialog = AlertDialog.Builder(this).setView(panel).setNegativeButton("TUTUP", null).create()
        val playable = item.pairedVideo ?: if (item.video) item.uri else null
        if (playable != null) {
            panel.addView(comicButton("▶ PLAY MOMENT") {
                preview.visibility = View.GONE; video.visibility = View.VISIBLE
                video.setVideoURI(playable)
                video.setOnPreparedListener { player -> player.isLooping = true; video.start() }
            })
        }
        dialog.setOnDismissListener { video.stopPlayback() }
        dialog.show()
    }
}
