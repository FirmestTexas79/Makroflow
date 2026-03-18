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
            // Kreslíme sledovací kolečko
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_trainer, container, false)
        viewFinder = root.findViewById(R.id.viewFinder)
        tvStatus = root.findViewById(R.id.tvDetectionStatus)
        btnAction = root.findViewById(R.id.btnStartTraining)
        val cameraCard = root.findViewById<MaterialCardView>(R.id.cameraCard)

        pathOverlay = BarbellPathOverlay(requireContext())
        cameraCard.addView(pathOverlay)

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
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(viewFinder.surfaceProvider)
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(requireContext())) { proxy ->
                analyzeImage(proxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
            } catch (e: Exception) { Log.e("TRAINER", "Chyba kamery", e) }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyzeImage(proxy: ImageProxy) {
        val mediaImage = proxy.image ?: return
        val rotation = proxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotation)

        objectDetector?.process(image)
            ?.addOnSuccessListener { results ->
                val bestResult = results.firstOrNull { it.boundingBox.width() > 50 }

                if (bestResult != null) {
                    framesSinceLastValid = 0
                    tvStatus.text = "OBJEKT ZAMĚŘEN"

                    val box = bestResult.boundingBox
                    val imgW = mediaImage.width.toFloat()
                    val imgH = mediaImage.height.toFloat()

                    // 1. VÝPOČET MĚŘÍTKA A OFFSETU (Toto opraví posun doprava dolů)
                    // PreviewView v 'fillCenter' režimu obraz roztahuje.
                    // Musíme zjistit, o kolik je obraz posunutý.
                    val viewW = pathOverlay.width.toFloat()
                    val viewH = pathOverlay.height.toFloat()

                    // Poměr stran (pro 90/270 rotaci prohazujeme imgW a imgH)
                    val scaleX = viewW / imgH
                    val scaleY = viewH / imgW
                    val scale = Math.max(scaleX, scaleY)

                    // Offsety vyrovnají to, co "přečuhuje" mimo obrazovku
                    val offsetX = (viewW - imgH * scale) / 2f
                    val offsetY = (viewH - imgW * scale) / 2f

                    // 2. Mapování se započtením offsetu
                    // ... uvnitř onSuccessListener, tam kde máš ty výpočty ...

// 2. Mapování se započtením offsetu
                    val relX = box.centerX().toFloat() / imgW
                    val relY = box.centerY().toFloat() / imgH

                    val targetX: Float
                    val targetY: Float
                    val rawRadius: Float



                    if (rotation == 90 || rotation == 270) {
                        // Přidáváme k X (posun doprava), ubíráme z Y (posun nahoru)
                        targetX = relX * pathOverlay.width + (offsetX * 1.2f)
                        targetY = relY * pathOverlay.height + (offsetY * 0.8f)

                        rawRadius = ((box.height().toFloat() / imgW) * pathOverlay.width / 2f) * scale
                    } else {
                        targetX = relX * pathOverlay.width + offsetX
                        targetY = relY * pathOverlay.height + offsetY
                        rawRadius = ((box.width().toFloat() / imgW) * pathOverlay.width / 2f) * scale
                    }

                    // 3. EXTRÉMNÍ VYHLAZENÍ (Smoothing) - tvoje původní 0.25f
                    val smoothedX = if (lastValidPoint == null) targetX else lastValidPoint!!.x + (targetX - lastValidPoint!!.x) * 0.25f
                    val smoothedY = if (lastValidPoint == null) targetY else lastValidPoint!!.y + (targetY - lastValidPoint!!.y) * 0.25f
                    val currentPoint = PointF(smoothedX, smoothedY)

                    // 4. FIXACE VELIKOSTI
                    val finalRadius = if (lastValidPoint == null) rawRadius else {
                        rawRadius.coerceIn(40f, 150f)
                    }

                    if (isRecording) {
                        val isDown = if (lastKnownY != null) currentPoint.y > lastKnownY!! else true
                        pathOverlay.addPoint(currentPoint.x, currentPoint.y, isDown)

                        pathOverlay.setTrackedBox(RectF(
                            currentPoint.x - finalRadius,
                            currentPoint.y - finalRadius,
                            currentPoint.x + finalRadius,
                            currentPoint.y + finalRadius
                        ))

                        lastKnownY = currentPoint.y
                    }
                    lastValidPoint = currentPoint
                } else {
                    handleDetectionLoss()
                }
            }
            ?.addOnCompleteListener { proxy.close() }
    }
    private fun handleDetectionLoss() {
        framesSinceLastValid++
        // --- KLÍČOVÝ RESET TRAJEKTORIE ---
        // Když se objekt ztratí (třeba na 10 framů), resetujeme trajektorii.
        // Tím zabráníme té "dlouhé čáře" přes obrazovku, když se objekt znova najde.
        if (framesSinceLastValid > 10) {
            tvStatus.text = "HLEDÁM..."
            tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BC6C25"))
            pathOverlay.setTrackedBox(null)
            lastValidPoint = null // Tím vynutíme bezpečné vyhlazení v příštím framu
        }
    }
}