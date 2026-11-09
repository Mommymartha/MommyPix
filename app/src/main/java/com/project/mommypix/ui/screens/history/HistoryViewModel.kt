package com.project.mommypix.ui.screens.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.project.mommypix.MommyPixApp
import com.project.mommypix.data.local.DownloadHistoryItem
import com.project.mommypix.data.preferences.WallpaperTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val historyItems: List<DownloadHistoryItem> = emptyList(),
    val totalStorageBytes: Long = 0L,
    val isApplying: Boolean = false,
    val userMessage: String? = null,
    val errorMessage: String? = null
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MommyPixApp
    private val repository = app.repository

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeDownloadHistory().collectLatest { items ->
                val storage = repository.getStorageUsageBytes()
                _uiState.update {
                    it.copy(
                        historyItems = items,
                        totalStorageBytes = storage
                    )
                }
            }
        }
    }

    fun applyWallpaper(item: DownloadHistoryItem, target: WallpaperTarget) {
        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true, errorMessage = null) }
            val result = repository.applyHistoricalWallpaper(item, target)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isApplying = false,
                            userMessage = "Wallpaper #${item.id} applied to ${target.title}!"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isApplying = false,
                            errorMessage = "Failed to apply wallpaper: ${err.localizedMessage}"
                        )
                    }
                }
            )
        }
    }

    fun deleteItem(item: DownloadHistoryItem) {
        viewModelScope.launch {
            repository.deleteDownloadHistory(item.id)
            val storage = repository.getStorageUsageBytes()
            _uiState.update {
                it.copy(
                    totalStorageBytes = storage,
                    userMessage = "Wallpaper #${item.id} removed from history."
                )
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearDownloadHistory()
            _uiState.update {
                it.copy(
                    totalStorageBytes = 0L,
                    userMessage = "Download history and wallpaper cache cleared."
                )
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(userMessage = null, errorMessage = null) }
    }
}

