package org.plos.edu.lang.engine

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

private const val TIMELINE_DAYS: Int = 14
private const val MATURE_STABILITY_DAYS: Double = 7.0
private const val NOON_OFFSET_MS: Long = 12L * 3_600_000L
private val SIM_CARD_ID: CardId = CardId("sim:cohort")

data class RawLogSample(
    val cardId: String,
    val gradeCode: Int,
    val reviewedAtMs: Long,
)

data class ForecastSimInput(
    val activeStates: List<MemoryState>,
    val unseenPoolCount: Int,
    val todayRemainingDue: Int,
    val todayRemainingNew: Int,
    val dailyNewLimit: Int,
    val dailyTotalCap: DailyTotalCap,
    val retention: RetentionTarget,
    val dayStartMs: Long,
)

data class TimelineProjection(
    val historyBars: List<HistoryDayBar>,
    val forecastBars: List<ForecastDayBar>,
    val estimatedFinishDays: Int,
)

private class SimCohort(
    var state: MemoryState,
    var s90: Double,
    var count: Int,
)

private data class FirstGradeEntry(
    val reviewedAtMs: Long,
    val gradeCode: Int,
)

private data class DayStepSpec(
    val dayStartMs: Long,
    val dueLimit: Int,
    val newLimit: Int,
    val retention: RetentionTarget,
)

private data class DayStepOutcome(
    val matureCount: Int,
    val youngCount: Int,
    val newCount: Int,
)

@Singleton
class TimelineProjector @Inject constructor(
    private val scheduler: MemoryScheduler,
) {
    fun project(
        logs: List<RawLogSample>,
        simInput: ForecastSimInput,
    ): TimelineProjection {
        val history = buildHistory(logs, simInput.dayStartMs)
        val (forecast, finishDays) = simulateForecast(simInput)
        return TimelineProjection(
            historyBars = history,
            forecastBars = forecast,
            estimatedFinishDays = finishDays,
        )
    }

    private fun buildHistory(
        logs: List<RawLogSample>,
        todayStartMs: Long,
    ): List<HistoryDayBar> {
        val dayMs = StudyDayClock.MILLIS_PER_DAY
        val windowStart = todayStartMs - (TIMELINE_DAYS - 1) * dayMs
        val todayEnd = todayStartMs + dayMs
        val earliestByDayCard = Array(TIMELINE_DAYS) {
            HashMap<String, FirstGradeEntry>()
        }
        for (sample in logs) {
            val ts = sample.reviewedAtMs
            if (ts < windowStart || ts >= todayEnd) continue
            val dayIdx = ((ts - windowStart) / dayMs)
                .toInt()
                .coerceIn(0, TIMELINE_DAYS - 1)
            val dayMap = earliestByDayCard[dayIdx]
            val prev = dayMap[sample.cardId]
            if (prev == null || ts < prev.reviewedAtMs) {
                dayMap[sample.cardId] = FirstGradeEntry(ts, sample.gradeCode)
            }
        }
        return (0 until TIMELINE_DAYS).map { idx ->
            val label = historyDayLabel(idx)
            var easy = 0
            var good = 0
            var hard = 0
            var again = 0
            for (entry in earliestByDayCard[idx].values) {
                when (entry.gradeCode) {
                    ReviewGrade.EASY.code -> easy++
                    ReviewGrade.GOOD.code -> good++
                    ReviewGrade.HARD.code -> hard++
                    else -> again++
                }
            }
            HistoryDayBar(
                dayLabel = label,
                easyCount = easy,
                goodCount = good,
                hardCount = hard,
                againCount = again,
            )
        }
    }

    private fun historyDayLabel(idx: Int): String =
        when (val daysAgo = (TIMELINE_DAYS - 1) - idx) {
            0 -> "今"
            1 -> "昨"
            2 -> "前"
            else -> "-$daysAgo"
        }

    private fun simulateForecast(
        input: ForecastSimInput,
    ): Pair<List<ForecastDayBar>, Int> {
        val cohorts = ArrayList<SimCohort>(input.activeStates.size + 32)
        for (st in input.activeStates) {
            cohorts.add(SimCohort(st, scheduler.s90Days(st), 1))
        }
        var unseenLeft = input.unseenPoolCount
        var finishDay = if (unseenLeft <= 0) 0 else -1
        val todaySpec = DayStepSpec(
            dayStartMs = input.dayStartMs,
            dueLimit = input.todayRemainingDue,
            newLimit = min(unseenLeft, input.todayRemainingNew),
            retention = input.retention,
        )
        val todayOut = stepSingleDay(cohorts, todaySpec)
        unseenLeft -= todayOut.newCount
        if (unseenLeft <= 0 && finishDay < 0) finishDay = 0
        val dayCap = input.dailyTotalCap.remainingAfter(0)
        val bars = ArrayList<ForecastDayBar>(TIMELINE_DAYS)
        val dayMs = StudyDayClock.MILLIS_PER_DAY
        for (offset in 1..TIMELINE_DAYS) {
            val simSpec = DayStepSpec(
                dayStartMs = input.dayStartMs + offset * dayMs,
                dueLimit = dayCap,
                newLimit = min(unseenLeft, input.dailyNewLimit),
                retention = input.retention,
            )
            val outcome = stepSingleDay(cohorts, simSpec)
            unseenLeft -= outcome.newCount
            if (unseenLeft <= 0 && finishDay < 0) finishDay = offset
            val label = when (offset) {
                1 -> "明"
                2 -> "后"
                else -> "+$offset"
            }
            bars.add(
                ForecastDayBar(
                    dayLabel = label,
                    matureCount = outcome.matureCount,
                    youngCount = outcome.youngCount,
                    newCount = outcome.newCount,
                ),
            )
        }
        val etaDays = resolveFinishDays(finishDay, unseenLeft, bars, input)
        return bars to etaDays
    }

    private fun stepSingleDay(
        cohorts: MutableList<SimCohort>,
        spec: DayStepSpec,
    ): DayStepOutcome {
        val dayEndMs = spec.dayStartMs + StudyDayClock.MILLIS_PER_DAY
        val midMs = spec.dayStartMs + NOON_OFFSET_MS
        val dueList = cohorts.filter { it.state.dueEpochMs < dayEndMs }
            .sortedBy { it.s90 }
        var remDue = spec.dueLimit.coerceAtLeast(0)
        var mature = 0
        var young = 0
        for (cohort in dueList) {
            if (remDue <= 0) break
            val take = min(cohort.count, remDue)
            val preS90 = cohort.s90
            val nextState = advanceGood(
                current = cohort.state,
                dayStartMs = spec.dayStartMs,
                nowMs = midMs,
                retention = spec.retention,
            )
            val nextS90 = scheduler.s90Days(nextState)
            if (take == cohort.count) {
                cohort.state = nextState
                cohort.s90 = nextS90
            } else {
                cohort.count -= take
                cohorts.add(SimCohort(nextState, nextS90, take))
            }
            if (preS90 >= MATURE_STABILITY_DAYS) {
                mature += take
            } else {
                young += take
            }
            remDue -= take
        }
        val actualNew = min(spec.newLimit.coerceAtLeast(0), remDue)
        if (actualNew > 0) {
            val initState = advanceGood(
                current = null,
                dayStartMs = spec.dayStartMs,
                nowMs = midMs,
                retention = spec.retention,
            )
            val initS90 = scheduler.s90Days(initState)
            cohorts.add(SimCohort(initState, initS90, actualNew))
        }
        return DayStepOutcome(mature, young, actualNew)
    }

    private fun advanceGood(
        current: MemoryState?,
        dayStartMs: Long,
        nowMs: Long,
        retention: RetentionTarget,
    ): MemoryState {
        val ctx = SchedulingContext(
            cardId = SIM_CARD_ID,
            current = current,
            nowEpochMs = nowMs,
            dayStartEpochMs = dayStartMs,
            retention = retention,
        )
        return scheduler.commit(ctx, ReviewGrade.GOOD).nextState
    }

    private fun resolveFinishDays(
        finishDay: Int,
        unseenLeft: Int,
        bars: List<ForecastDayBar>,
        input: ForecastSimInput,
    ): Int {
        if (finishDay >= 0) return finishDay
        val tailSum = bars.takeLast(5).sumOf { it.newCount }
        val steadyCapPace = if (input.dailyTotalCap.isUnlimited) {
            input.dailyNewLimit
        } else {
            max(1, min(input.dailyNewLimit, input.dailyTotalCap.rawLimit / 5))
        }
        val pace = if (tailSum > 0) {
            max(1, min(tailSum / 5, steadyCapPace))
        } else {
            steadyCapPace.coerceAtLeast(1)
        }
        return TIMELINE_DAYS + (unseenLeft + pace - 1) / pace
    }
}
