package org.plos.edu.lang.engine

abstract class MorphologyEngine {
    abstract fun resolveWord(
        rawSurface: String,
        mastery: MasterySnapshot,
    ): WordResolution

    abstract fun tokenizeLine(
        line: String,
        mastery: MasterySnapshot,
    ): List<TokenSlice>

    abstract fun queryLexicon(
        track: StudyTrack,
        query: String,
        mastery: MasterySnapshot,
    ): LexiconSearchResult

    abstract fun extractRootsFromText(
        rawText: String,
        mastery: MasterySnapshot,
    ): List<RootOverview>
}

private data class RuleCandidate(
    val root: RootEntry,
    val rule: AffixRule,
)

private data class ResolutionSpec(
    val surface: String,
    val rootId: RootId,
    val matchedLexeme: LexemeEntry?,
    val breakdown: String,
)

class RuleLexiconMorphologyEngine(
    private val orderedRoots: List<RootEntry>,
    lexemes: List<LexemeEntry>,
    rules: List<AffixRule>,
) : MorphologyEngine() {
    private val rootById: Map<RootId, RootEntry> =
        orderedRoots.associateBy { it.id }
    private val rootByLemma: Map<String, RootEntry> =
        buildRootSurfaceIndex(orderedRoots)
    private val lexemeBySurface: Map<String, LexemeEntry> =
        buildLexemeSurfaceIndex(lexemes)
    private val membersByRoot: Map<RootId, List<LexemeEntry>> =
        lexemes.groupBy { it.rootId }
    private val prioritizedRules: List<AffixRule> = rules.sortedByDescending {
        it.prefix.length + it.suffix.length
    }
    private val cliticSuffixes: List<String> = rules
        .filter {
            it.prefix.isEmpty() &&
                it.suffix.isNotEmpty() &&
                it.restoredEnding.isEmpty()
        }
        .map { it.suffix }

    override fun resolveWord(
        rawSurface: String,
        mastery: MasterySnapshot,
    ): WordResolution {
        val cleaned = normalizeToken(rawSurface)
        if (cleaned.isEmpty()) return unresolved(rawSurface)
        val matched = resolveNormalized(cleaned, rawSurface, mastery)
        if (matched != null) return matched
        val yoRelaxed = cleaned.replace('ё', 'е')
        if (yoRelaxed != cleaned) {
            val alt = resolveNormalized(yoRelaxed, rawSurface, mastery)
            if (alt != null) return alt
        }
        return unresolved(rawSurface)
    }

    override fun tokenizeLine(
        line: String,
        mastery: MasterySnapshot,
    ): List<TokenSlice> {
        if (line.isEmpty()) return emptyList()
        val slices = ArrayList<TokenSlice>(16)
        var cursor = 0
        val length = line.length
        while (cursor < length) {
            val wordEnd = scanWordEnd(line, cursor)
            if (wordEnd > cursor) {
                val wordText = line.substring(cursor, wordEnd)
                val resolved = resolveWord(wordText, mastery)
                val slice = TokenSlice(
                    rawText = wordText,
                    lookupWord = normalizeToken(wordText),
                    rootId = resolved.root?.id,
                    stage = resolved.stage,
                )
                slices.add(slice)
                cursor = wordEnd
            } else {
                val punctEnd = scanNonWordEnd(line, cursor)
                val punctText = line.substring(cursor, punctEnd)
                val punctSlice = TokenSlice(
                    rawText = punctText,
                    lookupWord = "",
                    rootId = null,
                    stage = MasteryStage.MASTERED,
                )
                slices.add(punctSlice)
                cursor = punctEnd
            }
        }
        return slices
    }

    override fun queryLexicon(
        track: StudyTrack,
        query: String,
        mastery: MasterySnapshot,
    ): LexiconSearchResult {
        val scopedRoots = track.rootIds.mapNotNull { rootById[it] }
        val trimmed = normalizeToken(query)
        if (trimmed.isEmpty()) {
            return LexiconSearchResult(
                inTrack = scopedRoots.map { toOverview(it, mastery) },
            )
        }
        val scopedSet = track.rootIds.toHashSet()
        val resolvedId = resolveWord(trimmed, mastery).root?.id
        val inTrackHits = searchPool(scopedRoots, trimmed, resolvedId, mastery)
        if (track.id.isAll) return LexiconSearchResult(inTrackHits)
        val extraPool = orderedRoots.filter { it.id !in scopedSet }
        val extraHits = searchPool(extraPool, trimmed, resolvedId, mastery)
        return LexiconSearchResult(inTrackHits, extraHits)
    }

    override fun extractRootsFromText(
        rawText: String,
        mastery: MasterySnapshot,
    ): List<RootOverview> {
        if (rawText.isBlank()) return emptyList()
        val seenIds = LinkedHashSet<RootId>()
        var cursor = 0
        val len = rawText.length
        while (cursor < len) {
            val wordEnd = scanWordEnd(rawText, cursor)
            if (wordEnd > cursor) {
                val token = rawText.substring(cursor, wordEnd)
                val root = resolveWord(token, mastery).root
                if (root != null) seenIds.add(root.id)
                cursor = wordEnd
            } else {
                cursor = scanNonWordEnd(rawText, cursor)
            }
        }
        return seenIds.mapNotNull { id ->
            rootById[id]?.let { toOverview(it, mastery) }
        }
    }

    private fun searchPool(
        pool: List<RootEntry>,
        keyword: String,
        resolvedId: RootId?,
        mastery: MasterySnapshot,
    ): List<RootOverview> {
        val ranked = ArrayList<Pair<Int, RootEntry>>()
        for (root in pool) {
            val tier = matchTier(root, keyword, resolvedId)
            if (tier > 0) ranked.add(tier to root)
        }
        ranked.sortBy { it.first }
        return ranked.map { toOverview(it.second, mastery) }
    }

    private fun matchTier(
        root: RootEntry,
        keyword: String,
        resolvedId: RootId?,
    ): Int {
        val normLemma = normalizeToken(root.lemma)
        if (normLemma == keyword) return 1
        if (root.id == resolvedId) return 2
        if (normLemma.startsWith(keyword)) return 3
        return if (matchesRoot(root, keyword)) 4 else 0
    }

    private fun resolveNormalized(
        cleaned: String,
        rawSurface: String,
        mastery: MasterySnapshot,
    ): WordResolution? {
        val directLexeme = lexemeBySurface[cleaned]
        if (directLexeme != null) {
            return buildFromLexeme(rawSurface, directLexeme, mastery)
        }
        val directRoot = rootByLemma[cleaned]
        if (directRoot != null) {
            val spec = ResolutionSpec(
                surface = rawSurface,
                rootId = directRoot.id,
                matchedLexeme = null,
                breakdown = directRoot.lemma,
            )
            return buildResolution(spec, null, mastery)
        }
        val strippedMatch = matchViaRules(cleaned)
        if (strippedMatch != null) {
            return buildFromRule(rawSurface, strippedMatch, mastery)
        }
        val redupMatch = matchReduplication(cleaned)
        if (redupMatch != null) {
            val note = "${redupMatch.lemma} (重叠构词)"
            val spec = ResolutionSpec(rawSurface, redupMatch.id, null, note)
            return buildResolution(spec, null, mastery)
        }
        return matchWithClitic(cleaned, mastery, rawSurface)
    }

    private fun matchesRoot(root: RootEntry, keyword: String): Boolean {
        if (normalizeToken(root.lemma).contains(keyword)) return true
        if (root.spokenForms.any { normalizeToken(it).contains(keyword) }) {
            return true
        }
        if (root.senses.matchesSearch(keyword)) return true
        val members = membersByRoot[root.id].orEmpty()
        return members.any { member ->
            normalizeToken(member.surface).contains(keyword) ||
                member.meaning.lowercase().contains(keyword) ||
                member.counterpart.lowercase().contains(keyword)
        }
    }

    private fun toOverview(
        root: RootEntry,
        mastery: MasterySnapshot,
    ): RootOverview {
        val members = membersByRoot[root.id].orEmpty()
        val stage = mastery.stageOf(root.id)
        val disp = mastery.dispositionOf(root.id)
        return RootOverview(
            root = root,
            members = members,
            stage = stage,
            disposition = disp,
            planStage = PlanStage.resolve(disp, stage),
            stabilityDays = mastery.stabilityOf(root.id),
            familiarityRatio = mastery.familiarityOf(root.id),
        )
    }

    private fun buildFromLexeme(
        surface: String,
        lexeme: LexemeEntry,
        mastery: MasterySnapshot,
    ): WordResolution {
        val spec = ResolutionSpec(
            surface = surface,
            rootId = lexeme.rootId,
            matchedLexeme = lexeme,
            breakdown = lexeme.breakdown,
        )
        return buildResolution(spec, null, mastery)
    }

    private fun buildResolution(
        spec: ResolutionSpec,
        rule: AffixRule?,
        mastery: MasterySnapshot,
    ): WordResolution {
        val rootId = spec.rootId
        val stage = mastery.stageOf(rootId)
        val disp = mastery.dispositionOf(rootId)
        return WordResolution(
            surface = spec.surface,
            root = rootById[rootId],
            matchedLexeme = spec.matchedLexeme,
            breakdown = spec.breakdown,
            appliedRule = rule,
            familyMembers = membersByRoot[rootId].orEmpty(),
            stage = stage,
            disposition = disp,
            planStage = PlanStage.resolve(disp, stage),
            familiarityRatio = mastery.familiarityOf(rootId),
        )
    }

    private fun buildFromRule(
        surface: String,
        match: RuleCandidate,
        mastery: MasterySnapshot,
    ): WordResolution {
        val pre = if (match.rule.prefix.isNotEmpty()) {
            "${match.rule.prefix}- + "
        } else {
            ""
        }
        val suf = if (match.rule.suffix.isNotEmpty()) {
            " + -${match.rule.suffix}"
        } else {
            ""
        }
        val formula = "$pre${match.root.lemma}$suf"
        val spec = ResolutionSpec(surface, match.root.id, null, formula)
        return buildResolution(spec, match.rule, mastery)
    }

    private fun matchViaRules(word: String): RuleCandidate? {
        for (rule in prioritizedRules) {
            val candidate = tryApplyRule(word, rule)
            if (candidate != null) return candidate
        }
        return matchNestedRules(word)
    }

    private fun matchNestedRules(word: String): RuleCandidate? {
        if (word.length < 5) return null
        for (outer in prioritizedRules) {
            val peeled = peelStem(word, outer) ?: continue
            if (peeled.length < 3 || peeled == word) continue
            val directLex = lexemeBySurface[peeled]
            if (directLex != null) {
                val root = rootById[directLex.rootId] ?: continue
                return RuleCandidate(root = root, rule = outer)
            }
            for (inner in prioritizedRules) {
                if (inner.id == outer.id) continue
                val hit = tryApplyRule(peeled, inner)
                if (hit != null) {
                    return RuleCandidate(root = hit.root, rule = outer)
                }
            }
        }
        return null
    }

    private fun peelStem(word: String, rule: AffixRule): String? {
        val minLen = rule.prefix.length + rule.suffix.length + 2
        if (word.length < minLen) return null
        if (rule.prefix.isNotEmpty() && !word.startsWith(rule.prefix)) {
            return null
        }
        if (rule.suffix.isNotEmpty() && !word.endsWith(rule.suffix)) {
            return null
        }
        val stemStart = rule.prefix.length
        val stemEnd = word.length - rule.suffix.length
        if (stemEnd <= stemStart) return null
        val rawStem = word.substring(stemStart, stemEnd)
        val restored = rule.restoredInitial + rawStem + rule.restoredEnding
        return if (restored.length >= 2) restored else null
    }

    private fun tryApplyRule(word: String, rule: AffixRule): RuleCandidate? {
        val candidateStem = peelStem(word, rule) ?: return null
        val foundRoot = rootByLemma[candidateStem] ?: return null
        return RuleCandidate(root = foundRoot, rule = rule)
    }

    private fun matchReduplication(word: String): RootEntry? {
        val dashIndex = word.indexOf('-')
        if (dashIndex <= 0 || dashIndex >= word.lastIndex) return null
        val left = word.substring(0, dashIndex)
        val right = word.substring(dashIndex + 1)
        return rootByLemma[left]
            ?: rootByLemma[right]
            ?: matchViaRules(left)?.root
            ?: matchViaRules(right)?.root
    }

    private fun matchWithClitic(
        cleaned: String,
        mastery: MasterySnapshot,
        rawSurface: String,
    ): WordResolution? {
        for (clitic in cliticSuffixes) {
            val canStrip = cleaned.length > clitic.length + 2 &&
                cleaned.endsWith(clitic)
            if (!canStrip) continue
            val baseWord = cleaned.substring(0, cleaned.length - clitic.length)
            val lexeme = lexemeBySurface[baseWord]
            if (lexeme != null) {
                val root = rootById[lexeme.rootId] ?: continue
                val formula = "${lexeme.breakdown} + -$clitic"
                val spec = ResolutionSpec(rawSurface, root.id, null, formula)
                return buildResolution(spec, null, mastery)
            }
            val stripped = matchViaRules(baseWord)
            if (stripped != null) {
                val base = buildFromRule(rawSurface, stripped, mastery)
                return base.copy(breakdown = "${base.breakdown} + -$clitic")
            }
        }
        return null
    }

    private fun buildRootSurfaceIndex(
        roots: List<RootEntry>,
    ): Map<String, RootEntry> {
        val index = HashMap<String, RootEntry>(roots.size * 2)
        for (root in roots) {
            val primary = normalizeToken(root.lemma)
            if (primary.isNotEmpty()) index[primary] = root
            val yoAlt = primary.replace('ё', 'е')
            if (yoAlt.isNotEmpty() && !index.containsKey(yoAlt)) {
                index[yoAlt] = root
            }
            for (form in root.spokenForms) {
                val key = normalizeToken(form)
                if (key.isNotEmpty() && !index.containsKey(key)) {
                    index[key] = root
                }
            }
        }
        return index
    }

    private fun buildLexemeSurfaceIndex(
        lexemes: List<LexemeEntry>,
    ): Map<String, LexemeEntry> {
        val index = HashMap<String, LexemeEntry>(lexemes.size * 2)
        for (lex in lexemes) {
            val key = normalizeToken(lex.surface)
            if (key.isNotEmpty()) index[key] = lex
            val yoAlt = key.replace('ё', 'е')
            if (yoAlt.isNotEmpty() && !index.containsKey(yoAlt)) {
                index[yoAlt] = lex
            }
        }
        return index
    }

    private fun unresolved(surface: String): WordResolution = WordResolution(
        surface = surface,
        root = null,
        matchedLexeme = null,
        breakdown = surface,
        appliedRule = null,
        familyMembers = emptyList(),
        stage = MasteryStage.UNSEEN,
        disposition = WordDisposition.AUTO,
        planStage = PlanStage.UNSEEN,
        familiarityRatio = 0f,
    )

    private fun normalizeToken(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            if (isCombiningMark(ch)) continue
            val mapped = if (ch == '’') '\'' else ch
            sb.append(mapped)
        }
        return sb.toString()
            .trim { !isBaseWordChar(it) }
            .lowercase()
    }

    private fun scanWordEnd(text: String, start: Int): Int {
        var idx = start
        val end = text.length
        while (idx < end) {
            val ch = text[idx]
            val isJoiner = ch == '-' || ch == '\'' || ch == '’'
            val internalJoin = isJoiner &&
                idx > start &&
                idx + 1 < end &&
                Character.isLetterOrDigit(text[idx + 1])
            val validTokenChar = Character.isLetterOrDigit(ch) ||
                isCombiningMark(ch) ||
                internalJoin
            if (validTokenChar) {
                idx++
            } else {
                break
            }
        }
        return idx
    }

    private fun scanNonWordEnd(text: String, start: Int): Int {
        var idx = start
        val end = text.length
        while (idx < end && !Character.isLetterOrDigit(text[idx])) {
            idx++
        }
        return idx
    }

    private fun isCombiningMark(ch: Char): Boolean =
        Character.getType(ch) == Character.NON_SPACING_MARK.toInt()

    private fun isBaseWordChar(ch: Char): Boolean =
        Character.isLetterOrDigit(ch) || ch == '-'
}
