package org.plos.edu.lang.engine

class PassageCorpus(
    private val morphology: MorphologyEngine,
    private val mastery: MasterySnapshot,
) {
    fun filterAndSearch(
        passages: List<ImmersionPassage>,
        genre: PassageGenre,
        rawQuery: String,
        sort: PassageSort,
    ): List<PassageSearchHit> {
        val genreFiltered = passages.filter { genre.matches(it.isDialogue) }
        val query = rawQuery.trim()
        val hits = if (query.isEmpty()) {
            genreFiltered.map { p -> PassageSearchHit(p, null, null) }
        } else {
            val lowered = query.lowercase()
            val queryRootId = morphology.resolveWord(query, mastery).root?.id
            genreFiltered.mapNotNull { p ->
                matchPassage(p, lowered, queryRootId)
            }
        }
        return when (sort) {
            PassageSort.GRADIENT -> hits
            PassageSort.COVERAGE_DESC -> hits.sortedByDescending {
                it.passage.knownCoveragePercent
            }
            PassageSort.COVERAGE_ASC -> hits.sortedBy {
                it.passage.knownCoveragePercent
            }
        }
    }

    private fun matchPassage(
        passage: ImmersionPassage,
        loweredQuery: String,
        queryRootId: RootId?,
    ): PassageSearchHit? {
        val headerHit = passage.title.lowercase().contains(loweredQuery) ||
            passage.levelTag.lowercase().contains(loweredQuery) ||
            passage.sceneSummary.lowercase().contains(loweredQuery)
        for (line in passage.lines) {
            val textHit = line.text.lowercase().contains(loweredQuery) ||
                line.sourceTranslation.lowercase().contains(loweredQuery)
            val rootHit = if (!textHit && queryRootId != null) {
                morphology.tokenizeLine(line.text, mastery)
                    .any { it.rootId == queryRootId }
            } else {
                false
            }
            if (textHit || rootHit) {
                val snippet = if (line.sourceTranslation.isNotEmpty()) {
                    "${line.text}  ${line.sourceTranslation}"
                } else {
                    line.text
                }
                return PassageSearchHit(
                    passage = passage,
                    matchedLineIndex = line.lineIndex,
                    matchedSnippet = snippet,
                )
            }
        }
        if (headerHit) {
            return PassageSearchHit(passage, null, null)
        }
        return null
    }
}
