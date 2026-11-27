package org.plos.edu.lang.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.plos.edu.lang.repository.LanguageWorkspace
import org.plos.edu.lang.views.model.ActiveSheet
import org.plos.edu.lang.views.model.AppUiState
import org.plos.edu.lang.views.model.BatchSelectionState
import org.plos.edu.lang.views.model.BootPhase
import org.plos.edu.lang.views.model.MainViewModel
import org.plos.edu.lang.views.screens.PassageEditorDialog
import org.plos.edu.lang.views.theme.ApplicationTheme
import org.plos.edu.lang.views.widgets.CustomTrackModalHost
import org.plos.edu.lang.views.widgets.GrammarSheet
import org.plos.edu.lang.views.widgets.LoadingScreen
import org.plos.edu.lang.views.widgets.WordSheet

@Composable
fun AppUi(viewModel: MainViewModel = hiltViewModel()) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val themePref = uiState.workspace?.preferences?.appTheme
    ApplicationTheme(themePref) {
        when (val phase = uiState.phase) {
            BootPhase.Loading -> LoadingScreen()
            is BootPhase.Ready -> {
                MainAppShell(uiState, phase.workspace, viewModel)
            }
        }
    }
}

@Composable
private fun MainAppShell(
    uiState: AppUiState,
    workspace: LanguageWorkspace,
    viewModel: MainViewModel,
) {
    val backStack = remember { mutableStateListOf<AppRoute>(AppRoute.Home) }
    var activeTab by rememberSaveable { mutableStateOf(HomeTab.STUDY) }
    val switchTab: (HomeTab) -> Unit = { nextTab ->
        if (nextTab != activeTab) {
            viewModel.speech.stop()
            viewModel.modifyBatch { _, _ -> BatchSelectionState() }
        }
        activeTab = nextTab
    }
    Router(
        backStack = backStack,
        activeTab = activeTab,
        onSelectTab = switchTab,
        viewModel = viewModel,
    )
    when (val sheet = uiState.activeSheet) {
        is ActiveSheet.Word -> WordSheet(sheet.state, workspace, viewModel)
        is ActiveSheet.Grammar -> GrammarSheet(sheet.rule, viewModel)
        is ActiveSheet.PassageEditor -> {
            PassageEditorDialog(sheet.state, workspace, viewModel)
        }
        null -> Unit
    }
    val trackModal = uiState.trackModal
    if (trackModal != null) {
        CustomTrackModalHost(trackModal, workspace, viewModel.customTracks)
    }
}
