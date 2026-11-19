package com.dailysatori.service.i18n

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The fast startup reader must never disagree with the YAML parser it replaces. */
class I18nTranslationParserTest {
    @Test fun fastReaderMatchesYamlParserForShippedResources() {
        val files = listOf(
            "../app/src/main/assets/i18n/zh.yaml",
            "../app/src/main/assets/i18n/en.yaml",
            "src/commonMain/resources/i18n/zh.yaml",
            "src/commonMain/resources/i18n/en.yaml",
        ).map(::File).filter { it.isFile }
        assertEquals(4, files.size, "translation resources are missing: ${files.map { it.path }}")
        files.forEach { file ->
            val text = file.readText()
            val fast = I18nTranslationParser.parse(text)
            assertNotNull(fast, "fast reader rejected ${file.path}")
            assertEquals(yamlNodeToMap(Yaml.default.parseToYamlNode(text)), fast, file.path)
        }
    }

    @Test fun flatBundleRoundTripsToTheSameMapAsYaml() {
        for (lang in listOf("zh", "en")) {
            val yaml = File("../app/src/main/assets/i18n/$lang.yaml").readText()
            val parsed = assertNotNull(I18nTranslationParser.parse(yaml), lang)
            val bundle = I18nFlatBundle.encode(parsed)
            assertEquals(yamlNodeToMap(Yaml.default.parseToYamlNode(yaml)), I18nFlatBundle.decode(bundle), lang)
        }
    }

    @Test fun committedFlatBundleStaysInSyncWithItsYamlSource() {
        for (lang in listOf("zh", "en")) {
            val yaml = File("../app/src/main/assets/i18n/$lang.yaml").readText()
            val bundle = File("../app/src/main/assets/i18n/$lang.txt")
            assertNotNull(bundle.takeIf { it.isFile }, "missing generated bundle for $lang")
            assertEquals(yamlNodeToMap(Yaml.default.parseToYamlNode(yaml)), I18nFlatBundle.decode(bundle.readText()), lang)
        }
    }

    @Test fun keysAndEscapesKeepTheDocumentedValues() {
        val text = """
            plain:
              value: 正在下载 {current}/{total}
              quoted: "今天 %d 项"
              single: '标签: "{tag}"'
              escaped: "第一行\n第二行"
            inline: 单项
        """.trimIndent()
        val parsed = assertNotNull(I18nTranslationParser.parse(text))
        @Suppress("UNCHECKED_CAST")
        val plain = parsed["plain"] as Map<String, Any>
        assertEquals("正在下载 {current}/{total}", plain["value"])
        assertEquals("今天 %d 项", plain["quoted"])
        assertEquals("标签: \"{tag}\"", plain["single"])
        assertEquals("第一行\n第二行", plain["escaped"])
        assertEquals("单项", parsed["inline"])
    }

    @Test fun unsupportedSyntaxReportsFallbackInsteadOfGuessing() {
        assertNull(I18nTranslationParser.parse("group:\n    deep: value\n"), "four-space nesting")
        assertNull(I18nTranslationParser.parse("group:\n  - item\n"), "sequence entry")
        assertNull(I18nTranslationParser.parse("group: &anchor value\n"), "anchor")
        assertNull(I18nTranslationParser.parse("group:\n  key: \"unterminated\n"), "unterminated quote")
        assertNull(I18nTranslationParser.parse("group:\n  key: \"bad \\q escape\"\n"), "unknown escape")
        assertNull(I18nTranslationParser.parse("group:\n"), "key without children")
        assertNull(I18nTranslationParser.parse("group:\n  nested:\n"), "nested key without children")
    }

    private fun yamlNodeToMap(node: YamlNode): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        if (node is YamlMap) {
            for ((key, value) in node.entries) result[key.content] = yamlValueToAny(value)
        }
        return result
    }

    private fun yamlValueToAny(node: YamlNode): Any = when (node) {
        is YamlMap -> {
            val map = mutableMapOf<String, Any>()
            for ((key, value) in node.entries) map[key.content] = yamlValueToAny(value)
            map
        }
        is YamlScalar -> node.content
        else -> node.toString()
    }
}
