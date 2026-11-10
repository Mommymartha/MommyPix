package com.project.mommypix.ui.screens.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.project.mommypix.MommyPixApp
import com.project.mommypix.admin.DeviceAdminManager
import com.project.mommypix.data.preferences.AppTheme
import com.project.mommypix.data.preferences.BothScreenMode
import com.project.mommypix.data.preferences.DownloadQuality
import com.project.mommypix.data.preferences.RatingFilter
import com.project.mommypix.data.preferences.UpdateInterval
import com.project.mommypix.data.preferences.UserPreferences
import com.project.mommypix.data.preferences.WallpaperTarget
import com.project.mommypix.data.reddit.SubredditPacks
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
    val randomMinMinutesInput: String = "5",
    val randomMaxMinutesInput: String = "30",
    val customSubredditInput: String = "",
    val includedTagInput: String = "",
    val blockedTagInput: String = "",
    val hasCredentials: Boolean = false,
    val isTestingConnection: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val canScheduleExactAlarms: Boolean = true,
    val isDeviceAdminActive: Boolean = false,
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
    private val deviceAdminManager = DeviceAdminManager(app)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadCredentials()
        observePreferences()
        updateStorageAndCacheInfo()
        checkBatteryOptimizationStatus()
        checkExactAlarmStatus()
        checkDeviceAdminStatus()
    }

    fun checkDeviceAdminStatus() {
        val active = deviceAdminManager.isDeviceAdminActive
        _uiState.update { it.copy(isDeviceAdminActive = active) }
    }

    fun getEnableDeviceAdminIntent(): Intent = deviceAdminManager.createEnableAdminIntent()
    fun getSecuritySettingsFallbackIntent(): Intent = deviceAdminManager.createSecuritySettingsFallbackIntent()

    fun deactivateDeviceAdmin() {
        val success = deviceAdminManager.deactivateAdmin()
        checkDeviceAdminStatus()
        _uiState.update {
            it.copy(
                userMessage = if (success) "Uninstall protection deactivated." else "Uninstall protection is not active."
            )
        }
    }

    fun checkBatteryOptimizationStatus() {
        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoring = pm?.isIgnoringBatteryOptimizations(app.packageName) ?: false
        _uiState.update { it.copy(isIgnoringBatteryOptimizations = isIgnoring) }
    }

    fun checkExactAlarmStatus() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val am = app.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
            val canSchedule = am?.canScheduleExactAlarms() ?: true
            _uiState.update { it.copy(canScheduleExactAlarms = canSchedule) }
        } else {
            _uiState.update { it.copy(canScheduleExactAlarms = true) }
        }
    }

    private fun loadCredentials() {
        val uid = credentialStorage.getUserId().orEmpty()
        val key = credentialStorage.getApiKey().orEmpty()
        _uiState.update {
            it.copy(
                userIdInput = uid,
                apiKeyInput = key,
                hasCredentials = credentialStorage.hasCustomCredentials()
            )
        }
    }

    fun toggleSource(sourceId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.toggleSource(sourceId, isEnabled)
            repository.clearPrefetchPoolAndReplenish()
        }
    }

    fun toggleUseCustomRule34Credentials(useCustom: Boolean) {
        viewModelScope.launch {
            prefsRepository.setUseCustomRule34Credentials(useCustom)
            repository.clearPrefetchPoolAndReplenish()
            _uiState.update {
                it.copy(
                    userMessage = if (useCustom) "Switched to private API key mode" else "Using default public key (Zero-effort)"
                )
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            prefsRepository.userPreferencesFlow.collectLatest { prefs ->
                val nextUpdateText = if (prefs.isAutoWallpaperEnabled) {
                    WallpaperScheduler.formatEstimatedNextUpdate(
                        intervalMinutes = prefs.effectiveIntervalMinutes,
                        lastTimestamp = prefs.lastAutoUpdateTimestamp,
                        isRandom = prefs.autoWallpaperInterval == UpdateInterval.RANDOM,
                        randomMin = prefs.randomMinMinutes,
                        randomMax = prefs.randomMaxMinutes
                    )
                } else {
                    "Automatic wallpaper updates are turned OFF"
                }

                _uiState.update {
                    it.copy(
                        userPreferences = prefs,
                        customIntervalMinutesInput = prefs.customIntervalMinutes.toString(),
                        randomMinMinutesInput = prefs.randomMinMinutes.toString(),
                        randomMaxMinutesInput = prefs.randomMaxMinutes.toString(),
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

    fun onRandomMinMinutesChanged(value: String) {
        val filtered = value.filter { it.isDigit() }
        _uiState.update { it.copy(randomMinMinutesInput = filtered) }

        val parsedMin = filtered.toLongOrNull() ?: return
        if (parsedMin > 0) {
            val max = _uiState.value.userPreferences.randomMaxMinutes.coerceAtLeast(parsedMin)
            viewModelScope.launch {
                prefsRepository.setRandomIntervalRange(parsedMin, max)
                val prefs = _uiState.value.userPreferences
                if (prefs.isAutoWallpaperEnabled && prefs.autoWallpaperInterval == UpdateInterval.RANDOM) {
                    WallpaperScheduler.scheduleWallpaperWork(
                        app,
                        prefs.effectiveIntervalMinutes,
                        prefs.isWifiOnly
                    )
                }
            }
        }
    }

    fun onRandomMaxMinutesChanged(value: String) {
        val filtered = value.filter { it.isDigit() }
        _uiState.update { it.copy(randomMaxMinutesInput = filtered) }

        val parsedMax = filtered.toLongOrNull() ?: return
        if (parsedMax > 0) {
            val min = _uiState.value.userPreferences.randomMinMinutes
            val safeMax = parsedMax.coerceAtLeast(min)
            viewModelScope.launch {
                prefsRepository.setRandomIntervalRange(min, safeMax)
                val prefs = _uiState.value.userPreferences
                if (prefs.isAutoWallpaperEnabled && prefs.autoWallpaperInterval == UpdateInterval.RANDOM) {
                    WallpaperScheduler.scheduleWallpaperWork(
                        app,
                        prefs.effectiveIntervalMinutes,
                        prefs.isWifiOnly
                    )
                }
            }
        }
    }

    // --- Subreddit Wallpapers Operations ---

    fun toggleBooruEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setBooruEnabled(enabled)
            repository.clearPrefetchPoolAndReplenish()
            _uiState.update {
                it.copy(
                    userMessage = if (enabled) "Imageboard / Sites rotation enabled" else "Imageboard / Sites rotation disabled"
                )
            }
        }
    }

    fun toggleRedditEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setRedditEnabled(enabled)
            repository.clearPrefetchPoolAndReplenish()
            _uiState.update {
                it.copy(
                    userMessage = if (enabled) "Subreddit wallpapers enabled" else "Subreddit wallpapers disabled"
                )
            }
        }
    }

    fun toggleSubreddit(subreddit: String, isChecked: Boolean) {
        viewModelScope.launch {
            prefsRepository.toggleSubreddit(subreddit, isChecked)
            repository.clearPrefetchPoolAndReplenish()
        }
    }

    fun onCustomSubredditInputChanged(value: String) {
        _uiState.update { it.copy(customSubredditInput = value) }
    }

    fun addCustomSubreddit(subreddit: String = _uiState.value.customSubredditInput) {
        val clean = subreddit.trim().removePrefix("r/").removePrefix("/")
        if (clean.isBlank()) return
        viewModelScope.launch {
            prefsRepository.addCustomSubreddit(clean)
            repository.clearPrefetchPoolAndReplenish()
            _uiState.update {
                it.copy(
                    customSubredditInput = "",
                    userMessage = "Added r/$clean to your active subreddits"
                )
            }
        }
    }

    fun removeCustomSubreddit(subreddit: String) {
        viewModelScope.launch {
            prefsRepository.removeCustomSubreddit(subreddit)
            repository.clearPrefetchPoolAndReplenish()
            _uiState.update {
                it.copy(userMessage = "Removed r/$subreddit")
            }
        }
    }

    fun enableAllSubreddits() {
        viewModelScope.launch {
            prefsRepository.setEnabledSubreddits(SubredditPacks.ALL_SUBREDDITS.toSet())
            repository.clearPrefetchPoolAndReplenish()
            _uiState.update { it.copy(userMessage = "All curated subreddits enabled") }
        }
    }

    fun resetSubredditsToDefault() {
        viewModelScope.launch {
            prefsRepository.setEnabledSubreddits(SubredditPacks.DEFAULT_ENABLED_SUBREDDITS)
            repository.clearPrefetchPoolAndReplenish()
            _uiState.update { it.copy(userMessage = "Subreddits reset to recommended set") }
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
            repository.clearPrefetchPoolAndReplenish()
            _uiState.update { it.copy(includedTagInput = "", userMessage = "Added #$clean to required tags") }
        }
    }

    fun removeIncludedTag(tag: String) {
        viewModelScope.launch {
            prefsRepository.removeIncludedTag(tag)
            repository.clearPrefetchPoolAndReplenish()
            _uiState.update { it.copy(userMessage = "Removed #$tag from required tags") }
        }
    }

    fun addBlockedTag(tag: String = _uiState.value.blockedTagInput) {
        val clean = tag.trim().lowercase().replace(' ', '_')
        if (clean.isBlank()) return
        viewModelScope.launch {
            prefsRepository.addBlockedTag(clean)
            repository.clearPrefetchPoolAndReplenish()
            _uiState.update { it.copy(blockedTagInput = "", userMessage = "Blocked #$clean — will not be downloaded") }
        }
    }

    fun removeBlockedTag(tag: String) {
        viewModelScope.launch {
            prefsRepository.removeBlockedTag(tag)
            repository.clearPrefetchPoolAndReplenish()
            _uiState.update { it.copy(userMessage = "Unblocked #$tag") }
        }
    }

    fun clearAllTagFilters() {
        viewModelScope.launch {
            prefsRepository.clearAllTagFilters()
            repository.clearPrefetchPoolAndReplenish()
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

    fun setRatingFilter(filter: RatingFilter) {
        viewModelScope.launch {
            prefsRepository.setRatingFilter(filter)
            repository.clearPrefetchPoolAndReplenish()
            _uiState.update { it.copy(userMessage = "Content filter set to: ${filter.title}") }
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
                val effectiveMins = when (interval) {
                    UpdateInterval.CUSTOM -> prefs.customIntervalMinutes.coerceAtLeast(1L)
                    UpdateInterval.RANDOM -> prefs.effectiveIntervalMinutes
                    else -> interval.minutes.coerceAtLeast(1L)
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

    fun setSaveToGallery(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setSaveToGallery(enabled)
            _uiState.update {
                it.copy(
                    userMessage = if (enabled) {
                        "Auto-save to Gallery enabled (Pictures/MommyPix)"
                    } else {
                        "Auto-save to Gallery disabled (wallpapers stay private)"
                    }
                )
            }
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
