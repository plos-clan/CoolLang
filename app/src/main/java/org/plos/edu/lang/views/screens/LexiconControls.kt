package org.plos.edu.lang.views.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.plos.edu.lang.engine.LexiconSort
import org.plos.edu.lang.engine.RootId
import org.plos.edu.lang.engine.StudyTrack
import org.plos.edu.lang.engine.WordDisposition
import org.plos.edu.lang.views.model.BatchSelectionState
import org.plos.edu.lang.views.model.LexiconSection
import org.plos.edu.lang.views.model.MainViewModel
import org.plos.edu.lang.views.model.TrackModalState
import org.plos.edu.lang.views.widgets.AppIcon
import org.plos.edu.lang.views.widgets.CompactFilterPill
import org.plos.edu.lang.views.widgets.CompactSortPill
import org.plos.edu.lang.views.widgets.SymbolIcon

@Composable
fun LexiconSummaryToolbar(
    activeTrack: StudyTrack,
    selectedTotal: Int,
    sort: LexiconSort,
    viewModel: MainViewModel,
) {
    val barMod = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 6.dp)
    val onCycleSort = {
        viewModel.modifyLexiconFilter { it.copy(sort = it.sort.next()) }
    }
    val onEnterBatch = {
        viewModel.modifyBatch { b, _ -> b.entered(null) }
    }
    val onOpenExtract = {
        val modal = TrackModalState.TextExtractor(activeTrack.id)
        viewModel.customTracks.open(modal)
    }
    Row(
        modifier = barMod,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${activeTrack.title} · 已选 $selectedTotal 词",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (activeTrack.isCustom) {
                CompactFilterPill(
                    label = "加词",
                    selected = true,
                    onClick = onOpenExtract,
                )
            }
            if (activeTrack.rootIds.isNotEmpty()) {
                CompactSortPill(label = sort.label, onClick = onCycleSort)
                CompactFilterPill(
                    label = "批量",
                    selected = false,
                    onClick = onEnterBatch,
                )
            }
        }
    }
}

@Composable
fun BatchSelectionToolbar(
    batch: BatchSelectionState,
    allExpandedSelected: Boolean,
    viewModel: MainViewModel,
) {
    val barMod = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 12.dp, top = 2.dp, bottom = 2.dp)
    val label = if (allExpandedSelected) "取消全选" else "全选展开"
    val btnPad = PaddingValues(horizontal = 8.dp)
    val onToggleAll = {
        viewModel.modifyBatch { b, secs -> b.toggledAllExpanded(secs) }
    }
    val onDone = {
        viewModel.modifyBatch { _, _ -> BatchSelectionState() }
    }
    Row(
        modifier = barMod,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "已勾选 ${batch.selectedIds.size} 词（可上下滑选）",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onToggleAll, contentPadding = btnPad) {
                Text(text = label, style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onDone, contentPadding = btnPad) {
                Text(
                    text = "完成",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun SectionGroupHeader(
    section: LexiconSection,
    batch: BatchSelectionState,
    viewModel: MainViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    val chevron = if (section.expanded) AppIcon.EXPAND else AppIcon.COLLAPSED
    val allGroupChosen = section.items.isNotEmpty() &&
        section.items.all { it.root.id in batch.selectedIds }
    val rowMod = Modifier
        .fillMaxWidth()
        .background(scheme.surfaceContainerLow)
        .clickable {
            viewModel.modifyLexiconFilter { it.toggledSection(section.group) }
        }
        .padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp)
    val showGroupSelect = batch.active &&
        section.group.inTrack &&
        section.expanded &&
        section.items.isNotEmpty()
    val onToggleGroup = {
        viewModel.modifyBatch { b, secs ->
            b.toggledSection(secs, section.group)
        }
    }
    Column {
        Row(
            modifier = rowMod,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SymbolIcon(
                    icon = chevron,
                    modifier = Modifier.size(20.dp),
                    tint = scheme.onSurfaceVariant,
                )
                Text(
                    text = section.group.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${section.items.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (showGroupSelect) {
                val grpLabel = if (allGroupChosen) "取消本组" else "全选本组"
                val grpMod = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onToggleGroup)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                Text(
                    text = grpLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = grpMod,
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = scheme.outlineVariant)
    }
}

@Composable
fun BatchActionDock(
    selectedIds: Set<RootId>,
    isCustomTrack: Boolean,
    viewModel: MainViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    val hasSelection = selectedIds.isNotEmpty()
    val rowMod = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp)
    Surface(
        color = scheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isCustomTrack) {
            CustomBatchButtonsRow(rowMod, hasSelection, selectedIds, viewModel)
        } else {
            OfficialBatchButtonsRow(rowMod, hasSelection, viewModel)
        }
    }
}

@Composable
private fun CustomBatchButtonsRow(
    modifier: Modifier,
    hasSelection: Boolean,
    selectedIds: Set<RootId>,
    viewModel: MainViewModel,
) {
    val btnPad = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            enabled = hasSelection,
            onClick = {
                viewModel.applyBatchDisposition(WordDisposition.MASTERED)
            },
            modifier = Modifier.weight(1f),
            contentPadding = btnPad,
        ) {
            Text(text = "标为熟知", maxLines = 1, softWrap = false)
        }
        FilledTonalButton(
            enabled = hasSelection,
            onClick = {
                viewModel.customTracks.removeFromActiveTrack(selectedIds)
            },
            modifier = Modifier.weight(1f),
            contentPadding = btnPad,
        ) {
            Text(text = "移出词书", maxLines = 1, softWrap = false)
        }
        OutlinedButton(
            enabled = hasSelection,
            onClick = {
                viewModel.applyBatchDisposition(WordDisposition.AUTO)
            },
            modifier = Modifier.weight(1f),
            contentPadding = btnPad,
        ) {
            Text(text = "恢复待学", maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun OfficialBatchButtonsRow(
    modifier: Modifier,
    hasSelection: Boolean,
    viewModel: MainViewModel,
) {
    val btnPad = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Button(
            enabled = hasSelection,
            onClick = {
                viewModel.applyBatchDisposition(WordDisposition.MASTERED)
            },
            modifier = Modifier.weight(1f),
            contentPadding = btnPad,
        ) {
            Text(text = "标熟", maxLines = 1, softWrap = false)
        }
        FilledTonalButton(
            enabled = hasSelection,
            onClick = {
                viewModel.applyBatchDisposition(WordDisposition.IGNORED)
            },
            modifier = Modifier.weight(1f),
            contentPadding = btnPad,
        ) {
            Text(text = "不想背", maxLines = 1, softWrap = false)
        }
        FilledTonalButton(
            enabled = hasSelection,
            onClick = viewModel.customTracks::addBatchToCustomBook,
            modifier = Modifier.weight(1f),
            contentPadding = btnPad,
        ) {
            Text(text = "加词书", maxLines = 1, softWrap = false)
        }
        OutlinedButton(
            enabled = hasSelection,
            onClick = {
                viewModel.applyBatchDisposition(WordDisposition.AUTO)
            },
            modifier = Modifier.weight(1f),
            contentPadding = btnPad,
        ) {
            Text(text = "恢复", maxLines = 1, softWrap = false)
        }
    }
}
