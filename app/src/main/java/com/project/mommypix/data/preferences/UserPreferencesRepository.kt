package com.project.mommypix.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.project.mommypix.data.reddit.SubredditPacks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val WALLPAPER_TARGET = stringPreferencesKey("wallpaper_target")
        val BOTH_SCREEN_MODE = stringPreferencesKey("both_screen_mode")
        val AUTO_WALLPAPER_ENABLED = booleanPreferencesKey("auto_wallpaper_enabled")
        val AUTO_WALLPAPER_INTERVAL = stringPreferencesKey("auto_wallpaper_interval")
        val CUSTOM_INTERVAL_MINUTES = longPreferencesKey("custom_interval_minutes")
        val RANDOM_MIN_MINUTES = longPreferencesKey("random_min_minutes")
        val RANDOM_MAX_MINUTES = longPreferencesKey("random_max_minutes")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val APP_THEME = stringPreferencesKey("app_theme")
        val LAST_AUTO_UPDATE_TIMESTAMP = longPreferencesKey("last_auto_update_timestamp")
        val INCLUDED_TAGS = stringSetPreferencesKey("included_tags")
        val BLOCKED_TAGS = stringSetPreferencesKey("blocked_tags")
        val ENABLED_SOURCES = stringSetPreferencesKey("enabled_sources")
        val USE_CUSTOM_RULE34_CREDS = booleanPreferencesKey("use_custom_rule34_creds")
        val RATING_FILTER = stringPreferencesKey("rating_filter")
        val SAVE_TO_GALLERY = booleanPreferencesKey("save_to_gallery")
        val IS_BOORU_ENABLED = booleanPreferencesKey("is_booru_enabled")
        val IS_REDDIT_ENABLED = booleanPreferencesKey("is_reddit_enabled")
        val ENABLED_SUBREDDITS = stringSetPreferencesKey("enabled_subreddits")
        val CUSTOM_SUBREDDITS = stringSetPreferencesKey("custom_subreddits")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val targetStr = preferences[PreferencesKeys.WALLPAPER_TARGET]
            val target = targetStr?.let { runCatching { WallpaperTarget.valueOf(it) }.getOrNull() }
                ?: WallpaperTarget.BOTH

            val modeStr = preferences[PreferencesKeys.BOTH_SCREEN_MODE]
            val mode = modeStr?.let { runCatching { BothScreenMode.valueOf(it) }.getOrNull() }
                ?: BothScreenMode.DIFFERENT_IMAGES

            val isAutoEnabled = preferences[PreferencesKeys.AUTO_WALLPAPER_ENABLED] ?: false

            val intervalStr = preferences[PreferencesKeys.AUTO_WALLPAPER_INTERVAL]
            val interval = intervalStr?.let { runCatching { UpdateInterval.valueOf(it) }.getOrNull() }
                ?: UpdateInterval.HOURS_1

            val customMinutes = preferences[PreferencesKeys.CUSTOM_INTERVAL_MINUTES] ?: 1L
            val randomMin = preferences[PreferencesKeys.RANDOM_MIN_MINUTES] ?: 5L
            val randomMax = preferences[PreferencesKeys.RANDOM_MAX_MINUTES] ?: 30L

            val wifiOnly = preferences[PreferencesKeys.WIFI_ONLY] ?: false

            val qualityStr = preferences[PreferencesKeys.DOWNLOAD_QUALITY]
            val quality = qualityStr?.let { runCatching { DownloadQuality.valueOf(it) }.getOrNull() }
                ?: DownloadQuality.ORIGINAL

            val themeStr = preferences[PreferencesKeys.APP_THEME]
            val theme = themeStr?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }
                ?: AppTheme.SYSTEM

            val lastUpdate = preferences[PreferencesKeys.LAST_AUTO_UPDATE_TIMESTAMP] ?: 0L

            val incTags = preferences[PreferencesKeys.INCLUDED_TAGS] ?: emptySet()
            val blkTags = preferences[PreferencesKeys.BLOCKED_TAGS] ?: emptySet()

            val rawSources = preferences[PreferencesKeys.ENABLED_SOURCES]
            val sources = rawSources?.filter { ImageSource.fromId(it) != null }?.toSet()
                ?.ifEmpty { ImageSource.DEFAULT_ENABLED }
                ?: ImageSource.DEFAULT_ENABLED
            val useCustomCreds = preferences[PreferencesKeys.USE_CUSTOM_RULE34_CREDS] ?: false

            val ratingFilterStr = preferences[PreferencesKeys.RATING_FILTER]
            val ratingFilter = ratingFilterStr?.let { runCatching { RatingFilter.valueOf(it) }.getOrNull() }
                ?: RatingFilter.BOTH

            val saveToGallery = preferences[PreferencesKeys.SAVE_TO_GALLERY] ?: false
            val isBooruEnabled = preferences[PreferencesKeys.IS_BOORU_ENABLED] ?: true
            val isRedditEnabled = preferences[PreferencesKeys.IS_REDDIT_ENABLED] ?: true
            val enabledSubs = preferences[PreferencesKeys.ENABLED_SUBREDDITS] ?: SubredditPacks.DEFAULT_ENABLED_SUBREDDITS
            val customSubs = preferences[PreferencesKeys.CUSTOM_SUBREDDITS] ?: emptySet()

            UserPreferences(
                wallpaperTarget = target,
                bothScreenMode = mode,
                isAutoWallpaperEnabled = isAutoEnabled,
                autoWallpaperInterval = interval,
                customIntervalMinutes = customMinutes,
                randomMinMinutes = randomMin,
                randomMaxMinutes = randomMax,
                isWifiOnly = wifiOnly,
                downloadQuality = quality,
                appTheme = theme,
                lastAutoUpdateTimestamp = lastUpdate,
                includedTags = incTags,
                blockedTags = blkTags,
                enabledSources = sources,
                useCustomRule34Credentials = useCustomCreds,
                ratingFilter = ratingFilter,
                saveToGallery = saveToGallery,
                isBooruEnabled = isBooruEnabled,
                isRedditEnabled = isRedditEnabled,
                enabledSubreddits = enabledSubs,
                customSubreddits = customSubs
            )
        }

    suspend fun setWallpaperTarget(target: WallpaperTarget) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WALLPAPER_TARGET] = target.name
        }
    }

    suspend fun setBothScreenMode(mode: BothScreenMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BOTH_SCREEN_MODE] = mode.name
        }
    }

    suspend fun setAutoWallpaperEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_WALLPAPER_ENABLED] = enabled
        }
    }

    suspend fun setAutoWallpaperInterval(interval: UpdateInterval) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_WALLPAPER_INTERVAL] = interval.name
        }
    }

    suspend fun setCustomIntervalMinutes(minutes: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CUSTOM_INTERVAL_MINUTES] = minutes
        }
    }

    suspend fun setWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WIFI_ONLY] = wifiOnly
        }
    }

    suspend fun setDownloadQuality(quality: DownloadQuality) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DOWNLOAD_QUALITY] = quality.name
        }
    }

    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_THEME] = theme.name
        }
    }

    suspend fun setLastAutoUpdateTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_AUTO_UPDATE_TIMESTAMP] = timestamp
        }
    }

    suspend fun addIncludedTag(tag: String) {
        val clean = tag.trim().lowercase().replace(' ', '_')
        if (clean.isBlank()) return
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.INCLUDED_TAGS] ?: emptySet()
            preferences[PreferencesKeys.INCLUDED_TAGS] = current + clean
        }
    }

    suspend fun removeIncludedTag(tag: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.INCLUDED_TAGS] ?: emptySet()
            preferences[PreferencesKeys.INCLUDED_TAGS] = current - tag
        }
    }

    suspend fun addBlockedTag(tag: String) {
        val clean = tag.trim().lowercase().replace(' ', '_')
        if (clean.isBlank()) return
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.BLOCKED_TAGS] ?: emptySet()
            preferences[PreferencesKeys.BLOCKED_TAGS] = current + clean
        }
    }

    suspend fun removeBlockedTag(tag: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.BLOCKED_TAGS] ?: emptySet()
            preferences[PreferencesKeys.BLOCKED_TAGS] = current - tag
        }
    }

    suspend fun clearAllTagFilters() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.INCLUDED_TAGS)
            preferences.remove(PreferencesKeys.BLOCKED_TAGS)
        }
    }

    suspend fun toggleSource(sourceId: String, isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.ENABLED_SOURCES] ?: ImageSource.DEFAULT_ENABLED
            val updated = if (isEnabled) {
                current + sourceId
            } else {
                if (current.size > 1) current - sourceId else current
            }
            preferences[PreferencesKeys.ENABLED_SOURCES] = updated
        }
    }

    suspend fun setUseCustomRule34Credentials(useCustom: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_CUSTOM_RULE34_CREDS] = useCustom
        }
    }

    suspend fun setRatingFilter(filter: RatingFilter) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RATING_FILTER] = filter.name
        }
    }

    suspend fun setSaveToGallery(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SAVE_TO_GALLERY] = enabled
        }
    }

    suspend fun setRandomIntervalRange(min: Long, max: Long) {
        val safeMin = min.coerceAtLeast(1L)
        val safeMax = max.coerceAtLeast(safeMin)
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RANDOM_MIN_MINUTES] = safeMin
            preferences[PreferencesKeys.RANDOM_MAX_MINUTES] = safeMax
        }
    }

    suspend fun setBooruEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_BOORU_ENABLED] = enabled
        }
    }

    suspend fun setRedditEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_REDDIT_ENABLED] = enabled
        }
    }

    suspend fun toggleSubreddit(subreddit: String, isEnabled: Boolean) {
        val clean = subreddit.trim().removePrefix("r/").removePrefix("/")
        if (clean.isBlank()) return
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.ENABLED_SUBREDDITS] ?: SubredditPacks.DEFAULT_ENABLED_SUBREDDITS
            preferences[PreferencesKeys.ENABLED_SUBREDDITS] = if (isEnabled) {
                current + clean
            } else {
                current - clean
            }
        }
    }

    suspend fun addCustomSubreddit(subreddit: String) {
        val clean = subreddit.trim().removePrefix("r/").removePrefix("/")
        if (clean.isBlank()) return
        context.dataStore.edit { preferences ->
            val currentCustom = preferences[PreferencesKeys.CUSTOM_SUBREDDITS] ?: emptySet()
            preferences[PreferencesKeys.CUSTOM_SUBREDDITS] = currentCustom + clean

            val currentEnabled = preferences[PreferencesKeys.ENABLED_SUBREDDITS] ?: SubredditPacks.DEFAULT_ENABLED_SUBREDDITS
            preferences[PreferencesKeys.ENABLED_SUBREDDITS] = currentEnabled + clean
        }
    }

    suspend fun removeCustomSubreddit(subreddit: String) {
        val clean = subreddit.trim().removePrefix("r/").removePrefix("/")
        context.dataStore.edit { preferences ->
            val currentCustom = preferences[PreferencesKeys.CUSTOM_SUBREDDITS] ?: emptySet()
            preferences[PreferencesKeys.CUSTOM_SUBREDDITS] = currentCustom - clean

            val currentEnabled = preferences[PreferencesKeys.ENABLED_SUBREDDITS] ?: SubredditPacks.DEFAULT_ENABLED_SUBREDDITS
            preferences[PreferencesKeys.ENABLED_SUBREDDITS] = currentEnabled - clean
        }
    }

    suspend fun setEnabledSubreddits(subreddits: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ENABLED_SUBREDDITS] = subreddits
        }
    }
}
