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

        val pdfDocument = PdfDocument()
        val paint = Paint()

        var currentPage = createNewPage(pdfDocument, reportTitle, context)
        var canvas = currentPage.canvas
        var yPos = HEADER_END_Y

        // --- 1. INFO O UŽIVATELI ---
        val lifestyleStr = when(profile.activityMultiplier) {
            1.2f -> "Ležérní (minimum pohybu)"
            1.4f -> "Aktivní (práce v pohybu)"
            1.6f -> "Sportovec (těžké tréninky)"
            else -> "Vlastní (${profile.activityMultiplier})"
        }

        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 15f
        canvas.drawText("KLIENT: ${profile.id}", MARGIN, yPos, paint)
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