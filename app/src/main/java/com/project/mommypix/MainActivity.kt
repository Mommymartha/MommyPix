package com.project.mommypix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.project.mommypix.data.preferences.AppTheme
import com.project.mommypix.ui.navigation.Screen
import com.project.mommypix.ui.screens.favorites.FavoritesScreen
import com.project.mommypix.ui.screens.favorites.FavoritesViewModel
import com.project.mommypix.ui.screens.history.HistoryScreen
import com.project.mommypix.ui.screens.history.HistoryViewModel
import com.project.mommypix.ui.screens.home.HomeScreen
import com.project.mommypix.ui.screens.home.HomeViewModel
import com.project.mommypix.ui.screens.preview.PreviewScreen
import com.project.mommypix.ui.screens.preview.PreviewViewModel
import com.project.mommypix.ui.screens.settings.SettingsScreen
import com.project.mommypix.ui.screens.settings.SettingsViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.project.mommypix.ui.theme.MommyPixTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MommyPixApp
        lifecycleScope.launch(Dispatchers.IO) {
            app.repository.replenishPrefetchPool()
        }

        setContent {
            val userPrefs by app.preferencesRepository.userPreferencesFlow
                .collectAsState(initial = null)

            val currentTheme = userPrefs?.appTheme ?: AppTheme.SYSTEM

            MommyPixTheme(appTheme = currentTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    val homeViewModel: HomeViewModel = viewModel()
                    val previewViewModel: PreviewViewModel = viewModel()
                    val historyViewModel: HistoryViewModel = viewModel()
                    val favoritesViewModel: FavoritesViewModel = viewModel()
                    val settingsViewModel: SettingsViewModel = viewModel()

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                viewModel = homeViewModel,
                                onNavigateToPreview = { postId ->
                                    navController.navigate(Screen.Preview.createRoute(postId))
                                },
                                onNavigateToFavorites = {
                                    navController.navigate(Screen.Favorites.route)
                                },
                                onNavigateToHistory = {
                                    navController.navigate(Screen.History.route)
                                },
                                onNavigateToSettings = {
                                    navController.navigate(Screen.Settings.route)
                                }
                            )
                        }

                        composable(
                            route = Screen.Preview.route,
                            arguments = listOf(
                                navArgument("postId") { type = NavType.LongType }
                            )
                        ) { backStackEntry ->
                            val postId = backStackEntry.arguments?.getLong("postId") ?: 0L
                            PreviewScreen(
                                postId = postId,
                                viewModel = previewViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Favorites.route) {
                            FavoritesScreen(
                                viewModel = favoritesViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onPreviewPost = { postId ->
                                    navController.navigate(Screen.Preview.createRoute(postId))
                                }
                            )
                        }

                        composable(Screen.History.route) {
                            HistoryScreen(
                                viewModel = historyViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onPreviewItem = { postId ->
                                    navController.navigate(Screen.Preview.createRoute(postId))
                                }
                            )
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
