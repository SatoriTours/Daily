package com.dailysatori.ui.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Spacing

@Composable
fun DataPrivacyScreen(onBack: () -> Unit) {
    AppScaffold(title = "数据与隐私", onBack = onBack) { modifier ->
        Column(modifier = modifier.fillMaxSize().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text("数据存储", style = MaterialTheme.typography.titleMedium)
            Text("日记、提醒、收藏和同步任务保存在此设备上。")
            Text("外部收藏同步仅在你连接的来源启用时访问对应服务。")
            Text("管理备份、导入和网络服务，请前往设置。")
        }
    }
}
