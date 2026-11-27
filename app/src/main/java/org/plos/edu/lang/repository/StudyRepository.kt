package org.plos.edu.lang.repository

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.plos.edu.lang.database.CardStateEntity
import org.plos.edu.lang.database.LanguageEntity
import org.plos.edu.lang.database.MainDatabase
import org.plos.edu.lang.database.ReviewLogEntity
import org.plos.edu.lang.database.WordDispositionEntity
import org.plos.edu.lang.engine.CardId
import org.plos.edu.lang.engine.LangCode
import org.plos.edu.lang.engine.LearnerPreferences
import org.plos.edu.lang.engine.MasteryStage
import org.plos.edu.lang.engine.ParsedCardKey
import org.plos.edu.lang.engine.PreferenceMutation
import org.plos.edu.lang.engine.ReadingBookmark
import org.plos.edu.lang.engine.ReviewGrade
import org.plos.edu.lang.engine.RootId
import org.plos.edu.lang.engine.StudyDayClock
import org.plos.edu.lang.engine.TrackId
import org.plos.edu.lang.engine.UndoRestoration
import org.plos.edu.lang.engine.UnitId
import org.plos.edu.lang.engine.WordDisposition
import org.plos.edu.lang.modules.DataStoreManager

private const val RECENT_HISTORY_LIMIT: Int = 3
private const val SQLITE_CHUNK_SIZE: Int = 400

private data class ActiveSelection(
    val prefs: LearnerPreferences,
    val languages: List<LanguageEntity>,
)

private data class DispositionBackup(
    val langCode: String,
    val targetRootIds: List<String>,
    val priorDispositions: List<WordDispositionEntity>,
    val clearedCardStates: List<CardStateEntity>,
)

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class StudyRepository @Inject constructor(
    private val database: MainDatabase,
    private val dataStore: DataStoreManager,
    private val packPersistence: PackPersistence,
    private val assembler: WorkspaceAssembler,
) : StudyGateway() {
    private val dao = database.studyDao()
    private val reviewMutex = Mutex()
    private val runtimeFlow = MutableStateFlow(SessionRuntimeState())
    @Volatile
    private var dispositionBackup: DispositionBackup? = null

    override fun observeWorkspace(): Flow<LanguageWorkspace> {
        val headerFlow = combine(
            dataStore.preferencesFlow,
            dao.observeLanguages(),
        ) { prefs, languages ->
            ActiveSelection(prefs = prefs, languages = languages)
        }
        return headerFlow
            .flatMapLatest(::observeSelectedLanguage)
            .filterNotNull()
            .flowOn(Dispatchers.Default)
    }

    override suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        packPersistence.ensureSeeded()
    }

    override suspend fun recordReview(
        cardId: CardId,
        grade: ReviewGrade,
    ) = withContext(Dispatchers.IO) {
        reviewMutex.withLock {
            val parsed = cardId.parse() ?: return@withLock
            val rootIdStr = resolveTargetRootId(parsed) ?: return@withLock
            val prefs = dataStore.current()
            val storedEntity = dao.findCardState(cardId.value)
            val nowMs = System.currentTimeMillis()
            val stepSpec = ReviewStepSpec(
                cardId = cardId,
                storedState = storedEntity,
                retention = prefs.retention,
                grade = grade,
            )
            val next = assembler.computeNextReview(stepSpec, nowMs).nextState
            val nextEntity = CardStateEntity(
                cardId = cardId.value,
                langCode = parsed.langCode,
                unitId = parsed.unitId,
                rootId = rootIdStr,
                longStability = next.longStability.value,
                shortStability = next.shortStability.value,
                difficulty = next.difficulty.value,
                dueEpochMs = next.dueEpochMs,
                lastReviewEpochMs = next.lastReviewEpochMs,
                reps = next.reps,
                lapses = next.lapses,
            )
            val logEntity = ReviewLogEntity(
                cardId = cardId.value,
                langCode = parsed.langCode,
                gradeCode = grade.code,
                reviewedAtMs = nowMs,
                stabilityAfter = next.longStability.value,
                difficultyAfter = next.difficulty.value,
            )
            val logId = database.withTransaction {
                dao.upsertCardState(nextEntity)
                dao.insertReviewLog(logEntity)
            }
            val undo = UndoRecord.Graded(
                cardId = cardId,
                langCode = parsed.langCode,
                chosenGrade = grade,
                previousState = storedEntity,
                insertedLogId = logId,
            )
            pushUndoRecord(cardId, undo)
        }
    }

    override suspend fun markCardMastered(
        cardId: CardId,
    ) = withContext(Dispatchers.IO) {
        reviewMutex.withLock {
            val parsed = cardId.parse() ?: return@withLock
            val rootIdStr = resolveTargetRootId(parsed) ?: return@withLock
            val prior = dao.findWordDispositions(
                parsed.langCode,
                listOf(rootIdStr),
            ).firstOrNull()
            val entity = WordDispositionEntity(
                langCode = parsed.langCode,
                rootId = rootIdStr,
                dispositionCode = WordDisposition.MASTERED.code,
                updatedAtMs = System.currentTimeMillis(),
            )
            database.withTransaction {
                dao.upsertWordDispositions(listOf(entity))
            }
            val undo = UndoRecord.MasteredMark(
                cardId = cardId,
                langCode = parsed.langCode,
                rootId = RootId(rootIdStr),
                previousDisposition = prior,
            )
            pushUndoRecord(cardId, undo)
        }
    }

    override suspend fun undoLastReview(): UndoRestoration? =
        withContext(Dispatchers.IO) {
            reviewMutex.withLock {
                val record = runtimeFlow.value.undoRecord
                    ?: return@withLock null
                val restoration = database.withTransaction {
                    record.revert(dao)
                }
                runtimeFlow.update { it.afterUndo(restoration) }
                restoration
            }
        }

    override suspend fun selectTrack(trackId: TrackId) {
        val lang = dataStore.current().activeLang
        runtimeFlow.update { prev ->
            prev.copy(priorityCardId = null, activeRestoration = null)
        }
        dataStore.setTrackForLanguage(lang, trackId)
    }

    override suspend fun updateWordDispositions(
        rootIds: Set<RootId>,
        disposition: WordDisposition,
    ): Int = withContext(Dispatchers.IO) {
        if (rootIds.isEmpty()) return@withContext 0
        reviewMutex.withLock {
            val lang = dataStore.current().activeLang.value
            val rawIds = rootIds.map { it.value }
            val backup = captureDispositionBackup(lang, rawIds, disposition)
            dispositionBackup = backup
            database.withTransaction {
                applyDispositionWrite(backup, disposition)
            }
            rawIds.size
        }
    }

    override suspend fun undoWordDispositions(): Int =
        withContext(Dispatchers.IO) {
            reviewMutex.withLock {
                val backup = dispositionBackup ?: return@withContext 0
                dispositionBackup = null
                database.withTransaction {
                    restoreDispositionBackup(backup)
                }
                backup.targetRootIds.size
            }
        }

    override suspend fun selectLanguage(langCode: LangCode) {
        val found = withContext(Dispatchers.IO) {
            dao.findLanguage(langCode.value)
        } ?: return
        runtimeFlow.value = SessionRuntimeState()
        dataStore.setLanguage(LangCode(found.code))
    }

    override suspend fun mutatePreference(mutation: PreferenceMutation) {
        dataStore.mutate(mutation)
    }

    override suspend fun boostTodayNewQuota(delta: Int) {
        val dayStartMs = StudyDayClock.dayStartMs(System.currentTimeMillis())
        dataStore.addTodayBonusQuota(dayStartMs, delta)
    }

    private suspend fun captureDispositionBackup(
        lang: String,
        rawIds: List<String>,
        disposition: WordDisposition,
    ): DispositionBackup {
        val priorDisps = ArrayList<WordDispositionEntity>()
        val clearedStates = ArrayList<CardStateEntity>()
        for (chunk in rawIds.chunked(SQLITE_CHUNK_SIZE)) {
            priorDisps.addAll(dao.findWordDispositions(lang, chunk))
            if (disposition == WordDisposition.AUTO) {
                val graduated = dao.findCardStatesByRoots(lang, chunk)
                    .filter { it.longStability >= MasteryStage.MASTERED_DAYS }
                clearedStates.addAll(graduated)
            }
        }
        return DispositionBackup(
            langCode = lang,
            targetRootIds = rawIds,
            priorDispositions = priorDisps,
            clearedCardStates = clearedStates,
        )
    }

    private suspend fun applyDispositionWrite(
        backup: DispositionBackup,
        disposition: WordDisposition,
    ) {
        val lang = backup.langCode
        val rawIds = backup.targetRootIds
        if (disposition == WordDisposition.AUTO) {
            for (chunk in rawIds.chunked(SQLITE_CHUNK_SIZE)) {
                dao.deleteWordDispositions(lang, chunk)
            }
            val clearedRoots = backup.clearedCardStates
                .map { it.rootId }
                .distinct()
            for (chunk in clearedRoots.chunked(SQLITE_CHUNK_SIZE)) {
                dao.deleteCardStatesByRoots(lang, chunk)
            }
            return
        }
        val nowMs = System.currentTimeMillis()
        val updated = rawIds.map { rid ->
            WordDispositionEntity(
                langCode = lang,
                rootId = rid,
                dispositionCode = disposition.code,
                updatedAtMs = nowMs,
            )
        }
        dao.upsertWordDispositions(updated)
    }

    private suspend fun restoreDispositionBackup(backup: DispositionBackup) {
        for (chunk in backup.targetRootIds.chunked(SQLITE_CHUNK_SIZE)) {
            dao.deleteWordDispositions(backup.langCode, chunk)
        }
        if (backup.priorDispositions.isNotEmpty()) {
            dao.upsertWordDispositions(backup.priorDispositions)
        }
        if (backup.clearedCardStates.isNotEmpty()) {
            dao.upsertCardStates(backup.clearedCardStates)
        }
    }

    private suspend fun resolveTargetRootId(parsed: ParsedCardKey): String? {
        val explicit = dao.findUnit(parsed.langCode, parsed.unitId)
        if (explicit != null) return explicit.targetRootId
        val synRoot = UnitId(parsed.unitId).synthesizedRootId() ?: return null
        return dao.findRoot(parsed.langCode, synRoot.value)?.id
    }

    private fun pushUndoRecord(cardId: CardId, undo: UndoRecord) {
        runtimeFlow.update { prev ->
            val filtered = prev.recentCards.filterNot { it == cardId }
            val updatedRecent = (listOf(cardId) + filtered)
                .take(RECENT_HISTORY_LIMIT)
            SessionRuntimeState(
                undoRecord = undo,
                recentCards = updatedRecent,
                priorityCardId = null,
            )
        }
    }

    private fun observeSelectedLanguage(
        selection: ActiveSelection,
    ): Flow<LanguageWorkspace?> {
        val available = selection.languages
        if (available.isEmpty()) return flowOf(null)
        val activeEntity = available.firstOrNull {
            it.code == selection.prefs.activeLang.value
        } ?: available.first()
        val code = activeEntity.code
        val profiles = available.map(LoadedLanguagePack::profileOf)
        val contentFlow = combine(
            dao.observeUnits(code),
            dao.observeCustomTracks(code),
            dao.observePassages(code),
            dao.observePassageLines(code),
            dao.observeReadingProgress(),
        ) { units, customTracks, passages, lines, bookmarks ->
            val bookmarkMap = bookmarks.associate { b ->
                val mark = ReadingBookmark(
                    lineIndex = b.lineIndex,
                    scrollOffset = b.scrollOffset,
                    hasRead = true,
                    completed = b.completed,
                )
                b.passageId to mark
            }
            DynamicContent(units, customTracks, passages, lines, bookmarkMap)
        }
        val progressFlow = combine(
            dao.observeCardStates(code),
            dao.observeReviewLogs(code),
            dao.observeWordDispositions(code),
            runtimeFlow,
        ) { states, logs, dispositions, runtime ->
            DynamicProgress(states, logs, dispositions, runtime)
        }
        return combine(contentFlow, progressFlow) { content, progress ->
            val loadedPack = packPersistence.resolvePack(activeEntity)
            val buildInput = WorkspaceBuildInput(
                prefs = selection.prefs,
                languages = profiles,
                pack = loadedPack,
                content = content,
                progress = progress,
            )
            assembler.assemble(buildInput)
        }
    }
}
