package org.plos.edu.lang.engine

import kotlinx.serialization.Serializable

@Serializable
data class PackProfileDto(
    val code: String,
    val targetName: String,
    val localizedName: String,
    val sourceName: String,
    val ttsLocaleTag: String,
    val defaultTrackId: String = "",
    val packVersion: Int,
)

@Serializable
data class StyleDto(
    val key: String,
    val label: String,
    val sortOrder: Int,
)

@Serializable
data class StudyTrackDto(
    val id: String,
    val category: String = "推荐词书",
    val title: String,
    val summary: String,
    val defaultOrder: String = "book",
    val rootIds: List<String>,
)

@Serializable
data class AffixRuleDto(
    val id: String,
    val kind: String,
    val prefix: String,
    val suffix: String,
    val restoredInitial: String = "",
    val restoredEnding: String = "",
    val patternLabel: String,
    val functionSummary: String,
)

@Serializable
data class PosSenseDto(
    val pos: String = "",
    val meaning: String,
)

@Serializable
data class RootDto(
    val id: String,
    val lemma: String,
    val spokenForms: List<String> = emptyList(),
    val phoneticHint: String = "",
    val senses: List<PosSenseDto>,
    val usageNote: String = "",
    val collocations: List<String> = emptyList(),
    val examples: List<ExampleDto> = emptyList(),
    val frequencyRank: Int,
)

@Serializable
data class LexemeDto(
    val id: String,
    val rootId: String,
    val surface: String,
    val phoneticHint: String = "",
    val styleKey: String,
    val breakdown: String,
    val meaning: String,
    val counterpart: String = "",
)

@Serializable
data class ExampleDto(
    val text: String,
    val focusWord: String = "",
    val meaning: String,
)

@Serializable
data class StudyUnitDto(
    val id: String,
    val targetRootId: String,
    val headword: String,
    val variantForms: List<String> = emptyList(),
    val styleBadge: String = "",
    val phoneticHint: String = "",
    val senses: List<PosSenseDto>,
    val examples: List<ExampleDto> = emptyList(),
    val contrastLabel: String = "",
    val contrastText: String = "",
    val rankOrder: Int,
)

@Serializable
data class PassageDto(
    val id: String,
    val title: String,
    val levelTag: String,
    val sceneSummary: String,
    val isCustom: Boolean = false,
)

@Serializable
data class PassageLineDto(
    val passageId: String,
    val lineIndex: Int,
    val speaker: String = "",
    val text: String,
    val sourceTranslation: String = "",
    val contrastLabel: String = "",
    val contrastText: String = "",
    val grammarIds: List<String> = emptyList(),
)

@Serializable
data class GrammarExampleDto(
    val targetText: String,
    val sourceText: String,
    val note: String = "",
)

@Serializable
data class GrammarRuleDto(
    val id: String,
    val tagLabel: String,
    val title: String,
    val keyPattern: String,
    val summary: String,
    val bulletPoints: List<String>,
    val examples: List<GrammarExampleDto>,
)

@Serializable
data class LanguagePackPayload(
    val profile: PackProfileDto,
    val styles: List<StyleDto>,
    val tracks: List<StudyTrackDto> = emptyList(),
    val affixRules: List<AffixRuleDto>,
    val roots: List<RootDto>,
    val lexemes: List<LexemeDto>,
    val units: List<StudyUnitDto>,
    val passages: List<PassageDto>,
    val passageLines: List<PassageLineDto>,
    val grammarRules: List<GrammarRuleDto>,
)

@Serializable
data class CardStateDto(
    val cardId: String,
    val langCode: String,
    val unitId: String,
    val rootId: String,
    val longStability: Double,
    val shortStability: Double,
    val difficulty: Double,
    val dueEpochMs: Long,
    val lastReviewEpochMs: Long,
    val reps: Int,
    val lapses: Int,
)

@Serializable
data class ReviewLogDto(
    val cardId: String,
    val langCode: String,
    val gradeCode: Int,
    val reviewedAtMs: Long,
    val stabilityAfter: Double,
    val difficultyAfter: Double,
)

@Serializable
data class WordDispositionDto(
    val langCode: String,
    val rootId: String,
    val dispositionCode: Int,
    val updatedAtMs: Long,
)

@Serializable
data class CustomTrackDto(
    val langCode: String,
    val id: String,
    val title: String,
    val rootIds: List<String>,
)

@Serializable
data class ArchivedPassageLineDto(
    val lineIndex: Int,
    val speaker: String = "",
    val text: String,
)

@Serializable
data class ArchivedPassageDto(
    val langCode: String,
    val id: String,
    val title: String,
    val levelTag: String,
    val sceneSummary: String,
    val lines: List<ArchivedPassageLineDto>,
)

@Serializable
data class ReadingBookmarkDto(
    val passageId: String,
    val lineIndex: Int,
    val scrollOffset: Int,
    val completed: Boolean,
)

@Serializable
data class ProgressArchive(
    val exportedAtMs: Long,
    val activeLang: String,
    val appTheme: String = "default",
    val retention: Double,
    val dailyNewLimit: Int,
    val dailyTotalLimit: Int = 80,
    val speechRate: Float,
    val autoPlayAudio: Boolean = true,
    val ttsEnginePkg: String = "",
    val activeTracks: Map<String, String> = emptyMap(),
    val trackOrders: Map<String, String> = emptyMap(),
    val shuffleSeed: Long = 0x517CC1B727220A95L,
    val cardStates: List<CardStateDto>,
    val reviewLogs: List<ReviewLogDto>,
    val dispositions: List<WordDispositionDto> = emptyList(),
    val customTracks: List<CustomTrackDto> = emptyList(),
    val customPassages: List<ArchivedPassageDto> = emptyList(),
    val readingBookmarks: List<ReadingBookmarkDto> = emptyList(),
)
