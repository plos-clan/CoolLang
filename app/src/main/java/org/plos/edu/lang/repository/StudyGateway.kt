package org.plos.edu.lang.repository

import kotlinx.coroutines.flow.Flow
import org.plos.edu.lang.engine.CardId
import org.plos.edu.lang.engine.CustomPassageDraft
import org.plos.edu.lang.engine.CustomTrackDraft
import org.plos.edu.lang.engine.ExtractCommitSpec
import org.plos.edu.lang.engine.ImmersionPassage
import org.plos.edu.lang.engine.LangCode
import org.plos.edu.lang.engine.LanguageProfile
import org.plos.edu.lang.engine.LearnedTodayItem
import org.plos.edu.lang.engine.LearnerPreferences
import org.plos.edu.lang.engine.MasterySnapshot
import org.plos.edu.lang.engine.MorphologyEngine
import org.plos.edu.lang.engine.PassageId
import org.plos.edu.lang.engine.PreferenceMutation
import org.plos.edu.lang.engine.ProgressAnalytics
import org.plos.edu.lang.engine.ReadingBookmark
import org.plos.edu.lang.engine.ReviewGrade
import org.plos.edu.lang.engine.RootId
import org.plos.edu.lang.engine.StudyCard
import org.plos.edu.lang.engine.StudyTrack
import org.plos.edu.lang.engine.StyleKey
import org.plos.edu.lang.engine.TrackId
import org.plos.edu.lang.engine.TrackWordsMutation
import org.plos.edu.lang.engine.UndoPreview
import org.plos.edu.lang.engine.UndoRestoration
import org.plos.edu.lang.engine.WordDisposition

data class LanguageWorkspace(
    val languages: List<LanguageProfile>,
    val activeProfile: LanguageProfile,
    val tracks: List<StudyTrack>,
    val activeTrack: StudyTrack,
    val styleLabels: Map<StyleKey, String>,
    val preferences: LearnerPreferences,
    val analytics: ProgressAnalytics,
    val sessionQueue: List<StudyCard>,
    val learnedToday: List<LearnedTodayItem>,
    val hasRemainingNewUnits: Boolean,
    val undoPreview: UndoPreview?,
    val activeRestoration: UndoRestoration?,
    val passages: List<ImmersionPassage>,
    val mastery: MasterySnapshot,
    val morphology: MorphologyEngine,
) {
    val customTracks: List<StudyTrack>
        get() = tracks.filter { it.isCustom }
}

abstract class StudyGateway {
    abstract fun observeWorkspace(): Flow<LanguageWorkspace>
    abstract suspend fun ensureSeeded()
    abstract suspend fun recordReview(cardId: CardId, grade: ReviewGrade)
    abstract suspend fun markCardMastered(cardId: CardId)
    abstract suspend fun undoLastReview(): UndoRestoration?
    abstract suspend fun selectTrack(trackId: TrackId)
    abstract suspend fun updateWordDispositions(
        rootIds: Set<RootId>,
        disposition: WordDisposition,
    ): Int
    abstract suspend fun undoWordDispositions(): Int
    abstract suspend fun selectLanguage(langCode: LangCode)
    abstract suspend fun mutatePreference(mutation: PreferenceMutation)
    abstract suspend fun boostTodayNewQuota(delta: Int)
}

abstract class CorpusGateway {
    abstract suspend fun saveCustomPassage(
        draft: CustomPassageDraft,
    ): PassageId
    abstract suspend fun deleteCustomPassage(passageId: PassageId): Boolean
    abstract suspend fun restoreDeletedPassage(): PassageId?
    abstract suspend fun saveReadingBookmark(
        passageId: PassageId,
        bookmark: ReadingBookmark,
    )
    abstract suspend fun createCustomTrack(draft: CustomTrackDraft): TrackId
    abstract suspend fun renameCustomTrack(
        langCode: LangCode,
        trackId: TrackId,
        newTitle: String,
    ): Boolean
    abstract suspend fun deleteCustomTrack(
        langCode: LangCode,
        trackId: TrackId,
    ): Boolean
    abstract suspend fun restoreDeletedTrack(): TrackId?
    abstract suspend fun mutateTrackWords(mutation: TrackWordsMutation): Int
    abstract suspend fun applyPickedBooks(
        langCode: LangCode,
        rootIds: Set<RootId>,
        chosenIds: Set<TrackId>,
    ): String?
    abstract suspend fun commitExtraction(
        langCode: LangCode,
        spec: ExtractCommitSpec,
    ): String
}

abstract class ArchiveGateway {
    abstract suspend fun importLanguagePackJson(
        rawJson: String,
    ): LanguageProfile
    abstract suspend fun exportProgressJson(): String
    abstract suspend fun importProgressJson(rawJson: String)
}
