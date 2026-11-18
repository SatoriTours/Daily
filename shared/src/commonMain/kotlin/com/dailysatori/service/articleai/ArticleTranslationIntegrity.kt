package com.dailysatori.service.articleai

private val codeFence = Regex("""(?ms)^ {0,3}(`{3,}|~{3,})[^\n]*\n(.*?)^ {0,3}\1[ \t]*$""")
private val inlineCode = Regex("""(`+)([^`\n]+)\1""")
private val fenceLine = Regex("""(?m)^ {0,3}(?:`{3,}|~{3,})""")
private val webLink = Regex("""https?://[^\s<>"`\])]+""", RegexOption.IGNORE_CASE)
private val versionOrDecimal = Regex("""(?<![0-9.])(?:[0-9]+\.)+[0-9]+(?![0-9])""")
private val hanText = Regex("[\\p{IsHan}]")
private val paragraphBreak = Regex("""\n[ \t]*\n+""")
private val translatedOmission = Regex("""(?im)^\s*(?:\[|【|（)?(?:其余(?:略|省略)|(?:下文|后文)省略|translation omitted|rest omitted)(?:\]|】|）|[。.]|\s)*$""")

/** Cheap rejection signals, not proof of semantic completeness or translation quality. */
internal fun translationLooksComplete(source: String, translation: String): Boolean {
    val original = source.replace("\r\n", "\n")
    val result = translation.replace("\r\n", "\n")
    val sourceFences = fencedSnippets(original)
    val translatedFences = fencedSnippets(result)
    if (!translatedFences.containsAll(sourceFences) ||
        fenceLine.findAll(result.replace(codeFence, "")).any()) return false

    val sourceProse = original.replace(codeFence, "")
    val resultProse = result.replace(codeFence, "")
    val sourceSnippets = inlineCode.findAll(sourceProse).map { it.groupValues[2].trim() }.toSet()
    val resultSnippets = inlineCode.findAll(resultProse).map { it.groupValues[2].trim() }.toSet()
    if (!resultSnippets.containsAll(sourceSnippets) || !webTargets(resultProse).containsAll(webTargets(sourceProse)))
        return false
    val sourceText = proseWithoutAnchors(sourceProse)
    val resultText = proseWithoutAnchors(resultProse)
    if (!versionOrDecimal.findAll(normalizeDigitsAndDots(resultText)).map { it.value }.toSet()
            .containsAll(versionOrDecimal.findAll(normalizeDigitsAndDots(sourceText)).map { it.value }.toSet())) return false
    if (sourceText.count(Char::isLetter) >= 4 && !hanText.containsMatchIn(resultText)) return false
    if (translatedOmission.containsMatchIn(resultText) && !translatedOmission.containsMatchIn(sourceText)) return false
    val blocks = proseBlocks(sourceText, 30)
    // Permit natural paragraph splits and merges, but not a short recap replacing many full sections.
    if (blocks >= 4 && proseBlocks(resultText, 8) * 2 < blocks) return false
    return true
}

internal fun summaryContainsChinese(summary: String): Boolean =
    hanText.containsMatchIn(proseWithoutAnchors(summary.replace(codeFence, "")))

private fun fencedSnippets(text: String): Set<String> =
    codeFence.findAll(text).map { it.groupValues[2].trimEnd() }.toSet()

private fun webTargets(text: String): Set<String> =
    webLink.findAll(text).map { it.value.trimEnd('.', ',', ';', '。', '，', '；') }.toSet()

private fun proseWithoutAnchors(value: String): String = value.replace(inlineCode, "").replace(webLink, "")

private fun proseBlocks(value: String, minimumLetters: Int): Int =
    value.split(paragraphBreak).count { block -> block.count(Char::isLetter) >= minimumLetters }

private fun normalizeDigitsAndDots(value: String): String = value.map {
    when (it) {
        in '０'..'９' -> '0' + (it - '０')
        '．' -> '.'
        else -> it
    }
}.joinToString("")
