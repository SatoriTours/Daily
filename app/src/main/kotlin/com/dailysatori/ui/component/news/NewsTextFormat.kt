package com.dailysatori.ui.component.news

private val NewsIntroWhitespaceRegex = Regex("""\s+""")
private val NewsIntroLabelRegex = Regex("""\*\*[^*\n]{1,24}[：:]\*\*\s*""")
private val NewsIntroBoldRegex = Regex("""\*\*(.*?)\*\*""")

internal fun String.cleanNewsIntroText(): String = replace(NewsIntroLabelRegex, "")
    .replace(NewsIntroBoldRegex, "$1")
    .trim()
    .replace(NewsIntroWhitespaceRegex, " ")
    .take(160)
