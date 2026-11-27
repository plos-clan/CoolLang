package org.plos.edu.lang.views.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.plos.edu.lang.engine.LearnerPreferences
import org.plos.edu.lang.engine.NewWordOrder
import org.plos.edu.lang.engine.PlanStage
import org.plos.edu.lang.engine.PreferenceMutation
import org.plos.edu.lang.repository.LanguageWorkspace
import org.plos.edu.lang.views.model.MainViewModel

private data class OrderRowSpec(
    val order: NewWordOrder,
    val selected: Boolean,
    val onSelect: () -> Unit,
)

@Composable
fun NewWordOrderCard(
    workspace: LanguageWorkspace,
    viewModel: MainViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    val lang = workspace.activeProfile.code
    val track = workspace.activeTrack
    val currentOrder = workspace.preferences.orderFor(lang, track)
    val trackKey = LearnerPreferences.trackOrderKey(lang, track.id)
    val previewWords = remember(track, workspace.mastery) {
        workspace.morphology
            .queryLexicon(track, "", workspace.mastery)
            .inTrack
            .asSequence()
            .filter { it.planStage == PlanStage.UNSEEN }
            .take(4)
            .map { it.root.lemma }
            .toList()
    }
    val rowSpecs = NewWordOrder.entries.map { order ->
        val mut = PreferenceMutation.TrackWordOrder(trackKey, order)
        OrderRowSpec(
            order = order,
            selected = order == currentOrder,
            onSelect = { viewModel.applyPreference(mut) },
        )
    }
    val onReshuffle = {
        viewModel.applyPreference(PreferenceMutation.ReshuffleNewWords)
    }
    val dividerCol = scheme.outlineVariant.copy(alpha = 0.5f)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            OrderCardHeader(
                canReshuffle = currentOrder == NewWordOrder.SHUFFLED,
                onReshuffle = onReshuffle,
            )
            for (rowSpec in rowSpecs) {
                HorizontalDivider(color = dividerCol)
                NewWordOrderRow(rowSpec)
            }
            if (previewWords.isNotEmpty()) {
                HorizontalDivider(color = dividerCol)
                UpcomingOrderPreview(previewWords)
            }
        }
    }
}

@Composable
private fun OrderCardHeader(
    canReshuffle: Boolean,
    onReshuffle: () -> Unit,
) {
    val headerMod = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 8.dp)
    val btnPad = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
    Row(
        modifier = headerMod,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "新词出词顺序",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (canReshuffle) {
            TextButton(
                onClick = onReshuffle,
                contentPadding = btnPad,
                modifier = Modifier.height(32.dp),
            ) {
                Text(text = "重新洗牌")
            }
        }
    }
}

@Composable
private fun NewWordOrderRow(spec: OrderRowSpec) {
    val scheme = MaterialTheme.colorScheme
    val weight = if (spec.selected) FontWeight.SemiBold else FontWeight.Normal
    val rowMod = Modifier
        .fillMaxWidth()
        .selectable(
            selected = spec.selected,
            role = Role.RadioButton,
            onClick = spec.onSelect,
        )
        .padding(horizontal = 16.dp, vertical = 11.dp)
    Row(
        modifier = rowMod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = spec.selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = spec.order.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = weight,
            )
            Text(
                text = spec.order.description,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UpcomingOrderPreview(words: List<String>) {
    val scheme = MaterialTheme.colorScheme
    val joined = words.joinToString(", ")
    val outerMod = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp)
    val textMod = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
    Surface(
        color = scheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        modifier = outerMod,
    ) {
        Text(
            text = "待学预览：$joined",
            modifier = textMod,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
