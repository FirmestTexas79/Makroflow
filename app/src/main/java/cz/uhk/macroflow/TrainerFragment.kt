package cz.uhk.macroflow

import android.Manifest
import android.annotation.SuppressLint
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

class TrainerFragment : Fragment() {

    private lateinit var viewFinder: PreviewView
    private lateinit var tvStatus: TextView
    private lateinit var btnAction: MaterialButton
    private lateinit var graphicOverlay: GraphicOverlay
    private var objectDetector: ObjectDetector? = null

    private var isRecording = false
    private var lastKnownY: Float? = null
    private var lastValidPoint: PointF? = null
    private var framesSinceLastValid: Int = 0
    private var lastStableRadius: Float = 0f

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) setupCamera()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_trainer, container, false)
        viewFinder = root.findViewById(R.id.viewFinder)
        tvStatus = root.findViewById(R.id.tvDetectionStatus)
        btnAction = root.findViewById(R.id.btnStartTraining)
        graphicOverlay = root.findViewById(R.id.graphicOverlay)

        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .build()
        objectDetector = ObjectDetection.getClient(options)

        btnAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#606C38"))

        btnAction.setOnClickListener {
            isRecording = !isRecording
            if (isRecording) {
                btnAction.text = "STOP"
                btnAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BC6C25"))
                graphicOverlay.reset()
                lastKnownY = null
                lastValidPoint = null
                lastStableRadius = 0f
            } else {
                btnAction.text = "ZAČÍT TRÉNINK"
                btnAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#606C38"))
            }
        }

        root.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
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
                    viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("TRAINER", "Chyba kamery", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyzeImage(proxy: ImageProxy) {
        val mediaImage = proxy.image ?: run { proxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)

        // Předáme rozměry zdroje do overlay
        graphicOverlay.setPreviewSize(image.width, image.height)

        objectDetector?.process(image)
            ?.addOnSuccessListener { results ->
                val bestResult = results.filter {
                    val ratio = it.boundingBox.width().toFloat() / it.boundingBox.height().toFloat()
                    ratio in 0.90f..1.15f && it.boundingBox.width() > 50
                }.maxByOrNull { it.boundingBox.width() }

                if (bestResult != null) {
                    val box = bestResult.boundingBox
                    val alpha = 0.85f

                    val smoothedX = lastValidPoint?.let { it.x + (box.centerX() - it.x) * alpha } ?: box.centerX().toFloat()
                    val smoothedY = lastValidPoint?.let { it.y + (box.centerY() - it.y) * alpha } ?: box.centerY().toFloat()

                    val currentRadius = (box.width().toFloat() + box.height().toFloat()) / 4f
                    if (lastStableRadius == 0f) lastStableRadius = currentRadius
                    lastStableRadius += (currentRadius - lastStableRadius) * 0.2f

                    val rawBox = RectF(
                        smoothedX - lastStableRadius, smoothedY - lastStableRadius,
                        smoothedX + lastStableRadius, smoothedY + lastStableRadius
                    )

                    graphicOverlay.setTrackedBox(rawBox)

                    if (isRecording) {
                        val isDown = lastKnownY?.let { smoothedY > it } ?: true
                        graphicOverlay.addPoint(smoothedX, smoothedY, isDown)
                        lastKnownY = smoothedY
                    }
                    lastValidPoint = PointF(smoothedX, smoothedY)
                    framesSinceLastValid = 0
                    tvStatus.text = "KOTOUČ ZAMĚŘEN"
                } else {
                    handleLoss()
                }
            }
            ?.addOnCompleteListener { proxy.close() }
    }

    private fun handleLoss() {
        framesSinceLastValid++
        if (framesSinceLastValid > 15) {
            tvStatus.text = "HLEDÁM..."
            graphicOverlay.setTrackedBox(null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        objectDetector?.close()
    }
}