package org.plos.edu.lang.views.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.plos.edu.lang.engine.RootId
import org.plos.edu.lang.views.model.BatchSelectionState
import org.plos.edu.lang.views.model.MainViewModel

private data class ScrubGestureCallbacks(
    val onDown: (Float) -> Unit,
    val onMove: (Float) -> Unit,
    val onUp: () -> Unit,
)

private class GutterDragTracker(
    private val listState: LazyListState,
    private val onApply: (RootId, Boolean) -> Unit,
) {
    private var targetSelect: Boolean = true
    private var lastHit: RootId? = null

    fun onStart(yPx: Float, selectedIds: Set<RootId>) {
        val rootId = WordRowKey.decodeAtY(listState, yPx)
        lastHit = rootId
        if (rootId != null) {
            targetSelect = rootId !in selectedIds
            onApply(rootId, targetSelect)
        }
    }

    fun onMove(yPx: Float, selectedIds: Set<RootId>) {
        val rootId = WordRowKey.decodeAtY(listState, yPx) ?: return
        if (rootId == lastHit) return
        lastHit = rootId
        if ((rootId in selectedIds) != targetSelect) {
            onApply(rootId, targetSelect)
        }
    }

    fun reset() {
        lastHit = null
    }
}

@Composable
fun QuickJumpIndexRail(
    rail: QuickRailModel,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentRail by rememberUpdatedState(rail)
    val currentList by rememberUpdatedState(listState)
    var railHeightPx by remember { mutableIntStateOf(1) }
    var isScrubbing by remember { mutableStateOf(false) }
    var touchYPx by remember { mutableFloatStateOf(0f) }
    var scrubbedStopIdx by remember { mutableIntStateOf(0) }
    val listFraction by remember(rail, listState) {
        derivedStateOf { rail.scrollFraction(listState) }
    }
    val activeFraction = if (isScrubbing) {
        (touchYPx / railHeightPx.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
    } else {
        listFraction
    }
    val activeSlotIdx = rail.highlightedSlotIndex(activeFraction)
    val onScrubAt: (Float, Boolean) -> Unit = { rawY, force ->
        val clampedY = rawY.coerceIn(0f, railHeightPx.toFloat())
        touchYPx = clampedY
        val nextStop = currentRail.stopIndexAtY(clampedY, railHeightPx)
        if (force || nextStop != scrubbedStopIdx) {
            scrubbedStopIdx = nextStop
            val targetItem = currentRail.stops[nextStop].listIndex
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch { currentList.scrollToItem(targetItem) }
        }
    }
    val callbacks = ScrubGestureCallbacks(
        onDown = { y ->
            isScrubbing = true
            onScrubAt(y, true)
        },
        onMove = { y -> onScrubAt(y, false) },
        onUp = { isScrubbing = false },
    )
    val bubbleProgress by animateFloatAsState(
        targetValue = if (isScrubbing) 1f else 0f,
        animationSpec = tween(durationMillis = 130),
        label = "railBubble",
    )
    val outerMod = modifier
        .height(236.dp)
        .padding(end = 2.dp)
    Box(modifier = outerMod) {
        if (bubbleProgress > 0.01f && rail.stops.isNotEmpty()) {
            val safeIdx = scrubbedStopIdx.coerceIn(0, rail.stops.lastIndex)
            val bubbleText = rail.stops[safeIdx].bubbleLabel
            val bubbleH = with(density) { 38.dp.roundToPx() }
            val rawTop = touchYPx.roundToInt() - bubbleH / 2
            FloatingRailCallout(
                text = bubbleText,
                progress = bubbleProgress,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 24.dp)
                    .offset { IntOffset(0, rawTop) },
            )
        }
        val touchMod = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(24.dp)
            .onSizeChanged { railHeightPx = it.height.coerceAtLeast(1) }
            .verticalScrubPointer(callbacks)
        FramelessScaleRail(
            slots = rail.slots,
            activeSlotIdx = activeSlotIdx,
            progressFraction = activeFraction,
            modifier = touchMod,
        )
    }
}

private fun Modifier.verticalScrubPointer(
    callbacks: ScrubGestureCallbacks,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        callbacks.onDown(down.position.y)
        do {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull()
            if (change != null && change.pressed) {
                change.consume()
                callbacks.onMove(change.position.y)
            }
        } while (event.changes.any { it.pressed })
        callbacks.onUp()
    }
}

@Composable
private fun FramelessScaleRail(
    slots: List<RailVisualSlot>,
    activeSlotIdx: Int,
    progressFraction: Float,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var trackHeightPx by remember { mutableIntStateOf(1) }
    val thumbHeightDp = 14.dp
    val thumbHeightPx = with(density) { thumbHeightDp.roundToPx() }
    val travelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0)
    val thumbTopPx = (progressFraction.coerceIn(0f, 1f) * travelPx).roundToInt()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            for ((idx, slot) in slots.withIndex()) {
                RailSlotMark(slot = slot, active = idx == activeSlotIdx)
            }
        }
        Box(
            modifier = Modifier
                .padding(start = 1.5.dp)
                .fillMaxHeight()
                .width(3.dp)
                .onSizeChanged { trackHeightPx = it.height.coerceAtLeast(1) },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .width(1.dp)
                    .clip(CircleShape)
                    .background(scheme.outlineVariant.copy(alpha = 0.35f)),
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, thumbTopPx) }
                    .width(3.dp)
                    .height(thumbHeightDp)
                    .clip(CircleShape)
                    .background(scheme.primary),
            )
        }
    }
}

@Composable
private fun RailSlotMark(
    slot: RailVisualSlot,
    active: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    when (slot) {
        is RailVisualSlot.Anchor -> {
            val textColor = if (active) {
                scheme.primary
            } else {
                scheme.onSurfaceVariant.copy(alpha = 0.72f)
            }
            val weight = if (active) FontWeight.Bold else FontWeight.Medium
            Text(
                text = slot.label,
                fontSize = 8.5.sp,
                lineHeight = 10.sp,
                fontWeight = weight,
                color = textColor,
                maxLines = 1,
            )
        }
        is RailVisualSlot.Dot -> {
            val dotSize = if (active) 3.5.dp else 2.5.dp
            val dotColor = if (active) {
                scheme.primary
            } else {
                scheme.outline.copy(alpha = 0.50f)
            }
            val dotMod = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
            Box(modifier = dotMod)
        }
    }
}

@Composable
private fun FloatingRailCallout(
    text: String,
    progress: Float,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tipPx = with(LocalDensity.current) { 7.dp.toPx() }
    val shape = remember(tipPx) { CalloutBubbleShape.create(tipPx) }
    val animMod = modifier
        .defaultMinSize(minWidth = 52.dp)
        .height(38.dp)
        .graphicsLayer {
            alpha = progress
            val scale = 0.78f + 0.22f * progress
            scaleX = scale
            scaleY = scale
            translationX = (1f - progress) * 16f
        }
    Surface(
        color = scheme.primary,
        contentColor = scheme.onPrimary,
        shape = shape,
        shadowElevation = 6.dp,
        modifier = animMod,
    ) {
        Box(
            modifier = Modifier.padding(start = 12.dp, end = 19.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

private object CalloutBubbleShape {
    fun create(tipWidthPx: Float): Shape = GenericShape { size, _ ->
        val bodyRight = (size.width - tipWidthPx).coerceAtLeast(1f)
        val r = size.height / 2f
        val pill = Path().apply {
            addRoundRect(
                RoundRect(0f, 0f, bodyRight, size.height, CornerRadius(r, r)),
            )
        }
        val pointer = Path().apply {
            moveTo(bodyRight - r * 0.35f, size.height * 0.22f)
            lineTo(size.width, size.height * 0.5f)
            lineTo(bodyRight - r * 0.35f, size.height * 0.78f)
            close()
        }
        op(pill, pointer, PathOperation.Union)
    }
}

@Composable
fun BatchGutterDragOverlay(
    listState: LazyListState,
    batch: BatchSelectionState,
    viewModel: MainViewModel,
) {
    val haptics = LocalHapticFeedback.current
    val currentSelected by rememberUpdatedState(batch.selectedIds)
    val tracker = remember(listState, viewModel) {
        GutterDragTracker(listState) { rootId, select ->
            val single = setOf(rootId)
            viewModel.modifyBatch { b, _ -> b.updatedMany(single, select) }
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val callbacks = ScrubGestureCallbacks(
        onDown = { y -> tracker.onStart(y, currentSelected) },
        onMove = { y -> tracker.onMove(y, currentSelected) },
        onUp = tracker::reset,
    )
    val gutterMod = Modifier
        .fillMaxHeight()
        .width(48.dp)
        .verticalScrubPointer(callbacks)
    Box(modifier = gutterMod)
}
