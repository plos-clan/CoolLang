package org.plos.edu.lang.views.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.roundToInt
import org.plos.edu.lang.engine.LanguageProfile
import org.plos.edu.lang.engine.PreferenceMutation
import org.plos.edu.lang.modules.SpeechStatus
import org.plos.edu.lang.modules.TtsEngineOption
import org.plos.edu.lang.views.model.MainViewModel
import org.plos.edu.lang.views.theme.studyColors
import org.plos.edu.lang.views.widgets.AppIcon
import org.plos.edu.lang.views.widgets.COMPACT_TOP_BAR_HEIGHT
import org.plos.edu.lang.views.widgets.SymbolIcon
import org.plos.edu.lang.views.widgets.TopBarTitle

private enum class RecommendedEngine(
    val title: String,
    val packageName: String,
    val homepageUrl: String,
) {
    EDGE_TTS(
        title = "Edge TTS Engine",
        packageName = "top.initsnow.edge_tts_android",
        homepageUrl = "https://github.com/Initsnow/edge-tts-android",
    ),
    HAYAI_TTS(
        title = "HayaiTTS",
        packageName = "dev.ahmedmohamed.hayaitts",
        homepageUrl = "https://github.com/HayaiApp/HayaiTTS",
    ),
    VOX_SHERPA(
        title = "VoxSherpa TTS",
        packageName = "com.CodeBySonu.VoxSherpa",
        homepageUrl = "https://github.com/CodeBySonu95/VoxSherpa-TTS",
    );

    val summary: String
        get() = when (this) {
            EDGE_TTS ->
                "调用微软 Edge 在线朗读接口并经 Rust 原生解码输出，免下载本地语音包，即装即用"
            HAYAI_TTS ->
                "基于 Sherpa-ONNX 本地离线推理，内置 Piper、Kokoro 等 600 余款模型目录，支持试听下载与音色克隆"
            VOX_SHERPA ->
                "集成 Kokoro-82M 与 Piper 双离线内核，自带多角色对话工作台与音频导出，兼顾系统朗读与本地生成"
        }
}

@Composable
fun SpeechSettings(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val workspace = state.workspace ?: return
    val prefs = workspace.preferences
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.speech.refreshEnginesAndAudition(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val onRateCommit: (Float) -> Unit = { rate ->
        viewModel.applyPreference(PreferenceMutation.SpeechRate(rate))
    }
    val onSelectEngine: (String) -> Unit = { pkg ->
        viewModel.applyPreference(PreferenceMutation.TtsEngine(pkg))
    }
    Scaffold(topBar = { SpeechTopBar(onBack) }) { innerPadding ->
        val scrollMod = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
        Column(
            modifier = scrollMod,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AuditionStatusCard(
                profile = workspace.activeProfile,
                status = state.speechStatus,
                onAudition = {
                    viewModel.speech.refreshEnginesAndAudition(true)
                },
            )
            SpeechRateCard(prefs.speechRate, onRateCommit)
            InstalledEnginesCard(state.ttsEngines, context, onSelectEngine)
            RecommendedEnginesCard(state.ttsEngines, context)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeechTopBar(onBack: () -> Unit) {
    TopAppBar(
        expandedHeight = COMPACT_TOP_BAR_HEIGHT,
        title = { TopBarTitle("语音设置") },
        navigationIcon = {
            IconButton(onClick = onBack) { SymbolIcon(AppIcon.BACK) }
        },
    )
}

@Composable
private fun AuditionStatusCard(
    profile: LanguageProfile,
    status: SpeechStatus,
    onAudition: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val study = MaterialTheme.studyColors
    val innerMod = Modifier.fillMaxWidth().padding(16.dp)
    val statusColor =
        if (status.ready) study.pillText.mastered else scheme.error
    val btnPad = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = innerMod,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${profile.localizedName} (${profile.ttsLocaleTag})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = status.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                )
            }
            FilledTonalButton(onClick = onAudition, contentPadding = btnPad) {
                SymbolIcon(AppIcon.VOLUME, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "试听")
            }
        }
    }
}

@Composable
private fun SpeechRateCard(
    savedRate: Float,
    onCommitRate: (Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var draftRate by remember(savedRate) { mutableFloatStateOf(savedRate) }
    val formatted = String.format(Locale.US, "%.2fx", draftRate)
    val innerMod = Modifier.fillMaxWidth().padding(16.dp)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = innerMod,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "朗读语速",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
                )
            }
            Slider(
                value = draftRate,
                onValueChange = { raw ->
                    draftRate = (raw * 20f).roundToInt() / 20f
                },
                onValueChangeFinished = { onCommitRate(draftRate) },
                valueRange = 0.65f..1.25f,
            )
            SpeechRateScaleFooter()
        }
    }
}

@Composable
private fun SpeechRateScaleFooter() {
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant
    val small = MaterialTheme.typography.labelSmall
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = "0.65x 慢速跟读", style = small, color = subColor)
        Text(text = "0.95x 自然清晰", style = small, color = subColor)
        Text(text = "1.25x 日常快速", style = small, color = subColor)
    }
}

@Composable
private fun InstalledEnginesCard(
    engines: List<TtsEngineOption>,
    context: Context,
    onSelectEngine: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dividerCol = scheme.outlineVariant.copy(alpha = 0.5f)
    val headerPad = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 4.dp)
    val openSysSettings = {
        val intent = Intent("com.android.settings.TTS_SETTINGS")
        runCatching { context.startActivity(intent) }
        Unit
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            Row(
                modifier = headerPad,
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "已安装语音合成引擎",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = openSysSettings) {
                    Text(text = "系统语音设置")
                }
            }
            if (engines.isEmpty()) {
                Text(
                    text = "未检测到已安装的语音引擎",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                for ((idx, item) in engines.withIndex()) {
                    if (idx > 0) HorizontalDivider(color = dividerCol)
                    InstalledEngineRow(item, context, onSelectEngine)
                }
            }
        }
    }
}

@Composable
private fun InstalledEngineRow(
    item: TtsEngineOption,
    context: Context,
    onSelectEngine: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val launchIntent = remember(item.packageName) {
        context.packageManager.getLaunchIntentForPackage(item.packageName)
    }
    val titleWeight =
        if (item.isActive) FontWeight.SemiBold else FontWeight.Normal
    val rowMod = Modifier
        .fillMaxWidth()
        .selectable(
            selected = item.isActive,
            role = Role.RadioButton,
            onClick = { onSelectEngine(item.packageName) },
        )
        .padding(horizontal = 16.dp, vertical = 12.dp)
    Row(
        modifier = rowMod,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(selected = item.isActive, onClick = null)
            Column {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = titleWeight,
                )
                Text(
                    text = item.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (launchIntent != null) {
            val onOpen = {
                runCatching { context.startActivity(launchIntent) }
                Unit
            }
            val openMod = Modifier
                .clip(CircleShape)
                .background(scheme.surfaceContainerHigh)
                .clickable(onClick = onOpen)
                .padding(horizontal = 10.dp, vertical = 4.dp)
            Text(
                text = "打开应用",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.primary,
                modifier = openMod,
            )
        }
    }
}

@Composable
private fun RecommendedEnginesCard(
    installed: List<TtsEngineOption>,
    context: Context,
) {
    val scheme = MaterialTheme.colorScheme
    val dividerCol = scheme.outlineVariant.copy(alpha = 0.5f)
    val installedPkgs = remember(installed) {
        installed.mapTo(HashSet()) { it.packageName }
    }
    val headerMod = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = headerMod,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "第三方引擎推荐",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "点击条目可访问官方开源主页，安装后返回本页即可切换使用",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            for (spec in RecommendedEngine.entries) {
                HorizontalDivider(color = dividerCol)
                val isInstalled = spec.packageName in installedPkgs
                RecommendedEngineItem(spec, isInstalled, context)
            }
        }
    }
}

@Composable
private fun RecommendedEngineItem(
    spec: RecommendedEngine,
    isInstalled: Boolean,
    context: Context,
) {
    val scheme = MaterialTheme.colorScheme
    val openWebpage = {
        val intent = Intent(Intent.ACTION_VIEW, spec.homepageUrl.toUri())
        runCatching { context.startActivity(intent) }
        Unit
    }
    val itemMod = Modifier
        .fillMaxWidth()
        .clickable(onClick = openWebpage)
        .padding(horizontal = 16.dp, vertical = 14.dp)
    Column(
        modifier = itemMod,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RecommendedEngineHeader(spec, isInstalled)
        Text(
            text = spec.summary,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecommendedEngineHeader(
    spec: RecommendedEngine,
    isInstalled: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val study = MaterialTheme.studyColors
    val installedMod = Modifier
        .clip(MaterialTheme.shapes.extraSmall)
        .background(study.green.mastered.copy(alpha = 0.16f))
        .padding(horizontal = 6.dp, vertical = 2.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = spec.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isInstalled) {
                Text(
                    text = "已安装",
                    style = MaterialTheme.typography.labelSmall,
                    color = study.pillText.mastered,
                    fontWeight = FontWeight.Medium,
                    modifier = installedMod,
                )
            }
            SymbolIcon(
                icon = AppIcon.COLLAPSED,
                modifier = Modifier.size(18.dp),
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}
