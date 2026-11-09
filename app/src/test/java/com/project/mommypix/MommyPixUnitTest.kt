package com.project.mommypix

import com.google.gson.Gson
import com.project.mommypix.data.api.PostDto
import com.project.mommypix.domain.model.Post
import com.project.mommypix.security.ContentFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MommyPixUnitTest {

    @Test
    fun testContentFilterRejectsProhibitedContent() {
        assertFalse(ContentFilter.isPostPermitted("girl solo blue_hair loli"))
        assertFalse(ContentFilter.isPostPermitted("shota boy smile"))
        assertFalse(ContentFilter.isPostPermitted("underage school_uniform"))
        assertFalse(ContentFilter.isPostPermitted("child outdoors"))
        assertFalse(ContentFilter.isPostPermitted("toddler play"))
        assertFalse(ContentFilter.isPostPermitted("girl cute loli_ears"))
    }

    @Test
    fun testContentFilterAllowsPermittedContent() {
        assertTrue(ContentFilter.isPostPermitted("1girl solo long_hair smile looking_at_viewer dress"))
        assertTrue(ContentFilter.isPostPermitted("scenery sunset cloud sky wallpaper"))
        assertTrue(ContentFilter.isPostPermitted("cyberpunk city neon_lights futuristic"))
        assertTrue(ContentFilter.isPostPermitted(null))
        assertTrue(ContentFilter.isPostPermitted(""))
    }

    @Test
    fun testSafeQueryBuilding() {
        val query = ContentFilter.buildSafeTagQuery("sort:random")
        assertTrue(query.startsWith("sort:random"))
        assertTrue(query.contains("-loli"))
        assertTrue(query.contains("-shota"))
        assertTrue(query.contains("-child"))
        assertTrue(query.contains("-underage"))
    }

    @Test
    fun testPostModelMediaHandling() {
        val imagePost = Post(
            id = 101,
            fileUrl = "https://api-cdn.rule34.xxx/images/101.jpg",
            previewUrl = "https://api-cdn.rule34.xxx/thumbnails/101.jpg",
            sampleUrl = null,
            width = 1920,
            height = 1080,
            rating = "questionable",
            tags = listOf("scenery", "solo")
        )

        assertTrue(imagePost.isSupportedImage)
        assertFalse(imagePost.isVideo)
        assertEquals("1920x1080", imagePost.dimensionsFormatted)
        assertEquals("Questionable", imagePost.ratingLabel)

        val videoPost = imagePost.copy(fileUrl = "https://api-cdn.rule34.xxx/images/101.mp4")
        assertFalse(videoPost.isSupportedImage)
        assertTrue(videoPost.isVideo)
    }

    @Test
    fun testPostDtoDeserializationAndMapping() {
        val json = """
            {
                "id": 888888,
                "file_url": "https://api-cdn.rule34.xxx/images/test.png",
                "preview_url": "https://api-cdn.rule34.xxx/thumbnails/test.jpg",
                "sample_url": "https://api-cdn.rule34.xxx/samples/test.jpg",
                "width": 3840,
                "height": 2160,
                "rating": "explicit",
                "tags": "scenery landscape sunset 4k"
            }
        """.trimIndent()

        val dto = Gson().fromJson(json, PostDto::class.java)
        assertNotNull(dto)
        assertEquals(888888L, dto.id)

        val post = dto.toDomain()
        assertNotNull(post)
        assertEquals(888888L, post?.id)
        assertEquals("3840x2160", post?.dimensionsFormatted)
        assertEquals("Explicit", post?.ratingLabel)
        assertEquals(4, post?.tags?.size)
        assertTrue(post?.tags?.contains("landscape") == true)
    }
}

