package com.project.mommypix.data.preferences

import com.project.mommypix.data.reddit.SubredditPacks

enum class WallpaperTarget(val title: String) {
    HOME("Home Screen Only"),
    LOCK("Lock Screen Only"),
    BOTH("Both Screens")
}

enum class BothScreenMode(val title: String, val description: String) {
    SAME_IMAGE("Same Image", "Apply one random image to both home and lock screens"),
    DIFFERENT_IMAGES("Different Images", "Fetch two independent random images for home and lock")
}

enum class UpdateInterval(val title: String, val minutes: Long) {
    MINUTES_1("1 minute (Fast test)", 1),
    MINUTES_5("5 minutes", 5),
    MINUTES_15("15 minutes", 15),
    MINUTES_30("30 minutes", 30),
    HOURS_1("1 hour", 60),
    HOURS_3("3 hours", 180),
    HOURS_6("6 hours", 360),
    HOURS_12("12 hours", 720),
    HOURS_24("24 hours", 1440),
    CUSTOM("Custom interval", -1),
    RANDOM("Random interval", -2)
}

enum class DownloadQuality(val title: String) {
    ORIGINAL("Original Resolution"),
    SAMPLE("Sample (Optimized)")
}

enum class AppTheme(val title: String) {
    SYSTEM("Follow System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class RatingFilter(val id: String, val title: String, val description: String) {
    BOTH("both", "Both (Safe + NSFW)", "Shuffle across all ratings at random"),
    SAFE_ONLY("safe", "Safe Only (SFW)", "Only clean, general, and all-ages wallpapers"),
    NSFW_ONLY("nsfw", "NSFW Only (Mature)", "Only adult, questionable, and explicit wallpapers")
}

enum class ImageSource(
    val id: String,
    val displayName: String,
    val description: String,
    val isZeroAuth: Boolean
) {
    RULE34(
        id = "rule34",
        displayName = "Rule34.xxx",
        description = "NSFW & Anime imageboard • Authenticated API",
        isZeroAuth = true
    ),
    PORNPICS(
        id = "pornpics",
        displayName = "PornPics",
        description = "Real model photography & galleries • Web Scraper",
        isZeroAuth = true
    ),
    YANDE_RE(
        id = "yande",
        displayName = "Yande.re",
        description = "Ultra high-resolution artwork & NSFW scans • Zero-Auth",
        isZeroAuth = true
    );

    companion object {
        fun fromId(id: String): ImageSource? = entries.find { it.id == id }
        val DEFAULT_ENABLED: Set<String> = entries.map { it.id }.toSet()
    }
}

data class UserPreferences(
    val wallpaperTarget: WallpaperTarget = WallpaperTarget.BOTH,
    val bothScreenMode: BothScreenMode = BothScreenMode.DIFFERENT_IMAGES,
    val isAutoWallpaperEnabled: Boolean = false,
    val autoWallpaperInterval: UpdateInterval = UpdateInterval.HOURS_1,
    val customIntervalMinutes: Long = 1L,
    val randomMinMinutes: Long = 5L,
    val randomMaxMinutes: Long = 30L,
    val isWifiOnly: Boolean = false,
    val downloadQuality: DownloadQuality = DownloadQuality.ORIGINAL,
    val appTheme: AppTheme = AppTheme.SYSTEM,
    val lastAutoUpdateTimestamp: Long = 0L,
    val includedTags: Set<String> = emptySet(),
    val blockedTags: Set<String> = emptySet(),
    val enabledSources: Set<String> = ImageSource.DEFAULT_ENABLED,
    val useCustomRule34Credentials: Boolean = false,
    val ratingFilter: RatingFilter = RatingFilter.BOTH,
    val saveToGallery: Boolean = false,
    val isBooruEnabled: Boolean = true,
    val isRedditEnabled: Boolean = true,
    val enabledSubreddits: Set<String> = SubredditPacks.DEFAULT_ENABLED_SUBREDDITS,
    val customSubreddits: Set<String> = emptySet()
) {
    val effectiveIntervalMinutes: Long
        get() = when (autoWallpaperInterval) {
            UpdateInterval.CUSTOM -> customIntervalMinutes.coerceAtLeast(1L)
            UpdateInterval.RANDOM -> {
                val min = randomMinMinutes.coerceAtLeast(1L)
                val max = randomMaxMinutes.coerceAtLeast(min)
                if (min == max) min else kotlin.random.Random.nextLong(min, max + 1L)
            }
            else -> autoWallpaperInterval.minutes.coerceAtLeast(1L)
        }

    val displayIntervalTitle: String
        get() = when (autoWallpaperInterval) {
            UpdateInterval.CUSTOM -> {
                val hours = customIntervalMinutes / 60
                val mins = customIntervalMinutes % 60
                when {
                    customIntervalMinutes < 60 -> "$customIntervalMinutes min"
                    mins == 0L -> "$hours ${if (hours == 1L) "hour" else "hours"}"
                    else -> "${hours}h ${mins}m"
                }
            }
            UpdateInterval.RANDOM -> {
                "Random ($randomMinMinutes - $randomMaxMinutes min)"
            }
            else -> autoWallpaperInterval.title
        }

    val allActiveSubreddits: Set<String>
        get() = enabledSubreddits + customSubreddits
}
