package org.plos.edu.lang.engine

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

private const val LEARN_AHEAD_MS: Long = 20L * 60_000L
private const val DUE_BURST: Int = 2
private const val MIN_RELEARN_GAP: Int = 4
private const val MIN_SIBLING_GAP: Int = 4

data class SessionQuota(
    val newAllowance: Int,
    val totalAllowance: Int,
)

data class SessionPlanResult(
    val queue: List<StudyCard>,
    val deferredDueCount: Int,
)

data class QueuePlanInput(
    val langCode: LangCode,
    val units: List<StudyUnit>,
    val newEligibleUnitIds: Set<UnitId>,
    val rootMap: Map<RootId, RootEntry>,
    val membersByRoot: Map<RootId, List<LexemeEntry>>,
    val stateByCardId: Map<String, MemoryState>,
    val retention: RetentionTarget,
    val nowMs: Long,
    val dayStartMs: Long,
    val quota: SessionQuota,
    val rootsIntroducedToday: Set<RootId>,
    val recentCardIds: Set<CardId>,
    val priorityCardId: CardId?,
)

private data class CardSeed(
    val cardId: CardId,
    val unit: StudyUnit,
    val root: RootEntry,
    val state: MemoryState?,
)

@Singleton
class SessionPlanner @Inject constructor(
    private val scheduler: MemoryScheduler,
) {
    fun planSession(input: QueuePlanInput): SessionPlanResult {
        val dailyDueAll = ArrayList<CardSeed>()
        val relearnSeeds = ArrayList<CardSeed>()
        val unseenCandidates = ArrayList<CardSeed>()
        val blockedNewRoots = HashSet<RootId>(input.rootsIntroducedToday)
        val aheadCutoff = input.nowMs + LEARN_AHEAD_MS
        val lang = input.langCode.value
        for (unit in input.units) {
            val root = input.rootMap[unit.targetRootId] ?: continue
            val cid = CardId.of(lang, unit.id.value)
            val state = input.stateByCardId[cid.value]
            if (state == null) {
                val canPick = unit.id in input.newEligibleUnitIds &&
                    root.id !in blockedNewRoots
                if (canPick) {
                    unseenCandidates.add(CardSeed(cid, unit, root, null))
                    blockedNewRoots.add(root.id)
                }
                continue
            }
            val isDueNow = state.dueEpochMs <= input.nowMs
            val isShortRelearn = state.longStability.value < 1.0 &&
                state.dueEpochMs <= aheadCutoff
            if (!isDueNow && !isShortRelearn) continue
            val isRecent = cid in input.recentCardIds || !isDueNow
            val seed = CardSeed(cid, unit, root, state)
            if (isRecent) {
                relearnSeeds.add(seed)
            } else {
                dailyDueAll.add(seed)
            }
        }
        val dueBudget = (input.quota.totalAllowance - relearnSeeds.size)
            .coerceAtLeast(0)
        val chosenDue = selectDueWithinBudget(dailyDueAll, dueBudget, input)
        val deferredDue = (dailyDueAll.size - chosenDue.size).coerceAtLeast(0)
        val remAfterDue = (dueBudget - chosenDue.size).coerceAtLeast(0)
        val newLimit = min(input.quota.newAllowance, remAfterDue)
        val newSeeds = unseenCandidates.take(newLimit)
        relearnSeeds.sortBy { it.state?.dueEpochMs ?: 0L }
        val baseSeeds = interleaveSeeds(chosenDue, newSeeds)
        val mergedSeeds = insertRelearning(baseSeeds, relearnSeeds)
        val spacedSeeds = spaceSiblingRoots(mergedSeeds)
        val orderedSeeds = promotePriority(spacedSeeds, input)
        val cards = orderedSeeds.map { materializeCard(it, input) }
        return SessionPlanResult(queue = cards, deferredDueCount = deferredDue)
    }

    private fun selectDueWithinBudget(
        allDue: MutableList<CardSeed>,
        budget: Int,
        input: QueuePlanInput,
    ): List<CardSeed> {
        if (budget <= 0 || allDue.isEmpty()) return emptyList()
        val picked = if (allDue.size <= budget) {
            allDue
        } else {
            allDue.sortBy { seed ->
                seed.state?.let(scheduler::s90Days) ?: Double.MAX_VALUE
            }
            ArrayList(allDue.subList(0, budget))
        }
        picked.sortBy { dailyOrderHash(input.dayStartMs, it.cardId) }
        return picked
    }

    private fun interleaveSeeds(
        due: List<CardSeed>,
        fresh: List<CardSeed>,
    ): MutableList<CardSeed> {
        val total = due.size + fresh.size
        val result = ArrayList<CardSeed>(total)
        var dIdx = 0
        var nIdx = 0
        while (dIdx < due.size || nIdx < fresh.size) {
            var burst = 0
            while (dIdx < due.size && burst < DUE_BURST) {
                result.add(due[dIdx++])
                burst++
            }
            if (nIdx < fresh.size) {
                result.add(fresh[nIdx++])
            }
        }
        return result
    }

    private fun insertRelearning(
        base: MutableList<CardSeed>,
        relearn: List<CardSeed>,
    ): MutableList<CardSeed> {
        if (relearn.isEmpty()) return base
        for ((idx, item) in relearn.withIndex()) {
            val targetPos = min(base.size, MIN_RELEARN_GAP + idx * 2)
            base.add(targetPos, item)
        }
        return base
    }

    private fun spaceSiblingRoots(
        seeds: MutableList<CardSeed>,
    ): MutableList<CardSeed> {
        if (seeds.size < 2) return seeds
        val placed = ArrayList<CardSeed>(seeds.size)
        val deferred = ArrayDeque<CardSeed>()
        for (candidate in seeds) {
            if (hasRecentSibling(placed, candidate.root.id)) {
                deferred.addLast(candidate)
            } else {
                placed.add(candidate)
                drainCompatibleDeferred(placed, deferred)
            }
        }
        while (deferred.isNotEmpty()) {
            placed.add(deferred.removeFirst())
        }
        return placed
    }

    private fun drainCompatibleDeferred(
        placed: MutableList<CardSeed>,
        deferred: ArrayDeque<CardSeed>,
    ) {
        if (deferred.isEmpty()) return
        val count = deferred.size
        var checked = 0
        while (checked < count && deferred.isNotEmpty()) {
            val next = deferred.removeFirst()
            if (hasRecentSibling(placed, next.root.id)) {
                deferred.addLast(next)
            } else {
                placed.add(next)
            }
            checked++
        }
    }

    private fun hasRecentSibling(
        placed: List<CardSeed>,
        rootId: RootId,
    ): Boolean {
        val start = (placed.size - MIN_SIBLING_GAP).coerceAtLeast(0)
        for (idx in start until placed.size) {
            if (placed[idx].root.id == rootId) return true
        }
        return false
    }

    private fun promotePriority(
        seeds: MutableList<CardSeed>,
        input: QueuePlanInput,
    ): List<CardSeed> {
        val priorityId = input.priorityCardId ?: return seeds
        val existingIdx = seeds.indexOfFirst { it.cardId == priorityId }
        if (existingIdx == 0) return seeds
        if (existingIdx > 0) {
            val picked = seeds.removeAt(existingIdx)
            seeds.add(0, picked)
            return seeds
        }
        val lang = input.langCode.value
        val matchUnit = input.units.firstOrNull { u ->
            CardId.of(lang, u.id.value) == priorityId
        } ?: return seeds
        val root = input.rootMap[matchUnit.targetRootId] ?: return seeds
        val state = input.stateByCardId[priorityId.value]
        seeds.add(0, CardSeed(priorityId, matchUnit, root, state))
        return seeds
    }

    private fun materializeCard(
        seed: CardSeed,
        input: QueuePlanInput,
    ): StudyCard {
        val ctx = SchedulingContext(
            cardId = seed.cardId,
            current = seed.state,
            nowEpochMs = input.nowMs,
            dayStartEpochMs = input.dayStartMs,
            retention = input.retention,
        )
        val preview = scheduler.preview(ctx)
        val members = input.membersByRoot[seed.root.id].orEmpty()
        return StudyCard(
            id = seed.cardId,
            unit = seed.unit,
            root = seed.root,
            familyMembers = members,
            state = seed.state,
            preview = preview,
        )
    }

    private fun dailyOrderHash(daySeed: Long, cardId: CardId): Long {
        val idBits = cardId.value.hashCode().toLong()
        var z = daySeed xor (idBits * -7046029254386353131L)
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }
}
