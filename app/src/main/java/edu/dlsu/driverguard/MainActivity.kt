package edu.dlsu.driverguard

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val inferenceExecutor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val modelFile by lazy { File(filesDir, "driver_state.tflite") }
    private val labelsFile by lazy { File(filesDir, "labels.txt") }
    private var detector: YoloDetector? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var backCamera = false
    @Volatile private var cameraGeneration = 0

    private lateinit var preview: PreviewView
    private lateinit var overlay: DetectionOverlay
    private lateinit var status: TextView
    private lateinit var results: TextView
    private var frames = 0
    private var fps = 0.0
    private var fpsStart = System.currentTimeMillis()

    private val selectModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) installLocalFile(uri, modelFile, "model")
    }
    private val selectLabels = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) installLocalFile(uri, labelsFile, "labels")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(createUi())
        if (hasCamera()) startCamera() else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 44)
        reloadModel()
    }

    private fun createUi(): LinearLayout {
        val pad = (12 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        buttons.addView(Button(this).apply {
            text = "Import labels.txt"
            setOnClickListener { selectLabels.launch(arrayOf("text/*", "application/octet-stream", "*/*")) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(this).apply {
            text = "Import .tflite"
            setOnClickListener { selectModel.launch(arrayOf("application/octet-stream", "*/*")) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttons)
        root.addView(SwitchCompat(this).apply {
            text = "Use back camera"
            setOnCheckedChangeListener { _, checked ->
                backCamera = checked
                cameraGeneration++
                this@MainActivity.overlay.mirrorX = !checked
                this@MainActivity.overlay.update(emptyList(), 1, 1)
                results.text = "Switching to ${if (checked) "back" else "front"} camera..."
                if (hasCamera()) startCamera()
            }
        })
        val cameraStack = FrameLayout(this)
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        overlay = DetectionOverlay(this)
        cameraStack.addView(preview, FrameLayout.LayoutParams(-1, -1))
        cameraStack.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(cameraStack, LinearLayout.LayoutParams(-1, 0, 1f))
        status = TextView(this).apply { text = "No trained model loaded"; textSize = 15f }
        results = TextView(this).apply { text = "Camera preview only until the model is imported"; textSize = 14f }
        root.addView(status)
        root.addView(results)
        return root
    }

    private fun installLocalFile(uri: android.net.Uri, dest: File, what: String) {
        status.text = "Importing $what..."
        inferenceExecutor.execute {
            try {
                val tmp = File(filesDir, dest.name + ".partial")
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Cannot open chosen file" }
                    tmp.outputStream().use { input.copyTo(it) }
                }
                require(tmp.length() > 0L) { "Selected file is empty" }
                if (what == "labels") {
                    val names = tmp.readLines().map { it.trim() }.filter { it.isNotEmpty() }
                    require(names.isNotEmpty() && names.size <= 100) { "labels.txt needs one class per line" }
                } else {
                    require(tmp.length() > 100_000) { "Model is unexpectedly small, select a .tflite file" }
                }
                // Stop inference before replacing files to avoid incompatible model/labels pairs.
                detector?.close(); detector = null
                if (dest.exists()) dest.delete()
                check(tmp.renameTo(dest)) { "Could not store imported file" }
                loadModelOnWorker()
            } catch (t: Throwable) {
                runOnUiThread { status.text = "Import failed · ${t.message}" }
            }
        }
    }

    private fun reloadModel() {
        inferenceExecutor.execute { loadModelOnWorker() }
    }

    private fun loadModelOnWorker() {
        try {
            detector?.close(); detector = null
            if (!modelFile.exists() || !labelsFile.exists()) {
                runOnUiThread {
                    status.text = "Import labels.txt and an exported model.tflite"
                    overlay.update(emptyList(), 1, 1)
                }
                return
            }
            val labels = labelsFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
            val candidate = YoloDetector(modelFile, labels)
            detector = candidate
            runOnUiThread { status.text = "Model ready · ${candidate.info}" }
        } catch (t: Throwable) {
            runOnUiThread { status.text = "Model incompatible · ${t.message}" }
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                val selector = if (backCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                require(provider.hasCamera(selector)) { "No ${if (backCamera) "back" else "front"} camera found" }
                val viewfinder = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also { it.setAnalyzer(cameraExecutor, ::analyze) }
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, viewfinder, analysis)
            } catch (t: Throwable) {
                status.text = "Camera error · ${t.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy) {
        val generation = cameraGeneration
        if (!busy.compareAndSet(false, true)) { image.close(); return }
        val bitmap = try {
            val raw = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            if (rotation == 0) raw else Bitmap.createBitmap(
                raw, 0, 0, raw.width, raw.height,
                Matrix().apply { postRotate(rotation.toFloat()) }, true
            ).also { if (it !== raw) raw.recycle() }
        } catch (_: Exception) { null } finally { image.close() }
        if (bitmap == null) { busy.set(false); return }
        inferenceExecutor.execute {
            try {
                val detections = detector?.detect(bitmap).orEmpty()
                val now = System.currentTimeMillis()
                frames++
                if (now - fpsStart >= 1000L) {
                    fps = frames * 1000.0 / (now - fpsStart)
                    fpsStart = now; frames = 0
                }
                val imageWidth = bitmap.width
                val imageHeight = bitmap.height
                val modelActive = detector != null
                runOnUiThread {
                    if (generation != cameraGeneration) return@runOnUiThread
                    overlay.update(detections, imageWidth, imageHeight)
                    results.text = buildString {
                        append("Processed FPS ${"%.1f".format(fps)} · ${if (backCamera) "Back" else "Front"} camera")
                        if (!modelActive) append(" · No model loaded")
                        append("\n")
                        if (detections.isEmpty()) append("No objects detected")
                        else detections.take(8).forEach { append("${it.label} · ${(it.score * 100).toInt()}%\n") }
                    }
                }
            } catch (t: Throwable) {
                runOnUiThread { status.text = "Inference error · ${t.message}" }
            } finally {
                bitmap.recycle()
                busy.set(false)
            }
        }
    }

    private fun hasCamera() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 44) {
            if (hasCamera()) startCamera() else status.text = "Camera permission denied"
        }
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        inferenceExecutor.execute { detector?.close() }
        inferenceExecutor.shutdown()
        super.onDestroy()
    }
}
