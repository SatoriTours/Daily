package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dailysatori.service.reminder.LeapDayPolicy
import com.dailysatori.service.reminder.ReminderRecurrence
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

private enum class EditorPicker { START, END, TIME }

@Composable
fun ReminderEditScreen(
    reminderId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: ReminderViewModel = koinViewModel(),
) {
    val reminders by viewModel.reminders.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val ui by viewModel.state.collectAsState()
    val existing = reminders.firstOrNull { it.id == reminderId }
    var editor by remember(existing?.id) { mutableStateOf(existing?.let(ReminderEditorState::from) ?: ReminderEditorState.createDefault()) }
    var picker by remember { mutableStateOf<EditorPicker?>(null) }
    LaunchedEffect(reminderId) { viewModel.resetAiParse() }
    val save = {
        val submitted = editor
        editor = submitted.copy(saving = true)
        viewModel.saveEditor(existing, submitted) { id, next -> editor = next; id?.let(onSaved) }
    }
    AppScaffold(
        title = if (existing == null) "新建提醒" else "编辑提醒",
        onBack = onBack,
        bottomBar = {
            Surface(shadowElevation = Spacing.xs) {
                Button(onClick = save, enabled = editor.canSave, modifier = Modifier.fillMaxWidth().padding(Spacing.m)) {
                    Text(if (editor.saving) "保存中…" else "保存提醒")
                }
            }
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text("提醒我什么", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = ui.aiParse.prompt,
                            onValueChange = viewModel::onAiPromptChanged,
                            placeholder = { Text("例如：每年 9 月 2 日晚上 8 点提醒我充值") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = viewModel::interpretAiPrompt, enabled = ui.aiParse.prompt.isNotBlank() && !ui.aiParse.isInterpreting) {
                                Text(if (ui.aiParse.isInterpreting) "解析中…" else "AI 解析")
                            }
                        }
                        ui.aiParse.error?.let { Text("解析未完全成功：$it，可继续手动修改。", color = MaterialTheme.colorScheme.error) }
                        ui.aiParse.draft?.let { draft ->
                            Text(if (ui.aiParse.requiresConfirmation) "解析结果需要你确认，应用后仍可修改。" else "已生成配置预览，应用后仍可修改。", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { editor = editor.applyParsedDraft(draft) }) { Text("应用解析结果") }
                        }
                    }
                }
            }
            item { Text("详细配置", style = MaterialTheme.typography.titleMedium) }
            item { OutlinedTextField(editor.content, { editor = editor.copy(content = it, notice = null) }, label = { Text("提醒内容") }, modifier = Modifier.fillMaxWidth()) }
            item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextButton(onClick = { picker = EditorPicker.START }) { Text("开始 ${editor.startDate}") }
                TextButton(onClick = { picker = EditorPicker.END }) { Text("结束 ${editor.endDate}") }
                TextButton(onClick = { picker = EditorPicker.TIME }) { Text("时间 ${editor.firstReminderTime}") }
            }
            }
            item { Text("重复方式", style = MaterialTheme.typography.titleSmall) }
            item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf("一次" to ReminderEditorMode.ONCE, "每月" to ReminderEditorMode.MONTHLY, "每年" to ReminderEditorMode.YEARLY, "连续" to ReminderEditorMode.CONSECUTIVE).forEach { (label, mode) ->
                    FilterChip(selected = when (mode) {
                        ReminderEditorMode.CONSECUTIVE -> editor.activeDayRule is com.dailysatori.service.reminder.ReminderActiveDayRule.ConsecutiveDateRange
                        ReminderEditorMode.ONCE -> editor.recurrence == ReminderRecurrence.Once && editor.activeDayRule !is com.dailysatori.service.reminder.ReminderActiveDayRule.ConsecutiveDateRange
                        ReminderEditorMode.MONTHLY -> editor.recurrence is ReminderRecurrence.Monthly
                        ReminderEditorMode.YEARLY -> editor.recurrence is ReminderRecurrence.Yearly
                    }, onClick = { editor = editor.selectMode(mode) }, label = { Text(label) })
                }
            }
            }
            if (editor.recurrence is ReminderRecurrence.Yearly && editor.startDate.monthNumber == 2 && editor.startDate.dayOfMonth == 29) {
                item { Text("非闰年时使用：") }
                item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    LeapDayPolicy.entries.forEach { policy ->
                        FilterChip(selected = (editor.recurrence as ReminderRecurrence.Yearly).leapDayPolicy == policy && editor.leapDayFallbackChosen, onClick = { editor = editor.copy(recurrence = ReminderRecurrence.Yearly(2, 29, policy), leapDayFallbackChosen = true) }, label = { Text(if (policy == LeapDayPolicy.FEBRUARY_28) "2 月 28 日" else "3 月 1 日") })
                    }
                }
                }
            }
            item { Text("提醒配置", style = MaterialTheme.typography.titleSmall) }
            item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                profiles.forEach { profile -> FilterChip(selected = editor.profile.kind == profile.kind, onClick = { editor = editor.copy(profile = profile.snapshot, notice = null) }, label = { Text(profile.name) }) }
            }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("实际提醒行为", style = MaterialTheme.typography.titleSmall)
                        Text(editor.actualBehaviorSummary(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            editor.validationMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            editor.notice?.let { notice -> item { Text(notice, color = MaterialTheme.colorScheme.error) } }
        }
    }
    when (picker) {
        EditorPicker.START -> DateDialog(editor.startDate, { picker = null }) { selected -> editor = editor.copy(startDate = selected, endDate = if (editor.endDate < selected) selected else editor.endDate); picker = null }
        EditorPicker.END -> DateDialog(editor.endDate, { picker = null }) { editor = editor.copy(endDate = it); picker = null }
        EditorPicker.TIME -> TimeDialog(editor.firstReminderTime, { picker = null }) { editor = editor.copy(firstReminderTime = it); picker = null }
        null -> Unit
    }
}
