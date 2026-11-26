package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.dailysatori.service.diary.*
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

internal data class DiaryTranscriptPolishRequest(
    val attachmentId: Long,
    val original: String,
    val snapshot: DiaryAssistantSelectionSnapshot,
    val generateOnOpen: Boolean,
)

@Composable
internal fun DiaryTranscriptPolishPreview(
    request: DiaryTranscriptPolishRequest,
    applied: DiaryPolishedTranscript?,
    onFeedbackChange: (Long, String) -> Unit,
    canApply: Boolean,
    onApply: (String) -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    service: DiaryTranscriptPolishService = koinInject(),
    i18n: I18nService = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val gate = remember(request) { DiaryAssistantRequestGate() }
    var job by remember(request) { mutableStateOf<Job?>(null) }
    var draft by remember(request) { mutableStateOf(if (request.generateOnOpen) applied?.content.orEmpty() else request.original) }
    var canAdopt by remember(request) { mutableStateOf(!request.generateOnOpen) }
    val versions = applied?.adoptedVersions().orEmpty()
    var selectedVersionId by remember(request) { mutableStateOf(applied?.currentVersion()?.id ?: versions.lastOrNull()?.id) }
    var versionMenuOpen by remember(request) { mutableStateOf(false) }
    val referenceVersion = versions.firstOrNull { it.id == selectedVersionId }
    var errorKey by remember(request) { mutableStateOf<String?>(null) }
    var loading by remember(request) { mutableStateOf(false) }
    var showOriginal by remember(request) { mutableStateOf(!request.generateOnOpen) }

    fun cancelRequest() {
        gate.invalidate()
        job?.cancel()
        job = null
        loading = false
    }
    fun generate() {
        cancelRequest()
        val generation = gate.begin()
        errorKey = null
        loading = true
        canAdopt = false
        val reference = referenceVersion
        val history = versions.toList()
        job = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { service.polish(request.original, reference, history) }
                if (gate.isCurrent(generation)) {
                    draft = result
                    canAdopt = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (gate.isCurrent(generation)) errorKey = when (error) {
                    is DiaryAssistantMissingConfigurationException -> "missing_config"
                    is DiaryTranscriptPolishInputException -> "invalid_input"
                    is DiaryTranscriptPolishResponseException -> "invalid_response"
                    else -> "failed"
                }
            } finally {
                if (gate.isCurrent(generation)) loading = false
            }
        }
    }
    LaunchedEffect(request) { if (request.generateOnOpen && applied == null) generate() }
    DisposableEffect(request) { onDispose { cancelRequest() } }

    Surface(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.66f),
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(i18n.t("diary_polish.title"), style = MaterialTheme.typography.titleMedium.editorial(), color = MaterialTheme.colorScheme.onSurface)
            HorizontalDivider(thickness = BorderWidth.xs, color = MaterialTheme.colorScheme.outline)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(i18n.t("diary_polish.privacy"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (versions.isNotEmpty()) {
                    Text(i18n.t("diary_polish.feedback_hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box {
                        OutlinedButton(
                            onClick = { versionMenuOpen = true },
                            enabled = !loading,
                            shape = RoundedCornerShape(Radius.m),
                            border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
                        ) {
                            Text(i18n.t("diary_polish.reference_version", referenceVersion?.id ?: 0))
                        }
                        DropdownMenu(expanded = versionMenuOpen, onDismissRequest = { versionMenuOpen = false }) {
                            versions.asReversed().forEach { version ->
                                DropdownMenuItem(text = { Text(i18n.t("diary_polish.version", version.id)) }, onClick = {
                                    selectedVersionId = version.id
                                    draft = version.content
                                    canAdopt = false
                                    versionMenuOpen = false
                                })
                            }
                        }
                    }
                    referenceVersion?.let { version ->
                        SelectionContainer { Text(version.content, style = MaterialTheme.typography.bodySmall) }
                        OutlinedTextField(
                            value = version.feedback,
                            onValueChange = { feedback ->
                                if (feedback.length <= 4_000) {
                                    onFeedbackChange(version.id, feedback)
                                    canAdopt = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(), enabled = !loading,
                            shape = RoundedCornerShape(Radius.m),
                            minLines = 2, maxLines = 4,
                            label = { Text(i18n.t("diary_polish.feedback")) },
                            placeholder = { Text(i18n.t("diary_polish.feedback_placeholder")) },
                            supportingText = { Text(i18n.t("diary_polish.feedback_limit", version.feedback.length)) },
                        )
                    }
                }
                if (loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(i18n.t("diary_polish.loading"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                errorKey?.let { key ->
                    Text(i18n.t("diary_polish.$key"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (key == "missing_config") TextButton(onClick = onOpenSettings) {
                        Text(i18n.t("diary_polish.settings"))
                    }
                }
                if (draft.isNotBlank()) OutlinedTextField(
                    value = draft, onValueChange = { draft = it; canAdopt = true }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.m),
                    minLines = 4, maxLines = 8, enabled = !loading,
                    label = { Text(i18n.t("diary_polish.draft")) },
                )
                if (!canApply) Text(i18n.t("diary_polish.conflict"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    Text(i18n.t("diary_polish.target"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer { Text(request.snapshot.selectedText, style = MaterialTheme.typography.bodySmall) }
                }
                TextButton(onClick = { showOriginal = !showOriginal }) {
                    Text(i18n.t(if (showOriginal) "diary_polish.hide_original" else "diary_polish.original"))
                }
                if (showOriginal) SelectionContainer { Text(request.original, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { cancelRequest(); errorKey = null; draft = request.original; canAdopt = true }) {
                    Text(i18n.t("diary_polish.restore"))
                }
            }
            HorizontalDivider(thickness = BorderWidth.xs, color = MaterialTheme.colorScheme.outline)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs, androidx.compose.ui.Alignment.End)) {
                TextButton(onClick = onClose) { Text(i18n.t("diary_polish.cancel")) }
                TextButton(onClick = { generate() }, enabled = !loading) { Text(i18n.t("diary_polish.regenerate")) }
                Button(
                    onClick = { onApply(draft) },
                    enabled = !loading && canApply && canAdopt && draft.isNotBlank(),
                    shape = RoundedCornerShape(Radius.m),
                ) {
                    Text(i18n.t("diary_polish.apply"))
                }
            }
        }
    }
}
