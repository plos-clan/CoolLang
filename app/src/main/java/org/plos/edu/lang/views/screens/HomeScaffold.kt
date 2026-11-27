package org.plos.edu.lang.views.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.plos.edu.lang.views.AppRoute
import org.plos.edu.lang.views.HomeTab
import org.plos.edu.lang.views.model.ActiveSheet
import org.plos.edu.lang.views.model.AppUiState
import org.plos.edu.lang.views.model.MainViewModel
import org.plos.edu.lang.views.model.PassageEditorState
import org.plos.edu.lang.views.widgets.AppIcon
import org.plos.edu.lang.views.widgets.COMPACT_TOP_BAR_HEIGHT
import org.plos.edu.lang.views.widgets.ObserveNoticeSnackbar
import org.plos.edu.lang.views.widgets.SymbolIcon
import org.plos.edu.lang.views.widgets.TopBarTitle

@Composable
fun HomeScaffold(
    activeTab: HomeTab,
    onSelectTab: (HomeTab) -> Unit,
    onNavigate: (AppRoute) -> Unit,
    viewModel: MainViewModel,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val workspace = uiState.workspace
    val snackbarHost = remember { SnackbarHostState() }
    ObserveNoticeSnackbar(uiState.activeNotice, snackbarHost, viewModel)
    val remainingCards = workspace?.sessionQueue?.size ?: 0
    Scaffold(
        topBar = { HomeTopBar(activeTab, uiState, onNavigate, viewModel) },
        bottomBar = { HomeBottomBar(activeTab, remainingCards, onSelectTab) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { innerPadding ->
        val bodyMod = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        Box(modifier = bodyMod) {
            HomeTabBody(activeTab, onSelectTab, onNavigate, viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    activeTab: HomeTab,
    uiState: AppUiState,
    onNavigate: (AppRoute) -> Unit,
    viewModel: MainViewModel,
) {
    val langName = uiState.workspace?.activeProfile?.localizedName ?: "CoolLang"
    TopAppBar(
        expandedHeight = COMPACT_TOP_BAR_HEIGHT,
        title = { TopBarTitle("$langName · ${activeTab.label}") },
        actions = {
            HomeTopBarActions(activeTab, uiState, onNavigate, viewModel)
        },
    )
}

@Composable
private fun HomeBottomBar(
    activeTab: HomeTab,
    remainingCards: Int,
    onSelectTab: (HomeTab) -> Unit,
) {
    NavigationBar {
        for (tab in HomeTab.entries) {
            NavigationBarItem(
                selected = tab == activeTab,
                onClick = { onSelectTab(tab) },
                icon = {
                    val count = if (tab == HomeTab.STUDY) remainingCards else 0
                    StudyTabIcon(tab.icon, count)
                },
                label = { Text(text = tab.label) },
            )
        }
    }
}

@Composable
private fun StudyTabIcon(
    icon: AppIcon,
    remainingCards: Int,
) {
    if (remainingCards <= 0) {
        SymbolIcon(icon)
        return
    }
    val scheme = MaterialTheme.colorScheme
    val badgeText = if (remainingCards > 999) "999+" else "$remainingCards"
    val badgeMod = Modifier.offset(x = 10.dp, y = (-2).dp)
    BadgedBox(
        badge = {
            Badge(
                modifier = badgeMod,
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary,
            ) {
                Text(text = badgeText)
            }
        },
    ) {
        SymbolIcon(icon)
    }
}

@Composable
private fun HomeTabBody(
    activeTab: HomeTab,
    onSelectTab: (HomeTab) -> Unit,
    onNavigate: (AppRoute) -> Unit,
    viewModel: MainViewModel,
) {
    when (activeTab) {
        HomeTab.STUDY -> {
            val callbacks = ReviewNavCallbacks(
                onOpenImmerse = { onSelectTab(HomeTab.IMMERSE) },
                onOpenTodayLearned = { onNavigate(AppRoute.TodayLearned) },
                onOpenTrackCatalog = { onNavigate(AppRoute.TrackCatalog) },
            )
            Review(viewModel, callbacks)
        }
        HomeTab.IMMERSE -> {
            Immerse(viewModel) { pid, lineIdx ->
                onNavigate(AppRoute.Reader(pid.value, lineIdx))
            }
        }
        HomeTab.INSIGHTS -> Insights(viewModel)
        HomeTab.LEXICON -> Lexicon(viewModel)
    }
}

@Composable
private fun HomeTopBarActions(
    activeTab: HomeTab,
    uiState: AppUiState,
    onNavigate: (AppRoute) -> Unit,
    viewModel: MainViewModel,
) {
    val workspace = uiState.workspace
    when (activeTab) {
        HomeTab.STUDY -> {
            if (workspace?.learnedToday?.isNotEmpty() == true) {
                IconButton(onClick = { onNavigate(AppRoute.TodayLearned) }) {
                    SymbolIcon(AppIcon.HISTORY)
                }
            }
        }
        HomeTab.IMMERSE -> {
            val searchIcon = if (uiState.passageFilter.searchOpen) {
                AppIcon.CLOSE
            } else {
                AppIcon.SEARCH
            }
            val toggleSearch = {
                viewModel.modifyPassageFilter { it.toggledSearch() }
            }
            val openCreate = {
                val st = PassageEditorState()
                viewModel.showSheet(ActiveSheet.PassageEditor(st))
            }
            IconButton(onClick = toggleSearch) { SymbolIcon(searchIcon) }
            IconButton(onClick = openCreate) { SymbolIcon(AppIcon.ADD) }
        }
        HomeTab.LEXICON -> {
            val searchIcon = if (uiState.lexiconFilter.searchOpen) {
                AppIcon.CLOSE
            } else {
                AppIcon.SEARCH
            }
            val toggleSearch = {
                viewModel.modifyLexiconFilter { it.toggledSearch() }
            }
            IconButton(onClick = toggleSearch) { SymbolIcon(searchIcon) }
            IconButton(onClick = { onNavigate(AppRoute.TrackCatalog) }) {
                SymbolIcon(AppIcon.SHELF)
            }
        }
        HomeTab.INSIGHTS -> Unit
    }
    IconButton(onClick = { onNavigate(AppRoute.Settings) }) {
        SymbolIcon(AppIcon.TUNE)
    }
}
