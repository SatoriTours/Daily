package com.dailysatori.ui.component.scaffold

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    isMainPage: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            if (isMainPage) {
                Column(Modifier.statusBarsPadding().padding(start = Spacing.m, end = Spacing.m, top = Spacing.s)) {
                    MainPageHeader(title = title, actions = actions)
                    HorizontalDivider(
                        modifier = Modifier.padding(top = Spacing.s),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            } else {
                AppTopBar(
                    title = title,
                    onBack = onBack,
                    showBack = showBack,
                    myNavigationLabel = myNavigationLabel,
                    onMyNavigationClick = onMyNavigationClick,
                    actions = actions,
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
