package org.plos.edu.lang.engine

import kotlin.math.roundToInt
import org.plos.edu.lang.Constants

data class MemoryState(
    val longStability: StabilityDays,
    val shortStability: StabilityDays,
    val difficulty: DifficultyScore,
    val dueEpochMs: Long,
    val lastReviewEpochMs: Long,
    val reps: Int,
    val lapses: Int,
)

data class GradeOption(
    val grade: ReviewGrade,
    val intervalLabel: String,
    val nextState: MemoryState,
)

data class SchedulePreview(
    val options: List<GradeOption>,
) {
    fun optionFor(grade: ReviewGrade): GradeOption? =
        options.firstOrNull { it.grade == grade }
}

data class ReviewCommit(
    val cardId: CardId,
    val grade: ReviewGrade,
    val reviewedAtMs: Long,
    val previousState: MemoryState?,
    val nextState: MemoryState,
)

data class UndoRestoration(
    val cardId: CardId,
    val previousGrade: ReviewGrade?,
    val restoredMasteredMark: Boolean = false,
)

data class LanguageProfile(
    val code: LangCode,
    val targetName: String,
    val localizedName: String,
    val sourceName: String,
    val ttsLocaleTag: String,
)

data class StudyTrack(
    val id: TrackId,
    val category: String,
    val title: String,
    val summary: String,
    val rootIds: List<RootId>,
    val defaultOrder: NewWordOrder = NewWordOrder.BOOK_ORDER,
    val isCustom: Boolean = false,
) {
    fun orderedBy(
        order: NewWordOrder,
        rootMap: Map<RootId, RootEntry>,
        shuffleSeed: Long,
    ): StudyTrack = when (order) {
        NewWordOrder.BOOK_ORDER -> this
        NewWordOrder.SHUFFLED -> {
            val idBits = id.value.hashCode().toLong()
            val trackSeed = shuffleSeed xor (idBits * -7046029254386353131L)
            val shuffled = rootIds.sortedBy { rid ->
                NewWordOrder.shuffleRank(trackSeed, rid)
            }
            copy(rootIds = shuffled)
        }
        NewWordOrder.FREQUENCY -> {
            val byFreq = rootIds.sortedBy { rid ->
                rootMap[rid]?.frequencyRank ?: Int.MAX_VALUE
            }
            copy(rootIds = byFreq)
        }
    }

    companion object {
        const val CUSTOM_CATEGORY: String = "我的词书"
    }
}

data class AffixRule(
    val id: String,
    val langCode: LangCode,
    val kind: AffixKind,
    val prefix: String,
    val suffix: String,
    val restoredInitial: String,
    val restoredEnding: String,
    val patternLabel: String,
    val functionSummary: String,
)

data class RootEntry(
    val id: RootId,
    val langCode: LangCode,
    val lemma: String,
    val spokenForms: List<String>,
    val phoneticHint: String,
    val senses: WordSenses,
    val usageNote: String,
    val collocations: List<String>,
    val examples: List<ExampleItem> = emptyList(),
    val frequencyRank: Int,
)

data class LexemeEntry(
    val id: LexemeId,
    val langCode: LangCode,
    val rootId: RootId,
    val surface: String,
    val phoneticHint: String,
    val styleKey: StyleKey,
    val breakdown: String,
    val meaning: String,
    val counterpart: String,
)

data class TokenSlice(
    val rawText: String,
    val lookupWord: String,
    val rootId: RootId?,
    val stage: MasteryStage,
)

data class WordResolution(
    val surface: String,
    val root: RootEntry?,
    val matchedLexeme: LexemeEntry?,
    val breakdown: String,
    val appliedRule: AffixRule?,
    val familyMembers: List<LexemeEntry>,
    val stage: MasteryStage,
    val disposition: WordDisposition,
    val planStage: PlanStage,
    val familiarityRatio: Float,
)

data class RootOverview(
    val root: RootEntry,
    val members: List<LexemeEntry>,
    val stage: MasteryStage,
    val disposition: WordDisposition,
    val planStage: PlanStage,
    val stabilityDays: Double,
    val familiarityRatio: Float,
)

data class MasterySnapshot(
    val stages: Map<RootId, MasteryStage>,
    val stabilities: Map<RootId, Double>,
    val familiarities: Map<RootId, Float>,
    val dispositions: Map<RootId, WordDisposition> = emptyMap(),
) {
    fun stageOf(rootId: RootId): MasteryStage =
        stages[rootId] ?: MasteryStage.UNSEEN

    fun stabilityOf(rootId: RootId): Double = stabilities[rootId] ?: 0.0

    fun familiarityOf(rootId: RootId): Float = familiarities[rootId] ?: 0f

    fun dispositionOf(rootId: RootId): WordDisposition =
        dispositions[rootId] ?: WordDisposition.AUTO

    fun planStageOf(rootId: RootId): PlanStage =
        PlanStage.resolve(dispositionOf(rootId), stageOf(rootId))
}

data class LexiconSearchResult(
    val inTrack: List<RootOverview>,
    val outsideTrack: List<RootOverview> = emptyList(),
)

data class SentenceContrast(
    val label: String,
    val text: String,
) {
    companion object {
        fun of(label: String, text: String): SentenceContrast? {
            if (text.isBlank()) return null
            val cleanLabel = label.ifBlank { "对照表达" }
            return SentenceContrast(label = cleanLabel, text = text)
        }
    }
}

data class ExampleItem(
    val text: String,
    val focusWord: String,
    val meaning: String,
    val sourceLabel: String = "",
) {
    companion object {
        fun encodeLines(items: List<ExampleItem>): String =
            items.joinToString("\n") { item ->
                val t = sanitizeCell(item.text)
                val f = sanitizeCell(item.focusWord)
                val m = sanitizeCell(item.meaning)
                "$t\t$f\t$m"
            }

        fun decodeLines(raw: String): List<ExampleItem> {
            if (raw.isBlank()) return emptyList()
            return raw.lines().mapNotNull(::parseLine)
        }

        private fun sanitizeCell(value: String): String =
            value.replace('\t', ' ').replace('\n', ' ').trim()

        private fun parseLine(line: String): ExampleItem? {
            val first = line.indexOf('\t')
            if (first <= 0) return null
            val second = line.indexOf('\t', first + 1)
            if (second < 0) return null
            val text = line.substring(0, first).trim()
            val focus = line.substring(first + 1, second).trim()
            val meaning = line.substring(second + 1).trim()
            if (text.isEmpty()) return null
            return ExampleItem(text, focus, meaning)
        }
    }
}

data class GrammarExample(
    val targetText: String,
    val sourceText: String,
    val note: String,
)

data class GrammarRule(
    val id: RuleId,
    val langCode: LangCode,
    val tagLabel: String,
    val title: String,
    val keyPattern: String,
    val summary: String,
    val bulletPoints: List<String>,
    val examples: List<GrammarExample>,
)

data class StudyUnit(
    val id: UnitId,
    val langCode: LangCode,
    val targetRootId: RootId,
    val headword: String,
    val variantForms: List<String>,
    val styleBadge: String,
    val phoneticHint: String,
    val senses: WordSenses,
    val collocations: List<String>,
    val examples: List<ExampleItem>,
    val contrast: SentenceContrast?,
    val rankOrder: Int,
)

data class StudyCard(
    val id: CardId,
    val unit: StudyUnit,
    val root: RootEntry,
    val familyMembers: List<LexemeEntry>,
    val state: MemoryState?,
    val preview: SchedulePreview,
)

data class UndoPreview(
    val card: StudyCard,
    val restoration: UndoRestoration,
)

data class LearnedTodayItem(
    val unit: StudyUnit,
    val root: RootEntry,
    val familyMembers: List<LexemeEntry>,
    val stage: MasteryStage,
    val stabilityDays: Double,
    val familiarityRatio: Float,
    val isNewlyIntroduced: Boolean,
    val lastReviewedAtMs: Long,
)

data class PassageLine(
    val lineIndex: Int,
    val speaker: String,
    val text: String,
    val sourceTranslation: String,
    val contrast: SentenceContrast?,
    val grammarRules: List<GrammarRule>,
)

data class ReadingBookmark(
    val lineIndex: Int = 0,
    val scrollOffset: Int = 0,
    val hasRead: Boolean = false,
    val completed: Boolean = false,
) {
    fun statusLabel(totalLines: Int): String? = when {
        completed -> "已读完"
        hasRead && totalLines > 0 -> {
            val cur = (lineIndex + 1).coerceIn(1, totalLines)
            "读至 $cur/$totalLines 句"
        }
        else -> null
    }
}

data class ImmersionPassage(
    val id: PassageId,
    val langCode: LangCode,
    val title: String,
    val levelTag: String,
    val sceneSummary: String,
    val isCustom: Boolean,
    val knownCoveragePercent: Int,
    val bookmark: ReadingBookmark,
    val lines: List<PassageLine>,
) {
    val isDialogue: Boolean
        get() = lines.any { it.speaker.isNotEmpty() }
}

data class PassageSearchHit(
    val passage: ImmersionPassage,
    val matchedLineIndex: Int?,
    val matchedSnippet: String?,
)

data class HistoryDayBar(
    val dayLabel: String,
    val easyCount: Int,
    val goodCount: Int,
    val hardCount: Int,
    val againCount: Int,
) {
    val totalCount: Int
        get() = easyCount + goodCount + hardCount + againCount
}

data class ForecastDayBar(
    val dayLabel: String,
    val matureCount: Int,
    val youngCount: Int,
    val newCount: Int,
) {
    val totalCount: Int
        get() = matureCount + youngCount + newCount
}

data class StabilityBucket(
    val rangeLabel: String,
    val wordCount: Int,
)

data class ProgressAnalytics(
    val unseenCount: Int,
    val learningCount: Int,
    val masteredCount: Int,
    val ignoredCount: Int,
    val selectedCount: Int,
    val trackTotalCount: Int,
    val actualRecallRate: Double?,
    val targetRetention: Double,
    val totalReviewsLogged: Int,
    val deferredDueCount: Int,
    val estimatedFinishDays: Int,
    val historyBars: List<HistoryDayBar>,
    val forecastBars: List<ForecastDayBar>,
    val stabilityBuckets: List<StabilityBucket>,
)

data class LearnerPreferences(
    val activeLang: LangCode,
    val appTheme: String,
    val retention: RetentionTarget,
    val dailyNewLimit: Int,
    val dailyTotalCap: DailyTotalCap = DailyTotalCap.DEFAULT,
    val speechRate: Float,
    val autoPlayAudio: Boolean,
    val ttsEnginePkg: String,
    val trackByLang: Map<LangCode, TrackId> = emptyMap(),
    val orderByTrack: Map<String, NewWordOrder> = emptyMap(),
    val shuffleSeed: Long = Constants.DEFAULT_SHUFFLE_SEED,
    val bonusDayStartMs: Long = 0L,
    val bonusNewCount: Int = 0,
) {
    fun trackFor(lang: LangCode): TrackId? = trackByLang[lang]

    fun orderFor(lang: LangCode, track: StudyTrack): NewWordOrder =
        orderByTrack[trackOrderKey(lang, track.id)] ?: track.defaultOrder

    fun activeBonusToday(dayStartMs: Long): Int =
        if (bonusDayStartMs == dayStartMs) bonusNewCount else 0

    companion object {
        fun trackOrderKey(lang: LangCode, trackId: TrackId): String =
            "${lang.value}/${trackId.value}"
    }
}

@ConsistentCopyVisibility
data class StudyPacePlan private constructor(
    val retention: RetentionTarget,
    val dailyNewLimit: Int,
    val dailyTotalCap: DailyTotalCap,
) {
    companion object {
        const val MIN_DAILY_NEW: Int = 1
        const val MAX_DAILY_NEW: Int = 9999

        fun of(
            retention: RetentionTarget,
            dailyNewLimit: Int,
            dailyTotalCap: DailyTotalCap,
        ): StudyPacePlan {
            val cleanNew = dailyNewLimit.coerceIn(
                MIN_DAILY_NEW,
                MAX_DAILY_NEW,
            )
            val cleanCap = dailyTotalCap.raisedAtLeast(cleanNew)
            return StudyPacePlan(retention, cleanNew, cleanCap)
        }

        fun loadMultiplier(retention: RetentionTarget): Double {
            val gap = (1.0 - retention.value).coerceAtLeast(0.03)
            return 1.0 + 0.433 / gap
        }

        fun recommendTotal(
            dailyNew: Int,
            retention: RetentionTarget,
        ): Int {
            val safeNew = dailyNew.coerceIn(MIN_DAILY_NEW, MAX_DAILY_NEW)
            val raw = safeNew * loadMultiplier(retention)
            return raw.roundToInt().coerceIn(safeNew, DailyTotalCap.MAX_BOUND)
        }

        fun recommendNew(
            dailyTotal: Int,
            retention: RetentionTarget,
        ): Int {
            val safeTot = dailyTotal.coerceIn(
                MIN_DAILY_NEW,
                DailyTotalCap.MAX_BOUND,
            )
            val raw = safeTot / loadMultiplier(retention)
            return raw.roundToInt().coerceIn(MIN_DAILY_NEW, safeTot)
        }
    }
}

sealed interface PreferenceMutation {
    data class Theme(val value: String) : PreferenceMutation
    data class StudyPace(val plan: StudyPacePlan) : PreferenceMutation
    data class TrackWordOrder(
        val trackKey: String,
        val order: NewWordOrder,
    ) : PreferenceMutation
    data object ReshuffleNewWords : PreferenceMutation
    data class SpeechRate(val rate: Float) : PreferenceMutation
    data class AutoPlayAudio(val enabled: Boolean) : PreferenceMutation
    data class TtsEngine(val pkg: String) : PreferenceMutation
}

data class CustomPassageDraft(
    val langCode: LangCode,
    val title: String,
    val rawBody: String,
    val editId: PassageId? = null,
)

enum class TrackMembershipOp { ADD, REMOVE }

data class CustomTrackDraft(
    val langCode: LangCode,
    val title: String,
    val initialRootIds: List<RootId> = emptyList(),
)

data class TrackWordsMutation(
    val langCode: LangCode,
    val trackId: TrackId,
    val rootIds: Collection<RootId>,
    val op: TrackMembershipOp,
)

data class ExtractCommitSpec(
    val targetTrackId: TrackId?,
    val newTitle: String,
    val rootIds: List<RootId>,
)
