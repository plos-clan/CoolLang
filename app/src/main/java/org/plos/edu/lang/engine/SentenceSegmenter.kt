package org.plos.edu.lang.engine

import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_SPEAKER_LEN: Int = 16
private const val MAX_SUMMARY_CHARS: Int = 68
private val WHITESPACE_REGEX = Regex("\\s+")

private val NON_BREAKING_ABBREVS: Set<String> = setOf(
    "jl", "jln", "dr", "drs", "prof", "ir", "no", "rp",
    "tn", "ny", "nn", "sdr", "bpk", "hj", "kh", "pt",
    "cv", "tbk", "dkk", "dll", "dsb", "dst", "hlm", "hal",
    "tgl", "bln", "thn", "kab", "kec", "kel", "prov", "vol",
    "ed", "mr", "mrs", "ms", "st", "ud", "ma", "sh",
    "ave", "blvd", "rd", "inc", "ltd", "corp", "co", "vs",
    "etc", "jr", "sr", "dept", "gov", "sen", "rep", "fig",
    "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep",
    "sept", "oct", "nov", "dec",
    "г", "гг", "ул", "пр", "пер", "пл", "д", "кв",
    "корп", "стр", "им", "обл", "р", "руб", "коп", "тыс",
    "млн", "млрд", "см", "мм", "км", "кг", "др", "рис",
    "табл", "ч", "мин", "сек",
)

data class SegmentedSentence(
    val speaker: String,
    val text: String,
)

private data class SpeakerBody(
    val speaker: String,
    val body: String,
)

@Singleton
class SentenceSegmenter @Inject constructor() {
    fun segment(rawBody: String): List<SegmentedSentence> {
        val result = ArrayList<SegmentedSentence>()
        val paragraphs = rawBody
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        for (paragraph in paragraphs) {
            val extracted = splitSpeakerPrefix(paragraph)
            val pieces = splitParagraphSentences(extracted.body)
            for ((idx, piece) in pieces.withIndex()) {
                val spk = if (idx == 0) extracted.speaker else ""
                result.add(SegmentedSentence(speaker = spk, text = piece))
            }
        }
        return result
    }

    fun summarizeOpening(sentences: List<SegmentedSentence>): String {
        if (sentences.isEmpty()) return ""
        val joined = sentences.take(2).joinToString(" ") { it.text.trim() }
        val normalized = joined.replace(WHITESPACE_REGEX, " ").trim()
        if (normalized.length <= MAX_SUMMARY_CHARS) return normalized
        return normalized.substring(0, MAX_SUMMARY_CHARS).trimEnd() + "…"
    }

    private fun splitSpeakerPrefix(paragraph: String): SpeakerBody {
        val colonIdx = findSpeakerColon(paragraph)
        if (colonIdx <= 0 || colonIdx > MAX_SPEAKER_LEN) {
            return SpeakerBody("", paragraph)
        }
        val candidate = paragraph.substring(0, colonIdx).trim()
        val remainder = paragraph.substring(colonIdx + 1).trim()
        if (candidate.isEmpty() || remainder.isEmpty()) {
            return SpeakerBody("", paragraph)
        }
        val validSpeaker = candidate.all(::isSpeakerChar) &&
            candidate.count { it == ' ' } <= 1
        if (!validSpeaker) return SpeakerBody("", paragraph)
        return SpeakerBody(candidate, remainder)
    }

    private fun findSpeakerColon(text: String): Int {
        val limit = text.length.coerceAtMost(MAX_SPEAKER_LEN + 2)
        for (i in 0 until limit) {
            val ch = text[i]
            if (ch == ':' || ch == '：') return i
            if (isSentenceTerminal(ch)) return -1
        }
        return -1
    }

    private fun splitParagraphSentences(body: String): List<String> {
        val sentences = ArrayList<String>()
        val len = body.length
        var start = 0
        var i = 0
        while (i < len) {
            val ch = body[i]
            if (!isSentenceTerminal(ch)) {
                i++
                continue
            }
            if (ch == '.' && isNonBreakingPeriod(body, start, i)) {
                i++
                continue
            }
            var end = i + 1
            while (end < len && isSentenceTerminal(body[end])) {
                end++
            }
            while (end < len && isClosingWrapper(body[end])) {
                end++
            }
            val boundaryReady = end >= len ||
                body[end].isWhitespace() ||
                isCjkChar(body[end])
            if (!boundaryReady) {
                i = end
                continue
            }
            val slice = body.substring(start, end).trim()
            if (slice.isNotEmpty()) sentences.add(slice)
            while (end < len && body[end].isWhitespace()) {
                end++
            }
            start = end
            i = end
        }
        if (start < len) {
            val tail = body.substring(start).trim()
            if (tail.isNotEmpty()) sentences.add(tail)
        }
        return sentences
    }

    private fun isNonBreakingPeriod(
        text: String,
        sentStart: Int,
        dotIdx: Int,
    ): Boolean {
        val prevDigit = dotIdx > 0 && text[dotIdx - 1].isDigit()
        val nextDigit = dotIdx + 1 < text.length && text[dotIdx + 1].isDigit()
        if (prevDigit && nextDigit) return true
        var tokenStart = dotIdx
        while (tokenStart > sentStart) {
            if (!isAbbrevTokenChar(text[tokenStart - 1])) break
            tokenStart--
        }
        val token = text.substring(tokenStart, dotIdx)
        if (token.isEmpty()) return false
        val isListPrefix = tokenStart == sentStart &&
            token.length <= 3 &&
            token.all { it.isDigit() }
        if (isListPrefix) return true
        if (token.length == 1 && token[0].isLetter()) return true
        if ('.' in token) return true
        return token.lowercase() in NON_BREAKING_ABBREVS
    }

    private fun isSentenceTerminal(ch: Char): Boolean =
        ch == '.' || ch == '!' || ch == '?' || ch == '…' ||
            ch == '。' || ch == '！' || ch == '？'

    private fun isClosingWrapper(ch: Char): Boolean =
        ch == '"' || ch == '\'' || ch == '”' || ch == '’' ||
            ch == '»' || ch == ')' || ch == '）' || ch == ']' ||
            ch == '」' || ch == '』'

    private fun isAbbrevTokenChar(ch: Char): Boolean =
        ch.isLetterOrDigit() || ch == '.'

    private fun isSpeakerChar(ch: Char): Boolean =
        ch.isLetter() || ch == ' ' || ch == '\u0301'

    private fun isCjkChar(ch: Char): Boolean =
        ch.code in 0x4E00..0x9FFF
}
