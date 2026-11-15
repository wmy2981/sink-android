package cool.sink.mobile.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cool.sink.mobile.AppContainer
import cool.sink.mobile.ui.screens.ConnectScreen
import cool.sink.mobile.ui.screens.LinkEditScreen
import cool.sink.mobile.ui.screens.LinksScreen
import cool.sink.mobile.ui.screens.SettingsScreen
import cool.sink.mobile.ui.theme.SinkTheme

private object Routes {
    const val CONNECT = "connect"
    const val LINKS = "links"
    const val EDIT = "edit?slug={slug}"
    const val SETTINGS = "settings"
}

@Composable
fun SinkApp(container: AppContainer) {
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(initialValue = null)

    val current = settings
    if (current == null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0C)))
        return
    }

    SinkTheme(themeMode = current.themeMode, dynamicColor = current.dynamicColor) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val navController = rememberNavController()
            val startDestination = remember {
                if (current.isConnected) Routes.LINKS else Routes.CONNECT
            }

            NavHost(
                navController = navController,
                startDestination = startDestination,
                enterTransition = {
                    slideInHorizontally(animationSpec = tween(240)) { fullWidth -> fullWidth }
                },
                exitTransition = {
                    slideOutHorizontally(animationSpec = tween(240)) { fullWidth -> -fullWidth / 4 } +
                        fadeOut(animationSpec = tween(180))
                },
                popEnterTransition = {
                    slideInHorizontally(animationSpec = tween(240)) { fullWidth -> -fullWidth / 4 } +
                        fadeIn(animationSpec = tween(180))
                },
                popExitTransition = {
                    slideOutHorizontally(animationSpec = tween(240)) { fullWidth -> fullWidth }
                },
            ) {
                composable(Routes.CONNECT) {
                    ConnectScreen(
                        repository = container.repository,
                        onConnected = {
                            navController.navigate(Routes.LINKS) {
                                popUpTo(Routes.CONNECT) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }

                composable(Routes.LINKS) { entry ->
                    val refreshSignal by entry.savedStateHandle
                        .getStateFlow("links_refresh", false)
                        .collectAsStateWithLifecycle()
                    LinksScreen(
                        repository = container.repository,
                        refreshSignal = refreshSignal,
                        onRefreshHandled = { entry.savedStateHandle["links_refresh"] = false },
                        onCreate = { navController.navigate("edit") },
                        onEdit = { slug ->
                            navController.navigate("edit?slug=${Uri.encode(slug)}")
                        },
                        onSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }

                composable(
                    route = Routes.EDIT,
                    arguments = listOf(
                        navArgument("slug") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) { entry ->
                    val slug = entry.arguments?.getString("slug")
                    LinkEditScreen(
                        repository = container.repository,
                        slug = slug,
                        onBack = { navController.popBackStack() },
                        onSaved = {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("links_refresh", true)
                            navController.popBackStack()
                        },
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        repository = container.repository,
                        settingsStore = container.settingsStore,
                        onBack = { navController.popBackStack() },
                        onDisconnected = {
                            navController.navigate(Routes.CONNECT) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
        }
    }
}
