package org.plos.edu.lang.modules

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.plos.edu.lang.Constants
import org.plos.edu.lang.engine.DailyTotalCap
import org.plos.edu.lang.engine.LangCode
import org.plos.edu.lang.engine.LearnerPreferences
import org.plos.edu.lang.engine.NewWordOrder
import org.plos.edu.lang.engine.PreferenceMutation
import org.plos.edu.lang.engine.RetentionTarget
import org.plos.edu.lang.engine.StudyPacePlan
import org.plos.edu.lang.engine.TrackId

val Context.dataStore by preferencesDataStore(name = "settings")

private val KEY_LANG = stringPreferencesKey("active_lang")
private val KEY_THEME = stringPreferencesKey("theme")
private val KEY_RETENTION = doublePreferencesKey("retention_target")
private val KEY_DAILY_NEW = intPreferencesKey("daily_new_limit")
private val KEY_DAILY_TOTAL = intPreferencesKey("daily_total_limit")
private val KEY_SPEECH_RATE = floatPreferencesKey("speech_rate")
private val KEY_AUTO_PLAY = booleanPreferencesKey("auto_play_audio")
private val KEY_TTS_ENGINE = stringPreferencesKey("tts_engine_pkg")
private val KEY_ACTIVE_TRACKS = stringPreferencesKey("active_tracks")
private val KEY_TRACK_ORDERS = stringPreferencesKey("track_word_orders")
private val KEY_SHUFFLE_SEED = longPreferencesKey("new_word_shuffle_seed")
private val KEY_BONUS_DAY_MS = longPreferencesKey("bonus_day_ms")
private val KEY_BONUS_NEW_COUNT = intPreferencesKey("bonus_new_count")

@Singleton
class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.dataStore

    val preferencesFlow: Flow<LearnerPreferences> =
        store.data.map(::mapPreferences)

    suspend fun current(): LearnerPreferences = preferencesFlow.first()

    suspend fun setLanguage(lang: LangCode) {
        store.edit { prefs -> prefs[KEY_LANG] = lang.value }
    }

    suspend fun mutate(mutation: PreferenceMutation) {
        store.edit { prefs -> applyMutation(prefs, mutation) }
    }

    private fun applyMutation(
        prefs: MutablePreferences,
        mutation: PreferenceMutation,
    ) {
        when (mutation) {
            is PreferenceMutation.Theme -> {
                prefs[KEY_THEME] = mutation.value
            }
            is PreferenceMutation.StudyPace -> {
                val plan = mutation.plan
                prefs[KEY_RETENTION] = plan.retention.value
                prefs[KEY_DAILY_NEW] = plan.dailyNewLimit
                prefs[KEY_DAILY_TOTAL] = plan.dailyTotalCap.rawLimit
            }
            is PreferenceMutation.TrackWordOrder -> {
                val raw = prefs[KEY_TRACK_ORDERS].orEmpty()
                val updated = LinkedHashMap(decodeOrderMap(raw))
                updated[mutation.trackKey] = mutation.order
                prefs[KEY_TRACK_ORDERS] = encodeOrderMap(updated)
            }
            PreferenceMutation.ReshuffleNewWords -> {
                val prev = prefs[KEY_SHUFFLE_SEED]
                    ?: Constants.DEFAULT_SHUFFLE_SEED
                val step = prev + -7046029254386353131L
                prefs[KEY_SHUFFLE_SEED] = step xor System.nanoTime()
            }
            is PreferenceMutation.SpeechRate -> {
                prefs[KEY_SPEECH_RATE] = mutation.rate.coerceIn(0.6f, 1.3f)
            }
            is PreferenceMutation.AutoPlayAudio -> {
                prefs[KEY_AUTO_PLAY] = mutation.enabled
            }
            is PreferenceMutation.TtsEngine -> {
                prefs[KEY_TTS_ENGINE] = mutation.pkg
            }
        }
    }

    suspend fun setTrackForLanguage(lang: LangCode, trackId: TrackId) {
        store.edit { prefs ->
            val currentMap = decodeTrackMap(prefs[KEY_ACTIVE_TRACKS].orEmpty())
            val updated = LinkedHashMap(currentMap)
            updated[lang] = trackId
            prefs[KEY_ACTIVE_TRACKS] = encodeTrackMap(updated)
        }
    }

    suspend fun addTodayBonusQuota(dayStartMs: Long, delta: Int) {
        store.edit { prefs ->
            val storedDay = prefs[KEY_BONUS_DAY_MS] ?: 0L
            val currentBonus = if (storedDay == dayStartMs) {
                prefs[KEY_BONUS_NEW_COUNT] ?: 0
            } else {
                0
            }
            prefs[KEY_BONUS_DAY_MS] = dayStartMs
            prefs[KEY_BONUS_NEW_COUNT] = (currentBonus + delta).coerceIn(0, 500)
        }
    }

    suspend fun restoreAll(restored: LearnerPreferences) {
        val plan = StudyPacePlan.of(
            retention = restored.retention,
            dailyNewLimit = restored.dailyNewLimit,
            dailyTotalCap = restored.dailyTotalCap,
        )
        store.edit { prefs ->
            prefs[KEY_LANG] = restored.activeLang.value
            prefs[KEY_THEME] = restored.appTheme
            prefs[KEY_RETENTION] = plan.retention.value
            prefs[KEY_DAILY_NEW] = plan.dailyNewLimit
            prefs[KEY_DAILY_TOTAL] = plan.dailyTotalCap.rawLimit
            prefs[KEY_SPEECH_RATE] = restored.speechRate.coerceIn(0.6f, 1.3f)
            prefs[KEY_AUTO_PLAY] = restored.autoPlayAudio
            prefs[KEY_TTS_ENGINE] = restored.ttsEnginePkg
            prefs[KEY_ACTIVE_TRACKS] = encodeTrackMap(restored.trackByLang)
            prefs[KEY_TRACK_ORDERS] = encodeOrderMap(restored.orderByTrack)
            prefs[KEY_SHUFFLE_SEED] = restored.shuffleSeed
        }
    }

    private fun mapPreferences(prefs: Preferences): LearnerPreferences {
        val rawLang = prefs[KEY_LANG]?.takeIf { it.isNotBlank() }
            ?: Constants.DEFAULT_LANG
        val lang = LangCode(rawLang)
        val theme = prefs[KEY_THEME] ?: Constants.DEFAULT_THEME
        val rawRet = prefs[KEY_RETENTION] ?: Constants.DEFAULT_RETENTION
        val safeRet = RetentionTarget(rawRet.coerceIn(0.75, 0.96))
        val rawNew = prefs[KEY_DAILY_NEW] ?: Constants.DEFAULT_DAILY_NEW
        val rawTotal = prefs[KEY_DAILY_TOTAL] ?: Constants.DEFAULT_DAILY_TOTAL
        val pace = StudyPacePlan.of(
            retention = safeRet,
            dailyNewLimit = rawNew,
            dailyTotalCap = DailyTotalCap.fromStored(rawTotal),
        )
        val rate = (prefs[KEY_SPEECH_RATE] ?: Constants.DEFAULT_SPEECH_RATE)
            .coerceIn(0.6f, 1.3f)
        val autoPlay = prefs[KEY_AUTO_PLAY] ?: Constants.DEFAULT_AUTO_PLAY
        val enginePkg = prefs[KEY_TTS_ENGINE].orEmpty()
        val tracks = decodeTrackMap(prefs[KEY_ACTIVE_TRACKS].orEmpty())
        val orders = decodeOrderMap(prefs[KEY_TRACK_ORDERS].orEmpty())
        val seed = prefs[KEY_SHUFFLE_SEED] ?: Constants.DEFAULT_SHUFFLE_SEED
        val bonusDay = prefs[KEY_BONUS_DAY_MS] ?: 0L
        val bonusCnt = prefs[KEY_BONUS_NEW_COUNT] ?: 0
        return LearnerPreferences(
            activeLang = lang,
            appTheme = theme,
            retention = pace.retention,
            dailyNewLimit = pace.dailyNewLimit,
            dailyTotalCap = pace.dailyTotalCap,
            speechRate = rate,
            autoPlayAudio = autoPlay,
            ttsEnginePkg = enginePkg,
            trackByLang = tracks,
            orderByTrack = orders,
            shuffleSeed = seed,
            bonusDayStartMs = bonusDay,
            bonusNewCount = bonusCnt,
        )
    }

    private fun encodeOrderMap(map: Map<String, NewWordOrder>): String =
        map.entries.joinToString(",") { (k, v) -> "$k=${v.tag}" }

    private fun decodeOrderMap(raw: String): Map<String, NewWordOrder> {
        if (raw.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, NewWordOrder>()
        for (pair in raw.split(",")) {
            val eq = pair.indexOf('=')
            if (eq <= 0 || eq >= pair.lastIndex) continue
            val k = pair.substring(0, eq).trim()
            val v = pair.substring(eq + 1).trim()
            if (k.isNotEmpty() && v.isNotEmpty()) {
                result[k] = NewWordOrder.fromTag(v)
            }
        }
        return result
    }

    private fun encodeTrackMap(map: Map<LangCode, TrackId>): String =
        map.entries.joinToString(",") { (k, v) -> "${k.value}=${v.value}" }

    private fun decodeTrackMap(raw: String): Map<LangCode, TrackId> {
        if (raw.isBlank()) return emptyMap()
        val result = LinkedHashMap<LangCode, TrackId>()
        for (pair in raw.split(",")) {
            val eq = pair.indexOf('=')
            if (eq <= 0 || eq >= pair.lastIndex) continue
            val k = pair.substring(0, eq).trim()
            val v = pair.substring(eq + 1).trim()
            if (k.isNotEmpty() && v.isNotEmpty()) {
                result[LangCode(k)] = TrackId(v)
            }
        }
        return result
    }
}
