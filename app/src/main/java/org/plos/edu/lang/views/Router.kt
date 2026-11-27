package org.plos.edu.lang.views

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import org.plos.edu.lang.views.model.MainViewModel
import org.plos.edu.lang.views.screens.HomeScaffold
import org.plos.edu.lang.views.screens.PassageReader
import org.plos.edu.lang.views.screens.Settings
import org.plos.edu.lang.views.screens.SettingsNavCallbacks
import org.plos.edu.lang.views.screens.SpeechSettings
import org.plos.edu.lang.views.screens.StudyPaceSettings
import org.plos.edu.lang.views.screens.TodayLearned
import org.plos.edu.lang.views.screens.TrackCatalog
import org.plos.edu.lang.views.widgets.AppIcon

enum class HomeTab(
    val label: String,
    val icon: AppIcon,
) {
    STUDY("学习", AppIcon.BOLT),
    IMMERSE("阅读", AppIcon.STORIES),
    INSIGHTS("进度", AppIcon.INSIGHTS),
    LEXICON("词书", AppIcon.LEXICON),
}

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object Home : AppRoute

    @Serializable
    data class Reader(
        val passageId: String,
        val initialLineIndex: Int = -1,
    ) : AppRoute

    @Serializable
    data object TrackCatalog : AppRoute

    @Serializable
    data object TodayLearned : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object StudyPaceSettings : AppRoute

    @Serializable
    data object SpeechSettings : AppRoute
}

@Composable
fun Router(
    backStack: SnapshotStateList<AppRoute>,
    activeTab: HomeTab,
    onSelectTab: (HomeTab) -> Unit,
    viewModel: MainViewModel,
) {
    val popBack: () -> Unit = {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }
    val onNavigate: (AppRoute) -> Unit = { route -> backStack.add(route) }
    val settingsNav = SettingsNavCallbacks(
        onBack = popBack,
        onOpenStudyPace = { onNavigate(AppRoute.StudyPaceSettings) },
        onOpenSpeech = { onNavigate(AppRoute.SpeechSettings) },
    )
    NavDisplay(
        backStack = backStack,
        onBack = popBack,
        transitionSpec = { RouteTransitions.push() },
        popTransitionSpec = { RouteTransitions.pop() },
        predictivePopTransitionSpec = { RouteTransitions.pop() },
        entryProvider = entryProvider {
            entry<AppRoute.Home> {
                HomeScaffold(activeTab, onSelectTab, onNavigate, viewModel)
            }
            entry<AppRoute.Reader> { route ->
                val pid = route.passageId
                val idx = route.initialLineIndex
                OverlayRouteSurface {
                    PassageReader(pid, idx, viewModel, popBack)
                }
            }
            entry<AppRoute.TrackCatalog> {
                OverlayRouteSurface {
                    TrackCatalog(viewModel = viewModel, onBack = popBack)
                }
            }
            entry<AppRoute.TodayLearned> {
                OverlayRouteSurface {
                    TodayLearned(viewModel = viewModel, onBack = popBack)
                }
            }
            entry<AppRoute.Settings> {
                OverlayRouteSurface {
                    Settings(viewModel = viewModel, nav = settingsNav)
                }
            }
            entry<AppRoute.StudyPaceSettings> {
                OverlayRouteSurface {
                    StudyPaceSettings(viewModel = viewModel, onBack = popBack)
                }
            }
            entry<AppRoute.SpeechSettings> {
                OverlayRouteSurface {
                    SpeechSettings(viewModel = viewModel, onBack = popBack)
                }
            }
        },
    )
}

@Composable
private fun OverlayRouteSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shadowElevation = 6.dp,
        content = content,
    )
}

private object RouteTransitions {
    private val slideSpec = tween<IntOffset>(
        durationMillis = 280,
        easing = FastOutSlowInEasing,
    )

    fun push(): ContentTransform {
        val enter = slideInHorizontally(slideSpec) { width -> width }
        val exit = slideOutHorizontally(slideSpec) { width -> -(width / 4) }
        return ContentTransform(
            targetContentEnter = enter,
            initialContentExit = exit,
            targetContentZIndex = 1f,
        )
    }

    fun pop(): ContentTransform {
        val enter = slideInHorizontally(slideSpec) { width -> -(width / 4) }
        val exit = slideOutHorizontally(slideSpec) { width -> width }
        return ContentTransform(
            targetContentEnter = enter,
            initialContentExit = exit,
            targetContentZIndex = -1f,
        )
    }
}
