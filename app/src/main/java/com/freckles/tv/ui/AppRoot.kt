package com.freckles.tv.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.freckles.tv.ui.common.LocalRepo
import com.freckles.tv.ui.detail.MovieDetailScreen
import com.freckles.tv.ui.detail.ShowDetailScreen
import com.freckles.tv.ui.home.HomeScreen
import com.freckles.tv.ui.library.LibraryScreen
import com.freckles.tv.ui.search.SearchScreen
import com.freckles.tv.ui.stats.StatsScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
    Search("search", "Cerca", Icons.Filled.Search),
    Library("library", "Libreria", Icons.Filled.VideoLibrary),
    Stats("stats", "Statistiche", Icons.Filled.BarChart),
}

@Composable
fun AppRoot(username: String) {
    val nav = rememberNavController()
    val repo = LocalRepo.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { repo.messages.collect { snackbar.showSnackbar(it) } }

    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val showBar = Tab.entries.any { it.route == route }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) NavigationBar {
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
        NavHost(nav, startDestination = Tab.Home.route, modifier = Modifier.padding(padding)) {
            composable(Tab.Home.route) { HomeScreen(username, openShow, openMovie) }
            composable(Tab.Search.route) { SearchScreen(openShow, openMovie) }
            composable(Tab.Library.route) { LibraryScreen(openShow, openMovie) }
            composable(Tab.Stats.route) { StatsScreen() }
            composable("show/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                ShowDetailScreen(it.arguments!!.getLong("id")) { nav.popBackStack() }
            }
            composable("movie/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                MovieDetailScreen(it.arguments!!.getLong("id")) { nav.popBackStack() }
            }
        }
    }
}
