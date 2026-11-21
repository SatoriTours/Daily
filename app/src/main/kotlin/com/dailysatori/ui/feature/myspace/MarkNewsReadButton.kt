package com.dailysatori.ui.feature.myspace

import com.dailysatori.ui.component.appbar.PageAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import com.dailysatori.service.i18n.I18nService
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

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
internal fun markNewsReadAction(
    article: ReadNewsArticle,
    viewModel: MySpaceViewModel = koinViewModel(),
    reversible: Boolean = false,
): PageAction? {
    val readable = article.hasReadableBody()
    if (!readable && !reversible) return null
    val read = if (reversible) article.key in viewModel.state.collectAsStateWithLifecycle().value.readArticleKeys else false
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
    val label = if (reversible) koinInject<I18nService>().t(if (read) "article.mark_unread" else "article.mark_read")
        else stringResource(if (saved) R.string.my_space_read_marked else R.string.my_space_read)
    return PageAction(label, Icons.Outlined.CheckCircle, enabled = !saving && (read || (readable && (reversible || !saved)))) {
        saving = true
        if (read) viewModel.markUnread(article.key) { saving = false }
        else viewModel.markRead(article) {
            if (!reversible) saved = true
            saving = false
            android.widget.Toast.makeText(context, success, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun MarkNewsReadButton(article: ReadNewsArticle, viewModel: MySpaceViewModel = koinViewModel()) {
    markNewsReadAction(article, viewModel)?.let { action ->
        TextButton(enabled = action.enabled, onClick = action::perform) { Text(action.label) }
    }
}

@Composable
fun MarkNewsReadMenuItem(action: PageAction?, onDismiss: () -> Unit) {
    action?.let {
        DropdownMenuItem(text = { Text(action.label) }, leadingIcon = { Icon(action.icon, null) },
            enabled = action.enabled, onClick = { onDismiss(); action.perform() })
    }
}
