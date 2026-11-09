package com.project.mommypix.domain.model

data class Post(
    val id: Long,
    val fileUrl: String,
    val previewUrl: String,
    val sampleUrl: String?,
    val width: Int,
    val height: Int,
    val rating: String,
    val tags: List<String>,
    val isFavorite: Boolean = false
) {
    val dimensionsFormatted: String
        get() = "${width}x${height}"

    val ratingLabel: String
        get() = when (rating.lowercase()) {
            "s", "safe", "general" -> "Safe"
            "q", "questionable" -> "Questionable"
            "e", "explicit" -> "Explicit"
            else -> rating.replaceFirstChar { it.uppercase() }
        }

    val isSupportedImage: Boolean
        get() {
            val cleanUrl = fileUrl.lowercase().substringBefore("?")
            return cleanUrl.endsWith(".jpg") ||
                    cleanUrl.endsWith(".jpeg") ||
                    cleanUrl.endsWith(".png") ||
                    cleanUrl.endsWith(".webp")
        }

    val isVideo: Boolean
        get() {
            val cleanUrl = fileUrl.lowercase().substringBefore("?")
            return cleanUrl.endsWith(".mp4") || cleanUrl.endsWith(".webm")
        }
}

