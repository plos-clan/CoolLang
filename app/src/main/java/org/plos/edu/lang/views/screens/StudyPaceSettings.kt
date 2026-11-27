package org.plos.edu.lang.views.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import org.plos.edu.lang.engine.DailyTotalCap
import org.plos.edu.lang.engine.LearnerPreferences
import org.plos.edu.lang.engine.PreferenceMutation
import org.plos.edu.lang.engine.RetentionTarget
import org.plos.edu.lang.engine.StudyPacePlan
import org.plos.edu.lang.views.model.MainViewModel
import org.plos.edu.lang.views.widgets.AppIcon
import org.plos.edu.lang.views.widgets.COMPACT_TOP_BAR_HEIGHT
import org.plos.edu.lang.views.widgets.CompactFilterPill
import org.plos.edu.lang.views.widgets.SymbolIcon
import org.plos.edu.lang.views.widgets.TopBarTitle

private enum class PaceEditOrigin { NONE, NEW_EDITED, TOTAL_EDITED }

private data class StepperFieldSpec(
    val valueText: String,
    val stepDelta: Int,
    val onTextChange: (String) -> Unit,
    val onStep: (Int) -> Unit,
)

@Stable
private class PaceDraftState(
    initial: LearnerPreferences,
    private val onCommit: (StudyPacePlan) -> Unit,
) {
    var retentionPct by mutableIntStateOf(
        (initial.retention.value * 100.0).roundToInt().coerceIn(80, 95),
    )
        private set
    var newText by mutableStateOf(initial.dailyNewLimit.toString())
        private set
    var totalText by mutableStateOf(
        if (initial.dailyTotalCap.isUnlimited) ""
        else initial.dailyTotalCap.rawLimit.toString(),
    )
        private set
    var isUnlimited by mutableStateOf(initial.dailyTotalCap.isUnlimited)
        private set
    var editOrigin by mutableStateOf(PaceEditOrigin.NONE)
        private set

    val retentionTarget: RetentionTarget
        get() = RetentionTarget(retentionPct / 100.0)
    val parsedNew: Int?
        get() = newText.toIntOrNull()?.takeIf { it >= 1 }
    val parsedTotal: Int?
        get() = totalText.toIntOrNull()?.takeIf { it >= 1 }

    val hasCapConflict: Boolean
        get() = !isUnlimited &&
            parsedNew != null &&
            parsedTotal != null &&
            (parsedTotal ?: 0) < (parsedNew ?: 0)

    val recommendedTotal: Int?
        get() {
            if (editOrigin != PaceEditOrigin.NEW_EDITED) return null
            val n = parsedNew ?: return null
            val rec = StudyPacePlan.recommendTotal(n, retentionTarget)
            return if (!isUnlimited && parsedTotal == rec) null else rec
        }

    val recommendedNew: Int?
        get() {
            if (editOrigin != PaceEditOrigin.TOTAL_EDITED || isUnlimited) {
                return null
            }
            val t = parsedTotal ?: return null
            val rec = StudyPacePlan.recommendNew(t, retentionTarget)
            return if (parsedNew == rec) null else rec
        }

    fun updateFieldText(forTotal: Boolean, raw: String) {
        val digits = raw.filter(Char::isDigit).take(4)
        if (forTotal) {
            totalText = digits
            isUnlimited = digits.isEmpty()
            if (!isUnlimited) editOrigin = PaceEditOrigin.TOTAL_EDITED
        } else {
            newText = digits
            editOrigin = PaceEditOrigin.NEW_EDITED
        }
        tryCommit()
    }

    fun stepField(forTotal: Boolean, delta: Int) {
        if (forTotal) {
            val minVal = parsedNew ?: 1
            val base = parsedTotal
                ?: StudyPacePlan.recommendTotal(minVal, retentionTarget)
            val next = (base + delta).coerceIn(minVal, DailyTotalCap.MAX_BOUND)
            isUnlimited = false
            totalText = next.toString()
            editOrigin = PaceEditOrigin.TOTAL_EDITED
        } else {
            val next = ((parsedNew ?: 15) + delta)
                .coerceIn(1, StudyPacePlan.MAX_DAILY_NEW)
            newText = next.toString()
            editOrigin = PaceEditOrigin.NEW_EDITED
            if (!isUnlimited && (parsedTotal ?: next) < next) {
                totalText = next.toString()
            }
        }
        tryCommit()
    }

    fun toggleUnlimited() {
        if (isUnlimited) {
            val rec = StudyPacePlan.recommendTotal(
                parsedNew ?: 15,
                retentionTarget,
            )
            isUnlimited = false
            totalText = rec.toString()
        } else {
            isUnlimited = true
            totalText = ""
        }
        editOrigin = PaceEditOrigin.NONE
        tryCommit()
    }

    fun acceptRecommendation(forTotal: Boolean, value: Int) {
        if (forTotal) {
            isUnlimited = false
            totalText = value.toString()
        } else {
            newText = value.toString()
        }
        editOrigin = PaceEditOrigin.NONE
        tryCommit()
    }

    fun updateRetention(nextPct: Int) {
        val clamped = nextPct.coerceIn(80, 95)
        if (clamped == retentionPct) return
        retentionPct = clamped
        if (editOrigin == PaceEditOrigin.NONE) {
            editOrigin = PaceEditOrigin.NEW_EDITED
        }
        tryCommit()
    }

    private fun tryCommit() {
        val n = parsedNew ?: return
        val cap = if (isUnlimited) {
            DailyTotalCap.UNLIMITED
        } else {
            val t = parsedTotal ?: return
            if (t < n) return
            DailyTotalCap.bounded(t)
        }
        onCommit(StudyPacePlan.of(retentionTarget, n, cap))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPaceSettings(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val workspace = state.workspace ?: return
    val draft = remember(workspace.activeProfile.code) {
        PaceDraftState(workspace.preferences) { plan ->
            viewModel.applyPreference(PreferenceMutation.StudyPace(plan))
        }
    }
    val topBar: @Composable () -> Unit = {
        TopAppBar(
            expandedHeight = COMPACT_TOP_BAR_HEIGHT,
            title = { TopBarTitle("学习设置") },
            navigationIcon = {
                IconButton(onClick = onBack) { SymbolIcon(AppIcon.BACK) }
            },
        )
    }
    Scaffold(topBar = topBar) { innerPadding ->
        val scrollMod = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
        Column(
            modifier = scrollMod,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DailyQuotaCard(draft)
            RetentionTargetCard(draft)
            NewWordOrderCard(workspace, viewModel)
        }
    }
}

@Composable
private fun DailyQuotaCard(draft: PaceDraftState) {
    val dividerCol = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val innerMod = Modifier.fillMaxWidth().padding(16.dp)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = innerMod,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "每日词量",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            NewQuotaSection(draft)
            HorizontalDivider(color = dividerCol)
            TotalQuotaSection(draft)
        }
    }
}

@Composable
private fun NewQuotaSection(draft: PaceDraftState) {
    val newSpec = StepperFieldSpec(
        valueText = draft.newText,
        stepDelta = 5,
        onTextChange = { draft.updateFieldText(false, it) },
        onStep = { draft.stepField(false, it) },
    )
    val recNew = draft.recommendedNew
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuotaLabelBlock(Modifier.weight(1f), "每日新词", "每天新学词数")
            NumericStepperBox(newSpec)
        }
        if (recNew != null) {
            RecommendationActionChip("推荐新词 $recNew") {
                draft.acceptRecommendation(false, recNew)
            }
        }
    }
}

@Composable
private fun TotalQuotaSection(draft: PaceDraftState) {
    val scheme = MaterialTheme.colorScheme
    val totalSpec = StepperFieldSpec(
        valueText = draft.totalText,
        stepDelta = 10,
        onTextChange = { draft.updateFieldText(true, it) },
        onStep = { draft.stepField(true, it) },
    )
    val recTotal = draft.recommendedTotal
    val pill: @Composable () -> Unit = {
        CompactFilterPill("不限", draft.isUnlimited, draft::toggleUnlimited)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuotaLabelBlock(
                modifier = Modifier.weight(1f),
                title = "每日总词",
                subtitle = "含新词与复习词",
                badge = pill,
            )
            if (!draft.isUnlimited) NumericStepperBox(totalSpec)
        }
        if (draft.hasCapConflict) {
            val minReq = draft.parsedNew ?: 1
            Text(
                text = "总词上限需不小于每日新词 ($minReq)",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.error,
            )
        } else if (recTotal != null) {
            RecommendationActionChip("推荐总词 $recTotal") {
                draft.acceptRecommendation(true, recTotal)
            }
        }
    }
}

@Composable
private fun QuotaLabelBlock(
    modifier: Modifier,
    title: String,
    subtitle: String,
    badge: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            badge?.invoke()
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NumericStepperBox(spec: StepperFieldSpec) {
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val borderCol = if (focused) scheme.primary else scheme.outlineVariant
    val borderWidth = if (focused) 1.6.dp else 1.dp
    val fieldMod = Modifier
        .width(74.dp)
        .height(38.dp)
        .onFocusChanged { focused = it.isFocused }
    val textStyle = MaterialTheme.typography.titleMedium.copy(
        color = scheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
    val kbOpts = KeyboardOptions(
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Done,
    )
    val kbActs = KeyboardActions(onDone = { focusManager.clearFocus() })
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RoundStepButton("−") { spec.onStep(-spec.stepDelta) }
        Surface(
            color = scheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(borderWidth, borderCol),
            modifier = fieldMod,
        ) {
            Box(contentAlignment = Alignment.Center) {
                BasicTextField(
                    value = spec.valueText,
                    onValueChange = spec.onTextChange,
                    singleLine = true,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = kbOpts,
                    keyboardActions = kbActs,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        RoundStepButton("+") { spec.onStep(spec.stepDelta) }
    }
}

@Composable
private fun RoundStepButton(symbol: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val mod = Modifier
        .size(34.dp)
        .clip(CircleShape)
        .background(scheme.surfaceContainerHigh)
        .clickable(onClick = onClick)
    Box(modifier = mod, contentAlignment = Alignment.Center) {
        Text(
            text = symbol,
            color = scheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun RecommendationActionChip(label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val smallMed = MaterialTheme.typography.labelMedium
    val pillMod = Modifier
        .clip(CircleShape)
        .background(scheme.secondaryContainer)
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 5.dp)
    Row(
        modifier = pillMod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = scheme.onSecondaryContainer, style = smallMed)
        Text(
            text = "填入",
            style = smallMed,
            color = scheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RetentionTargetCard(draft: PaceDraftState) {
    val scheme = MaterialTheme.colorScheme
    val subColor = scheme.onSurfaceVariant
    val small = MaterialTheme.typography.labelSmall
    val headStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
    )
    val bodySmall = MaterialTheme.typography.bodySmall
    val pctLabel = "${draft.retentionPct}%"
    val innerMod = Modifier.fillMaxWidth().padding(16.dp)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = innerMod,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("目标留存率", style = headStyle)
                Text(pctLabel, color = scheme.primary, style = headStyle)
            }
            Slider(
                value = draft.retentionPct.toFloat(),
                onValueChange = { draft.updateRetention(it.roundToInt()) },
                valueRange = 80f..95f,
                steps = 14,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("80% 轻量", style = small, color = subColor)
                Text("90% 均衡", style = small, color = subColor)
                Text("95% 牢固", style = small, color = subColor)
            }
            Text("留存率越高，复习间隔越短", style = bodySmall, color = subColor)
        }
    }
}
