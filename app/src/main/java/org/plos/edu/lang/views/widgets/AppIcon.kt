package org.plos.edu.lang.views.widgets

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.plos.edu.lang.R

val COMPACT_TOP_BAR_HEIGHT: Dp = 52.dp

enum class AppIcon(@DrawableRes val resId: Int) {
    BOLT(R.drawable.ic_sym_bolt),
    STORIES(R.drawable.ic_sym_stories),
    INSIGHTS(R.drawable.ic_sym_insights),
    LEXICON(R.drawable.ic_sym_lexicon),
    SHELF(R.drawable.ic_sym_shelf),
    EXPAND(R.drawable.ic_sym_expand_more),
    COLLAPSED(R.drawable.ic_sym_chevron_right),
    SORT(R.drawable.ic_sym_sort),
    TUNE(R.drawable.ic_sym_tune),
    BACK(R.drawable.ic_sym_back),
    FAMILIAR(R.drawable.ic_sym_familiar),
    HISTORY(R.drawable.ic_sym_history),
    VOLUME(R.drawable.ic_sym_volume),
    PLAY(R.drawable.ic_sym_play),
    PAUSE(R.drawable.ic_sym_pause),
    ADD(R.drawable.ic_sym_add),
    SEARCH(R.drawable.ic_sym_search),
    CLOSE(R.drawable.ic_sym_close),
    CHECK(R.drawable.ic_sym_check),
    LANGUAGE(R.drawable.ic_sym_language),
    FOLDER(R.drawable.ic_sym_folder),
    THEME(R.drawable.ic_sym_theme),
    TODAY(R.drawable.ic_sym_today),
    BACKUP(R.drawable.ic_sym_backup),
    RESTORE(R.drawable.ic_sym_restore),
    SPARK(R.drawable.ic_sym_spark),
    BOOKMARK(R.drawable.ic_sym_bookmark),
    BOOKMARK_FILLED(R.drawable.ic_sym_bookmark_filled),
}

@Composable
fun SymbolIcon(
    icon: AppIcon,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(id = icon.resId),
        contentDescription = null,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun TopBarTitle(text: String) {
    val titleStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = 17.5.sp,
        lineHeight = 23.sp,
    )
    Text(
        text = text,
        style = titleStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
