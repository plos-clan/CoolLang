package org.plos.edu.lang.views.screens

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.roundToInt
import org.plos.edu.lang.Constants
import org.plos.edu.lang.engine.PreferenceMutation
import org.plos.edu.lang.views.model.MainViewModel
import org.plos.edu.lang.views.widgets.AppIcon
import org.plos.edu.lang.views.widgets.COMPACT_TOP_BAR_HEIGHT
import org.plos.edu.lang.views.widgets.ObserveNoticeSnackbar
import org.plos.edu.lang.views.widgets.PreferenceClickableItem
import org.plos.edu.lang.views.widgets.PreferenceGroupTitle
import org.plos.edu.lang.views.widgets.PreferenceList
import org.plos.edu.lang.views.widgets.PreferenceRowSpec
import org.plos.edu.lang.views.widgets.PreferenceSelectSpec
import org.plos.edu.lang.views.widgets.PreferenceSwitchItem
import org.plos.edu.lang.views.widgets.SymbolIcon
import org.plos.edu.lang.views.widgets.TopBarTitle

data class SettingsNavCallbacks(
    val onBack: () -> Unit,
    val onOpenStudyPace: () -> Unit,
    val onOpenSpeech: () -> Unit,
)

private object UriTextIo {
    fun write(resolver: ContentResolver, uri: Uri, content: String) {
        resolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(content)
        }
    }

    fun read(resolver: ContentResolver, uri: Uri): String? =
        resolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            reader.readText()
        }
}

@Composable
fun Settings(
    viewModel: MainViewModel,
    nav: SettingsNavCallbacks,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val workspace = state.workspace ?: return
    val prefs = workspace.preferences
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    ObserveNoticeSnackbar(state.activeNotice, snackbarHost, viewModel)
    val jsonMime = "application/json"
    val openTypes = arrayOf(jsonMime, "*/*")
    val createDoc = ActivityResultContracts.CreateDocument(jsonMime)
    val exportLauncher = rememberLauncherForActivityResult(createDoc) { uri ->
        if (uri != null) {
            viewModel.exportProgress { encoded ->
                UriTextIo.write(context.contentResolver, uri, encoded)
            }
        }
    }
    val importProgressLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val raw = uri?.let { UriTextIo.read(context.contentResolver, it) }
        if (!raw.isNullOrBlank()) viewModel.importProgress(raw)
    }
    val importPackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val raw = uri?.let { UriTextIo.read(context.contentResolver, it) }
        if (!raw.isNullOrBlank()) viewModel.importLanguagePack(raw)
    }
    val langOptions = workspace.languages.map { profile ->
        profile.code to "${profile.localizedName} (${profile.targetName})"
    }
    val themeOptions = listOf(
        "default" to "跟随系统",
        "light" to "浅色模式",
        "dark" to "深色模式",
    )
    val retPct = (prefs.retention.value * 100.0).roundToInt()
    val totalSummary = if (prefs.dailyTotalCap.isUnlimited) {
        "总词不限"
    } else {
        "总词 ${prefs.dailyTotalCap.rawLimit}"
    }
    val activeLang = workspace.activeProfile.code
    val orderLabel = prefs.orderFor(activeLang, workspace.activeTrack).label
    val paceSummary =
        "新词 ${prefs.dailyNewLimit} · $totalSummary · 留存 $retPct% · $orderLabel"
    val activeEngineLabel = state.ttsEngines
        .firstOrNull { it.isActive }
        ?.label
        ?: "系统语音引擎"
    val rateText = String.format(Locale.US, "%.2fx", prefs.speechRate)
    val speechSummary = "$activeEngineLabel · 语速 $rateText"
    val iconPad = Modifier.padding(8.dp)
    val chevron: @Composable () -> Unit = {
        SymbolIcon(
            icon = AppIcon.COLLAPSED,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Scaffold(
        topBar = { SettingsTopBar(nav.onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { innerPadding ->
        val scrollMod = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
        Column(modifier = scrollMod) {
            PreferenceGroupTitle("学习与记忆")
            val langSpec = PreferenceSelectSpec(
                text = "学习语言",
                currentValue = workspace.activeProfile.code,
                options = langOptions,
                icon = { SymbolIcon(AppIcon.LANGUAGE, iconPad) },
            )
            PreferenceList(langSpec, viewModel::switchLanguage)
            val paceRow = PreferenceRowSpec(
                text = "学习设置",
                secondaryText = paceSummary,
                icon = { SymbolIcon(AppIcon.TODAY, iconPad) },
                trailing = chevron,
            )
            PreferenceClickableItem(paceRow, nav.onOpenStudyPace)
            HorizontalDivider()
            PreferenceGroupTitle("发音与朗读")
            val autoPlayRow = PreferenceRowSpec(
                text = "出卡自动朗读",
                secondaryText = "展示新词卡或复习卡时自动播放发音",
                icon = { SymbolIcon(AppIcon.VOLUME, iconPad) },
            )
            PreferenceSwitchItem(
                spec = autoPlayRow,
                checked = prefs.autoPlayAudio,
                onCheckedChange = { enabled ->
                    val mut = PreferenceMutation.AutoPlayAudio(enabled)
                    viewModel.applyPreference(mut)
                },
            )
            val speechRow = PreferenceRowSpec(
                text = "语音设置",
                secondaryText = speechSummary,
                icon = { SymbolIcon(AppIcon.SPARK, iconPad) },
                trailing = chevron,
            )
            PreferenceClickableItem(speechRow, nav.onOpenSpeech)
            HorizontalDivider()
            PreferenceGroupTitle("界面外观")
            val themeSpec = PreferenceSelectSpec(
                text = "深色主题",
                currentValue = prefs.appTheme,
                options = themeOptions,
                icon = { SymbolIcon(AppIcon.THEME, iconPad) },
            )
            PreferenceList(themeSpec) { chosen ->
                viewModel.applyPreference(PreferenceMutation.Theme(chosen))
            }
            HorizontalDivider()
            PreferenceGroupTitle("词库与备份")
            val packRow = PreferenceRowSpec(
                text = "导入语言包",
                secondaryText = "从本地 JSON 文件载入自定义词库与阅读语料",
                icon = { SymbolIcon(AppIcon.FOLDER, iconPad) },
            )
            PreferenceClickableItem(packRow) {
                importPackLauncher.launch(openTypes)
            }
            val exportRow = PreferenceRowSpec(
                text = "导出学习备份",
                secondaryText = "将记忆曲线进度、复习记录与自选内容保存为文件",
                icon = { SymbolIcon(AppIcon.BACKUP, iconPad) },
            )
            PreferenceClickableItem(exportRow) {
                exportLauncher.launch(Constants.BACKUP_FILE_NAME)
            }
            val restoreRow = PreferenceRowSpec(
                text = "恢复学习备份",
                secondaryText = "从备份文件合并恢复学习状态与记录",
                icon = { SymbolIcon(AppIcon.RESTORE, iconPad) },
            )
            PreferenceClickableItem(restoreRow) {
                importProgressLauncher.launch(openTypes)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    TopAppBar(
        expandedHeight = COMPACT_TOP_BAR_HEIGHT,
        title = { TopBarTitle("设置") },
        navigationIcon = {
            IconButton(onClick = onBack) { SymbolIcon(AppIcon.BACK) }
        },
    )
}
