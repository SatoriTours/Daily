package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailysatori.shared.db.Book
import com.dailysatori.ui.component.card.DiaryCard
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.feature.book.ViewpointCard
import com.dailysatori.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiReferenceDetailSheet(
    state: AiReferenceDetailState,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.m)
                .padding(bottom = Spacing.xxl),
        ) {
            Text(
                text = "引用详情",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = Spacing.m),
            )
            ReferenceDetailContent(state)
        }
    }
}

@Composable
private fun ReferenceDetailContent(state: AiReferenceDetailState) {
    when {
        state.isLoading -> LoadingIndicator(modifier = Modifier.height(160.dp))
        state.diary != null -> DiaryCard(
            diary = state.diary,
            onClick = {},
            onDelete = {},
            showDelete = false,
        )
        state.viewpoint != null -> ViewpointCard(
            title = state.viewpoint.title,
            content = state.viewpoint.content,
            example = state.viewpoint.example,
            bookTitle = state.book?.let { "《${it.title}》 · ${it.author}" }.orEmpty(),
        )
        state.book != null -> BookReferenceSummary(state.book)
        else -> EmptyState(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = state.error ?: "内容不存在或已删除",
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )
    }
}

@Composable
private fun BookReferenceSummary(book: Book) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (book.author.isNotBlank()) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = book.author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (book.introduction.isNotBlank()) {
            Spacer(modifier = Modifier.height(Spacing.m))
            Text(
                text = book.introduction,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
