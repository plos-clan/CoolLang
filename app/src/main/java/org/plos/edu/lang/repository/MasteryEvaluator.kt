package org.plos.edu.lang.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln
import kotlin.math.min
import org.plos.edu.lang.database.CardStateEntity
import org.plos.edu.lang.database.ReviewLogEntity
import org.plos.edu.lang.engine.DifficultyScore
import org.plos.edu.lang.engine.ForecastSimInput
import org.plos.edu.lang.engine.LearnerPreferences
import org.plos.edu.lang.engine.MasterySnapshot
import org.plos.edu.lang.engine.MasteryStage
import org.plos.edu.lang.engine.MemoryScheduler
import org.plos.edu.lang.engine.MemoryState
import org.plos.edu.lang.engine.PlanStage
import org.plos.edu.lang.engine.ProgressAnalytics
import org.plos.edu.lang.engine.RawLogSample
import org.plos.edu.lang.engine.ReviewGrade
import org.plos.edu.lang.engine.RootEntry
import org.plos.edu.lang.engine.RootId
import org.plos.edu.lang.engine.SessionPlanResult
import org.plos.edu.lang.engine.StabilityBucket
import org.plos.edu.lang.engine.StabilityDays
import org.plos.edu.lang.engine.StudyTrack
import org.plos.edu.lang.engine.TimelineProjector
import org.plos.edu.lang.engine.WordDisposition

private val STABILITY_LABELS: List<String> = listOf(
    "<1 天",
    "1~3 天",
    "4~7 天",
    "8~20 天",
    "21 天+",
)

data class MasteryEvalInput(
    val roots: List<RootEntry>,
    val states: List<CardStateEntity>,
    val dispositions: Map<RootId, WordDisposition>,
    val nowMs: Long,
)

data class AnalyticsEvalInput(
    val activeTrack: StudyTrack,
    val mastery: MasterySnapshot,
    val preferences: LearnerPreferences,
    val cardStates: List<CardStateEntity>,
    val reviewLogs: List<ReviewLogEntity>,
    val dayStartMs: Long,
    val sessionPlan: SessionPlanResult,
)

@Singleton
class MasteryEvaluator @Inject constructor(
    private val scheduler: MemoryScheduler,
    private val timelineProjector: TimelineProjector,
) {
    fun toMemoryState(entity: CardStateEntity): MemoryState =
        MemoryState(
            longStability = StabilityDays.clamped(entity.longStability),
            shortStability = StabilityDays.clamped(entity.shortStability),
            difficulty = DifficultyScore.clamped(entity.difficulty),
            dueEpochMs = entity.dueEpochMs,
            lastReviewEpochMs = entity.lastReviewEpochMs,
            reps = entity.reps,
            lapses = entity.lapses,
        )

    fun evaluateMastery(input: MasteryEvalInput): MasterySnapshot {
        val roots = input.roots
        val byRoot = input.states.groupBy { RootId(it.rootId) }
        val stageMap = HashMap<RootId, MasteryStage>(roots.size)
        val stabilityMap = HashMap<RootId, Double>(roots.size)
        val familiarityMap = HashMap<RootId, Float>(roots.size)
        val logDenom = ln(MasteryStage.MASTERED_DAYS + 1.0)
        for (root in roots) {
            val disp = input.dispositions[root.id] ?: WordDisposition.AUTO
            val linked = byRoot[root.id].orEmpty().filter { it.reps > 0 }
            var avgS90 = 0.0
            var avgR = 0.0
            if (linked.isNotEmpty()) {
                var s90Sum = 0.0
                var rSum = 0.0
                for (entity in linked) {
                    val mem = toMemoryState(entity)
                    s90Sum += scheduler.s90Days(mem)
                    rSum += scheduler.retrievability(mem, input.nowMs)
                }
                avgS90 = s90Sum / linked.size
                avgR = rSum / linked.size
            }
            if (disp == WordDisposition.MASTERED) {
                stageMap[root.id] = MasteryStage.MASTERED
                stabilityMap[root.id] = avgS90
                familiarityMap[root.id] = 1f
            } else if (linked.isEmpty()) {
                stageMap[root.id] = MasteryStage.UNSEEN
                stabilityMap[root.id] = 0.0
                familiarityMap[root.id] = 0f
            } else {
                val stage = MasteryStage.fromStability(avgS90, true)
                val sProgress = min(1.0, ln(1.0 + avgS90) / logDenom)
                val rawFam = if (stage == MasteryStage.MASTERED) {
                    0.88 + 0.12 * avgR
                } else {
                    (0.15 + 0.73 * sProgress) * (0.45 + 0.55 * avgR)
                }
                stageMap[root.id] = stage
                stabilityMap[root.id] = avgS90
                familiarityMap[root.id] = rawFam.toFloat().coerceIn(0.08f, 1f)
            }
        }
        return MasterySnapshot(
            stages = stageMap,
            stabilities = stabilityMap,
            familiarities = familiarityMap,
            dispositions = input.dispositions,
        )
    }

    fun evaluateAnalytics(input: AnalyticsEvalInput): ProgressAnalytics {
        val mastery = input.mastery
        var unseen = 0
        var learning = 0
        var mastered = 0
        var ignored = 0
        val stabCounts = IntArray(5)
        for (rootId in input.activeTrack.rootIds) {
            when (mastery.planStageOf(rootId)) {
                PlanStage.UNSEEN -> unseen++
                PlanStage.LEARNING -> learning++
                PlanStage.MASTERED -> mastered++
                PlanStage.IGNORED -> ignored++
            }
            val isAutoSeen = mastery.dispositionOf(rootId) ==
                WordDisposition.AUTO &&
                mastery.stageOf(rootId) != MasteryStage.UNSEEN
            if (isAutoSeen) {
                val days = mastery.stabilityOf(rootId)
                val bucketIdx = when {
                    days < 1.0 -> 0
                    days < 4.0 -> 1
                    days < 8.0 -> 2
                    days < 21.0 -> 3
                    else -> 4
                }
                stabCounts[bucketIdx]++
            }
        }
        val activeStates = input.cardStates.mapNotNull { card ->
            val rootDisp = mastery.dispositionOf(RootId(card.rootId))
            if (card.reps > 0 && rootDisp == WordDisposition.AUTO) {
                toMemoryState(card)
            } else {
                null
            }
        }
        val queue = input.sessionPlan.queue
        val todayRemDue = queue.count { it.state != null }
        val todayRemNew = queue.count { it.state == null }
        val prefs = input.preferences
        val simInput = ForecastSimInput(
            activeStates = activeStates,
            unseenPoolCount = unseen,
            todayRemainingDue = todayRemDue,
            todayRemainingNew = todayRemNew,
            dailyNewLimit = prefs.dailyNewLimit,
            dailyTotalCap = prefs.dailyTotalCap,
            retention = prefs.retention,
            dayStartMs = input.dayStartMs,
        )
        val rawSamples = input.reviewLogs.map { log ->
            RawLogSample(log.cardId, log.gradeCode, log.reviewedAtMs)
        }
        val projection = timelineProjector.project(rawSamples, simInput)
        val stabilityBars = STABILITY_LABELS.mapIndexed { idx, range ->
            StabilityBucket(rangeLabel = range, wordCount = stabCounts[idx])
        }
        val logs = input.reviewLogs
        val recallRate = computeSubsequentRecallRate(logs)
        return ProgressAnalytics(
            unseenCount = unseen,
            learningCount = learning,
            masteredCount = mastered,
            ignoredCount = ignored,
            selectedCount = unseen + learning + mastered,
            trackTotalCount = input.activeTrack.rootIds.size,
            actualRecallRate = recallRate,
            targetRetention = prefs.retention.value,
            totalReviewsLogged = logs.size,
            deferredDueCount = input.sessionPlan.deferredDueCount,
            estimatedFinishDays = projection.estimatedFinishDays,
            historyBars = projection.historyBars,
            forecastBars = projection.forecastBars,
            stabilityBuckets = stabilityBars,
        )
    }

    private fun computeSubsequentRecallRate(
        logs: List<ReviewLogEntity>,
    ): Double? {
        if (logs.isEmpty()) return null
        val firstStampByCard = HashMap<String, Long>()
        for (entry in logs) {
            val prevMin = firstStampByCard[entry.cardId]
            if (prevMin == null || entry.reviewedAtMs < prevMin) {
                firstStampByCard[entry.cardId] = entry.reviewedAtMs
            }
        }
        var total = 0
        var passed = 0
        for (entry in logs) {
            val firstStamp = firstStampByCard[entry.cardId]
            if (entry.reviewedAtMs > (firstStamp ?: entry.reviewedAtMs)) {
                total++
                if (entry.gradeCode != ReviewGrade.AGAIN.code) passed++
            }
        }
        if (total == 0) return null
        return passed.toDouble() / total.toDouble()
    }
}
