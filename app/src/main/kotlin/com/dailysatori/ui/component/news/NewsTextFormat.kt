package com.dailysatori.ui.component.news

private val NewsIntroWhitespaceRegex = Regex("\\s+")

internal fun String.cleanNewsIntroText(): String = trim()
    .replace(NewsIntroWhitespaceRegex, " ")
    .take(160)
