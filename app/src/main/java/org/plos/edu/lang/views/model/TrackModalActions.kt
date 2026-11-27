package org.plos.edu.lang.views.model

import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.plos.edu.lang.engine.CustomTrackDraft
import org.plos.edu.lang.engine.ExtractCommitSpec
import org.plos.edu.lang.engine.ImmersionPassage
import org.plos.edu.lang.engine.RootId
import org.plos.edu.lang.engine.StudyTrack
import org.plos.edu.lang.engine.TrackId
import org.plos.edu.lang.engine.TrackMembershipOp
import org.plos.edu.lang.engine.TrackWordsMutation
import org.plos.edu.lang.repository.CorpusGateway

@Stable
class TrackModalActions(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<AppUiState>,
    private val corpusGateway: CorpusGateway,
    private val buildNotice: (String, NoticeUndoKind?) -> NoticeSpec,
) {
    fun open(modal: TrackModalState?) {
        uiState.update { it.copy(trackModal = modal) }
    }

    fun openForPassage(passage: ImmersionPassage) {
        val fullText = passage.lines.joinToString("\n") { it.text }
        val modal = TrackModalState.TextExtractor(
            targetTrackId = null,
            defaultBookTitle = passage.title,
            initialText = fullText,
            isProseSource = true,
        )
        open(modal)
    }

    fun onTapBookmark(rootId: RootId) {
        val ws = uiState.value.workspace ?: return
        val custom = ws.customTracks
        if (custom.isEmpty()) {
            open(TrackModalState.Create(listOf(rootId)))
            return
        }
        if (custom.size >= 2) {
            open(TrackModalState.PickBooks(setOf(rootId)))
            return
        }
        val book = custom.first()
        val removing = rootId in book.rootIds
        val op = if (removing) {
            TrackMembershipOp.REMOVE
        } else {
            TrackMembershipOp.ADD
        }
        val mut = TrackWordsMutation(
            langCode = ws.activeProfile.code,
            trackId = book.id,
            rootIds = listOf(rootId),
            op = op,
        )
        scope.launch {
            if (corpusGateway.mutateTrackWords(mut) > 0) {
                val verb = if (removing) {
                    "从「${book.title}」移出"
                } else {
                    "加入「${book.title}」"
                }
                val notice = buildNotice("已$verb", null)
                uiState.update { it.copy(activeNotice = notice) }
            }
        }
    }

    fun addBatchToCustomBook() {
        val ws = uiState.value.workspace ?: return
        val selected = uiState.value.lexiconBatch.selectedIds
        if (selected.isEmpty()) return
        val custom = ws.customTracks
        if (custom.isEmpty()) {
            open(TrackModalState.Create(selected.toList()))
            return
        }
        if (custom.size >= 2) {
            open(TrackModalState.PickBooks(selected))
            return
        }
        val book = custom.first()
        val mut = TrackWordsMutation(
            langCode = ws.activeProfile.code,
            trackId = book.id,
            rootIds = selected,
            op = TrackMembershipOp.ADD,
        )
        scope.launch {
            val added = corpusGateway.mutateTrackWords(mut)
            val msg = if (added > 0) {
                "已将 $added 词加入「${book.title}」"
            } else {
                "所选词条已在「${book.title}」中"
            }
            finishModal(msg, closeInspector = false)
        }
    }

    fun removeFromActiveTrack(rootIds: Set<RootId>) {
        val ws = uiState.value.workspace ?: return
        val track = ws.activeTrack
        if (!track.isCustom || rootIds.isEmpty()) return
        val mut = TrackWordsMutation(
            langCode = ws.activeProfile.code,
            trackId = track.id,
            rootIds = rootIds,
            op = TrackMembershipOp.REMOVE,
        )
        scope.launch {
            val removed = corpusGateway.mutateTrackWords(mut)
            if (removed > 0) {
                finishModal("已从「${track.title}」移出 $removed 词", true)
            }
        }
    }

    fun submitCreateOrRename(
        title: String,
        renameId: TrackId?,
        pendingRootIds: List<RootId>,
    ) {
        val lang = uiState.value.workspace?.activeProfile?.code ?: return
        scope.launch {
            if (renameId != null) {
                corpusGateway.renameCustomTrack(lang, renameId, title)
                finishModal(null, closeInspector = false)
                return@launch
            }
            val clean = title.trim().ifEmpty { "自建词书" }
            val draft = CustomTrackDraft(lang, clean, pendingRootIds)
            corpusGateway.createCustomTrack(draft)
            val msg = if (pendingRootIds.isEmpty()) {
                "已创建词书「$clean」"
            } else {
                "已创建「$clean」并加入 ${pendingRootIds.size} 词"
            }
            finishModal(msg, closeInspector = false)
        }
    }

    fun deleteTrack(track: StudyTrack) {
        val lang = uiState.value.workspace?.activeProfile?.code ?: return
        if (!track.isCustom) return
        scope.launch {
            if (corpusGateway.deleteCustomTrack(lang, track.id)) {
                val spec = buildNotice(
                    "已删除词书「${track.title}」",
                    NoticeUndoKind.TRACK_DELETE,
                )
                uiState.update { it.copy(activeNotice = spec) }
            }
        }
    }

    fun commitPickedBooks(rootIds: Set<RootId>, chosenIds: Set<TrackId>) {
        val lang = uiState.value.workspace?.activeProfile?.code ?: return
        scope.launch {
            val msg = corpusGateway.applyPickedBooks(lang, rootIds, chosenIds)
            finishModal(msg, closeInspector = false)
        }
    }

    fun commitExtraction(spec: ExtractCommitSpec) {
        val lang = uiState.value.workspace?.activeProfile?.code ?: return
        if (spec.rootIds.isEmpty()) return
        scope.launch {
            val msg = corpusGateway.commitExtraction(lang, spec)
            finishModal(msg, closeInspector = false)
        }
    }

    private fun finishModal(noticeMsg: String?, closeInspector: Boolean) {
        val notice = noticeMsg?.let { buildNotice(it, null) }
        uiState.update { prev ->
            val nextSheet = if (closeInspector &&
                prev.activeSheet is ActiveSheet.Word
            ) {
                null
            } else {
                prev.activeSheet
            }
            prev.copy(
                trackModal = null,
                activeSheet = nextSheet,
                lexiconBatch = BatchSelectionState(),
                activeNotice = notice ?: prev.activeNotice,
            )
        }
    }
}
