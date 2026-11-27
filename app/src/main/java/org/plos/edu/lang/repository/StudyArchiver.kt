package org.plos.edu.lang.repository

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.plos.edu.lang.Constants
import org.plos.edu.lang.database.CardStateEntity
import org.plos.edu.lang.database.MainDatabase
import org.plos.edu.lang.database.PassageEntity
import org.plos.edu.lang.database.PassageLineEntity
import org.plos.edu.lang.database.ReadingProgressEntity
import org.plos.edu.lang.database.ReviewLogEntity
import org.plos.edu.lang.database.TrackEntity
import org.plos.edu.lang.database.WordDispositionEntity
import org.plos.edu.lang.engine.ArchivedPassageDto
import org.plos.edu.lang.engine.ArchivedPassageLineDto
import org.plos.edu.lang.engine.CardStateDto
import org.plos.edu.lang.engine.CustomTrackDto
import org.plos.edu.lang.engine.DailyTotalCap
import org.plos.edu.lang.engine.LangCode
import org.plos.edu.lang.engine.LanguageProfile
import org.plos.edu.lang.engine.LearnerPreferences
import org.plos.edu.lang.engine.NewWordOrder
import org.plos.edu.lang.engine.ProgressArchive
import org.plos.edu.lang.engine.ReadingBookmarkDto
import org.plos.edu.lang.engine.RetentionTarget
import org.plos.edu.lang.engine.ReviewLogDto
import org.plos.edu.lang.engine.StudyPacePlan
import org.plos.edu.lang.engine.StudyTrack
import org.plos.edu.lang.engine.TrackId
import org.plos.edu.lang.engine.WordDispositionDto
import org.plos.edu.lang.modules.DataStoreManager

@Singleton
class StudyArchiver @Inject constructor(
    private val database: MainDatabase,
    private val dataStore: DataStoreManager,
    private val packPersistence: PackPersistence,
) : ArchiveGateway() {
    private val dao = database.studyDao()
    private val codec: ArchiveCodec = packPersistence.codec

    override suspend fun importLanguagePackJson(
        rawJson: String,
    ): LanguageProfile = withContext(Dispatchers.IO) {
        val payload = codec.decodeLanguagePack(rawJson)
        val profile = packPersistence.persistImportedPack(payload)
        dataStore.setLanguage(profile.code)
        profile
    }

    override suspend fun exportProgressJson(): String =
        withContext(Dispatchers.IO) {
            val prefs = dataStore.current()
            val states = dao.loadAllCardStates().map(::toCardStateDto)
            val logs = dao.loadAllReviewLogs().map(::toReviewLogDto)
            val dispositions = dao.loadAllWordDispositions().map { d ->
                WordDispositionDto(
                    langCode = d.langCode,
                    rootId = d.rootId,
                    dispositionCode = d.dispositionCode,
                    updatedAtMs = d.updatedAtMs,
                )
            }
            val customTracks = dao.loadAllCustomTracks().map { tr ->
                CustomTrackDto(
                    langCode = tr.langCode,
                    id = tr.id,
                    title = tr.title,
                    rootIds = CsvCodec.decode(tr.rootIdsCsv),
                )
            }
            val customPassages = exportCustomPassages()
            val bookmarks = dao.loadAllReadingProgress().map { b ->
                ReadingBookmarkDto(
                    passageId = b.passageId,
                    lineIndex = b.lineIndex,
                    scrollOffset = b.scrollOffset,
                    completed = b.completed,
                )
            }
            val trackMap = prefs.trackByLang.entries.associate { (k, v) ->
                k.value to v.value
            }
            val orderMap = prefs.orderByTrack.mapValues { it.value.tag }
            val archive = ProgressArchive(
                exportedAtMs = System.currentTimeMillis(),
                activeLang = prefs.activeLang.value,
                appTheme = prefs.appTheme,
                retention = prefs.retention.value,
                dailyNewLimit = prefs.dailyNewLimit,
                dailyTotalLimit = prefs.dailyTotalCap.rawLimit,
                speechRate = prefs.speechRate,
                autoPlayAudio = prefs.autoPlayAudio,
                ttsEnginePkg = prefs.ttsEnginePkg,
                activeTracks = trackMap,
                trackOrders = orderMap,
                shuffleSeed = prefs.shuffleSeed,
                cardStates = states,
                reviewLogs = logs,
                dispositions = dispositions,
                customTracks = customTracks,
                customPassages = customPassages,
                readingBookmarks = bookmarks,
            )
            codec.encodeProgress(archive)
        }

    override suspend fun importProgressJson(
        rawJson: String,
    ) = withContext(Dispatchers.IO) {
        val archive = codec.decodeProgress(rawJson)
        val stateEntities = archive.cardStates.map(::toCardStateEntity)
        val logEntities = archive.reviewLogs.map(::toReviewLogEntity)
        val dispositionEntities = archive.dispositions.map { dto ->
            WordDispositionEntity(
                langCode = dto.langCode,
                rootId = dto.rootId,
                dispositionCode = dto.dispositionCode,
                updatedAtMs = dto.updatedAtMs,
            )
        }
        val trackEntities = archive.customTracks.mapIndexed { idx, dto ->
            TrackEntity(
                langCode = dto.langCode,
                id = dto.id,
                category = StudyTrack.CUSTOM_CATEGORY,
                title = dto.title.trim().ifEmpty { "自建词书" },
                summary = "自建词书",
                defaultOrder = NewWordOrder.BOOK_ORDER.tag,
                rootIdsCsv = CsvCodec.encode(dto.rootIds),
                isCustom = true,
                sortOrder = 100 + idx,
            )
        }
        val passageEntities = archive.customPassages.mapIndexed { idx, dto ->
            PassageEntity(
                langCode = dto.langCode,
                id = dto.id,
                title = dto.title,
                levelTag = dto.levelTag,
                sceneSummary = dto.sceneSummary,
                isCustom = true,
                sortOrder = 1000 + idx,
            )
        }
        val lineEntities = archive.customPassages.flatMap { p ->
            p.lines.map { l ->
                PassageLineEntity(
                    langCode = p.langCode,
                    passageId = p.id,
                    lineIndex = l.lineIndex,
                    speaker = l.speaker,
                    text = l.text,
                    sourceTranslation = "",
                    contrastLabel = "",
                    contrastText = "",
                    grammarIdsCsv = "",
                )
            }
        }
        val bookmarkEntities = archive.readingBookmarks.map { b ->
            ReadingProgressEntity(
                passageId = b.passageId,
                lineIndex = b.lineIndex.coerceAtLeast(0),
                scrollOffset = b.scrollOffset.coerceAtLeast(0),
                completed = b.completed,
            )
        }
        database.withTransaction {
            for (p in archive.customPassages) {
                dao.removePassageLines(p.langCode, p.id)
            }
            dao.upsertTracks(trackEntities)
            dao.upsertPassages(passageEntities)
            dao.upsertPassageLines(lineEntities)
            dao.upsertReadingProgressList(bookmarkEntities)
            dao.upsertCardStates(stateEntities)
            dao.upsertWordDispositions(dispositionEntities)
            dao.insertReviewLogs(logEntities)
        }
        restorePreferences(archive)
    }

    private suspend fun exportCustomPassages(): List<ArchivedPassageDto> {
        val passages = dao.loadCustomPassages()
        if (passages.isEmpty()) return emptyList()
        val linesByKey = dao.loadCustomPassageLines().groupBy {
            "${it.langCode}|${it.passageId}"
        }
        return passages.map { p ->
            val key = "${p.langCode}|${p.id}"
            val sortedLines = linesByKey[key]
                .orEmpty()
                .sortedBy { it.lineIndex }
                .map { l ->
                    ArchivedPassageLineDto(l.lineIndex, l.speaker, l.text)
                }
            ArchivedPassageDto(
                langCode = p.langCode,
                id = p.id,
                title = p.title,
                levelTag = p.levelTag,
                sceneSummary = p.sceneSummary,
                lines = sortedLines,
            )
        }
    }

    private suspend fun restorePreferences(archive: ProgressArchive) {
        val safeRet = RetentionTarget(archive.retention.coerceIn(0.75, 0.96))
        val restoredTracks = archive.activeTracks
            .filter { (k, v) -> k.isNotBlank() && v.isNotBlank() }
            .map { (k, v) -> LangCode(k) to TrackId(v) }
            .toMap()
        val restoredOrders = archive.trackOrders
            .filter { (k, v) -> k.isNotBlank() && v.isNotBlank() }
            .mapValues { (_, v) -> NewWordOrder.fromTag(v) }
        val pace = StudyPacePlan.of(
            retention = safeRet,
            dailyNewLimit = archive.dailyNewLimit,
            dailyTotalCap = DailyTotalCap.fromStored(archive.dailyTotalLimit),
        )
        val rawLang = archive.activeLang.ifBlank { Constants.DEFAULT_LANG }
        val restoredPrefs = LearnerPreferences(
            activeLang = LangCode(rawLang),
            appTheme = archive.appTheme,
            retention = pace.retention,
            dailyNewLimit = pace.dailyNewLimit,
            dailyTotalCap = pace.dailyTotalCap,
            speechRate = archive.speechRate.coerceIn(0.6f, 1.3f),
            autoPlayAudio = archive.autoPlayAudio,
            ttsEnginePkg = archive.ttsEnginePkg,
            trackByLang = restoredTracks,
            orderByTrack = restoredOrders,
            shuffleSeed = archive.shuffleSeed,
        )
        dataStore.restoreAll(restoredPrefs)
    }

    private fun toCardStateDto(e: CardStateEntity): CardStateDto = CardStateDto(
        cardId = e.cardId,
        langCode = e.langCode,
        unitId = e.unitId,
        rootId = e.rootId,
        longStability = e.longStability,
        shortStability = e.shortStability,
        difficulty = e.difficulty,
        dueEpochMs = e.dueEpochMs,
        lastReviewEpochMs = e.lastReviewEpochMs,
        reps = e.reps,
        lapses = e.lapses,
    )

    private fun toCardStateEntity(dto: CardStateDto): CardStateEntity =
        CardStateEntity(
            cardId = dto.cardId,
            langCode = dto.langCode,
            unitId = dto.unitId,
            rootId = dto.rootId,
            longStability = dto.longStability,
            shortStability = dto.shortStability,
            difficulty = dto.difficulty,
            dueEpochMs = dto.dueEpochMs,
            lastReviewEpochMs = dto.lastReviewEpochMs,
            reps = dto.reps,
            lapses = dto.lapses,
        )

    private fun toReviewLogDto(log: ReviewLogEntity): ReviewLogDto =
        ReviewLogDto(
            cardId = log.cardId,
            langCode = log.langCode,
            gradeCode = log.gradeCode,
            reviewedAtMs = log.reviewedAtMs,
            stabilityAfter = log.stabilityAfter,
            difficultyAfter = log.difficultyAfter,
        )

    private fun toReviewLogEntity(dto: ReviewLogDto): ReviewLogEntity =
        ReviewLogEntity(
            cardId = dto.cardId,
            langCode = dto.langCode,
            gradeCode = dto.gradeCode,
            reviewedAtMs = dto.reviewedAtMs,
            stabilityAfter = dto.stabilityAfter,
            difficultyAfter = dto.difficultyAfter,
        )
}
