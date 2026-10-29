package com.livecam.app

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
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

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.CAMERA] == true) startCamera() else info.text = "Izin kamera diperlukan"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else permissionRequest.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16,12,16,16); setBackgroundColor(android.graphics.Color.rgb(255,239,190)) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "LiveCam ✦"; textSize = 25f; setTextColor(android.graphics.Color.BLACK); setTypeface(null,1) }, LinearLayout.LayoutParams(0,-2,1f))
        modeButton = Button(this).apply { text = "LIVE PHOTO"; setOnClickListener { switchMode() } }
        header.addView(modeButton); root.addView(header)
        preview = PreviewView(this).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
        root.addView(preview, LinearLayout.LayoutParams(-1,0,1f))
        info = TextView(this).apply { text = "Siap mengambil momen"; gravity = Gravity.CENTER; textSize = 13f; setTextColor(android.graphics.Color.rgb(45,45,45)); setPadding(4,12,4,12) }
        root.addView(info)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        controls.addView(Button(this).apply { text = "GALERI"; setOnClickListener { openGallery() } })
        shutter = Button(this).apply { text = "●"; textSize = 24f; setTextColor(-1); setOnClickListener { takeMoment() } }
        controls.addView(shutter, LinearLayout.LayoutParams(100,76).apply { setMargins(12,0,12,0) })
        controls.addView(Button(this).apply { text = "FLIP"; setOnClickListener { flipCamera() } }); root.addView(controls)
        setContentView(root)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val previewUseCase = androidx.camera.core.Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val photoUseCase = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            imageCapture = photoUseCase
            val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))).build()
            val videoUseCase = VideoCapture.withOutput(recorder)
            videoCapture = videoUseCase
            try { provider.unbindAll(); provider.bindToLifecycle(this,selector,previewUseCase,photoUseCase,videoUseCase); info.text = "Kamera aktif" }
            catch (e: Exception) { info.text = "Kamera gagal: " + e.localizedMessage }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchMode() {
        if (liveRecording) { stopVideo(); liveRecording = false }
        mode = 1 - mode
        modeButton.text = if (mode == 0) "LIVE PHOTO" else "MOTION PHOTO"
        info.text = if (mode == 0) "Live Photo: tekan shutter untuk mulai dan berhenti" else "Motion Photo: foto + klip 3 detik"
    }

    private fun fileName(prefix: String, ext: String) = prefix + "_" + currentMomentId + ext

    private fun takeMoment() {
        if (mode == 0 && liveRecording) {
            stopVideo()
            liveRecording = false
            info.text = "Live Photo tersimpan ✦"
            return
        }
        val capture = imageCapture ?: return
        shutter.isEnabled = false
        currentMomentId = SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(Date())
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME,fileName("IMG",".jpg")); put(MediaStore.MediaColumns.MIME_TYPE,"image/jpeg"); put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/LiveCam") }
        val output = ImageCapture.OutputFileOptions.Builder(contentResolver,MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values).build()
        capture.takePicture(output,ContextCompat.getMainExecutor(this),object: ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) { Toast.makeText(this@MainActivity,"Foto tersimpan",Toast.LENGTH_SHORT).show(); if (mode == 0) liveAction() else startClip(3000); shutter.isEnabled = true }
            override fun onError(exception: ImageCaptureException) { info.text = "Foto gagal: " + exception.message; shutter.isEnabled = true }
        })
    }

    private fun liveAction() {
        if (!liveRecording) { startClip(0); liveRecording = true; info.text = "Merekam Live Photo… tekan shutter lagi untuk berhenti" }
        else { stopVideo(); liveRecording = false; info.text = "Live clip disimpan" }
    }

    private fun startClip(durationMs: Long) {
        val video = videoCapture ?: return
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME,fileName(if (mode == 0) "LIVE" else "MOTION",".mp4")); put(MediaStore.MediaColumns.MIME_TYPE,"video/mp4"); put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/LiveCam") }
        val output = MediaStoreOutputOptions.Builder(contentResolver,MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
        var pending = video.output.prepareRecording(this,output)
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pending = pending.withAudioEnabled()
        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> info.text = "Sedang merekam klip…"
                is VideoRecordEvent.Finalize -> { info.text = if (event.hasError()) "Video gagal disimpan" else "Video tersimpan • Movies/LiveCam"; recording = null }
            }
        }
        if (durationMs > 0) android.os.Handler(mainLooper).postDelayed({ if (recording != null && mode == 1) stopVideo() },durationMs)
    }

    private fun stopVideo() { recording?.stop(); recording = null }
    private fun flipCamera() { if (liveRecording) { stopVideo(); liveRecording = false }; selector = if (selector == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA; startCamera() }
    private fun openGallery() { startActivity(android.content.Intent(this, GalleryActivity::class.java)) }
    override fun onDestroy() { stopVideo(); super.onDestroy() }
}
