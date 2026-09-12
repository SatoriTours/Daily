package com.dailysatori.ui.feature.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.service.diary.DiaryThoughtService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiaryThoughtViewModel(private val service: DiaryThoughtService) : ViewModel() {
    val state = service.state
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError = _saveError.asStateFlow()

    fun refresh() = service.requestRefresh()

    fun setUseInChat(enabled: Boolean) {
        viewModelScope.launch {
            _saveError.value = null
            try {
                kotlinx.coroutines.withContext(Dispatchers.IO) { service.setUseInChat(enabled) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _saveError.value = "对话偏好保存失败，请重试"
            }
        }
    }

    fun saveCorrections(text: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            _saveError.value = null
            try {
                kotlinx.coroutines.withContext(Dispatchers.IO) { service.saveCorrections(text) }
                onSaved()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _saveError.value = "补充与修正保存失败，请重试"
            }
        }
    }
}
