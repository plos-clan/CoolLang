package org.plos.edu.lang.repository

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.plos.edu.lang.database.MainDatabase
import org.plos.edu.lang.database.PassageEntity
import org.plos.edu.lang.database.PassageLineEntity
import org.plos.edu.lang.database.ReadingProgressEntity
import org.plos.edu.lang.database.TrackEntity
import org.plos.edu.lang.engine.CustomPassageDraft
import org.plos.edu.lang.engine.CustomTrackDraft
import org.plos.edu.lang.engine.ExtractCommitSpec
import org.plos.edu.lang.engine.LangCode
import org.plos.edu.lang.engine.NewWordOrder
import org.plos.edu.lang.engine.PassageId
import org.plos.edu.lang.engine.ReadingBookmark
import org.plos.edu.lang.engine.RootId
import org.plos.edu.lang.engine.SentenceSegmenter
import org.plos.edu.lang.engine.StudyTrack
import org.plos.edu.lang.engine.TrackId
import org.plos.edu.lang.engine.TrackMembershipOp
import org.plos.edu.lang.engine.TrackWordsMutation
import org.plos.edu.lang.modules.DataStoreManager

private data class DeletedPassageBackup(
    val passage: PassageEntity,
    val lines: List<PassageLineEntity>,
    val readingProgress: ReadingProgressEntity?,
)

@Singleton
class CorpusRepository @Inject constructor(
    private val database: MainDatabase,
    private val dataStore: DataStoreManager,
    private val segmenter: SentenceSegmenter,
) : CorpusGateway() {
    private val dao = database.studyDao()
    @Volatile
    private var deletedPassageBackup: DeletedPassageBackup? = null
    @Volatile
    private var deletedTrackBackup: TrackEntity? = null

    override suspend fun saveCustomPassage(
        draft: CustomPassageDraft,
    ): PassageId = withContext(Dispatchers.IO) {
        val code = draft.langCode.value
        val editKey = draft.editId?.value
        val existing = editKey?.let { dao.findCustomPassage(code, it) }
        val passageId = existing?.let { PassageId(it.id) }
            ?: PassageId("$code:custom_${System.currentTimeMillis()}")
        val segmented = segmenter.segment(draft.rawBody)
        val sortOrder = existing?.sortOrder ?: (dao.maxPassageOrder(code) + 1)
        val titleText = draft.title.trim().ifEmpty { "自导入短文" }
        val summary = segmenter.summarizeOpening(segmented).ifEmpty {
            titleText
        }
        val passageEntity = PassageEntity(
            langCode = code,
            id = passageId.value,
            title = titleText,
            levelTag = "自导入",
            sceneSummary = summary,
            isCustom = true,
            sortOrder = sortOrder,
        )
        val lineEntities = segmented.mapIndexed { idx, item ->
            PassageLineEntity(
                langCode = code,
                passageId = passageId.value,
                lineIndex = idx,
                speaker = item.speaker,
                text = item.text,
                sourceTranslation = "",
                contrastLabel = "",
                contrastText = "",
                grammarIdsCsv = "",
            )
        }
        database.withTransaction {
            if (existing != null) {
                dao.removePassageLines(code, passageId.value)
                val savedPos = dao.findReadingProgress(passageId.value)
                val maxLine = (lineEntities.size - 1).coerceAtLeast(0)
                if (savedPos != null && savedPos.lineIndex > maxLine) {
                    val clamped = savedPos.copy(lineIndex = maxLine)
                    dao.upsertReadingProgress(clamped)
                }
            }
            dao.upsertPassages(listOf(passageEntity))
            dao.upsertPassageLines(lineEntities)
        }
        passageId
    }

    override suspend fun deleteCustomPassage(
        passageId: PassageId,
    ): Boolean = withContext(Dispatchers.IO) {
        val lang = dataStore.current().activeLang.value
        val existing = dao.findCustomPassage(lang, passageId.value)
            ?: return@withContext false
        val lines = dao.loadLinesForPassage(lang, passageId.value)
        val progress = dao.findReadingProgress(passageId.value)
        deletedPassageBackup = DeletedPassageBackup(
            passage = existing,
            lines = lines,
            readingProgress = progress,
        )
        database.withTransaction {
            dao.removePassageLines(lang, passageId.value)
            dao.removeCustomPassage(lang, passageId.value)
            dao.deleteReadingProgress(passageId.value)
        }
        true
    }

    override suspend fun restoreDeletedPassage(): PassageId? =
        withContext(Dispatchers.IO) {
            val backup = deletedPassageBackup ?: return@withContext null
            deletedPassageBackup = null
            database.withTransaction {
                dao.upsertPassages(listOf(backup.passage))
                dao.upsertPassageLines(backup.lines)
                val savedPos = backup.readingProgress
                if (savedPos != null) {
                    dao.upsertReadingProgress(savedPos)
                }
            }
            PassageId(backup.passage.id)
        }

    override suspend fun saveReadingBookmark(
        passageId: PassageId,
        bookmark: ReadingBookmark,
    ) = withContext(Dispatchers.IO) {
        val saved = dao.findReadingProgress(passageId.value)
        val prevDone = saved?.completed == true
        val entity = ReadingProgressEntity(
            passageId = passageId.value,
            lineIndex = bookmark.lineIndex.coerceAtLeast(0),
            scrollOffset = bookmark.scrollOffset.coerceAtLeast(0),
            completed = prevDone || bookmark.completed,
        )
        dao.upsertReadingProgress(entity)
    }

    override suspend fun createCustomTrack(
        draft: CustomTrackDraft,
    ): TrackId = withContext(Dispatchers.IO) {
        val code = draft.langCode.value
        val stamp = System.currentTimeMillis()
        val trackId = TrackId("$code:utrack_$stamp")
        val cleanTitle = draft.title.trim().ifEmpty { "自建词书" }
        val uniqueIds = draft.initialRootIds.map { it.value }.distinct()
        val nextOrder = dao.maxCustomTrackOrder(code) + 1
        val entity = TrackEntity(
            langCode = code,
            id = trackId.value,
            category = StudyTrack.CUSTOM_CATEGORY,
            title = cleanTitle,
            summary = "自建词书",
            defaultOrder = NewWordOrder.BOOK_ORDER.tag,
            rootIdsCsv = CsvCodec.encode(uniqueIds),
            isCustom = true,
            sortOrder = nextOrder,
        )
        dao.upsertTracks(listOf(entity))
        trackId
    }

    override suspend fun renameCustomTrack(
        langCode: LangCode,
        trackId: TrackId,
        newTitle: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return@withContext false
        val existing = dao.findCustomTrack(langCode.value, trackId.value)
            ?: return@withContext false
        dao.upsertTracks(listOf(existing.copy(title = trimmed)))
        true
    }

    override suspend fun deleteCustomTrack(
        langCode: LangCode,
        trackId: TrackId,
    ): Boolean = withContext(Dispatchers.IO) {
        val code = langCode.value
        val existing = dao.findCustomTrack(code, trackId.value)
            ?: return@withContext false
        deletedTrackBackup = existing
        dao.removeCustomTrack(code, trackId.value)
        true
    }

    override suspend fun restoreDeletedTrack(): TrackId? =
        withContext(Dispatchers.IO) {
            val backup = deletedTrackBackup ?: return@withContext null
            deletedTrackBackup = null
            dao.upsertTracks(listOf(backup))
            TrackId(backup.id)
        }

    override suspend fun mutateTrackWords(
        mutation: TrackWordsMutation,
    ): Int = withContext(Dispatchers.IO) {
        if (mutation.rootIds.isEmpty()) return@withContext 0
        val code = mutation.langCode.value
        val existing = dao.findCustomTrack(code, mutation.trackId.value)
            ?: return@withContext 0
        val current = CsvCodec.decode(existing.rootIdsCsv)
        val delta = mutation.rootIds.map { it.value }.distinct()
        val updated = when (mutation.op) {
            TrackMembershipOp.ADD -> {
                val curSet = current.toHashSet()
                val added = delta.filter { it !in curSet }
                if (added.isEmpty()) return@withContext 0
                current + added
            }
            TrackMembershipOp.REMOVE -> {
                val dropSet = delta.toHashSet()
                val kept = current.filter { it !in dropSet }
                val removed = current.size - kept.size
                if (removed <= 0) return@withContext 0
                kept
            }
        }
        val changedCount = kotlin.math.abs(updated.size - current.size)
        val saved = existing.copy(rootIdsCsv = CsvCodec.encode(updated))
        dao.upsertTracks(listOf(saved))
        changedCount
    }

    override suspend fun applyPickedBooks(
        langCode: LangCode,
        rootIds: Set<RootId>,
        chosenIds: Set<TrackId>,
    ): String? = withContext(Dispatchers.IO) {
        if (rootIds.isEmpty()) return@withContext null
        if (rootIds.size == 1) {
            setSingleWordMemberships(langCode.value, rootIds.first(), chosenIds)
            return@withContext null
        }
        var totalAdded = 0
        for (tid in chosenIds) {
            val mut = TrackWordsMutation(
                langCode = langCode,
                trackId = tid,
                rootIds = rootIds,
                op = TrackMembershipOp.ADD,
            )
            totalAdded += mutateTrackWords(mut)
        }
        if (totalAdded > 0) "已加入所选词书" else "词条已在所选词书中"
    }

    override suspend fun commitExtraction(
        langCode: LangCode,
        spec: ExtractCommitSpec,
    ): String = withContext(Dispatchers.IO) {
        val targetId = spec.targetTrackId
        if (targetId != null) {
            val existing = dao.findCustomTrack(langCode.value, targetId.value)
            val bookTitle = existing?.title ?: "词书"
            val mut = TrackWordsMutation(
                langCode = langCode,
                trackId = targetId,
                rootIds = spec.rootIds,
                op = TrackMembershipOp.ADD,
            )
            val added = mutateTrackWords(mut)
            if (added > 0) {
                "已向「$bookTitle」添加 $added 词"
            } else {
                "所选词条已在「$bookTitle」中"
            }
        } else {
            val cleanTitle = spec.newTitle.trim().ifEmpty { "自建词书" }
            val draft = CustomTrackDraft(langCode, cleanTitle, spec.rootIds)
            createCustomTrack(draft)
            "已创建「$cleanTitle」并添加 ${spec.rootIds.size} 词"
        }
    }

    private suspend fun setSingleWordMemberships(
        code: String,
        rootId: RootId,
        selectedTrackIds: Set<TrackId>,
    ) {
        val targetId = rootId.value
        val chosenRaw = selectedTrackIds.mapTo(HashSet()) { it.value }
        database.withTransaction {
            val allCustom = dao.loadCustomTracks(code)
            val dirty = ArrayList<TrackEntity>()
            for (tr in allCustom) {
                val list = CsvCodec.decode(tr.rootIdsCsv)
                val shouldContain = tr.id in chosenRaw
                val hasNow = targetId in list
                if (shouldContain && !hasNow) {
                    val nextCsv = CsvCodec.encode(list + targetId)
                    dirty.add(tr.copy(rootIdsCsv = nextCsv))
                } else if (!shouldContain && hasNow) {
                    val kept = list.filter { it != targetId }
                    dirty.add(tr.copy(rootIdsCsv = CsvCodec.encode(kept)))
                }
            }
            if (dirty.isNotEmpty()) {
                dao.upsertTracks(dirty)
            }
        }
    }
}
