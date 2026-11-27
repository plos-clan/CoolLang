package org.plos.edu.lang.views.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.plos.edu.lang.engine.CardId
import org.plos.edu.lang.engine.CustomPassageDraft
import org.plos.edu.lang.engine.ImmersionPassage
import org.plos.edu.lang.engine.LangCode
import org.plos.edu.lang.engine.PassageId
import org.plos.edu.lang.engine.PreferenceMutation
import org.plos.edu.lang.engine.ReadingBookmark
import org.plos.edu.lang.engine.ReviewGrade
import org.plos.edu.lang.engine.RootId
import org.plos.edu.lang.engine.RootOverview
import org.plos.edu.lang.engine.StudyTrack
import org.plos.edu.lang.engine.TrackId
import org.plos.edu.lang.engine.WordDisposition
import org.plos.edu.lang.engine.WordResolution
import org.plos.edu.lang.modules.PassagePlayback
import org.plos.edu.lang.modules.SpeechConductor
import org.plos.edu.lang.modules.SpeechStatus
import org.plos.edu.lang.modules.TtsEngineOption
import org.plos.edu.lang.repository.ArchiveGateway
import org.plos.edu.lang.repository.CorpusGateway
import org.plos.edu.lang.repository.LanguageWorkspace
import org.plos.edu.lang.repository.StudyGateway

private const val BONUS_NEW_BATCH: Int = 10

private data class StreamBundle(
    val workspace: LanguageWorkspace,
    val speech: SpeechStatus,
    val engines: List<TtsEngineOption>,
    val playback: PassagePlayback?,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val gateway: StudyGateway,
    private val corpusGateway: CorpusGateway,
    private val archiveGateway: ArchiveGateway,
    val speech: SpeechConductor,
) : ViewModel() {
    private val uiState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = uiState.asStateFlow()
    private val noticeSeq = AtomicLong(0L)
    val customTracks = TrackModalActions(
        scope = viewModelScope,
        uiState = uiState,
        corpusGateway = corpusGateway,
        buildNotice = ::buildNotice,
    )

    init {
        observeStreams()
    }

    fun revealCard() {
        uiState.update { it.copy(cardRevealed = true) }
    }

    fun gradeCard(cardId: CardId, grade: ReviewGrade) {
        viewModelScope.launch {
            speech.stop()
            speech.armNextCardAutoSpeak()
            uiState.update { it.copy(cardRevealed = false) }
            gateway.recordReview(cardId, grade)
        }
    }

    fun markCardMastered(cardId: CardId, headword: String) {
        viewModelScope.launch {
            speech.stop()
            speech.armNextCardAutoSpeak()
            val notice = buildNotice(
                message = "已标记「$headword」为熟知",
                undoKind = NoticeUndoKind.CARD_REVIEW_UNDO,
            )
            uiState.update {
                it.copy(cardRevealed = false, activeNotice = notice)
            }
            gateway.markCardMastered(cardId)
        }
    }

    fun undoPreviousCard() {
        val current = uiState.value
        if (current.cardRevealed) return
        if (current.workspace?.undoPreview == null) return
        viewModelScope.launch {
            speech.stop()
            gateway.undoLastReview()
        }
    }

    fun boostTodayNewQuota() {
        viewModelScope.launch {
            speech.armNextCardAutoSpeak()
            gateway.boostTodayNewQuota(BONUS_NEW_BATCH)
            postNotice("今日已临时增加 $BONUS_NEW_BATCH 张学习额度")
        }
    }

    fun saveReadingBookmark(passageId: PassageId, bookmark: ReadingBookmark) {
        viewModelScope.launch {
            corpusGateway.saveReadingBookmark(passageId, bookmark)
        }
    }

    fun showSheet(sheet: ActiveSheet?) {
        uiState.update { it.copy(activeSheet = sheet) }
    }

    fun inspectWord(word: String) {
        val ws = uiState.value.workspace ?: return
        val resolved = ws.morphology.resolveWord(word, ws.mastery)
        val inspector = WordInspectorState(resolved, InspectorScope.LOOKUP)
        uiState.update { it.copy(activeSheet = ActiveSheet.Word(inspector)) }
    }

    fun inspectRoot(
        overview: RootOverview,
        scope: InspectorScope = InspectorScope.LOOKUP,
    ) {
        val resolution = WordResolution(
            surface = overview.root.lemma,
            root = overview.root,
            matchedLexeme = null,
            breakdown = overview.root.lemma,
            appliedRule = null,
            familyMembers = overview.members,
            stage = overview.stage,
            disposition = overview.disposition,
            planStage = overview.planStage,
            familiarityRatio = overview.familiarityRatio,
        )
        val inspector = WordInspectorState(resolution, scope)
        uiState.update { it.copy(activeSheet = ActiveSheet.Word(inspector)) }
    }

    fun selectTrack(trackId: TrackId) {
        viewModelScope.launch {
            uiState.update { it.copy(lexiconBatch = BatchSelectionState()) }
            gateway.selectTrack(trackId)
        }
    }

    fun markEntireTrackMastered(track: StudyTrack) {
        val targets = track.rootIds.toSet()
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val count = gateway.updateWordDispositions(
                targets,
                WordDisposition.MASTERED,
            )
            if (count <= 0) return@launch
            val msg = "已将「${track.title}」共 $count 词标为熟知"
            val spec = buildNotice(msg, NoticeUndoKind.WORD_DISPOSITION)
            uiState.update { it.copy(activeNotice = spec) }
        }
    }

    fun modifyBatch(
        transform: (
            BatchSelectionState,
            List<LexiconSection>,
        ) -> BatchSelectionState,
    ) {
        uiState.update { prev ->
            val next = transform(prev.lexiconBatch, prev.lexiconSections)
            prev.copy(lexiconBatch = next)
        }
    }

    fun applyBatchDisposition(disposition: WordDisposition) {
        val targets = uiState.value.lexiconBatch.selectedIds
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val count = gateway.updateWordDispositions(targets, disposition)
            if (count <= 0) return@launch
            val label = dispositionNoticeText(count, disposition)
            val spec = buildNotice(label, NoticeUndoKind.WORD_DISPOSITION)
            uiState.update {
                it.copy(
                    lexiconBatch = BatchSelectionState(),
                    activeNotice = spec,
                )
            }
        }
    }

    fun setWordsDisposition(
        rootIds: Set<RootId>,
        disposition: WordDisposition,
    ) {
        if (rootIds.isEmpty()) return
        viewModelScope.launch {
            val count = gateway.updateWordDispositions(rootIds, disposition)
            if (count <= 0) return@launch
            val label = dispositionNoticeText(count, disposition)
            val spec = buildNotice(label, NoticeUndoKind.WORD_DISPOSITION)
            uiState.update { prev ->
                val cleared = prev.activeSheet.takeUnless {
                    it is ActiveSheet.Word
                }
                prev.copy(activeSheet = cleared, activeNotice = spec)
            }
        }
    }

    fun modifyLexiconFilter(transform: (LexiconFilter) -> LexiconFilter) {
        uiState.update { prev ->
            val next = transform(prev.lexiconFilter)
            prev.copy(
                lexiconFilter = next,
                lexiconSections = next.buildSections(prev.workspace),
            )
        }
    }

    fun modifyPassageFilter(transform: (PassageFilter) -> PassageFilter) {
        uiState.update { prev ->
            val next = transform(prev.passageFilter)
            prev.copy(
                passageFilter = next,
                visiblePassages = next.apply(prev.workspace),
            )
        }
    }

    fun submitCustomPassage(draft: CustomPassageDraft) {
        val isEdit = draft.editId != null
        viewModelScope.launch {
            val savedId = corpusGateway.saveCustomPassage(draft)
            val msg = if (isEdit) "已保存文章修改" else "已按句切分并导入阅读文本"
            uiState.update {
                it.copy(
                    activeSheet = null,
                    scrollTargetPassageId = if (isEdit) null else savedId,
                    activeNotice = buildNotice(msg, null),
                )
            }
        }
    }

    fun deleteCustomPassage(passage: ImmersionPassage) {
        viewModelScope.launch {
            speech.stop()
            val deleted = corpusGateway.deleteCustomPassage(passage.id)
            if (deleted) {
                val spec = buildNotice(
                    message = "已删除「${passage.title}」",
                    undoKind = NoticeUndoKind.PASSAGE_DELETE,
                )
                uiState.update { it.copy(activeNotice = spec) }
            }
        }
    }

    fun performNoticeUndo(kind: NoticeUndoKind) {
        when (kind) {
            NoticeUndoKind.CARD_REVIEW_UNDO -> undoPreviousCard()
            NoticeUndoKind.PASSAGE_DELETE -> restoreDeletedPassageAsync()
            NoticeUndoKind.TRACK_DELETE -> {
                viewModelScope.launch { corpusGateway.restoreDeletedTrack() }
            }
            NoticeUndoKind.WORD_DISPOSITION -> {
                viewModelScope.launch { gateway.undoWordDispositions() }
            }
        }
    }

    fun clearPassageScrollTarget() {
        uiState.update { it.copy(scrollTargetPassageId = null) }
    }

    fun switchLanguage(langCode: LangCode) {
        viewModelScope.launch {
            speech.stop()
            gateway.selectLanguage(langCode)
            uiState.update { prev ->
                val resetLex = prev.lexiconFilter.copy(
                    searchOpen = false,
                    query = "",
                )
                val resetPass = prev.passageFilter.copy(
                    searchOpen = false,
                    query = "",
                )
                prev.copy(
                    lexiconFilter = resetLex,
                    passageFilter = resetPass,
                    lexiconBatch = BatchSelectionState(),
                    activeSheet = null,
                    trackModal = null,
                )
            }
        }
    }

    fun applyPreference(mutation: PreferenceMutation) {
        viewModelScope.launch {
            gateway.mutatePreference(mutation)
        }
    }

    fun exportProgress(onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { archiveGateway.exportProgressJson() }
                .onSuccess(onReady)
                .onFailure { postNotice("导出失败") }
        }
    }

    fun importProgress(rawJson: String) {
        viewModelScope.launch {
            speech.stop()
            runCatching { archiveGateway.importProgressJson(rawJson) }
                .onSuccess { postNotice("学习进度已恢复") }
                .onFailure { postNotice("备份格式无效") }
        }
    }

    fun importLanguagePack(rawJson: String) {
        viewModelScope.launch {
            speech.stop()
            runCatching { archiveGateway.importLanguagePackJson(rawJson) }
                .onSuccess { p -> postNotice("已载入语言包：${p.localizedName}") }
                .onFailure { postNotice("语言包解析失败") }
        }
    }

    fun clearNotice(noticeId: Long) {
        uiState.update { prev ->
            if (prev.activeNotice?.id == noticeId) {
                prev.copy(activeNotice = null)
            } else {
                prev
            }
        }
    }

    private fun restoreDeletedPassageAsync() {
        viewModelScope.launch {
            val restoredId = corpusGateway.restoreDeletedPassage()
                ?: return@launch
            uiState.update { it.copy(scrollTargetPassageId = restoredId) }
        }
    }

    private fun dispositionNoticeText(
        count: Int,
        disposition: WordDisposition,
    ): String {
        val prefix = if (count == 1) "已" else "已将 $count 词"
        return when (disposition) {
            WordDisposition.MASTERED -> "${prefix}标为熟知"
            WordDisposition.IGNORED -> "${prefix}设为不想背"
            WordDisposition.AUTO -> "${prefix}恢复为待学计划"
        }
    }

    private fun observeStreams() {
        viewModelScope.launch {
            gateway.ensureSeeded()
            combine(
                gateway.observeWorkspace(),
                speech.status,
                speech.engines,
                speech.playback,
            ) { workspace, speechState, engines, playback ->
                StreamBundle(workspace, speechState, engines, playback)
            }.collect(::applySnapshot)
        }
    }

    private fun applySnapshot(bundle: StreamBundle) {
        val workspace = bundle.workspace
        speech.syncWorkspace(workspace)
        val topCard = workspace.sessionQueue.firstOrNull()
        uiState.update { prev ->
            val prevTopId = prev.workspace?.sessionQueue?.firstOrNull()?.id
            val nextTopId = topCard?.id
            val sameCard = prevTopId != null && prevTopId == nextTopId
            val prevRest = prev.workspace?.activeRestoration
            val nextRest = workspace.activeRestoration
            val newlyRestored = nextTopId != null &&
                nextRest?.cardId == nextTopId &&
                nextRest != prevRest
            val keepRevealed = newlyRestored || (sameCard && prev.cardRevealed)
            prev.copy(
                phase = BootPhase.Ready(workspace),
                speechStatus = bundle.speech,
                ttsEngines = bundle.engines,
                playback = bundle.playback,
                cardRevealed = keepRevealed,
                lexiconSections = prev.lexiconFilter.buildSections(workspace),
                visiblePassages = prev.passageFilter.apply(workspace),
            )
        }
    }

    private fun buildNotice(
        message: String,
        undoKind: NoticeUndoKind?,
    ): NoticeSpec = NoticeSpec(
        id = noticeSeq.incrementAndGet(),
        message = message,
        undoKind = undoKind,
    )

    private fun postNotice(message: String) {
        val spec = buildNotice(message, null)
        uiState.update { it.copy(activeNotice = spec) }
    }
}
