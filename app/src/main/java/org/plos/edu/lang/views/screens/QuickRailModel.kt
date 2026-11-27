package org.plos.edu.lang.views.screens

import androidx.compose.foundation.lazy.LazyListState
import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import org.plos.edu.lang.engine.LexiconGroup
import org.plos.edu.lang.engine.LexiconSort
import org.plos.edu.lang.engine.RootId
import org.plos.edu.lang.views.model.LexiconSection

private const val WORD_KEY_PREFIX: String = "w|"

object WordRowKey {
    fun encode(group: LexiconGroup, rootId: RootId): String =
        "$WORD_KEY_PREFIX${group.ordinal}|${rootId.value}"

    fun decodeAtY(listState: LazyListState, yPx: Float): RootId? {
        val yInt = yPx.toInt()
        val hit = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            yInt >= info.offset && yInt <= info.offset + info.size
        }
        val rawKey = hit?.key as? String ?: return null
        if (!rawKey.startsWith(WORD_KEY_PREFIX)) return null
        val sep = rawKey.indexOf('|', WORD_KEY_PREFIX.length)
        if (sep < 0 || sep >= rawKey.lastIndex) return null
        val grpOrd = rawKey
            .substring(WORD_KEY_PREFIX.length, sep)
            .toIntOrNull()
            ?: return null
        val grp = LexiconGroup.entries.getOrNull(grpOrd) ?: return null
        if (!grp.inTrack) return null
        return RootId(rawKey.substring(sep + 1))
    }
}

sealed interface RailVisualSlot {
    val stopIndex: Int

    data class Anchor(
        val label: String,
        override val stopIndex: Int,
    ) : RailVisualSlot

    data class Dot(override val stopIndex: Int) : RailVisualSlot
}

data class AlphaRailStop(
    val bubbleLabel: String,
    val listIndex: Int,
)

data class QuickRailModel(
    val stops: List<AlphaRailStop>,
    val slots: List<RailVisualSlot>,
    val endItemIndex: Int,
) {
    val isAvailable: Boolean
        get() = stops.size >= 4 && slots.isNotEmpty()

    fun stopIndexAtY(yPx: Float, railHeightPx: Int): Int {
        val h = railHeightPx.toFloat().coerceAtLeast(1f)
        val fraction = (yPx / h).coerceIn(0f, 0.999f)
        return (fraction * stops.size).toInt().coerceIn(0, stops.lastIndex)
    }

    fun scrollFraction(listState: LazyListState): Float {
        if (stops.size < 2) return 0f
        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo
        if (visible.isEmpty()) return 0f
        val startIdx = stops.first().listIndex
        if (!listState.canScrollBackward &&
            listState.firstVisibleItemIndex <= startIdx
        ) {
            return 0f
        }
        if (!listState.canScrollForward) return 1f
        val avgSize = (visible.sumOf { it.size }.toFloat() / visible.size)
            .coerceAtLeast(1f)
        val currentPos = listState.firstVisibleItemIndex +
            (listState.firstVisibleItemScrollOffset / avgSize).coerceIn(0f, 1f)
        val visibleSpan = info.viewportSize.height / avgSize
        val effectiveEnd = (endItemIndex - visibleSpan + 1f)
            .coerceAtLeast(startIdx + 1f)
        if (currentPos <= startIdx) return 0f
        if (currentPos >= effectiveEnd) return 1f
        val lastStop = stops.lastIndex
        for (i in 0 until lastStop) {
            val lo = stops[i].listIndex.toFloat()
            val rawHi = if (i == lastStop - 1) {
                min(stops[i + 1].listIndex.toFloat(), effectiveEnd)
            } else {
                stops[i + 1].listIndex.toFloat()
            }
            val hi = rawHi.coerceAtLeast(lo + 0.01f)
            if (currentPos <= hi || i == lastStop - 1) {
                val local = ((currentPos - lo) / (hi - lo)).coerceIn(0f, 1f)
                return ((i + local) / lastStop).coerceIn(0f, 1f)
            }
        }
        return 1f
    }

    fun highlightedSlotIndex(fraction: Float): Int {
        if (slots.isEmpty() || stops.isEmpty()) return -1
        val targetStop = (fraction.coerceIn(0f, 1f) * stops.lastIndex)
            .roundToInt()
        var bestIdx = 0
        var bestDist = Int.MAX_VALUE
        for ((idx, slot) in slots.withIndex()) {
            val dist = abs(slot.stopIndex - targetStop)
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = idx
            }
        }
        return bestIdx
    }

    companion object {
        val EMPTY = QuickRailModel(emptyList(), emptyList(), 0)

        fun build(
            sections: List<LexiconSection>,
            sort: LexiconSort,
        ): QuickRailModel {
            val totalExpanded = sections.sumOf {
                if (it.expanded) it.items.size else 0
            }
            if (totalExpanded < 24) return EMPTY
            if (sort == LexiconSort.ALPHABETICAL) {
                return buildAlphabetical(sections)
            }
            return buildProgressRail(sections)
        }

        private fun buildAlphabetical(
            sections: List<LexiconSection>,
        ): QuickRailModel {
            val primaryIdx = sections.indices
                .filter { sections[it].expanded }
                .maxByOrNull { sections[it].items.size }
                ?: return EMPTY
            var startCursor = 1
            for (i in 0 until primaryIdx) {
                val prev = sections[i]
                startCursor += 1 + if (prev.expanded) prev.items.size else 0
            }
            val primary = sections[primaryIdx]
            val stopMap = TreeMap<Char, Int>()
            scanSectionLetters(primary, startCursor, stopMap)
            val stops = stopMap.map { (ch, idx) ->
                AlphaRailStop(ch.toString(), idx)
            }
            if (stops.size < 4) return EMPTY
            val anchorCount = stops.size.coerceAtMost(7)
            val anchorIndices = (0 until anchorCount).map { i ->
                ((stops.lastIndex.toDouble() * i) / (anchorCount - 1))
                    .roundToInt()
            }.distinct()
            val slots = ArrayList<RailVisualSlot>(anchorIndices.size * 2)
            for ((pos, stopIdx) in anchorIndices.withIndex()) {
                val anchorLabel = stops[stopIdx].bubbleLabel
                slots.add(RailVisualSlot.Anchor(anchorLabel, stopIdx))
                if (pos < anchorIndices.lastIndex) {
                    val midIdx = (stopIdx + anchorIndices[pos + 1]) / 2
                    slots.add(RailVisualSlot.Dot(midIdx))
                }
            }
            val endItem = startCursor + primary.items.size - 1
            return QuickRailModel(stops, slots, endItem)
        }

        private fun scanSectionLetters(
            section: LexiconSection,
            startCursor: Int,
            stops: MutableMap<Char, Int>,
        ) {
            var cursor = startCursor
            for (item in section.items) {
                val first = firstBaseLetter(item.root.lemma)
                if (first != null && first !in stops) {
                    stops[first] = cursor
                }
                cursor += 1
            }
        }

        private fun buildProgressRail(
            sections: List<LexiconSection>,
        ): QuickRailModel {
            val totalItems = sections.sumOf { sec ->
                1 + if (sec.expanded) sec.items.size else 0
            }
            val lastItem = (totalItems - 1).coerceAtLeast(0)
            val stepCount = 20
            val stops = (0..stepCount).map { step ->
                val pct = step * 5
                val lbl = when (pct) {
                    0 -> "顶"
                    100 -> "底"
                    else -> "$pct%"
                }
                val target = (lastItem * step) / stepCount
                AlphaRailStop(lbl, target)
            }
            val slots = listOf(
                RailVisualSlot.Anchor("顶", 0),
                RailVisualSlot.Dot(2),
                RailVisualSlot.Anchor("25", 5),
                RailVisualSlot.Dot(7),
                RailVisualSlot.Anchor("50", 10),
                RailVisualSlot.Dot(12),
                RailVisualSlot.Anchor("75", 15),
                RailVisualSlot.Dot(17),
                RailVisualSlot.Anchor("底", 20),
            )
            return QuickRailModel(stops, slots, lastItem)
        }

        private fun firstBaseLetter(lemma: String): Char? {
            for (ch in lemma) {
                val type = Character.getType(ch)
                if (type == Character.NON_SPACING_MARK.toInt()) continue
                if (ch.isLetter()) return ch.uppercaseChar()
            }
            return null
        }
    }
}
