package cz.uhk.macroflow.training

import cz.uhk.macroflow.data.BarbellRepEntity
import cz.uhk.macroflow.data.BarbellSetEntity
import cz.uhk.macroflow.training.analysis.Lift
import cz.uhk.macroflow.training.analysis.LoadVelocity
import cz.uhk.macroflow.training.analysis.LoadVelocityProfile
import cz.uhk.macroflow.training.analysis.LvPoint
import cz.uhk.macroflow.training.analysis.RepMetrics
import cz.uhk.macroflow.training.analysis.SetSummary

/** Převod mezi výsledkem analýzy a entitami v DB (oběma směry – report hodnotí z DB). */
object BarbellMapper {

    fun toSetEntity(
        s: SetSummary, date: String, startedAt: Long, setIndex: Int, loadKg: Double?, plateDiameterCm: Double
    ) = BarbellSetEntity(
        date = date, startedAt = startedAt, exercise = s.lift.name, setIndex = setIndex,
        loadKg = loadKg, plateDiameterCm = plateDiameterCm, repCount = s.repCount,
        avgRomCm = s.avgRomCm, weightedRomCm = s.weightedRomCm, romCvPct = s.romCvPct,
        avgEccentricMs = s.avgEccentricMs, avgConcentricMs = s.avgConcentricMs, avgTotalMs = s.avgTotalMs,
        bestMcv = s.bestMcv, lastMcv = s.lastMcv, velocityLossPct = s.velocityLossPct,
        avgDeviationCm = s.avgDeviationCm, quality = s.quality
    )

    fun toRepEntities(s: SetSummary) = s.reps.map { r ->
        BarbellRepEntity(
            setId = 0, repIndex = r.index, startCm = r.startCm, turnCm = r.turnCm, endCm = r.endCm,
            romCm = r.romCm, eccentricMs = r.eccentricMs, concentricMs = r.concentricMs, pauseMs = r.pauseMs,
            totalMs = r.totalMs, meanVelocity = r.meanConcentricVelocity, peakVelocity = r.peakConcentricVelocity,
            deviationCm = r.deviationCm, quality = r.quality
        )
    }

    fun toLvPoint(set: BarbellSetEntity): LvPoint? =
        set.loadKg?.let { LvPoint(it, set.bestMcv, set.quality) }

    /**
     * Profil zátěž–rychlost pro [date]: z jeho sérií, a když na přímku nestačí,
     * se sklonem z předchozích dnů v [sets] (sety jednoho cviku, typicky 6 týdnů zpět).
     */
    fun profileFor(lift: Lift, date: String, sets: List<BarbellSetEntity>): LoadVelocityProfile? {
        val mine = sets.filter { it.exercise == lift.name }
        val today = mine.filter { it.date == date }.mapNotNull(::toLvPoint)
        val earlier = mine.filter { it.date < date }.groupBy { it.date }.values.map { d -> d.mapNotNull(::toLvPoint) }
        return LoadVelocity.fit(lift, today, LoadVelocity.historicalSlopes(lift, earlier))
    }

    fun toSummary(set: BarbellSetEntity, reps: List<BarbellRepEntity>) = SetSummary(
        lift = Lift.from(set.exercise),
        reps = reps.map { r ->
            RepMetrics(
                index = r.repIndex, startCm = r.startCm, turnCm = r.turnCm, endCm = r.endCm, romCm = r.romCm,
                eccentricMs = r.eccentricMs, concentricMs = r.concentricMs, pauseMs = r.pauseMs,
                totalMs = r.totalMs, meanConcentricVelocity = r.meanVelocity,
                peakConcentricVelocity = r.peakVelocity, deviationCm = r.deviationCm, quality = r.quality
            )
        },
        cmPerPx = 0.0,
        avgRomCm = set.avgRomCm, weightedRomCm = set.weightedRomCm, romCvPct = set.romCvPct,
        avgEccentricMs = set.avgEccentricMs, avgConcentricMs = set.avgConcentricMs, avgTotalMs = set.avgTotalMs,
        bestMcv = set.bestMcv, lastMcv = set.lastMcv, velocityLossPct = set.velocityLossPct,
        avgDeviationCm = set.avgDeviationCm, quality = set.quality
    )
}
