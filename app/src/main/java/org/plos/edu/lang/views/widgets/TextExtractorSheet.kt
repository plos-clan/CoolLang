package org.plos.edu.lang.views.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.plos.edu.lang.engine.ExtractCommitSpec
import org.plos.edu.lang.engine.PlanStage
import org.plos.edu.lang.engine.RootId
import org.plos.edu.lang.engine.RootOverview
import org.plos.edu.lang.engine.StudyTrack
import org.plos.edu.lang.engine.TrackId
import org.plos.edu.lang.repository.LanguageWorkspace
import org.plos.edu.lang.views.model.TrackModalActions
import org.plos.edu.lang.views.model.TrackModalState

private const val BASIC_RANK_CUTOFF: Int = 600

private enum class ExtractorStep { INPUT, PREVIEW }

@Stable
private class ExtractorDraft(
    modal: TrackModalState.TextExtractor,
    private val workspace: LanguageWorkspace,
) {
    val lockedTarget: Boolean = modal.targetTrackId != null
    val allowEditSource: Boolean = !modal.isProseSource
    var step by mutableStateOf(
        if (modal.initialText.isBlank()) ExtractorStep.INPUT
        else ExtractorStep.PREVIEW,
    )
    var rawText by mutableStateOf(modal.initialText)
    var chosenTrackId by mutableStateOf(resolveInitialTarget(modal, workspace))
        private set
    var newBookTitle by mutableStateOf(modal.defaultBookTitle)
    var excludeMastered by mutableStateOf(true)
        private set
    var excludeBasic by mutableStateOf(modal.isProseSource)
        private set
    var manualOn by mutableStateOf(emptySet<RootId>())
        private set
    var manualOff by mutableStateOf(emptySet<RootId>())
        private set
    var extracted by mutableStateOf(initialExtract(modal, workspace))
        private set

    val targetExistingIds: Set<RootId>
        get() = workspace.customTracks
            .firstOrNull { it.id == chosenTrackId }
            ?.rootIds?.toHashSet().orEmpty()

    val selectedIds: List<RootId>
        get() = extracted.filter(::isSelected).map { it.root.id }

    fun runExtraction() {
        val list = workspace.morphology.extractRootsFromText(
            rawText = rawText,
            mastery = workspace.mastery,
        )
        extracted = list
        val looksLikeProse = list.size >= 12 &&
            rawText.any { it == '.' || it == '?' || it == '!' || it == '。' }
        excludeBasic = looksLikeProse
        manualOn = emptySet()
        manualOff = emptySet()
        step = ExtractorStep.PREVIEW
    }

    fun selectDestination(trackId: TrackId?) {
        chosenTrackId = trackId
        manualOn = emptySet()
        manualOff = emptySet()
    }

    fun toggleFilter(basic: Boolean) {
        if (basic) excludeBasic = !excludeBasic
        else excludeMastered = !excludeMastered
        manualOn = emptySet()
        manualOff = emptySet()
    }

    fun isSelected(item: RootOverview): Boolean {
        val rid = item.root.id
        if (rid in manualOn) return true
        if (rid in manualOff) return false
        if (rid in targetExistingIds) return false
        if (excludeMastered && item.planStage == PlanStage.MASTERED) {
            return false
        }
        if (excludeBasic && item.root.frequencyRank <= BASIC_RANK_CUTOFF) {
            return false
        }
        return true
    }

    fun toggleWord(rootId: RootId, currentlyOn: Boolean) {
        if (currentlyOn) {
            manualOn = manualOn - rootId
            manualOff = manualOff + rootId
        } else {
            manualOff = manualOff - rootId
            manualOn = manualOn + rootId
        }
    }

    fun toggleAll() {
        val allActive = extracted.isNotEmpty() && extracted.all(::isSelected)
        if (allActive) {
            manualOn = emptySet()
            manualOff = extracted.mapTo(HashSet()) { it.root.id }
        } else {
            val existing = targetExistingIds
            manualOff = emptySet()
            manualOn = extracted
                .map { it.root.id }
                .filterNot { it in existing }
                .toHashSet()
        }
    }

    fun toCommitSpec(): ExtractCommitSpec = ExtractCommitSpec(
        targetTrackId = chosenTrackId,
        newTitle = newBookTitle,
        rootIds = selectedIds,
    )

    companion object {
        private fun resolveInitialTarget(
            modal: TrackModalState.TextExtractor,
            workspace: LanguageWorkspace,
        ): TrackId? {
            if (modal.targetTrackId != null) return modal.targetTrackId
            if (modal.defaultBookTitle.isNotEmpty()) return null
            return workspace.customTracks.firstOrNull()?.id
        }

        private fun initialExtract(
            modal: TrackModalState.TextExtractor,
            workspace: LanguageWorkspace,
        ): List<RootOverview> {
            val text = modal.initialText
            if (text.isBlank()) return emptyList()
            return workspace.morphology.extractRootsFromText(
                rawText = text,
                mastery = workspace.mastery,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextExtractorSheet(
    modal: TrackModalState.TextExtractor,
    workspace: LanguageWorkspace,
    actions: TrackModalActions,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val draft = remember(modal, workspace.activeProfile.code) {
        ExtractorDraft(modal, workspace)
    }
    val outerMod = Modifier
        .fillMaxWidth()
        .imePadding()
        .padding(start = 18.dp, end = 18.dp, bottom = 24.dp)
    ModalBottomSheet(
        onDismissRequest = { actions.open(null) },
        sheetState = sheetState,
    ) {
        Column(modifier = outerMod) {
            if (draft.step == ExtractorStep.INPUT) {
                ExtractorInputPane(draft, workspace)
            } else {
                ExtractorPreviewPane(draft, workspace, actions)
            }
        }
    }
}

@Composable
private fun ExtractorInputPane(
    draft: ExtractorDraft,
    workspace: LanguageWorkspace,
) {
    val targetBook = workspace.customTracks.firstOrNull {
        it.id == draft.chosenTrackId
    }
    val titleText = if (targetBook != null) {
        "提取加词 · ${targetBook.title}"
    } else {
        "从文本提取创建词书"
    }
    val fieldMod = Modifier
        .fillMaxWidth()
        .height(170.dp)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = titleText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (!draft.lockedTarget && draft.chosenTrackId == null) {
            OutlinedTextField(
                value = draft.newBookTitle,
                onValueChange = { draft.newBookTitle = it },
                label = { Text(text = "词书名称") },
                placeholder = { Text(text = "例如：外刊精读生词") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = draft.rawText,
            onValueChange = { draft.rawText = it },
            label = { Text(text = "粘贴外文段落或单词列表") },
            placeholder = { Text(text = "自动识别已收录词汇并还原变形词根") },
            modifier = fieldMod,
        )
        Button(
            onClick = draft::runExtraction,
            enabled = draft.rawText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "解析并预览词条")
        }
    }
}

@Composable
private fun ExtractorPreviewPane(
    draft: ExtractorDraft,
    workspace: LanguageWorkspace,
    actions: TrackModalActions,
) {
    val selectedCount = draft.selectedIds.size
    val canCommit = selectedCount > 0 &&
        (draft.chosenTrackId != null || draft.newBookTitle.isNotBlank())
    val confirmLabel = if (draft.chosenTrackId == null) {
        "创建词书并添加 ($selectedCount 词)"
    } else {
        "确认添加 ($selectedCount 词)"
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PreviewTopHeader(draft)
        if (!draft.lockedTarget) {
            DestinationSelectorBlock(draft, workspace.customTracks)
        }
        FilterPillsBar(draft)
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        ExtractedWordsViewport(draft)
        Button(
            onClick = { actions.commitExtraction(draft.toCommitSpec()) },
            enabled = canCommit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = confirmLabel)
        }
    }
}

@Composable
private fun PreviewTopHeader(draft: ExtractorDraft) {
    val total = draft.extracted.size
    val selected = draft.selectedIds.size
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "词条提取预览",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "识别到 $total 个词根 · 已勾选 $selected 个",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (draft.allowEditSource) {
                TextButton(onClick = { draft.step = ExtractorStep.INPUT }) {
                    Text(text = "重填文本")
                }
            }
            if (total > 0) {
                TextButton(onClick = draft::toggleAll) {
                    val allOn = selected == total
                    Text(text = if (allOn) "清空" else "全选")
                }
            }
        }
    }
}

@Composable
private fun DestinationSelectorBlock(
    draft: ExtractorDraft,
    customBooks: List<StudyTrack>,
) {
    if (customBooks.isNotEmpty()) {
        val scrollMod = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
        Row(
            modifier = scrollMod,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactFilterPill(
                label = "+ 新建词书",
                selected = draft.chosenTrackId == null,
                onClick = { draft.selectDestination(null) },
            )
            for (book in customBooks) {
                val chosen = draft.chosenTrackId == book.id
                CompactFilterPill(book.title, chosen) {
                    draft.selectDestination(book.id)
                }
            }
        }
    }
    if (draft.chosenTrackId == null) {
        OutlinedTextField(
            value = draft.newBookTitle,
            onValueChange = { draft.newBookTitle = it },
            label = { Text(text = "新词书名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FilterPillsBar(draft: ExtractorDraft) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CompactFilterPill(
            label = "排除已熟知",
            selected = draft.excludeMastered,
            onClick = { draft.toggleFilter(basic = false) },
        )
        CompactFilterPill(
            label = "排除高频基础词",
            selected = draft.excludeBasic,
            onClick = { draft.toggleFilter(basic = true) },
        )
    }
}

@Composable
private fun ExtractedWordsViewport(draft: ExtractorDraft) {
    val items = draft.extracted
    if (items.isEmpty()) {
        val emptyMod = Modifier.fillMaxWidth().height(140.dp)
        Box(modifier = emptyMod, contentAlignment = Alignment.Center) {
            Text(
                text = "未在文本中识别出词库已收录词汇",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val existing = draft.targetExistingIds
    val listMod = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 300.dp)
    LazyColumn(modifier = listMod) {
        items(items = items, key = { it.root.id.value }) { item ->
            val rid = item.root.id
            val checked = draft.isSelected(item)
            val alreadyIn = rid in existing
            ExtractedWordRow(item, checked, alreadyIn) {
                draft.toggleWord(rid, checked)
            }
        }
    }
}

@Composable
private fun ExtractedWordRow(
    item: RootOverview,
    checked: Boolean,
    alreadyInBook: Boolean,
    onToggle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val rowMod = Modifier
        .fillMaxWidth()
        .clickable(enabled = !alreadyInBook, onClick = onToggle)
        .padding(vertical = 8.dp)
    val wordColor = if (checked) scheme.onSurface else scheme.onSurfaceVariant
    Row(
        modifier = rowMod,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundCheckDot(checked = checked, disabled = alreadyInBook)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = item.root.lemma,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = wordColor,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(10.dp))
        InlineSensesText(
            senses = item.root.senses,
            modifier = Modifier.weight(1f),
        )
        val statusTag = when {
            alreadyInBook -> "已在书内" to scheme.primary
            item.planStage == PlanStage.MASTERED -> "已熟知" to scheme.outline
            else -> null
        }
        if (statusTag != null) {
            Text(
                text = statusTag.first,
                style = MaterialTheme.typography.labelSmall,
                color = statusTag.second,
            )
        }
    }
}

@Composable
private fun RoundCheckDot(checked: Boolean, disabled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val bg = when {
        disabled -> scheme.surfaceContainerHigh
        checked -> scheme.primary
        else -> Color.Transparent
    }
    val border = if (checked || disabled) null else {
        BorderStroke(1.5.dp, scheme.outline)
    }
    Surface(
        color = bg,
        contentColor = scheme.onPrimary,
        shape = CircleShape,
        border = border,
        modifier = Modifier.size(20.dp),
    ) {
        if (checked) {
            Box(contentAlignment = Alignment.Center) {
                SymbolIcon(AppIcon.CHECK, Modifier.size(14.dp))
            }
        }
    }
}
