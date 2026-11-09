package com.project.mommypix.ui.screens.settings

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.project.mommypix.MommyPixApp
import com.project.mommypix.data.preferences.AppTheme
import com.project.mommypix.data.preferences.BothScreenMode
import com.project.mommypix.data.preferences.DownloadQuality
import com.project.mommypix.data.preferences.UpdateInterval
import com.project.mommypix.data.preferences.UserPreferences
import com.project.mommypix.data.preferences.WallpaperTarget
import com.project.mommypix.worker.WallpaperScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val userIdInput: String = "",
    val apiKeyInput: String = "",
    val customIntervalMinutesInput: String = "1",
    val includedTagInput: String = "",
    val blockedTagInput: String = "",
    val hasCredentials: Boolean = false,
    val isTestingConnection: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val userPreferences: UserPreferences = UserPreferences(),
    val storageUsageBytes: Long = 0L,
    val nextScheduledUpdateText: String = "",
    val connectionStatusMessage: String? = null,
    val isConnectionSuccess: Boolean? = null,
    val userMessage: String? = null,
    val errorMessage: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MommyPixApp
    private val credentialStorage = app.credentialStorage
    private val prefsRepository = app.preferencesRepository
    private val repository = app.repository

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadCredentials()
        observePreferences()
        updateStorageAndCacheInfo()
        checkBatteryOptimizationStatus()
    }

    fun checkBatteryOptimizationStatus() {
        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoring = pm?.isIgnoringBatteryOptimizations(app.packageName) ?: false
        _uiState.update { it.copy(isIgnoringBatteryOptimizations = isIgnoring) }
    }

    private fun loadCredentials() {
        val uid = credentialStorage.getUserId().orEmpty()
        val key = credentialStorage.getApiKey().orEmpty()
        _uiState.update {
            it.copy(
                userIdInput = uid,
                apiKeyInput = key,
                hasCredentials = credentialStorage.hasCredentials()
            )
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            prefsRepository.userPreferencesFlow.collectLatest { prefs ->
                val nextUpdateText = if (prefs.isAutoWallpaperEnabled) {
                    WallpaperScheduler.formatEstimatedNextUpdate(
                        prefs.effectiveIntervalMinutes,
                        prefs.lastAutoUpdateTimestamp
                    )
                } else {
                    "Automatic wallpaper updates are turned OFF"
                }

                _uiState.update {
                    it.copy(
                        userPreferences = prefs,
                        customIntervalMinutesInput = prefs.customIntervalMinutes.toString(),
                        nextScheduledUpdateText = nextUpdateText
                    )
                }
            }
        }
    }

    fun onUserIdChanged(value: String) {
        _uiState.update { it.copy(userIdInput = value) }
    }

    fun onApiKeyChanged(value: String) {
        _uiState.update { it.copy(apiKeyInput = value) }
    }

    fun onCustomIntervalMinutesChanged(value: String) {
        val filtered = value.filter { it.isDigit() }
        _uiState.update { it.copy(customIntervalMinutesInput = filtered) }

        val parsed = filtered.toLongOrNull() ?: return
        if (parsed > 0) {
            viewModelScope.launch {
                prefsRepository.setCustomIntervalMinutes(parsed)
                val prefs = _uiState.value.userPreferences
                if (prefs.isAutoWallpaperEnabled && prefs.autoWallpaperInterval == UpdateInterval.CUSTOM) {
                    WallpaperScheduler.scheduleWallpaperWork(
                        app,
                        parsed.coerceAtLeast(1L),
                        prefs.isWifiOnly
                    )
                }
            }
        }
    }

    // --- Tag Filters & Blacklist Operations ---

    fun onIncludedTagInputChanged(value: String) {
        _uiState.update { it.copy(includedTagInput = value) }
    }

    fun onBlockedTagInputChanged(value: String) {
        _uiState.update { it.copy(blockedTagInput = value) }
    }

    fun addIncludedTag(tag: String = _uiState.value.includedTagInput) {
        val clean = tag.trim().lowercase().replace(' ', '_')
        if (clean.isBlank()) return
        viewModelScope.launch {
            prefsRepository.addIncludedTag(clean)
            repository.clearBuffer()
            _uiState.update { it.copy(includedTagInput = "", userMessage = "Added #$clean to required tags") }
        }
    }

    fun removeIncludedTag(tag: String) {
        viewModelScope.launch {
            prefsRepository.removeIncludedTag(tag)
            repository.clearBuffer()
            _uiState.update { it.copy(userMessage = "Removed #$tag from required tags") }
        }
    }

    fun addBlockedTag(tag: String = _uiState.value.blockedTagInput) {
        val clean = tag.trim().lowercase().replace(' ', '_')
        if (clean.isBlank()) return
        viewModelScope.launch {
            prefsRepository.addBlockedTag(clean)
            repository.clearBuffer()
            _uiState.update { it.copy(blockedTagInput = "", userMessage = "Blocked #$clean — will not be downloaded") }
        }
    }

    fun removeBlockedTag(tag: String) {
        viewModelScope.launch {
            prefsRepository.removeBlockedTag(tag)
            repository.clearBuffer()
            _uiState.update { it.copy(userMessage = "Unblocked #$tag") }
        }
    }

    fun clearAllTagFilters() {
        viewModelScope.launch {
            prefsRepository.clearAllTagFilters()
            repository.clearBuffer()
            _uiState.update { it.copy(userMessage = "All tag filters cleared (Full Random)") }
        }
    }

    // --- Credentials & Settings ---

    fun saveCredentials() {
        val uid = _uiState.value.userIdInput.trim()
        val key = _uiState.value.apiKeyInput.trim()

        if (uid.isBlank() || key.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Both User ID and API Key are required")
            }
            return
        }

        credentialStorage.saveCredentials(uid, key)
        _uiState.update {
            it.copy(
                hasCredentials = true,
                userMessage = "Credentials securely stored in Keystore"
            )
        }
    }

    fun clearCredentials() {
        credentialStorage.clearCredentials()
        _uiState.update {
            it.copy(
                userIdInput = "",
                apiKeyInput = "",
                hasCredentials = false,
                userMessage = "Credentials removed"
            )
        }
    }

    fun testApiConnection() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTestingConnection = true,
                    connectionStatusMessage = null,
                    isConnectionSuccess = null
                )
            }

            if (_uiState.value.userIdInput.isNotBlank() && _uiState.value.apiKeyInput.isNotBlank()) {
                credentialStorage.saveCredentials(
                    _uiState.value.userIdInput.trim(),
                    _uiState.value.apiKeyInput.trim()
                )
            }

            val result = repository.testApiConnection()
            result.fold(
                onSuccess = { msg ->
                    _uiState.update {
                        it.copy(
                            isTestingConnection = false,
                            connectionStatusMessage = msg,
                            isConnectionSuccess = true,
                            hasCredentials = true
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isTestingConnection = false,
                            connectionStatusMessage = "Connection failed: ${err.localizedMessage}",
                            isConnectionSuccess = false
                        )
                    }
                }
            )
        }
    }

    fun setWallpaperTarget(target: WallpaperTarget) {
        viewModelScope.launch {
            prefsRepository.setWallpaperTarget(target)
        }
    }

    fun setBothScreenMode(mode: BothScreenMode) {
        viewModelScope.launch {
            prefsRepository.setBothScreenMode(mode)
        }
    }

    fun setAutoWallpaperEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setAutoWallpaperEnabled(enabled)
            val prefs = _uiState.value.userPreferences
            if (enabled) {
                WallpaperScheduler.scheduleWallpaperWork(
                    app,
                    prefs.effectiveIntervalMinutes,
                    prefs.isWifiOnly
                )
                _uiState.update { it.copy(userMessage = "Automatic wallpaper changes turned ON (${prefs.effectiveIntervalMinutes}m)") }
            } else {
                WallpaperScheduler.cancelWallpaperWork(app)
                _uiState.update { it.copy(userMessage = "Automatic wallpaper changes turned OFF") }
            }
        }
    }

    fun setAutoWallpaperInterval(interval: UpdateInterval) {
        viewModelScope.launch {
            prefsRepository.setAutoWallpaperInterval(interval)
            val prefs = _uiState.value.userPreferences
            if (prefs.isAutoWallpaperEnabled) {
                val effectiveMins = if (interval == UpdateInterval.CUSTOM) {
                    prefs.customIntervalMinutes.coerceAtLeast(1L)
                } else {
                    interval.minutes.coerceAtLeast(1L)
                }
                WallpaperScheduler.scheduleWallpaperWork(app, effectiveMins, prefs.isWifiOnly)
            }
        }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            prefsRepository.setWifiOnly(wifiOnly)
            val prefs = _uiState.value.userPreferences
            if (prefs.isAutoWallpaperEnabled) {
                WallpaperScheduler.scheduleWallpaperWork(app, prefs.effectiveIntervalMinutes, wifiOnly)
            }
        }
    }

    fun setDownloadQuality(quality: DownloadQuality) {
        viewModelScope.launch {
            prefsRepository.setDownloadQuality(quality)
        }
    }

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch {
            prefsRepository.setAppTheme(theme)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            val cleared = repository.clearCache()
            updateStorageAndCacheInfo()
            _uiState.update {
                it.copy(userMessage = "Image cache cleared ($cleared files removed)")
            }
        }
    }

    fun clearDownloadHistory() {
        viewModelScope.launch {
            repository.clearDownloadHistory()
            updateStorageAndCacheInfo()
            _uiState.update {
                it.copy(userMessage = "Download history and wallpaper files cleared")
            }
        }
    }

    private fun updateStorageAndCacheInfo() {
        viewModelScope.launch {
            val storage = repository.getStorageUsageBytes()
            _uiState.update { it.copy(storageUsageBytes = storage) }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(userMessage = null, errorMessage = null) }
    }
}
