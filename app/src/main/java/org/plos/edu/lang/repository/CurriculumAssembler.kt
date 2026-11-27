package org.plos.edu.lang.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import org.plos.edu.lang.database.CardStateEntity
import org.plos.edu.lang.database.PassageEntity
import org.plos.edu.lang.database.PassageLineEntity
import org.plos.edu.lang.database.UnitEntity
import org.plos.edu.lang.engine.CardId
import org.plos.edu.lang.engine.ExampleItem
import org.plos.edu.lang.engine.ImmersionPassage
import org.plos.edu.lang.engine.LearnedTodayItem
import org.plos.edu.lang.engine.MasterySnapshot
import org.plos.edu.lang.engine.MasteryStage
import org.plos.edu.lang.engine.PassageId
import org.plos.edu.lang.engine.PassageLine
import org.plos.edu.lang.engine.ReadingBookmark
import org.plos.edu.lang.engine.RootId
import org.plos.edu.lang.engine.RuleId
import org.plos.edu.lang.engine.SentenceContrast
import org.plos.edu.lang.engine.StudyTrack
import org.plos.edu.lang.engine.StudyUnit
import org.plos.edu.lang.engine.UnitId
import org.plos.edu.lang.engine.WordDisposition
import org.plos.edu.lang.engine.WordSenses

data class PassageScanOutput(
    val passages: List<ImmersionPassage>,
    val concordanceByRoot: Map<RootId, ExampleItem>,
)

data class CurriculumPlan(
    val units: List<StudyUnit>,
    val newEligibleIds: Set<UnitId>,
)

data class CurriculumBuildSpec(
    val pack: LoadedLanguagePack,
    val activeTrack: StudyTrack,
    val content: DynamicContent,
    val mastery: MasterySnapshot,
)

private class PassageTokenCounter(
    private val pack: LoadedLanguagePack,
    private val mastery: MasterySnapshot,
    private val concordance: MutableMap<RootId, ExampleItem>,
) {
    var totalTokens: Int = 0
        private set
    var knownTokens: Int = 0
        private set

    fun buildLine(row: PassageLineEntity, srcTag: String): PassageLine {
        val slices = pack.morphology.tokenizeLine(row.text, mastery)
        val canIndex = row.text.length in 12..140
        for (slice in slices) {
            val rid = slice.rootId ?: continue
            if (slice.lookupWord.isEmpty()) continue
            totalTokens++
            if (slice.stage != MasteryStage.UNSEEN) knownTokens++
            if (canIndex && rid !in concordance) {
                concordance[rid] = ExampleItem(
                    text = row.text,
                    focusWord = slice.rawText,
                    meaning = row.sourceTranslation,
                    sourceLabel = srcTag,
                )
            }
        }
        val ruleIds = CsvCodec.decode(row.grammarIdsCsv)
        val rules = ruleIds.mapNotNull { id -> pack.grammarMap[RuleId(id)] }
        val contrast = SentenceContrast.of(row.contrastLabel, row.contrastText)
        return PassageLine(
            lineIndex = row.lineIndex,
            speaker = row.speaker,
            text = row.text,
            sourceTranslation = row.sourceTranslation,
            contrast = contrast,
            grammarRules = rules,
        )
    }

    fun toPassage(
        entity: PassageEntity,
        bookmark: ReadingBookmark,
        lines: List<PassageLine>,
    ): ImmersionPassage {
        val coverage = if (totalTokens == 0) {
            0
        } else {
            ((knownTokens * 100.0) / totalTokens).roundToInt()
        }
        return ImmersionPassage(
            id = PassageId(entity.id),
            langCode = pack.profile.code,
            title = entity.title,
            levelTag = entity.levelTag,
            sceneSummary = entity.sceneSummary,
            isCustom = entity.isCustom,
            knownCoveragePercent = coverage,
            bookmark = bookmark,
            lines = lines,
        )
    }
}

@Singleton
class CurriculumAssembler @Inject constructor(
    private val codec: ArchiveCodec,
) {
    fun scanPassages(
        pack: LoadedLanguagePack,
        content: DynamicContent,
        mastery: MasterySnapshot,
    ): PassageScanOutput {
        val linesByPassage = content.passageLines.groupBy { it.passageId }
        val concordance = HashMap<RootId, ExampleItem>(256)
        val passages = content.passages.map { entity ->
            val rows = linesByPassage[entity.id]
                .orEmpty()
                .sortedBy { it.lineIndex }
            val counter = PassageTokenCounter(pack, mastery, concordance)
            val srcTag = "阅读重现 · ${entity.title}"
            val lines = rows.map { row -> counter.buildLine(row, srcTag) }
            val mark = content.bookmarks[entity.id] ?: ReadingBookmark()
            counter.toPassage(entity, mark, lines)
        }
        return PassageScanOutput(
            passages = passages,
            concordanceByRoot = concordance,
        )
    }

    fun buildCurriculum(
        spec: CurriculumBuildSpec,
        cardStates: List<CardStateEntity>,
        concordance: Map<RootId, ExampleItem>,
    ): CurriculumPlan {
        val pack = spec.pack
        val mastery = spec.mastery
        val explicitUnits = spec.content.units.map { entity ->
            toStudyUnit(pack, entity, concordance)
        }
        val explicitById = explicitUnits.associateBy { it.id }
        val bundledByRoot = HashMap<RootId, MutableList<StudyUnit>>()
        for (domainUnit in explicitUnits) {
            bundledByRoot
                .getOrPut(domainUnit.targetRootId) { ArrayList(2) }
                .add(domainUnit)
        }
        val ordered = ArrayList<StudyUnit>(spec.activeTrack.rootIds.size)
        val seenIds = HashSet<UnitId>(spec.activeTrack.rootIds.size)
        val newEligible = HashSet<UnitId>(spec.activeTrack.rootIds.size)
        for (rootId in spec.activeTrack.rootIds) {
            if (mastery.dispositionOf(rootId) != WordDisposition.AUTO) continue
            val nextRank = ordered.size + 1
            val candidates = bundledByRoot[rootId] ?: listOfNotNull(
                synthesizeForRoot(pack, rootId, concordance, nextRank),
            )
            for (u in candidates) {
                if (seenIds.add(u.id)) {
                    ordered.add(u)
                    newEligible.add(u.id)
                }
            }
        }
        for (state in cardStates) {
            if (state.reps <= 0) continue
            val rootId = RootId(state.rootId)
            if (mastery.dispositionOf(rootId) != WordDisposition.AUTO) continue
            val uid = UnitId(state.unitId)
            if (uid in seenIds) continue
            val unit = explicitById[uid] ?: synthesizeForRoot(
                pack = pack,
                rootId = rootId,
                concordance = concordance,
                rankOrder = ordered.size + 1,
            ) ?: continue
            if (seenIds.add(unit.id)) {
                ordered.add(unit)
            }
        }
        return CurriculumPlan(units = ordered, newEligibleIds = newEligible)
    }

    fun resolveUnitById(
        spec: CurriculumBuildSpec,
        concordance: Map<RootId, ExampleItem>,
        rawUnitId: String,
    ): StudyUnit? {
        val pack = spec.pack
        val stored = spec.content.units.firstOrNull { it.id == rawUnitId }
        if (stored != null) {
            return toStudyUnit(pack, stored, concordance)
        }
        val synRootId = UnitId(rawUnitId).synthesizedRootId() ?: return null
        return synthesizeForRoot(pack, synRootId, concordance, null)
    }

    fun buildLearnedToday(
        spec: CurriculumBuildSpec,
        progress: DynamicProgress,
        concordance: Map<RootId, ExampleItem>,
        dayStartMs: Long,
    ): List<LearnedTodayItem> {
        val logsByCard = progress.reviewLogs
            .filter { it.reviewedAtMs >= dayStartMs }
            .groupBy { it.cardId }
        if (logsByCard.isEmpty()) return emptyList()
        val pack = spec.pack
        val unitById = spec.content.units.associate { entity ->
            entity.id to toStudyUnit(pack, entity, concordance)
        }
        val stateById = progress.cardStates.associateBy { it.cardId }
        val mastery = spec.mastery
        val items = ArrayList<LearnedTodayItem>(logsByCard.size)
        for ((rawCardId, cardLogs) in logsByCard) {
            val parsed = CardId(rawCardId).parse() ?: continue
            val synRoot = UnitId(parsed.unitId).synthesizedRootId()
            val unit = unitById[parsed.unitId] ?: synRoot?.let {
                synthesizeForRoot(pack, it, concordance, null)
            } ?: continue
            val root = pack.rootMap[unit.targetRootId] ?: continue
            val totalReps = stateById[rawCardId]?.reps ?: cardLogs.size
            val item = LearnedTodayItem(
                unit = unit,
                root = root,
                familyMembers = pack.membersByRoot[root.id].orEmpty(),
                stage = mastery.stageOf(root.id),
                stabilityDays = mastery.stabilityOf(root.id),
                familiarityRatio = mastery.familiarityOf(root.id),
                isNewlyIntroduced = cardLogs.size >= totalReps,
                lastReviewedAtMs = cardLogs.maxOf { it.reviewedAtMs },
            )
            items.add(item)
        }
        items.sortByDescending { it.lastReviewedAtMs }
        return items
    }

    private fun synthesizeForRoot(
        pack: LoadedLanguagePack,
        rootId: RootId,
        concordance: Map<RootId, ExampleItem>,
        rankOrder: Int?,
    ): StudyUnit? {
        val root = pack.rootMap[rootId] ?: return null
        val base = root.examples
        val conc = concordance[rootId]
        val canAppend = conc != null &&
            base.size < 2 &&
            base.none { it.text.equals(conc.text, ignoreCase = true) }
        val merged = if (canAppend) base + conc else base
        return StudyUnit(
            id = UnitId.forRoot(root.id),
            langCode = pack.profile.code,
            targetRootId = root.id,
            headword = root.lemma,
            variantForms = root.spokenForms,
            styleBadge = "",
            phoneticHint = root.phoneticHint,
            senses = root.senses,
            collocations = root.collocations,
            examples = merged,
            contrast = null,
            rankOrder = rankOrder ?: root.frequencyRank,
        )
    }

    private fun toStudyUnit(
        pack: LoadedLanguagePack,
        unit: UnitEntity,
        concordance: Map<RootId, ExampleItem>,
    ): StudyUnit {
        val rootId = RootId(unit.targetRootId)
        val root = pack.rootMap[rootId]
        val unitEx = codec.decodeUnitExamples(unit.examplesJson)
        val baseExamples = unitEx.ifEmpty { root?.examples.orEmpty() }
        val conc = concordance[rootId]
        val canAppend = conc != null &&
            baseExamples.size < 3 &&
            baseExamples.none { it.text.equals(conc.text, ignoreCase = true) }
        val mergedExamples = if (canAppend) {
            baseExamples + conc
        } else {
            baseExamples
        }
        return StudyUnit(
            id = UnitId(unit.id),
            langCode = pack.profile.code,
            targetRootId = rootId,
            headword = unit.headword,
            variantForms = CsvCodec.decode(unit.variantFormsCsv),
            styleBadge = unit.styleBadge,
            phoneticHint = unit.phoneticHint.ifEmpty {
                root?.phoneticHint.orEmpty()
            },
            senses = WordSenses.decodeLines(unit.sensesLines),
            collocations = root?.collocations.orEmpty(),
            examples = mergedExamples,
            contrast = SentenceContrast.of(
                unit.contrastLabel,
                unit.contrastText,
            ),
            rankOrder = unit.rankOrder,
        )
    }
}
