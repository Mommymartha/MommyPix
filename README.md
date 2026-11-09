# MommyPix — Random Wallpaper Android Application

**MommyPix** is a modern, high-performance native Android application built with **Kotlin**, **Jetpack Compose**, and **Material 3**. It aggregates random high-resolution wallpapers across multiple booru providers (**Safebooru**, **Konachan**, **Yande.re**, and **Rule34.xxx**), works **out-of-the-box with zero effort**, previews images with smooth zoom/pan gestures, stores favorites and download history locally, and automatically updates wallpapers at custom intervals down to 1 minute using Android exact alarms and WorkManager.

---

## Key Features

### 1. Multi-Source Wallpaper Engine (Zero-Effort Out-of-the-Box)
- **Zero-Effort First Launch**: New users don't have to enter any API keys or create accounts. Open sources work immediately on launch!
- **Supported Providers (7 Major Booru Boards)**:
  - **Safebooru** (`safebooru.org`): Clean anime & manga artwork (**Zero Auth**).
  - **Konachan** (`konachan.net`): High-resolution phone & desktop wallpapers (**Zero Auth**).
  - **Yande.re** (`yande.re`): Ultra high-resolution scans & illustrations (**Zero Auth**).
  - **Danbooru** (`danbooru.donmai.us`): Massive anime & game art archive (**Zero Auth**).
  - **Gelbooru** (`gelbooru.com`): Popular booru imageboard archive (**Zero Auth**).
  - **TBIB** (`tbib.org`): "The Big Idols Booru" anime idol wallpapers (**Zero Auth**).
  - **Rule34.xxx** (`api.rule34.xxx`): Built-in public key by default or private API credentials for advanced users.
- **Provider Toggles**: In Settings, check or uncheck any provider to customize your wallpaper pool.
- **Automatic Failover**: If any site is experiencing network lag or downtime, MommyPix seamlessly falls back to your other enabled sites.
- **Source Badges**: Each wallpaper displays a badge showing its origin site (`[Safebooru]`, `[Danbooru]`, `[Gelbooru]`, `[Konachan]`, etc.).

### 2. Content Rating Filter (Safe vs NSFW vs Both)
- **1-Tap Quick Switcher on Main Screen**:
  - `🎲 Both`: Shuffles safe and NSFW wallpapers together at random.
  - `🛡️ Safe Only`: Strict all-ages filter (`general`, `safe`, `g`).
  - `🔞 NSFW Only`: Only adult, questionable, and explicit wallpapers (`questionable`, `explicit`, `q`, `e`).
- **Dual-Layer Enforcement**: Rating tags are passed directly into API queries AND verified locally on every downloaded post to guarantee 100% adherence to your chosen rating mode.

### 2. Wallpaper Target & Individual Screen Controls
- **Target Options**:
  - Home screen only (`FLAG_SYSTEM`)
  - Lock screen only (`FLAG_LOCK`)
  - Both screens (`FLAG_SYSTEM or FLAG_LOCK`)
- **Dual Screen Modes**:
  - **Same Image**: One random wallpaper applied to both Home and Lock screens.
  - **Different Images**: Two independent random wallpapers applied separately to Home and Lock screens.
- **Non-Congested Controls**:
  - Dedicated **Shuffle Home** & **Apply Home** buttons (stacked vertically).
  - Dedicated **Shuffle Lock** & **Apply Lock** buttons (stacked vertically).
  - One-tap **Apply Both** button.
  - Concurrency protected with coroutine `Mutex` to prevent wallpaper collisions.

### 3. High-Precision Background Updates & OS Keep-Alive
- **Intervals Down to 1 Minute**: Powered by Android `AlarmManager` with `setExactAndAllowWhileIdle()`, allowing 1-minute, 5-minute, or custom minute intervals that fire accurately even during Android Doze mode.
- **Background Persistence**: Runs reliably even when the app is swiped away from recent apps or closed.
- **Reboot Resilience**: Automatically resumes wallpaper schedules on device restart via `BootReceiver` (`BOOT_COMPLETED`).
- **Battery Exemption**: Includes a one-click button in Settings to request Android Battery Optimization exemption (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) so OEM task-killers (Samsung, Xiaomi, OnePlus) won't freeze background alarms.
- **Turn Off Anytime**: Quick toggle and dedicated "Turn Off Automatic Updates" button.

### 4. Tag Filtering & Blacklist (Content Customization)
- **Blocked Tags (Blacklist)**: Exclude tags you dislike (e.g. `furry`, `feet`, `3d`, `monster`). Negative tags are automatically appended to API queries and verified client-side.
- **Quick Block Chips**: One-tap suggestion chips for frequently blocked categories.
- **Included Tags (Whitelist)**: Restrict wallpapers to specific themes (e.g. `1girl`, `cyberpunk`, `landscape`).
- **Instant Cache Flush**: Tag changes immediately clear previous random buffers.

### 5. Interactive Preview Screen
- Full-screen high-resolution preview with smooth pinch-to-zoom (1x to 5x) and pan gestures.
- Double-tap gesture to quickly toggle zoom level.
- Metadata modal sheet showing post ID, source provider, exact resolution, color-coded rating badge, and expandable tag chips.
- Confirmation dialogs before setting wallpapers.

### 6. Download History & Persistent Favorites
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
│   │   ├── PostDto.kt                    # DTO with multi-engine URL resolution
│   │   └── Rule34Api.kt                  # Multi-engine Retrofit REST interface (Gelbooru + Moebooru)
│   ├── downloader/
│   │   └── ImageDownloader.kt            # Validates format/size & exports to Pictures/MommyPix
│   ├── local/
│   │   ├── AppDatabase.kt                # SQLiteOpenHelper for Favorites & Download History
│   │   └── Entities.kt                   # Database data entities
│   ├── preferences/
│   │   ├── AppPreferences.kt             # Data classes, ImageSource enum & settings
│   │   └── UserPreferencesRepository.kt  # Jetpack DataStore persistent settings
│   └── repository/
│       └── WallpaperRepository.kt        # Multi-source repository coordinating APIs, cache & fallback
│
├── domain/
│   └── model/
│       └── Post.kt                       # Post domain entity with sourceName & media checks
│
├── security/
│   ├── ContentFilter.kt                  # Content moderation filter
│   └── CredentialStorage.kt              # Keystore-backed AES256-GCM secure storage & public keys
│
├── wallpaper/
│   └── WallpaperManagerHelper.kt         # Android WallpaperManager API with mutex lock
│
├── worker/
│   ├── WallpaperAlarmReceiver.kt         # BroadcastReceiver for exact alarms with WakeLock
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
    │   ├── WallpaperCard.kt              # Preview card with source badge & favorite heart
    │   ├── RatingBadge.kt                # Safe, Questionable, Explicit badge
    │   └── TagsFlowRow.kt                # Expandable Material 3 flow chips for tags
    ├── screens/
    │   ├── home/                         # Home screen with single/dual previews & separated action buttons
    │   ├── preview/                      # Zoomable full-screen preview & metadata drawer with source info
    │   ├── history/                      # Download history grid & storage management
    │   ├── favorites/                    # Persistent favorites gallery
    │   └── settings/                     # Multi-source toggles, Rule34 public/private key, intervals, tags
    └── theme/
        ├── Color.kt                      # Vibrant modern theme palette
        ├── Theme.kt                      # Material 3 dynamic color & theme switcher
        └── Type.kt
```

---

## Running in Android Studio

1. Open **Android Studio** and choose **Open an Existing Project**.
2. Select the `MommyPix` directory (`project/MommyPix`).
3. Wait for Gradle sync to complete.
4. Click **Run** (`Shift + F10`) or select your connected physical phone or emulator.
5. The app launches and will immediately display random wallpapers with **zero setup required**!
