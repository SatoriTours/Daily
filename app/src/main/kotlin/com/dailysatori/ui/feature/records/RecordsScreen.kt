package com.dailysatori.ui.feature.records

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun RecordsScreen(
    onArticleClick: (Long) -> Unit = {},
    onMyClick: () -> Unit = {},
) {
    Text("记录")
}
