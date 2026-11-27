package org.plos.edu.lang.modules

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.plos.edu.lang.engine.CardId
import org.plos.edu.lang.engine.ImmersionPassage
import org.plos.edu.lang.engine.PassageId
import org.plos.edu.lang.repository.LanguageWorkspace

private const val SLOW_SPEECH_RATE: Float = 0.68f
private const val SEQ_TAG_PREFIX: String = "seq|"

data class PassagePlayback(
    val passageId: PassageId,
    val lineIndex: Int,
) {
    fun encodeTag(): String = "$SEQ_TAG_PREFIX${passageId.value}|$lineIndex"

    companion object {
        fun parseTag(tag: String): PassagePlayback? {
            if (!tag.startsWith(SEQ_TAG_PREFIX)) return null
            val payload = tag.removePrefix(SEQ_TAG_PREFIX)
            val sep = payload.lastIndexOf('|')
            if (sep <= 0 || sep >= payload.lastIndex) return null
            val rawId = payload.substring(0, sep)
            if (rawId.isBlank()) return null
            val idx = payload.substring(sep + 1).toIntOrNull() ?: return null
            return PassagePlayback(PassageId(rawId), idx)
        }
    }
}

@Singleton
class SpeechConductor @Inject constructor(
    private val speechEngine: SpeechEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val playbackState = MutableStateFlow<PassagePlayback?>(null)
    val playback: StateFlow<PassagePlayback?> = playbackState.asStateFlow()
    val status: StateFlow<SpeechStatus> = speechEngine.status
    val engines: StateFlow<List<TtsEngineOption>> = speechEngine.engines

    private var activeWorkspace: LanguageWorkspace? = null
    private var observedCardId: CardId? = null
    private var pendingAutoSpeakCardId: CardId? = null
    private var autoSpeakArmed: Boolean = false

    init {
        scope.launch {
            speechEngine.finishedTag.collect(::onUtteranceFinished)
        }
    }

    fun syncWorkspace(workspace: LanguageWorkspace) {
        activeWorkspace = workspace
        speechEngine.configure(
            localeTag = workspace.activeProfile.ttsLocaleTag,
            enginePkg = workspace.preferences.ttsEnginePkg,
        )
        val topCard = workspace.sessionQueue.firstOrNull()
        if (topCard == null) {
            observedCardId = null
            pendingAutoSpeakCardId = null
            autoSpeakArmed = false
            return
        }
        if (topCard.id != observedCardId) {
            observedCardId = topCard.id
            pendingAutoSpeakCardId = if (autoSpeakArmed) topCard.id else null
            autoSpeakArmed = false
        }
        if (pendingAutoSpeakCardId != topCard.id) return
        val prefs = workspace.preferences
        if (!prefs.autoPlayAudio || playbackState.value != null) {
            pendingAutoSpeakCardId = null
            return
        }
        if (speechEngine.status.value != SpeechStatus.READY) return
        pendingAutoSpeakCardId = null
        val word = topCard.unit.headword
        if (word.isNotEmpty()) {
            dispatchSpeech(workspace, word, prefs.speechRate, "")
        }
    }

    fun armNextCardAutoSpeak() {
        autoSpeakArmed = true
    }

    fun stop() {
        autoSpeakArmed = false
        pendingAutoSpeakCardId = null
        speechEngine.stop()
        if (playbackState.value != null) {
            playbackState.value = null
        }
    }

    fun speakNormal(text: String) {
        val ws = activeWorkspace ?: return
        playbackState.value = null
        dispatchSpeech(ws, text, ws.preferences.speechRate, "")
    }

    fun speakSlow(text: String) {
        val ws = activeWorkspace ?: return
        playbackState.value = null
        dispatchSpeech(ws, text, SLOW_SPEECH_RATE, "")
    }

    fun refreshEnginesAndAudition(playSample: Boolean) {
        speechEngine.refreshCatalog()
        if (!playSample) return
        val ws = activeWorkspace ?: return
        playbackState.value = null
        val sample = when (ws.activeProfile.code.value) {
            "en" -> "Clear pronunciation makes every new word easier to recall."
            "ru" -> "Доброе утро, послушаем чистое и естественное произношение."
            else -> "Selamat pagi, mari kita dengarkan pelafalan yang jernih."
        }
        dispatchSpeech(ws, sample, ws.preferences.speechRate, "")
    }

    fun togglePassage(passageId: PassageId, startLineIndex: Int) {
        if (playbackState.value?.passageId == passageId) {
            stop()
            return
        }
        val ws = activeWorkspace ?: return
        val passage = ws.passages.firstOrNull { it.id == passageId } ?: return
        if (passage.lines.isEmpty()) return
        val clampedIdx = startLineIndex.coerceIn(0, passage.lines.lastIndex)
        speakPassageLine(ws, passage, clampedIdx)
    }

    private fun onUtteranceFinished(tag: String) {
        val finished = PassagePlayback.parseTag(tag) ?: return
        if (playbackState.value != finished) return
        val ws = activeWorkspace ?: return
        val passage = ws.passages.firstOrNull { it.id == finished.passageId }
        val nextIdx = finished.lineIndex + 1
        if (passage == null || nextIdx > passage.lines.lastIndex) {
            playbackState.value = null
            return
        }
        speakPassageLine(ws, passage, nextIdx)
    }

    private fun speakPassageLine(
        workspace: LanguageWorkspace,
        passage: ImmersionPassage,
        arrayIdx: Int,
    ) {
        val line = passage.lines.getOrNull(arrayIdx) ?: return
        val next = PassagePlayback(passage.id, arrayIdx)
        playbackState.value = next
        dispatchSpeech(
            workspace = workspace,
            text = line.text,
            rate = workspace.preferences.speechRate,
            tag = next.encodeTag(),
        )
    }

    private fun dispatchSpeech(
        workspace: LanguageWorkspace,
        text: String,
        rate: Float,
        tag: String,
    ) {
        val request = UtteranceRequest(
            text = text,
            localeTag = workspace.activeProfile.ttsLocaleTag,
            rate = rate,
            utteranceTag = tag,
        )
        speechEngine.speak(request)
    }
}
