package com.dailysatori.data.repository

import com.dailysatori.config.DatabaseConfig
import kotlin.test.Test
import kotlin.test.assertTrue

class McpServerRepositoryPresetApiTest {
    @Test
    fun schemaVersionTracksMcpPresetMetadata() {
        assertTrue(DatabaseConfig.currentSchemaVersion >= 23L)
    }

    @Suppress("unused")
    private fun repositoryExposesPresetPersistenceApi(repository: McpServerRepository) {
        repository.getByServerUrl("https://example.com/mcp")
        repository.insertPreset(
            name = "Example / Search",
            serverUrl = "https://example.com/mcp",
            apiKey = "secret",
            provider = "example",
            templateId = "example-search",
            templateType = "normal",
            configJson = "{}",
        )
    }
}
