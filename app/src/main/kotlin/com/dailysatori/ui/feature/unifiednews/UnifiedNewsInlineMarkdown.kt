package com.dailysatori.ui.feature.unifiednews

private val BoldAsteriskRegex = Regex("""\*\*(.*?)\*\*""")
private val BoldUnderscoreRegex = Regex("""__(.*?)__""")
private val InlineCodeRegex = Regex("""`([^`]*)`""")
private val PunctuationSpacingRegex = Regex("""\s+([。！？；：，,.!?;:])""")

internal fun String.withoutUnifiedNewsBasicMarkdown(): String = this
    .replace(BoldAsteriskRegex, "$1")
    .replace(BoldUnderscoreRegex, "$1")
    .replace(InlineCodeRegex, "$1")

internal fun String.normalizeUnifiedNewsPunctuationSpacing(): String =
    replace(PunctuationSpacingRegex, "$1")
