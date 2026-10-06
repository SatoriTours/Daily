package com.dailysatori.ui.feature.settings.skills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.service.skill.canDeleteSkill
import com.dailysatori.service.skill.skillBuiltinBadge
import com.dailysatori.service.skill.skillEnabledStatus
import com.dailysatori.service.skill.skillTokenStatus
import com.dailysatori.shared.db.Skill_config
import com.dailysatori.ui.component.settings.SettingsScaffold as AppScaffold
import com.dailysatori.ui.component.settings.SettingsEditorBottomBar
import com.dailysatori.ui.component.settings.SettingsEditorMessage
import com.dailysatori.ui.component.settings.matchesSettingsQuery
import com.dailysatori.ui.component.settings.SettingsFormSection
import com.dailysatori.ui.component.settings.rememberSettingsEditorBack
import com.dailysatori.ui.component.settings.SettingsSearchEmptyState
import androidx.compose.runtime.saveable.rememberSaveable
import com.dailysatori.service.i18n.I18nService
import org.koin.compose.koinInject
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun SkillSettingsScreen(onBack: () -> Unit) {
    val viewModel: SkillSettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<Skill_config?>(null) }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(state.message, state.isSaving) {
        if (skillShouldCloseEditorAfterSave(state.message, state.isSaving)) {
            adding = false
            editing = null
            viewModel.consumeMessage()
        }
    }

    val target = editing
    if (adding || target != null) {
        SkillEditScreen(
            skill = target,
            isSaving = state.isSaving,
            isTesting = state.isTesting,
            error = state.error,
            testMessage = state.testMessage,
            onSave = viewModel::save,
            onTest = viewModel::testSkill,
            onFieldsChanged = viewModel::clearTestMessage,
            onBack = {
                viewModel.clearTestMessage()
                adding = false
                editing = null
            },
        )
        return
    }

    SkillListScreen(
        skills = state.skills,
        onBack = onBack,
        onAdd = { adding = true },
        onEdit = { editing = it },
        onDelete = viewModel::delete,
    )
}

@Composable
private fun SkillListScreen(
    skills: List<Skill_config>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Skill_config) -> Unit,
    onDelete: (Skill_config) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visibleSkills = skills.filter { matchesSettingsQuery(query, it.name, it.description) }
    AppScaffold(
        useGroupNavigation = true,
        title = skillSettingsScreenTitle(),
        onBack = onBack,
        searchQuery = query, onSearchQueryChange = { query = it },
        actions = {
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = skillAddButtonText())
            }
        },
    ) { modifier ->
        if (query.isNotBlank() && visibleSkills.isEmpty()) SettingsSearchEmptyState(modifier)
        else LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item { SkillCountText(skills.size) }
            items(visibleSkills, key = { it.id }) { skill ->
                SkillCard(skill = skill, onEdit = { onEdit(skill) }, onDelete = { onDelete(skill) })
            }
        }
    }
}

@Composable
private fun SkillCountText(count: Int) {
    Text(
        text = "$count 个 Skill",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SkillCard(skill: Skill_config, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onEdit,
        shape = RoundedCornerShape(Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.m)) {
            SkillCardHeader(skill, onDelete)
            Text(
                text = "${skillBuiltinBadge(skill.builtin)} · ${skillEnabledStatus(skill.enabled)} · ${skillTokenStatus(skill.api_token)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (skill.description.isNotBlank()) SkillDescription(skill.description)
        }
    }
}

@Composable
private fun SkillCardHeader(skill: Skill_config, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = skill.name,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (canDeleteSkill(skill.builtin)) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除 Skill")
            }
        }
    }
}

@Composable
private fun SkillDescription(description: String) {
    Spacer(modifier = Modifier.height(Spacing.xs))
    Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SkillEditScreen(
    skill: Skill_config?,
    isSaving: Boolean,
    isTesting: Boolean,
    error: String?,
    testMessage: String?,
    onSave: (SkillEditInput) -> Unit,
    onTest: (SkillEditInput) -> Unit,
    onFieldsChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val i18n: I18nService = koinInject()
    val fields = rememberSkillEditFields(skill, onFieldsChanged)
    val changed = fields.name != skill?.name.orEmpty() || fields.description != skill?.description.orEmpty() ||
        fields.gatewayUrl != skill?.gateway_url.orEmpty() || fields.apiToken != skill?.api_token.orEmpty() ||
        fields.skillVersion != skill?.skill_version.orEmpty() || fields.enabled != (skill?.enabled == 1L) ||
        fields.provider != skill?.provider.orEmpty() || fields.templateId != skill?.template_id.orEmpty() ||
        fields.toolSchemaJson != skill?.tool_schema_json.orEmpty()
    val requestBack = rememberSettingsEditorBack(changed, isSaving || isTesting, onBack)
    AppScaffold(
        title = skill?.name ?: skillAddButtonText(),
        onBack = requestBack,
        bottomBar = {
            SettingsEditorBottomBar(
                canTest = !isTesting,
                canSave = !isSaving,
                isTesting = isTesting,
                isSaving = isSaving,
                testText = skillTestButtonText(isTesting),
                saveText = skillSaveButtonText(isSaving),
                onTest = { onTest(fields.toInput(skill?.id)) },
                onSave = { onSave(fields.toInput(skill?.id)) },
            )
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = Spacing.m),
            contentPadding = PaddingValues(vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            skillCoreFieldItems(fields, skillCoreFieldsEditable(skill?.builtin ?: 0L), i18n)
            item {
                SettingsFormSection(i18n.t("settings_design.access_enable")) {
                    SkillTokenField(fields)
                    SkillEnabledRow(fields)
                }
            }
            if (error != null) item { SettingsEditorMessage(error, isError = true) }
            if (testMessage != null) item { SettingsEditorMessage(testMessage, isError = false) }
        }
    }
}

@Composable
private fun rememberSkillEditFields(skill: Skill_config?, onFieldsChanged: () -> Unit): SkillEditFields {
    var name by remember(skill?.id) { mutableStateOf(skill?.name.orEmpty()) }
    var description by remember(skill?.id) { mutableStateOf(skill?.description.orEmpty()) }
    var gatewayUrl by remember(skill?.id) { mutableStateOf(skill?.gateway_url.orEmpty()) }
    var apiToken by remember(skill?.id) { mutableStateOf(skill?.api_token.orEmpty()) }
    var skillVersion by remember(skill?.id) { mutableStateOf(skill?.skill_version.orEmpty()) }
    var enabled by remember(skill?.id) { mutableStateOf(skill?.enabled == 1L) }
    var provider by remember(skill?.id) { mutableStateOf(skill?.provider.orEmpty()) }
    var templateId by remember(skill?.id) { mutableStateOf(skill?.template_id.orEmpty()) }
    var toolSchemaJson by remember(skill?.id) { mutableStateOf(skill?.tool_schema_json.orEmpty()) }
    return SkillEditFields(
        name, { name = it; onFieldsChanged() },
        description, { description = it; onFieldsChanged() },
        gatewayUrl, { gatewayUrl = it; onFieldsChanged() },
        apiToken, { apiToken = it; onFieldsChanged() },
        skillVersion, { skillVersion = it; onFieldsChanged() },
        enabled, { enabled = it; onFieldsChanged() },
        provider, { provider = it; onFieldsChanged() },
        templateId, { templateId = it; onFieldsChanged() },
        toolSchemaJson, { toolSchemaJson = it; onFieldsChanged() },
    )
}

private data class SkillEditFields(
    val name: String,
    val onNameChange: (String) -> Unit,
    val description: String,
    val onDescriptionChange: (String) -> Unit,
    val gatewayUrl: String,
    val onGatewayUrlChange: (String) -> Unit,
    val apiToken: String,
    val onApiTokenChange: (String) -> Unit,
    val skillVersion: String,
    val onSkillVersionChange: (String) -> Unit,
    val enabled: Boolean,
    val onEnabledChange: (Boolean) -> Unit,
    val provider: String,
    val onProviderChange: (String) -> Unit,
    val templateId: String,
    val onTemplateIdChange: (String) -> Unit,
    val toolSchemaJson: String,
    val onToolSchemaJsonChange: (String) -> Unit,
)

private fun androidx.compose.foundation.lazy.LazyListScope.skillCoreFieldItems(
    fields: SkillEditFields,
    editable: Boolean,
    i18n: I18nService,
) {
    item {
        SettingsFormSection(i18n.t("settings_design.skill_info")) {
            SkillTextField(fields.name, fields.onNameChange, "名称", editable, singleLine = true)
            SkillTextField(fields.description, fields.onDescriptionChange, "给 AI 的能力描述", editable, minLines = 3)
            SkillTextField(fields.skillVersion, fields.onSkillVersionChange, "Skill Version", editable, singleLine = true)
        }
    }
    item {
        SettingsFormSection(i18n.t("settings_design.service_connection")) {
            SkillTextField(fields.gatewayUrl, fields.onGatewayUrlChange, "Gateway URL", editable, singleLine = true)
            SkillTextField(fields.provider, fields.onProviderChange, "Provider", editable, singleLine = true)
            SkillTextField(fields.templateId, fields.onTemplateIdChange, "Template ID", editable, singleLine = true)
            SkillTextField(fields.toolSchemaJson, fields.onToolSchemaJsonChange, "Tool Schema JSON", editable, minLines = 4)
        }
    }
}

@Composable
private fun SkillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
    )
}

@Composable
private fun SkillTokenField(fields: SkillEditFields) {
    OutlinedTextField(
        value = fields.apiToken,
        onValueChange = fields.onApiTokenChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("API Token") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
    )
}

@Composable
private fun SkillEnabledRow(fields: SkillEditFields) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("启用", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "启用后 Agent 可以调用这个 Skill",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = fields.enabled, onCheckedChange = fields.onEnabledChange)
    }
}

private fun SkillEditFields.toInput(skillId: Long?) = SkillEditInput(
    id = skillId,
    name = name,
    description = description,
    gatewayUrl = gatewayUrl,
    apiToken = apiToken,
    skillVersion = skillVersion,
    enabled = enabled,
    provider = provider,
    templateId = templateId,
    toolSchemaJson = toolSchemaJson,
)
