package org.plos.edu.lang.views.widgets

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import org.plos.edu.lang.engine.MasterySnapshot
import org.plos.edu.lang.engine.MasteryStage
import org.plos.edu.lang.engine.MorphologyEngine
import org.plos.edu.lang.engine.RootId
import org.plos.edu.lang.engine.TokenSlice

enum class HighlightPolicy {
    FOCUS_ONLY,
    RECENTLY_LEARNED,
}

data class TokenTextSpec(
    val text: String,
    val style: TextStyle,
    val mastery: MasterySnapshot,
    val morphology: MorphologyEngine,
    val policy: HighlightPolicy,
    val focusWord: String = "",
    val focusRootId: RootId? = null,
)

@Composable
fun TokenizedText(
    spec: TokenTextSpec,
    onTapWord: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val slices = remember(spec.text, spec.mastery, spec.morphology) {
        spec.morphology.tokenizeLine(spec.text, spec.mastery)
    }
    val annotated = buildTokenAnnotatedString(slices, spec, scheme, onTapWord)
    Text(text = annotated, style = spec.style)
}

private fun buildTokenAnnotatedString(
    slices: List<TokenSlice>,
    spec: TokenTextSpec,
    scheme: ColorScheme,
    onTapWord: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    val normalizedFocus = stripCombining(spec.focusWord).trim().lowercase()
    for (slice in slices) {
        if (slice.lookupWord.isEmpty()) {
            append(slice.rawText)
        } else {
            val targetRoot = spec.focusRootId
            val isFocal = matchesFocalWord(slice, normalizedFocus, targetRoot)
            val spanStyle = styleForToken(slice, spec.policy, isFocal, scheme)
            val linkStyles = TextLinkStyles(style = spanStyle)
            val link = LinkAnnotation.Clickable(
                tag = slice.lookupWord,
                styles = linkStyles,
            ) {
                onTapWord(slice.rawText)
            }
            withLink(link) {
                append(slice.rawText)
            }
        }
    }
}

private fun stripCombining(raw: String): String {
    if (raw.isEmpty()) return ""
    val sb = StringBuilder(raw.length)
    for (ch in raw) {
        if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) {
            sb.append(ch)
        }
    }
    return sb.toString()
}

private fun matchesFocalWord(
    slice: TokenSlice,
    normalizedFocus: String,
    focusRootId: RootId?,
): Boolean {
    if (normalizedFocus.isNotEmpty() && slice.lookupWord == normalizedFocus) {
        return true
    }
    return focusRootId != null && slice.rootId == focusRootId
}

private fun styleForToken(
    slice: TokenSlice,
    policy: HighlightPolicy,
    isFocal: Boolean,
    scheme: ColorScheme,
): SpanStyle = when (policy) {
    HighlightPolicy.FOCUS_ONLY -> {
        if (isFocal) {
            SpanStyle(
                color = scheme.primary,
                fontWeight = FontWeight.Bold,
            )
        } else {
            SpanStyle(color = scheme.onSurface)
        }
    }
    HighlightPolicy.RECENTLY_LEARNED -> when (slice.stage) {
        MasteryStage.LEARNING -> SpanStyle(
            color = scheme.primary,
            fontWeight = FontWeight.Medium,
        )
        MasteryStage.UNSEEN -> SpanStyle(color = scheme.onSurface)
        MasteryStage.MASTERED -> SpanStyle(color = scheme.onSurface)
    }
}
