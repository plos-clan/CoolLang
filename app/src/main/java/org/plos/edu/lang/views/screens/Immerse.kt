package org.plos.edu.lang.views.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.plos.edu.lang.engine.CustomPassageDraft
import org.plos.edu.lang.engine.ImmersionPassage
import org.plos.edu.lang.engine.PassageGenre
import org.plos.edu.lang.engine.PassageId
import org.plos.edu.lang.engine.PassageSearchHit
import org.plos.edu.lang.engine.PassageSort
import org.plos.edu.lang.repository.LanguageWorkspace
import org.plos.edu.lang.views.model.ActiveSheet
import org.plos.edu.lang.views.model.MainViewModel
import org.plos.edu.lang.views.model.PassageEditorState
import org.plos.edu.lang.views.widgets.AppAlertDialog
import org.plos.edu.lang.views.widgets.AppAlertSpec
import org.plos.edu.lang.views.widgets.CompactFilterPill
import org.plos.edu.lang.views.widgets.CompactSearchBar
import org.plos.edu.lang.views.widgets.CompactSortPill
import org.plos.edu.lang.views.widgets.DialogAction

private data class PassageListSpec(
    val hits: List<PassageSearchHit>,
    val listState: LazyListState,
)

@Composable
fun Immerse(
    viewModel: MainViewModel,
    onOpenPassage: (PassageId, Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val workspace = state.workspace ?: return
    val filter = state.passageFilter
    val listState = rememberLazyListState()
    LaunchedEffect(filter.genre, filter.sort, filter.query) {
        listState.scrollToItem(0)
    }
    val scrollTarget = state.scrollTargetPassageId
    LaunchedEffect(scrollTarget, state.visiblePassages) {
        if (scrollTarget == null) return@LaunchedEffect
        val idx = state.visiblePassages.indexOfFirst {
            it.passage.id == scrollTarget
        }
        if (idx >= 0) {
            listState.animateScrollToItem(idx)
            viewModel.clearPassageScrollTarget()
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = filter.searchOpen) {
            CompactSearchBar(
                query = filter.query,
                placeholder = "搜索文章标题原句译文或变形词",
                onQueryChange = { q ->
                    viewModel.modifyPassageFilter { it.copy(query = q) }
                },
            )
        }
        PassageControlBar(
            activeGenre = filter.genre,
            sort = filter.sort,
            allPassages = workspace.passages,
            viewModel = viewModel,
        )
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        val listSpec = PassageListSpec(state.visiblePassages, listState)
        PassageListViewport(
            modifier = Modifier.weight(1f),
            spec = listSpec,
            viewModel = viewModel,
            onOpenPassage = onOpenPassage,
        )
    }
}

@Composable
private fun PassageListViewport(
    modifier: Modifier,
    spec: PassageListSpec,
    viewModel: MainViewModel,
    onOpenPassage: (PassageId, Int) -> Unit,
) {
    val hits = spec.hits
    if (hits.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "未找到匹配的阅读篇章",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val listPad = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 10.dp,
        bottom = 20.dp,
    )
    LazyColumn(
        state = spec.listState,
        modifier = modifier,
        contentPadding = listPad,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items = hits, key = { it.passage.id.value }) { hit ->
            PassageSummaryCard(
                hit = hit,
                viewModel = viewModel,
                onOpenPassage = onOpenPassage,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun PassageControlBar(
    activeGenre: PassageGenre,
    sort: PassageSort,
    allPassages: List<ImmersionPassage>,
    viewModel: MainViewModel,
) {
    val barMod = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 6.dp)
    val onCycleSort = {
        viewModel.modifyPassageFilter { it.copy(sort = it.sort.next()) }
    }
    Row(
        modifier = barMod,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (genre in PassageGenre.entries) {
                val count = allPassages.count { genre.matches(it.isDialogue) }
                val onPick = {
                    viewModel.modifyPassageFilter { it.copy(genre = genre) }
                }
                CompactFilterPill(
                    label = "${genre.label} $count",
                    selected = genre == activeGenre,
                    onClick = onPick,
                )
            }
        }
        CompactSortPill(label = sort.label, onClick = onCycleSort)
    }
}

@Composable
private fun PassageSummaryCard(
    hit: PassageSearchHit,
    viewModel: MainViewModel,
    onOpenPassage: (PassageId, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val passage = hit.passage
    val targetLine = hit.matchedLineIndex ?: -1
    val cardMod = modifier
        .fillMaxWidth()
        .clickable { onOpenPassage(passage.id, targetLine) }
    val innerMod = Modifier
        .fillMaxWidth()
        .padding(14.dp)
    val scheme = MaterialTheme.colorScheme
    val snippet = hit.matchedSnippet
    OutlinedCard(modifier = cardMod) {
        Column(
            modifier = innerMod,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PassageBadgesRow(passage, hit.matchedLineIndex)
            Text(
                text = passage.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = passage.sceneSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = if (snippet != null) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (snippet != null) {
                MatchedSnippetBox(snippet)
            }
            if (passage.isCustom) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = scheme.outlineVariant.copy(alpha = 0.55f),
                )
                CustomPassageActionsRow(passage, viewModel)
            }
        }
    }
}

@Composable
private fun CustomPassageActionsRow(
    passage: ImmersionPassage,
    viewModel: MainViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    val btnPad = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    val btnMod = Modifier.height(28.dp)
    val onExtract = { viewModel.customTracks.openForPassage(passage) }
    val onEdit = {
        val st = PassageEditorState.forPassage(passage)
        viewModel.showSheet(ActiveSheet.PassageEditor(st))
    }
    val onDelete = { viewModel.deleteCustomPassage(passage) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onExtract,
            contentPadding = btnPad,
            modifier = btnMod,
        ) {
            Text(text = "提取生词", style = MaterialTheme.typography.labelMedium)
        }
        TextButton(
            onClick = onEdit,
            contentPadding = btnPad,
            modifier = btnMod,
        ) {
            Text(text = "编辑", style = MaterialTheme.typography.labelMedium)
        }
        TextButton(
            onClick = onDelete,
            contentPadding = btnPad,
            modifier = btnMod,
        ) {
            Text(
                text = "删除",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.error,
            )
        }
    }
}

@Composable
private fun PassageBadgesRow(
    passage: ImmersionPassage,
    matchedLineIndex: Int?,
) {
    val scheme = MaterialTheme.colorScheme
    val statusText = passage.bookmark.statusLabel(passage.lines.size)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniBadgeChip(
            text = passage.levelTag,
            bgColor = scheme.primaryContainer,
            fgColor = scheme.onPrimaryContainer,
        )
        MiniBadgeChip(
            text = "已学覆盖 ${passage.knownCoveragePercent}%",
            bgColor = scheme.secondaryContainer,
            fgColor = scheme.onSecondaryContainer,
        )
        if (matchedLineIndex != null) {
            MiniBadgeChip(
                text = "第 ${matchedLineIndex + 1} 句命中",
                bgColor = scheme.tertiaryContainer,
                fgColor = scheme.onTertiaryContainer,
            )
        } else if (statusText != null) {
            val done = passage.bookmark.completed
            val bg = if (done) {
                scheme.tertiaryContainer
            } else {
                scheme.surfaceContainerHighest
            }
            val fg = if (done) {
                scheme.onTertiaryContainer
            } else {
                scheme.onSurfaceVariant
            }
            MiniBadgeChip(text = statusText, bgColor = bg, fgColor = fg)
        }
    }
}

@Composable
private fun MiniBadgeChip(
    text: String,
    bgColor: Color,
    fgColor: Color,
) {
    val pad = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
    Surface(
        color = bgColor,
        contentColor = fgColor,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = text,
            modifier = pad,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun MatchedSnippetBox(snippet: String) {
    val scheme = MaterialTheme.colorScheme
    val snipPad = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 6.dp)
    Surface(
        color = scheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = snippet,
            modifier = snipPad,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PassageEditorDialog(
    editor: PassageEditorState,
    workspace: LanguageWorkspace,
    viewModel: MainViewModel,
) {
    val editKey = editor.editPassageId?.value ?: "__create__"
    var title by rememberSaveable(editKey) {
        mutableStateOf(editor.initialTitle)
    }
    var body by rememberSaveable(editKey) {
        mutableStateOf(editor.initialBody)
    }
    val isEdit = editor.editPassageId != null
    val dismiss = DialogAction(
        label = "取消",
        onClick = { viewModel.showSheet(null) },
    )
    val confirm = DialogAction(
        label = if (isEdit) "保存" else "导入",
        enabled = body.isNotBlank(),
        onClick = {
            val draft = CustomPassageDraft(
                langCode = workspace.activeProfile.code,
                title = title,
                rawBody = body,
                editId = editor.editPassageId,
            )
            viewModel.submitCustomPassage(draft)
        },
    )
    val heading = if (isEdit) "编辑阅读文本" else "导入阅读文本"
    val alertSpec = AppAlertSpec(heading, dismiss, confirm)
    AppAlertDialog(alertSpec) {
        ImportFieldsColumn(
            title = title,
            body = body,
            onTitleChange = { title = it },
            onBodyChange = { body = it },
        )
    }
}

@Composable
private fun ImportFieldsColumn(
    title: String,
    body: String,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
) {
    val bodyMod = Modifier
        .fillMaxWidth()
        .height(156.dp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text(text = "标题（可选）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = body,
            onValueChange = onBodyChange,
            label = { Text(text = "正文内容") },
            placeholder = { Text(text = "粘贴文章或对话，自动按句切分") },
            modifier = bodyMod,
        )
    }
}
