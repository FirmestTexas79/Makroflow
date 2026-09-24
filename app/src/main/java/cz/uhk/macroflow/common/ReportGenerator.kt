package cz.uhk.macroflow.common

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import cz.uhk.macroflow.R
import cz.uhk.macroflow.dashboard.MacroCalculator
import cz.uhk.macroflow.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ReportGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 45f
    private const val HEADER_END_Y = 135f // Konstanta pro začátek obsahu pod logem

    suspend fun generatePdfReport(context: Context, reportTitle: String): Uri? = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val profile = db.userProfileDao().getProfileSync() ?: return@withContext null
        val trainingPrefs = context.getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)

        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val clientName = user?.displayName ?: "Sportovec"

        val pdfDocument = PdfDocument()
        val paint = Paint()

        var currentPage = createNewPage(pdfDocument, reportTitle, context)
        var canvas = currentPage.canvas
        var yPos = HEADER_END_Y

        // --- 1. INFO O UŽIVATELI ---
        val lifestyleStr = cz.uhk.macroflow.energy.Lifestyle.fromStored(profile.activityMultiplier).label

        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 15f
        // ✅ Teď vypisujeme reálné jméno nebo "Sportovec"
        canvas.drawText("KLIENT: $clientName", MARGIN, yPos, paint)
        yPos += 22f

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 11f
        canvas.drawText("Věk: ${profile.age} let  |  Váha: ${profile.weight} kg  |  Výška: ${profile.height} cm", MARGIN, yPos, paint)
        yPos += 18f
        canvas.drawText("Cíl: ${profile.goal}  |  Kroky: ${profile.stepGoal}  |  Styl: $lifestyleStr", MARGIN, yPos, paint)
        yPos += 40f

        // --- 2. TÝDENNÍ TRÉNINKOVÝ PLÁN ---
        paint.color = Color.parseColor("#283618")
        canvas.drawRect(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + 1.5f, paint)
        yPos += 22f
        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13f
        canvas.drawText("TÝDENNÍ TRÉNINKOVÝ PLÁN", MARGIN, yPos, paint)
        yPos += 22f

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 10f
        val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val daysCz = listOf("Pondělí", "Úterý", "Středa", "Čtvrtek", "Pátek", "Sobota", "Neděle")

        daysOfWeek.forEachIndexed { index, dayEn ->
            val strength = trainingPrefs.getString("type_$dayEn", "rest") ?: "rest"
            val cardio = trainingPrefs.getString("kardio_type_$dayEn", "rest") ?: "rest"

            val trainingDesc = when {
                strength != "rest" && cardio != "rest" -> "KOMBO: Síla ($strength) + Kardio ($cardio)"
                strength != "rest" -> "SILOVÝ: $strength"
                cardio != "rest" -> "KARDIO: $cardio"
                else -> "Odpočinek (Rest Day)"
            }

            canvas.drawText("${daysCz[index].uppercase()}:", MARGIN, yPos, paint)
            canvas.drawText(trainingDesc, MARGIN + 100f, yPos, paint)
            yPos += 16f
        }
        yPos += 30f

        // --- 3. STANOVENÉ DENNÍ CÍLE ---
        paint.color = Color.parseColor("#BC6C25")
        canvas.drawRect(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + 1.5f, paint)
        yPos += 22f
        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("STANOVENÉ DENNÍ CÍLE (DOPORUČENÉ)", MARGIN, yPos, paint)
        yPos += 22f

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 10f
        val tempCal = Calendar.getInstance()
        for (i in 1..7) {
            tempCal.set(Calendar.DAY_OF_WEEK, i)
            val target = MacroCalculator.calculateForDate(context, tempCal.time)
            val dayName = SimpleDateFormat("EEEE", Locale("cs", "CZ")).format(tempCal.time)

            canvas.drawText("${dayName.uppercase()}:", MARGIN, yPos, paint)
            canvas.drawText("${target.calories.toInt()} kcal", MARGIN + 100f, yPos, paint)
            canvas.drawText("B: ${target.protein.toInt()}g | S: ${target.carbs.toInt()}g | T: ${target.fat.toInt()}g | Vl: ${target.fiber.toInt()}g", MARGIN + 180f, yPos, paint)
            yPos += 16f
        }
        // Adaptivní výdej (fáze B) – jak se model přizpůsobil reálným datům
        cz.uhk.macroflow.data.AppDatabase.getDatabase(context).adaptiveTdeeDao().getLatestSync()?.let { a ->
            yPos += 6f
            val line = if (a.status == cz.uhk.macroflow.energy.AdaptiveExpenditure.Status.OK.name)
                "Adaptivní výdej: ${a.adaptiveTdee.toInt()} kcal (rovnice ${a.modelTdee.toInt()} kcal, " +
                    "korekce ${"%+.0f".format((a.factor - 1) * 100)} %, jistota ${(a.confidence * 100).toInt()} %, " +
                    "${a.weighIns} vážení / ${a.loggedDays} zapsaných dní)"
            else
                "Adaptivní výdej: zatím málo dat (${a.weighIns} vážení, ${a.loggedDays} zapsaných dní za 28 dní)"
            canvas.drawText(line, MARGIN, yPos, paint)
            yPos += 16f
        }
        yPos += 30f

        // --- 4. SOUHRN AKTIVITY (Voda + Kroky) ---
        paint.color = Color.parseColor("#606C38")
        canvas.drawRect(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + 1.5f, paint)
        yPos += 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("SOUHRN AKTIVITY A HYDRATACE (7 DNÍ)", MARGIN, yPos, paint)
        yPos += 20f

        paint.typeface = Typeface.DEFAULT
        val historyCal = Calendar.getInstance()
        for (i in 0 until 7) {
            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(historyCal.time)
            val steps = db.stepsDao().getStepsForDateSync(dateKey)
            val waterMl = db.waterDao().getTotalMlForDateSync(dateKey)
            val dateStr = SimpleDateFormat("dd.MM. (EEE)", Locale("cs", "CZ")).format(historyCal.time)

            canvas.drawText(dateStr, MARGIN, yPos, paint)
            canvas.drawText("Kroky: ${steps?.count ?: 0}", MARGIN + 100f, yPos, paint)
            canvas.drawText("Voda: ${String.format("%.1f", waterMl / 1000f)} L", MARGIN + 250f, yPos, paint)
            yPos += 15f
            historyCal.add(Calendar.DAY_OF_YEAR, -1)
        }
        yPos += 35f

        // --- 5. DETAILNÍ HISTORIE JÍDEL ---
        if (yPos > PAGE_HEIGHT - 120) {
            pdfDocument.finishPage(currentPage)
            currentPage = createNewPage(pdfDocument, reportTitle, context)
            canvas = currentPage.canvas
            yPos = HEADER_END_Y
        }

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 14f
        paint.color = Color.BLACK
        canvas.drawText("DETAILNÍ LOG POTRAVIN", MARGIN, yPos, paint)
        yPos += 25f

        // Definice sloupců pro tabulku
        val colName = MARGIN
        val colKcal = MARGIN + 220f
        val colP = MARGIN + 280f
        val colS = MARGIN + 330f
        val colT = MARGIN + 380f
        val colVl = MARGIN + 430f

        val foodCal = Calendar.getInstance()
        for (i in 0 until 7) {
            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(foodCal.time)
            val snacks = db.consumedSnackDao().getConsumedByDateSync(dateKey)

            if (snacks.isNotEmpty()) {
                // Kontrola místa pro nadpis dne a hlavičku tabulky
                if (yPos > PAGE_HEIGHT - 100) {
                    pdfDocument.finishPage(currentPage)
                    currentPage = createNewPage(pdfDocument, reportTitle, context)
                    canvas = currentPage.canvas
                    yPos = HEADER_END_Y
                }

                // Nadpis dne
                yPos += 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 11f
                paint.color = Color.parseColor("#283618")
                val dayTitle = SimpleDateFormat("EEEE dd.MM.", Locale("cs", "CZ")).format(foodCal.time)
                canvas.drawText(dayTitle.uppercase(), MARGIN, yPos, paint)
                yPos += 5f
                canvas.drawRect(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + 0.5f, paint)
                yPos += 15f

                // Hlavička tabulky
                paint.textSize = 9f
                paint.color = Color.GRAY
                canvas.drawText("NÁZEV POTRAVINY", colName, yPos, paint)
                canvas.drawText("KCAL", colKcal, yPos, paint)
                canvas.drawText("B", colP, yPos, paint)
                canvas.drawText("S", colS, yPos, paint)
                canvas.drawText("T", colT, yPos, paint)
                canvas.drawText("VL", colVl, yPos, paint)
                yPos += 12f

                paint.typeface = Typeface.DEFAULT
                paint.color = Color.BLACK

                snacks.forEach { snack ->
                    // Kontrola místa pro řádek jídla
                    if (yPos > PAGE_HEIGHT - 50) {
                        pdfDocument.finishPage(currentPage)
                        currentPage = createNewPage(pdfDocument, reportTitle, context)
                        canvas = currentPage.canvas
                        yPos = HEADER_END_Y

                        // Znovu vykreslit hlavičku na nové stránce, pokud den pokračuje
                        paint.color = Color.GRAY
                        canvas.drawText("NÁZEV POTRAVINY (pokr.)", colName, yPos, paint)
                        yPos += 12f
                        paint.color = Color.BLACK
                    }

                    // Ořezání dlouhého názvu, aby nepřetékal do čísel
                    val displayName = if (snack.name.length > 35) snack.name.take(32) + "..." else snack.name

                    canvas.drawText(displayName, colName, yPos, paint)
                    canvas.drawText("${snack.calories}", colKcal, yPos, paint)
                    canvas.drawText("${snack.p.toInt()}g", colP, yPos, paint)
                    canvas.drawText("${snack.s.toInt()}g", colS, yPos, paint)
                    canvas.drawText("${snack.t.toInt()}g", colT, yPos, paint)
                    canvas.drawText("${snack.fiber.toInt()}g", colVl, yPos, paint)

                    yPos += 14f
                }
                yPos += 15f // Mezera mezi dny
            }
            foodCal.add(Calendar.DAY_OF_YEAR, -1)
        }

        drawFooter(canvas)
        pdfDocument.finishPage(currentPage)

        // Samostatná stránka pro trenéra: sledování činky kamerou
        drawBarbellSection(pdfDocument, reportTitle, context, db)

        val file = File(context.cacheDir, "MakroFlow_Report.pdf")
        return@withContext try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            pdfDocument.close()
            null
        }
    }

    /**
     * Stránka „Sledování činky“: posledních 14 dní, po dnech a cvicích.
     * Každá série: souhrn; každé opakování: body rozsahu, rozsah, doby fází, rychlost, dráha –
     * buňky podbarvené stejným přechodem zelená → červená jako v aplikaci, legenda dole.
     */
    private fun drawBarbellSection(pdf: PdfDocument, title: String, context: Context, db: AppDatabase) {
        val dao = db.barbellDao()
        val from = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -13) }.time)
        val sets = dao.getSetsSince(from)
        if (sets.isEmpty()) return

        val paint = Paint().apply { isAntiAlias = true }
        var page = createNewPage(pdf, title, context)
        var c = page.canvas
        var y = HEADER_END_Y

        val cols = floatArrayOf(22f, 95f, 50f, 55f, 50f, 55f, 45f, 45f)   // šířky sloupců
        val headers = listOf("#", "Body cm (start/obrat/konec)", "Rozsah", "Spouštění", "Zvedání", "Rychlost", "Dráha", "Kvalita")

        fun newPageIfNeeded(need: Float) {
            if (y + need > PAGE_HEIGHT - 150) {
                drawBarbellLegend(c, paint)
                drawFooter(c)
                pdf.finishPage(page)
                page = createNewPage(pdf, title, context)
                c = page.canvas
                y = HEADER_END_Y
            }
        }

        paint.color = Color.BLACK; paint.textSize = 14f; paint.typeface = Typeface.DEFAULT_BOLD
        c.drawText("SLEDOVÁNÍ ČINKY (posledních 14 dní)", MARGIN, y, paint)
        y += 22f

        sets.groupBy { it.date to it.exercise }.forEach { (key, daySets) ->
            val summaries = daySets.map { cz.uhk.macroflow.training.BarbellMapper.toSummary(it, dao.getReps(it.id)) }
            newPageIfNeeded(40f)
            paint.textSize = 12f; paint.typeface = Typeface.DEFAULT_BOLD; paint.color = Color.parseColor("#283618")
            c.drawText("${key.first} · ${cz.uhk.macroflow.training.analysis.Lift.from(key.second).label}", MARGIN, y, paint)
            y += 16f

            daySets.zip(summaries).forEach { (set, sum) ->
                newPageIfNeeded(34f + 13f * (sum.reps.size + 1))
                // Souhrn série se zabarvením proti nejlepší sérii dne
                val setScore = cz.uhk.macroflow.training.analysis.RepRating.setScore(sum, summaries)
                paint.color = cz.uhk.macroflow.training.analysis.RepRating.color(setScore); paint.alpha = 110
                c.drawRect(MARGIN, y - 10f, PAGE_WIDTH - MARGIN, y + 14f, paint)
                paint.alpha = 255; paint.color = Color.BLACK; paint.typeface = Typeface.DEFAULT_BOLD; paint.textSize = 9f
                val load = set.loadKg?.let { String.format(Locale.US, " · %.1f kg", it) } ?: ""
                c.drawText("Série ${set.setIndex}$load · ${set.repCount} opak. · rozsah Ø %.1f cm (vážený %.1f, var. %.0f %%) · nejrychlejší %.2f m/s · ztráta %.0f %%"
                    .format(Locale.US, set.avgRomCm, set.weightedRomCm, set.romCvPct, set.bestMcv, set.velocityLossPct), MARGIN + 4f, y, paint)
                paint.typeface = Typeface.DEFAULT
                c.drawText("spouštění Ø %.2f s · zvedání Ø %.2f s · rep Ø %.2f s · dráha Ø %.1f cm · kvalita sledování %.0f %%"
                    .format(Locale.US, set.avgEccentricMs / 1000.0, set.avgConcentricMs / 1000.0, set.avgTotalMs / 1000.0,
                        set.avgDeviationCm, set.quality * 100), MARGIN + 4f, y + 11f, paint)
                y += 26f

                // Hlavička tabulky
                var x = MARGIN
                paint.textSize = 7.5f; paint.typeface = Typeface.DEFAULT_BOLD
                headers.forEachIndexed { i, h -> c.drawText(h, x + 2f, y, paint); x += cols[i] }
                y += 4f

                paint.typeface = Typeface.DEFAULT; paint.textSize = 8f
                sum.reps.forEach { r ->
                    val rr = cz.uhk.macroflow.training.analysis.RepRating
                    val cells = listOf(
                        "${r.index}" to null,
                        String.format(Locale.US, "%.0f / %.0f / %.0f", r.startCm, r.turnCm, r.endCm) to null,
                        String.format(Locale.US, "%.1f cm", r.romCm) to rr.score(rr.Metric.ROM, r, sum),
                        String.format(Locale.US, "%.2f s", r.eccentricMs / 1000.0) to rr.score(rr.Metric.ECCENTRIC, r, sum),
                        String.format(Locale.US, "%.2f s", r.concentricMs / 1000.0) to rr.score(rr.Metric.CONCENTRIC, r, sum),
                        String.format(Locale.US, "%.2f m/s", r.meanConcentricVelocity) to rr.score(rr.Metric.VELOCITY, r, sum),
                        String.format(Locale.US, "%.1f cm", r.deviationCm) to rr.score(rr.Metric.DEVIATION, r, sum),
                        String.format(Locale.US, "%.0f %%", r.quality * 100) to null
                    )
                    x = MARGIN
                    cells.forEachIndexed { i, (txt, score) ->
                        if (score != null) {
                            paint.color = rr.color(score); paint.alpha = 120
                            c.drawRect(x, y + 1f, x + cols[i] - 2f, y + 12f, paint)
                            paint.alpha = 255
                        }
                        paint.color = Color.BLACK
                        c.drawText(txt, x + 2f, y + 10f, paint)
                        x += cols[i]
                    }
                    y += 13f
                }
                y += 10f
            }
            y += 6f
        }
        drawBarbellLegend(c, paint)
        drawFooter(c)
        pdf.finishPage(page)
    }

    private fun drawBarbellLegend(c: Canvas, paint: Paint) {
        val rr = cz.uhk.macroflow.training.analysis.RepRating
        var y = PAGE_HEIGHT - 140f
        paint.color = Color.BLACK; paint.textSize = 9f; paint.typeface = Typeface.DEFAULT_BOLD
        c.drawText("Legenda barev", MARGIN, y, paint)
        y += 6f
        val w = PAGE_WIDTH - 2 * MARGIN
        val steps = 60
        for (i in 0 until steps) {
            paint.color = rr.color(i / (steps - 1.0))
            c.drawRect(MARGIN + w * i / steps, y, MARGIN + w * (i + 1) / steps + 0.5f, y + 8f, paint)
        }
        y += 18f
        paint.color = Color.BLACK; paint.typeface = Typeface.DEFAULT; paint.textSize = 7.5f
        c.drawText("zelená = jako nejlepší rep / v normě", MARGIN, y, paint)
        val right = "červená = výrazně horší"
        c.drawText(right, PAGE_WIDTH - MARGIN - paint.measureText(right), y, paint)
        y += 11f
        rr.Metric.entries.forEach { m -> c.drawText("${m.label}: ${m.legend}", MARGIN, y, paint); y += 10f }
        c.drawText("Rychlost = průměrná rychlost zvedání (m/s). Vážený rozsah = průměr vážený kvalitou sledování repu. Série se srovnávají s nejlepší sérií dne.", MARGIN, y, paint)
    }

    private fun createNewPage(pdfDocument: PdfDocument, title: String, context: Context): PdfDocument.Page {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pdfDocument.pages.size + 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        val logoSize = 60
        val bitmap = Bitmap.createBitmap(logoSize, logoSize, Bitmap.Config.ARGB_8888)
        val vectorDrawable = ContextCompat.getDrawable(context, R.drawable.ic_logo_black)
        vectorDrawable?.let {
            it.setBounds(0, 0, logoSize, logoSize)
            it.draw(Canvas(bitmap))
            canvas.drawBitmap(bitmap, MARGIN, 30f, paint)
        }

        paint.color = Color.BLACK
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("MAKROFLOW", MARGIN + logoSize + 15f, 72f, paint)

        paint.textSize = 11f
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.GRAY
        canvas.drawText("$title | Strana ${pdfDocument.pages.size + 1}", MARGIN + logoSize + 15f, 92f, paint)

        return page
    }

    private fun drawFooter(canvas: Canvas) {
        val paint = Paint()
        paint.color = Color.LTGRAY
        paint.textSize = 8f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Vygenerováno aplikací MakroFlow 2.0. Neslouží jako lékařské doporučení.", PAGE_WIDTH / 2f, PAGE_HEIGHT - 30f, paint)
    }
}