package org.plos.edu.lang.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import org.plos.edu.lang.database.CardStateEntity
import org.plos.edu.lang.database.PassageEntity
import org.plos.edu.lang.database.PassageLineEntity
import org.plos.edu.lang.database.ReviewLogEntity
import org.plos.edu.lang.database.StudyDao
import org.plos.edu.lang.database.TrackEntity
import org.plos.edu.lang.database.UnitEntity
import org.plos.edu.lang.database.WordDispositionEntity
import org.plos.edu.lang.engine.CardId
import org.plos.edu.lang.engine.ExampleItem
import org.plos.edu.lang.engine.LanguageProfile
import org.plos.edu.lang.engine.LearnerPreferences
import org.plos.edu.lang.engine.MemoryScheduler
import org.plos.edu.lang.engine.MemoryState
import org.plos.edu.lang.engine.NewWordOrder
import org.plos.edu.lang.engine.QueuePlanInput
import org.plos.edu.lang.engine.ReadingBookmark
import org.plos.edu.lang.engine.RetentionTarget
import org.plos.edu.lang.engine.ReviewCommit
import org.plos.edu.lang.engine.ReviewGrade
import org.plos.edu.lang.engine.RootId
import org.plos.edu.lang.engine.SchedulingContext
import org.plos.edu.lang.engine.SessionPlanner
import org.plos.edu.lang.engine.SessionQuota
import org.plos.edu.lang.engine.StudyCard
import org.plos.edu.lang.engine.StudyDayClock
import org.plos.edu.lang.engine.StudyTrack
import org.plos.edu.lang.engine.TrackId
import org.plos.edu.lang.engine.UndoPreview
import org.plos.edu.lang.engine.UndoRestoration
import org.plos.edu.lang.engine.WordDisposition

sealed class UndoRecord {
    abstract val cardId: CardId
    abstract val langCode: String

    abstract suspend fun revert(dao: StudyDao): UndoRestoration

    abstract fun toRestoration(): UndoRestoration

    abstract fun restoredMemoryState(
        memStates: Map<String, MemoryState>,
        evaluator: MasteryEvaluator,
    ): MemoryState?

    abstract fun isValidAgainst(logs: List<ReviewLogEntity>): Boolean

    data class Graded(
        override val cardId: CardId,
        override val langCode: String,
        val chosenGrade: ReviewGrade,
        val previousState: CardStateEntity?,
        val insertedLogId: Long,
    ) : UndoRecord() {
        override suspend fun revert(dao: StudyDao): UndoRestoration {
            dao.deleteReviewLogById(insertedLogId)
            if (previousState == null) {
                dao.deleteCardState(cardId.value)
            } else {
                dao.upsertCardState(previousState)
            }
            return toRestoration()
        }

        override fun toRestoration(): UndoRestoration = UndoRestoration(
            cardId = cardId,
            previousGrade = chosenGrade,
            restoredMasteredMark = false,
        )

        override fun restoredMemoryState(
            memStates: Map<String, MemoryState>,
            evaluator: MasteryEvaluator,
        ): MemoryState? = previousState?.let(evaluator::toMemoryState)

        override fun isValidAgainst(logs: List<ReviewLogEntity>): Boolean =
            logs.maxOfOrNull { it.logId } == insertedLogId
    }

    data class MasteredMark(
        override val cardId: CardId,
        override val langCode: String,
        val rootId: RootId,
        val previousDisposition: WordDispositionEntity?,
    ) : UndoRecord() {
        override suspend fun revert(dao: StudyDao): UndoRestoration {
            if (previousDisposition == null) {
                dao.deleteWordDispositions(langCode, listOf(rootId.value))
            } else {
                dao.upsertWordDispositions(listOf(previousDisposition))
            }
            return toRestoration()
        }

        override fun toRestoration(): UndoRestoration = UndoRestoration(
            cardId = cardId,
            previousGrade = null,
            restoredMasteredMark = true,
        )

        override fun restoredMemoryState(
            memStates: Map<String, MemoryState>,
            evaluator: MasteryEvaluator,
        ): MemoryState? = memStates[cardId.value]

        override fun isValidAgainst(logs: List<ReviewLogEntity>): Boolean =
            true
    }
}

data class SessionRuntimeState(
    val undoRecord: UndoRecord? = null,
    val recentCards: List<CardId> = emptyList(),
    val priorityCardId: CardId? = null,
    val activeRestoration: UndoRestoration? = null,
) {
    fun afterUndo(restoration: UndoRestoration): SessionRuntimeState =
        SessionRuntimeState(
            undoRecord = null,
            recentCards = recentCards.filterNot { it == restoration.cardId },
            priorityCardId = restoration.cardId,
            activeRestoration = restoration,
        )
}

data class DynamicContent(
    val units: List<UnitEntity>,
    val customTracks: List<TrackEntity>,
    val passages: List<PassageEntity>,
    val passageLines: List<PassageLineEntity>,
    val bookmarks: Map<String, ReadingBookmark>,
)

data class DynamicProgress(
    val cardStates: List<CardStateEntity>,
    val reviewLogs: List<ReviewLogEntity>,
    val dispositions: List<WordDispositionEntity>,
    val runtime: SessionRuntimeState,
)

data class WorkspaceBuildInput(
    val prefs: LearnerPreferences,
    val languages: List<LanguageProfile>,
    val pack: LoadedLanguagePack,
    val content: DynamicContent,
    val progress: DynamicProgress,
)

data class ReviewStepSpec(
    val cardId: CardId,
    val storedState: CardStateEntity?,
    val retention: RetentionTarget,
    val grade: ReviewGrade,
)

private data class UndoPreviewContext(
    val spec: CurriculumBuildSpec,
    val concordance: Map<RootId, ExampleItem>,
    val memStates: Map<String, MemoryState>,
    val retention: RetentionTarget,
)

@Singleton
class WorkspaceAssembler @Inject constructor(
    private val scheduler: MemoryScheduler,
    private val sessionPlanner: SessionPlanner,
    private val evaluator: MasteryEvaluator,
    private val curriculumAssembler: CurriculumAssembler,
) {
    fun computeNextReview(spec: ReviewStepSpec, nowMs: Long): ReviewCommit {
        val existingState = spec.storedState?.let(evaluator::toMemoryState)
        val dayStartMs = StudyDayClock.dayStartMs(nowMs)
        val schedCtx = SchedulingContext(
            cardId = spec.cardId,
            current = existingState,
            nowEpochMs = nowMs,
            dayStartEpochMs = dayStartMs,
            retention = spec.retention,
        )
        return scheduler.commit(schedCtx, spec.grade)
    }

    fun assemble(input: WorkspaceBuildInput): LanguageWorkspace {
        val nowMs = System.currentTimeMillis()
        val dayStartMs = StudyDayClock.dayStartMs(nowMs)
        val pack = input.pack
        val langCode = pack.profile.code
        val lang = langCode.value
        val progress = input.progress
        val cardStates = progress.cardStates
        val dispositionMap = progress.dispositions.associate { d ->
            RootId(d.rootId) to WordDisposition.fromCode(d.dispositionCode)
        }
        val evalInput = MasteryEvalInput(
            roots = pack.roots,
            states = cardStates,
            dispositions = dispositionMap,
            nowMs = nowMs,
        )
        val mastery = evaluator.evaluateMastery(evalInput)
        val userTracks = input.content.customTracks.map { tr ->
            val validIds = CsvCodec.decode(tr.rootIdsCsv)
                .map(::RootId)
                .filter { it in pack.rootMap }
                .distinct()
            StudyTrack(
                id = TrackId(tr.id),
                category = StudyTrack.CUSTOM_CATEGORY,
                title = tr.title,
                summary = tr.summary,
                rootIds = validIds,
                defaultOrder = NewWordOrder.fromTag(tr.defaultOrder),
                isCustom = true,
            )
        }
        val allTracks = userTracks + pack.tracks
        val chosenTrackId = input.prefs.trackFor(langCode)
            ?: pack.defaultTrackId
        val rawTrack = allTracks.firstOrNull { it.id == chosenTrackId }
            ?: pack.tracks.firstOrNull { it.id == pack.defaultTrackId }
            ?: allTracks.first()
        val wordOrder = input.prefs.orderFor(langCode, rawTrack)
        val activeTrack = rawTrack.orderedBy(
            order = wordOrder,
            rootMap = pack.rootMap,
            shuffleSeed = input.prefs.shuffleSeed,
        )
        val scanned = curriculumAssembler.scanPassages(
            pack = pack,
            content = input.content,
            mastery = mastery,
        )
        val currSpec = CurriculumBuildSpec(
            pack = pack,
            activeTrack = activeTrack,
            content = input.content,
            mastery = mastery,
        )
        val curriculum = curriculumAssembler.buildCurriculum(
            spec = currSpec,
            cardStates = cardStates,
            concordance = scanned.concordanceByRoot,
        )
        val memStates = cardStates.associate { entity ->
            entity.cardId to evaluator.toMemoryState(entity)
        }
        val todayLogs = progress.reviewLogs.filter {
            it.reviewedAtMs >= dayStartMs
        }
        val studiedTodayIds = todayLogs.mapTo(HashSet()) { it.cardId }
        val logsByCard = todayLogs.groupBy { it.cardId }
        val newTodayStates = cardStates.filter { state ->
            val countToday = logsByCard[state.cardId]?.size ?: 0
            state.reps > 0 && countToday >= state.reps
        }
        val rootsToday = newTodayStates.mapTo(HashSet()) { state ->
            RootId(state.rootId)
        }
        val aheadCutoff = nowMs + 20L * 60_000L
        val finishedToday = cardStates.count { state ->
            if (state.cardId !in studiedTodayIds) return@count false
            val dueNow = state.dueEpochMs <= nowMs
            val shortRelearn = state.longStability < 1.0 &&
                state.dueEpochMs <= aheadCutoff
            !dueNow && !shortRelearn
        }
        val prefs = input.prefs
        val activeBonus = prefs.activeBonusToday(dayStartMs)
        val effectiveNewLimit = prefs.dailyNewLimit + activeBonus
        val effectiveTotalCap = prefs.dailyTotalCap.withBonus(activeBonus)
        val quota = SessionQuota(
            newAllowance = max(0, effectiveNewLimit - newTodayStates.size),
            totalAllowance = effectiveTotalCap.remainingAfter(finishedToday),
        )
        val hasMoreNew = curriculum.newEligibleIds.any { uid ->
            CardId.of(lang, uid.value).value !in memStates
        }
        val runtime = progress.runtime
        val planInput = QueuePlanInput(
            langCode = langCode,
            units = curriculum.units,
            newEligibleUnitIds = curriculum.newEligibleIds,
            rootMap = pack.rootMap,
            membersByRoot = pack.membersByRoot,
            stateByCardId = memStates,
            retention = prefs.retention,
            nowMs = nowMs,
            dayStartMs = dayStartMs,
            quota = quota,
            rootsIntroducedToday = rootsToday,
            recentCardIds = runtime.recentCards.toSet(),
            priorityCardId = runtime.priorityCardId,
        )
        val sessionPlan = sessionPlanner.planSession(planInput)
        val sessionCards = sessionPlan.queue
        val learnedToday = curriculumAssembler.buildLearnedToday(
            spec = currSpec,
            progress = progress,
            concordance = scanned.concordanceByRoot,
            dayStartMs = dayStartMs,
        )
        val analyticsSpec = AnalyticsEvalInput(
            activeTrack = activeTrack,
            mastery = mastery,
            preferences = prefs,
            cardStates = cardStates,
            reviewLogs = progress.reviewLogs,
            dayStartMs = dayStartMs,
            sessionPlan = sessionPlan,
        )
        val analytics = evaluator.evaluateAnalytics(analyticsSpec)
        val undoCtx = UndoPreviewContext(
            spec = currSpec,
            concordance = scanned.concordanceByRoot,
            memStates = memStates,
            retention = prefs.retention,
        )
        val undoPreview = buildUndoPreview(undoCtx, progress, nowMs, dayStartMs)
        val topId = sessionCards.firstOrNull()?.id
        val activeRest = runtime.activeRestoration
            ?.takeIf { it.cardId == topId }
        return LanguageWorkspace(
            languages = input.languages,
            activeProfile = pack.profile,
            tracks = allTracks,
            activeTrack = activeTrack,
            styleLabels = pack.styleLabels,
            preferences = prefs.copy(activeLang = langCode),
            analytics = analytics,
            sessionQueue = sessionCards,
            learnedToday = learnedToday,
            hasRemainingNewUnits = hasMoreNew,
            undoPreview = undoPreview,
            activeRestoration = activeRest,
            passages = scanned.passages,
            mastery = mastery,
            morphology = pack.morphology,
        )
    }

    private fun buildUndoPreview(
        ctx: UndoPreviewContext,
        progress: DynamicProgress,
        nowMs: Long,
        dayStartMs: Long,
    ): UndoPreview? {
        val record = progress.runtime.undoRecord ?: return null
        val pack = ctx.spec.pack
        if (record.langCode != pack.profile.code.value) return null
        if (!record.isValidAgainst(progress.reviewLogs)) return null
        val parsed = record.cardId.parse() ?: return null
        val unit = curriculumAssembler.resolveUnitById(
            spec = ctx.spec,
            concordance = ctx.concordance,
            rawUnitId = parsed.unitId,
        ) ?: return null
        val root = pack.rootMap[unit.targetRootId] ?: return null
        val priorState = record.restoredMemoryState(ctx.memStates, evaluator)
        val schedCtx = SchedulingContext(
            cardId = record.cardId,
            current = priorState,
            nowEpochMs = nowMs,
            dayStartEpochMs = dayStartMs,
            retention = ctx.retention,
        )
        val card = StudyCard(
            id = record.cardId,
            unit = unit,
            root = root,
            familyMembers = pack.membersByRoot[root.id].orEmpty(),
            state = priorState,
            preview = scheduler.preview(schedCtx),
        )
        return UndoPreview(card = card, restoration = record.toRestoration())
    }
}
