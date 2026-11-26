package com.dailysatori.ui.component.scaffold

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dailysatori.ui.component.appbar.PageSearch
import com.dailysatori.ui.component.appbar.PageAction
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.component.appbar.MainPageHeader
import com.dailysatori.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    showBack: Boolean = onBack != null,
    myNavigationLabel: String? = null,
    onMyNavigationClick: (() -> Unit)? = null,
    pageActions: List<PageAction> = emptyList(),
    search: PageSearch? = null,
    snackbarHost: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    isMainPage: Boolean = false,
    titleMeta: String? = null,
    subtitle: String? = null,
    /** Explicit opt-in for pages that need the action space; default keeps the assistant entry. */
    showAssistant: Boolean = true,
    showHeaderDivider: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            if (isMainPage && search == null) {
                Column(Modifier.statusBarsPadding().padding(start = Spacing.m, end = Spacing.s, top = Spacing.xs)) {
                    MainPageHeader(title = title, titleMeta = titleMeta, pageActions = pageActions)
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.m))
                    }
                    if (showHeaderDivider) {
                        HorizontalDivider(
                            modifier = Modifier.padding(top = Spacing.xs),
                            thickness = BorderWidth.xs,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            } else {
                AppTopBar(
                    title = title,
                    onBack = onBack,
                    showBack = showBack,
                    myNavigationLabel = myNavigationLabel,
                    onMyNavigationClick = onMyNavigationClick,
                    pageActions = pageActions,
                    search = search,
                    showAssistant = showAssistant,
                )
            }
        },
        snackbarHost = snackbarHost,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}
