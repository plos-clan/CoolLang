package org.plos.edu.lang.modules

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpeechStatus(val ready: Boolean, val hint: String) {
    INITIALIZING(false, "正在初始化语音合成服务"),
    READY(true, "语音引擎已就绪"),
    VOICE_MISSING(false, "当前引擎缺少该语言发音数据，请在语音设置中切换引擎或下载语音包"),
    UNAVAILABLE(false, "未检测到可用的系统 TTS 服务"),
}

data class TtsEngineOption(
    val packageName: String,
    val label: String,
    val isActive: Boolean = false,
)

data class UtteranceRequest(
    val text: String,
    val localeTag: String,
    val rate: Float,
    val utteranceTag: String = "",
)

abstract class SpeechEngine {
    abstract val status: StateFlow<SpeechStatus>
    abstract val engines: StateFlow<List<TtsEngineOption>>
    abstract val finishedTag: SharedFlow<String>

    abstract fun configure(localeTag: String, enginePkg: String)

    abstract fun refreshCatalog()

    abstract fun speak(request: UtteranceRequest)

    abstract fun stop()

    abstract fun release()
}

@Singleton
class SystemSpeechEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpeechEngine() {
    private val statusState = MutableStateFlow(SpeechStatus.INITIALIZING)
    private val enginesState = MutableStateFlow(emptyList<TtsEngineOption>())
    private val finishedEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
    )
    private var engineReady = false
    private var currentTag = "id-ID"
    private var requestedPkg: String = ""
    private var pendingTargetPkg: String? = null
    private var activeBoundPkg: String = ""
    private var fallbackAttempted = false
    private var tts: TextToSpeech? = null

    override val status: StateFlow<SpeechStatus> = statusState.asStateFlow()
    override val engines: StateFlow<List<TtsEngineOption>> =
        enginesState.asStateFlow()
    override val finishedTag: SharedFlow<String> =
        finishedEvents.asSharedFlow()

    init {
        bindEngine(null)
    }

    override fun configure(localeTag: String, enginePkg: String) {
        currentTag = localeTag
        if (enginePkg != requestedPkg || tts == null) {
            requestedPkg = enginePkg
            fallbackAttempted = false
            bindEngine(enginePkg.ifBlank { null })
            return
        }
        if (engineReady) applyBestVoice(localeTag)
    }

    override fun refreshCatalog() {
        val activeTts = tts
        if (activeTts == null || !engineReady) {
            fallbackAttempted = false
            bindEngine(requestedPkg.ifBlank { null })
            return
        }
        val installed = activeTts.engines
        val preferred = resolveTargetPkg(installed, activeTts.defaultEngine)
        if (preferred.isNotEmpty() && preferred != activeBoundPkg) {
            fallbackAttempted = false
            bindEngine(preferred)
            return
        }
        publishEngines(installed)
        applyBestVoice(currentTag)
    }

    override fun speak(request: UtteranceRequest) {
        val cleanText = stripStressMarks(request.text)
        if (cleanText.isBlank() || !engineReady) return
        val activeTts = tts ?: return
        if (request.localeTag != currentTag) {
            currentTag = request.localeTag
            applyBestVoice(request.localeTag)
        }
        if (statusState.value != SpeechStatus.READY) return
        activeTts.setSpeechRate(request.rate)
        val utteranceId = request.utteranceTag.ifEmpty {
            "utt_${System.nanoTime()}"
        }
        activeTts.speak(
            cleanText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId,
        )
    }

    override fun stop() {
        tts?.stop()
    }

    override fun release() {
        val activeTts = tts ?: return
        activeTts.stop()
        activeTts.shutdown()
        tts = null
        pendingTargetPkg = null
        activeBoundPkg = ""
        engineReady = false
    }

    private fun stripStressMarks(raw: String): String {
        if (raw.indexOf('\u0301') < 0) return raw
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            if (ch != '\u0301') sb.append(ch)
        }
        return sb.toString()
    }

    private fun bindEngine(targetPkg: String?) {
        tts?.stop()
        tts?.shutdown()
        engineReady = false
        statusState.value = SpeechStatus.INITIALIZING
        pendingTargetPkg = targetPkg
        tts = if (targetPkg != null) {
            TextToSpeech(context, ::onEngineInitialized, targetPkg)
        } else {
            TextToSpeech(context, ::onEngineInitialized)
        }
    }

    private fun onEngineInitialized(resultCode: Int) {
        if (resultCode != TextToSpeech.SUCCESS) {
            if (!fallbackAttempted && pendingTargetPkg != null) {
                fallbackAttempted = true
                bindEngine(null)
                return
            }
            engineReady = false
            statusState.value = SpeechStatus.UNAVAILABLE
            return
        }
        val activeTts = tts ?: return
        val installed = activeTts.engines
        val sysDefault = activeTts.defaultEngine.orEmpty()
        val chosenPkg = resolveTargetPkg(installed, sysDefault)
        val currentActual = pendingTargetPkg ?: sysDefault
        if (pendingTargetPkg == null &&
            chosenPkg.isNotEmpty() &&
            chosenPkg != currentActual
        ) {
            bindEngine(chosenPkg)
            return
        }
        activeBoundPkg = currentActual
        publishEngines(installed)
        attachProgressListener(activeTts)
        engineReady = true
        applyBestVoice(currentTag)
    }

    private fun publishEngines(installed: List<TextToSpeech.EngineInfo>) {
        enginesState.value = installed.map { info ->
            TtsEngineOption(
                packageName = info.name,
                label = info.label,
                isActive = info.name == activeBoundPkg,
            )
        }
    }

    private fun resolveTargetPkg(
        installed: List<TextToSpeech.EngineInfo>,
        sysDefault: String,
    ): String {
        if (requestedPkg.isNotBlank() &&
            installed.any { it.name == requestedPkg }
        ) {
            return requestedPkg
        }
        val ranked = installed.minByOrNull { enginePriority(it.name) }
        return ranked?.name ?: sysDefault
    }

    private fun enginePriority(pkg: String): Int {
        val lower = pkg.lowercase(Locale.ROOT)
        return when {
            "edge_tts" in lower -> 0
            "edgetts" in lower -> 1
            "hayaitts" in lower || "voxsherpa" in lower -> 2
            "sherpa" in lower || "piper" in lower || "k2fsa" in lower -> 3
            pkg == "com.google.android.tts" -> 4
            else -> 5
        }
    }

    private fun attachProgressListener(activeTts: TextToSpeech) {
        val listener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (!utteranceId.isNullOrEmpty()) {
                    finishedEvents.tryEmit(utteranceId)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (!utteranceId.isNullOrEmpty()) {
                    finishedEvents.tryEmit(utteranceId)
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (!utteranceId.isNullOrEmpty()) {
                    finishedEvents.tryEmit(utteranceId)
                }
            }
        }
        activeTts.setOnUtteranceProgressListener(listener)
    }

    private fun applyBestVoice(localeTag: String) {
        val activeTts = tts ?: return
        val locale = Locale.forLanguageTag(localeTag)
        val availability = activeTts.setLanguage(locale)
        val langReady = availability != TextToSpeech.LANG_MISSING_DATA &&
            availability != TextToSpeech.LANG_NOT_SUPPORTED
        val bestVoice = selectHighestQualityVoice(activeTts, locale)
        val voiceResult = if (bestVoice != null) {
            runCatching { activeTts.setVoice(bestVoice) }
                .getOrDefault(TextToSpeech.ERROR)
        } else {
            TextToSpeech.ERROR
        }
        val voiceReady = voiceResult == TextToSpeech.SUCCESS
        statusState.value = if (langReady || voiceReady) {
            SpeechStatus.READY
        } else {
            SpeechStatus.VOICE_MISSING
        }
    }

    private fun canonicalLang(code: String): String {
        val lower = code.lowercase(Locale.ROOT)
        return if (lower == "in") "id" else lower
    }

    private fun selectHighestQualityVoice(
        activeTts: TextToSpeech,
        targetLocale: Locale,
    ): Voice? {
        val allVoices = runCatching { activeTts.voices }.getOrNull()
            ?: return null
        val targetLang = canonicalLang(targetLocale.language)
        val targetCountry = targetLocale.country
        val candidates = allVoices.filter { v ->
            canonicalLang(v.locale.language) == targetLang &&
                !v.features.contains("notInstalled")
        }
        return candidates.maxWithOrNull(
            compareBy<Voice> { v ->
                targetCountry.isEmpty() ||
                    v.locale.country.equals(targetCountry, ignoreCase = true)
            }
                .thenBy { it.quality }
                .thenBy { -it.latency },
        )
    }
}
