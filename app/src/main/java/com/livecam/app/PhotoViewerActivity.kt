package com.livecam.app

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File

class PhotoViewerActivity : AppCompatActivity() {
    private lateinit var photo: ImageView
    private lateinit var video: VideoView
    private lateinit var playButton: Button
    private var tempVideo: File? = null
    private var motionUri: Uri? = null
    private var legacyVideoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        val photoValue = intent.getStringExtra(EXTRA_PHOTO_URI)
        if (photoValue == null) { finish(); return }
        val photoUri = Uri.parse(photoValue)
        val isMotion = intent.getBooleanExtra(EXTRA_IS_MOTION, false)
        motionUri = if (isMotion) photoUri else null
        legacyVideoUri = intent.getStringExtra(EXTRA_VIDEO_URI)?.let { Uri.parse(it) }

        val root = android.widget.FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        photo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageURI(photoUri)
            contentDescription = "Fullscreen photo"
        }
        video = VideoView(this).apply { visibility = View.GONE; setBackgroundColor(Color.BLACK) }
        root.addView(photo, android.widget.FrameLayout.LayoutParams(-1, -1))
        root.addView(video, android.widget.FrameLayout.LayoutParams(-1, -1))

        val close = Button(this).apply {
            text = "✕"; textSize = 18f; setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(255, 207, 66))
            setOnClickListener { finish() }
        }
        val closeParams = android.widget.FrameLayout.LayoutParams(54, 54, Gravity.TOP or Gravity.START)
        closeParams.setMargins(18, 24, 0, 0)
        root.addView(close, closeParams)

        playButton = Button(this).apply {
            text = if (isMotion || legacyVideoUri != null) "▶ PLAY MOMENT" else "PHOTO"
            textSize = 14f; setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(255, 207, 66))
            isEnabled = isMotion || legacyVideoUri != null
            setOnClickListener { playMotion() }
        }
        val playParams = android.widget.FrameLayout.LayoutParams(-2, 56, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        playParams.setMargins(16, 0, 16, 30)
        root.addView(playButton, playParams)
        setContentView(root)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun playMotion() {
        playButton.isEnabled = false
        val legacy = legacyVideoUri
        if (legacy != null) {
            startVideo(legacy)
            return
        }
        val source = motionUri ?: return
        val extracted = File(cacheDir, "viewer_motion_\${System.currentTimeMillis()}.mp4")
        Thread {
            val success = MotionPhotoReader.extractVideo(this, source, extracted)
            runOnUiThread {
                if (success) {
                    tempVideo = extracted
                    startVideo(Uri.fromFile(extracted))
                } else {
                    extracted.delete()
                    playButton.isEnabled = true
                    Toast.makeText(this, "Data video Motion Photo tidak terbaca", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun startVideo(uri: Uri) {
        photo.visibility = View.GONE
        video.visibility = View.VISIBLE
        playButton.text = "■ KEMBALI KE FOTO"
        playButton.isEnabled = true
        playButton.setOnClickListener {
            video.stopPlayback()
            video.visibility = View.GONE
            photo.visibility = View.VISIBLE
            playButton.text = "▶ PLAY MOMENT"
            playButton.setOnClickListener { playMotion() }
        }
        video.setVideoURI(uri)
        video.setOnPreparedListener { player -> player.isLooping = true; video.start() }
        video.setOnErrorListener { _, _, _ ->
            Toast.makeText(this, "Video Motion Photo tidak bisa diputar", Toast.LENGTH_SHORT).show()
            video.visibility = View.GONE
            photo.visibility = View.VISIBLE
            true
        }
    }

    override fun onDestroy() {
        video.stopPlayback()
        tempVideo?.delete()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PHOTO_URI = "photo_uri"
        const val EXTRA_IS_MOTION = "is_motion"
        const val EXTRA_VIDEO_URI = "video_uri"
    }
}
