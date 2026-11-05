package cz.uhk.macroflow.report

import android.content.Context
import cz.uhk.macroflow.dashboard.MacroCalculator
import cz.uhk.macroflow.dashboard.MacroResult
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.GameEventType
import cz.uhk.macroflow.energy.WeightTrend
import cz.uhk.macroflow.history.CalendarWeek
import cz.uhk.macroflow.settings.WaterGoal
import cz.uhk.macroflow.training.exercises.ExerciseLibrary
import cz.uhk.macroflow.training.log.WorkoutRepository
import cz.uhk.macroflow.wearable.ActivityGroups
import cz.uhk.macroflow.wearable.SleepInsights
import cz.uhk.macroflow.wearable.SportGroup
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/**
 * Android strana týdenního reportu (docs/adr/0100): z databáze poskládá [WeekIn] pro týden obsahující [anchor].
 * Nic se neukládá, předchozí týden se počítá stejnou cestou. Volat mimo hlavní vlákno.
 */
object WeeklyReportData {
    fun load(ctx: Context, anchor: LocalDate = LocalDate.now()): Report = WeeklyReportModel.build(week(ctx, anchor))

    fun label(anchor: LocalDate): String = CalendarWeek.label(anchor)

    private fun LocalDate.toDate(): Date = Date.from(atStartOfDay(ZoneId.systemDefault()).toInstant())

    fun week(ctx: Context, anchor: LocalDate): WeekIn {
        val app = ctx.applicationContext
        val db = AppDatabase.getDatabase(app)
        val today = LocalDate.now()
        val monday = CalendarWeek.monday(anchor)
        val sunday = monday.plusDays(6)
        val lastShown = minOf(sunday, today)                    // dny, které už začaly
        val closedUntil = minOf(sunday, today.minusDays(1))     // dny, které už skončily
        fun days(from: LocalDate, to: LocalDate) = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        val closed = if (closedUntil.isBefore(monday)) emptyList() else days(monday, closedUntil)
        val profile = runBlocking { db.userProfileDao().getProfileSync() }

        // Osobní cíle uzavřených dnů (stejný model jako Dashboard a Historie)
        val targets: Map<LocalDate, MacroResult> = closed.mapNotNull { d ->
            runCatching { MacroCalculator.calculateForDate(app, d.toDate()) }.getOrNull()?.let { d to it }
        }.toMap()

        // Jídlo: jen uzavřené dny se zápisem
        val consumed = db.consumedSnackDao().getConsumedSinceSync(monday.toString()).filter { it.date <= sunday.toString() }.groupBy { it.date }
        val food = closed.mapNotNull { d ->
            val items = consumed[d.toString()]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val t = targets[d]?.takeIf { it.protein > 0 } ?: return@mapNotNull null
            DayFood(d.toEpochDay().toInt(), items.sumOf { it.calories.toDouble() }, items.sumOf { it.p.toDouble() },
                items.sumOf { it.s.toDouble() }, items.sumOf { it.t.toDouble() }, t.calories, t.protein, t.carbs, t.fat)
        }

        // Trénink: plán z TrainingPrefs, hotovo podle deníku, rychlého zápisu nebo hodinek
        val plan = app.getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)
        val sets = db.workoutDao().getAllSync()
        val setDays = sets.map { it.date }.toSet()
        val events = db.gameEventDao()
        val acts = db.wearableDao().getActivitiesBetweenSync(monday.toString(), sunday.toString())
        fun done(d: LocalDate): Boolean {
            val k = d.toString()
            return k in setDays || events.countOnDate(GameEventType.WORKOUT_DONE.name, k) > 0 ||
                events.countOnDate(GameEventType.CARDIO_DONE.name, k) > 0 ||
                acts.any { a ->
                    a.date == k && ((ActivityGroups.of(a.typeKey) == SportGroup.STRENGTH && a.durationSec >= 20 * 60) ||
                        (ActivityGroups.of(a.typeKey) == SportGroup.CARDIO && a.durationSec >= 10 * 60))
                }
        }
        val started = if (lastShown.isBefore(monday)) emptyList() else days(monday, lastShown)
        fun planned(d: LocalDate): Boolean {
            val n = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            return (plan.getString("type_$n", "rest") ?: "rest") != "rest" || (plan.getString("kardio_type_$n", "rest") ?: "rest") != "rest"
        }
        // Dnešek se do plánu počítá, jen když už je splněný (jinak by hodnocení pokazil nedokončený den)
        val plannedSoFar = started.count { planned(it) && (it <= closedUntil || done(it)) }
        val doneDays = started.count { done(it) }

        val logged = WorkoutRepository.toLogged(sets)
        val mondayEpoch = monday.toEpochDay().toInt()
        val weekSets = logged.filter { it.day in mondayEpoch..(mondayEpoch + 6) }
        val earlier = logged.filter { it.day < mondayEpoch }
        val names = logged.map { it.exerciseId }.distinct().associateWith { ExerciseLibrary.byId(it)?.name ?: it }

        // Spánek: noc patří ke dni probuzení
        val wear = db.wearableDao().getDaysSync(monday.minusDays(7).toString(), sunday.toString()).filter { it.date <= today.toString() }
        fun night(d: cz.uhk.macroflow.wearable.WearableDayEntity) = d.sleepSec?.takeIf { it > 0 }?.let {
            NightIn(LocalDate.parse(d.date).toEpochDay().toInt(), it, d.sleepScore, SleepInsights.bedtimeMin(d))
        }
        val nights = wear.filter { it.date >= monday.toString() }.mapNotNull { night(it) }
        val prevNights = wear.filter { it.date < monday.toString() }.mapNotNull { night(it) }

        // Tělo: vyhlazená hmotnost na konci týdne proti stavu o 7 dní dřív
        val checkIns = db.checkInDao().getAllCheckInsSync()
        val weights = checkIns.filter { it.weight > 0 }.mapNotNull { c ->
            runCatching { LocalDate.parse(c.date).toEpochDay().toInt() to c.weight }.getOrNull()
        }.toMap()
        val endEpoch = lastShown.toEpochDay().toInt()
        val points = WeightTrend().smooth(weights)
        val end = points.lastOrNull { it.day <= endEpoch }
        val start = end?.let { e -> points.firstOrNull { it.day == e.day - 7 } }
        val weighIns = weights.keys.count { it in mondayEpoch..(mondayEpoch + 6) && it <= endEpoch }

        // Kroky a voda (uzavřené dny)
        val steps = db.stepsDao().getAllStepsSync().associate { it.date to it.count }
        val wearSteps = wear.associate { it.date to it.steps }
        val stepList = closed.mapNotNull { d -> (steps[d.toString()] ?: wearSteps[d.toString()])?.takeIf { it > 0 } }
        val water = db.waterDao().getWaterSinceSync(monday.toString()).filter { it.date <= sunday.toString() }
            .groupBy { it.date }.mapValues { e -> e.value.sumOf { it.amountMl } }
        val waterLogged = closed.count { (water[it.toString()] ?: 0) > 0 }
        val waterDays = closed.count { d ->
            (water[d.toString()] ?: 0) >= WaterGoal.ml(targets[d]?.water)
        }

        // Ranní rituál
        val weekChecks = checkIns.filter { it.date >= monday.toString() && it.date <= lastShown.toString() }

        return WeekIn(
            monday = mondayEpoch, today = today.toEpochDay().toInt(), goal = profile?.goal ?: "MAINTAIN",
            food = food, plannedSoFar = plannedSoFar, doneDays = doneDays,
            weekSets = weekSets, earlierSets = earlier, names = names,
            nights = nights, prevNights = prevNights,
            weighIns = weighIns, trendKg = end?.level, trendChangeKg = if (end != null && start != null) end.level - start.level else null,
            steps = stepList, stepGoal = profile?.stepGoal ?: 6000,
            waterDays = waterDays, waterLoggedDays = waterLogged, closedDays = closed.size,
            checkInDays = weekChecks.size, avgEnergy = weekChecks.map { it.energyLevel }.takeIf { it.isNotEmpty() }?.average()
        )
    }
}
