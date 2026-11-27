package org.plos.edu.lang.views.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt
import org.plos.edu.lang.engine.MasterySnapshot
import org.plos.edu.lang.engine.PlanStage
import org.plos.edu.lang.engine.StudyTrack
import org.plos.edu.lang.views.model.MainViewModel
import org.plos.edu.lang.views.model.TrackModalActions
import org.plos.edu.lang.views.model.TrackModalState
import org.plos.edu.lang.views.theme.studyColors

internal data class TrackStatSummary(
    val track: StudyTrack,
    val masteredCount: Int,
    val learningCount: Int,
    val unseenCount: Int,
) {
    val selectedTotal: Int
        get() = masteredCount + learningCount + unseenCount

    companion object {
        fun evaluate(
            track: StudyTrack,
            mastery: MasterySnapshot,
        ): TrackStatSummary {
            var mastered = 0
            var learning = 0
            var unseen = 0
            for (rid in track.rootIds) {
                when (mastery.planStageOf(rid)) {
                    PlanStage.MASTERED -> mastered++
                    PlanStage.LEARNING -> learning++
                    PlanStage.UNSEEN -> unseen++
                    PlanStage.IGNORED -> Unit
                }
            }
            return TrackStatSummary(track, mastered, learning, unseen)
        }
    }
}

internal data class BookCardSpec(
    val item: TrackStatSummary,
    val isActive: Boolean,
)

@Composable
internal fun TrackBookCard(
    spec: BookCardSpec,
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val item = spec.item
    val track = item.track
    val border = if (spec.isActive) {
        BorderStroke(1.6.dp, scheme.primary)
    } else {
        CardDefaults.outlinedCardBorder()
    }
    val container = if (spec.isActive) {
        scheme.primaryContainer.copy(alpha = 0.22f)
    } else {
        scheme.surface
    }
    val denom = max(1, item.selectedTotal)
    val masteredRatio = item.masteredCount.toFloat() / denom
    val learningRatio = item.learningCount.toFloat() / denom
    val countLabel = "${track.rootIds.size} 词"
    val onSelect = {
        viewModel.selectTrack(track.id)
        onBack()
    }
    val cardMod = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
        .clickable(onClick = onSelect)
    val colors = CardDefaults.outlinedCardColors(containerColor = container)
    OutlinedCard(modifier = cardMod, border = border, colors = colors) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BookCardTitleRow(track.title, spec.isActive, countLabel)
            if (track.summary.isNotEmpty() && !track.isCustom) {
                Text(
                    text = track.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (track.rootIds.isNotEmpty()) {
                MiniMasteryProgressBar(masteredRatio, learningRatio)
            }
            BookCardStatsRow(item, denom) {
                viewModel.markEntireTrackMastered(track)
            }
            if (track.isCustom) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = scheme.outlineVariant.copy(alpha = 0.55f),
                )
                CustomBookActionsRow(track, viewModel.customTracks)
            }
        }
    }
}

@Composable
private fun BookCardTitleRow(
    title: String,
    isActive: Boolean,
    countLabel: String,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = countLabel,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
            if (isActive) {
                val tagMod = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(scheme.primary)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
                Text(
                    text = "在背",
                    modifier = tagMod,
                    color = scheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun BookCardStatsRow(
    item: TrackStatSummary,
    denom: Int,
    onMarkAllMastered: () -> Unit,
) {
    val track = item.track
    val learnCnt = item.learningCount
    val statText = if (track.rootIds.isEmpty()) {
        "暂无单词 · 可粘贴文本提取或随手加词"
    } else {
        val pct = ((item.masteredCount * 100.0) / denom).roundToInt()
        val learningSeg = if (learnCnt > 0) " · 在学 $learnCnt" else ""
        "熟知 ${item.masteredCount} ($pct%)$learningSeg · 待学 ${item.unseenCount}"
    }
    val canBulkMaster = item.unseenCount + learnCnt > 0 && !track.id.isAll
    val btnPad = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = statText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
        if (canBulkMaster) {
            TextButton(
                onClick = onMarkAllMastered,
                contentPadding = btnPad,
                modifier = Modifier.height(28.dp),
            ) {
                Text(
                    text = "整书标熟",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun CustomBookActionsRow(
    track: StudyTrack,
    actions: TrackModalActions,
) {
    val scheme = MaterialTheme.colorScheme
    val btnPad = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    val btnMod = Modifier.height(28.dp)
    val onAddWords = {
        actions.open(TrackModalState.TextExtractor(targetTrackId = track.id))
    }
    val onRename = {
        actions.open(TrackModalState.Rename(track.id, track.title))
    }
    val onDelete = { actions.deleteTrack(track) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onAddWords,
            contentPadding = btnPad,
            modifier = btnMod,
        ) {
            Text(text = "加词", style = MaterialTheme.typography.labelMedium)
        }
        TextButton(
            onClick = onRename,
            contentPadding = btnPad,
            modifier = btnMod,
        ) {
            Text(text = "改名", style = MaterialTheme.typography.labelMedium)
        }
        TextButton(
            onClick = onDelete,
            contentPadding = btnPad,
            modifier = btnMod,
        ) {
            Text(
                text = "删除",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.error,
            )
        }
    }
}

@Composable
private fun MiniMasteryProgressBar(
    masteredRatio: Float,
    learningRatio: Float,
) {
    val study = MaterialTheme.studyColors
    val rem = (1f - masteredRatio - learningRatio).coerceAtLeast(0f)
    val barMod = Modifier
        .fillMaxWidth()
        .height(6.dp)
        .clip(CircleShape)
        .background(study.green.pending)
    Row(modifier = barMod) {
        if (masteredRatio > 0f) {
            val mMod = Modifier
                .weight(masteredRatio)
                .fillMaxHeight()
                .background(study.green.mastered)
            Box(modifier = mMod)
        }
        if (learningRatio > 0f) {
            val lMod = Modifier
                .weight(learningRatio)
                .fillMaxHeight()
                .background(study.green.bright)
            Box(modifier = lMod)
        }
        if (rem > 0f) {
            Spacer(modifier = Modifier.weight(rem))
        }
    }
}
