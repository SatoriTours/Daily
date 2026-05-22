package com.dailysatori.ui.feature.book

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown

@Composable
fun ViewpointCard(
    title: String,
    content: String,
    example: String,
    bookTitle: String,
    modifier: Modifier = Modifier,
    fillAvailableHeight: Boolean = false,
) {
    val cardModifier = if (fillAvailableHeight) modifier.fillMaxWidth().fillMaxHeight() else modifier.fillMaxWidth()
    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(Radius.l),
        border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(Spacing.m).verticalScroll(rememberScrollState())) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(Spacing.s))
            Text(
                bookTitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(Spacing.s))
            Markdown(
                content = content,
                typography = MarkdownStyles.cardTypography(),
                padding = MarkdownStyles.cardPadding(),
            )
            if (example.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.s))
                Text(
                    "案例",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Markdown(
                    content = example,
                    typography = MarkdownStyles.cardTypography(),
                    padding = MarkdownStyles.cardPadding(),
                )
            }
        }
    }
}

fun viewpointCardFillsAvailableHeight(fillAvailableHeight: Boolean): Boolean = fillAvailableHeight

fun viewpointCardContentStartsAtTop(): Boolean = true
