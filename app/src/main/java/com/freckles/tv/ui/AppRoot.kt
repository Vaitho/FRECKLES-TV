package com.freckles.tv.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.freckles.tv.data.prefs.Profile
import com.freckles.tv.data.prefs.UserPrefs
import com.freckles.tv.ui.common.Avatar
import com.freckles.tv.ui.common.LocalRepo
import com.freckles.tv.ui.detail.MovieDetailScreen
import com.freckles.tv.ui.games.GameDetailScreen
import com.freckles.tv.ui.games.GamePreviewScreen
import com.freckles.tv.ui.detail.PreviewScreen
import com.freckles.tv.ui.detail.ShowDetailScreen
import com.freckles.tv.ui.home.HomeScreen
import com.freckles.tv.ui.library.LibraryScreen
import com.freckles.tv.ui.profile.AppDrawer
import com.freckles.tv.ui.profile.ProfileScreen
import com.freckles.tv.ui.search.SearchScreen
import com.freckles.tv.ui.settings.SettingsScreen
import com.freckles.tv.ui.stats.StatsScreen
import kotlinx.coroutines.launch

private enum class Tab(val route: String, val label: String, val title: String, val icon: ImageVector) {
    Home("home", "Home", "Freckles TV", Icons.Filled.Home),
    Search("search", "Cerca", "Cerca", Icons.Filled.Search),
    Library("library", "Libreria", "La tua libreria", Icons.Filled.VideoLibrary),
    Stats("stats", "Statistiche", "Statistiche", Icons.Filled.BarChart),
}

@Composable
fun AppRoot(profile: Profile, prefs: UserPrefs) {
    val nav = rememberNavController()
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    LaunchedEffect(Unit) { repo.messages.collect { snackbar.showSnackbar(it) } }

    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val currentTab = Tab.entries.firstOrNull { it.route == route }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = currentTab != null || drawer.isOpen,
        drawerContent = {
            AppDrawer(
                profile = profile,
                prefs = prefs,
                onProfile = { scope.launch { drawer.close() }; nav.navigate("profile") },
                onSettings = { scope.launch { drawer.close() }; nav.navigate("settings") },
            )
        },
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (currentTab != null) {
                    Row(
                        Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(profile, 42.dp, onClick = { scope.launch { drawer.open() } })
                        Text(
                            currentTab.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 14.dp),
                        )
                    }
                }
            },
            bottomBar = {
                if (currentTab != null) NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            },
        ) { padding ->
            val openShow = { id: Long -> nav.navigate("show/$id") }
            val openMovie = { id: Long -> nav.navigate("movie/$id") }
            val openGame = { id: Long -> nav.navigate("game/$id") }
            val openGamePreview = { rawgId: Int -> nav.navigate("gamepreview/$rawgId") }
            val openPreview = { type: String, id: Int -> nav.navigate("preview/$type/$id") }
            NavHost(
                nav,
                startDestination = Tab.Home.route,
                modifier = Modifier.padding(padding),
                enterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f) },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f) },
                popExitTransition = { fadeOut(tween(120)) },
            ) {
                composable(Tab.Home.route) { HomeScreen(profile, openShow, openMovie, openPreview, openGame) }
                composable(Tab.Search.route) { SearchScreen(openShow, openMovie, openPreview, openGame, openGamePreview) }
                composable(Tab.Library.route) { LibraryScreen(openShow, openMovie, openGame) }
                composable(Tab.Stats.route) { StatsScreen(openShow) }
                composable("settings") { SettingsScreen { nav.popBackStack() } }
                composable("profile") { ProfileScreen(profile, prefs, { nav.popBackStack() }, openShow, openMovie, openGame) }
                composable("show/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                    ShowDetailScreen(it.arguments!!.getLong("id")) { nav.popBackStack() }
                }
                composable(
                    "preview/{type}/{id}",
                    listOf(navArgument("type") { type = NavType.StringType }, navArgument("id") { type = NavType.IntType }),
                ) {
                    val type = it.arguments!!.getString("type")!!
                    val id = it.arguments!!.getInt("id")
                    PreviewScreen(type, id, { nav.popBackStack() }) { isShow, libId ->
                        nav.navigate(if (isShow) "show/$libId" else "movie/$libId") {
                            popUpTo("preview/{type}/{id}") { inclusive = true }
                        }
                    }
                }
                composable("gamepreview/{rawgId}", listOf(navArgument("rawgId") { type = NavType.IntType })) {
                    GamePreviewScreen(it.arguments!!.getInt("rawgId"), { nav.popBackStack() }) { libId ->
                        nav.navigate("game/$libId") { popUpTo("gamepreview/{rawgId}") { inclusive = true } }
                    }
                }
                composable("game/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                    GameDetailScreen(it.arguments!!.getLong("id")) { nav.popBackStack() }
                }
                composable("movie/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                    MovieDetailScreen(it.arguments!!.getLong("id")) { nav.popBackStack() }
                }
            }
        }
    }
}
