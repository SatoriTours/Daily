package com.dailysatori.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dailysatori.ui.theme.Spacing

@Composable
fun ContentViewer(
    summary: String?,
    originalContent: String?,
    modifier: Modifier = Modifier,
) {
    if (summary.isNullOrBlank() && originalContent.isNullOrBlank()) {
        Text(
            text = "暂无内容",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(Spacing.m),
        )
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = buildList {
        if (!summary.isNullOrBlank()) add("AI解读")
        if (!originalContent.isNullOrBlank()) add("原文")
    }

    if (tabs.isEmpty()) return

    Column(modifier = modifier) {
        if (tabs.size > 1) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selectedTab == index) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xs))

        val content = when {
            selectedTab == 0 && !summary.isNullOrBlank() -> summary
            else -> originalContent.orEmpty()
        }

        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m),
        )
    }
}
