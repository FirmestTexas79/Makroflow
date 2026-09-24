package cz.uhk.macroflow.training

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.training.analysis.Lift
import cz.uhk.macroflow.training.analysis.PlateTracker
import cz.uhk.macroflow.training.analysis.RepAnalyzer
import cz.uhk.macroflow.training.analysis.Sample
import cz.uhk.macroflow.training.analysis.SetDetector
import cz.uhk.macroflow.training.analysis.SetSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Sledování činky kamerou.
 *
 * Režim AKTIVNÍ: telefon opřený na zemi, série se spustí pohybem a ukončí klidem (SetDetector),
 * po sérii se hned ukáže rozbor opakování a uloží se do DB (→ PDF výpis pro trenéra).
 * Režim VYPNUTO: kamera běží a kotouč jde zamknout, ale nic se nezaznamenává.
 */
class TrainerFragment : Fragment() {

    private lateinit var viewFinder: PreviewView
    private lateinit var tvStatus: TextView
    private lateinit var tvRepCount: TextView
    private lateinit var tvLastRep: TextView
    private lateinit var btnMode: MaterialButton
    private lateinit var btnEndSet: MaterialButton
    private lateinit var etLoad: EditText
    private lateinit var etPlate: EditText
    private lateinit var liftChips: LinearLayout
    private lateinit var graphicOverlay: GraphicOverlay

    private var objectDetector: ObjectDetector? = null
    private lateinit var analysisExecutor: ExecutorService

    private val tracker = PlateTracker()
    private var detector = SetDetector()
    private var lastDetections: List<PlateTracker.Detection> = emptyList()
    private var lastFrameT = 0L

    private var isActive = false
    private var lift = Lift.SQUAT
    private var setStartedAt = 0L
    private var lastLiveAnalysis = 0L
    private var lastPathY: Float? = null

    private val prefs by lazy { requireContext().getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE) }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) setupCamera() else tvStatus.text = "BEZ KAMERY TO NEPŮJDE" }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_trainer, container, false)
        viewFinder = root.findViewById(R.id.viewFinder)
        tvStatus = root.findViewById(R.id.tvDetectionStatus)
        tvRepCount = root.findViewById(R.id.tvRepCount)
        tvLastRep = root.findViewById(R.id.tvLastRep)
        btnMode = root.findViewById(R.id.btnStartTraining)
        btnEndSet = root.findViewById(R.id.btnEndSet)
        etLoad = root.findViewById(R.id.etLoad)
        etPlate = root.findViewById(R.id.etPlate)
        liftChips = root.findViewById(R.id.liftChips)
        graphicOverlay = root.findViewById(R.id.graphicOverlay)

        analysisExecutor = Executors.newSingleThreadExecutor()
        // Víc objektů najednou: jinak detektor vrací jen ten nejvýraznější (často člověka) a kotouč chybí
        objectDetector = ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .build()
        )

        lift = Lift.from(prefs.getString("tracker_lift", Lift.SQUAT.name))
        etPlate.setText(prefs.getString("tracker_plate_cm", "45"))
        buildLiftChips()
        applyMode(false)

        btnMode.setOnClickListener { applyMode(!isActive) }
        btnEndSet.setOnClickListener { detector.finish()?.let { onSetFinished(it) } }
        root.findViewById<ImageButton>(R.id.btnBack).setOnClickListener { parentFragmentManager.popBackStack() }

        // Ťuknutím na kotouč se sledování zamkne na něj
        graphicOverlay.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_UP) {
                val ok = tracker.lockAt(graphicOverlay.toImageX(e.x), graphicOverlay.toImageY(e.y), lastDetections, lastFrameT)
                tvStatus.text = if (ok) "KOTOUČ ZAMČEN" else "TADY KOTOUČ NEVIDÍM"
                v.performClick()
            }
            true
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

    // ── UI ──────────────────────────────────────────────────────────────────

    private fun buildLiftChips() {
        val dp = resources.displayMetrics.density
        liftChips.removeAllViews()
        Lift.entries.forEach { l ->
            liftChips.addView(TextView(requireContext()).apply {
                text = l.label
                textSize = 13f
                setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
                val selected = l == lift
                setTextColor(Color.parseColor(if (selected) "#FEFAE0" else "#283618"))
                background = GradientDrawable().apply {
                    cornerRadius = 18 * dp
                    setColor(Color.parseColor(if (selected) "#283618" else "#CCFEFAE0"))
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { marginEnd = (8 * dp).toInt(); topMargin = (8 * dp).toInt() }
                setOnClickListener {
                    if (detector.state == SetDetector.State.ACTIVE) {
                        Toast.makeText(requireContext(), "Cvik změníš po dokončení série", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    lift = l
                    prefs.edit().putString("tracker_lift", l.name).apply()
                    buildLiftChips()
                }
            })
        }
    }

    private fun applyMode(active: Boolean) {
        if (!active && detector.state == SetDetector.State.ACTIVE) detector.finish()?.let { onSetFinished(it) }
        isActive = active
        detector = SetDetector(plateDiameterCm = plateDiameter())
        btnMode.text = if (active) "REŽIM: AKTIVNÍ" else "REŽIM: VYPNUTO"
        btnMode.backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (active) "#BC6C25" else "#606C38"))
        btnEndSet.visibility = View.GONE
        resetLive()
        // Telefon leží na zemi – obrazovka nesmí zhasnout uprostřed série
        activity?.window?.let { w ->
            if (active) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        prefs.edit().putString("tracker_plate_cm", etPlate.text.toString()).apply()
    }

    private fun resetLive() {
        tvRepCount.text = "0"
        tvLastRep.text = if (isActive) "čekám na pohyb…" else "opakování"
    }

    private fun plateDiameter(): Double =
        etPlate.text.toString().replace(",", ".").toDoubleOrNull()?.takeIf { it in 20.0..60.0 }
            ?: RepAnalyzer.DEFAULT_PLATE_DIAMETER_CM

    private fun loadKg(): Double? = etLoad.text.toString().replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 }

    // ── Kamera a detekce ────────────────────────────────────────────────────

    private fun setupCamera() {
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(viewFinder.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            // Analýza mimo hlavní vlákno – UI se nezasekává a snímky se nezahazují
            analysis.setAnalyzer(analysisExecutor) { proxy -> analyzeImage(proxy) }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                Log.e("TRAINER", "Chyba kamery", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyzeImage(proxy: ImageProxy) {
        val media = proxy.image ?: run { proxy.close(); return }
        val rotation = proxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(media, rotation)
        val upright = if (rotation == 90 || rotation == 270) image.height to image.width else image.width to image.height
        val tMs = System.currentTimeMillis()

        objectDetector?.process(image)
            ?.addOnSuccessListener { results ->
                if (!isAdded) return@addOnSuccessListener
                graphicOverlay.setPreviewSize(upright.first, upright.second)
                val dets = results.map {
                    val b = it.boundingBox
                    PlateTracker.Detection(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), it.trackingId)
                }
                onDetections(dets, tMs)
            }
            ?.addOnCompleteListener { proxy.close() }
    }

    private fun onDetections(dets: List<PlateTracker.Detection>, tMs: Long) {
        lastDetections = dets
        lastFrameT = tMs
        val track = tracker.update(dets, tMs)

        if (track == null) {
            graphicOverlay.setTrackedBox(null)
            tvStatus.text = "HLEDÁM KOTOUČ…"
            if (isActive) detector.onLost(tMs)?.let { onSetFinished(it) }
            return
        }

        graphicOverlay.setTrackedBox(RectF(track.x - track.radius, track.y - track.radius, track.x + track.radius, track.y + track.radius))
        tvStatus.text = if (track.measured) "KOTOUČ ZAMĚŘEN" else "KOTOUČ ZAMĚŘEN · PREDIKCE"
        if (!isActive || !track.measured) return

        val wasIdle = detector.state == SetDetector.State.IDLE
        val finished = detector.onSample(Sample(tMs, track.x, track.y, track.radius))
        if (wasIdle && detector.state == SetDetector.State.ACTIVE) {
            setStartedAt = tMs
            lastPathY = null
            graphicOverlay.reset()
            btnEndSet.visibility = View.VISIBLE
            tvLastRep.text = "série běží"
        }
        if (detector.state == SetDetector.State.ACTIVE) {
            graphicOverlay.addPoint(track.x, track.y, isDown = lastPathY?.let { track.y > it } ?: true)
            lastPathY = track.y
            updateLive(tMs)
        }
        finished?.let { onSetFinished(it) }
    }

    /** Průběžný počet repů (analýza je O(n), dvakrát za sekundu stačí). */
    private fun updateLive(tMs: Long) {
        if (tMs - lastLiveAnalysis < 500) return
        lastLiveAnalysis = tMs
        val s = RepAnalyzer.analyze(detector.currentSet(), lift, plateDiameter()) ?: return
        tvRepCount.text = s.repCount.toString()
        val r = s.reps.last()
        tvLastRep.text = String.format(Locale("cs", "CZ"), "posl.: %.0f cm · %.2f m/s", r.romCm, r.meanConcentricVelocity)
    }

    // ── Konec série ─────────────────────────────────────────────────────────

    private fun onSetFinished(samples: List<Sample>) {
        btnEndSet.visibility = View.GONE
        val diameter = plateDiameter()
        val summary = RepAnalyzer.analyze(samples, lift, diameter)
        if (summary == null) {
            Toast.makeText(requireContext(), "Série nerozpoznána – zkontroluj, že je kotouč celý v záběru.", Toast.LENGTH_LONG).show()
            resetLive()
            return
        }
        val ctx = requireContext().applicationContext
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(setStartedAt))
        val load = loadKg()
        viewLifecycleOwner.lifecycleScope.launch {
            val (setNo, todays) = withContext(Dispatchers.IO) {
                val dao = AppDatabase.getDatabase(ctx).barbellDao()
                val no = dao.countSets(date, lift.name) + 1
                dao.insertSetWithReps(
                    BarbellMapper.toSetEntity(summary, date, setStartedAt, no, load, diameter),
                    BarbellMapper.toRepEntities(summary)
                )
                val sets = dao.getSetsForDate(date).filter { it.exercise == lift.name }
                    .map { BarbellMapper.toSummary(it, dao.getReps(it.id)) }
                no to sets
            }
            if (isAdded) showSummary(summary, setNo, todays, load)
            resetLive()
        }
    }

    private fun showSummary(s: SetSummary, setNo: Int, todays: List<SetSummary>, load: Double?) {
        val title = "Série $setNo · ${s.lift.label}" + (load?.let { String.format(Locale.US, " · %.1f kg", it) } ?: "")
        val sheet = BottomSheetDialog(requireContext())
        sheet.setContentView(ScrollView(requireContext()).apply {
            addView(SetSummaryViews.build(requireContext(), title, s, todays, setNo))
        })
        sheet.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        objectDetector?.close()
        analysisExecutor.shutdown()
    }
}
