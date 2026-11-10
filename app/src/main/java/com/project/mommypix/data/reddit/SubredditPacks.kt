package com.project.mommypix.data.reddit

data class SubredditCategory(
    val id: String,
    val title: String,
    val emoji: String,
    val subreddits: List<String>
)

object SubredditPacks {

    val CATEGORIES: List<SubredditCategory> = listOf(
        SubredditCategory(
            id = "bdsm",
            title = "Bondage, Restraints & BDSM",
            emoji = "⛓️",
            subreddits = listOf("Bondage", "bdsm", "BDSMGW", "Femdom")
        ),
        SubredditCategory(
            id = "chastity",
            title = "Chastity & Denial",
            emoji = "🔒",
            subreddits = listOf("SissyChastity", "chastity", "sissylock", "Locktober")
        ),
        SubredditCategory(
            id = "sissy",
            title = "Feminization & Sissy",
            emoji = "🎀",
            subreddits = listOf(
                "Sissy", "Sissies", "Feminization", "ForcedFeminization",
                "sissydressing", "sissy_humiliation", "sissycaptions"
            )
        ),
        SubredditCategory(
            id = "femdom",
            title = "Humiliation & Femdom",
            emoji = "👑",
            subreddits = listOf(
                "FemdomHumiliation", "FemdomCommunity", "SissyFemdom", "chastityhumiliations"
            )
        ),
        SubredditCategory(
            id = "feet",
            title = "Feet & Footwear",
            emoji = "👣",
            subreddits = listOf("feet", "FootFetish", "RealGirls")
        ),
        SubredditCategory(
            id = "creators",
            title = "Cosplay & Creators",
            emoji = "✨",
            subreddits = listOf("alicedelish", "BelleDelphine", "BelleDelphinePatreon", "cosplay")
        ),
        SubredditCategory(
            id = "amateur",
            title = "Amateur & Real-Life Photography",
            emoji = "📸",
            subreddits = listOf(
                "gonewild", "Amateur", "altgonewild", "AsianHotties",
                "NSFW", "Hotwife", "Boobies"
            )
        )
    )

    val ALL_SUBREDDITS: List<String> = CATEGORIES.flatMap { it.subreddits }.distinct()

    val DEFAULT_ENABLED_SUBREDDITS: Set<String> = setOf(
        "Bondage", "bdsm", "Femdom", "Sissy", "Feminization", "gonewild", "Amateur", "feet",
        "alicedelish", "BelleDelphine"
    )

    fun mapSubredditToSearchTerm(subreddit: String): String {
        val clean = subreddit.trim().removePrefix("r/").removePrefix("/").lowercase()
        return when (clean) {
            "alicedelish" -> "alice-delish"
            "belledelphine", "belledelphinepatreon" -> "belle-delphine"
            "cosplay" -> "cosplay"
            "bondage", "bdsm", "bdsmgw" -> "bondage"
            "femdom", "femdomhumiliation", "femdomcommunity", "sissyfemdom" -> "femdom"
            "sissychastity", "chastity", "sissylock", "locktober", "chastityhumiliations" -> "chastity"
            "sissy", "sissies", "sissydressing", "sissy_humiliation", "sissycaptions" -> "sissy"
            "feminization", "forcedfeminization" -> "feminization"
            "feet", "footfetish" -> "feet"
            "realgirls", "gonewild", "amateur" -> "amateur"
            "altgonewild" -> "alt-girl"
            "asianhotties" -> "asian"
            "boobies" -> "boobs"
            "hotwife" -> "hotwife"
            else -> clean.replace('_', '-')
        }
    }
}

