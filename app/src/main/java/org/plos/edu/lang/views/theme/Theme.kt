package org.plos.edu.lang.views.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import org.plos.edu.lang.engine.ReviewGrade

@Immutable
data class GreenStudyStops(
    val mastered: Color,
    val bright: Color,
    val pale: Color,
    val pending: Color,
)

@Immutable
data class WarmStudyStops(
    val paleOrange: Color,
    val orange: Color,
    val red: Color,
)

@Immutable
data class PillTextStops(
    val mastered: Color,
    val learning: Color,
)

@Immutable
data class GradeTextStops(
    val again: Color,
    val hard: Color,
    val good: Color,
    val easy: Color,
)

@Immutable
data class StudyColors(
    val green: GreenStudyStops,
    val warm: WarmStudyStops,
    val pillText: PillTextStops,
    val gradeText: GradeTextStops,
) {
    fun learningColorFor(ratio: Float): Color {
        val clamped = ratio.coerceIn(0f, 1f)
        return when {
            clamped < 0.20f -> warm.red
            clamped < 0.40f -> warm.orange
            clamped < 0.60f -> warm.paleOrange
            clamped < 0.80f -> green.pale
            else -> green.bright
        }
    }

    fun gradeColor(grade: ReviewGrade): Color = when (grade) {
        ReviewGrade.AGAIN -> warm.red
        ReviewGrade.HARD -> warm.paleOrange
        ReviewGrade.GOOD -> green.pale
        ReviewGrade.EASY -> green.mastered
    }

    fun gradeTextColor(grade: ReviewGrade): Color = when (grade) {
        ReviewGrade.AGAIN -> gradeText.again
        ReviewGrade.HARD -> gradeText.hard
        ReviewGrade.GOOD -> gradeText.good
        ReviewGrade.EASY -> gradeText.easy
    }

    fun stabilityBucketColor(index: Int): Color = when (index) {
        0 -> warm.red
        1 -> warm.orange
        2 -> warm.paleOrange
        3 -> green.bright
        else -> green.mastered
    }
}

private val LightStudyPalette = StudyColors(
    green = GreenStudyStops(
        mastered = Color(0xFF206B3E),
        bright = Color(0xFF338735),
        pale = Color(0xFF52834D),
        pending = Color(0xFFE3E2E6),
    ),
    warm = WarmStudyStops(
        paleOrange = Color(0xFFED9915),
        orange = Color(0xFFD04B00),
        red = Color(0xFFC6332D),
    ),
    pillText = PillTextStops(
        mastered = Color(0xFF126135),
        learning = Color(0xFF1E6121),
    ),
    gradeText = GradeTextStops(
        again = Color(0xFF903831),
        hard = Color(0xFF794B00),
        good = Color(0xFF305F2D),
        easy = Color(0xFF126135),
    ),
)

private val DarkStudyPalette = StudyColors(
    green = GreenStudyStops(
        mastered = Color(0xFF42A968),
        bright = Color(0xFF5CB058),
        pale = Color(0xFF85B87D),
        pending = Color(0xFFC6C6CA),
    ),
    warm = WarmStudyStops(
        paleOrange = Color(0xFFFFB960),
        orange = Color(0xFFE27040),
        red = Color(0xFFE94D43),
    ),
    pillText = PillTextStops(
        mastered = Color(0xFF76DB95),
        learning = Color(0xFF8AD983),
    ),
    gradeText = GradeTextStops(
        again = Color(0xFFFFB4AB),
        hard = Color(0xFFFFB960),
        good = Color(0xFF9FD496),
        easy = Color(0xFF76DB95),
    ),
)

private val LocalStudyColors = staticCompositionLocalOf { DarkStudyPalette }

val MaterialTheme.studyColors: StudyColors
    @Composable
    @ReadOnlyComposable
    get() = LocalStudyColors.current

private val FallbackLightScheme = lightColorScheme()
private val FallbackDarkScheme = darkColorScheme()

@Composable
fun ApplicationTheme(theme: String?, content: @Composable () -> Unit) {
    val isDark = when (theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val useDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        useDynamic && isDark -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        isDark -> FallbackDarkScheme
        else -> FallbackLightScheme
    }
    val studyPalette = if (isDark) DarkStudyPalette else LightStudyPalette
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = context as? Activity ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(
                activity.window,
                view,
            )
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }
    CompositionLocalProvider(LocalStudyColors provides studyPalette) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
