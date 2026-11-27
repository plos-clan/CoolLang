package org.plos.edu.lang.views.model

import org.plos.edu.lang.engine.GrammarRule
import org.plos.edu.lang.engine.ImmersionPassage
import org.plos.edu.lang.engine.LexiconGroup
import org.plos.edu.lang.engine.LexiconSort
import org.plos.edu.lang.engine.PassageCorpus
import org.plos.edu.lang.engine.PassageGenre
import org.plos.edu.lang.engine.PassageId
import org.plos.edu.lang.engine.PassageSearchHit
import org.plos.edu.lang.engine.PassageSort
import org.plos.edu.lang.engine.RootId
import org.plos.edu.lang.engine.RootOverview
import org.plos.edu.lang.engine.TrackId
import org.plos.edu.lang.engine.WordResolution
import org.plos.edu.lang.modules.PassagePlayback
import org.plos.edu.lang.modules.SpeechStatus
import org.plos.edu.lang.modules.TtsEngineOption
import org.plos.edu.lang.repository.LanguageWorkspace

sealed interface BootPhase {
    data object Loading : BootPhase

    data class Ready(
        val workspace: LanguageWorkspace,
    ) : BootPhase
}

sealed interface ActiveSheet {
    data class Word(val state: WordInspectorState) : ActiveSheet

    data class Grammar(val rule: GrammarRule) : ActiveSheet

    data class PassageEditor(val state: PassageEditorState) : ActiveSheet
}

data class AppUiState(
    val phase: BootPhase = BootPhase.Loading,
    val speechStatus: SpeechStatus = SpeechStatus.INITIALIZING,
    val ttsEngines: List<TtsEngineOption> = emptyList(),
    val cardRevealed: Boolean = false,
    val lexiconFilter: LexiconFilter = LexiconFilter(),
    val lexiconBatch: BatchSelectionState = BatchSelectionState(),
    val lexiconSections: List<LexiconSection> = emptyList(),
    val passageFilter: PassageFilter = PassageFilter(),
    val visiblePassages: List<PassageSearchHit> = emptyList(),
    val scrollTargetPassageId: PassageId? = null,
    val playback: PassagePlayback? = null,
    val activeSheet: ActiveSheet? = null,
    val trackModal: TrackModalState? = null,
    val activeNotice: NoticeSpec? = null,
) {
    val workspace: LanguageWorkspace?
        get() = (phase as? BootPhase.Ready)?.workspace
}

data class PassageEditorState(
    val editPassageId: PassageId? = null,
    val initialTitle: String = "",
    val initialBody: String = "",
) {
    companion object {
        fun forPassage(passage: ImmersionPassage): PassageEditorState {
            val body = passage.lines.joinToString("\n") { line ->
                if (line.speaker.isEmpty()) {
                    line.text
                } else {
                    "${line.speaker}: ${line.text}"
                }
            }
            return PassageEditorState(
                editPassageId = passage.id,
                initialTitle = passage.title,
                initialBody = body,
            )
        }
    }
}

enum class NoticeUndoKind {
    CARD_REVIEW_UNDO,
    PASSAGE_DELETE,
    TRACK_DELETE,
    WORD_DISPOSITION,
}

sealed interface TrackModalState {
    data class Create(
        val pendingRootIds: List<RootId> = emptyList(),
        val allowTextMode: Boolean = false,
    ) : TrackModalState

    data class Rename(
        val trackId: TrackId,
        val currentTitle: String,
    ) : TrackModalState

    data class PickBooks(
        val rootIds: Set<RootId>,
    ) : TrackModalState

    data class TextExtractor(
        val targetTrackId: TrackId? = null,
        val defaultBookTitle: String = "",
        val initialText: String = "",
        val isProseSource: Boolean = false,
    ) : TrackModalState
}

enum class InspectorScope {
    LOOKUP,
    IN_TRACK,
    OUTSIDE_TRACK,
}

data class WordInspectorState(
    val resolution: WordResolution,
    val scope: InspectorScope,
)

data class NoticeSpec(
    val id: Long,
    val message: String,
    val undoKind: NoticeUndoKind? = null,
)

data class LexiconSection(
    val group: LexiconGroup,
    val expanded: Boolean,
    val items: List<RootOverview>,
)

data class BatchSelectionState(
    val active: Boolean = false,
    val selectedIds: Set<RootId> = emptySet(),
) {
    fun entered(initialId: RootId?): BatchSelectionState {
        val initial = if (initialId != null) setOf(initialId) else emptySet()
        return BatchSelectionState(active = true, selectedIds = initial)
    }

    fun toggled(rootId: RootId): BatchSelectionState {
        val next = if (rootId in selectedIds) {
            selectedIds - rootId
        } else {
            selectedIds + rootId
        }
        return copy(selectedIds = next)
    }

    fun updatedMany(ids: Set<RootId>, select: Boolean): BatchSelectionState {
        if (ids.isEmpty()) return this
        val next = if (select) selectedIds + ids else selectedIds - ids
        return copy(selectedIds = next)
    }

    fun toggledGroup(items: List<RootOverview>): BatchSelectionState {
        val ids = items.mapTo(LinkedHashSet(items.size)) { it.root.id }
        if (ids.isEmpty()) return this
        val allIn = selectedIds.containsAll(ids)
        val next = if (allIn) selectedIds - ids else selectedIds + ids
        return copy(selectedIds = next)
    }

    fun toggledAllExpanded(
        sections: List<LexiconSection>,
    ): BatchSelectionState {
        val visible = sections
            .filter { it.expanded && it.group.inTrack }
            .flatMap { it.items }
        return toggledGroup(visible)
    }

    fun toggledSection(
        sections: List<LexiconSection>,
        group: LexiconGroup,
    ): BatchSelectionState {
        if (!group.inTrack) return this
        val items = sections.firstOrNull { it.group == group }?.items.orEmpty()
        return toggledGroup(items)
    }
}

data class LexiconFilter(
    val searchOpen: Boolean = false,
    val query: String = "",
    val expandedGroups: Set<LexiconGroup> = LexiconGroup.DEFAULT_EXPANDED,
    val sort: LexiconSort = LexiconSort.PACK_ORDER,
) {
    fun toggledSearch(): LexiconFilter {
        val nextOpen = !searchOpen
        val nextQuery = if (nextOpen) query else ""
        return copy(searchOpen = nextOpen, query = nextQuery)
    }

    fun toggledSection(group: LexiconGroup): LexiconFilter {
        val next = if (group in expandedGroups) {
            expandedGroups - group
        } else {
            expandedGroups + group
        }
        return copy(expandedGroups = next)
    }

    fun buildSections(workspace: LanguageWorkspace?): List<LexiconSection> {
        if (workspace == null) return emptyList()
        val result = workspace.morphology.queryLexicon(
            track = workspace.activeTrack,
            query = query,
            mastery = workspace.mastery,
        )
        val sortedIn = sortItems(result.inTrack)
        val isSearching = query.isNotBlank()
        if (!isSearching && sortedIn.isEmpty()) return emptyList()
        val byGroup = sortedIn.groupBy { LexiconGroup.fromStage(it.planStage) }
        val customBook = workspace.activeTrack.isCustom
        val sections = ArrayList<LexiconSection>(5)
        for (group in LexiconGroup.IN_TRACK_ORDER) {
            val groupItems = byGroup[group].orEmpty()
            val hideEmptyIgnored = customBook && group == LexiconGroup.IGNORED
            if ((isSearching || hideEmptyIgnored) && groupItems.isEmpty()) {
                continue
            }
            val isExp = group in expandedGroups ||
                (isSearching && group != LexiconGroup.IGNORED)
            sections.add(LexiconSection(group, isExp, groupItems))
        }
        if (isSearching && result.outsideTrack.isNotEmpty()) {
            val outGrp = LexiconGroup.OUTSIDE_TRACK
            val outExp = outGrp in expandedGroups
            val sortedOut = sortItems(result.outsideTrack)
            sections.add(LexiconSection(outGrp, outExp, sortedOut))
        }
        return sections
    }

    private fun sortItems(raw: List<RootOverview>): List<RootOverview> =
        when (sort) {
            LexiconSort.PACK_ORDER -> raw
            LexiconSort.FREQUENCY -> raw.sortedBy { it.root.frequencyRank }
            LexiconSort.ALPHABETICAL -> raw.sortedBy {
                alphaKey(it.root.lemma)
            }
            LexiconSort.FAMILIARITY_ASC -> raw.sortedWith(
                compareBy<RootOverview> { it.familiarityRatio }
                    .thenBy { it.stabilityDays }
                    .thenBy { it.root.frequencyRank },
            )
            LexiconSort.FAMILIARITY_DESC -> raw.sortedWith(
                compareByDescending<RootOverview> { it.familiarityRatio }
                    .thenByDescending { it.stabilityDays }
                    .thenBy { it.root.frequencyRank },
            )
        }

    private fun alphaKey(lemma: String): String {
        val sb = StringBuilder(lemma.length)
        for (ch in lemma) {
            if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) {
                sb.append(ch.lowercaseChar())
            }
        }
        return sb.toString()
    }
}

data class PassageFilter(
    val searchOpen: Boolean = false,
    val query: String = "",
    val genre: PassageGenre = PassageGenre.ALL,
    val sort: PassageSort = PassageSort.GRADIENT,
) {
    fun toggledSearch(): PassageFilter {
        val nextOpen = !searchOpen
        val nextQuery = if (nextOpen) query else ""
        return copy(searchOpen = nextOpen, query = nextQuery)
    }

    fun apply(workspace: LanguageWorkspace?): List<PassageSearchHit> {
        if (workspace == null) return emptyList()
        val corpus = PassageCorpus(workspace.morphology, workspace.mastery)
        return corpus.filterAndSearch(
            passages = workspace.passages,
            genre = genre,
            rawQuery = query,
            sort = sort,
        )
    }
}
