package com.project.mommypix.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Preview : Screen("preview/{postId}") {
        fun createRoute(postId: Long) = "preview/$postId"
    }
    data object Favorites : Screen("favorites")
    data object History : Screen("history")
    data object Settings : Screen("settings")
}

