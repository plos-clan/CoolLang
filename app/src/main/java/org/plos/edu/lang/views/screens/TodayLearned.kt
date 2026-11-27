package org.plos.edu.lang.views.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.plos.edu.lang.engine.LearnedTodayItem
import org.plos.edu.lang.engine.MasterySnapshot
import org.plos.edu.lang.engine.RootOverview
import org.plos.edu.lang.views.model.MainViewModel
import org.plos.edu.lang.views.widgets.AppIcon
import org.plos.edu.lang.views.widgets.AudioPlayButton
import org.plos.edu.lang.views.widgets.COMPACT_TOP_BAR_HEIGHT
import org.plos.edu.lang.views.widgets.InlineSensesText
import org.plos.edu.lang.views.widgets.MasteryPill
import org.plos.edu.lang.views.widgets.ObserveNoticeSnackbar
import org.plos.edu.lang.views.widgets.SpeechCallbacks
import org.plos.edu.lang.views.widgets.SpokenVariantsRow
import org.plos.edu.lang.views.widgets.SymbolIcon
import org.plos.edu.lang.views.widgets.TopBarTitle

private data class TodayBodySpec(
    val items: List<LearnedTodayItem>,
    val mastery: MasterySnapshot,
    val speech: SpeechCallbacks,
)

@Composable
fun TodayLearned(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val workspace = state.workspace ?: return
    DisposableEffect(Unit) {
        onDispose { viewModel.speech.stop() }
    }
    val speech = remember(viewModel) {
        SpeechCallbacks(
            onNormal = viewModel.speech::speakNormal,
            onSlow = viewModel.speech::speakSlow,
        )
    }
    val bodySpec = TodayBodySpec(
        items = workspace.learnedToday,
        mastery = workspace.mastery,
        speech = speech,
    )
    val snackbarHost = remember { SnackbarHostState() }
    ObserveNoticeSnackbar(state.activeNotice, snackbarHost, viewModel)
    Scaffold(
        topBar = { TodayTopBar(onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { innerPadding ->
        val outerMod = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp)
        TodayBodyPane(outerMod, bodySpec) { viewModel.inspectRoot(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayTopBar(onBack: () -> Unit) {
    TopAppBar(
        expandedHeight = COMPACT_TOP_BAR_HEIGHT,
        title = { TopBarTitle("今日所学") },
        navigationIcon = {
            IconButton(onClick = onBack) { SymbolIcon(AppIcon.BACK) }
        },
    )
}

@Composable
private fun TodayBodyPane(
    modifier: Modifier,
    spec: TodayBodySpec,
    onInspect: (RootOverview) -> Unit,
) {
    val items = spec.items
    if (items.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "今日暂未开始学习词条",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val newCount = items.count { it.isNewlyIntroduced }
    val reviewCount = items.size - newCount
    val summary = "今日共学习 ${items.size} 个词条，" +
        "其中新学 $newCount 个，复习巩固 $reviewCount 个"
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items = items, key = { it.unit.id.value }) { item ->
                LearnedItemCard(item, spec.mastery, spec.speech, onInspect)
            }
        }
    }
}

@Composable
private fun LearnedItemCard(
    item: LearnedTodayItem,
    mastery: MasterySnapshot,
    speech: SpeechCallbacks,
    onInspect: (RootOverview) -> Unit,
) {
    val unit = item.unit
    val rootId = item.root.id
    val overview = RootOverview(
        root = item.root,
        members = item.familyMembers,
        stage = item.stage,
        disposition = mastery.dispositionOf(rootId),
        planStage = mastery.planStageOf(rootId),
        stabilityDays = item.stabilityDays,
        familiarityRatio = item.familiarityRatio,
    )
    val cardMod = Modifier
        .fillMaxWidth()
        .clickable { onInspect(overview) }
    val innerMod = Modifier
        .fillMaxWidth()
        .padding(14.dp)
    val firstEx = unit.examples.firstOrNull()?.text.orEmpty()
    OutlinedCard(modifier = cardMod) {
        Column(
            modifier = innerMod,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LearnedItemHeader(item, speech)
            SpokenVariantsRow(unit.variantForms, unit.styleBadge)
            val senseStyle = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
            )
            InlineSensesText(
                senses = unit.senses,
                textStyle = senseStyle,
                textColor = MaterialTheme.colorScheme.onSurface,
            )
            if (firstEx.isNotEmpty()) {
                Text(
                    text = firstEx,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LearnedItemHeader(
    item: LearnedTodayItem,
    speech: SpeechCallbacks,
) {
    val headword = item.unit.headword
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = headword,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            StudyKindTag(item.isNewlyIntroduced)
            MasteryPill(item.stage)
        }
        AudioPlayButton(text = headword, callbacks = speech)
    }
}

@Composable
private fun StudyKindTag(isNew: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (isNew) scheme.primaryContainer else scheme.surfaceVariant
    val fg = if (isNew) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    val label = if (isNew) "新学" else "复习"
    val pad = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
    Surface(
        color = bg,
        contentColor = fg,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = label,
            modifier = pad,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
