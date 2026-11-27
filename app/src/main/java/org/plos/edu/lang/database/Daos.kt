package org.plos.edu.lang.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    @Query("SELECT * FROM languages ORDER BY code ASC")
    fun observeLanguages(): Flow<List<LanguageEntity>>

    @Query("SELECT * FROM languages WHERE code = :code LIMIT 1")
    suspend fun findLanguage(code: String): LanguageEntity?

    @Query(
        "SELECT * FROM styles WHERE lang_code = :code " +
            "ORDER BY sort_order ASC",
    )
    suspend fun loadStyles(code: String): List<StyleEntity>

    @Query(
        "SELECT * FROM study_tracks WHERE lang_code = :code " +
            "AND is_custom = 0 ORDER BY sort_order ASC",
    )
    suspend fun loadBundledTracks(code: String): List<TrackEntity>

    @Query(
        "SELECT * FROM study_tracks WHERE lang_code = :code " +
            "AND is_custom = 1 ORDER BY sort_order DESC",
    )
    fun observeCustomTracks(code: String): Flow<List<TrackEntity>>

    @Query(
        "SELECT * FROM study_tracks WHERE lang_code = :code " +
            "AND is_custom = 1 ORDER BY sort_order DESC",
    )
    suspend fun loadCustomTracks(code: String): List<TrackEntity>

    @Query(
        "SELECT * FROM study_tracks WHERE lang_code = :code " +
            "AND id = :trackId AND is_custom = 1 LIMIT 1",
    )
    suspend fun findCustomTrack(code: String, trackId: String): TrackEntity?

    @Query("SELECT * FROM affix_rules WHERE lang_code = :code")
    suspend fun loadAffixRules(code: String): List<AffixRuleEntity>

    @Query(
        "SELECT * FROM roots WHERE lang_code = :code " +
            "ORDER BY frequency_rank ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun loadRootsPage(
        code: String,
        limit: Int,
        offset: Int,
    ): List<RootEntity>

    @Query(
        "SELECT * FROM lexemes WHERE lang_code = :code " +
            "ORDER BY id ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun loadLexemesPage(
        code: String,
        limit: Int,
        offset: Int,
    ): List<LexemeEntity>

    @Query(
        "SELECT * FROM grammar_rules WHERE lang_code = :code " +
            "ORDER BY sort_order ASC",
    )
    suspend fun loadGrammarRules(code: String): List<GrammarRuleEntity>

    @Query(
        "SELECT * FROM study_units WHERE lang_code = :code " +
            "ORDER BY rank_order ASC",
    )
    fun observeUnits(code: String): Flow<List<UnitEntity>>

    @Query(
        "SELECT * FROM passages WHERE lang_code = :code " +
            "ORDER BY is_custom DESC, sort_order ASC",
    )
    fun observePassages(code: String): Flow<List<PassageEntity>>

    @Query(
        "SELECT * FROM passage_lines WHERE lang_code = :code " +
            "ORDER BY line_index ASC",
    )
    fun observePassageLines(code: String): Flow<List<PassageLineEntity>>

    @Query("SELECT * FROM card_states WHERE lang_code = :code")
    fun observeCardStates(code: String): Flow<List<CardStateEntity>>

    @Query("SELECT * FROM review_logs WHERE lang_code = :code")
    fun observeReviewLogs(code: String): Flow<List<ReviewLogEntity>>

    @Query("SELECT * FROM word_dispositions WHERE lang_code = :code")
    fun observeWordDispositions(code: String): Flow<List<WordDispositionEntity>>

    @Query("SELECT * FROM reading_progress")
    fun observeReadingProgress(): Flow<List<ReadingProgressEntity>>

    @Query(
        "SELECT * FROM passages WHERE lang_code = :code " +
            "AND id = :passageId AND is_custom = 1 LIMIT 1",
    )
    suspend fun findCustomPassage(
        code: String,
        passageId: String,
    ): PassageEntity?

    @Query(
        "SELECT * FROM passage_lines WHERE lang_code = :code " +
            "AND passage_id = :passageId ORDER BY line_index ASC",
    )
    suspend fun loadLinesForPassage(
        code: String,
        passageId: String,
    ): List<PassageLineEntity>

    @Query(
        "SELECT * FROM reading_progress WHERE passage_id = :passageId LIMIT 1",
    )
    suspend fun findReadingProgress(passageId: String): ReadingProgressEntity?

    @Query("SELECT * FROM card_states WHERE card_id = :cardId LIMIT 1")
    suspend fun findCardState(cardId: String): CardStateEntity?

    @Query(
        "SELECT * FROM study_units WHERE lang_code = :code " +
            "AND id = :id LIMIT 1",
    )
    suspend fun findUnit(code: String, id: String): UnitEntity?

    @Query(
        "SELECT * FROM roots WHERE lang_code = :code " +
            "AND id = :id LIMIT 1",
    )
    suspend fun findRoot(code: String, id: String): RootEntity?

    @Query(
        "SELECT * FROM word_dispositions WHERE lang_code = :code " +
            "AND root_id IN (:rootIds)",
    )
    suspend fun findWordDispositions(
        code: String,
        rootIds: List<String>,
    ): List<WordDispositionEntity>

    @Query(
        "SELECT * FROM card_states WHERE lang_code = :code " +
            "AND root_id IN (:rootIds)",
    )
    suspend fun findCardStatesByRoots(
        code: String,
        rootIds: List<String>,
    ): List<CardStateEntity>

    @Query(
        "SELECT COALESCE(MAX(sort_order), 0) FROM study_tracks " +
            "WHERE lang_code = :code AND is_custom = 1",
    )
    suspend fun maxCustomTrackOrder(code: String): Int

    @Query(
        "SELECT COALESCE(MAX(sort_order), 0) FROM passages " +
            "WHERE lang_code = :code",
    )
    suspend fun maxPassageOrder(code: String): Int

    @Query("SELECT * FROM card_states")
    suspend fun loadAllCardStates(): List<CardStateEntity>

    @Query("SELECT * FROM review_logs ORDER BY reviewed_at_ms ASC")
    suspend fun loadAllReviewLogs(): List<ReviewLogEntity>

    @Query("SELECT * FROM word_dispositions")
    suspend fun loadAllWordDispositions(): List<WordDispositionEntity>

    @Query("SELECT * FROM reading_progress")
    suspend fun loadAllReadingProgress(): List<ReadingProgressEntity>

    @Query(
        "SELECT * FROM study_tracks WHERE is_custom = 1 " +
            "ORDER BY sort_order ASC",
    )
    suspend fun loadAllCustomTracks(): List<TrackEntity>

    @Query("SELECT * FROM passages WHERE is_custom = 1")
    suspend fun loadCustomPassages(): List<PassageEntity>

    @Query(
        "SELECT l.* FROM passage_lines l INNER JOIN passages p " +
            "ON l.lang_code = p.lang_code AND l.passage_id = p.id " +
            "WHERE p.is_custom = 1",
    )
    suspend fun loadCustomPassageLines(): List<PassageLineEntity>

    @Query(
        "DELETE FROM study_tracks WHERE lang_code = :code " +
            "AND id = :trackId AND is_custom = 1",
    )
    suspend fun removeCustomTrack(code: String, trackId: String)

    @Query(
        "DELETE FROM passages WHERE lang_code = :code " +
            "AND id = :passageId AND is_custom = 1",
    )
    suspend fun removeCustomPassage(code: String, passageId: String)

    @Query(
        "DELETE FROM passage_lines WHERE lang_code = :code " +
            "AND passage_id = :passageId",
    )
    suspend fun removePassageLines(code: String, passageId: String)

    @Query("DELETE FROM card_states WHERE card_id = :cardId")
    suspend fun deleteCardState(cardId: String)

    @Query(
        "DELETE FROM card_states WHERE lang_code = :code " +
            "AND root_id IN (:rootIds)",
    )
    suspend fun deleteCardStatesByRoots(code: String, rootIds: List<String>)

    @Query(
        "DELETE FROM word_dispositions WHERE lang_code = :code " +
            "AND root_id IN (:rootIds)",
    )
    suspend fun deleteWordDispositions(code: String, rootIds: List<String>)

    @Query("DELETE FROM review_logs WHERE log_id = :logId")
    suspend fun deleteReviewLogById(logId: Long)

    @Query("DELETE FROM reading_progress WHERE passage_id = :passageId")
    suspend fun deleteReadingProgress(passageId: String)

    @Upsert
    suspend fun upsertReadingProgress(entity: ReadingProgressEntity)

    @Upsert
    suspend fun upsertReadingProgressList(entities: List<ReadingProgressEntity>)

    @Query("DELETE FROM study_units WHERE lang_code = :code")
    suspend fun clearUnits(code: String)

    @Query(
        "DELETE FROM passage_lines WHERE lang_code = :code " +
            "AND passage_id IN (SELECT id FROM passages " +
            "WHERE lang_code = :code AND is_custom = 0)",
    )
    suspend fun clearBundledPassageLines(code: String)

    @Query("DELETE FROM passages WHERE lang_code = :code AND is_custom = 0")
    suspend fun clearBundledPassages(code: String)

    @Query("DELETE FROM styles WHERE lang_code = :code")
    suspend fun clearStyles(code: String)

    @Query(
        "DELETE FROM study_tracks WHERE lang_code = :code AND is_custom = 0",
    )
    suspend fun clearBundledTracks(code: String)

    @Query("DELETE FROM affix_rules WHERE lang_code = :code")
    suspend fun clearAffixRules(code: String)

    @Query("DELETE FROM roots WHERE lang_code = :code")
    suspend fun clearRoots(code: String)

    @Query("DELETE FROM lexemes WHERE lang_code = :code")
    suspend fun clearLexemes(code: String)

    @Query("DELETE FROM grammar_rules WHERE lang_code = :code")
    suspend fun clearGrammarRules(code: String)

    @Upsert
    suspend fun upsertLanguage(entity: LanguageEntity)

    @Upsert
    suspend fun upsertStyles(entities: List<StyleEntity>)

    @Upsert
    suspend fun upsertTracks(entities: List<TrackEntity>)

    @Upsert
    suspend fun upsertAffixRules(entities: List<AffixRuleEntity>)

    @Upsert
    suspend fun upsertRoots(entities: List<RootEntity>)

    @Upsert
    suspend fun upsertLexemes(entities: List<LexemeEntity>)

    @Upsert
    suspend fun upsertUnits(entities: List<UnitEntity>)

    @Upsert
    suspend fun upsertPassages(entities: List<PassageEntity>)

    @Upsert
    suspend fun upsertPassageLines(entities: List<PassageLineEntity>)

    @Upsert
    suspend fun upsertGrammarRules(entities: List<GrammarRuleEntity>)

    @Upsert
    suspend fun upsertCardState(entity: CardStateEntity)

    @Upsert
    suspend fun upsertCardStates(entities: List<CardStateEntity>)

    @Upsert
    suspend fun upsertWordDispositions(entities: List<WordDispositionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReviewLog(entity: ReviewLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReviewLogs(entities: List<ReviewLogEntity>)
}
