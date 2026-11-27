package org.plos.edu.lang.views.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt
import org.plos.edu.lang.engine.ForecastDayBar
import org.plos.edu.lang.engine.HistoryDayBar
import org.plos.edu.lang.engine.ReviewGrade
import org.plos.edu.lang.engine.TimelineMode
import org.plos.edu.lang.views.theme.studyColors

private data class LegendItem(
    val label: String,
    val color: Color,
)

private data class StackedColumn(
    val dayLabel: String,
    val showAxisLabel: Boolean,
    val segments: IntArray,
) {
    val totalCount: Int
        get() = segments.sum()
}

private data class StackedChartSpec(
    val columns: List<StackedColumn>,
    val segmentColors: List<Color>,
    val axisColor: Color,
    val textStyle: TextStyle,
)

private data class BarFrameGeometry(
    val left: Float,
    val width: Float,
    val top: Float,
    val bottom: Float,
) {
    val height: Float
        get() = bottom - top

    fun toRoundRect(corner: CornerRadius): RoundRect = RoundRect(
        left = left,
        top = top,
        right = left + width,
        bottom = bottom,
        cornerRadius = corner,
    )
}

@Composable
fun StudyTimelineCard(
    historyBars: List<HistoryDayBar>,
    forecastBars: List<ForecastDayBar>,
) {
    val scheme = MaterialTheme.colorScheme
    val study = MaterialTheme.studyColors
    var modeName by rememberSaveable {
        mutableStateOf(TimelineMode.LOAD_FORECAST.name)
    }
    val mode = TimelineMode.entries
        .firstOrNull { it.name == modeName }
        ?: TimelineMode.LOAD_FORECAST
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = scheme.onSurfaceVariant,
    )
    val isForecast = mode == TimelineMode.LOAD_FORECAST
    val legend = if (isForecast) {
        listOf(
            LegendItem("较熟", study.green.mastered),
            LegendItem("浅记", study.green.pale),
            LegendItem("新学", study.green.pending),
        )
    } else {
        listOf(
            LegendItem("简单", study.gradeColor(ReviewGrade.EASY)),
            LegendItem("认识", study.gradeColor(ReviewGrade.GOOD)),
            LegendItem("模糊", study.gradeColor(ReviewGrade.HARD)),
            LegendItem("忘记", study.gradeColor(ReviewGrade.AGAIN)),
        )
    }
    val columns = if (isForecast) {
        buildForecastColumns(forecastBars)
    } else {
        buildHistoryColumns(historyBars)
    }
    val summary = if (isForecast) {
        val sum = forecastBars.sumOf { it.totalCount }.toDouble()
        val avg = if (forecastBars.isEmpty()) {
            0
        } else {
            (sum / forecastBars.size).roundToInt()
        }
        "日均 $avg 词"
    } else {
        val sum = historyBars.sumOf { it.totalCount }
        "共 $sum 词"
    }
    val chartSpec = StackedChartSpec(
        columns = columns,
        segmentColors = legend.map { it.color },
        axisColor = scheme.outlineVariant,
        textStyle = labelStyle,
    )
    val innerMod = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = innerMod,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TimelineHeaderRow(
                title = mode.cardTitle,
                activeMode = mode,
                onSelectMode = { modeName = it.name },
            )
            TimelineLegendRow(legend, summary)
            StackedVerticalBarChart(chartSpec)
        }
    }
}

private fun buildForecastColumns(
    bars: List<ForecastDayBar>,
): List<StackedColumn> = bars.mapIndexed { idx, bar ->
    val showAxis = idx == 0 || idx == 1 || (idx >= 3 && idx % 2 == 1)
    val segs = intArrayOf(bar.matureCount, bar.youngCount, bar.newCount)
    StackedColumn(bar.dayLabel, showAxis, segs)
}

private fun buildHistoryColumns(
    bars: List<HistoryDayBar>,
): List<StackedColumn> = bars.mapIndexed { idx, bar ->
    val showAxis = (idx <= 9 && idx % 2 == 1) || idx >= 11
    val segs = intArrayOf(
        bar.easyCount,
        bar.goodCount,
        bar.hardCount,
        bar.againCount,
    )
    StackedColumn(bar.dayLabel, showAxis, segs)
}

@Composable
private fun TimelineHeaderRow(
    title: String,
    activeMode: TimelineMode,
    onSelectMode: (TimelineMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        TimelineModeSwitch(activeMode, onSelectMode)
    }
}

@Composable
private fun TimelineModeSwitch(
    activeMode: TimelineMode,
    onSelectMode: (TimelineMode) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val trackMod = Modifier
        .clip(CircleShape)
        .background(scheme.surfaceContainerHigh)
        .padding(2.dp)
    Row(
        modifier = trackMod,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (option in TimelineMode.entries) {
            val selected = option == activeMode
            val bg = if (selected) scheme.primary else Color.Transparent
            val fg = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
            val weight = if (selected) {
                FontWeight.SemiBold
            } else {
                FontWeight.Normal
            }
            val tabMod = Modifier
                .clip(CircleShape)
                .background(bg)
                .clickable { onSelectMode(option) }
                .padding(horizontal = 10.dp, vertical = 3.dp)
            Text(
                text = option.tabLabel,
                modifier = tabMod,
                color = fg,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = weight,
            )
        }
    }
}

@Composable
private fun TimelineLegendRow(
    items: List<LegendItem>,
    summary: String,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (item in items) {
                LegendDotLabel(item, scheme.onSurfaceVariant)
            }
        }
        Text(
            text = summary,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegendDotLabel(
    item: LegendItem,
    textColor: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dotMod = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(item.color)
        Box(modifier = dotMod)
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
        )
    }
}

@Composable
private fun StackedVerticalBarChart(spec: StackedChartSpec) {
    val measurer = rememberTextMeasurer()
    val canvasMod = Modifier
        .fillMaxWidth()
        .height(148.dp)
    Canvas(modifier = canvasMod) {
        val count = spec.columns.size
        if (count == 0) return@Canvas
        val topReserve = 20.dp.toPx()
        val bottomReserve = 24.dp.toPx()
        val baselineY = size.height - bottomReserve
        val maxBarArea = max(1f, baselineY - topReserve)
        val peak = max(1, spec.columns.maxOf { it.totalCount })
        drawLine(
            color = spec.axisColor.copy(alpha = 0.55f),
            start = Offset(0f, baselineY),
            end = Offset(size.width, baselineY),
            strokeWidth = 1.dp.toPx(),
        )
        val slotWidth = size.width / count
        val barWidth = (slotWidth * 0.58f).coerceAtMost(28.dp.toPx())
        val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        val stubHeight = 2.dp.toPx()
        val gapPx = 2.dp.toPx()
        val clipShapePath = Path()
        for ((idx, col) in spec.columns.withIndex()) {
            val centerX = idx * slotWidth + slotWidth / 2f
            val barLeft = centerX - barWidth / 2f
            val total = col.totalCount
            if (total <= 0) {
                drawRoundRect(
                    color = spec.axisColor.copy(alpha = 0.35f),
                    topLeft = Offset(barLeft, baselineY - stubHeight),
                    size = Size(barWidth, stubHeight),
                    cornerRadius = corner,
                )
            } else {
                val rawH = (total.toFloat() / peak) * maxBarArea
                val barH = max(6.dp.toPx(), rawH)
                val barTop = baselineY - barH
                val frame = BarFrameGeometry(
                    left = barLeft,
                    width = barWidth,
                    top = barTop,
                    bottom = baselineY,
                )
                clipShapePath.reset()
                clipShapePath.addRoundRect(frame.toRoundRect(corner))
                drawStackedSegments(
                    clipTarget = clipShapePath,
                    frame = frame,
                    segments = col.segments,
                    colors = spec.segmentColors,
                )
                val valLayout = measurer.measure("$total", spec.textStyle)
                val valX = (centerX - valLayout.size.width / 2f)
                    .coerceIn(0f, max(0f, size.width - valLayout.size.width))
                val valY = max(0f, barTop - valLayout.size.height - gapPx)
                drawText(valLayout, topLeft = Offset(valX, valY))
            }
            if (col.showAxisLabel) {
                val lblLayout = measurer.measure(col.dayLabel, spec.textStyle)
                val lblX = (centerX - lblLayout.size.width / 2f)
                    .coerceIn(0f, max(0f, size.width - lblLayout.size.width))
                val lblY = baselineY + 5.dp.toPx()
                drawText(lblLayout, topLeft = Offset(lblX, lblY))
            }
        }
    }
}

private fun DrawScope.drawStackedSegments(
    clipTarget: Path,
    frame: BarFrameGeometry,
    segments: IntArray,
    colors: List<Color>,
) {
    val total = segments.sum().coerceAtLeast(1)
    val lastNonZero = segments.indexOfLast { it > 0 }
    clipPath(clipTarget) {
        var cursorBottom = frame.bottom
        for ((sIdx, segVal) in segments.withIndex()) {
            if (segVal <= 0) continue
            val segTop = if (sIdx == lastNonZero) {
                frame.top
            } else {
                (cursorBottom - frame.height * (segVal.toFloat() / total))
                    .coerceAtLeast(frame.top)
            }
            val segColor = colors.getOrElse(sIdx) { Color.Gray }
            drawRect(
                color = segColor,
                topLeft = Offset(frame.left, segTop),
                size = Size(frame.width, cursorBottom - segTop),
            )
            cursorBottom = segTop
        }
    }
}
