package org.plos.edu.lang.views.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.plos.edu.lang.engine.MasteryStage
import org.plos.edu.lang.engine.PlanStage
import org.plos.edu.lang.engine.SentenceContrast
import org.plos.edu.lang.views.model.MainViewModel
import org.plos.edu.lang.views.model.NoticeSpec
import org.plos.edu.lang.views.theme.studyColors

data class SpeechCallbacks(
    val onNormal: (String) -> Unit,
    val onSlow: (String) -> Unit,
)

@Composable
fun ObserveNoticeSnackbar(
    notice: NoticeSpec?,
    hostState: SnackbarHostState,
    viewModel: MainViewModel,
) {
    LaunchedEffect(notice?.id) {
        if (notice != null) {
            hostState.currentSnackbarData?.dismiss()
            val undoKind = notice.undoKind
            val actionLabel = if (undoKind != null) "撤回" else null
            val result = hostState.showSnackbar(
                message = notice.message,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && undoKind != null) {
                viewModel.performNoticeUndo(undoKind)
            }
            viewModel.clearNotice(notice.id)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioPlayButton(
    text: String,
    callbacks: SpeechCallbacks,
    tonal: Boolean = false,
    diameter: Dp = 38.dp,
) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val bgColor = if (tonal) {
        scheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val fgColor = if (tonal) {
        scheme.onSecondaryContainer
    } else {
        scheme.onSurfaceVariant
    }
    val btnMod = Modifier
        .size(diameter)
        .clip(CircleShape)
        .combinedClickable(
            onClick = { callbacks.onNormal(text) },
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                callbacks.onSlow(text)
            },
        )
    Surface(
        color = bgColor,
        contentColor = fgColor,
        shape = CircleShape,
        modifier = btnMod,
    ) {
        Box(contentAlignment = Alignment.Center) {
            val iconMod = if (diameter < 36.dp) {
                Modifier.size(18.dp)
            } else {
                Modifier
            }
            SymbolIcon(AppIcon.VOLUME, iconMod)
        }
    }
}

@Composable
fun FamiliarityRing(
    stage: PlanStage,
    ratio: Float,
) {
    val scheme = MaterialTheme.colorScheme
    val study = MaterialTheme.studyColors
    val trackColor = scheme.outlineVariant.copy(alpha = 0.45f)
    val ignoredColor = scheme.outline.copy(alpha = 0.45f)
    val activeArcColor = study.learningColorFor(ratio)
    val masteredColor = study.green.mastered
    Canvas(modifier = Modifier.size(16.dp)) {
        val strokePx = 2.2.dp.toPx()
        val inset = strokePx / 2f
        val arcSize = Size(size.width - strokePx, size.height - strokePx)
        val topLeft = Offset(inset, inset)
        val ringStroke = Stroke(width = strokePx, cap = StrokeCap.Round)
        val baseRingColor = if (stage == PlanStage.IGNORED) {
            ignoredColor
        } else {
            trackColor
        }
        drawArc(
            color = baseRingColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = ringStroke,
        )
        when (stage) {
            PlanStage.MASTERED -> {
                drawArc(
                    color = masteredColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = ringStroke,
                )
                drawCircle(
                    color = masteredColor,
                    radius = 2.3.dp.toPx(),
                    center = center,
                )
            }
            PlanStage.LEARNING -> {
                val sweep = (ratio * 360f).coerceIn(42f, 336f)
                drawArc(
                    color = activeArcColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = ringStroke,
                )
            }
            PlanStage.IGNORED -> {
                val pad = 3.6.dp.toPx()
                drawLine(
                    color = ignoredColor,
                    start = Offset(pad, size.height - pad),
                    end = Offset(size.width - pad, pad),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            PlanStage.UNSEEN -> Unit
        }
    }
}

@Composable
fun MasteryPill(stage: MasteryStage) {
    val scheme = MaterialTheme.colorScheme
    val study = MaterialTheme.studyColors
    val containerColor = when (stage) {
        MasteryStage.UNSEEN -> scheme.surfaceVariant
        MasteryStage.LEARNING -> study.green.bright.copy(alpha = 0.16f)
        MasteryStage.MASTERED -> study.green.mastered.copy(alpha = 0.16f)
    }
    val textColor = when (stage) {
        MasteryStage.UNSEEN -> scheme.onSurfaceVariant
        MasteryStage.LEARNING -> study.pillText.learning
        MasteryStage.MASTERED -> study.pillText.mastered
    }
    val pad = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
    Surface(
        color = containerColor,
        contentColor = textColor,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = stage.label,
            modifier = pad,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
fun PlanStagePill(stage: PlanStage) {
    val scheme = MaterialTheme.colorScheme
    val study = MaterialTheme.studyColors
    val containerColor = when (stage) {
        PlanStage.UNSEEN -> scheme.surfaceVariant
        PlanStage.LEARNING -> study.green.bright.copy(alpha = 0.16f)
        PlanStage.MASTERED -> study.green.mastered.copy(alpha = 0.16f)
        PlanStage.IGNORED -> scheme.surfaceContainerHigh
    }
    val textColor = when (stage) {
        PlanStage.UNSEEN -> scheme.onSurfaceVariant
        PlanStage.LEARNING -> study.pillText.learning
        PlanStage.MASTERED -> study.pillText.mastered
        PlanStage.IGNORED -> scheme.outline
    }
    val pad = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
    Surface(
        color = containerColor,
        contentColor = textColor,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = stage.label,
            modifier = pad,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollocationsRow(
    collocations: List<String>,
    speech: SpeechCallbacks,
) {
    if (collocations.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val textPad = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (phrase in collocations) {
            val spoken = extractForeignPrefix(phrase)
            val chipMod = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable {
                    if (spoken.isNotEmpty()) speech.onNormal(spoken)
                }
            Surface(
                color = scheme.surfaceContainerHigh,
                contentColor = scheme.onSurface,
                shape = MaterialTheme.shapes.small,
                modifier = chipMod,
            ) {
                Text(
                    text = phrase,
                    modifier = textPad,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private fun extractForeignPrefix(phrase: String): String {
    val cut = phrase.indexOfFirst { it.code in 0x4E00..0x9FFF }
    if (cut < 0) return phrase.trim()
    return phrase.substring(0, cut).trim()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpokenVariantsRow(
    forms: List<String>,
    styleBadge: String,
) {
    if (forms.isEmpty() && styleBadge.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (forms.isNotEmpty()) {
            val joined = forms.joinToString(" · ")
            val pad = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            Surface(
                color = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = "口语常作 $joined",
                    modifier = pad,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        if (styleBadge.isNotEmpty()) {
            val pad = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            Surface(
                color = scheme.tertiaryContainer,
                contentColor = scheme.onTertiaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = styleBadge,
                    modifier = pad,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
fun ContrastBox(
    contrast: SentenceContrast,
    speech: SpeechCallbacks,
) {
    val scheme = MaterialTheme.colorScheme
    val innerPad = Modifier
        .fillMaxWidth()
        .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
    val hasCjk = contrast.text.any { it.code in 0x4E00..0x9FFF }
    Surface(
        color = scheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = innerPad,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contrast.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                )
                Text(
                    text = contrast.text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!hasCjk) {
                AudioPlayButton(text = contrast.text, callbacks = speech)
            }
        }
    }
}

@Composable
fun CompactFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bgColor = if (selected) {
        scheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val fgColor = if (selected) {
        scheme.onSecondaryContainer
    } else {
        scheme.onSurfaceVariant
    }
    val border = if (selected) {
        null
    } else {
        BorderStroke(1.dp, scheme.outlineVariant)
    }
    val pillMod = Modifier
        .height(30.dp)
        .clip(CircleShape)
        .clickable(onClick = onClick)
    val textMod = Modifier.padding(horizontal = 10.dp)
    Surface(
        color = bgColor,
        contentColor = fgColor,
        shape = CircleShape,
        border = border,
        modifier = pillMod,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                modifier = textMod,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
                maxLines = 1,
            )
        }
    }
}

@Composable
fun CompactSortPill(
    label: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val pillMod = Modifier
        .height(30.dp)
        .clip(CircleShape)
        .clickable(onClick = onClick)
    val innerMod = Modifier.padding(horizontal = 10.dp)
    Surface(
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        shape = CircleShape,
        modifier = pillMod,
    ) {
        Row(
            modifier = innerMod,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SymbolIcon(
                icon = AppIcon.SORT,
                modifier = Modifier.size(15.dp),
                tint = scheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
