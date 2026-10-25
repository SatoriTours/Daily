package com.dailysatori.ui.component.appbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor

data class PageSearch(
    val query: String,
    val placeholder: String,
    val onQueryChange: (String) -> Unit,
    val onClose: () -> Unit,
)

@Composable
internal fun PageSearchField(search: PageSearch) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = search.query, onValueChange = search.onQueryChange, singleLine = true,
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { field ->
            Box {
                if (search.query.isEmpty()) Text(search.placeholder,
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                field()
            }
        },
    )
}
