package com.project.mommypix.data.api

import com.google.gson.annotations.SerializedName
import com.project.mommypix.domain.model.Post

data class PostDto(
    @SerializedName("id")
    val id: Long? = null,

    @SerializedName("file_url")
    val fileUrl: String? = null,

    @SerializedName("preview_url")
    val previewUrl: String? = null,

    @SerializedName("sample_url")
    val sampleUrl: String? = null,

    @SerializedName("width")
    val width: Int? = null,

    @SerializedName("height")
    val height: Int? = null,

    @SerializedName("rating")
    val rating: String? = null,

    @SerializedName("tags")
    val tags: String? = null,

    @SerializedName("score")
    val score: Int? = null
) {
    fun toDomain(): Post? {
        val safeId = id ?: return null
        val safeFileUrl = fileUrl ?: return null

        val tagList = tags?.split("\\s+".toRegex())
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        return Post(
            id = safeId,
            fileUrl = safeFileUrl,
            previewUrl = previewUrl ?: safeFileUrl,
            sampleUrl = sampleUrl,
            width = width ?: 0,
            height = height ?: 0,
            rating = rating ?: "general",
            tags = tagList
        )
    }
}

