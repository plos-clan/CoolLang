package org.plos.edu.lang.views.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.plos.edu.lang.engine.GradeOption
import org.plos.edu.lang.engine.ReviewGrade
import org.plos.edu.lang.engine.StudyCard
import org.plos.edu.lang.repository.LanguageWorkspace
import org.plos.edu.lang.views.model.MainViewModel
import org.plos.edu.lang.views.theme.studyColors
import org.plos.edu.lang.views.widgets.AppIcon
import org.plos.edu.lang.views.widgets.SymbolIcon

data class ReviewNavCallbacks(
    val onOpenImmerse: () -> Unit,
    val onOpenTodayLearned: () -> Unit,
    val onOpenTrackCatalog: () -> Unit,
    val onBoostNewQuota: () -> Unit = {},
)

data class UndoDeckSpec(
    val currentKey: String,
    val canSwipeUndo: Boolean,
    val onCommitUndo: () -> Unit,
)

private class UndoSwipeController(
    private val scope: CoroutineScope,
) {
    private val slideOut = tween<Float>(
        durationMillis = 165,
        easing = FastOutSlowInEasing,
    )
    private val springBack = spring<Float>(
        stiffness = Spring.StiffnessMediumLow,
    )
    private var settleJob: Job? = null
    var isCommitted: Boolean = false
        private set
    var offsetPx by mutableFloatStateOf(0f)
        private set

    fun onDragStart() {
        if (isCommitted) return
        settleJob?.cancel()
        settleJob = null
    }

    fun onDragDelta(delta: Float, maxWidthPx: Int) {
        if (isCommitted) return
        settleJob?.cancel()
        settleJob = null
        val maxX = maxWidthPx.toFloat().coerceAtLeast(1f)
        offsetPx = (offsetPx + delta).coerceIn(0f, maxX)
    }

    fun onDragStopped(
        velocity: Float,
        maxWidthPx: Int,
        onCommit: () -> Unit,
    ) {
        if (isCommitted) return
        settleJob?.cancel()
        val widthF = maxWidthPx.toFloat().coerceAtLeast(1f)
        val passedDist = offsetPx >= widthF * 0.26f
        val passedFling = velocity >= 900f && offsetPx >= widthF * 0.08f
        if (passedDist || passedFling) {
            isCommitted = true
            settleJob = scope.launch {
                animate(
                    initialValue = offsetPx,
                    targetValue = widthF,
                    initialVelocity = velocity,
                    animationSpec = slideOut,
                ) { value, _ -> offsetPx = value }
                offsetPx = widthF
                onCommit()
            }
            return
        }
        settleJob = scope.launch {
            animate(
                initialValue = offsetPx,
                targetValue = 0f,
                initialVelocity = velocity,
                animationSpec = springBack,
            ) { value, _ -> offsetPx = value.coerceAtLeast(0f) }
            offsetPx = 0f
        }
    }

    fun snapReset() {
        settleJob?.cancel()
        settleJob = null
        offsetPx = 0f
    }
}

@Composable
fun UndoDeckBox(
    modifier: Modifier,
    spec: UndoDeckSpec,
    underlay: (@Composable () -> Unit)?,
    foreground: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableIntStateOf(1) }
    val controller = remember(spec.currentKey, scope) {
        UndoSwipeController(scope)
    }
    val hasUnderlay = underlay != null
    val activeSwipe = spec.canSwipeUndo && hasUnderlay
    LaunchedEffect(hasUnderlay) {
        val shouldReset = !hasUnderlay &&
            !controller.isCommitted &&
            controller.offsetPx > 0f
        if (shouldReset) {
            controller.snapReset()
        }
    }
    val dragState = rememberDraggableState { delta ->
        controller.onDragDelta(delta, widthPx)
    }
    val boxMod = modifier
        .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
        .draggable(
            orientation = Orientation.Horizontal,
            enabled = activeSwipe,
            state = dragState,
            onDragStarted = { controller.onDragStart() },
            onDragStopped = { velocity ->
                controller.onDragStopped(velocity, widthPx, spec.onCommitUndo)
            },
        )
    val currentOffset = controller.offsetPx
    Box(modifier = boxMod) {
        if (underlay != null && currentOffset > 0.5f) {
            val underMod = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val w = widthPx.toFloat().coerceAtLeast(1f)
                    val progress = (currentOffset / w).coerceIn(0f, 1f)
                    translationX = -w * 0.20f * (1f - progress)
                    val scale = 0.96f + 0.04f * progress
                    scaleX = scale
                    scaleY = scale
                }
            Box(modifier = underMod) { underlay() }
        }
        val fgMod = Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = currentOffset }
        Box(modifier = fgMod) { foreground() }
    }
}

@Composable
fun FamiliarIconAction(
    outlined: Boolean,
    onMarkMastered: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val border = if (outlined) BorderStroke(1.8.dp, scheme.primary) else null
    val bgColor = if (outlined) {
        scheme.primaryContainer
    } else {
        scheme.surfaceContainerHigh
    }
    val fgColor = if (outlined) scheme.primary else scheme.onSurfaceVariant
    val btnMod = Modifier
        .size(38.dp)
        .clip(CircleShape)
        .clickable(onClick = onMarkMastered)
    Surface(
        color = bgColor,
        contentColor = fgColor,
        shape = CircleShape,
        border = border,
        modifier = btnMod,
    ) {
        Box(contentAlignment = Alignment.Center) {
            SymbolIcon(AppIcon.FAMILIAR)
        }
    }
}

@Composable
fun BottomGradeDock(
    card: StudyCard,
    previousGrade: ReviewGrade?,
    viewModel: MainViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (grade in ReviewGrade.entries) {
            val option = card.preview.optionFor(grade) ?: continue
            val isPrevious = previousGrade == grade
            GradeButtonCell(
                modifier = Modifier.weight(1f),
                option = option,
                outlined = isPrevious,
                onSelect = { viewModel.gradeCard(card.id, option.grade) },
            )
        }
    }
}

@Composable
private fun GradeButtonCell(
    modifier: Modifier,
    option: GradeOption,
    outlined: Boolean,
    onSelect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val accentColor = MaterialTheme.studyColors.gradeTextColor(option.grade)
    val border = if (outlined) BorderStroke(1.8.dp, scheme.primary) else null
    val pad = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
    val weight = if (outlined) FontWeight.Bold else FontWeight.SemiBold
    FilledTonalButton(
        onClick = onSelect,
        modifier = modifier,
        border = border,
        contentPadding = pad,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = option.intervalLabel,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
            )
            Text(
                text = option.grade.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = weight,
            )
        }
    }
}

@Composable
fun SpeechBanner(message: String) {
    val scheme = MaterialTheme.colorScheme
    val textMod = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    Surface(
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            modifier = textMod,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun CompletedCard(
    modifier: Modifier,
    workspace: LanguageWorkspace,
    scrollState: ScrollState,
    callbacks: ReviewNavCallbacks,
) {
    val innerMod = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
        .padding(24.dp)
    val count = workspace.learnedToday.size
    val deferred = workspace.analytics.deferredDueCount
    val summary = if (deferred > 0) {
        "今日已完成 $count 词，另有 $deferred 词顺延至明日"
    } else {
        "今日已学 $count 词"
    }
    val canBoost = deferred > 0 || workspace.hasRemainingNewUnits
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = innerMod,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SymbolIcon(
                icon = AppIcon.CHECK,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "今日学习已完成",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))
            if (workspace.learnedToday.isNotEmpty()) {
                Button(
                    onClick = callbacks.onOpenTodayLearned,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "查看今日所学 ($count)")
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            if (canBoost) {
                val boostLabel = if (deferred > 0) "今日加背 10 词" else "今日加学 10 词"
                FilledTonalButton(
                    onClick = callbacks.onBoostNewQuota,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = boostLabel)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            FilledTonalButton(
                onClick = callbacks.onOpenImmerse,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "进入场景阅读")
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = callbacks.onOpenTrackCatalog,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "切换学习词书")
            }
        }
    }
}
