package com.dailysatori.data.repository

import com.dailysatori.config.DatabaseConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class McpServerRepositoryPresetApiTest {
    @Test
    fun schemaVersionTracksMcpPresetMetadata() {
        assertEquals(8L, DatabaseConfig.currentSchemaVersion)
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
