package com.project.mommypix.data.repository

import android.util.Log
import com.project.mommypix.data.api.ApiClient
import com.project.mommypix.data.api.PostDto
import com.project.mommypix.data.api.Rule34Api
import com.project.mommypix.data.downloader.ImageDownloader
import com.project.mommypix.data.downloader.PornPicsScraper
import com.project.mommypix.data.local.AppDatabase
import com.project.mommypix.data.local.DownloadHistoryItem
import com.project.mommypix.data.local.FavoriteEntity
import com.project.mommypix.data.preferences.ImageSource
import com.project.mommypix.data.preferences.RatingFilter
import com.project.mommypix.data.preferences.UserPreferences
import com.project.mommypix.data.preferences.UserPreferencesRepository
import com.project.mommypix.data.preferences.WallpaperTarget
import com.project.mommypix.data.reddit.RedditScraper
import com.project.mommypix.data.reddit.SubredditPacks
import com.project.mommypix.domain.model.Post
import com.project.mommypix.security.ContentFilter
import com.project.mommypix.security.CredentialStorage
import com.project.mommypix.wallpaper.WallpaperManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File

class WallpaperRepository(
    private val api: Rule34Api = ApiClient.rule34Api,
    private val credentialStorage: CredentialStorage,
    private val database: AppDatabase,
    private val downloader: ImageDownloader,
    private val wallpaperManagerHelper: WallpaperManagerHelper,
    private val preferencesRepository: UserPreferencesRepository? = null,
    private val pornPicsScraper: PornPicsScraper = PornPicsScraper(),
    private val redditScraper: RedditScraper = RedditScraper()
) {

    // In-memory cache of recent post IDs to avoid immediate duplicates
    private val seenPostIds = mutableSetOf<Long>()

    // In-memory cache of active posts so clicking a preview immediately finds it
    private val recentPostsCache = LinkedHashMap<Long, Post>(100, 0.75f, true)

    // Buffer of fetched valid posts to provide smooth transitions
    private val postBuffer = ArrayDeque<Post>()

    fun getCachedPost(postId: Long): Post? = recentPostsCache[postId]

    fun cachePost(post: Post) {
        recentPostsCache[post.id] = post
    }

    fun clearBuffer() {
        postBuffer.clear()
        seenPostIds.clear()
    }

    /**
     * Checks if image fetching is possible.
     * Always returns true because authenticated Rule34, PornPics, and Yande.re are ready out-of-the-box.
     */
    fun hasCredentials(): Boolean = true

    fun hasCustomCredentials(): Boolean = credentialStorage.hasCustomCredentials()

    /**
     * Tests API connectivity.
     */
    suspend fun testApiConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val userPrefs = preferencesRepository?.userPreferencesFlow?.first()
            val useCustom = userPrefs?.useCustomRule34Credentials ?: false
            val userId = credentialStorage.getEffectiveUserId(useCustom)
            val apiKey = credentialStorage.getEffectiveApiKey(useCustom)

            // Test Rule34 access with user's verified credentials
            val r34Response = api.getGelbooruPosts(
                url = "https://api.rule34.xxx/index.php",
                limit = 1,
                tags = ContentFilter.buildSafeTagQuery("order:random"),
                apiKey = apiKey,
                userId = userId
            )
            val r34Body = r34Response.body()?.string()?.trim().orEmpty()
            val r34Ok = r34Response.isSuccessful && r34Body.startsWith("[")

            // Test Yande.re (Zero-Auth Open Moebooru source)
            val yandereResponse = api.getMoebooruPosts(
                url = "https://yande.re/post.json",
                limit = 1,
                tags = ContentFilter.buildSafeTagQuery("order:random")
            )
            val yandereBody = yandereResponse.body()?.string()?.trim().orEmpty()
            val yandereOk = yandereResponse.isSuccessful && yandereBody.startsWith("[")

            // Test Reddit zero-auth public feed connectivity
            val testReddit = try {
                redditScraper.fetchRandomPosts(
                    subreddits = userPrefs?.allActiveSubreddits ?: setOf("gonewild", "Bondage"),
                    blockedTags = emptySet(),
                    ratingFilter = RatingFilter.BOTH
                )
            } catch (e: Exception) {
                emptyList()
            }
            val redditOk = testReddit.isNotEmpty()

            val testRealLifeFallback = if (!redditOk) {
                try {
                    pornPicsScraper.fetchPostsForSubreddits(
                        subreddits = userPrefs?.allActiveSubreddits ?: setOf("gonewild", "Bondage"),
                        blockedTags = emptySet()
                    )
                } catch (e: Exception) {
                    emptyList()
                }
            } else emptyList()
            val realLifeOk = redditOk || testRealLifeFallback.isNotEmpty()

            if (r34Ok || yandereOk || realLifeOk) {
                val details = buildString {
                    append("Connection successful! ")
                    if (r34Ok) append("Rule34 (User #$userId) OK. ") else append("Rule34 returned non-array (${r34Body.take(60)}). ")
                    if (yandereOk) append("Yande.re OK. ")
                    if (redditOk) {
                        append("Reddit (${testReddit.size} sample wallpapers found) OK.")
                    } else if (testRealLifeFallback.isNotEmpty()) {
                        append("Subreddits: ISP blocked direct Reddit; Real-Life CDN fallback OK.")
                    } else {
                        append("Subreddits: 0 posts.")
                    }
                }
                Result.success(details)
            } else {
                Result.failure(Exception("Could not connect to image sources: Rule34: HTTP ${r34Response.code()} ($r34Body)"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Test connection failed", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches a random valid post across enabled sources (Booru boards, PornPics, and Subreddits).
     * Respects user's Included tags and Blocked tags (blacklist),
     * and strictly rejects prohibited illegal content and unsupported formats.
     */
    suspend fun fetchRandomPost(forceRefresh: Boolean = false): Result<Post> = withContext(Dispatchers.IO) {
        try {
            if (forceRefresh) {
                postBuffer.clear()
            } else if (postBuffer.isNotEmpty()) {
                postBuffer.shuffle()
                val cached = postBuffer.removeFirst()
                val isFav = database.isFavorite(cached.id)
                return@withContext Result.success(cached.copy(isFavorite = isFav))
            }

            // Read user preferences
            val userPrefs = preferencesRepository?.userPreferencesFlow?.first()
            val includedTags = userPrefs?.includedTags.orEmpty()
            val blockedTags = userPrefs?.blockedTags.orEmpty()
            val ratingFilter = userPrefs?.ratingFilter ?: RatingFilter.BOTH
            val isBooruEnabled = userPrefs?.isBooruEnabled ?: true
            val isRedditEnabled = userPrefs?.isRedditEnabled ?: true

            val effectiveBooruEnabled = if (!isBooruEnabled && !isRedditEnabled) true else isBooruEnabled
            val effectiveRedditEnabled = if (!isBooruEnabled && !isRedditEnabled) true else isRedditEnabled

            val enabledSourceIds = userPrefs?.enabledSources ?: ImageSource.DEFAULT_ENABLED
            val candidateSources = enabledSourceIds.mapNotNull { ImageSource.fromId(it) }
                .ifEmpty { ImageSource.entries }
                .shuffled()

            val blockedLower = blockedTags.map { it.lowercase().replace(' ', '_') }.toSet()
            val includedLower = includedTags.map { it.lowercase().replace(' ', '_') }.toSet()

            var lastError: Exception? = null

            // Determine fetch sequence: Interleaved (both), Reddit only, or Booru only
            val tryRedditFirst = if (effectiveBooruEnabled && effectiveRedditEnabled) {
                kotlin.random.Random.nextBoolean()
            } else {
                effectiveRedditEnabled
            }

            var retrievedPosts = emptyList<Post>()

            if (tryRedditFirst) {
                if (effectiveRedditEnabled) {
                    try {
                        retrievedPosts = fetchFromReddit(userPrefs, blockedLower, ratingFilter)
                    } catch (e: Exception) {
                        Log.w(TAG, "Reddit fetch failed, attempting Booru fallback", e)
                        lastError = e
                    }
                }
                if (retrievedPosts.isEmpty() && effectiveBooruEnabled) {
                    try {
                        retrievedPosts = fetchFromBooruSources(candidateSources, userPrefs, blockedLower, includedLower, ratingFilter)
                    } catch (e: Exception) {
                        Log.w(TAG, "Booru fetch failed", e)
                        lastError = e
                    }
                }
            } else {
                if (effectiveBooruEnabled) {
                    try {
                        retrievedPosts = fetchFromBooruSources(candidateSources, userPrefs, blockedLower, includedLower, ratingFilter)
                    } catch (e: Exception) {
                        Log.w(TAG, "Booru fetch failed, attempting Reddit fallback", e)
                        lastError = e
                    }
                }
                if (retrievedPosts.isEmpty() && effectiveRedditEnabled) {
                    try {
                        retrievedPosts = fetchFromReddit(userPrefs, blockedLower, ratingFilter)
                    } catch (e: Exception) {
                        Log.w(TAG, "Reddit fetch failed", e)
                        lastError = e
                    }
                }
            }

            if (retrievedPosts.isNotEmpty()) {
                val shuffledPosts = retrievedPosts.shuffled()
                for (p in shuffledPosts) {
                    recentPostsCache[p.id] = p
                    if (!seenPostIds.contains(p.id)) {
                        postBuffer.add(p)
                    }
                }

                if (postBuffer.isEmpty()) {
                    postBuffer.addAll(shuffledPosts)
                }

                postBuffer.shuffle()
                val chosen = postBuffer.removeFirst()
                recentPostsCache[chosen.id] = chosen
                seenPostIds.add(chosen.id)
                if (seenPostIds.size > 300) {
                    seenPostIds.clear()
                }

                val isFav = database.isFavorite(chosen.id)
                return@withContext Result.success(chosen.copy(isFavorite = isFav))
            }

            // Fallback error if all enabled sources failed or returned empty
            Result.failure(
                lastError ?: Exception("No wallpapers found matching your filters across enabled sources.")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching random post", e)
            Result.failure(e)
        }
    }

    private suspend fun fetchFromReddit(
        userPrefs: UserPreferences?,
        blockedLower: Set<String>,
        ratingFilter: RatingFilter
    ): List<Post> {
        val targetSubs = userPrefs?.allActiveSubreddits ?: SubredditPacks.DEFAULT_ENABLED_SUBREDDITS
        val redditPosts = redditScraper.fetchRandomPosts(
            subreddits = targetSubs,
            blockedTags = userPrefs?.blockedTags.orEmpty(),
            ratingFilter = ratingFilter
        )
        if (redditPosts.isNotEmpty()) {
            return redditPosts
        }

        // Region / ISP Censorship Fallback:
        // Indian ISPs (Jio, Airtel, Vi) and Reddit regional filters frequently block/geoblock
        // unauthenticated Reddit NSFW endpoints. Automatically fallback to Real-Life photo CDN (PornPics)
        // using the exact same subreddit categories (BDSM, Feet, Sissy, Amateur, etc.)
        Log.i(TAG, "Reddit returned 0 posts (likely regional ISP block); seamlessly falling back to Real-Life photo CDN for chosen subreddits")
        return pornPicsScraper.fetchPostsForSubreddits(
            subreddits = targetSubs,
            blockedTags = userPrefs?.blockedTags.orEmpty()
        )
    }

    private suspend fun fetchFromBooruSources(
        candidateSources: List<ImageSource>,
        userPrefs: UserPreferences?,
        blockedLower: Set<String>,
        includedLower: Set<String>,
        ratingFilter: RatingFilter
    ): List<Post> {
        val includedTags = userPrefs?.includedTags.orEmpty()
        val blockedTags = userPrefs?.blockedTags.orEmpty()
        val useCustomRule34 = userPrefs?.useCustomRule34Credentials ?: false

        for (source in candidateSources) {
            try {
                if (source == ImageSource.PORNPICS) {
                    if (ratingFilter == RatingFilter.SAFE_ONLY) continue
                    val ppPosts = pornPicsScraper.fetchRandomPosts(includedTags, blockedTags)
                    if (ppPosts.isNotEmpty()) {
                        return ppPosts
                    }
                    continue
                }

                val dtoList = fetchFromSource(source, includedTags, blockedTags, useCustomRule34, ratingFilter)
                if (dtoList.isEmpty()) continue

                val validPosts = dtoList.mapNotNull { it.toDomain(source) }
                    .filter { post ->
                        val postTagsLower = post.tags.map { it.lowercase() }.toSet()
                        val isLegal = ContentFilter.isPostPermitted(post.tags.joinToString(" "))
                        val isImage = post.isSupportedImage
                        val containsBlocked = postTagsLower.any { blockedLower.contains(it) }
                        val matchesIncluded = includedLower.isEmpty() || postTagsLower.any { includedLower.contains(it) }
                        val r = post.rating.lowercase()
                        val matchesRating = when (ratingFilter) {
                            RatingFilter.BOTH -> true
                            RatingFilter.SAFE_ONLY -> r == "s" || r == "safe" || r == "general" || r == "g"
                            RatingFilter.NSFW_ONLY -> r == "q" || r == "questionable" || r == "e" || r == "explicit"
                        }
                        isLegal && isImage && !containsBlocked && matchesIncluded && matchesRating
                    }

                if (validPosts.isNotEmpty()) {
                    return validPosts
                }
            } catch (e: Exception) {
                Log.w(TAG, "Source ${source.displayName} query failed, trying next source", e)
            }
        }
        return emptyList()
    }

    /**
     * Parses raw JSON responses defensively to prevent Gson IllegalStateExceptions
     * (e.g., 'Expected BEGIN_ARRAY but was STRING') when servers return error messages.
     */
    private fun parsePostListSafely(rawBody: String?, source: ImageSource): List<PostDto> {
        val trimmed = rawBody?.trim().orEmpty()
        if (trimmed.isBlank()) {
            Log.w(TAG, "[$source] Received empty response from server")
            return emptyList()
        }

        if (!trimmed.startsWith("[")) {
            Log.w(TAG, "[$source] Received non-array response from server: ${trimmed.take(200)}")
            return emptyList()
        }

        return try {
            val listType = object : TypeToken<List<PostDto>>() {}.type
            Gson().fromJson<List<PostDto>>(trimmed, listType).orEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "[$source] JSON parsing failed: ${e.message}", e)
            emptyList()
        }
    }

    private suspend fun fetchFromSource(
        source: ImageSource,
        includedTags: Set<String>,
        blockedTags: Set<String>,
        useCustomRule34: Boolean,
        ratingFilter: RatingFilter
    ): List<PostDto> {
        val queryParts = mutableListOf<String>()

        if (source == ImageSource.PORNPICS) {
            return emptyList()
        }

        when (source) {
            ImageSource.RULE34 -> {
                queryParts.add("order:random")
                when (ratingFilter) {
                    RatingFilter.SAFE_ONLY -> queryParts.add("rating:safe")
                    RatingFilter.NSFW_ONLY -> queryParts.add("-rating:safe")
                    RatingFilter.BOTH -> { /* no rating filter token */ }
                }
            }
            ImageSource.YANDE_RE -> {
                queryParts.add("order:random")
                when (ratingFilter) {
                    RatingFilter.SAFE_ONLY -> queryParts.add("rating:s")
                    RatingFilter.NSFW_ONLY -> queryParts.add("-rating:s")
                    RatingFilter.BOTH -> { /* no rating filter token */ }
                }
            }
            ImageSource.PORNPICS -> {
                // PornPics content is retrieved via PornPicsScraper; no Booru query needed
            }
        }

        includedTags.forEach { tag ->
            val clean = tag.trim().lowercase().replace(' ', '_')
            if (clean.isNotBlank()) queryParts.add(clean)
        }

        blockedTags.forEach { tag ->
            val clean = tag.trim().lowercase().replace(' ', '_')
            if (clean.isNotBlank()) queryParts.add("-$clean")
        }

        val safeQuery = ContentFilter.buildSafeTagQuery(queryParts.joinToString(" "))

        val response: Response<ResponseBody> = when (source) {
            ImageSource.RULE34 -> {
                val userId = credentialStorage.getEffectiveUserId(useCustomRule34)
                val apiKey = credentialStorage.getEffectiveApiKey(useCustomRule34)
                api.getGelbooruPosts(
                    url = "https://api.rule34.xxx/index.php",
                    limit = 10,
                    tags = safeQuery,
                    apiKey = apiKey,
                    userId = userId
                )
            }
            ImageSource.YANDE_RE -> {
                api.getMoebooruPosts(
                    url = "https://yande.re/post.json",
                    limit = 10,
                    tags = safeQuery
                )
            }
            ImageSource.PORNPICS -> return emptyList()
        }

        if (!response.isSuccessful) {
            Log.w(TAG, "[$source] HTTP ${response.code()} ${response.message()}")
            return emptyList()
        }

        val bodyString = try {
            response.body()?.string()
        } catch (e: Exception) {
            Log.e(TAG, "[$source] Error reading response body", e)
            null
        }

        return parsePostListSafely(bodyString, source)
    }

    /**
     * Downloads post image with multi-tier fallback:
     * 1. Primary fileUrl
     * 2. sampleUrl (if present and different)
     * 3. previewUrl (what user sees rendered on screen)
     */
    private suspend fun downloadPostImageWithFallback(
        post: Post,
        onProgress: ((Int) -> Unit)? = null,
        exportToGallery: Boolean = false
    ): Result<File> {
        // 1. Attempt primary fileUrl
        val primaryResult = downloader.downloadImage(post.fileUrl, post.id, onProgress, exportToGallery)
        if (primaryResult.isSuccess) {
            return primaryResult
        }

        Log.w(TAG, "Primary fileUrl download failed for #${post.id} (${post.fileUrl}): ${primaryResult.exceptionOrNull()?.message}. Attempting fallback...")

        // 2. Attempt sampleUrl if present and different
        if (!post.sampleUrl.isNullOrBlank() && post.sampleUrl != post.fileUrl) {
            val sampleResult = downloader.downloadImage(post.sampleUrl, post.id, onProgress, exportToGallery)
            if (sampleResult.isSuccess) {
                return sampleResult
            }
        }

        // 3. Fallback to previewUrl (what user sees on screen)
        if (post.previewUrl.isNotBlank() && post.previewUrl != post.fileUrl) {
            val previewResult = downloader.downloadImage(post.previewUrl, post.id, onProgress, exportToGallery)
            if (previewResult.isSuccess) {
                return previewResult
            }
        }

        return primaryResult
    }

    /**
     * Downloads and applies wallpaper according to the user's target settings.
     */
    suspend fun applyPostAsWallpaper(
        post: Post,
        target: WallpaperTarget
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userPrefs = preferencesRepository?.userPreferencesFlow?.first()
            val saveToGallery = userPrefs?.saveToGallery ?: false
            val downloadResult = downloadPostImageWithFallback(post, exportToGallery = saveToGallery)
            val file = downloadResult.getOrElse { return@withContext Result.failure(it) }

            val applyResult = wallpaperManagerHelper.applyWallpaper(file, target)
            if (applyResult.isFailure) {
                return@withContext applyResult
            }

            database.addDownloadHistory(
                DownloadHistoryItem(
                    id = post.id,
                    localPath = file.absolutePath,
                    fileUrl = post.fileUrl,
                    previewUrl = post.previewUrl,
                    width = post.width,
                    height = post.height,
                    rating = post.rating,
                    tags = post.tags.joinToString(" "),
                    downloadedAt = System.currentTimeMillis(),
                    fileSizeBytes = file.length()
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting wallpaper for post ${post.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Handles setting dual different wallpapers for Home and Lock screens.
     */
    suspend fun applyDualDifferentWallpapers(
        homePost: Post,
        lockPost: Post
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userPrefs = preferencesRepository?.userPreferencesFlow?.first()
            val saveToGallery = userPrefs?.saveToGallery ?: false
            val homeDownload = downloadPostImageWithFallback(homePost, exportToGallery = saveToGallery)
            val homeFile = homeDownload.getOrElse { return@withContext Result.failure(it) }

            val lockDownload = downloadPostImageWithFallback(lockPost, exportToGallery = saveToGallery)
            val lockFile = lockDownload.getOrElse { return@withContext Result.failure(it) }

            val applyResult = wallpaperManagerHelper.applyDualWallpapers(homeFile, lockFile)
            if (applyResult.isFailure) {
                return@withContext applyResult
            }

            database.addDownloadHistory(
                DownloadHistoryItem(
                    id = homePost.id,
                    localPath = homeFile.absolutePath,
                    fileUrl = homePost.fileUrl,
                    previewUrl = homePost.previewUrl,
                    width = homePost.width,
                    height = homePost.height,
                    rating = homePost.rating,
                    tags = homePost.tags.joinToString(" "),
                    downloadedAt = System.currentTimeMillis(),
                    fileSizeBytes = homeFile.length()
                )
            )

            database.addDownloadHistory(
                DownloadHistoryItem(
                    id = lockPost.id,
                    localPath = lockFile.absolutePath,
                    fileUrl = lockPost.fileUrl,
                    previewUrl = lockPost.previewUrl,
                    width = lockPost.width,
                    height = lockPost.height,
                    rating = lockPost.rating,
                    tags = lockPost.tags.joinToString(" "),
                    downloadedAt = System.currentTimeMillis(),
                    fileSizeBytes = lockFile.length()
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying dual wallpapers", e)
            Result.failure(e)
        }
    }

    /**
     * Downloads an image and saves it to history.
     */
    suspend fun downloadAndSaveImage(
        post: Post,
        onProgress: ((Int) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val downloadResult = downloadPostImageWithFallback(post, onProgress, exportToGallery = true)
        val file = downloadResult.getOrElse { return@withContext Result.failure(it) }

        database.addDownloadHistory(
            DownloadHistoryItem(
                id = post.id,
                localPath = file.absolutePath,
                fileUrl = post.fileUrl,
                previewUrl = post.previewUrl,
                width = post.width,
                height = post.height,
                rating = post.rating,
                tags = post.tags.joinToString(" "),
                downloadedAt = System.currentTimeMillis(),
                fileSizeBytes = file.length()
            )
        )

        Result.success(file)
    }

    /**
     * Applies a downloaded historical file directly as wallpaper.
     */
    suspend fun applyHistoricalWallpaper(
        item: DownloadHistoryItem,
        target: WallpaperTarget
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val file = File(item.localPath)
        if (!file.exists()) {
            return@withContext Result.failure(Exception("File no longer exists on disk"))
        }
        wallpaperManagerHelper.applyWallpaper(file, target)
    }

    // --- Favorites ---

    suspend fun toggleFavorite(post: Post): Boolean = withContext(Dispatchers.IO) {
        val isFav = database.isFavorite(post.id)
        if (isFav) {
            database.removeFavorite(post.id)
            false
        } else {
            database.addFavorite(
                FavoriteEntity(
                    id = post.id,
                    fileUrl = post.fileUrl,
                    previewUrl = post.previewUrl,
                    sampleUrl = post.sampleUrl,
                    width = post.width,
                    height = post.height,
                    rating = post.rating,
                    tags = post.tags.joinToString(" ")
                )
            )
            true
        }
    }

    fun observeFavorites(): Flow<List<Post>> {
        return database.favoritesFlow.map { list -> list.map { it.toDomain() } }
    }

    fun observeDownloadHistory(): Flow<List<DownloadHistoryItem>> {
        return database.historyFlow
    }

    suspend fun deleteDownloadHistory(id: Long): Boolean {
        return database.deleteDownloadHistory(id)
    }

    suspend fun clearDownloadHistory() {
        database.clearDownloadHistory()
    }

    suspend fun getStorageUsageBytes(): Long {
        return database.getStorageUsageBytes()
    }

    suspend fun clearCache(): Long {
        return downloader.clearAllFiles()
    }

    companion object {
        private const val TAG = "WallpaperRepository"
    }
}
