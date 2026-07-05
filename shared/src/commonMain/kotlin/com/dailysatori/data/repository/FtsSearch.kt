package com.dailysatori.data.repository

internal fun String.toFtsPhraseQuery(): String =
    trim()
        .replace("\"", "\"\"")
        .let { "\"$it\"" }
