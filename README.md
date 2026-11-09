# MommyPix — Random Wallpaper Android Application

**MommyPix** is a modern, high-performance native Android application built with **Kotlin**, **Jetpack Compose**, and **Material 3**. It fetches random high-resolution wallpapers from the Rule34 API, previews them with full zoom/pan gestures, stores favorites and download history locally, and automatically updates wallpapers at custom intervals using Android exact alarms and WorkManager.

---

## Features

### 1. Wallpaper Management
- **Target Selection**:
  - Home screen only (`FLAG_SYSTEM`)
  - Lock screen only (`FLAG_LOCK`)
  - Both screens (`FLAG_SYSTEM or FLAG_LOCK`)
- **Dual Screen Modes**:
  - **Same Image**: One random wallpaper applied to both Home and Lock screens.
  - **Different Images**: Two independent random wallpapers applied separately to Home and Lock screens.
- **Individual Controls**:
  - Individual **Shuffle Home** & **Apply Home** buttons.
  - Individual **Shuffle Lock** & **Apply Lock** buttons.
  - One-tap **Apply Both** and **Shuffle Both** options.
  - Concurrency protected with coroutine `Mutex` to prevent wallpaper collisions.

### 2. High-Precision Background Updates & OS Keep-Alive
- **Intervals Down to 1 Minute**: Powered by Android `AlarmManager` with `setExactAndAllowWhileIdle()`, allowing fast 1-minute, 2-minute, 5-minute, or custom minute intervals that fire accurately even in Android Doze mode.
- **Background Persistence**: Runs reliably even when the app is swiped away from recent apps or closed.
- **Reboot Resilience**: Automatically resumes wallpaper schedules on device restart via `BootReceiver` (`BOOT_COMPLETED`).
- **Battery Exemption**: Includes a one-click button in Settings to disable Android Battery Optimization so OEM task-killers (Samsung, Xiaomi, OnePlus) won't terminate background alarms.

### 3. Rule34 API Integration & Content Moderation
- **Endpoint**: `https://api.rule34.xxx/index.php` using `s=post`, `q=index`, `page=dapi`, `json=1`, `tags=sort:random`.
- **Hardware-Backed Security**: User ID and API Key are encrypted with Android Keystore (`AES256_GCM`) via `EncryptedSharedPreferences`. Credentials are never hardcoded and are automatically redacted from OkHttp logs.
- **Strict Content Safety Filter**: Enforces automatic negative search exclusions and post-fetch token inspection to reject any depictions of minors or illegal material.
- **Format Verification**: Downloads original high-resolution images (`.jpg`, `.jpeg`, `.png`, `.webp`) and automatically filters out video files (`.mp4`, `.webm`).

### 4. Interactive Preview Screen
- Full-screen high-resolution preview with smooth pinch-to-zoom (1x to 5x) and pan gestures.
- Double-tap gesture to quickly toggle zoom level.
- Metadata modal sheet showing post ID, exact pixel resolution, color-coded rating badge, and expandable tag chips.
- Confirmation dialogs before setting wallpapers.

### 5. Download History & Persistent Favorites
- **Favorites**: Stored in a local SQLite database across app restarts with quick wallpaper application.
- **Download History**: Tracks saved wallpapers with thumbnails, download timestamps, dimensions, and total disk storage in MB.
- **Gallery Export**: Automatically exports saved wallpapers into the device's public **`Pictures/MommyPix`** gallery album via `MediaStore`.

---

## Architecture & Package Structure

The codebase is organized according to clean **MVVM + Repository** principles:

```
com.project.mommypix/
├── MommyPixApp.kt                        # Application class, DI container, Coil cache setup
├── MainActivity.kt                       # Single ComponentActivity & Jetpack Navigation Host
│
├── data/
│   ├── api/
│   │   ├── ApiClient.kt                  # OkHttp & Retrofit client with credential redaction
│   │   ├── PostDto.kt                    # DTO with domain model mapping
│   │   └── Rule34Api.kt                  # Retrofit REST interface
│   ├── downloader/
│   │   └── ImageDownloader.kt            # Validates format/size & exports to Pictures/MommyPix
│   ├── local/
│   │   ├── AppDatabase.kt                # SQLiteOpenHelper for Favorites & Download History
│   │   └── Entities.kt                   # Database data entities
│   ├── preferences/
│   │   ├── AppPreferences.kt             # Data classes & enums for settings
│   │   └── UserPreferencesRepository.kt  # Jetpack DataStore persistent settings
│   └── repository/
│       └── WallpaperRepository.kt        # Repository coordinating API, cache, deduplication
│
├── domain/
│   └── model/
│       └── Post.kt                       # Post domain entity with media checks
│
├── security/
│   ├── ContentFilter.kt                  # Content moderation filter
│   └── CredentialStorage.kt              # Android Keystore-backed AES256-GCM secure storage
│
├── wallpaper/
│   └── WallpaperManagerHelper.kt         # Android WallpaperManager API with mutex lock
│
├── worker/
│   ├── WallpaperAlarmReceiver.kt         # BroadcastReceiver for exact alarms (1m, 5m, etc.)
│   ├── BootReceiver.kt                   # Resumes wallpaper updates after device reboot
│   ├── WallpaperWorker.kt                # CoroutineWorker for auxiliary WorkManager tasks
│   ├── WallpaperScheduler.kt             # Alarm & WorkManager scheduler
│   └── NotificationHelper.kt             # Notification channel & wallpaper update alerts
│
└── ui/
    ├── navigation/
    │   └── Screen.kt                     # Compose Navigation routes
    ├── components/
    │   ├── AppTopBar.kt                  # Top app bar with action buttons
    │   ├── WallpaperCard.kt              # Preview card with metadata & favorite heart
    │   ├── RatingBadge.kt                # Safe, Questionable, Explicit badge
    │   └── TagsFlowRow.kt                # Expandable Material 3 flow chips for tags
    ├── screens/
    │   ├── home/                         # Home screen with single/dual previews & action buttons
    │   ├── preview/                      # Zoomable full-screen preview & metadata drawer
    │   ├── history/                      # Download history grid & storage management
    │   ├── favorites/                    # Persistent favorites gallery
    │   └── settings/                     # API credentials, intervals, targets, keep-alive, theme
    └── theme/
        ├── Color.kt                      # Vibrant modern theme palette
        ├── Theme.kt                      # Material 3 dynamic color & theme switcher
        └── Type.kt
```

---

## Getting Started in Android Studio

1. Open **Android Studio** and choose **Open an Existing Project**.
2. Select the `MommyPix` directory.
3. Wait for Gradle sync to complete.
4. Click **Run** (`Shift + F10`) to launch the app on your connected device or emulator.

---

## Configuring Rule34 API Credentials

1. Open **MommyPix**.
2. Tap the **Rule34 API Credentials Required** card on the home screen or tap the **Settings** icon.
3. Enter your:
   - **User ID**: Your Rule34 account ID.
   - **API Key**: Your Rule34 API key.
4. Tap **Save**, then tap **Test Connection** to verify your authentication.
5. In **Keep-Alive & Battery Exemption**, tap **Disable Battery Optimization** so Android will not throttle your background wallpaper updates.
6. Return to the Home screen and enjoy!

