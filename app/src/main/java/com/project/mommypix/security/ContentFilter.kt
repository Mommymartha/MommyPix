package com.project.mommypix.security

/**
 * Enforces strict content moderation rules.
 * Prohibits and filters out any content depicting minors, non-consensual acts,
 * or illegal/harmful material.
 */
object ContentFilter {

    /**
     * Tags that are strictly prohibited and immediately disqualified.
     */
    val PROHIBITED_TAGS: Set<String> = setOf(
        "loli",
        "lolicon",
        "shota",
        "shotacon",
        "underage",
        "child",
        "children",
        "minor",
        "minors",
        "toddler",
        "baby",
        "infant",
        "cub",
        "guro",
        "bestiality",
        "zoophilia",
        "necrophilia",
        "snuff",
        "rape",
        "non-con"
    )

    /**
     * Tags appended negatively to API search queries to prevent banned content from being fetched.
     */
    val EXCLUDED_QUERY_TAGS: List<String> = listOf(
        "-loli",
        "-lolicon",
        "-shota",
        "-shotacon",
        "-underage",
        "-child",
        "-children",
        "-toddler",
        "-baby",
        "-cub",
        "-guro",
        "-bestiality",
        "-rape"
    )

    /**
     * Checks if a post's tag string contains any prohibited content.
     * @return true if the content is safe and permitted; false if it contains prohibited tags.
     */
    fun isPostPermitted(tagsString: String?): Boolean {
        if (tagsString.isNullOrBlank()) return true

        val tagTokens = tagsString.lowercase()
            .split("\\s+".toRegex())
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        for (tag in tagTokens) {
            // Check direct match
            if (PROHIBITED_TAGS.contains(tag)) {
                return false
            }
            // Check sub-parts (e.g., loli_*, *_shota)
            for (prohibited in PROHIBITED_TAGS) {
                if (tag == prohibited ||
                    tag.startsWith("${prohibited}_") ||
                    tag.endsWith("_$prohibited") ||
                    tag.contains("_${prohibited}_")
                ) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Formats search tags by combining user/mode tags with mandatory safety exclusions.
     */
    fun buildSafeTagQuery(baseTags: String = "sort:random"): String {
        val queryParts = mutableListOf<String>()
        if (baseTags.isNotBlank()) {
            queryParts.add(baseTags.trim())
        }
        queryParts.addAll(EXCLUDED_QUERY_TAGS)
        return queryParts.joinToString(" ")
    }
}

