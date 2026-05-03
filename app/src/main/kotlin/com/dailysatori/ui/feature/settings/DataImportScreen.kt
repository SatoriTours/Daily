package com.dailysatori.ui.feature.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dailysatori.service.import.ImportService
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

data class ImportState(
    val isImporting: Boolean = false,
    val progress: Float = 0f,
    val result: ImportService.ImportResult? = null,
    val error: String? = null,
)

@Composable
fun DataImportScreen(
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val importService = remember { KoinPlatform.getKoin().get<ImportService>() }
    val coroutineScope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ImportState()) }

    val pickZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            val path = copyUriToTempFile(context, it)
            if (path != null) {
                coroutineScope.launch {
                    state = ImportState(isImporting = true)
                    try {
                        val result = importService.importFromZip(path)
                        state = ImportState(result = result)
                    } catch (e: Exception) {
                        state = ImportState(error = e.message ?: "导入失败")
                    }
                }
            } else {
                state = ImportState(error = "无法读取文件")
            }
        }
    }

    AppScaffold(
        title = "导入数据",
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.m)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Spacer(modifier = Modifier.height(Spacing.xs))

            ImportHeaderCard()

            Button(
                onClick = {
                    pickZipLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                },
                enabled = !state.isImporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Height.button),
                shape = RoundedCornerShape(Radius.m),
            ) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(Spacing.s))
                Text(if (state.isImporting) "导入中..." else "选择 ZIP 文件")
            }

            if (state.isImporting) {
                ProgressCard()
            }

            state.error?.let { error ->
                ErrorCard(error)
            }

            state.result?.let { result ->
                ResultCard(result)
            }

            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun ImportHeaderCard() {
    Card(
        shape = RoundedCornerShape(Radius.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                "从 Flutter 版本迁移数据",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "选择从 Daily Satori Flutter 版本导出的 ZIP 文件，将数据导入到当前应用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProgressCard() {
    Card(
        shape = RoundedCornerShape(Radius.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "正在导入数据，请勿关闭页面...",
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ErrorCard(error: String) {
    Card(
        shape = RoundedCornerShape(Radius.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(Spacing.m))
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun ResultCard(result: ImportService.ImportResult) {
    Card(
        shape = RoundedCornerShape(Radius.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(Spacing.s))
                Text("导入完成", style = MaterialTheme.typography.titleSmall)
            }

            val items = listOf(
                "设置" to result.settings,
                "AI 配置" to result.aiConfigs,
                "标签" to result.tags,
                "文章" to result.articles,
                "文章标签" to result.articleTags,
                "图片" to result.images,
                "日记" to result.diaries,
                "书籍" to result.books,
                "读书观点" to result.bookViewpoints,
                "周报" to result.weeklySummaries,
                "会话" to result.sessions,
                "MCP 服务" to result.mcpServers,
                "图片文件" to result.imageFilesCopied,
            )

            items.forEach { (label, count) ->
                if (count > 0) {
                    ResultRow(label, count)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, count: Int) {
    Surface(
        shape = RoundedCornerShape(Radius.s),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.m, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("$count 条", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun copyUriToTempFile(context: Context, uri: Uri): String? {
    return try {
        val tempFile = java.io.File(context.cacheDir, "import_${System.currentTimeMillis()}.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            java.io.FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        tempFile.absolutePath
    } catch (_: Exception) {
        null
    }
}
