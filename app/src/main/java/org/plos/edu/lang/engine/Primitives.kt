package org.plos.edu.lang.engine

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

object StudyDayClock {
    const val MILLIS_PER_DAY: Long = 86_400_000L
    const val MILLIS_PER_DAY_DOUBLE: Double = 86_400_000.0

    @OptIn(ExperimentalTime::class)
    fun dayStartMs(nowMs: Long): Long {
        val zone = TimeZone.currentSystemDefault()
        val nowInstant = Instant.fromEpochMilliseconds(nowMs)
        val localNow = nowInstant.toLocalDateTime(zone)
        val fourAm = LocalDateTime(localNow.date, LocalTime(4, 0))
        val fourAmMs = fourAm.toInstant(zone).toEpochMilliseconds()
        if (nowMs >= fourAmMs) return fourAmMs
        return fourAmMs - MILLIS_PER_DAY
    }
}

@JvmInline
value class LangCode(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
value class RootId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
value class LexemeId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
value class TrackId(val value: String) {
    init {
        require(value.isNotBlank())
    }

    val isAll: Boolean
        get() = value == ALL.value

    companion object {
        val ALL: TrackId = TrackId("__all__")
    }
}

@JvmInline
value class UnitId(val value: String) {
    init {
        require(value.isNotBlank())
    }

    fun synthesizedRootId(): RootId? {
        if (!value.startsWith(ROOT_UNIT_PREFIX)) return null
        val raw = value.removePrefix(ROOT_UNIT_PREFIX)
        return if (raw.isNotBlank()) RootId(raw) else null
    }

    companion object {
        private const val ROOT_UNIT_PREFIX: String = "root_"

        fun forRoot(rootId: RootId): UnitId =
            UnitId("$ROOT_UNIT_PREFIX${rootId.value}")
    }
}

data class ParsedCardKey(
    val langCode: String,
    val unitId: String,
)

@JvmInline
value class CardId(val value: String) {
    init {
        require(value.isNotBlank())
    }

    fun parse(): ParsedCardKey? {
        val sep = value.indexOf('|')
        if (sep <= 0 || sep >= value.lastIndex) return null
        return ParsedCardKey(
            langCode = value.substring(0, sep),
            unitId = value.substring(sep + 1),
        )
    }

    companion object {
        fun of(langCode: String, unitId: String): CardId =
            CardId("$langCode|$unitId")
    }
}

@JvmInline
value class PassageId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
value class RuleId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
value class StyleKey(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
value class RetentionTarget(val value: Double) {
    init {
        require(value in 0.70..0.97)
    }
}

@JvmInline
value class StabilityDays(val value: Double) {
    init {
        require(value in MIN_DAYS..MAX_DAYS)
    }

    companion object {
        const val MIN_DAYS: Double = 0.0001
        const val MAX_DAYS: Double = 36500.0

        fun clamped(raw: Double): StabilityDays =
            StabilityDays(raw.coerceIn(MIN_DAYS, MAX_DAYS))
    }
}

@JvmInline
value class DifficultyScore(val value: Double) {
    init {
        require(value in MIN_SCORE..MAX_SCORE)
    }

    companion object {
        const val MIN_SCORE: Double = 1.0
        const val MAX_SCORE: Double = 10.0

        fun clamped(raw: Double): DifficultyScore =
            DifficultyScore(raw.coerceIn(MIN_SCORE, MAX_SCORE))
    }
}

enum class ReviewGrade(val code: Int, val title: String) {
    AGAIN(1, "忘记"),
    HARD(2, "模糊"),
    GOOD(3, "认识"),
    EASY(4, "简单"),
}

enum class WordDisposition(val code: Int) {
    AUTO(0),
    MASTERED(1),
    IGNORED(2);

    companion object {
        fun fromCode(code: Int): WordDisposition = entries
            .firstOrNull { it.code == code }
            ?: AUTO
    }
}

enum class MasteryStage(val label: String) {
    UNSEEN("未学"),
    LEARNING("在学"),
    MASTERED("已学");

    companion object {
        const val MASTERED_DAYS: Double = 21.0

        fun fromStability(days: Double, seen: Boolean): MasteryStage = when {
            !seen -> UNSEEN
            days < MASTERED_DAYS -> LEARNING
            else -> MASTERED
        }
    }
}

enum class PlanStage(val label: String) {
    LEARNING("在学"),
    UNSEEN("待学"),
    MASTERED("熟知"),
    IGNORED("未选");

    companion object {
        fun resolve(
            disposition: WordDisposition,
            mastery: MasteryStage,
        ): PlanStage = when (disposition) {
            WordDisposition.IGNORED -> IGNORED
            WordDisposition.MASTERED -> MASTERED
            WordDisposition.AUTO -> when (mastery) {
                MasteryStage.MASTERED -> MASTERED
                MasteryStage.LEARNING -> LEARNING
                MasteryStage.UNSEEN -> UNSEEN
            }
        }
    }
}

enum class LexiconGroup(
    val label: String,
    val inTrack: Boolean,
) {
    LEARNING("在学", true),
    UNSEEN("待学", true),
    MASTERED("熟知", true),
    IGNORED("未选", true),
    OUTSIDE_TRACK("其他词书", false);

    companion object {
        val IN_TRACK_ORDER: List<LexiconGroup> = listOf(
            LEARNING,
            UNSEEN,
            MASTERED,
            IGNORED,
        )
        val DEFAULT_EXPANDED: Set<LexiconGroup> = setOf(
            LEARNING,
            UNSEEN,
            OUTSIDE_TRACK,
        )

        fun fromStage(stage: PlanStage): LexiconGroup = when (stage) {
            PlanStage.LEARNING -> LEARNING
            PlanStage.UNSEEN -> UNSEEN
            PlanStage.MASTERED -> MASTERED
            PlanStage.IGNORED -> IGNORED
        }
    }
}

enum class LexiconSort(val label: String) {
    PACK_ORDER("词序"),
    FREQUENCY("词频"),
    ALPHABETICAL("首字母"),
    FAMILIARITY_ASC("熟悉度 ↑"),
    FAMILIARITY_DESC("熟悉度 ↓");

    fun next(): LexiconSort = when (this) {
        PACK_ORDER -> FREQUENCY
        FREQUENCY -> ALPHABETICAL
        ALPHABETICAL -> FAMILIARITY_ASC
        FAMILIARITY_ASC -> FAMILIARITY_DESC
        FAMILIARITY_DESC -> PACK_ORDER
    }
}

enum class PassageGenre(val label: String) {
    ALL("全部"),
    DIALOGUE("对话"),
    NARRATIVE("短文");

    fun matches(isDialogue: Boolean): Boolean = when (this) {
        ALL -> true
        DIALOGUE -> isDialogue
        NARRATIVE -> !isDialogue
    }
}

enum class PassageSort(val label: String) {
    GRADIENT("进阶梯度"),
    COVERAGE_DESC("已学覆盖 ↓"),
    COVERAGE_ASC("已学覆盖 ↑");

    fun next(): PassageSort = when (this) {
        GRADIENT -> COVERAGE_DESC
        COVERAGE_DESC -> COVERAGE_ASC
        COVERAGE_ASC -> GRADIENT
    }
}

enum class AffixKind(val tag: String) {
    PREFIX("prefix"),
    SUFFIX("suffix"),
    CIRCUMFIX("circumfix"),
    MUTATION("mutation");

    companion object {
        fun fromTag(raw: String): AffixKind = entries
            .firstOrNull { it.tag == raw }
            ?: PREFIX
    }
}

@JvmInline
value class DailyTotalCap private constructor(val rawLimit: Int) {
    val isUnlimited: Boolean
        get() = rawLimit == UNLIMITED_RAW

    fun remainingAfter(completedCount: Int): Int =
        if (isUnlimited) {
            Int.MAX_VALUE
        } else {
            (rawLimit - completedCount).coerceAtLeast(0)
        }

    fun withBonus(bonusCount: Int): DailyTotalCap =
        if (isUnlimited || bonusCount <= 0) {
            this
        } else {
            DailyTotalCap((rawLimit + bonusCount).coerceAtMost(MAX_BOUND))
        }

    fun raisedAtLeast(minRequired: Int): DailyTotalCap =
        if (isUnlimited || rawLimit >= minRequired) {
            this
        } else {
            bounded(minRequired)
        }

    companion object {
        private const val UNLIMITED_RAW: Int = 0
        const val MIN_BOUND: Int = 1
        const val MAX_BOUND: Int = 9999
        val UNLIMITED: DailyTotalCap = DailyTotalCap(UNLIMITED_RAW)
        val DEFAULT: DailyTotalCap = bounded(80)

        fun bounded(limit: Int): DailyTotalCap =
            DailyTotalCap(limit.coerceIn(MIN_BOUND, MAX_BOUND))

        fun fromStored(raw: Int): DailyTotalCap =
            if (raw <= 0) UNLIMITED else bounded(raw)
    }
}

enum class TimelineMode(val tabLabel: String, val cardTitle: String) {
    REVIEW_HISTORY("回顾", "学习回顾"),
    LOAD_FORECAST("预测", "学习预测"),
}

enum class NewWordOrder(
    val tag: String,
    val label: String,
    val description: String,
) {
    BOOK_ORDER("book", "原书顺序", "按教材单元或原词表顺序出词"),
    SHUFFLED("shuffled", "随机乱序", "以固定随机种子打乱待学新词顺序"),
    FREQUENCY("frequency", "词频优先", "按语料库常用词频由高到低出词");

    companion object {
        fun fromTag(raw: String): NewWordOrder = entries
            .firstOrNull { it.tag == raw }
            ?: BOOK_ORDER

        fun shuffleRank(baseSeed: Long, rootId: RootId): Long {
            val bits = rootId.value.hashCode().toLong()
            var z = baseSeed xor (bits * -7046029254386353131L)
            z = (z xor (z ushr 30)) * -4658895280553007687L
            z = (z xor (z ushr 27)) * -7723592293110705685L
            return z xor (z ushr 31)
        }
    }
}

data class PosSense(
    val pos: String,
    val meaning: String,
)

@JvmInline
value class WordSenses(val entries: List<PosSense>) {
    fun matchesSearch(keyword: String): Boolean =
        entries.any { it.meaning.lowercase().contains(keyword) }

    fun encodeLines(): String = entries.joinToString("\n") { item ->
        "${item.pos}\t${item.meaning}"
    }

    companion object {
        fun decodeLines(raw: String): WordSenses {
            if (raw.isBlank()) return WordSenses(emptyList())
            val parsed = raw.lines().mapNotNull(::parseSenseLine)
            return WordSenses(parsed)
        }

        private fun parseSenseLine(line: String): PosSense? {
            val tab = line.indexOf('\t')
            if (tab < 0) {
                val trimmed = line.trim()
                return if (trimmed.isEmpty()) null else PosSense("", trimmed)
            }
            val pos = line.substring(0, tab).trim()
            val meaning = line.substring(tab + 1).trim()
            return if (meaning.isEmpty()) null else PosSense(pos, meaning)
        }
    }
}
