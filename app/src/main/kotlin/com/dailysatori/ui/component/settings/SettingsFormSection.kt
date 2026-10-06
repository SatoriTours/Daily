package com.dailysatori.ui.component.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dailysatori.ui.theme.*

@Composable
fun SettingsFormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    SettingsSectionCard(title) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m), content = content)
    }
}
