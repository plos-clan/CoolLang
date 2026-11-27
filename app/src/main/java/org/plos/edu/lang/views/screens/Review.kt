package org.plos.edu.lang.views.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.plos.edu.lang.engine.ExampleItem
import org.plos.edu.lang.engine.ReviewGrade
import org.plos.edu.lang.engine.RootOverview
import org.plos.edu.lang.engine.StudyCard
import org.plos.edu.lang.engine.UndoPreview
import org.plos.edu.lang.modules.SpeechStatus
import org.plos.edu.lang.repository.LanguageWorkspace
import org.plos.edu.lang.views.model.MainViewModel
import org.plos.edu.lang.views.widgets.AudioPlayButton
import org.plos.edu.lang.views.widgets.CollocationsRow
import org.plos.edu.lang.views.widgets.ContrastBox
import org.plos.edu.lang.views.widgets.HighlightPolicy
import org.plos.edu.lang.views.widgets.SpeechCallbacks
import org.plos.edu.lang.views.widgets.SpokenVariantsRow
import org.plos.edu.lang.views.widgets.TokenTextSpec
import org.plos.edu.lang.views.widgets.TokenizedText
import org.plos.edu.lang.views.widgets.WordBookmarkButton
import org.plos.edu.lang.views.widgets.WordSensesBlock

private data class CardViewFlags(
    val revealed: Boolean,
    val restoredMastered: Boolean,
)

private data class CardRenderModel(
    val workspace: LanguageWorkspace,
    val card: StudyCard,
    val flags: CardViewFlags,
    val speech: SpeechCallbacks,
)

@Composable
fun Review(
    viewModel: MainViewModel,
    callbacks: ReviewNavCallbacks,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val workspace = state.workspace ?: return
    val currentCard = workspace.sessionQueue.firstOrNull()
    val scrollState = rememberScrollState()
    LaunchedEffect(currentCard?.id) {
        scrollState.scrollTo(0)
    }
    val speech = remember(viewModel) {
        SpeechCallbacks(
            onNormal = viewModel.speech::speakNormal,
            onSlow = viewModel.speech::speakSlow,
        )
    }
    val undoPreview = workspace.undoPreview
    val canSwipeUndo = undoPreview != null &&
        (currentCard == null || !state.cardRevealed)
    val cardSlotKey = "${currentCard?.id?.value ?: "__completed__"}" +
        "|${currentCard?.state?.reps ?: -1}" +
        "|${workspace.activeRestoration != null}"
    val deckSpec = UndoDeckSpec(
        currentKey = cardSlotKey,
        canSwipeUndo = canSwipeUndo,
        onCommitUndo = viewModel::undoPreviousCard,
    )
    val underlaySlot: (@Composable () -> Unit)? = undoPreview?.let { prev ->
        { UndoPreviewStage(prev, workspace, speech, viewModel) }
    }
    val completedCallbacks = callbacks.copy(
        onBoostNewQuota = viewModel::boostTodayNewQuota,
    )
    val outerMod = Modifier
        .fillMaxSize()
        .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 10.dp)
    Column(
        modifier = outerMod,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.speechStatus == SpeechStatus.VOICE_MISSING) {
            SpeechBanner(state.speechStatus.hint)
        }
        val deckMod = Modifier
            .weight(1f)
            .fillMaxWidth()
        UndoDeckBox(
            modifier = deckMod,
            spec = deckSpec,
            underlay = underlaySlot,
        ) {
            if (currentCard == null) {
                CompletedCard(
                    modifier = Modifier.fillMaxSize(),
                    workspace = workspace,
                    scrollState = scrollState,
                    callbacks = completedCallbacks,
                )
            } else {
                val activeRestore = workspace.activeRestoration
                    ?.takeIf { it.cardId == currentCard.id }
                val restoredMark = activeRestore?.restoredMasteredMark == true
                val flags = CardViewFlags(state.cardRevealed, restoredMark)
                val model = CardRenderModel(
                    workspace = workspace,
                    card = currentCard,
                    flags = flags,
                    speech = speech,
                )
                CardStageColumn(
                    model = model,
                    previousGrade = activeRestore?.previousGrade,
                    scrollState = scrollState,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun UndoPreviewStage(
    preview: UndoPreview,
    workspace: LanguageWorkspace,
    speech: SpeechCallbacks,
    viewModel: MainViewModel,
) {
    val previewScroll = rememberScrollState()
    val flags = CardViewFlags(
        revealed = true,
        restoredMastered = preview.restoration.restoredMasteredMark,
    )
    val model = CardRenderModel(
        workspace = workspace,
        card = preview.card,
        flags = flags,
        speech = speech,
    )
    CardStageColumn(
        model = model,
        previousGrade = preview.restoration.previousGrade,
        scrollState = previewScroll,
        viewModel = viewModel,
    )
}

@Composable
private fun CardStageColumn(
    model: CardRenderModel,
    previousGrade: ReviewGrade?,
    scrollState: ScrollState,
    viewModel: MainViewModel,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActiveCardSurface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            model = model,
            scrollState = scrollState,
            viewModel = viewModel,
        )
        if (model.flags.revealed) {
            BottomGradeDock(
                card = model.card,
                previousGrade = previousGrade,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun ActiveCardSurface(
    modifier: Modifier,
    model: CardRenderModel,
    scrollState: ScrollState,
    viewModel: MainViewModel,
) {
    val revealed = model.flags.revealed
    val tapInteraction = remember { MutableInteractionSource() }
    val surfaceTapMod = if (!revealed) {
        Modifier.clickable(
            interactionSource = tapInteraction,
            indication = null,
            onClick = viewModel::revealCard,
        )
    } else {
        Modifier.verticalScroll(scrollState)
    }
    val bodyMod = surfaceTapMod
        .fillMaxSize()
        .padding(horizontal = 20.dp, vertical = 16.dp)
    ElevatedCard(modifier = modifier) {
        Column(modifier = bodyMod) {
            CardTopBlock(model, viewModel)
            if (!revealed) {
                Spacer(modifier = Modifier.weight(1f))
                UnrevealedBottomHint()
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(14.dp))
                RevealedSection(model, viewModel)
            }
        }
    }
}

@Composable
private fun UnrevealedBottomHint() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "轻点空白处显示释义",
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun CardTopBlock(
    model: CardRenderModel,
    viewModel: MainViewModel,
) {
    val card = model.card
    val unit = card.unit
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = unit.headword,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            if (unit.phoneticHint.isNotEmpty()) {
                Text(
                    text = unit.phoneticHint,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            SpokenVariantsRow(
                forms = unit.variantForms,
                styleBadge = unit.styleBadge,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AudioPlayButton(
                text = unit.headword,
                callbacks = model.speech,
                tonal = true,
            )
            WordBookmarkButton(
                rootId = card.root.id,
                workspace = model.workspace,
                viewModel = viewModel,
                size = 38.dp,
            )
            FamiliarIconAction(
                outlined = model.flags.restoredMastered,
                onMarkMastered = {
                    viewModel.markCardMastered(card.id, unit.headword)
                },
            )
        }
    }
}

@Composable
private fun ExampleSentenceBox(
    model: CardRenderModel,
    example: ExampleItem,
    viewModel: MainViewModel,
) {
    val workspace = model.workspace
    val focus = example.focusWord.ifBlank { model.card.unit.headword }
    val innerPad = Modifier
        .fillMaxWidth()
        .padding(start = 14.dp, end = 8.dp, top = 11.dp, bottom = 11.dp)
    val spec = TokenTextSpec(
        text = example.text,
        style = MaterialTheme.typography.titleMedium,
        mastery = workspace.mastery,
        morphology = workspace.morphology,
        policy = HighlightPolicy.FOCUS_ONLY,
        focusWord = focus,
        focusRootId = model.card.root.id,
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = innerPad,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ExampleTextColumn(
                modifier = Modifier.weight(1f),
                example = example,
                spec = spec,
                viewModel = viewModel,
            )
            AudioPlayButton(text = example.text, callbacks = model.speech)
        }
    }
}

@Composable
private fun ExampleTextColumn(
    modifier: Modifier,
    example: ExampleItem,
    spec: TokenTextSpec,
    viewModel: MainViewModel,
) {
    Column(modifier = modifier) {
        if (example.sourceLabel.isNotEmpty()) {
            Text(
                text = example.sourceLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        TokenizedText(spec, viewModel::inspectWord)
        if (example.meaning.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = example.meaning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RevealedSection(
    model: CardRenderModel,
    viewModel: MainViewModel,
) {
    val mastery = model.workspace.mastery
    val card = model.card
    val unit = card.unit
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WordSensesBlock(senses = unit.senses, prominent = true)
        if (card.root.usageNote.isNotEmpty()) {
            Text(
                text = card.root.usageNote,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (unit.collocations.isNotEmpty()) {
            CollocationsRow(unit.collocations, model.speech)
        }
        for (example in unit.examples) {
            ExampleSentenceBox(model, example, viewModel)
        }
        val contrast = unit.contrast
        if (contrast != null) {
            ContrastBox(contrast = contrast, speech = model.speech)
        }
        val root = card.root
        val showRootChip = card.familyMembers.isNotEmpty() ||
            !unit.headword.equals(root.lemma, ignoreCase = true)
        if (showRootChip) {
            val overview = RootOverview(
                root = root,
                members = card.familyMembers,
                stage = mastery.stageOf(root.id),
                disposition = mastery.dispositionOf(root.id),
                planStage = mastery.planStageOf(root.id),
                stabilityDays = mastery.stabilityOf(root.id),
                familiarityRatio = mastery.familiarityOf(root.id),
            )
            val rootTitle = if (card.familyMembers.isNotEmpty()) {
                "词族 · ${root.lemma} (${card.familyMembers.size})"
            } else {
                "原型 · ${root.lemma}"
            }
            AssistChip(
                onClick = { viewModel.inspectRoot(overview) },
                label = { Text(text = rootTitle) },
            )
        }
    }
}
