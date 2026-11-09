package com.project.mommypix.data.reddit

import android.util.Log
import com.project.mommypix.data.api.ApiClient
import com.project.mommypix.data.preferences.RatingFilter
import com.project.mommypix.domain.model.Post
import com.project.mommypix.security.ContentFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.random.Random

class RedditScraper(
    private val client: OkHttpClient = ApiClient.okHttpClient
) {

    // Fast-failing client with 3-4s timeout so blocked connections (e.g. Indian ISPs / DoT blocks)
    // never cause an infinite loading wheel on user's device.
    private val fastRedditClient: OkHttpClient = client.newBuilder()
        .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /**
     * Fetches random image wallpapers from the user's enabled subreddits.
     * Uses public Reddit feeds with adult verification cookies and fallback mirrors.
     * Zero-auth: No user account or Reddit app installation required.
     * Fails fast if regional ISP blocks or geofilters are active.
     */
    suspend fun fetchRandomPosts(
        subreddits: Set<String>,
        blockedTags: Set<String>,
        ratingFilter: RatingFilter
    ): List<Post> = withContext(Dispatchers.IO) {
        val targetSubs = subreddits.filter { it.isNotBlank() }
            .ifEmpty { SubredditPacks.DEFAULT_ENABLED_SUBREDDITS.toList() }

        val chosenSub = targetSubs.random().trim().removePrefix("r/").removePrefix("/")
        val blockedLower = blockedTags.map { it.lowercase().replace(' ', '_') }.toSet()

        // Fast attempt 1: Query selected subreddit
        val posts = fetchSubredditPosts(chosenSub, blockedLower, ratingFilter)
        if (posts.isNotEmpty()) {
            return@withContext posts.distinctBy { it.fileUrl }.shuffled()
        }

        // Fast attempt 2: If primary sub had 0 posts and multiple subs active, try one alternative
        if (targetSubs.size > 1) {
            val altSub = targetSubs.filter { it != chosenSub }.random().trim().removePrefix("r/").removePrefix("/")
            val altPosts = fetchSubredditPosts(altSub, blockedLower, ratingFilter)
            if (altPosts.isNotEmpty()) {
                return@withContext altPosts.distinctBy { it.fileUrl }.shuffled()
            }
        }

        emptyList()
    }

    private fun fetchSubredditPosts(
        subredditQuery: String,
        blockedLower: Set<String>,
        ratingFilter: RatingFilter
    ): List<Post> {
        val sort = listOf(
            "hot.json?limit=30&raw_json=1&include_over_18=on&over18=1",
            "top.json?t=month&limit=30&raw_json=1&include_over_18=on&over18=1"
        ).random()

        // 1. Primary: old.reddit.com with age-verification cookie and include_over_18
        val oldRedditUrl = "https://old.reddit.com/r/$subredditQuery/$sort"
        val oldJson = fetchUrl(oldRedditUrl, isReddit = true)
        if (oldJson.isNotBlank()) {
            val posts = parseRedditJson(oldJson, subredditQuery, blockedLower, ratingFilter)
            if (posts.isNotEmpty()) {
                Log.d(TAG, "Successfully fetched ${posts.size} posts from old.reddit.com for r/$subredditQuery")
                return posts
            }
        }

        // 2. Direct www.reddit.com JSON endpoint
        val directUrl = "https://www.reddit.com/r/$subredditQuery/hot.json?limit=30&raw_json=1"
        val directJson = fetchUrl(directUrl, isReddit = true)
        if (directJson.isNotBlank()) {
            val posts = parseRedditJson(directJson, subredditQuery, blockedLower, ratingFilter)
            if (posts.isNotEmpty()) {
                Log.d(TAG, "Successfully fetched ${posts.size} posts from reddit.com for r/$subredditQuery")
                return posts
            }
        }

        // 3. Secondary fallback: PullPush open Reddit search archive (Zero-auth, zero age-gate mirror)
        val pullpushUrl = "https://api.pullpush.io/reddit/search/submission/?subreddit=$subredditQuery&size=30"
        val pullpushJson = fetchUrl(pullpushUrl, isReddit = false)
        if (pullpushJson.isNotBlank()) {
            val posts = parsePullPushJson(pullpushJson, subredditQuery, blockedLower, ratingFilter)
            if (posts.isNotEmpty()) {
                Log.d(TAG, "Successfully fetched ${posts.size} posts from PullPush mirror for r/$subredditQuery")
                return posts
            }
        }

        Log.w(TAG, "No valid posts retrieved across Reddit providers for r/$subredditQuery")
        return emptyList()
    }

    private fun fetchUrl(url: String, isReddit: Boolean): String {
        return try {
            val reqBuilder = Request.Builder().url(url)
            if (isReddit) {
                reqBuilder
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Cookie", "over18=1; session_tracker=; edgebucket=;")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Referer", "https://old.reddit.com/")
            } else {
                reqBuilder
                    .header("User-Agent", "MommyPix/1.0 (Android)")
                    .header("Accept", "application/json")
            }

            fastRedditClient.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty().trim()
                    if (body.startsWith("{") || body.startsWith("[")) {
                        body
                    } else {
                        Log.w(TAG, "Request to $url returned non-JSON (HTML age gate/block): ${body.take(120)}")
                        ""
                    }
                } else {
                    Log.w(TAG, "Request to $url failed with HTTP ${response.code}")
                    ""
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception fetching $url: ${e.message}")
            ""
        }
    }

    private fun parseRedditJson(
        jsonString: String,
        subreddit: String,
        blockedLower: Set<String>,
        ratingFilter: RatingFilter
    ): List<Post> {
        val posts = mutableListOf<Post>()
        try {
            val root = JSONObject(jsonString)
            val dataObj = root.optJSONObject("data") ?: return emptyList()
            val children = dataObj.optJSONArray("children") ?: return emptyList()

            for (i in 0 until children.length()) {
                val child = children.optJSONObject(i) ?: continue
                val postData = child.optJSONObject("data") ?: continue

                val id = postData.optString("id", "")
                if (id.isBlank()) continue

                val title = postData.optString("title", "").trim()
                val over18 = postData.optBoolean("over_18", false)
                val isVideo = postData.optBoolean("is_video", false)
                if (isVideo) continue

                // Check rating filter
                val matchesRating = when (ratingFilter) {
                    RatingFilter.BOTH -> true
                    RatingFilter.SAFE_ONLY -> !over18
                    RatingFilter.NSFW_ONLY -> over18
                }
                if (!matchesRating) continue

                // Mandatory legal content filter
                if (!ContentFilter.isPostPermitted(title)) continue

                // User blacklist filter
                val titleTokens = title.lowercase().split("[^a-zA-Z0-9]+".toRegex()).filter { it.isNotBlank() }
                if (titleTokens.any { blockedLower.contains(it) }) continue

                val meaningfulTags = titleTokens.filter { it.length > 2 }.take(8)

                // 1. Extract from Reddit Galleries (multi-image posts)
                val mediaMetadata = postData.optJSONObject("media_metadata")
                var foundGalleryImages = false
                if (mediaMetadata != null) {
                    val keys = mediaMetadata.keys()
                    while (keys.hasNext()) {
                        val mediaId = keys.next()
                        val mediaItem = mediaMetadata.optJSONObject(mediaId) ?: continue
                        val status = mediaItem.optString("status", "valid")
                        if (status != "valid") continue

                        val sObj = mediaItem.optJSONObject("s")
                        val galleryUrl = sObj?.optString("u")?.replace("&amp;", "&")
                            ?: "https://i.redd.it/$mediaId.jpg"
                        val w = sObj?.optInt("x", 1080) ?: 1080
                        val h = sObj?.optInt("y", 1920) ?: 1920

                        val safeId = 80_000_000_000L + (kotlin.math.abs("$id-$mediaId".hashCode().toLong()) % 10_000_000_000L)
                        posts.add(
                            Post(
                                id = safeId,
                                fileUrl = galleryUrl,
                                previewUrl = galleryUrl,
                                sampleUrl = galleryUrl,
                                width = w,
                                height = h,
                                rating = if (over18) "explicit" else "safe",
                                tags = listOf("r/$subreddit") + meaningfulTags,
                                sourceName = "r/$subreddit"
                            )
                        )
                        foundGalleryImages = true
                    }
                }

                if (foundGalleryImages) continue

                // 2. Extract from single image post
                val rawUrl = postData.optString("url", "").trim()
                val previewObj = postData.optJSONObject("preview")
                val imagesArr = previewObj?.optJSONArray("images")
                val firstImg = imagesArr?.optJSONObject(0)
                val sourceObj = firstImg?.optJSONObject("source")
                val previewSourceUrl = sourceObj?.optString("url")?.replace("&amp;", "&")
                val width = sourceObj?.optInt("width", 1080) ?: 1080
                val height = sourceObj?.optInt("height", 1920) ?: 1920

                val fileUrl = when {
                    rawUrl.endsWith(".jpg", ignoreCase = true) ||
                    rawUrl.endsWith(".jpeg", ignoreCase = true) ||
                    rawUrl.endsWith(".png", ignoreCase = true) ||
                    rawUrl.endsWith(".webp", ignoreCase = true) -> rawUrl

                    rawUrl.contains("i.redd.it") -> rawUrl
                    rawUrl.contains("i.imgur.com") && !rawUrl.endsWith(".gifv", ignoreCase = true) -> {
                        if (rawUrl.contains(".")) rawUrl else "$rawUrl.jpg"
                    }
                    !previewSourceUrl.isNullOrBlank() -> previewSourceUrl
                    else -> null
                } ?: continue

                val safeId = 80_000_000_000L + (kotlin.math.abs(id.hashCode().toLong()) % 10_000_000_000L)

                posts.add(
                    Post(
                        id = safeId,
                        fileUrl = fileUrl,
                        previewUrl = previewSourceUrl ?: fileUrl,
                        sampleUrl = previewSourceUrl,
                        width = width,
                        height = height,
                        rating = if (over18) "explicit" else "safe",
                        tags = listOf("r/$subreddit") + meaningfulTags,
                        sourceName = "r/$subreddit"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Reddit JSON for r/$subreddit: ${e.message}", e)
        }

        return posts
    }

    private fun parsePullPushJson(
        jsonString: String,
        subreddit: String,
        blockedLower: Set<String>,
        ratingFilter: RatingFilter
    ): List<Post> {
        val posts = mutableListOf<Post>()
        try {
            val root = JSONObject(jsonString)
            val dataArr = root.optJSONArray("data") ?: return emptyList()

            for (i in 0 until dataArr.length()) {
                val item = dataArr.optJSONObject(i) ?: continue
                val id = item.optString("id", "")
                val title = item.optString("title", "").trim()
                val rawUrl = item.optString("url", "").trim()
                val over18 = item.optBoolean("over_18", true)

                if (rawUrl.isBlank()) continue

                val isImage = rawUrl.endsWith(".jpg", ignoreCase = true) ||
                        rawUrl.endsWith(".jpeg", ignoreCase = true) ||
                        rawUrl.endsWith(".png", ignoreCase = true) ||
                        rawUrl.endsWith(".webp", ignoreCase = true) ||
                        rawUrl.contains("i.redd.it") ||
                        (rawUrl.contains("i.imgur.com") && !rawUrl.endsWith(".gifv", ignoreCase = true))

                if (!isImage) continue

                val fileUrl = if (rawUrl.contains("i.imgur.com") && !rawUrl.contains(".")) "$rawUrl.jpg" else rawUrl

                val matchesRating = when (ratingFilter) {
                    RatingFilter.BOTH -> true
                    RatingFilter.SAFE_ONLY -> !over18
                    RatingFilter.NSFW_ONLY -> over18
                }
                if (!matchesRating) continue

                if (!ContentFilter.isPostPermitted(title)) continue

                val titleTokens = title.lowercase().split("[^a-zA-Z0-9]+".toRegex()).filter { it.isNotBlank() }
                if (titleTokens.any { blockedLower.contains(it) }) continue

                val safeId = 85_000_000_000L + (kotlin.math.abs(id.hashCode().toLong()) % 10_000_000_000L)
                val meaningfulTags = titleTokens.filter { it.length > 2 }.take(8)

                posts.add(
                    Post(
                        id = safeId,
                        fileUrl = fileUrl,
                        previewUrl = fileUrl,
                        sampleUrl = fileUrl,
                        width = 1080,
                        height = 1920,
                        rating = if (over18) "explicit" else "safe",
                        tags = listOf("r/$subreddit") + meaningfulTags,
                        sourceName = "r/$subreddit"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing PullPush JSON for r/$subreddit: ${e.message}", e)
        }
        return posts
    }

    companion object {
        private const val TAG = "RedditScraper"
    }
}
