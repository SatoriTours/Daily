package com.dailysatori.ui.feature.remotenews

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.ui.component.news.MagazineNewsCard
import com.dailysatori.ui.component.news.cleanNewsIntroText

@Composable
fun RemoteArticleSummaryCard(article: RemoteArticle, onClick: () -> Unit) {
    MagazineNewsCard(
        title = article.title.orEmpty(),
        summary = remoteArticleIntroText(article),
        meta = remoteArticleMetaText(article),
        coverUrl = article.coverUrl,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun remoteArticleIntroText(article: RemoteArticle): String? = listOfNotNull(
    article.summary?.cleanNewsIntroText(),
    article.viewpoints.firstOrNull { it.isNotBlank() }?.cleanNewsIntroText(),
    article.content?.cleanNewsIntroText(),
    article.feedName?.cleanNewsIntroText(),
    article.domain?.cleanNewsIntroText(),
).firstOrNull { it.isNotBlank() }

private fun remoteArticleMetaText(article: RemoteArticle): String? = listOfNotNull(
    remoteArticleTimeText(article),
    article.feedName?.takeIf { it.isNotBlank() },
    article.domain?.takeIf { it.isNotBlank() },
).joinToString(" · ").takeIf { it.isNotBlank() }

private fun remoteArticleTimeText(article: RemoteArticle): String? {
    val sourceTime = article.publishedAt ?: article.createdAt ?: article.processedAt
    return sourceTime?.replace('T', ' ')?.take(16)?.takeIf { it.isNotBlank() }
}
