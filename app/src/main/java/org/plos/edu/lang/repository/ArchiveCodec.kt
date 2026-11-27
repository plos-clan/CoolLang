package org.plos.edu.lang.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import org.plos.edu.lang.Constants
import org.plos.edu.lang.engine.ExampleDto
import org.plos.edu.lang.engine.ExampleItem
import org.plos.edu.lang.engine.GrammarExample
import org.plos.edu.lang.engine.GrammarExampleDto
import org.plos.edu.lang.engine.LanguagePackPayload
import org.plos.edu.lang.engine.ProgressArchive

object CsvCodec {
    fun encode(items: List<String>): String =
        items.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")

    fun decode(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun encodeLines(items: List<String>): String =
        items.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    fun decodeLines(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }
}

abstract class ArchiveCodec {
    abstract fun decodeLanguagePack(rawJson: String): LanguagePackPayload

    abstract fun encodeProgress(archive: ProgressArchive): String

    abstract fun decodeProgress(rawJson: String): ProgressArchive

    abstract fun encodeBullets(items: List<String>): String

    abstract fun decodeBullets(raw: String): List<String>

    abstract fun encodeExamples(items: List<GrammarExampleDto>): String

    abstract fun decodeExamples(raw: String): List<GrammarExample>

    abstract fun encodeUnitExamples(items: List<ExampleDto>): String

    abstract fun decodeUnitExamples(raw: String): List<ExampleItem>
}

@Singleton
class JsonArchiveCodec @Inject constructor() : ArchiveCodec() {
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun decodeLanguagePack(rawJson: String): LanguagePackPayload =
        json.decodeFromString(LanguagePackPayload.serializer(), rawJson)

    override fun encodeProgress(archive: ProgressArchive): String =
        json.encodeToString(ProgressArchive.serializer(), archive)

    override fun decodeProgress(rawJson: String): ProgressArchive =
        json.decodeFromString(ProgressArchive.serializer(), rawJson)

    override fun encodeBullets(items: List<String>): String =
        json.encodeToString(items)

    override fun decodeBullets(raw: String): List<String> =
        json.decodeFromString(raw)

    override fun encodeExamples(items: List<GrammarExampleDto>): String =
        json.encodeToString(items)

    override fun decodeExamples(raw: String): List<GrammarExample> {
        val decoded: List<GrammarExampleDto> = json.decodeFromString(raw)
        return decoded.map { dto ->
            GrammarExample(
                targetText = dto.targetText,
                sourceText = dto.sourceText,
                note = dto.note,
            )
        }
    }

    override fun encodeUnitExamples(items: List<ExampleDto>): String =
        json.encodeToString(items)

    override fun decodeUnitExamples(raw: String): List<ExampleItem> {
        if (raw.isBlank()) return emptyList()
        val decoded: List<ExampleDto> = json.decodeFromString(raw)
        return decoded.map { dto ->
            ExampleItem(
                text = dto.text,
                focusWord = dto.focusWord,
                meaning = dto.meaning,
            )
        }
    }
}

@Singleton
class PackAssetLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    val codec: ArchiveCodec,
) {
    fun loadBundledPacks(): List<LanguagePackPayload> {
        val dir = Constants.PACK_ASSET_DIR
        val names = context.assets.list(dir)?.toList().orEmpty()
        val jsonFiles = names.filter { it.endsWith(".json") }.sorted()
        return jsonFiles.map { fileName ->
            val raw = context.assets.open("$dir/$fileName")
                .bufferedReader()
                .use { it.readText() }
            codec.decodeLanguagePack(raw)
        }
    }
}
