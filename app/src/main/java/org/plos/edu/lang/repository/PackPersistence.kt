package org.plos.edu.lang.repository

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.plos.edu.lang.database.AffixRuleEntity
import org.plos.edu.lang.database.GrammarRuleEntity
import org.plos.edu.lang.database.LanguageEntity
import org.plos.edu.lang.database.LexemeEntity
import org.plos.edu.lang.database.MainDatabase
import org.plos.edu.lang.database.PassageEntity
import org.plos.edu.lang.database.PassageLineEntity
import org.plos.edu.lang.database.RootEntity
import org.plos.edu.lang.database.StyleEntity
import org.plos.edu.lang.database.TrackEntity
import org.plos.edu.lang.database.UnitEntity
import org.plos.edu.lang.engine.AffixKind
import org.plos.edu.lang.engine.AffixRule
import org.plos.edu.lang.engine.ExampleDto
import org.plos.edu.lang.engine.ExampleItem
import org.plos.edu.lang.engine.GrammarRule
import org.plos.edu.lang.engine.LangCode
import org.plos.edu.lang.engine.LanguagePackPayload
import org.plos.edu.lang.engine.LanguageProfile
import org.plos.edu.lang.engine.LexemeEntry
import org.plos.edu.lang.engine.LexemeId
import org.plos.edu.lang.engine.MorphologyEngine
import org.plos.edu.lang.engine.NewWordOrder
import org.plos.edu.lang.engine.PosSense
import org.plos.edu.lang.engine.PosSenseDto
import org.plos.edu.lang.engine.RootEntry
import org.plos.edu.lang.engine.RootId
import org.plos.edu.lang.engine.RuleId
import org.plos.edu.lang.engine.RuleLexiconMorphologyEngine
import org.plos.edu.lang.engine.StudyTrack
import org.plos.edu.lang.engine.StyleKey
import org.plos.edu.lang.engine.TrackId
import org.plos.edu.lang.engine.WordSenses

data class LoadedLanguagePack(
    val packVersion: Int,
    val profile: LanguageProfile,
    val defaultTrackId: TrackId?,
    val tracks: List<StudyTrack>,
    val styleLabels: Map<StyleKey, String>,
    val roots: List<RootEntry>,
    val rootMap: Map<RootId, RootEntry>,
    val membersByRoot: Map<RootId, List<LexemeEntry>>,
    val grammarMap: Map<RuleId, GrammarRule>,
    val morphology: MorphologyEngine,
) {
    companion object {
        fun profileOf(entity: LanguageEntity): LanguageProfile =
            LanguageProfile(
                code = LangCode(entity.code),
                targetName = entity.targetName,
                localizedName = entity.localizedName,
                sourceName = entity.sourceName,
                ttsLocaleTag = entity.ttsLocaleTag,
            )
    }
}

@Singleton
class PackPersistence @Inject constructor(
    private val packLoader: PackAssetLoader,
    private val database: MainDatabase,
) {
    val codec: ArchiveCodec = packLoader.codec
    private val dao = database.studyDao()
    private val seedMutex = Mutex()
    @Volatile
    private var cachedPack: LoadedLanguagePack? = null

    suspend fun ensureSeeded() {
        seedMutex.withLock {
            for (payload in packLoader.loadBundledPacks()) {
                val existing = dao.findLanguage(payload.profile.code)
                val outdated = existing == null ||
                    existing.packVersion < payload.profile.packVersion
                if (outdated) {
                    persistPayload(payload)
                    cachedPack = null
                }
            }
        }
    }

    suspend fun persistImportedPack(
        payload: LanguagePackPayload,
    ): LanguageProfile {
        persistPayload(payload)
        cachedPack = null
        val dto = payload.profile
        return LanguageProfile(
            code = LangCode(dto.code),
            targetName = dto.targetName,
            localizedName = dto.localizedName,
            sourceName = dto.sourceName,
            ttsLocaleTag = dto.ttsLocaleTag,
        )
    }

    suspend fun resolvePack(entity: LanguageEntity): LoadedLanguagePack {
        val current = cachedPack
        val matches = current != null &&
            current.profile.code.value == entity.code &&
            current.packVersion == entity.packVersion
        if (matches) return current
        val code = entity.code
        val langCode = LangCode(code)
        val styleLabels = dao.loadStyles(code).associate { s ->
            StyleKey(s.styleKey) to s.label
        }
        val affixRules = dao.loadAffixRules(code).map { rule ->
            AffixRule(
                id = rule.id,
                langCode = langCode,
                kind = AffixKind.fromTag(rule.kind),
                prefix = rule.prefix,
                suffix = rule.suffix,
                restoredInitial = rule.restoredInitial,
                restoredEnding = rule.restoredEnding,
                patternLabel = rule.patternLabel,
                functionSummary = rule.functionSummary,
            )
        }
        val roots = loadAllRoots(code, langCode)
        val rootIdSet = roots.mapTo(HashSet(roots.size)) { it.id }
        val storedTracks = dao.loadBundledTracks(code).mapNotNull { tr ->
            val validIds = CsvCodec.decode(tr.rootIdsCsv)
                .map(::RootId)
                .filter { it in rootIdSet }
            if (validIds.isEmpty()) {
                null
            } else {
                StudyTrack(
                    id = TrackId(tr.id),
                    category = tr.category.ifBlank { "推荐词书" },
                    title = tr.title,
                    summary = tr.summary,
                    rootIds = validIds,
                    defaultOrder = NewWordOrder.fromTag(tr.defaultOrder),
                )
            }
        }
        val allTrack = StudyTrack(
            id = TrackId.ALL,
            category = "全库总览",
            title = "全部词库",
            summary = "涵盖当前语言包收录的全部词条",
            rootIds = roots.map { it.id },
        )
        val tracks = storedTracks + allTrack
        val defaultTid = entity.defaultTrackId
            .takeIf { it.isNotBlank() }
            ?.let(::TrackId)
        val lexemes = loadAllLexemes(code, langCode)
        val grammarRules = dao.loadGrammarRules(code).map { rule ->
            GrammarRule(
                id = RuleId(rule.id),
                langCode = langCode,
                tagLabel = rule.tagLabel,
                title = rule.title,
                keyPattern = rule.keyPattern,
                summary = rule.summary,
                bulletPoints = codec.decodeBullets(rule.bulletsJson),
                examples = codec.decodeExamples(rule.examplesJson),
            )
        }
        val engine = RuleLexiconMorphologyEngine(roots, lexemes, affixRules)
        val built = LoadedLanguagePack(
            packVersion = entity.packVersion,
            profile = LoadedLanguagePack.profileOf(entity),
            defaultTrackId = defaultTid,
            tracks = tracks,
            styleLabels = styleLabels,
            roots = roots,
            rootMap = roots.associateBy { it.id },
            membersByRoot = lexemes.groupBy { it.rootId },
            grammarMap = grammarRules.associateBy { it.id },
            morphology = engine,
        )
        cachedPack = built
        return built
    }

    private suspend fun persistPayload(payload: LanguagePackPayload) {
        val code = payload.profile.code
        val langEntity = LanguageEntity(
            code = code,
            targetName = payload.profile.targetName,
            localizedName = payload.profile.localizedName,
            sourceName = payload.profile.sourceName,
            ttsLocaleTag = payload.profile.ttsLocaleTag,
            defaultTrackId = payload.profile.defaultTrackId,
            packVersion = payload.profile.packVersion,
        )
        val styleEntities = payload.styles.map { s ->
            StyleEntity(
                langCode = code,
                styleKey = s.key,
                label = s.label,
                sortOrder = s.sortOrder,
            )
        }
        val trackEntities = payload.tracks.mapIndexed { idx, tr ->
            TrackEntity(
                langCode = code,
                id = tr.id,
                category = tr.category,
                title = tr.title,
                summary = tr.summary,
                defaultOrder = tr.defaultOrder,
                rootIdsCsv = CsvCodec.encode(tr.rootIds),
                isCustom = false,
                sortOrder = idx,
            )
        }
        val ruleEntities = payload.affixRules.map { rule ->
            AffixRuleEntity(
                langCode = code,
                id = rule.id,
                kind = rule.kind,
                prefix = rule.prefix,
                suffix = rule.suffix,
                restoredInitial = rule.restoredInitial,
                restoredEnding = rule.restoredEnding,
                patternLabel = rule.patternLabel,
                functionSummary = rule.functionSummary,
            )
        }
        val rootEntities = payload.roots.map { root ->
            RootEntity(
                langCode = code,
                id = root.id,
                lemma = root.lemma,
                spokenFormsCsv = CsvCodec.encode(root.spokenForms),
                phoneticHint = root.phoneticHint,
                sensesLines = encodeSenseDtos(root.senses),
                usageNote = root.usageNote,
                collocationsLines = CsvCodec.encodeLines(root.collocations),
                examplesLines = encodeExampleDtos(root.examples),
                frequencyRank = root.frequencyRank,
            )
        }
        val lexemeEntities = payload.lexemes.map { lex ->
            LexemeEntity(
                langCode = code,
                id = lex.id,
                rootId = lex.rootId,
                surface = lex.surface,
                phoneticHint = lex.phoneticHint,
                styleKey = lex.styleKey,
                breakdown = lex.breakdown,
                meaning = lex.meaning,
                counterpart = lex.counterpart,
            )
        }
        val unitEntities = payload.units.map { unit ->
            UnitEntity(
                langCode = code,
                id = unit.id,
                targetRootId = unit.targetRootId,
                headword = unit.headword,
                variantFormsCsv = CsvCodec.encode(unit.variantForms),
                styleBadge = unit.styleBadge,
                phoneticHint = unit.phoneticHint,
                sensesLines = encodeSenseDtos(unit.senses),
                examplesJson = codec.encodeUnitExamples(unit.examples),
                contrastLabel = unit.contrastLabel,
                contrastText = unit.contrastText,
                rankOrder = unit.rankOrder,
            )
        }
        val passageEntities = payload.passages.mapIndexed { idx, p ->
            PassageEntity(
                langCode = code,
                id = p.id,
                title = p.title,
                levelTag = p.levelTag,
                sceneSummary = p.sceneSummary,
                isCustom = p.isCustom,
                sortOrder = idx,
            )
        }
        val lineEntities = payload.passageLines.map { line ->
            PassageLineEntity(
                langCode = code,
                passageId = line.passageId,
                lineIndex = line.lineIndex,
                speaker = line.speaker,
                text = line.text,
                sourceTranslation = line.sourceTranslation,
                contrastLabel = line.contrastLabel,
                contrastText = line.contrastText,
                grammarIdsCsv = CsvCodec.encode(line.grammarIds),
            )
        }
        val grammarEntities = payload.grammarRules.mapIndexed { idx, rule ->
            val bulletsJson = codec.encodeBullets(rule.bulletPoints)
            val examplesJson = codec.encodeExamples(rule.examples)
            GrammarRuleEntity(
                langCode = code,
                id = rule.id,
                tagLabel = rule.tagLabel,
                title = rule.title,
                keyPattern = rule.keyPattern,
                summary = rule.summary,
                bulletsJson = bulletsJson,
                examplesJson = examplesJson,
                sortOrder = idx,
            )
        }
        database.withTransaction {
            dao.clearBundledPassageLines(code)
            dao.clearBundledPassages(code)
            dao.clearUnits(code)
            dao.clearStyles(code)
            dao.clearBundledTracks(code)
            dao.clearAffixRules(code)
            dao.clearRoots(code)
            dao.clearLexemes(code)
            dao.clearGrammarRules(code)
            dao.upsertLanguage(langEntity)
            dao.upsertStyles(styleEntities)
            dao.upsertTracks(trackEntities)
            dao.upsertAffixRules(ruleEntities)
            for (chunk in rootEntities.chunked(2000)) dao.upsertRoots(chunk)
            for (chunk in lexemeEntities.chunked(2000)) dao.upsertLexemes(chunk)
            dao.upsertUnits(unitEntities)
            dao.upsertPassages(passageEntities)
            dao.upsertPassageLines(lineEntities)
            dao.upsertGrammarRules(grammarEntities)
        }
    }

    private suspend fun loadAllRoots(
        code: String,
        langCode: LangCode,
    ): List<RootEntry> {
        val out = ArrayList<RootEntry>(4096)
        val pageSize = 4000
        var offset = 0
        while (true) {
            val page = dao.loadRootsPage(code, pageSize, offset)
            for (root in page) {
                out.add(toRootEntry(langCode, root))
            }
            if (page.size < pageSize) break
            offset += pageSize
        }
        return out
    }

    private fun toRootEntry(
        langCode: LangCode,
        root: RootEntity,
    ): RootEntry = RootEntry(
        id = RootId(root.id),
        langCode = langCode,
        lemma = root.lemma,
        spokenForms = CsvCodec.decode(root.spokenFormsCsv),
        phoneticHint = root.phoneticHint,
        senses = WordSenses.decodeLines(root.sensesLines),
        usageNote = root.usageNote,
        collocations = CsvCodec.decodeLines(root.collocationsLines),
        examples = ExampleItem.decodeLines(root.examplesLines),
        frequencyRank = root.frequencyRank,
    )

    private suspend fun loadAllLexemes(
        code: String,
        langCode: LangCode,
    ): List<LexemeEntry> {
        val out = ArrayList<LexemeEntry>(1024)
        val pageSize = 4000
        var offset = 0
        while (true) {
            val page = dao.loadLexemesPage(code, pageSize, offset)
            for (lex in page) {
                out.add(toLexemeEntry(langCode, lex))
            }
            if (page.size < pageSize) break
            offset += pageSize
        }
        return out
    }

    private fun toLexemeEntry(
        langCode: LangCode,
        lex: LexemeEntity,
    ): LexemeEntry = LexemeEntry(
        id = LexemeId(lex.id),
        langCode = langCode,
        rootId = RootId(lex.rootId),
        surface = lex.surface,
        phoneticHint = lex.phoneticHint,
        styleKey = StyleKey(lex.styleKey),
        breakdown = lex.breakdown,
        meaning = lex.meaning,
        counterpart = lex.counterpart,
    )

    private fun encodeSenseDtos(dtos: List<PosSenseDto>): String {
        val list = dtos.map { PosSense(it.pos, it.meaning) }
        return WordSenses(list).encodeLines()
    }

    private fun encodeExampleDtos(dtos: List<ExampleDto>): String {
        val items = dtos.map { ExampleItem(it.text, it.focusWord, it.meaning) }
        return ExampleItem.encodeLines(items)
    }
}
