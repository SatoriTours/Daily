package com.dailysatori.ui.feature.book

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@Composable
fun ViewpointCard(
    title: String,
    content: String,
    example: String,
    bookTitle: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(Spacing.m)) {
            Text(bookTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(Spacing.s))
            Text(content, style = MaterialTheme.typography.bodyMedium, maxLines = 10, overflow = TextOverflow.Ellipsis)
            if (example.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.m))
                Text("示例", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(example, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
