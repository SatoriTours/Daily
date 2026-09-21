package com.dailysatori.ui.feature.myspace

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.R
import com.dailysatori.service.opportunity.ReadNewsArticle
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.shared.db.Article
import kotlinx.datetime.Clock
import org.koin.androidx.compose.koinViewModel

fun Article.toReadNewsArticle() = ReadNewsArticle(
    key = readNewsKey(url, "local:$id"), title = title.orEmpty(), content = original_markdown_content.orEmpty(),
    url = url, source = url.orEmpty().substringAfter("://").substringBefore('/'),
    publishedAt = pub_date?.let { kotlinx.datetime.Instant.fromEpochMilliseconds(it).toString() },
    readAt = Clock.System.now().toEpochMilliseconds(), localArticleId = id,
)

fun RemoteArticle.toReadNewsArticle(sourceIdentity: String, localId: Long? = null) = ReadNewsArticle(
    key = readNewsKey(url, "remote:$sourceIdentity:$id"), title = title.orEmpty(), content = content.orEmpty(),
    url = url, source = feedName ?: domain ?: sourceIdentity, publishedAt = publishedAt ?: createdAt,
    readAt = Clock.System.now().toEpochMilliseconds(), localArticleId = localId,
)

@Composable
fun MarkNewsReadButton(article: ReadNewsArticle, viewModel: MySpaceViewModel = koinViewModel()) {
    if (!article.hasReadableBody()) return
    var saved by rememberSaveable(article.key, article.content) { mutableStateOf(false) }
    var saving by remember(article.key) { mutableStateOf(false) }
    val failed by viewModel.operationFailed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val error = stringResource(R.string.my_space_read_error)
    val success = stringResource(R.string.my_space_read_done)
    LaunchedEffect(failed) {
        if (failed && saving) {
            saving = false
            android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    TextButton(enabled = !saving && !saved, onClick = {
        saving = true
        viewModel.markRead(article) {
            saved = true; saving = false
            android.widget.Toast.makeText(context, success, android.widget.Toast.LENGTH_SHORT).show()
        }
    }) { Text(stringResource(if (saved) R.string.my_space_read_marked else R.string.my_space_read)) }
}
