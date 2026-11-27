package org.plos.edu.lang.views.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.plos.edu.lang.engine.RootId
import org.plos.edu.lang.engine.StudyTrack
import org.plos.edu.lang.engine.TrackId
import org.plos.edu.lang.repository.LanguageWorkspace
import org.plos.edu.lang.views.model.TrackModalActions
import org.plos.edu.lang.views.model.TrackModalState

private data class NameDialogSpec(
    val dialogTitle: String,
    val initialText: String,
    val confirmLabel: String,
    val renameId: TrackId?,
)

@Composable
fun CustomTrackModalHost(
    modal: TrackModalState,
    workspace: LanguageWorkspace,
    actions: TrackModalActions,
) {
    when (modal) {
        is TrackModalState.Create -> {
            val heading = if (modal.pendingRootIds.isEmpty()) {
                "新建自建词书"
            } else {
                "新建词书并加入 (${modal.pendingRootIds.size} 词)"
            }
            val spec = NameDialogSpec(heading, "", "创建", null)
            TrackNameDialog(
                spec = spec,
                pendingRootIds = modal.pendingRootIds,
                allowTextMode = modal.allowTextMode,
                actions = actions,
            )
        }
        is TrackModalState.Rename -> {
            val spec = NameDialogSpec(
                dialogTitle = "重命名词书",
                initialText = modal.currentTitle,
                confirmLabel = "保存",
                renameId = modal.trackId,
            )
            TrackNameDialog(
                spec = spec,
                pendingRootIds = emptyList(),
                allowTextMode = false,
                actions = actions,
            )
        }
        is TrackModalState.PickBooks -> {
            PickCustomTracksDialog(modal.rootIds, workspace, actions)
        }
        is TrackModalState.TextExtractor -> {
            TextExtractorSheet(modal, workspace, actions)
        }
    }
}

@Composable
private fun TrackNameDialog(
    spec: NameDialogSpec,
    pendingRootIds: List<RootId>,
    allowTextMode: Boolean,
    actions: TrackModalActions,
) {
    var title by rememberSaveable(spec.renameId?.value) {
        mutableStateOf(spec.initialText)
    }
    val dismiss = DialogAction("取消") { actions.open(null) }
    val confirm = DialogAction(
        label = spec.confirmLabel,
        enabled = title.isNotBlank(),
        onClick = {
            actions.submitCreateOrRename(title, spec.renameId, pendingRootIds)
        },
    )
    val alertSpec = AppAlertSpec(spec.dialogTitle, dismiss, confirm)
    val onOpenTextMode = {
        val extractor = TrackModalState.TextExtractor(
            targetTrackId = null,
            defaultBookTitle = title.trim(),
        )
        actions.open(extractor)
    }
    AppAlertDialog(alertSpec) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(text = "词书名称") },
                placeholder = { Text(text = "例如：外刊生词、期末重点") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (allowTextMode) {
                FilledTonalButton(
                    onClick = onOpenTextMode,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "从粘贴文本提取单词建书")
                }
            }
        }
    }
}

@Composable
private fun PickCustomTracksDialog(
    rootIds: Set<RootId>,
    workspace: LanguageWorkspace,
    actions: TrackModalActions,
) {
    val books = workspace.customTracks
    val initialChecked = remember(rootIds, books) {
        if (rootIds.size == 1) {
            val single = rootIds.first()
            books.filter { single in it.rootIds }.mapTo(HashSet()) { it.id }
        } else {
            emptySet()
        }
    }
    var chosenIds by remember(initialChecked) { mutableStateOf(initialChecked) }
    val canConfirm = rootIds.size == 1 || chosenIds.isNotEmpty()
    val dismiss = DialogAction("取消") { actions.open(null) }
    val confirm = DialogAction(
        label = "确定",
        enabled = canConfirm,
        onClick = { actions.commitPickedBooks(rootIds, chosenIds) },
    )
    val heading = if (rootIds.size == 1) {
        "选择收入的词书"
    } else {
        "将 ${rootIds.size} 词加入词书"
    }
    val onToggleBook: (TrackId) -> Unit = { tid ->
        chosenIds = if (tid in chosenIds) chosenIds - tid else chosenIds + tid
    }
    val onCreateNew = {
        actions.open(TrackModalState.Create(rootIds.toList()))
    }
    AppAlertDialog(AppAlertSpec(heading, dismiss, confirm)) {
        PickBooksListContent(books, chosenIds, onToggleBook, onCreateNew)
    }
}

@Composable
private fun PickBooksListContent(
    books: List<StudyTrack>,
    chosenIds: Set<TrackId>,
    onToggle: (TrackId) -> Unit,
    onCreateNew: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scrollMod = Modifier
        .fillMaxWidth()
        .heightIn(max = 260.dp)
        .verticalScroll(rememberScrollState())
    val newRowMod = Modifier
        .fillMaxWidth()
        .clickable(onClick = onCreateNew)
        .padding(vertical = 10.dp)
    Column(modifier = scrollMod) {
        for (book in books) {
            BookCheckRow(
                book = book,
                checked = book.id in chosenIds,
                onToggle = { onToggle(book.id) },
            )
        }
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
        Row(
            modifier = newRowMod,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SymbolIcon(
                icon = AppIcon.ADD,
                modifier = Modifier.size(20.dp),
                tint = scheme.primary,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "新建词书并加入",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun BookCheckRow(
    book: StudyTrack,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val rowMod = Modifier
        .fillMaxWidth()
        .clickable(onClick = onToggle)
        .padding(vertical = 6.dp)
    Row(
        modifier = rowMod,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${book.rootIds.size} 词",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
