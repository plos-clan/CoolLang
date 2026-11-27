package org.plos.edu.lang.views.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.plos.edu.lang.engine.PlanStage
import org.plos.edu.lang.engine.RootOverview
import org.plos.edu.lang.engine.StudyTrack
import org.plos.edu.lang.views.model.BatchSelectionState
import org.plos.edu.lang.views.model.InspectorScope
import org.plos.edu.lang.views.model.LexiconSection
import org.plos.edu.lang.views.model.MainViewModel
import org.plos.edu.lang.views.model.TrackModalState
import org.plos.edu.lang.views.widgets.AppIcon
import org.plos.edu.lang.views.widgets.CompactSearchBar
import org.plos.edu.lang.views.widgets.FamiliarityRing
import org.plos.edu.lang.views.widgets.InlineSensesText
import org.plos.edu.lang.views.widgets.SymbolIcon

private data class LexiconRowModel(
    val overview: RootOverview,
    val inTrack: Boolean,
    val batchActive: Boolean,
    val selected: Boolean,
)

private data class LexiconBodySpec(
    val sections: List<LexiconSection>,
    val batch: BatchSelectionState,
    val rail: QuickRailModel,
    val emptyCustomTrack: StudyTrack?,
) {
    fun rowModelFor(item: RootOverview, inTrack: Boolean): LexiconRowModel {
        val allowBatch = batch.active && inTrack
        return LexiconRowModel(
            overview = item,
            inTrack = inTrack,
            batchActive = allowBatch,
            selected = allowBatch && item.root.id in batch.selectedIds,
        )
    }
}

private data class SectionListRenderSpec(
    val body: LexiconBodySpec,
    val endPad: Dp,
    val dividerColor: Color,
    val dividerMod: Modifier,
)

@Composable
fun Lexicon(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val workspace = state.workspace ?: return
    val filter = state.lexiconFilter
    val batch = state.lexiconBatch
    val sections = state.lexiconSections
    BackHandler(enabled = batch.active) {
        viewModel.modifyBatch { _, _ -> BatchSelectionState() }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(workspace.activeTrack.id, filter.sort, filter.query) {
        listState.scrollToItem(0)
    }
    val expandedItems = remember(sections) {
        sections
            .filter { it.expanded && it.group.inTrack }
            .flatMap { it.items }
    }
    val allExpandedSelected = remember(expandedItems, batch.selectedIds) {
        expandedItems.isNotEmpty() &&
            expandedItems.all { it.root.id in batch.selectedIds }
    }
    val railModel = remember(sections, filter.sort) {
        QuickRailModel.build(sections, filter.sort)
    }
    val activeTrack = workspace.activeTrack
    val isBlankQuery = filter.query.isBlank()
    val emptyCustom = activeTrack.takeIf { it.isCustom && isBlankQuery }
    val bodySpec = LexiconBodySpec(sections, batch, railModel, emptyCustom)
    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = filter.searchOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            CompactSearchBar(
                query = filter.query,
                onQueryChange = { q ->
                    viewModel.modifyLexiconFilter { it.copy(query = q) }
                },
            )
        }
        if (batch.active) {
            BatchSelectionToolbar(batch, allExpandedSelected, viewModel)
        } else {
            LexiconSummaryToolbar(
                activeTrack = activeTrack,
                selectedTotal = workspace.analytics.selectedCount,
                sort = filter.sort,
                viewModel = viewModel,
            )
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        LexiconSectionViewport(
            modifier = Modifier.weight(1f),
            spec = bodySpec,
            listState = listState,
            viewModel = viewModel,
        )
        AnimatedVisibility(
            visible = batch.active,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            BatchActionDock(
                selectedIds = batch.selectedIds,
                isCustomTrack = activeTrack.isCustom,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun LexiconSectionViewport(
    modifier: Modifier,
    spec: LexiconBodySpec,
    listState: LazyListState,
    viewModel: MainViewModel,
) {
    if (spec.sections.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val custom = spec.emptyCustomTrack
            if (custom != null) {
                EmptyCustomBookPrompt(custom, viewModel)
            } else {
                Text(
                    text = "未找到匹配的词条",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    val batch = spec.batch
    val hasRail = spec.rail.isAvailable && !batch.active
    val renderSpec = SectionListRenderSpec(
        body = spec,
        endPad = if (hasRail) 24.dp else 16.dp,
        dividerColor = MaterialTheme.colorScheme.outlineVariant
            .copy(alpha = 0.45f),
        dividerMod = Modifier.padding(
            start = if (batch.active) 78.dp else 48.dp,
            end = if (hasRail) 24.dp else 0.dp,
        ),
    )
    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            for (section in spec.sections) {
                lexiconSectionEntries(section, renderSpec, viewModel)
            }
        }
        if (batch.active) {
            BatchGutterDragOverlay(listState, batch, viewModel)
        } else if (hasRail) {
            QuickJumpIndexRail(
                rail = spec.rail,
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.lexiconSectionEntries(
    section: LexiconSection,
    renderSpec: SectionListRenderSpec,
    viewModel: MainViewModel,
) {
    stickyHeader(key = "hdr_${section.group.name}") {
        SectionGroupHeader(section, renderSpec.body.batch, viewModel)
    }
    if (!section.expanded) return
    val inTrack = section.group.inTrack
    val keyFn: (RootOverview) -> Any = {
        WordRowKey.encode(section.group, it.root.id)
    }
    items(items = section.items, key = keyFn) { item ->
        val rowModel = renderSpec.body.rowModelFor(item, inTrack)
        CompactLexiconRow(rowModel, renderSpec.endPad, viewModel)
        HorizontalDivider(
            thickness = 0.5.dp,
            color = renderSpec.dividerColor,
            modifier = renderSpec.dividerMod,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactLexiconRow(
    model: LexiconRowModel,
    endPadding: Dp,
    viewModel: MainViewModel,
) {
    val haptics = LocalHapticFeedback.current
    val overview = model.overview
    val root = overview.root
    val scheme = MaterialTheme.colorScheme
    val rowBg = if (model.selected) {
        scheme.primaryContainer.copy(alpha = 0.28f)
    } else {
        Color.Transparent
    }
    val onRowTap = {
        if (model.batchActive) {
            viewModel.modifyBatch { b, _ -> b.toggled(root.id) }
        } else {
            val scope = if (model.inTrack) {
                InspectorScope.IN_TRACK
            } else {
                InspectorScope.OUTSIDE_TRACK
            }
            viewModel.inspectRoot(overview, scope)
        }
    }
    val onRowLongPress = {
        if (!model.batchActive && model.inTrack) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.modifyBatch { b, _ -> b.entered(root.id) }
        }
    }
    val rowMod = Modifier
        .fillMaxWidth()
        .heightIn(min = 46.dp)
        .combinedClickable(onClick = onRowTap, onLongClick = onRowLongPress)
        .padding(start = 16.dp, end = endPadding, top = 9.dp, bottom = 9.dp)
    val isIgnored = overview.planStage == PlanStage.IGNORED
    val lemmaColor = if (isIgnored) {
        scheme.onSurfaceVariant
    } else {
        scheme.onSurface
    }
    Surface(color = rowBg, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = rowMod,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (model.batchActive) {
                BatchCheckIndicator(model.selected)
                Spacer(modifier = Modifier.width(10.dp))
            }
            FamiliarityRing(
                stage = overview.planStage,
                ratio = overview.familiarityRatio,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = root.lemma,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = lemmaColor,
                maxLines = 1,
            )
            val firstSpoken = root.spokenForms.firstOrNull()
            if (firstSpoken != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = firstSpoken,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            InlineSensesText(
                senses = root.senses,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EmptyCustomBookPrompt(
    track: StudyTrack,
    viewModel: MainViewModel,
) {
    val onExtract = {
        val modal = TrackModalState.TextExtractor(targetTrackId = track.id)
        viewModel.customTracks.open(modal)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "「${track.title}」暂无单词",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "可粘贴外文自动提取，也可点右上角搜索全库或阅读点词加入",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = onExtract) {
            Text(text = "粘贴文本提取加词")
        }
    }
}

@Composable
private fun BatchCheckIndicator(selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val bgColor = if (selected) scheme.primary else Color.Transparent
    val border = if (selected) null else BorderStroke(1.5.dp, scheme.outline)
    Surface(
        color = bgColor,
        contentColor = scheme.onPrimary,
        shape = CircleShape,
        border = border,
        modifier = Modifier.size(20.dp),
    ) {
        if (selected) {
            Box(contentAlignment = Alignment.Center) {
                SymbolIcon(AppIcon.CHECK, Modifier.size(14.dp))
            }
        }
    }
}
