package com.dailysatori.ui.component.settings

import com.dailysatori.config.findProvider
import com.dailysatori.service.diary.speechSettingsProviders
import com.dailysatori.ui.feature.aiconfig.AiConfigDraft
import com.dailysatori.ui.feature.aiconfig.AiConfigEditState
import com.dailysatori.ui.feature.settings.remotenews.RemoteNewsDraft
import com.dailysatori.ui.feature.settings.remotenews.RemoteNewsSettingsState
import com.dailysatori.ui.feature.settings.speech.SpeechSettingsState
import kotlin.test.*

class SettingsEditorStateTest {
    @Test fun speechOnlyPromptsForChangesAfterLoadingAndDisablesSavingUnchangedValues() {
        val config = speechSettingsProviders.first().newConfig().copy(apiKey = "saved-key")
        val saved = SpeechSettingsState(config = config, savedConfig = config, loaded = true)
        assertFalse(saved.hasChanges)
        assertFalse(saved.canSave)
        val edited = saved.copy(config = config.copy(apiKey = "new-key"))
        assertTrue(edited.hasChanges)
        assertTrue(edited.canSave)
        assertFalse(edited.copy(saving = true).canSave)
        assertFalse(edited.copy(config = config).hasChanges)
    }

    @Test fun aiCatalogUpdatesDoNotCountAsEditsButCredentialsAndDefaultChoiceDo() {
        val provider = requireNotNull(findProvider("openai"))
        val saved = AiConfigEditState(selectedProvider = provider, customModelName = "model",
            apiToken = "saved-key", savedDraft = AiConfigDraft(provider.id, "model", "saved-key", false))
        assertFalse(saved.hasChanges)
        assertFalse(saved.copy(isRefreshingModels = true, modelRefreshMessage = "refreshed").hasChanges)
        assertTrue(saved.copy(apiToken = "new-key").hasChanges)
        assertTrue(saved.copy(isDefault = true).hasChanges)
        assertTrue(saved.copy(customModelName = "other-model").hasChanges)
        assertFalse(AiConfigEditState().hasChanges)
    }

    @Test fun remoteNewsChecksAllEditableFieldsAndBlocksActionsDuringWork() {
        val saved = RemoteNewsSettingsState(isEditing = true, name = "Source", baseUrl = "https://example.com",
            token = "saved-key", savedDraft = RemoteNewsDraft("Source", "https://example.com", "saved-key", true))
        assertFalse(saved.hasChanges)
        assertTrue(saved.copy(name = "New source").hasChanges)
        assertTrue(saved.copy(baseUrl = "https://other.example.com").hasChanges)
        assertTrue(saved.copy(token = "new-key").hasChanges)
        assertTrue(saved.copy(enabled = false).hasChanges)
        assertFalse(saved.copy(isEditing = false, name = "New source").hasChanges)
        val scenarios = listOf(
            Triple(false, false, false) to false,
            Triple(true, false, false) to true,
            Triple(false, true, false) to true,
            Triple(false, false, true) to true,
            Triple(true, true, false) to true,
            Triple(true, false, true) to true,
            Triple(false, true, true) to true,
            Triple(true, true, true) to true,
        )
        scenarios.forEach { (flags, expectedBusy) ->
            assertEquals(expectedBusy, saved.copy(isSaving = flags.first, isTesting = flags.second,
                isDeleting = flags.third).busy, "saving/testing/deleting=$flags")
        }
    }
}
