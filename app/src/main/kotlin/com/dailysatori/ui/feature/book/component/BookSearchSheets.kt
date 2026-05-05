package com.dailysatori.ui.feature.book.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.dailysatori.service.book.BookSearchResult
import com.dailysatori.ui.feature.book.AnalysisStatus
import com.dailysatori.ui.feature.book.BookContentSearchResultItem
import com.dailysatori.ui.feature.book.BookContentSearchState
import com.dailysatori.ui.feature.book.BookSearchState
import com.dailysatori.ui.feature.book.bookContentSearchBookLine
import com.dailysatori.ui.feature.book.bookContentSearchPreview
import com.dailysatori.ui.feature.book.bookResultAddActionDescription
import com.dailysatori.ui.feature.book.bookResultDoubanActionDescription
import com.dailysatori.ui.feature.book.bookSearchRetryActionText
import com.dailysatori.ui.feature.book.booksAddSearchLoadingText
import com.dailysatori.ui.feature.book.booksAddSheetTitle
import com.dailysatori.ui.feature.book.booksContentSearchLoadingText
import com.dailysatori.ui.feature.book.booksContentSearchSheetTitle
import com.dailysatori.ui.feature.book.doubanBookSearchUrl
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@Composable
internal fun BookAddSearchSheet(
    state: BookSearchState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onAdd: (BookSearchResult) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m)
            .padding(bottom = Spacing.xxl),
    ) {
        Text(booksAddSheetTitle(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(Spacing.s))
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("输入书名添加...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(Radius.s),
        )
        if (state.isAnalyzing || state.analysisMessage != null) {
            AnalysisStatus(
                isAnalyzing = state.isAnalyzing,
                step = state.analysisStep,
                message = state.analysisMessage,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
        if (state.error != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = onRetry,
                    enabled = state.query.isNotBlank() && !state.isLoading,
                    contentPadding = PaddingValues(horizontal = Spacing.s, vertical = Spacing.xxs),
                ) {
                    Text(bookSearchRetryActionText(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (state.isLoading) SearchSheetStatus(booksAddSearchLoadingText())
        LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
            items(state.visibleResults.take(8)) { result ->
                BookSearchResultCard(
                    result = result,
                    isAnalyzing = state.isAnalyzing,
                    onAdd = { onAdd(result) },
                    onOpenDouban = { uriHandler.openUri(doubanBookSearchUrl(result)) },
                )
            }
        }
    }
}

@Composable
private fun BookSearchResultCard(
    result: BookSearchResult,
    isAnalyzing: Boolean,
    onAdd: () -> Unit,
    onOpenDouban: () -> Unit,
) {
    val context = LocalContext.current
    val imageRequest = remember(context, result.coverUrl) {
        ImageRequest.Builder(context)
            .data(result.coverUrl.ifBlank { null })
            .httpHeaders(
                NetworkHeaders.Builder()
                    .set("Referer", "https://book.douban.com/")
                    .set("User-Agent", "Mozilla/5.0 DailySatori Android")
                    .build(),
            )
            .build()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.s)
            .clip(RoundedCornerShape(Radius.m))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(Spacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = imageRequest,
            placeholder = painterResource(android.R.drawable.ic_menu_gallery),
            error = painterResource(android.R.drawable.ic_menu_gallery),
            contentDescription = result.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 72.dp, height = 104.dp).clip(RoundedCornerShape(Radius.s)),
        )
        Spacer(modifier = Modifier.width(Spacing.s))
        Column(modifier = Modifier.weight(1f)) {
            Text(result.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (result.author.isNotBlank()) Text(result.author, style = MaterialTheme.typography.labelSmall)
            if (result.introduction.isNotBlank()) {
                Text(
                    bookContentSearchPreview(result.introduction, 90),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                FilledTonalButton(
                    onClick = onOpenDouban,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = Spacing.s, vertical = Spacing.xxs),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = bookResultDoubanActionDescription(),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.xxs))
                    Text("豆瓣", style = MaterialTheme.typography.labelSmall)
                }
                FilledTonalButton(
                    onClick = onAdd,
                    enabled = !isAnalyzing,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = Spacing.s, vertical = Spacing.xxs),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = bookResultAddActionDescription(),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.xxs))
                    Text("添加", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
internal fun BookContentSearchSheet(
    state: BookContentSearchState,
    onQueryChange: (String) -> Unit,
    onResultClick: (BookContentSearchResultItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m)
            .padding(bottom = Spacing.xxl),
    ) {
        Text(booksContentSearchSheetTitle(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(Spacing.s))
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索书名、作者或观点...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(Radius.s),
        )
        if (state.query.isNotBlank() && !state.hasSearched) SearchSheetStatus(booksContentSearchLoadingText())
        LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
            items(state.visibleResults.take(12)) { result ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onResultClick(result) }
                        .padding(top = Spacing.s)
                        .clip(RoundedCornerShape(Radius.m))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(Spacing.s),
                ) {
                    Text(result.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(bookContentSearchBookLine(result.bookTitle, result.author), style = MaterialTheme.typography.labelSmall)
                    Text(
                        bookContentSearchPreview(result.content),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                    if (result.example.isNotBlank()) {
                        Text(
                            "例子：${bookContentSearchPreview(result.example, 42)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xxs),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSheetStatus(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.s)
            .clip(RoundedCornerShape(Radius.s))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .padding(Spacing.s),
    )
}
