package cz.uhk.macroflow

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
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
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

data class TrajectoryPoint(val x: Float, val y: Float, val goingDown: Boolean, val timestamp: Long)

class TrainerFragment : Fragment() {

    private lateinit var viewFinder: PreviewView
    private lateinit var tvStatus: TextView
    private lateinit var btnAction: MaterialButton
    private lateinit var pathOverlay: BarbellPathOverlay
    private var objectDetector: ObjectDetector? = null

    private var isRecording = false
    private var lastKnownY: Float? = null
    private var lastValidPoint: PointF? = null
    private var lastRadius: Float = 60f
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
            color = Color.parseColor("#DDA15E")
            strokeWidth = 6f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
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
            currentTrackedBox?.let { box ->
                canvas.drawCircle(box.centerX(), box.centerY(), box.width() / 2f, paintOutline)
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_trainer, container, false)
        viewFinder = root.findViewById(R.id.viewFinder)
        tvStatus = root.findViewById(R.id.tvDetectionStatus)
        btnAction = root.findViewById(R.id.btnStartTraining)
        val cameraCard = root.findViewById<MaterialCardView>(R.id.cameraCard)

        pathOverlay = BarbellPathOverlay(requireContext())
        cameraCard.addView(
            pathOverlay,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .build()
        objectDetector = ObjectDetection.getClient(options)

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
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            setupCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(requireContext())) { proxy ->
                analyzeImage(proxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("TRAINER", "Chyba kamery", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyzeImage(proxy: ImageProxy) {
        val mediaImage = proxy.image ?: run { proxy.close(); return }
        val rotation = proxy.imageInfo.rotationDegrees

        // ML Kit bere rotaci v úvahu pro bounding boxy
        val image = InputImage.fromMediaImage(mediaImage, rotation)

        // Rozměry snímku z kamery (po rotaci na portrait)
        val imgW = image.width.toFloat()
        val imgH = image.height.toFloat()

        objectDetector?.process(image)
            ?.addOnSuccessListener { results ->
                val bestResult = results
                    .filter { it.boundingBox.width() > 40 && it.boundingBox.height() > 40 }
                    .maxByOrNull { it.boundingBox.width().toLong() * it.boundingBox.height() }

                if (bestResult != null) {
                    framesSinceLastValid = 0
                    tvStatus.text = "OBJEKT ZAMĚŘEN"

                    val box = bestResult.boundingBox

                    // --- RUČNÍ PŘEPOČET S OHLEDEM NA FILL_CENTER ---
                    val viewW = pathOverlay.width.toFloat()
                    val viewH = pathOverlay.height.toFloat()

                    // Výpočet měřítka (jak moc se obraz roztáhl)
                    val scale = Math.max(viewW / imgW, viewH / imgH)

                    // Výpočet ořezu (kolik pixelů je "mimo" obrazovku)
                    val offsetX = (viewW - imgW * scale) / 2f
                    val offsetY = (viewH - imgH * scale) / 2f

                    // Finální souřadnice: (pozice v obrazu * měřítko) + posun ořezu
                    val targetX = (box.centerX() * scale) + offsetX
                    val targetY = (box.centerY() * scale) + offsetY

                    // Vyhlazení (tvých 0.3)
                    val alpha = 0.3f
                    val smoothedX = lastValidPoint?.let { it.x + (targetX - it.x) * alpha } ?: targetX
                    val smoothedY = lastValidPoint?.let { it.y + (targetY - it.y) * alpha } ?: targetY
                    val currentPoint = PointF(smoothedX, smoothedY)

                    // Výpočet poloměru (šířka boxu přepočítaná na pixely view)
                    val targetRadius = (box.width() * scale) / 2f
                    val clampedRadius = targetRadius.coerceIn(25f, 130f)
                    lastRadius += (clampedRadius - lastRadius) * 0.15f

                    pathOverlay.setTrackedBox(
                        RectF(
                            currentPoint.x - lastRadius,
                            currentPoint.y - lastRadius,
                            currentPoint.x + lastRadius,
                            currentPoint.y + lastRadius
                        )
                    )

                    if (isRecording) {
                        val isDown = lastKnownY?.let { currentPoint.y > it } ?: true
                        pathOverlay.addPoint(currentPoint.x, currentPoint.y, isDown)
                        lastKnownY = currentPoint.y
                    }

                    lastValidPoint = currentPoint

                } else {
                    handleDetectionLoss()
                }
            }
            ?.addOnCompleteListener {
                proxy.close()
            }
    }

    private fun handleDetectionLoss() {
        framesSinceLastValid++
        if (framesSinceLastValid > 10) {
            tvStatus.text = "HLEDÁM..."
            pathOverlay.setTrackedBox(null)
        }
        if (framesSinceLastValid > 30) {
            lastValidPoint = null
            lastKnownY = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        objectDetector?.close()
        objectDetector = null
    }
}