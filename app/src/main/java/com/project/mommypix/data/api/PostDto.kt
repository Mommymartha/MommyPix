package com.project.mommypix.data.api

import com.google.gson.annotations.SerializedName
import com.project.mommypix.data.preferences.ImageSource
import com.project.mommypix.domain.model.Post

data class PostDto(
    @SerializedName("id")
    val id: Long? = null,

    @SerializedName("file_url")
    val fileUrl: String? = null,

    @SerializedName("large_file_url")
    val largeFileUrl: String? = null,

    @SerializedName("preview_url")
    val previewUrl: String? = null,

    @SerializedName("preview_file_url")
    val previewFileUrl: String? = null,

    @SerializedName("sample_url")
    val sampleUrl: String? = null,

    @SerializedName("directory")
    val directory: String? = null,

    @SerializedName("image")
    val image: String? = null,

    @SerializedName("width")
    val width: Int? = null,

    @SerializedName("image_width")
    val imageWidth: Int? = null,

    @SerializedName("height")
    val height: Int? = null,

    @SerializedName("image_height")
    val imageHeight: Int? = null,

    @SerializedName("rating")
    val rating: String? = null,

    @SerializedName("tags")
    val tags: String? = null,

    @SerializedName("tag_string")
    val tagString: String? = null,

    @SerializedName("score")
    val score: Int? = null
) {
    fun toDomain(source: ImageSource = ImageSource.RULE34): Post? {
        val safeId = id ?: return null

        val rawFileUrl = largeFileUrl ?: fileUrl
        val resolvedFileUrl = when {
            !rawFileUrl.isNullOrBlank() -> {
                if (rawFileUrl.startsWith("//")) "https:$rawFileUrl" else rawFileUrl
            }
            source == ImageSource.SAFEBOORU && !directory.isNullOrBlank() && !image.isNullOrBlank() -> {
                "https://safebooru.org/images/$directory/$image"
            }
            source == ImageSource.GELBOORU && !directory.isNullOrBlank() && !image.isNullOrBlank() -> {
                "https://img3.gelbooru.com/images/$directory/$image"
            }
            source == ImageSource.TBIB && !directory.isNullOrBlank() && !image.isNullOrBlank() -> {
                "https://tbib.org/images/$directory/$image"
            }
            else -> null
        } ?: return null

        val rawPreviewUrl = previewFileUrl ?: previewUrl
        val resolvedPreviewUrl = when {
            !rawPreviewUrl.isNullOrBlank() -> {
                if (rawPreviewUrl.startsWith("//")) "https:$rawPreviewUrl" else rawPreviewUrl
            }
            source == ImageSource.SAFEBOORU && !directory.isNullOrBlank() && !image.isNullOrBlank() -> {
                "https://safebooru.org/thumbnails/$directory/thumbnail_$image"
            }
            source == ImageSource.GELBOORU && !directory.isNullOrBlank() && !image.isNullOrBlank() -> {
                "https://img3.gelbooru.com/thumbnails/$directory/thumbnail_$image"
            }
            source == ImageSource.TBIB && !directory.isNullOrBlank() && !image.isNullOrBlank() -> {
                "https://tbib.org/thumbnails/$directory/thumbnail_$image"
            }
            else -> resolvedFileUrl
        }

        val resolvedSampleUrl = when {
            !sampleUrl.isNullOrBlank() -> {
                if (sampleUrl.startsWith("//")) "https:$sampleUrl" else sampleUrl
            }
            else -> null
        }

        val allTags = tags ?: tagString ?: ""
        val tagList = allTags.split("\\s+".toRegex())
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val resolvedWidth = width ?: imageWidth ?: 0
        val resolvedHeight = height ?: imageHeight ?: 0

        return Post(
            id = safeId,
            fileUrl = resolvedFileUrl,
            previewUrl = resolvedPreviewUrl,
            sampleUrl = resolvedSampleUrl,
            width = resolvedWidth,
            height = resolvedHeight,
            rating = rating ?: "general",
            tags = tagList,
            sourceName = source.displayName
        )
    }
}
