package com.dailysatori.service.articleai

import com.dailysatori.service.externalfavorites.sha256Hex
import com.dailysatori.service.parser.articleNeedsTranslation

enum class ArticleBodyKind { BODY, SOURCE_SUMMARY, PREVIEW, MISSING, ERROR_PAGE, UNVERIFIED }
enum class ArticleLanguage { CHINESE, FOREIGN, UNCERTAIN }
enum class ArticleAiMode(val summary: Boolean, val translation: Boolean) {
    SUMMARY(true, false), TRANSLATION(false, true), BOTH(true, true),
}

data class ArticleBody(
    val kind: ArticleBodyKind,
    val markdown: String = "",
    val hash: String = "",
    val language: ArticleLanguage = ArticleLanguage.UNCERTAIN,
    val units: Int = 0,
) {
    val isShort: Boolean get() = units <= if (language == ArticleLanguage.FOREIGN) 150 else 300

    fun automatic(hasSummary: Boolean): ArticleAiMode? =
        if (kind == ArticleBodyKind.BODY) readingMode(hasSummary) else null

    fun readingMode(hasSummary: Boolean): ArticleAiMode? = when {
        language == ArticleLanguage.CHINESE && !isShort && !hasSummary -> ArticleAiMode.SUMMARY
        language == ArticleLanguage.FOREIGN && !isShort && !hasSummary -> ArticleAiMode.BOTH
        language == ArticleLanguage.FOREIGN -> ArticleAiMode.TRANSLATION
        else -> null
    }
}

private val code = Regex("(?ms)^ {0,3}(`{3,}|~{3,})[^\n]*\n.*?^ {0,3}\\1[ \t]*$|`+[^`\n]+`+")
private val links = Regex("!?\\[([^]\\n]*)]\\([^\\n)]*\\)")
private val noise = Regex(
    """(?im)^\s*(?:advertisement|广告|相关阅读|推荐阅读|share this article|subscribe to our newsletter)\s*[：:]?\s*$""",
)
private val errorPage = Regex(
    """(?im)^\s*(?:[#>*]+\s*)?(?:access denied|403 forbidden|404 not found|page not found|just a moment\.{0,3}|verify (?:that )?you are human|please verify you are human|enable javascript and cookies to continue|☁?\s*cloudflare|访问被拒绝|网页无法访问)\s*[.!！。]?\s*$""",
)
private val han = Regex("[\\p{IsHan}]")
private val kana = Regex("[\\p{IsHiragana}\\p{IsKatakana}]")
private val japaneseHanEvidence = Regex("運転|列車運休|(?:東京|新宿|渋谷|京都|大阪|博多)駅|駅(?:前|構内|弁|周辺)|[働込峠]方")
private val ambiguousHanEvidence = Regex("[駅働込沢辻峠畑咲]|国際|情報|発表")
private val hangul = Regex("[\\p{IsHangul}]")
private val words = Regex("[\\p{L}\\p{M}]+(?:['’-][\\p{L}\\p{M}]+)*")
private val whitespace = Regex("\\s+")

/**
 * [analyzeIncomplete] keeps the origin kind but still derives text, language and fingerprint so a
 * preview the user explicitly asked to process can be queued, cached and displayed as its own version.
 */
fun analyzeArticleBody(
    value: String?,
    origin: ArticleBodyKind = ArticleBodyKind.BODY,
    analyzeIncomplete: Boolean = false,
): ArticleBody {
    if (origin != ArticleBodyKind.BODY && !analyzeIncomplete) return ArticleBody(origin)
    val body = value.orEmpty().replace("\r\n", "\n").replace('\uFEFF', ' ').replace(noise, "").trim()
    if (body.isEmpty()) return ArticleBody(ArticleBodyKind.MISSING)
    if (errorPage.containsMatchIn(body.take(1400)) && body.length < 3000 ||
        Regex("(?i)^<(?:!doctype|html)\\b").containsMatchIn(body)) return ArticleBody(ArticleBodyKind.ERROR_PAGE)
    val prose = body.replace(code, " ").replace(links) { if (it.value.startsWith("!")) " " else it.groupValues[1] }
        .replace(Regex("""(?i)https?://\S+|<[^>\n]+>|&[a-zA-Z]+;"""), " ")
    val chinese = han.findAll(prose).count()
    val japanese = kana.findAll(prose).count()
    val korean = hangul.findAll(prose).count()
    val other = words.findAll(prose.replace(han, " ").replace(kana, " ").replace(hangul, " "))
        .map { it.value }.toList()
    val otherLetters = other.sumOf { it.length }
    val language = when {
        japanese > 0 && (japanese >= 2 || japanese * 8 >= chinese) && japanese * 7 >= chinese -> ArticleLanguage.FOREIGN
        korean > 0 && korean * 2 >= chinese -> ArticleLanguage.FOREIGN
        chinese > 0 && japaneseHanEvidence.containsMatchIn(prose) &&
            (chinese <= 20 || japaneseHanEvidence.findAll(prose).count() >= 2) &&
            otherLetters <= chinese * 3 -> ArticleLanguage.FOREIGN
        other.isEmpty() && (japanese > 0 || korean > 0 ||
            chinese <= 20 && ambiguousHanEvidence.containsMatchIn(prose)) -> ArticleLanguage.UNCERTAIN
        chinese > 0 && articleNeedsTranslation(body) -> ArticleLanguage.FOREIGN
        chinese > 0 && (other.isEmpty() || chinese * 3 >= otherLetters && chinese >= other.size * 2) -> ArticleLanguage.CHINESE
        other.isNotEmpty() && (chinese == 0 || otherLetters >= chinese * 5) -> ArticleLanguage.FOREIGN
        else -> ArticleLanguage.UNCERTAIN
    }
    val kind = if (origin == ArticleBodyKind.BODY) ArticleBodyKind.BODY else origin
    return ArticleBody(kind, body, sha256Hex(fingerprintText(body)), language,
        chinese + other.size + words.findAll(prose.replace(han, " ").replace(Regex("[^ぁ-ヿ가-힣]+"), " ")).count())
}

/** A preview or unverified snapshot still carries paragraphs the user may explicitly process. */
fun ArticleBody.manualProcessable(): Boolean =
    markdown.isNotBlank() && (kind == ArticleBodyKind.BODY || kind == ArticleBodyKind.PREVIEW ||
        kind == ArticleBodyKind.SOURCE_SUMMARY || kind == ArticleBodyKind.UNVERIFIED)

private fun fingerprintText(body: String): String = buildString {
    var offset = 0
    code.findAll(body).forEach { block ->
        append(body.substring(offset, block.range.first).replace(whitespace, " "))
        append(block.value) // Changes in code indentation can change meaning.
        offset = block.range.last + 1
    }
    append(body.substring(offset).replace(whitespace, " "))
}.trim()

data class ArticleModelLimits(val context: Int, val output: Int)
data class ArticleAiInput(val body: String, val partial: Boolean, val estimatedTokens: Int, val maxOutput: Int)

private const val ARTICLE_HARD_LIMIT = 12_000
private const val PROMPT_RESERVE = 1024
private const val SUMMARY_RESERVE = 768
private const val JSON_RESERVE = 128

// No matching tokenizer ships in the app. Byte length conservatively bounds byte-level tokenizers.
fun articleTokenEstimate(value: String): Int = value.encodeToByteArray().size

fun articleAiInput(body: String, mode: ArticleAiMode, limits: ArticleModelLimits): ArticleAiInput? {
    val fixed = JSON_RESERVE + if (mode.summary) SUMMARY_RESERVE else 0
    if (fixed > limits.output) return null
    // Translation may grow (Chinese UTF-8, escaping, preserved Markdown). Reserve twice the input.
    val limit = minOf(ARTICLE_HARD_LIMIT,
        (limits.context - PROMPT_RESERVE - fixed) / if (mode.translation) 3 else 1,
        if (mode.translation) (limits.output - fixed) / 2 else ARTICLE_HARD_LIMIT)
    if (limit <= 0) return null
    val tokens = articleTokenEstimate(body)
    val prefix = if (tokens <= limit) body else completeArticlePrefix(body, limit) ?: return null
    val used = articleTokenEstimate(prefix)
    return ArticleAiInput(prefix, tokens > limit, used, fixed + if (mode.translation) used * 2 else 0)
}

/** Partition an over-budget document without treating a valid prefix as a full translation. */
internal fun articleReadingInputs(
    body: String,
    mode: ArticleAiMode,
    limits: ArticleModelLimits,
): List<Pair<ArticleAiMode, ArticleAiInput>>? {
    articleAiInput(body, mode, limits)?.takeUnless { it.partial }?.let { return listOf(mode to it) }
    val inputs = mutableListOf<Pair<ArticleAiMode, ArticleAiInput>>()
    var rest = body
    while (rest.isNotBlank()) {
        val part = articleAiInput(rest, mode, limits) ?: return null
        inputs += mode to part.copy(partial = false)
        rest = rest.substring(part.body.length).trimStart()
    }
    return inputs.takeIf { it.isNotEmpty() }
}

private fun completeArticlePrefix(body: String, limit: Int): String? {
    val chars = body.take(limit + 1)
    val fenced = code.findAll(body).map { it.range }.toList()
    val ends = Regex("""\n\s*\n|[。！？!?](?=\s|[^\p{ASCII}]|$)|\.(?=\s|$)""").findAll(chars)
    return ends.map { if (it.value.startsWith("\n")) it.range.first else it.range.last + 1 }
        .filter { end -> fenced.none { end in it } }
        .lastOrNull { articleTokenEstimate(body.substring(0, it)) <= limit }
        ?.let { body.substring(0, it).trimEnd() }
}
