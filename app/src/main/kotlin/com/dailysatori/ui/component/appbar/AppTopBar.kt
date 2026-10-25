package com.dailysatori.ui.component.appbar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.dailysatori.ui.theme.Height

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    showBack: Boolean = onBack != null,
    myNavigationLabel: String? = null,
    onMyNavigationClick: (() -> Unit)? = null,
    onTitleDoubleClick: (() -> Unit)? = null,
    pageActions: List<PageAction> = emptyList(),
    search: PageSearch? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    expandedHeight: Dp = Height.appBar,
    showAssistant: Boolean = true,
    backIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    backContentDescription: String = "返回",
) {
    require(actions == null || !showAssistant) { "Ordinary headers must declare named pageActions" }
    val navigateBack = search?.onClose ?: onBack
    CenterAlignedTopAppBar(
        expandedHeight = expandedHeight,
        windowInsets = TopAppBarDefaults.windowInsets,
        title = {
            if (search != null) PageSearchField(search) else {
            val titleModifier = if (onTitleDoubleClick != null) {
                Modifier.combinedClickable(onClick = {}, onDoubleClick = onTitleDoubleClick)
            } else {
                Modifier
            }
            Text(
                text = title,
                modifier = titleModifier,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            }
        },
        navigationIcon = {
            if ((showBack || search != null) && navigateBack != null) {
                IconButton(onClick = navigateBack) {
                    Icon(
                        backIcon,
                        contentDescription = backContentDescription,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else if (myNavigationLabel != null && onMyNavigationClick != null) {
                IconButton(onClick = onMyNavigationClick) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = myNavigationLabel,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        actions = {
            if (search == null) {
                PageActions(pageActions)
                actions?.invoke(this)
            }
            if (showAssistant) AssistantTopBarAction()
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
