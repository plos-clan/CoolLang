package org.plos.edu.lang.views.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.plos.edu.lang.engine.GrammarRule
import org.plos.edu.lang.engine.PassageLine
import org.plos.edu.lang.engine.ReadingBookmark
import org.plos.edu.lang.repository.LanguageWorkspace
import org.plos.edu.lang.views.model.ActiveSheet
import org.plos.edu.lang.views.model.MainViewModel
import org.plos.edu.lang.views.widgets.AppIcon
import org.plos.edu.lang.views.widgets.AudioPlayButton
import org.plos.edu.lang.views.widgets.COMPACT_TOP_BAR_HEIGHT
import org.plos.edu.lang.views.widgets.ContrastBox
import org.plos.edu.lang.views.widgets.HighlightPolicy
import org.plos.edu.lang.views.widgets.ObserveNoticeSnackbar
import org.plos.edu.lang.views.widgets.SpeechCallbacks
import org.plos.edu.lang.views.widgets.SymbolIcon
import org.plos.edu.lang.views.widgets.TokenTextSpec
import org.plos.edu.lang.views.widgets.TokenizedText
import org.plos.edu.lang.views.widgets.TopBarTitle

private enum class LineEmphasis {
    NONE,
    SEARCH_MATCH,
    PLAYING;

    companion object {
        fun resolve(idx: Int, playingIdx: Int?, targetIdx: Int): LineEmphasis =
            when (idx) {
                playingIdx -> PLAYING
                targetIdx -> SEARCH_MATCH
                else -> NONE
            }
    }
}

private data class LineRenderModel(
    val line: PassageLine,
    val showTranslation: Boolean,
    val emphasis: LineEmphasis,
    val speech: SpeechCallbacks,
)

private data class ReaderControlSpec(
    val isContinuous: Boolean,
    val hasAnyTranslation: Boolean,
    val showTranslation: Boolean,
)

@OptIn(FlowPreview::class)
@Composable
fun PassageReader(
    passageId: String,
    targetLineIndex: Int,
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val workspace = state.workspace ?: return
    val passage = workspace.passages.firstOrNull { it.id.value == passageId }
    if (passage == null) {
        LaunchedEffect(passageId) {
            viewModel.speech.stop()
            onBack()
        }
        return
    }
    val snackbarHost = remember { SnackbarHostState() }
    ObserveNoticeSnackbar(state.activeNotice, snackbarHost, viewModel)
    val maxIdx = (passage.lines.size - 1).coerceAtLeast(0)
    val hasJump = targetLineIndex >= 0
    val initialIdx =
        (if (hasJump) targetLineIndex else passage.bookmark.lineIndex)
            .coerceIn(0, maxIdx)
    val initialOffset =
        if (hasJump) 0 else passage.bookmark.scrollOffset.coerceAtLeast(0)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIdx,
        initialFirstVisibleItemScrollOffset = initialOffset,
    )
    val buildMark = {
        val done = listState.layoutInfo.totalItemsCount > 0 &&
            !listState.canScrollForward
        ReadingBookmark(
            lineIndex = listState.firstVisibleItemIndex,
            scrollOffset = listState.firstVisibleItemScrollOffset,
            hasRead = true,
            completed = done,
        )
    }
    DisposableEffect(passage.id) {
        onDispose {
            viewModel.saveReadingBookmark(passage.id, buildMark())
            viewModel.speech.stop()
        }
    }
    LaunchedEffect(listState, passage.id) {
        snapshotFlow(buildMark)
            .distinctUntilChanged()
            .debounce(350L)
            .collect { mark -> viewModel.saveReadingBookmark(passage.id, mark) }
    }
    val activePlayback = state.playback?.takeIf { it.passageId == passage.id }
    val playingIdx = activePlayback?.lineIndex
    LaunchedEffect(playingIdx) {
        if (playingIdx != null && playingIdx in passage.lines.indices) {
            listState.animateScrollToItem(playingIdx)
        }
    }
    val hasAnyTranslation = remember(passage.lines) {
        passage.lines.any {
            it.sourceTranslation.isNotEmpty() || it.contrast != null
        }
    }
    val speech = remember(viewModel) {
        SpeechCallbacks(
            onNormal = viewModel.speech::speakNormal,
            onSlow = viewModel.speech::speakSlow,
        )
    }
    var showTranslation by rememberSaveable { mutableStateOf(true) }
    val controlSpec = ReaderControlSpec(
        isContinuous = activePlayback != null,
        hasAnyTranslation = hasAnyTranslation,
        showTranslation = showTranslation,
    )
    val handleBack = {
        viewModel.speech.stop()
        onBack()
    }
    val onTogglePlay = {
        val fromIdx = listState.firstVisibleItemIndex
        viewModel.speech.togglePassage(passage.id, fromIdx)
    }
    val onToggleTranslation = { showTranslation = !showTranslation }
    val onExtract = { viewModel.customTracks.openForPassage(passage) }
    Scaffold(
        topBar = { ReaderTopBar(passage.title, handleBack, onExtract) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            ReaderFloatingDock(
                spec = controlSpec,
                onTogglePlay = onTogglePlay,
                onToggleTranslation = onToggleTranslation,
            )
        },
    ) { innerPadding ->
        val listPad = PaddingValues(
            top = innerPadding.calculateTopPadding() + 6.dp,
            bottom = innerPadding.calculateBottomPadding() + 12.dp,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = listPad,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(
                items = passage.lines,
                key = { _, item -> item.lineIndex },
            ) { idx, line ->
                val emphasis =
                    LineEmphasis.resolve(idx, playingIdx, targetLineIndex)
                val model =
                    LineRenderModel(line, showTranslation, emphasis, speech)
                DialogueLineCard(model, workspace, viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
    onExtractWords: () -> Unit,
) {
    TopAppBar(
        expandedHeight = COMPACT_TOP_BAR_HEIGHT,
        title = { TopBarTitle(title) },
        navigationIcon = {
            IconButton(onClick = onBack) { SymbolIcon(AppIcon.BACK) }
        },
        actions = {
            IconButton(onClick = onExtractWords) {
                SymbolIcon(AppIcon.BOOKMARK)
            }
        },
    )
}

@Composable
private fun ReaderFloatingDock(
    spec: ReaderControlSpec,
    onTogglePlay: () -> Unit,
    onToggleTranslation: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dockMod = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(top = 4.dp, bottom = 12.dp)
    val border = BorderStroke(0.8.dp, scheme.outlineVariant.copy(alpha = 0.7f))
    val rowMod = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
    val dividerMod = Modifier
        .width(1.dp)
        .height(18.dp)
        .background(scheme.outlineVariant.copy(alpha = 0.7f))
    Box(modifier = dockMod, contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = scheme.surfaceContainerHigh,
            border = border,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = rowMod,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PlayControlSegment(spec.isContinuous, onTogglePlay)
                if (spec.hasAnyTranslation) {
                    val transOn = spec.showTranslation
                    Box(modifier = dividerMod)
                    TranslationSegment(transOn, onToggleTranslation)
                }
            }
        }
    }
}

@Composable
private fun PlayControlSegment(
    isContinuous: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (isContinuous) scheme.primaryContainer else Color.Transparent
    val fg = if (isContinuous) scheme.onPrimaryContainer else scheme.onSurface
    val iconTint = if (isContinuous) {
        scheme.onPrimaryContainer
    } else {
        scheme.primary
    }
    val icon = if (isContinuous) AppIcon.PAUSE else AppIcon.PLAY
    val label = if (isContinuous) "暂停朗读" else "连续朗读"
    val weight = if (isContinuous) FontWeight.SemiBold else FontWeight.Medium
    val segMod = Modifier
        .height(36.dp)
        .clip(CircleShape)
        .background(bg)
        .clickable(onClick = onClick)
        .padding(horizontal = 14.dp)
    Row(
        modifier = segMod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SymbolIcon(
            icon = icon,
            modifier = Modifier.size(18.dp),
            tint = iconTint,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = weight,
            color = fg,
        )
    }
}

@Composable
private fun TranslationSegment(
    active: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (active) scheme.secondaryContainer else Color.Transparent
    val fg = if (active) {
        scheme.onSecondaryContainer
    } else {
        scheme.onSurfaceVariant
    }
    val weight = if (active) FontWeight.SemiBold else FontWeight.Medium
    val segMod = Modifier
        .height(36.dp)
        .clip(CircleShape)
        .background(bg)
        .clickable(onClick = onClick)
        .padding(horizontal = 14.dp)
    Row(
        modifier = segMod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (active) {
            SymbolIcon(
                icon = AppIcon.CHECK,
                modifier = Modifier.size(16.dp),
                tint = fg,
            )
        }
        Text(
            text = "译文",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = weight,
            color = fg,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogueLineCard(
    model: LineRenderModel,
    workspace: LanguageWorkspace,
    viewModel: MainViewModel,
) {
    val line = model.line
    val scheme = MaterialTheme.colorScheme
    val border = when (model.emphasis) {
        LineEmphasis.PLAYING -> BorderStroke(1.6.dp, scheme.primary)
        LineEmphasis.SEARCH_MATCH -> BorderStroke(1.3.dp, scheme.tertiary)
        LineEmphasis.NONE -> CardDefaults.outlinedCardBorder()
    }
    val containerColor = when (model.emphasis) {
        LineEmphasis.PLAYING -> scheme.secondaryContainer.copy(alpha = 0.28f)
        LineEmphasis.SEARCH_MATCH -> {
            scheme.tertiaryContainer.copy(alpha = 0.18f)
        }
        LineEmphasis.NONE -> scheme.surface
    }
    val cardColors = CardDefaults.outlinedCardColors(
        containerColor = containerColor,
    )
    val innerMod = Modifier
        .fillMaxWidth()
        .padding(14.dp)
    val tokenSpec = TokenTextSpec(
        text = line.text,
        style = MaterialTheme.typography.titleMedium,
        mastery = workspace.mastery,
        morphology = workspace.morphology,
        policy = HighlightPolicy.RECENTLY_LEARNED,
    )
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = border,
        colors = cardColors,
    ) {
        Column(
            modifier = innerMod,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LineTextBlock(line, tokenSpec, model.speech, viewModel::inspectWord)
            if (line.grammarRules.isNotEmpty()) {
                GrammarChipsRow(line.grammarRules) { rule ->
                    viewModel.showSheet(ActiveSheet.Grammar(rule))
                }
            }
            LineNotesBlock(line, model.showTranslation, model.speech)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GrammarChipsRow(
    rules: List<GrammarRule>,
    onInspectRule: (GrammarRule) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (rule in rules) {
            AssistChip(
                onClick = { onInspectRule(rule) },
                leadingIcon = { SymbolIcon(AppIcon.SPARK) },
                label = { Text(text = rule.tagLabel) },
            )
        }
    }
}

@Composable
private fun LineTextBlock(
    line: PassageLine,
    tokenSpec: TokenTextSpec,
    speech: SpeechCallbacks,
    onWordTap: (String) -> Unit,
) {
    if (line.speaker.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = line.speaker,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            AudioPlayButton(text = line.text, callbacks = speech)
        }
        TokenizedText(tokenSpec, onWordTap)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TokenizedText(tokenSpec, onWordTap)
        }
        AudioPlayButton(text = line.text, callbacks = speech)
    }
}

@Composable
private fun LineNotesBlock(
    line: PassageLine,
    showTranslation: Boolean,
    speech: SpeechCallbacks,
) {
    val contrast = line.contrast
    val hasNote = line.sourceTranslation.isNotEmpty() || contrast != null
    if (!showTranslation || !hasNote) return
    HorizontalDivider()
    if (line.sourceTranslation.isNotEmpty()) {
        Text(
            text = line.sourceTranslation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (contrast != null) {
        ContrastBox(contrast = contrast, speech = speech)
    }
}
