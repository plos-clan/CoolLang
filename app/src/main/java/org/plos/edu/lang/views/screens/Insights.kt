package org.plos.edu.lang.views.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import org.plos.edu.lang.engine.LearnerPreferences
import org.plos.edu.lang.engine.ProgressAnalytics
import org.plos.edu.lang.engine.StabilityBucket
import org.plos.edu.lang.engine.StudyTrack
import org.plos.edu.lang.views.model.MainViewModel
import org.plos.edu.lang.views.theme.studyColors

private data class BarDatum(
    val label: String,
    val count: Int,
    val color: Color,
)

private data class BarChartSpec(
    val bars: List<BarDatum>,
    val axisColor: Color,
    val textStyle: TextStyle,
)

@Composable
fun Insights(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val workspace = state.workspace ?: return
    val analytics = workspace.analytics
    val scrollMod = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 10.dp)
    Column(
        modifier = scrollMod,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ActiveTrackPlanCard(
            activeTrack = workspace.activeTrack,
            analytics = analytics,
            preferences = workspace.preferences,
        )
        StudyTimelineCard(
            historyBars = analytics.historyBars,
            forecastBars = analytics.forecastBars,
        )
        StabilityLadderCard(analytics.stabilityBuckets)
    }
}

@Composable
private fun ActiveTrackPlanCard(
    activeTrack: StudyTrack,
    analytics: ProgressAnalytics,
    preferences: LearnerPreferences,
) {
    val scheme = MaterialTheme.colorScheme
    val study = MaterialTheme.studyColors
    val total = max(1, analytics.selectedCount)
    val masteredRatio = analytics.masteredCount.toFloat() / total
    val learningRatio = analytics.learningCount.toFloat() / total
    val unseenRatio = analytics.unseenCount.toFloat() / total
    val newLim = preferences.dailyNewLimit
    val capDesc = if (preferences.dailyTotalCap.isUnlimited) {
        "每日 $newLim 新词"
    } else {
        "每日 $newLim 新词 · 总上限 ${preferences.dailyTotalCap.rawLimit}"
    }
    val etaSummary = when {
        analytics.unseenCount > 0 -> {
            val days = max(1, analytics.estimatedFinishDays)
            "按$capDesc，预计约 $days 天学完本词书剩余新词"
        }
        analytics.learningCount > 0 -> {
            "新词已全部引入，剩余 ${analytics.learningCount} 词正在滚动巩固"
        }
        else -> "当前词书计划内词汇已全部掌握"
    }
    val innerMod = Modifier.fillMaxWidth().padding(16.dp)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = innerMod,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TrackPlanHeader(activeTrack)
            val etaPad = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            Surface(
                color = scheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = etaSummary,
                    modifier = etaPad,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurface,
                )
            }
            StackedMasteryBar(masteredRatio, learningRatio, unseenRatio)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StageTableRow(
                    dotColor = study.green.mastered,
                    label = "熟知",
                    count = analytics.masteredCount,
                    total = total,
                )
                StageTableRow(
                    dotColor = study.green.bright,
                    label = "在学",
                    count = analytics.learningCount,
                    total = total,
                )
                StageTableRow(
                    dotColor = study.green.pending,
                    label = "待学",
                    count = analytics.unseenCount,
                    total = total,
                )
            }
            if (analytics.ignoredCount > 0) {
                val ignoredMsg = "另有 ${analytics.ignoredCount} 词已设为不想背，" +
                    "不计入计划分母（全书 ${analytics.trackTotalCount} 词）"
                Text(
                    text = ignoredMsg,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            RetentionMetricFooter(analytics)
        }
    }
}

@Composable
private fun TrackPlanHeader(activeTrack: StudyTrack) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "当前词书 · ${activeTrack.category}",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.primary,
            )
            Text(
                text = activeTrack.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = "共 ${activeTrack.rootIds.size} 词",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StackedMasteryBar(
    masteredRatio: Float,
    learningRatio: Float,
    unseenRatio: Float,
) {
    val study = MaterialTheme.studyColors
    val barMod = Modifier
        .fillMaxWidth()
        .height(12.dp)
        .clip(CircleShape)
        .background(study.green.pending)
    Row(modifier = barMod) {
        if (masteredRatio > 0f) {
            val segMod = Modifier
                .weight(masteredRatio)
                .fillMaxHeight()
                .background(study.green.mastered)
            Box(modifier = segMod)
        }
        if (learningRatio > 0f) {
            val segMod = Modifier
                .weight(learningRatio)
                .fillMaxHeight()
                .background(study.green.bright)
            Box(modifier = segMod)
        }
        if (unseenRatio > 0f) {
            val segMod = Modifier
                .weight(unseenRatio)
                .fillMaxHeight()
                .background(study.green.pending)
            Box(modifier = segMod)
        }
    }
}

@Composable
private fun StageTableRow(
    dotColor: Color,
    label: String,
    count: Int,
    total: Int,
) {
    val pct = ((count * 100.0) / total).roundToInt()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val dotMod = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
            Box(modifier = dotMod)
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = "$count 词 ($pct%)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RetentionMetricFooter(analytics: ProgressAnalytics) {
    val scheme = MaterialTheme.colorScheme
    val targetPct = (analytics.targetRetention * 100.0).roundToInt()
    val recallValue = analytics.actualRecallRate?.let { rate ->
        String.format(Locale.US, "%.1f%%", rate * 100.0)
    } ?: "暂无"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MetricCell("实际回忆率", recallValue)
        MetricCell("目标留存率", "$targetPct%")
        MetricCell("累计复习", "${analytics.totalReviewsLogged} 次")
    }
    if (analytics.actualRecallRate == null) {
        Text(
            text = "进入次轮复习检验后将自动统计实际回忆率",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetricCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StabilityLadderCard(buckets: List<StabilityBucket>) {
    val scheme = MaterialTheme.colorScheme
    val study = MaterialTheme.studyColors
    val activeTotal = buckets.sumOf { it.wordCount }
    val bars = buckets.mapIndexed { idx, bucket ->
        BarDatum(
            label = bucket.rangeLabel,
            count = bucket.wordCount,
            color = study.stabilityBucketColor(idx),
        )
    }
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = scheme.onSurfaceVariant,
    )
    ChartPanelCard(
        title = "记忆牢固度",
        subtitle = "已建档 $activeTotal 词",
        spec = BarChartSpec(bars, scheme.outlineVariant, labelStyle),
    )
}

@Composable
private fun ChartPanelCard(
    title: String,
    subtitle: String,
    spec: BarChartSpec,
) {
    val innerMod = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = innerMod,
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VerticalBarChart(spec)
        }
    }
}

@Composable
private fun VerticalBarChart(spec: BarChartSpec) {
    val measurer = rememberTextMeasurer()
    val canvasMod = Modifier
        .fillMaxWidth()
        .height(148.dp)
    Canvas(modifier = canvasMod) {
        val count = spec.bars.size
        if (count == 0) return@Canvas
        val topReserve = 20.dp.toPx()
        val bottomReserve = 24.dp.toPx()
        val baselineY = size.height - bottomReserve
        val maxBarArea = max(1f, baselineY - topReserve)
        val peak = max(1, spec.bars.maxOf { it.count })
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
        for ((idx, item) in spec.bars.withIndex()) {
            val slotLeft = idx * slotWidth
            val centerX = slotLeft + slotWidth / 2f
            val rawH = (item.count.toFloat() / peak) * maxBarArea
            val barH = if (item.count > 0) {
                max(6.dp.toPx(), rawH)
            } else {
                stubHeight
            }
            val barColor = if (item.count == 0) {
                spec.axisColor.copy(alpha = 0.35f)
            } else {
                item.color
            }
            val barLeft = centerX - barWidth / 2f
            val barTop = baselineY - barH
            drawRoundRect(
                color = barColor,
                topLeft = Offset(barLeft, barTop),
                size = Size(barWidth, barH),
                cornerRadius = corner,
            )
            if (item.count > 0) {
                val valLayout = measurer.measure(
                    text = item.count.toString(),
                    style = spec.textStyle,
                )
                val valX = (centerX - valLayout.size.width / 2f)
                    .coerceIn(0f, max(0f, size.width - valLayout.size.width))
                val gapPx = 2.dp.toPx()
                val valY = max(0f, barTop - valLayout.size.height - gapPx)
                drawText(valLayout, topLeft = Offset(valX, valY))
            }
            val lblLayout = measurer.measure(
                text = item.label,
                style = spec.textStyle,
            )
            val lblX = (centerX - lblLayout.size.width / 2f)
                .coerceIn(0f, max(0f, size.width - lblLayout.size.width))
            val lblY = baselineY + 5.dp.toPx()
            drawText(lblLayout, topLeft = Offset(lblX, lblY))
        }
    }
}
