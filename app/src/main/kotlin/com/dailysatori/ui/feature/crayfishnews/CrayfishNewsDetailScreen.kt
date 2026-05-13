package com.dailysatori.ui.feature.crayfishnews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailysatori.service.crayfishnews.CrayfishNewsDetail
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown

@Composable
fun CrayfishNewsDetailScreen(
    news: CrayfishNewsDetail,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val title = news.filename.removeSuffix(".md")
        .replace("news-summary-", "")
        .replace("dji-news-", "")

    AppScaffold(title = title, onBack = onBack) { modifier ->
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.m),
        ) {
            news.generated?.takeIf { it.isNotBlank() }?.let { generated ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(Spacing.xs))
                        Text(generated, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            news.sections.forEach { (sectionTitle, sectionContent) ->
                if (sectionTitle.isNotBlank()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Spacer(Modifier.width(Spacing.s))
                            Text(sectionTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (sectionContent.isNotBlank()) {
                    item {
                        SelectionContainer {
                            Markdown(
                                content = sectionContent,
                                typography = MarkdownStyles.typography(),
                                padding = MarkdownStyles.padding(),
                            )
                        }
                    }
                }
            }

            if (news.content.isNotBlank() && news.sections.isEmpty()) {
                item {
                    SelectionContainer {
                        Markdown(
                            content = news.content,
                            typography = MarkdownStyles.typography(),
                            padding = MarkdownStyles.padding(),
                        )
                    }
                }
            }
        }
    }
}
