package com.dailysatori.ui.feature.book.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dailysatori.shared.db.Book
import com.dailysatori.ui.feature.book.booksPickerRowMinHeightDp
import com.dailysatori.ui.feature.book.booksSwipeDeleteActionText
import com.dailysatori.ui.feature.book.booksSwipeDeleteActionWidthDp
import com.dailysatori.ui.feature.book.booksSwipeDeleteMaxRevealDp
import com.dailysatori.ui.feature.book.booksSwipeRefreshActionText
import com.dailysatori.ui.feature.book.booksSwipeRefreshActionWidthDp
import com.dailysatori.ui.feature.book.booksSwipeRefreshMaxRevealDp
import com.dailysatori.ui.feature.book.booksSwipeRefreshingActionText
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import kotlin.math.roundToInt

@Composable
internal fun BookPickerSwipeRow(
    book: Book,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean = false,
) {
    val deleteRevealWidthPx = with(LocalDensity.current) { booksSwipeDeleteMaxRevealDp().dp.toPx() }
    val refreshRevealWidthPx = with(LocalDensity.current) { booksSwipeRefreshMaxRevealDp().dp.toPx() }
    var offsetX by remember(book.id) { mutableFloatStateOf(0f) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(
                modifier = Modifier
                    .size(booksSwipeRefreshActionWidthDp().dp)
                    .clip(bookPickerRefreshActionShape())
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(enabled = !isRefreshing) {
                        offsetX = 0f
                        onRefresh()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    if (isRefreshing) booksSwipeRefreshingActionText() else booksSwipeRefreshActionText(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                modifier = Modifier
                    .size(booksSwipeDeleteActionWidthDp().dp)
                    .clip(bookPickerDeleteActionShape())
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable {
                        offsetX = 0f
                        onDelete()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    booksSwipeDeleteActionText(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(deleteRevealWidthPx, refreshRevealWidthPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            offsetX = when {
                                offsetX <= -deleteRevealWidthPx / 2f -> -deleteRevealWidthPx
                                offsetX >= refreshRevealWidthPx / 2f -> refreshRevealWidthPx
                                else -> 0f
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceIn(-deleteRevealWidthPx, refreshRevealWidthPx)
                        },
                    )
                },
        ) {
            BookPickerRow(
                book = book,
                isSelected = isSelected,
                onSelect = onSelect,
                actionSide = when {
                    offsetX > 0f -> BookPickerActionSide.Start
                    offsetX < 0f -> BookPickerActionSide.End
                    else -> BookPickerActionSide.None
                },
            )
        }
    }
}

@Composable
private fun BookPickerRow(
    book: Book,
    isSelected: Boolean,
    onSelect: () -> Unit,
    actionSide: BookPickerActionSide,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(booksPickerRowMinHeightDp().dp)
            .clip(bookPickerRowShape(actionSide))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onSelect)
            .padding(Spacing.m),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.author.isNotBlank()) {
                Text(
                    book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isSelected) {
            Spacer(modifier = Modifier.width(Spacing.xs))
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private enum class BookPickerActionSide { None, Start, End }

private fun bookPickerRowShape(actionSide: BookPickerActionSide) = when (actionSide) {
    BookPickerActionSide.Start -> RoundedCornerShape(
        topStart = 0.dp,
        bottomStart = 0.dp,
        topEnd = Radius.s,
        bottomEnd = Radius.s,
    )
    BookPickerActionSide.End -> RoundedCornerShape(
        topStart = Radius.s,
        bottomStart = Radius.s,
        topEnd = 0.dp,
        bottomEnd = 0.dp,
    )
    BookPickerActionSide.None -> RoundedCornerShape(Radius.s)
}

private fun bookPickerRefreshActionShape() = RoundedCornerShape(
    topStart = Radius.s,
    bottomStart = Radius.s,
    topEnd = 0.dp,
    bottomEnd = 0.dp,
)

private fun bookPickerDeleteActionShape() = RoundedCornerShape(
    topStart = 0.dp,
    bottomStart = 0.dp,
    topEnd = Radius.s,
    bottomEnd = Radius.s,
)
