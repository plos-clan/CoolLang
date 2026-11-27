package org.plos.edu.lang.views.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.plos.edu.lang.engine.ExampleItem
import org.plos.edu.lang.engine.LexemeEntry
import org.plos.edu.lang.engine.MasteryStage
import org.plos.edu.lang.engine.PlanStage
import org.plos.edu.lang.engine.RootEntry
import org.plos.edu.lang.engine.RootId
import org.plos.edu.lang.engine.WordDisposition
import org.plos.edu.lang.repository.LanguageWorkspace
import org.plos.edu.lang.views.model.InspectorScope
import org.plos.edu.lang.views.model.MainViewModel
import org.plos.edu.lang.views.model.WordInspectorState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordSheet(
    inspector: WordInspectorState,
    workspace: LanguageWorkspace,
    viewModel: MainViewModel,
) {
    val resolution = inspector.resolution
    val scope = inspector.scope
    val speech = remember(viewModel) {
        SpeechCallbacks(
            onNormal = viewModel.speech::speakNormal,
            onSlow = viewModel.speech::speakSlow,
        )
    }
    val bodyMod = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
    ModalBottomSheet(onDismissRequest = { viewModel.showSheet(null) }) {
        Column(
            modifier = bodyMod,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InspectorHeader(inspector, workspace, speech, viewModel)
            val ruleInfo = resolution.appliedRule?.functionSummary
            val rawBreak = resolution.breakdown
            val showMorph = rawBreak.isNotEmpty() &&
                !rawBreak.equals(resolution.surface, ignoreCase = true)
            if (showMorph || !ruleInfo.isNullOrEmpty()) {
                MorphologyBanner(resolution.breakdown, ruleInfo)
            }
            val root = resolution.root
            if (root == null) {
                Text(
                    text = "当前词库暂未收录该词，可在设置中导入扩展语言包。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val custom = workspace.activeTrack.isCustom
                RootSummaryCard(root, speech)
                if (scope != InspectorScope.LOOKUP) {
                    DispositionActionRow(root.id, inspector, custom, viewModel)
                }
                if (resolution.familyMembers.isNotEmpty()) {
                    FamilyList(resolution.familyMembers, workspace, speech)
                }
            }
        }
    }
}

@Composable
private fun InspectorHeader(
    inspector: WordInspectorState,
    workspace: LanguageWorkspace,
    speech: SpeechCallbacks,
    viewModel: MainViewModel,
) {
    val res = inspector.resolution
    val scope = inspector.scope
    val root = res.root
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = res.surface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (root != null && scope == InspectorScope.IN_TRACK) {
                FamiliarityRing(
                    stage = res.planStage,
                    ratio = res.familiarityRatio,
                )
                PlanStagePill(res.planStage)
            } else if (root != null && res.stage != MasteryStage.UNSEEN) {
                MasteryPill(res.stage)
            }
        }
        if (root != null) {
            WordBookmarkButton(root.id, workspace, viewModel)
        } else {
            AudioPlayButton(
                text = res.surface,
                callbacks = speech,
                tonal = true,
            )
        }
    }
    val matched = res.matchedLexeme
    if (matched != null) {
        val counter = if (matched.counterpart.isNotEmpty()) {
            " · 对应 ${matched.counterpart}"
        } else {
            ""
        }
        Text(
            text = "${matched.meaning}$counter",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun DispositionActionRow(
    rootId: RootId,
    inspector: WordInspectorState,
    isCustomTrack: Boolean,
    viewModel: MainViewModel,
) {
    val res = inspector.resolution
    val allowIgnore = inspector.scope == InspectorScope.IN_TRACK
    val btnPad = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    val canReset = res.disposition != WordDisposition.AUTO ||
        res.stage == MasteryStage.MASTERED
    val resetLabel = if (allowIgnore) "恢复待学" else "取消熟知"
    val applyDisp: (WordDisposition) -> Unit = { disp ->
        viewModel.setWordsDisposition(setOf(rootId), disp)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (res.planStage != PlanStage.MASTERED) {
            FilledTonalButton(
                onClick = { applyDisp(WordDisposition.MASTERED) },
                modifier = Modifier.weight(1f),
                contentPadding = btnPad,
            ) {
                Text(text = "标为熟知")
            }
        }
        if (allowIgnore && isCustomTrack) {
            val onRemove = {
                viewModel.customTracks.removeFromActiveTrack(setOf(rootId))
            }
            OutlinedButton(
                onClick = onRemove,
                modifier = Modifier.weight(1f),
                contentPadding = btnPad,
            ) {
                Text(text = "移出词书")
            }
        } else if (allowIgnore && res.planStage != PlanStage.IGNORED) {
            OutlinedButton(
                onClick = { applyDisp(WordDisposition.IGNORED) },
                modifier = Modifier.weight(1f),
                contentPadding = btnPad,
            ) {
                Text(text = "不想背")
            }
        }
        if (canReset) {
            OutlinedButton(
                onClick = { applyDisp(WordDisposition.AUTO) },
                modifier = Modifier.weight(1f),
                contentPadding = btnPad,
            ) {
                Text(text = resetLabel)
            }
        }
    }
}

@Composable
private fun MorphologyBanner(breakdown: String, ruleSummary: String?) {
    val text = if (!ruleSummary.isNullOrEmpty()) {
        "构词拆解：$breakdown（$ruleSummary）"
    } else {
        "构词拆解：$breakdown"
    }
    val pad = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp)
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            modifier = pad,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun RootSummaryCard(root: RootEntry, speech: SpeechCallbacks) {
    val innerMod = Modifier
        .fillMaxWidth()
        .padding(14.dp)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = innerMod,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val hint = if (root.phoneticHint.isNotEmpty()) {
                    " · ${root.phoneticHint}"
                } else {
                    ""
                }
                Text(
                    text = "核心词：${root.lemma}$hint",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                AudioPlayButton(text = root.lemma, callbacks = speech)
            }
            if (root.spokenForms.isNotEmpty()) {
                SpokenVariantsRow(root.spokenForms, "")
            }
            WordSensesBlock(senses = root.senses, prominent = false)
            if (root.collocations.isNotEmpty()) {
                CollocationsRow(
                    collocations = root.collocations,
                    speech = speech,
                )
            }
            val firstEx = root.examples.firstOrNull()
            if (firstEx != null) {
                RootExamplePreview(firstEx, speech)
            }
            if (root.usageNote.isNotEmpty()) {
                Text(
                    text = root.usageNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RootExamplePreview(
    example: ExampleItem,
    speech: SpeechCallbacks,
) {
    val scheme = MaterialTheme.colorScheme
    val meanStyle = MaterialTheme.typography.bodySmall
    val meanColor = scheme.onSurfaceVariant
    val rowMod = Modifier
        .fillMaxWidth()
        .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
    Surface(
        color = scheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = rowMod,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = example.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (example.meaning.isNotEmpty()) {
                    Text(example.meaning, color = meanColor, style = meanStyle)
                }
            }
            AudioPlayButton(text = example.text, callbacks = speech)
        }
    }
}

@Composable
private fun FamilyList(
    members: List<LexemeEntry>,
    workspace: LanguageWorkspace,
    speech: SpeechCallbacks,
) {
    Text(
        text = "同源变形与口语写法",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    for (item in members.take(8)) {
        val styleTitle = workspace.styleLabels[item.styleKey]
            ?: item.styleKey.value
        val counterInfo = if (item.counterpart.isNotEmpty()) {
            " · 对应 ${item.counterpart}"
        } else {
            ""
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${item.surface}  [$styleTitle]$counterInfo",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${item.breakdown} · ${item.meaning}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AudioPlayButton(text = item.surface, callbacks = speech)
        }
    }
}
