package cz.uhk.macroflow

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

data class TrajectoryPoint(val x: Float, val y: Float, val goingDown: Boolean, val timestamp: Long)

class TrainerFragment : Fragment() {

    private lateinit var viewFinder: PreviewView
    private lateinit var tvStatus: TextView
    private lateinit var btnAction: MaterialButton
    private lateinit var pathOverlay: BarbellPathOverlay

    private var isRecording = false
    private var lastKnownY: Float? = null
    private var lastValidPoint: PointF? = null
    private var framesSinceLastValid: Int = 0

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) setupCamera()
    }

    inner class BarbellPathOverlay(context: Context) : View(context) {
        private val points = mutableListOf<TrajectoryPoint>()
        private var currentTrackedBox: RectF? = null

        private val paintDown = Paint().apply {
            color = Color.parseColor("#BC6C25")
            strokeWidth = 12f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }
        private val paintUp = Paint().apply {
            color = Color.parseColor("#606C38")
            strokeWidth = 12f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }
        private val paintOutline = Paint().apply {
            color = Color.parseColor("#606C38")
            strokeWidth = 8f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            isAntiAlias = true
        }

        fun addPoint(x: Float, y: Float, isDown: Boolean) {
            points.add(TrajectoryPoint(x, y, isDown, System.currentTimeMillis()))
            invalidate()
        }

        fun setTrackedBox(rect: RectF?) {
            currentTrackedBox = rect
            invalidate()
        }

        fun reset() {
            points.clear()
            currentTrackedBox = null
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (isRecording && currentTrackedBox != null) {
                val box = currentTrackedBox!!
                canvas.drawCircle(box.centerX(), box.centerY(), (box.width() / 2f) + 10f, paintOutline)
            }
            if (points.size < 2) return
            for (i in 1 until points.size) {
                val p1 = points[i - 1]
                val p2 = points[i]
                if (p2.timestamp - p1.timestamp > 300) continue
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, if (p2.goingDown) paintDown else paintUp)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_trainer, container, false)
        viewFinder = root.findViewById(R.id.viewFinder)
        viewFinder.scaleType = PreviewView.ScaleType.FILL_CENTER

        tvStatus = root.findViewById(R.id.tvDetectionStatus)
        btnAction = root.findViewById(R.id.btnStartTraining)
        val cameraCard = root.findViewById<MaterialCardView>(R.id.cameraCard)

        pathOverlay = BarbellPathOverlay(requireContext())
        cameraCard.addView(pathOverlay)

        root.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        btnAction.setOnClickListener {
            isRecording = !isRecording
            if (isRecording) {
                btnAction.text = "STOP"
                btnAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BC6C25"))
                pathOverlay.reset()
                lastKnownY = null
                lastValidPoint = null
            } else {
                btnAction.text = "ZAČÍT TRÉNINK"
                btnAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DDA15E"))
            }
        }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) setupCamera()
        else requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // ... (imports stejné)

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Kvalitu necháme na kameře, nepoužíváme setTargetResolution
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(viewFinder.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                // Zrušili jsme TargetResolution pro vyšší kvalitu
                .build()

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(requireContext())) { proxy ->
                analyzeImage(proxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
            } catch (e: Exception) { e.printStackTrace() }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyzeImage(proxy: ImageProxy) {
        val mediaImage = proxy.image ?: return
        val rotationDegrees = proxy.imageInfo.rotationDegrees

        // Rozměry zdroje (z kamery)
        val imgW = if (rotationDegrees == 90 || rotationDegrees == 270) mediaImage.height.toFloat() else mediaImage.width.toFloat()
        val imgH = if (rotationDegrees == 90 || rotationDegrees == 270) mediaImage.width.toFloat() else mediaImage.height.toFloat()

        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .build()

        ObjectDetection.getClient(options).process(image)
            .addOnSuccessListener { results ->
                val bestResult = results.firstOrNull { obj ->
                    val box = obj.boundingBox
                    val aspectRatio = box.width().toFloat() / box.height().toFloat()
                    aspectRatio in 0.7f..1.3f && box.width() > 50
                }

                if (bestResult != null) {
                    framesSinceLastValid = 0
                    tvStatus.text = "OBJEKT ZAMĚŘEN"
                    tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#606C38"))

                    if (isRecording) {
                        val box = bestResult.boundingBox

                        // --- FINÁLNÍ MATEMATIKA PRO VYCENTROVÁNÍ ---

                        // 1. Kde je objekt na kameře v procentech (0.0 až 1.0)
                        val relX = box.centerX().toFloat() / (if (rotationDegrees % 180 != 0) mediaImage.height else mediaImage.width).toFloat()
                        val relY = box.centerY().toFloat() / (if (rotationDegrees % 180 != 0) mediaImage.width else mediaImage.height).toFloat()

                        // 2. Poměry stran okna (3:4) vs obrazu kamery (16:9)
                        val viewAspectRatio = pathOverlay.width.toFloat() / pathOverlay.height.toFloat()
                        val imageAspectRatio = imgW / imgH

                        var finalX: Float
                        var finalY: Float

                        // Logika FILL_CENTER: ořezáváme šířku, výška sedí
                        val scale = pathOverlay.height.toFloat() / imgH
                        val visibleWidthOnImage = pathOverlay.width.toFloat() / scale
                        val startX = (imgW - visibleWidthOnImage) / 2f

                        // Přepočet X: vezmeme centerX v pixelech, odečteme začátek viditelné zóny a vynásobíme měřítkem
                        val currentRawX = (box.centerX().toFloat() - startX) * scale
                        val currentRawY = relY * pathOverlay.height

                        // Smoothing (Lerp) - aby to neskákalo
                        val lerpX = if (lastValidPoint == null) currentRawX else lastValidPoint!!.x + (currentRawX - lastValidPoint!!.x) * 0.4f
                        val lerpY = if (lastValidPoint == null) currentRawY else lastValidPoint!!.y + (currentRawY - lastValidPoint!!.y) * 0.4f

                        val currentPoint = PointF(lerpX, lerpY)
                        val isDown = if (lastKnownY != null) currentPoint.y > lastKnownY!! else true

                        pathOverlay.addPoint(currentPoint.x, currentPoint.y, isDown)

                        // Radius outline kruhu (škálovaný podle výšky)
                        val radius = (box.width().toFloat() * scale) / 2f
                        pathOverlay.setTrackedBox(RectF(currentPoint.x - radius, currentPoint.y - radius, currentPoint.x + radius, currentPoint.y + radius))

                        lastValidPoint = currentPoint
                        lastKnownY = currentPoint.y
                    }
                } else {
                    handleDetectionLoss()
                }
            }
            .addOnCompleteListener { proxy.close() }
    }

    private fun handleDetectionLoss() {
        if (isRecording && lastValidPoint != null && framesSinceLastValid < 5) {
            framesSinceLastValid++
            tvStatus.text = "DRŽÍM LOCK..."
            pathOverlay.addPoint(lastValidPoint!!.x, lastValidPoint!!.y, lastKnownY!! > (lastValidPoint!!.y - 1))
        } else {
            framesSinceLastValid++
            tvStatus.text = "HLEDÁM..."
            tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BC6C25"))
            pathOverlay.setTrackedBox(null)
            if (framesSinceLastValid > 5) lastValidPoint = null
        }
    }
}