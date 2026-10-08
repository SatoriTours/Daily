package com.dailysatori.ui.feature.myspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.service.i18n.I18nService
import org.koin.compose.koinInject
import com.dailysatori.ui.theme.*

@Composable
internal fun NewsOpportunityCard(
    item: NewsOpportunity,
    rank: Int,
    onOpen: () -> Unit,
    isCaptured: Boolean = false,
    onTopicAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val i18n: I18nService = koinInject()
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(start = Spacing.m, top = Spacing.s, bottom = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
            Text(rank.toString().padStart(2, '0'), Modifier.padding(top = Spacing.xs),
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f).padding(vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.action, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.article.source, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onTopicAction) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.s),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(i18n.t(opportunityActionLabelKey(isCaptured)), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
