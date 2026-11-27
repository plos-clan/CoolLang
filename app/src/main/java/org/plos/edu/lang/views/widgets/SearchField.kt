package org.plos.edu.lang.views.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun CompactSearchBar(
    query: String,
    placeholder: String = "搜索单词或中文释义",
    autoFocus: Boolean = true,
    onQueryChange: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val fieldState = rememberTextFieldState(initialText = query)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }.collect(onQueryChange)
    }
    LaunchedEffect(autoFocus) {
        if (autoFocus) focusRequester.requestFocus()
    }
    val outerMod = Modifier
        .fillMaxWidth()
        .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 6.dp)
        .height(38.dp)
    val rowMod = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp)
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = scheme.onSurface,
    )
    val currentText = fieldState.text
    val isEmpty = currentText.isEmpty()
    Surface(
        color = scheme.surfaceContainerHigh,
        shape = CircleShape,
        modifier = outerMod,
    ) {
        Row(
            modifier = rowMod,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SymbolIcon(
                icon = AppIcon.SEARCH,
                modifier = Modifier.size(18.dp),
                tint = scheme.onSurfaceVariant,
            )
            val fieldMod = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
            BasicTextField(
                state = fieldState,
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = textStyle,
                cursorBrush = SolidColor(scheme.primary),
                modifier = fieldMod,
                decorator = { inner ->
                    SearchDecoratorBox(isEmpty, placeholder, inner)
                },
            )
            if (currentText.isNotEmpty()) {
                ClearSearchButton { fieldState.clearText() }
            }
        }
    }
}

@Composable
private fun SearchDecoratorBox(
    showPlaceholder: Boolean,
    placeholder: String,
    innerTextField: @Composable () -> Unit,
) {
    Box(contentAlignment = Alignment.CenterStart) {
        if (showPlaceholder) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        innerTextField()
    }
}

@Composable
private fun ClearSearchButton(onClear: () -> Unit) {
    val clearMod = Modifier
        .size(22.dp)
        .clip(CircleShape)
        .clickable(onClick = onClear)
    Box(modifier = clearMod, contentAlignment = Alignment.Center) {
        SymbolIcon(
            icon = AppIcon.CLOSE,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
