package com.dailysatori.ui.component.appbar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.ui.theme.*

@Composable
fun MainPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    pageActions: List<PageAction> = emptyList(),
    titleWeight: FontWeight = FontWeight.Bold,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Height.appBarCompact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = titleWeight,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        PageActions(pageActions)
        AssistantTopBarAction()
    }
}
