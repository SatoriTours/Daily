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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
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
import com.dailysatori.ui.component.scaffold.AppScaffold
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
        }
    }

    val target = editing
    if (adding || target != null) {
        SkillEditScreen(
            skill = target,
            isSaving = state.isSaving,
            error = state.error,
            onSave = viewModel::save,
            onBack = { adding = false; editing = null },
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
    AppScaffold(
        title = skillSettingsScreenTitle(),
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = skillAddButtonText())
            }
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            item { SkillCountText(skills.size) }
            items(skills, key = { it.id }) { skill ->
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
        shape = RoundedCornerShape(Radius.m),
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
    error: String?,
    onSave: (SkillEditInput) -> Unit,
    onBack: () -> Unit,
) {
    val fields = rememberSkillEditFields(skill)
    AppScaffold(title = skill?.name ?: skillAddButtonText(), onBack = onBack) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = Spacing.m),
            contentPadding = PaddingValues(vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            skillCoreFieldItems(fields, skillCoreFieldsEditable(skill?.builtin ?: 0L))
            item { SkillTokenField(fields) }
            item { SkillEnabledRow(fields) }
            if (error != null) item { SkillErrorText(error) }
            item { SkillSaveButton(skill?.id, fields, isSaving, onSave) }
        }
    }
}

@Composable
private fun rememberSkillEditFields(skill: Skill_config?): SkillEditFields {
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
        name, { name = it }, description, { description = it }, gatewayUrl, { gatewayUrl = it },
        apiToken, { apiToken = it }, skillVersion, { skillVersion = it }, enabled, { enabled = it },
        provider, { provider = it }, templateId, { templateId = it }, toolSchemaJson, { toolSchemaJson = it },
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
) {
    item { SkillTextField(fields.name, fields.onNameChange, "名称", editable, singleLine = true) }
    item { SkillTextField(fields.description, fields.onDescriptionChange, "给 AI 的能力描述", editable, minLines = 3) }
    item { SkillTextField(fields.gatewayUrl, fields.onGatewayUrlChange, "Gateway URL", editable, singleLine = true) }
    item { SkillTextField(fields.skillVersion, fields.onSkillVersionChange, "Skill Version", editable, singleLine = true) }
    item { SkillTextField(fields.provider, fields.onProviderChange, "Provider", editable, singleLine = true) }
    item { SkillTextField(fields.templateId, fields.onTemplateIdChange, "Template ID", editable, singleLine = true) }
    item { SkillTextField(fields.toolSchemaJson, fields.onToolSchemaJsonChange, "Tool Schema JSON", editable, minLines = 4) }
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

@Composable
private fun SkillErrorText(error: String) {
    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SkillSaveButton(
    skillId: Long?,
    fields: SkillEditFields,
    isSaving: Boolean,
    onSave: (SkillEditInput) -> Unit,
) {
    Button(
        onClick = { onSave(fields.toInput(skillId)) },
        enabled = !isSaving,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(skillSaveButtonText(isSaving)) }
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
