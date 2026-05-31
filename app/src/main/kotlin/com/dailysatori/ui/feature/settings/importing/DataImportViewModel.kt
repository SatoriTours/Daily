package com.dailysatori.ui.feature.settings.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.service.import.ImportService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DataImportState(
    val isImporting: Boolean = false,
    val progress: Float = 0f,
    val result: ImportService.ImportResult? = null,
    val error: String? = null,
)

class DataImportViewModel(
    private val importService: ImportService,
) : ViewModel() {
    private val _state = MutableStateFlow(DataImportState())
    val state: StateFlow<DataImportState> = _state.asStateFlow()

    init {
        observeImportProgress()
    }

    fun importFromZip(path: String?) {
        if (path == null) {
            _state.value = DataImportState(error = "无法读取文件")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = DataImportState(isImporting = true)
            try {
                val result = importService.importFromZip(path)
                _state.value = DataImportState(progress = 1f, result = result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = DataImportState(error = error.message ?: "导入失败")
            }
        }
    }

    private fun observeImportProgress() {
        viewModelScope.launch(Dispatchers.IO) {
            importService.progress.collect { progress ->
                _state.update { state ->
                    if (state.isImporting) state.copy(progress = progress.toFloat()) else state
                }
            }
        }
    }
}
