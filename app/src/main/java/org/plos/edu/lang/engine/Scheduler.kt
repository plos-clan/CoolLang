package org.plos.edu.lang.engine

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

private const val S_MIN: Double = StabilityDays.MIN_DAYS
private const val S_MAX: Double = StabilityDays.MAX_DAYS
private const val D_MIN: Double = DifficultyScore.MIN_SCORE
private const val D_MAX: Double = DifficultyScore.MAX_SCORE
private const val DR_MIN: Double = 0.0001
private const val DR_MAX: Double = 0.9999
private const val MIN_T: Double = 1.0 / 86_400.0
private const val NEWTON_ITERS: Int = 7
private const val BISECTION_ITERS: Int = 50
private const val SUB_DAY_CUTOFF_DAYS: Double = 0.979
private const val MIN_RELEARN_MINS: Long = 5L
private const val SLOW_BLOCK_START: Int = 7
private const val FAST_BLOCK_START: Int = 15

private val FSRS7_DEFAULT_WEIGHTS: DoubleArray = doubleArrayOf(
    0.1104,
    2.2395,
    3.9221,
    11.7841,
    6.1686,
    0.6457,
    3.6807,
    1.9795,
    0.0000,
    1.3826,
    0.7024,
    0.5999,
    0.8146,
    0.6398,
    1.0000,
    1.3207,
    0.6707,
    3.8668,
    0.4416,
    0.0934,
    1.8631,
    0.6162,
    1.0869,
    0.1567,
    0.0801,
    0.2421,
    0.9464,
    0.1433,
    0.7145,
    0.0000,
    0.5667,
    0.3734,
    0.5333,
    0.3048,
)

data class SchedulingContext(
    val cardId: CardId,
    val current: MemoryState?,
    val nowEpochMs: Long,
    val dayStartEpochMs: Long,
    val retention: RetentionTarget,
)

abstract class MemoryScheduler {
    abstract fun preview(context: SchedulingContext): SchedulePreview

    abstract fun commit(
        context: SchedulingContext,
        grade: ReviewGrade,
    ): ReviewCommit

    abstract fun retrievability(
        state: MemoryState,
        nowEpochMs: Long,
    ): Double

    abstract fun s90Days(state: MemoryState): Double
}

private data class DualTraceState(
    val sSlow: Double,
    val sFast: Double,
    val difficulty: Double,
)

private data class StabilityStepInput(
    val lastS: Double,
    val lastD: Double,
    val retrievability: Double,
    val grade: ReviewGrade,
)

private data class CurveSample(
    val retention: Double,
    val derivative: Double,
)

private data class AlignedSchedule(
    val dueEpochMs: Long,
    val label: String,
)

@Singleton
class FsrsScheduler @Inject constructor() : MemoryScheduler() {
    private val w: DoubleArray = FSRS7_DEFAULT_WEIGHTS

    override fun preview(context: SchedulingContext): SchedulePreview {
        val options = ReviewGrade.entries.map { grade ->
            computeOutcome(context, grade)
        }
        return SchedulePreview(options = options)
    }

    override fun commit(
        context: SchedulingContext,
        grade: ReviewGrade,
    ): ReviewCommit {
        val option = computeOutcome(context, grade)
        return ReviewCommit(
            cardId = context.cardId,
            grade = grade,
            reviewedAtMs = context.nowEpochMs,
            previousState = context.current,
            nextState = option.nextState,
        )
    }

    override fun retrievability(
        state: MemoryState,
        nowEpochMs: Long,
    ): Double {
        val elapsedMs = max(0L, nowEpochMs - state.lastReviewEpochMs)
        val elapsedDays = elapsedMs.toDouble() /
            StudyDayClock.MILLIS_PER_DAY_DOUBLE
        val trace = toTrace(state)
        return evaluateCurve(elapsedDays, trace).retention
    }

    override fun s90Days(state: MemoryState): Double =
        solveIntervalDays(0.90, toTrace(state))

    private fun toTrace(state: MemoryState): DualTraceState = DualTraceState(
        sSlow = state.longStability.value.coerceIn(S_MIN, S_MAX),
        sFast = state.shortStability.value.coerceIn(S_MIN, S_MAX),
        difficulty = state.difficulty.value.coerceIn(D_MIN, D_MAX),
    )

    private fun computeOutcome(
        context: SchedulingContext,
        grade: ReviewGrade,
    ): GradeOption {
        val previous = context.current
        val traces = if (previous == null) {
            initialTraces(grade)
        } else {
            updatedTraces(context.nowEpochMs, previous, grade)
        }
        val rawDays = solveIntervalDays(context.retention.value, traces)
        val schedule = alignSchedule(rawDays, context)
        val prevReps = previous?.reps ?: 0
        val prevLapses = previous?.lapses ?: 0
        val isLapse = previous != null && grade == ReviewGrade.AGAIN
        val addedLapse = if (isLapse) 1 else 0
        val state = MemoryState(
            longStability = StabilityDays.clamped(traces.sSlow),
            shortStability = StabilityDays.clamped(traces.sFast),
            difficulty = DifficultyScore.clamped(traces.difficulty),
            dueEpochMs = schedule.dueEpochMs,
            lastReviewEpochMs = context.nowEpochMs,
            reps = prevReps + 1,
            lapses = prevLapses + addedLapse,
        )
        return GradeOption(
            grade = grade,
            intervalLabel = schedule.label,
            nextState = state,
        )
    }

    private fun initialTraces(grade: ReviewGrade): DualTraceState {
        val sSlow = w[grade.code - 1].coerceIn(S_MIN, S_MAX)
        val sFast = (sSlow * 0.8).coerceIn(S_MIN, S_MAX)
        val d0 = initialDifficulty(grade)
        return DualTraceState(
            sSlow = sSlow,
            sFast = sFast,
            difficulty = d0,
        )
    }

    private fun updatedTraces(
        nowMs: Long,
        previous: MemoryState,
        grade: ReviewGrade,
    ): DualTraceState {
        val elapsedMs = max(0L, nowMs - previous.lastReviewEpochMs)
        val deltaDays = elapsedMs.toDouble() /
            StudyDayClock.MILLIS_PER_DAY_DOUBLE
        val last = toTrace(previous)
        val rMixed = evaluateCurve(deltaDays, last).retention
        val slowInput = StabilityStepInput(
            lastS = last.sSlow,
            lastD = last.difficulty,
            retrievability = rMixed,
            grade = grade,
        )
        val newSlow = stabilityForSet(slowInput, SLOW_BLOCK_START)
        val rFast = fastComponentRecall(deltaDays, last.sFast)
        val fastInput = StabilityStepInput(
            lastS = last.sFast,
            lastD = last.difficulty,
            retrievability = rFast,
            grade = grade,
        )
        val rawFast = stabilityForSet(fastInput, FAST_BLOCK_START)
        val cappedFast = if (grade == ReviewGrade.AGAIN) {
            min(rawFast, newSlow * 0.8)
        } else {
            rawFast
        }
        val newD = nextDifficulty(last.difficulty, grade, rMixed)
        return DualTraceState(
            sSlow = newSlow,
            sFast = cappedFast.coerceIn(S_MIN, S_MAX),
            difficulty = newD,
        )
    }

    private fun stabilityForSet(
        step: StabilityStepInput,
        startIdx: Int,
    ): Double {
        val hardPenalty = if (step.grade == ReviewGrade.HARD) {
            w[startIdx + 6]
        } else {
            1.0
        }
        val easyBonus = if (step.grade == ReviewGrade.EASY) {
            w[startIdx + 7]
        } else {
            1.0
        }
        val failGrowth = (step.lastS + 1.0).pow(w[startIdx + 4]) - 1.0
        val failExp = exp((1.0 - step.retrievability) * w[startIdx + 5])
        val failS = w[startIdx + 3] * failGrowth * failExp
        val postLapse = min(step.lastS, failS)
        val recFactor = exp((1.0 - step.retrievability) * w[startIdx + 2]) - 1.0
        val sinc = exp(w[startIdx] - 1.5) *
            (11.0 - step.lastD) *
            step.lastS.pow(-w[startIdx + 1]) *
            recFactor *
            hardPenalty *
            easyBonus +
            1.0
        val successS = max(postLapse, step.lastS * sinc)
        val chosen = if (step.grade != ReviewGrade.AGAIN) {
            successS
        } else {
            postLapse
        }
        return chosen.coerceIn(S_MIN, S_MAX)
    }

    private fun initialDifficulty(grade: ReviewGrade): Double {
        val raw = w[4] - exp(w[5] * (grade.code - 1)) + 1.0
        return raw.coerceIn(D_MIN, D_MAX)
    }

    private fun nextDifficulty(
        lastD: Double,
        grade: ReviewGrade,
        retrievability: Double,
    ): Double {
        val rawDelta = -w[6] * (grade.code - 3.0)
        val scaledDelta = if (grade == ReviewGrade.AGAIN) {
            rawDelta * (retrievability + 0.1)
        } else {
            rawDelta
        }
        val damped = lastD + (10.0 - lastD) * scaledDelta / 9.0
        val anchor = initialDifficulty(ReviewGrade.EASY)
        val reverted = anchor * 0.01 + damped * 0.99
        return reverted.coerceIn(D_MIN, D_MAX)
    }

    private fun fastComponentRecall(
        elapsedDays: Double,
        sFast: Double,
    ): Double {
        val t = max(0.0, elapsedDays)
        val sf = sFast.coerceIn(S_MIN, S_MAX)
        val decayMag = (w[23] * sf.pow(w[33] - 0.3)).coerceIn(0.01, 0.95)
        val decay = -decayMag
        val factor = exp(min(60.0, ln(w[25]) / decay)) - 1.0
        return (1.0 + factor * (t / sf)).pow(decay)
    }

    private fun evaluateCurve(
        elapsedDays: Double,
        trace: DualTraceState,
    ): CurveSample {
        val t = max(0.0, elapsedDays)
        val s = max(S_MIN, trace.sSlow)
        val sf = max(S_MIN, trace.sFast)
        val d = trace.difficulty.coerceIn(D_MIN, D_MAX)
        val decay1Mag = (w[23] * sf.pow(w[33] - 0.3)).coerceIn(0.01, 0.95)
        val decay1 = -decay1Mag
        val factor1 = exp(min(60.0, ln(w[25]) / decay1)) - 1.0
        val b1 = 1.0 + factor1 * (t / sf)
        val r1 = b1.pow(decay1)
        val dr1 = decay1 * b1.pow(decay1 - 1.0) * factor1 / sf
        val decay2 = -w[24].coerceIn(0.01, 0.95)
        val factor2 = w[26].pow(1.0 / decay2) - 1.0
        val dTimescale = exp((d - 5.0) * (w[32] - 0.3))
        val b2 = 1.0 + factor2 * dTimescale * (t / s)
        val r2 = b2.pow(decay2)
        val dr2 = decay2 * b2.pow(decay2 - 1.0) * factor2 * dTimescale / s
        val weight1 = w[27] * sf.pow(-w[29])
        val weight2 = w[28] * s.pow(w[30]) * exp((d - 5.0) * (w[31] - 0.5))
        val weightSum = max(1e-9, weight1 + weight2)
        val rawRet = (weight1 * r1 + weight2 * r2) / weightSum
        val rawDer = (weight1 * dr1 + weight2 * dr2) / weightSum
        return CurveSample(
            retention = rawRet * (1.0 - 2e-5) + 1e-5,
            derivative = rawDer * (1.0 - 2e-5),
        )
    }

    private fun solveIntervalDays(
        targetRetention: Double,
        trace: DualTraceState,
    ): Double {
        val dr = targetRetention.coerceIn(DR_MIN, DR_MAX)
        if (dr >= DR_MAX) return 0.0
        val minLogT = ln(MIN_T)
        val maxLogT = ln(S_MAX)
        var logT = ln(max(MIN_T, max(trace.sSlow, trace.sFast)))
        repeat(NEWTON_ITERS) {
            logT = logT.coerceIn(minLogT, maxLogT)
            val t = exp(logT).coerceIn(MIN_T, S_MAX)
            val sample = evaluateCurve(t, trace)
            val dfDu = min(-1e-12, sample.derivative * t)
            val step = ((sample.retention - dr) / dfDu).coerceIn(-4.0, 4.0)
            logT -= step
            if (!logT.isFinite()) {
                return solveIntervalBisection(dr, trace)
            }
        }
        val candidate = exp(logT).coerceIn(0.0, S_MAX)
        val checkR = evaluateCurve(candidate, trace).retention
        if (checkR.isFinite() && abs(checkR - dr) <= 1e-3) {
            return candidate
        }
        return solveIntervalBisection(dr, trace)
    }

    private fun solveIntervalBisection(
        dr: Double,
        trace: DualTraceState,
    ): Double {
        var lo = 0.0
        var hi = max(1.0, max(trace.sSlow, trace.sFast))
        while (evaluateCurve(hi, trace).retention > dr && hi < S_MAX) {
            hi = min(S_MAX, hi * 2.0)
        }
        repeat(BISECTION_ITERS) {
            val mid = 0.5 * (lo + hi)
            if (evaluateCurve(mid, trace).retention > dr) {
                lo = mid
            } else {
                hi = mid
            }
        }
        return (0.5 * (lo + hi)).coerceIn(0.0, S_MAX)
    }

    private fun alignSchedule(
        rawDays: Double,
        context: SchedulingContext,
    ): AlignedSchedule {
        if (rawDays < SUB_DAY_CUTOFF_DAYS) {
            val mins = max(MIN_RELEARN_MINS, (rawDays * 1440.0).roundToLong())
            val dueMs = context.nowEpochMs + mins * 60_000L
            val label = if (mins < 60L) {
                "${mins}分"
            } else {
                val hours = max(1L, (mins.toDouble() / 60.0).roundToLong())
                "${hours}时"
            }
            return AlignedSchedule(dueEpochMs = dueMs, label = label)
        }
        val calendarDays = max(1L, rawDays.roundToLong())
        val dueMs = context.dayStartEpochMs +
            calendarDays * StudyDayClock.MILLIS_PER_DAY
        val label = formatCalendarDays(calendarDays)
        return AlignedSchedule(dueEpochMs = dueMs, label = label)
    }

    private fun formatCalendarDays(days: Long): String {
        if (days < 30L) return "${days}天"
        if (days < 365L) {
            val tenths = ((days.toDouble() / 30.0) * 10.0).roundToLong() / 10.0
            return "${tenths}月"
        }
        val years = ((days.toDouble() / 365.0) * 10.0).roundToLong() / 10.0
        return "${years}年"
    }
}
