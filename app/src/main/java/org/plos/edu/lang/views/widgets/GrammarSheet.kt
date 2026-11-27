package org.plos.edu.lang.views.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.plos.edu.lang.engine.GrammarExample
import org.plos.edu.lang.engine.GrammarRule
import org.plos.edu.lang.views.model.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarSheet(
    rule: GrammarRule,
    viewModel: MainViewModel,
) {
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "句法点拨 · ${rule.tagLabel}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = rule.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            val scheme = MaterialTheme.colorScheme
            val formulaPad = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
            Surface(
                color = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = rule.keyPattern,
                    modifier = formulaPad,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                text = rule.summary,
                style = MaterialTheme.typography.bodyMedium,
            )
            for (point in rule.bulletPoints) {
                Text(
                    text = "• $point",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (rule.examples.isNotEmpty()) {
                HorizontalDivider()
                for (example in rule.examples) {
                    RuleExampleRow(example, speech)
                }
            }
        }
    }
}

@Composable
private fun RuleExampleRow(
    example: GrammarExample,
    speech: SpeechCallbacks,
) {
    val rowMod = Modifier
        .fillMaxWidth()
        .clickable { speech.onNormal(example.targetText) }
        .padding(vertical = 4.dp)
    Row(
        modifier = rowMod,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = example.targetText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = example.sourceText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (example.note.isNotEmpty()) {
                Text(
                    text = example.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        AudioPlayButton(text = example.targetText, callbacks = speech)
    }
}
