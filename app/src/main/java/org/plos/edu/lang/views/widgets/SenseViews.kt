package org.plos.edu.lang.views.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.plos.edu.lang.engine.PosSense
import org.plos.edu.lang.engine.RootId
import org.plos.edu.lang.engine.WordSenses
import org.plos.edu.lang.repository.LanguageWorkspace
import org.plos.edu.lang.views.model.MainViewModel
import org.plos.edu.lang.views.model.TrackModalState

private data class InlineSensesPayload(
    val text: AnnotatedString,
    val inlineMap: Map<String, InlineTextContent>,
)

@Composable
fun WordSensesBlock(
    senses: WordSenses,
    prominent: Boolean,
) {
    val entries = senses.entries
    if (entries.isEmpty()) return
    val textStyle = when {
        !prominent -> MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.Medium,
        )
        entries.size == 1 -> MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.SemiBold,
        )
        else -> MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
        )
    }
    val rowGap = if (prominent) 10.dp else 6.dp
    val pillTopPad = if (prominent) 4.dp else 2.dp
    Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
        for (item in entries) {
            SenseItemRow(item, textStyle, pillTopPad)
        }
    }
}

@Composable
private fun SenseItemRow(
    item: PosSense,
    textStyle: TextStyle,
    pillTopPad: Dp,
) {
    val scheme = MaterialTheme.colorScheme
    val pillMod = Modifier
        .padding(top = pillTopPad)
        .defaultMinSize(minWidth = 28.dp)
        .clip(MaterialTheme.shapes.extraSmall)
        .background(scheme.secondaryContainer)
        .padding(horizontal = 6.dp, vertical = 2.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (item.pos.isNotEmpty()) {
            Box(modifier = pillMod, contentAlignment = Alignment.Center) {
                Text(
                    text = item.pos,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSecondaryContainer,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = item.meaning,
            style = textStyle,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun InlineSensesText(
    senses: WordSenses,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (senses.entries.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val payload = remember(
        senses,
        scheme.secondaryContainer,
        scheme.onSecondaryContainer,
    ) {
        buildInlineSenses(senses, scheme)
    }
    Text(
        text = payload.text,
        inlineContent = payload.inlineMap,
        modifier = modifier,
        style = textStyle,
        color = textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun buildInlineSenses(
    senses: WordSenses,
    scheme: ColorScheme,
): InlineSensesPayload {
    val map = HashMap<String, InlineTextContent>(senses.entries.size)
    val annotated = buildAnnotatedString {
        for ((idx, item) in senses.entries.withIndex()) {
            if (idx > 0) append("  ")
            if (item.pos.isNotEmpty()) {
                val tagId = "p$idx"
                appendInlineContent(tagId, item.pos)
                append(" ")
                map[tagId] = createPosInlineBadge(item.pos, scheme)
            }
            append(item.meaning)
        }
    }
    return InlineSensesPayload(annotated, map)
}

private fun createPosInlineBadge(
    pos: String,
    scheme: ColorScheme,
): InlineTextContent {
    val fullChars = pos.count { it != '·' }
    val dots = pos.count { it == '·' }
    val widthSp = (fullChars * 11.5 + dots * 5.5 + 9.0).sp
    val placeholder = Placeholder(
        width = widthSp,
        height = 17.5.sp,
        placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
    )
    return InlineTextContent(placeholder) {
        val badgeMod = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(scheme.secondaryContainer)
        val badgeStyle = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(modifier = badgeMod, contentAlignment = Alignment.Center) {
            Text(
                text = pos,
                style = badgeStyle,
                color = scheme.onSecondaryContainer,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WordBookmarkButton(
    rootId: RootId,
    workspace: LanguageWorkspace,
    viewModel: MainViewModel,
    size: Dp = 36.dp,
) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val inAnyBook = workspace.customTracks.any { rootId in it.rootIds }
    val icon = if (inAnyBook) AppIcon.BOOKMARK_FILLED else AppIcon.BOOKMARK
    val bg = if (inAnyBook) {
        scheme.primaryContainer
    } else {
        scheme.surfaceContainerHigh
    }
    val fg = if (inAnyBook) {
        scheme.onPrimaryContainer
    } else {
        scheme.onSurfaceVariant
    }
    val onLongPress = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (workspace.customTracks.isEmpty()) {
            viewModel.customTracks.onTapBookmark(rootId)
        } else {
            val modal = TrackModalState.PickBooks(setOf(rootId))
            viewModel.customTracks.open(modal)
        }
    }
    val btnMod = Modifier
        .size(size)
        .clip(CircleShape)
        .background(bg)
        .combinedClickable(
            onClick = { viewModel.customTracks.onTapBookmark(rootId) },
            onLongClick = onLongPress,
        )
    Box(modifier = btnMod, contentAlignment = Alignment.Center) {
        SymbolIcon(icon = icon, modifier = Modifier.size(20.dp), tint = fg)
    }
}
