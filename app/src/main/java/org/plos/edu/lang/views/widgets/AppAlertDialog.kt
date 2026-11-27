package org.plos.edu.lang.views.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DialogAction(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

data class AppAlertSpec(
    val title: String,
    val dismissAction: DialogAction,
    val confirmAction: DialogAction? = null,
    val contentPadding: PaddingValues = PaddingValues(
        horizontal = 22.dp,
        vertical = 6.dp,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppAlertDialog(
    spec: AppAlertSpec,
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicAlertDialog(onDismissRequest = spec.dismissAction.onClick) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            AlertSurfaceBody(spec, content)
        }
    }
}

@Composable
private fun AlertSurfaceBody(
    spec: AppAlertSpec,
    content: @Composable ColumnScope.() -> Unit,
) {
    val titleMod = Modifier.padding(
        start = 22.dp,
        end = 22.dp,
        top = 20.dp,
        bottom = 10.dp,
    )
    val titleStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val footerMod = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = spec.title,
            modifier = titleMod,
            style = titleStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val bodyMod = Modifier
            .fillMaxWidth()
            .weight(weight = 1f, fill = false)
            .padding(spec.contentPadding)
        Column(modifier = bodyMod, content = content)
        Row(
            modifier = footerMod,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        ) {
            CompactDialogButton(spec.dismissAction)
            val confirm = spec.confirmAction
            if (confirm != null) {
                CompactDialogButton(confirm)
            }
        }
    }
}

@Composable
private fun CompactDialogButton(action: DialogAction) {
    val btnPad = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
    TextButton(
        onClick = action.onClick,
        enabled = action.enabled,
        modifier = Modifier.height(38.dp),
        contentPadding = btnPad,
    ) {
        Text(text = action.label)
    }
}
