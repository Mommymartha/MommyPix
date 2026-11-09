package com.project.mommypix.data.preferences

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
    CUSTOM("Custom interval", -1)
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

data class UserPreferences(
    val wallpaperTarget: WallpaperTarget = WallpaperTarget.BOTH,
    val bothScreenMode: BothScreenMode = BothScreenMode.SAME_IMAGE,
    val isAutoWallpaperEnabled: Boolean = false,
    val autoWallpaperInterval: UpdateInterval = UpdateInterval.HOURS_1,
    val customIntervalMinutes: Long = 1L,
    val isWifiOnly: Boolean = false,
    val downloadQuality: DownloadQuality = DownloadQuality.ORIGINAL,
    val appTheme: AppTheme = AppTheme.SYSTEM,
    val lastAutoUpdateTimestamp: Long = 0L,
    val includedTags: Set<String> = emptySet(),
    val blockedTags: Set<String> = emptySet()
) {
    val effectiveIntervalMinutes: Long
        get() = if (autoWallpaperInterval == UpdateInterval.CUSTOM) {
            customIntervalMinutes.coerceAtLeast(1L)
        } else {
            autoWallpaperInterval.minutes.coerceAtLeast(1L)
        }

    val displayIntervalTitle: String
        get() = if (autoWallpaperInterval == UpdateInterval.CUSTOM) {
            val hours = customIntervalMinutes / 60
            val mins = customIntervalMinutes % 60
            when {
                customIntervalMinutes < 60 -> "$customIntervalMinutes min"
                mins == 0L -> "$hours ${if (hours == 1L) "hour" else "hours"}"
                else -> "${hours}h ${mins}m"
            }
        } else {
            autoWallpaperInterval.title
        }
}
