package org.plos.edu.lang.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "languages")
data class LanguageEntity(
    @PrimaryKey @ColumnInfo(name = "code") val code: String,
    @ColumnInfo(name = "target_name") val targetName: String,
    @ColumnInfo(name = "localized_name") val localizedName: String,
    @ColumnInfo(name = "source_name") val sourceName: String,
    @ColumnInfo(name = "tts_locale_tag") val ttsLocaleTag: String,
    @ColumnInfo(name = "default_track_id") val defaultTrackId: String,
    @ColumnInfo(name = "pack_version") val packVersion: Int,
)

@Entity(
    tableName = "styles",
    primaryKeys = ["lang_code", "style_key"],
)
data class StyleEntity(
    @ColumnInfo(name = "lang_code") val langCode: String,
    @ColumnInfo(name = "style_key") val styleKey: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(
    tableName = "study_tracks",
    primaryKeys = ["lang_code", "id"],
)
data class TrackEntity(
    @ColumnInfo(name = "lang_code") val langCode: String,
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "summary") val summary: String,
    @ColumnInfo(name = "default_order") val defaultOrder: String,
    @ColumnInfo(name = "root_ids_csv") val rootIdsCsv: String,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(
    tableName = "affix_rules",
    primaryKeys = ["lang_code", "id"],
)
data class AffixRuleEntity(
    @ColumnInfo(name = "lang_code") val langCode: String,
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "prefix") val prefix: String,
    @ColumnInfo(name = "suffix") val suffix: String,
    @ColumnInfo(name = "restored_initial") val restoredInitial: String,
    @ColumnInfo(name = "restored_ending") val restoredEnding: String,
    @ColumnInfo(name = "pattern_label") val patternLabel: String,
    @ColumnInfo(name = "function_summary") val functionSummary: String,
)

@Entity(
    tableName = "roots",
    primaryKeys = ["lang_code", "id"],
    indices = [Index(value = ["lang_code", "frequency_rank"])],
)
data class RootEntity(
    @ColumnInfo(name = "lang_code") val langCode: String,
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "lemma") val lemma: String,
    @ColumnInfo(name = "spoken_forms_csv") val spokenFormsCsv: String,
    @ColumnInfo(name = "phonetic_hint") val phoneticHint: String,
    @ColumnInfo(name = "senses_lines") val sensesLines: String,
    @ColumnInfo(name = "usage_note") val usageNote: String,
    @ColumnInfo(name = "collocations_lines") val collocationsLines: String,
    @ColumnInfo(name = "examples_lines") val examplesLines: String,
    @ColumnInfo(name = "frequency_rank") val frequencyRank: Int,
)

@Entity(
    tableName = "lexemes",
    primaryKeys = ["lang_code", "id"],
    indices = [Index(value = ["lang_code", "root_id"])],
)
data class LexemeEntity(
    @ColumnInfo(name = "lang_code") val langCode: String,
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "root_id") val rootId: String,
    @ColumnInfo(name = "surface") val surface: String,
    @ColumnInfo(name = "phonetic_hint") val phoneticHint: String,
    @ColumnInfo(name = "style_key") val styleKey: String,
    @ColumnInfo(name = "breakdown") val breakdown: String,
    @ColumnInfo(name = "meaning") val meaning: String,
    @ColumnInfo(name = "counterpart") val counterpart: String,
)

@Entity(
    tableName = "study_units",
    primaryKeys = ["lang_code", "id"],
    indices = [Index(value = ["lang_code", "rank_order"])],
)
data class UnitEntity(
    @ColumnInfo(name = "lang_code") val langCode: String,
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "target_root_id") val targetRootId: String,
    @ColumnInfo(name = "headword") val headword: String,
    @ColumnInfo(name = "variant_forms_csv") val variantFormsCsv: String,
    @ColumnInfo(name = "style_badge") val styleBadge: String,
    @ColumnInfo(name = "phonetic_hint") val phoneticHint: String,
    @ColumnInfo(name = "senses_lines") val sensesLines: String,
    @ColumnInfo(name = "examples_json") val examplesJson: String,
    @ColumnInfo(name = "contrast_label") val contrastLabel: String,
    @ColumnInfo(name = "contrast_text") val contrastText: String,
    @ColumnInfo(name = "rank_order") val rankOrder: Int,
)

@Entity(
    tableName = "passages",
    primaryKeys = ["lang_code", "id"],
)
data class PassageEntity(
    @ColumnInfo(name = "lang_code") val langCode: String,
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "level_tag") val levelTag: String,
    @ColumnInfo(name = "scene_summary") val sceneSummary: String,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(
    tableName = "passage_lines",
    primaryKeys = ["lang_code", "passage_id", "line_index"],
)
data class PassageLineEntity(
    @ColumnInfo(name = "lang_code") val langCode: String,
    @ColumnInfo(name = "passage_id") val passageId: String,
    @ColumnInfo(name = "line_index") val lineIndex: Int,
    @ColumnInfo(name = "speaker") val speaker: String,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "source_translation") val sourceTranslation: String,
    @ColumnInfo(name = "contrast_label") val contrastLabel: String,
    @ColumnInfo(name = "contrast_text") val contrastText: String,
    @ColumnInfo(name = "grammar_ids_csv") val grammarIdsCsv: String,
)

@Entity(
    tableName = "grammar_rules",
    primaryKeys = ["lang_code", "id"],
)
data class GrammarRuleEntity(
    @ColumnInfo(name = "lang_code") val langCode: String,
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "tag_label") val tagLabel: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "key_pattern") val keyPattern: String,
    @ColumnInfo(name = "summary") val summary: String,
    @ColumnInfo(name = "bullets_json") val bulletsJson: String,
    @ColumnInfo(name = "examples_json") val examplesJson: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(
    tableName = "card_states",
    indices = [Index(value = ["lang_code", "due_epoch_ms"])],
)
data class CardStateEntity(
    @PrimaryKey @ColumnInfo(name = "card_id") val cardId: String,
    @ColumnInfo(name = "lang_code") val langCode: String,
    @ColumnInfo(name = "unit_id") val unitId: String,
    @ColumnInfo(name = "root_id") val rootId: String,
    @ColumnInfo(name = "long_stability") val longStability: Double,
    @ColumnInfo(name = "short_stability") val shortStability: Double,
    @ColumnInfo(name = "difficulty") val difficulty: Double,
    @ColumnInfo(name = "due_epoch_ms") val dueEpochMs: Long,
    @ColumnInfo(name = "last_review_epoch_ms") val lastReviewEpochMs: Long,
    @ColumnInfo(name = "reps") val reps: Int,
    @ColumnInfo(name = "lapses") val lapses: Int,
)

@Entity(
    tableName = "review_logs",
    indices = [
        Index(value = ["lang_code", "reviewed_at_ms"]),
        Index(value = ["card_id", "reviewed_at_ms"], unique = true),
    ],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "log_id")
    val logId: Long = 0L,
    @ColumnInfo(name = "card_id") val cardId: String,
    @ColumnInfo(name = "lang_code") val langCode: String,
    @ColumnInfo(name = "grade_code") val gradeCode: Int,
    @ColumnInfo(name = "reviewed_at_ms") val reviewedAtMs: Long,
    @ColumnInfo(name = "stability_after") val stabilityAfter: Double,
    @ColumnInfo(name = "difficulty_after") val difficultyAfter: Double,
)

@Entity(
    tableName = "word_dispositions",
    primaryKeys = ["lang_code", "root_id"],
)
data class WordDispositionEntity(
    @ColumnInfo(name = "lang_code") val langCode: String,
    @ColumnInfo(name = "root_id") val rootId: String,
    @ColumnInfo(name = "disposition_code") val dispositionCode: Int,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
)

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey @ColumnInfo(name = "passage_id") val passageId: String,
    @ColumnInfo(name = "line_index") val lineIndex: Int,
    @ColumnInfo(name = "scroll_offset") val scrollOffset: Int,
    @ColumnInfo(name = "completed") val completed: Boolean,
)
