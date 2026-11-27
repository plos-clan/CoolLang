package org.plos.edu.lang.views.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PreferenceRowSpec(
    val text: String,
    val secondaryText: String? = null,
    val icon: (@Composable () -> Unit)? = null,
    val trailing: (@Composable () -> Unit)? = null,
)

data class PreferenceSelectSpec<T>(
    val text: String,
    val currentValue: T,
    val options: List<Pair<T, String>>,
    val icon: (@Composable () -> Unit)? = null,
)

@Composable
fun PreferenceGroupTitle(title: String) {
    val mod = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    Text(
        text = title,
        modifier = mod,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun PreferenceClickableItem(
    spec: PreferenceRowSpec,
    onClick: () -> Unit,
) {
    val rowMod = Modifier
        .fillMaxWidth()
        .heightIn(min = 76.dp)
        .clickable(onClick = onClick)
    val sub = spec.secondaryText
    ListItem(
        modifier = rowMod,
        supportingContent = if (sub != null) {
            { Text(text = sub) }
        } else {
            null
        },
        leadingContent = spec.icon,
        trailingContent = spec.trailing,
    ) {
        Text(text = spec.text)
    }
}

@Composable
fun PreferenceSwitchItem(
    spec: PreferenceRowSpec,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val rowMod = Modifier
        .fillMaxWidth()
        .heightIn(min = 76.dp)
        .toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
    val sub = spec.secondaryText
    ListItem(
        modifier = rowMod,
        supportingContent = if (sub != null) {
            { Text(text = sub) }
        } else {
            null
        },
        leadingContent = spec.icon,
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null)
        },
    ) {
        Text(text = spec.text)
    }
}

@Composable
fun <T> PreferenceList(
    spec: PreferenceSelectSpec<T>,
    onSubmit: (T) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    val selectedLabel = spec.options
        .firstOrNull { it.first == spec.currentValue }
        ?.second
        .orEmpty()
    val rowSpec = PreferenceRowSpec(
        text = spec.text,
        secondaryText = selectedLabel,
        icon = spec.icon,
    )
    PreferenceClickableItem(spec = rowSpec) { dialogOpen = true }
    if (dialogOpen) {
        SingleChoiceDialog(
            spec = spec,
            onSelect = { chosen ->
                onSubmit(chosen)
                dialogOpen = false
            },
            onDismiss = { dialogOpen = false },
        )
    }
}

@Composable
fun <T> SingleChoiceDialog(
    spec: PreferenceSelectSpec<T>,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val alertSpec = AppAlertSpec(
        title = spec.text,
        dismissAction = DialogAction(label = "取消", onClick = onDismiss),
        contentPadding = PaddingValues(vertical = 2.dp),
    )
    AppAlertDialog(alertSpec) {
        val listMod = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .selectableGroup()
        Column(modifier = listMod) {
            for ((optionKey, optionTitle) in spec.options) {
                ChoiceOptionRow(
                    title = optionTitle,
                    selected = optionKey == spec.currentValue,
                    onClick = { onSelect(optionKey) },
                )
            }
        }
    }
}

@Composable
private fun ChoiceOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val itemMod = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onClick,
        )
        .padding(horizontal = 22.dp, vertical = 10.dp)
    val textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp)
    Row(
        modifier = itemMod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = title,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
