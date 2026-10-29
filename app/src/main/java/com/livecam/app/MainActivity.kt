package com.livecam.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var preview: PreviewView
    private lateinit var info: TextView
    private lateinit var modeButton: Button
    private lateinit var shutter: Button
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var selector = CameraSelector.DEFAULT_BACK_CAMERA
    private var liveRecording = false
    private var mode = 0
    private var currentMomentId = ""
    private var pendingPhotoFile: File? = null

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.CAMERA] == true) startCamera()
        else info.text = "Izin kamera diperlukan"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else permissionRequest.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 16)
            setBackgroundColor(android.graphics.Color.rgb(255, 239, 190))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "LiveCam ✦"; textSize = 25f
            setTextColor(android.graphics.Color.BLACK); setTypeface(null, 1)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        modeButton = Button(this).apply { text = "LIVE PHOTO"; setOnClickListener { switchMode() } }
        header.addView(modeButton)
        root.addView(header)

        preview = PreviewView(this).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        info = TextView(this).apply {
            text = "Siap mengambil momen"; gravity = Gravity.CENTER; textSize = 13f
            setTextColor(android.graphics.Color.rgb(45, 45, 45)); setPadding(4, 12, 4, 12)
        }
        root.addView(info)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        controls.addView(Button(this).apply { text = "GALERI"; setOnClickListener { openGallery() } })
        shutter = Button(this).apply {
            text = "●"; textSize = 24f; setTextColor(-1)
            setOnClickListener { takeMoment() }
        }
        controls.addView(shutter, LinearLayout.LayoutParams(100, 76).apply { setMargins(12, 0, 12, 0) })
        controls.addView(Button(this).apply { text = "FLIP"; setOnClickListener { flipCamera() } })
        root.addView(controls)
        setContentView(root)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val previewUseCase = androidx.camera.core.Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val photoUseCase = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            imageCapture = photoUseCase
            val recorderBuilder = Recorder.Builder().setQualitySelector(
                QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
            )
            val videoUseCase = VideoCapture.withOutput(recorderBuilder.build())
            videoCapture = videoUseCase
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, previewUseCase, photoUseCase, videoUseCase)
                info.text = "Kamera aktif"
            } catch (error: Exception) {
                info.text = "Kamera gagal: " + error.localizedMessage
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchMode() {
        if (liveRecording) {
            liveRecording = false
            stopVideo()
        }
        mode = 1 - mode
        modeButton.text = if (mode == 0) "LIVE PHOTO" else "MOTION PHOTO"
        info.text = if (mode == 0) "Live Photo: shutter kedua untuk menyelesaikan" else "Motion Photo: foto + klip 3 detik"
    }

    private fun takeMoment() {
        if (mode == 0 && liveRecording) {
            liveRecording = false
            shutter.isEnabled = false
            stopVideo()
            info.text = "Menyatukan Live Photo…"
            return
        }
        val capture = imageCapture ?: return
        if (recording != null) return
        shutter.isEnabled = false
        currentMomentId = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val photoFile = File(cacheDir, "photo_$currentMomentId.jpg")
        pendingPhotoFile = photoFile
        val output = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        capture.takePicture(output, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                if (mode == 0) {
                    startClip(0)
                    liveRecording = true
                    info.text = "Merekam Live Photo… tekan shutter lagi untuk selesai"
                    shutter.isEnabled = true
                } else {
                    startClip(3000)
                    info.text = "Merekam Motion Photo…"
                }
            }
            override fun onError(exception: ImageCaptureException) {
                photoFile.delete()
                pendingPhotoFile = null
                info.text = "Foto gagal: " + exception.message
                shutter.isEnabled = true
            }
        })
    }

    private fun startClip(durationMs: Long) {
        val video = videoCapture ?: run {
            pendingPhotoFile?.delete()
            pendingPhotoFile = null
            shutter.isEnabled = true
            info.text = "Perekam video belum siap"
            return
        }
        val clipFile = File(cacheDir, "motion_$currentMomentId.mp4")
        val output = FileOutputOptions.Builder(clipFile).build()
        var pending = video.output.prepareRecording(this, output)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> info.text = if (mode == 0) "Live Photo sedang merekam…" else "Motion Photo sedang merekam…"
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    val photo = pendingPhotoFile
                    pendingPhotoFile = null
                    if (event.hasError() || photo == null || !clipFile.exists()) {
                        photo?.delete()
                        clipFile.delete()
                        info.text = "Gagal membuat Motion Photo"
                    } else {
                        info.text = "Menyatukan foto + video…"
                        Thread {
                            val saved = MotionPhotoWriter.save(this, photo, clipFile, "MOTIONPHOTO_$currentMomentId.jpg")
                            runOnUiThread {
                                info.text = if (saved) "Motion Photo tersimpan sebagai satu foto ✦" else "Gagal menyimpan Motion Photo"
                                shutter.isEnabled = true
                            }
                        }.start()
                    }
                }
            }
        }
        if (durationMs > 0L) {
            android.os.Handler(mainLooper).postDelayed({
                if (recording != null && mode == 1) stopVideo()
            }, durationMs)
        }
    }

    private fun stopVideo() {
        recording?.stop()
    }

    private fun flipCamera() {
        if (liveRecording) {
            liveRecording = false
            stopVideo()
        }
        selector = if (selector == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        startCamera()
    }

    private fun openGallery() {
        startActivity(android.content.Intent(this, GalleryActivity::class.java))
    }

    override fun onDestroy() {
        recording?.stop()
        super.onDestroy()
    }
}
