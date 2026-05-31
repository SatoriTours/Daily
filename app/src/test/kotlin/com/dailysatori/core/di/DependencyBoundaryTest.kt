package com.dailysatori.core.di

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DependencyBoundaryTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun aiConfigEditorDoesNotPullDependenciesFromKoinInsideComposable() {
        val source = source("src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditScreen.kt")

        assertFalse(source.contains("KoinPlatform.getKoin()"))
        assertFalse(source.contains("AIConfigRepository"))
        assertFalse(source.contains("AiService"))
        assertTrue(source.contains("AiConfigEditViewModel"))
        assertTrue(source.contains("koinViewModel"))
    }

    @Test
    fun memorySearchSheetDoesNotInjectRepositoriesInsideComposable() {
        val source = source("src/main/kotlin/com/dailysatori/ui/feature/aichat/MemorySearchSheet.kt")

        assertFalse(source.contains("koinInject"))
        assertFalse(source.contains("MemoryRepository"))
        assertFalse(source.contains("MemoryExtractService"))
        assertFalse(source.contains("ArticleRepository"))
        assertFalse(source.contains("DiaryRepository"))
        assertFalse(source.contains("BookRepository"))
        assertFalse(source.contains("BookViewpointRepository"))
        assertTrue(source.contains("MemorySearchViewModel"))
        assertTrue(source.contains("koinViewModel"))
    }

    @Test
    fun settingsViewModelUsesConstructorInjectedSettingRepository() {
        val source = source("src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsViewModel.kt")

        assertFalse(source.contains("KoinJavaComponent"))
        assertFalse(source.contains("get<SettingRepository>"))
        assertTrue(source.contains("private val settingRepo: SettingRepository"))
    }

    @Test
    fun dataImportScreenUsesViewModelForImportOperation() {
        val source = source("src/main/kotlin/com/dailysatori/ui/feature/settings/importing/DataImportScreen.kt")

        assertFalse(source.contains("KoinPlatform.getKoin()"))
        assertFalse(source.contains("rememberCoroutineScope"))
        assertFalse(source.contains("importService.importFromZip"))
        assertTrue(source.contains("DataImportViewModel"))
        assertTrue(source.contains("koinViewModel"))
    }

    @Test
    fun dataImportViewModelOwnsImportService() {
        val source = source("src/main/kotlin/com/dailysatori/ui/feature/settings/importing/DataImportViewModel.kt")

        assertTrue(source.contains("private val importService: ImportService"))
        assertTrue(source.contains("viewModelScope.launch(Dispatchers.IO)"))
        assertTrue(source.contains("importService.importFromZip(path)"))
        assertTrue(source.contains("DataImportState(error = \"无法读取文件\")"))
    }

    @Test
    fun viewModelModuleRegistersRefactorViewModelsWithNamedDependencies() {
        val source = source("src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt")

        assertTrue(source.contains("AiConfigEditViewModel("))
        assertTrue(source.contains("MemorySearchViewModel("))
        assertTrue(source.contains("settingRepo = get<SettingRepository>()"))
        assertTrue(source.contains("DataImportViewModel("))
        assertTrue(source.contains("importService = get<ImportService>()"))
        assertTrue(source.contains("sourceRepo = get()"))
        assertTrue(source.contains("remoteNewsService = get()"))
        assertTrue(source.contains("repo = get()"))
        assertTrue(source.contains("remoteMcpClient = get()"))
        assertTrue(source.contains("repository = get()"))
        assertTrue(source.contains("connectionTester = get()"))
    }
}
