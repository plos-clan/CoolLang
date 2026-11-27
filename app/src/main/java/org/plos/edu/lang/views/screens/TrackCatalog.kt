package org.plos.edu.lang.views.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.plos.edu.lang.engine.MasterySnapshot
import org.plos.edu.lang.engine.StudyTrack
import org.plos.edu.lang.engine.TrackId
import org.plos.edu.lang.views.model.MainViewModel
import org.plos.edu.lang.views.model.TrackModalState
import org.plos.edu.lang.views.widgets.AppIcon
import org.plos.edu.lang.views.widgets.COMPACT_TOP_BAR_HEIGHT
import org.plos.edu.lang.views.widgets.CompactSearchBar
import org.plos.edu.lang.views.widgets.ObserveNoticeSnackbar
import org.plos.edu.lang.views.widgets.SymbolIcon
import org.plos.edu.lang.views.widgets.TopBarTitle

private data class CategoryGroup(
    val category: String,
    val books: List<TrackStatSummary>,
) {
    companion object {
        fun from(
            tracks: List<StudyTrack>,
            mastery: MasterySnapshot,
            rawQuery: String,
        ): List<CategoryGroup> {
            val q = rawQuery.trim().lowercase()
            val filtered = tracks.filter { matchesQuery(it, q) }
            val map = LinkedHashMap<String, MutableList<TrackStatSummary>>()
            for (tr in filtered) {
                val stat = TrackStatSummary.evaluate(tr, mastery)
                map.getOrPut(tr.category) { ArrayList() }.add(stat)
            }
            return map.map { (cat, list) -> CategoryGroup(cat, list) }
        }

        private fun matchesQuery(track: StudyTrack, q: String): Boolean {
            if (q.isEmpty()) return true
            return track.title.lowercase().contains(q) ||
                track.category.lowercase().contains(q) ||
                track.summary.lowercase().contains(q)
        }
    }
}

private data class CatalogListSpec(
    val groups: List<CategoryGroup>,
    val activeTrackId: TrackId,
    val expandedCategories: Set<String>,
    val searching: Boolean,
) {
    fun isExpanded(category: String): Boolean =
        searching || category in expandedCategories
}

@Composable
fun TrackCatalog(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val workspace = state.workspace ?: return
    val snackbarHost = remember { SnackbarHostState() }
    ObserveNoticeSnackbar(state.activeNotice, snackbarHost, viewModel)
    var query by rememberSaveable { mutableStateOf("") }
    val activeCategory = workspace.activeTrack.category
    val langKey = workspace.activeProfile.code.value
    var expandedCategories by rememberSaveable(langKey) {
        mutableStateOf(setOf(activeCategory, StudyTrack.CUSTOM_CATEGORY))
    }
    val groups = remember(workspace.tracks, workspace.mastery, query) {
        CategoryGroup.from(workspace.tracks, workspace.mastery, query)
    }
    val listSpec = CatalogListSpec(
        groups = groups,
        activeTrackId = workspace.activeTrack.id,
        expandedCategories = expandedCategories,
        searching = query.isNotBlank(),
    )
    val toggleCategory: (String) -> Unit = { cat ->
        if (!listSpec.searching) {
            expandedCategories = if (cat in expandedCategories) {
                expandedCategories - cat
            } else {
                expandedCategories + cat
            }
        }
    }
    val onCreate = {
        val modal = TrackModalState.Create(allowTextMode = true)
        viewModel.customTracks.open(modal)
    }
    Scaffold(
        topBar = { CatalogTopBar(onBack, onCreate) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { innerPadding ->
        val rootMod = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        Column(modifier = rootMod) {
            CompactSearchBar(
                query = query,
                placeholder = "搜索学段考试或词书名称",
                autoFocus = false,
                onQueryChange = { query = it },
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            CatalogListPane(
                spec = listSpec,
                onToggleCategory = toggleCategory,
                viewModel = viewModel,
                onBack = onBack,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogTopBar(
    onBack: () -> Unit,
    onCreateTrack: () -> Unit,
) {
    TopAppBar(
        expandedHeight = COMPACT_TOP_BAR_HEIGHT,
        title = { TopBarTitle("选择学习词书") },
        navigationIcon = {
            IconButton(onClick = onBack) { SymbolIcon(AppIcon.BACK) }
        },
        actions = {
            IconButton(onClick = onCreateTrack) { SymbolIcon(AppIcon.ADD) }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogListPane(
    spec: CatalogListSpec,
    onToggleCategory: (String) -> Unit,
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    if (spec.groups.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "未找到匹配的词书",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        for (group in spec.groups) {
            val expanded = spec.isExpanded(group.category)
            stickyHeader(key = "cat_${group.category}") {
                CategorySectionHeader(
                    category = group.category,
                    bookCount = group.books.size,
                    expanded = expanded,
                    onToggle = { onToggleCategory(group.category) },
                )
            }
            if (expanded) {
                categoryBookItems(group, spec.activeTrackId, viewModel, onBack)
            }
        }
    }
}

private fun LazyListScope.categoryBookItems(
    group: CategoryGroup,
    activeTrackId: TrackId,
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    items(items = group.books, key = { it.track.id.value }) { item ->
        val cardSpec = BookCardSpec(item, item.track.id == activeTrackId)
        TrackBookCard(cardSpec, viewModel, onBack)
    }
}

@Composable
private fun CategorySectionHeader(
    category: String,
    bookCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val chevron = if (expanded) AppIcon.EXPAND else AppIcon.COLLAPSED
    val rowMod = Modifier
        .fillMaxWidth()
        .background(scheme.surfaceContainerLow)
        .clickable(onClick = onToggle)
        .padding(horizontal = 16.dp, vertical = 11.dp)
    Column {
        Row(
            modifier = rowMod,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SymbolIcon(icon = chevron, tint = scheme.primary)
                Text(
                    text = category,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "$bookCount 本词书",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = scheme.outlineVariant)
    }
}
