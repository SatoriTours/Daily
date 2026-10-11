package com.dailysatori.service.hotnews

import org.jsoup.Jsoup

internal actual fun parseGithubHotNews(html: String): List<HotNewsItem> =
    Jsoup.parse(html, "https://github.com").select("article.Box-row").mapIndexedNotNull { index, article ->
        val link = article.selectFirst("h2 a") ?: return@mapIndexedNotNull null
        val id = link.attr("href").trim('/')
        if (!Regex("[\\w.-]+/[\\w.-]+").matches(id)) return@mapIndexedNotNull null
        val description = article.selectFirst("p")?.text().orEmpty()
        val growth = article.selectFirst("span.float-sm-right")?.text()?.let {
            Regex("[\\d,]+").find(it)?.value?.replace(",", "")
        }
        HotNewsItem(id, HotNewsPlatform.GITHUB, index + 1, id, "https://github.com/$id", description, heat = growth)
    }
