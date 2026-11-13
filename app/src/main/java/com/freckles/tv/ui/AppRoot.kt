package com.freckles.tv.ui

import androidx.compose.runtime.CompositionLocalProvider
import com.freckles.tv.ui.common.LocalOpenPerson
import com.freckles.tv.ui.detail.PersonScreen
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.material.icons.filled.PlayCircle
import com.freckles.tv.data.remote.NightApi
import com.freckles.tv.ui.watch.PlayerScreen
import com.freckles.tv.ui.watch.WatchScreen
import com.freckles.tv.ui.watch.NightData
import com.freckles.tv.ui.watch.WatchMovieScreen
import com.freckles.tv.ui.watch.WatchSeriesScreen
import com.freckles.tv.ui.profile.AppDrawer
import com.freckles.tv.ui.profile.ProfileScreen
import com.freckles.tv.ui.search.SearchScreen
import com.freckles.tv.ui.settings.SettingsScreen
import com.freckles.tv.ui.stats.StatsScreen
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Spacer
import kotlinx.coroutines.launch

private enum class Tab(val route: String, val label: String, val title: String, val icon: ImageVector) {
    Home("home", "Home", "Freckles TV", Icons.Filled.Home),
    Search("search", "Cerca", "Cerca", Icons.Filled.Search),
    Library("library", "Libreria", "La tua libreria", Icons.Filled.VideoLibrary),
    Watch("watch", "Guarda", "Guarda", Icons.Filled.PlayCircle),
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

    val night = remember(profile.nightUrl) { NightData(NightApi(profile.nightUrl)) }
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val currentTab = Tab.entries.firstOrNull { it.route == route }
    // Mentre le barre spariscono con l'animazione mostrano ancora l'ultima scheda aperta.
    var shownTab by remember { mutableStateOf(Tab.Home) }
    if (currentTab != null) shownTab = currentTab
    // Telefono in orizzontale (o schermo largo): barra di navigazione laterale invece che in basso.
    val wide = LocalConfiguration.current.run { screenWidthDp > screenHeightDp }
    val goTab = { tab: Tab ->
        nav.navigate(tab.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

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
                AnimatedVisibility(currentTab != null && !wide, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Row(
                        Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(profile, 42.dp, onClick = { scope.launch { drawer.open() } })
                        Text(
                            shownTab.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 14.dp),
                        )
                    }
                }
            },
            bottomBar = {
                AnimatedVisibility(currentTab != null && !wide, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) { NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { goTab(tab) },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                } }
            },
        ) { padding ->
            val openShow = { id: Long -> nav.navigate("show/$id") }
            val openMovie = { id: Long -> nav.navigate("movie/$id") }
            val openGame = { id: Long -> nav.navigate("game/$id") }
            // extId ("igdb:123") nel percorso: i due punti diventano "_" e tornano al loro posto nella destinazione
            val openGamePreview = { extId: String -> nav.navigate("gamepreview/${extId.replace(':', '_')}") }
            val openPreview = { type: String, id: Int -> nav.navigate("preview/$type/$id") }
            // Le righe del cast aprono la pagina dell’attore da qualunque schermata di dettaglio.
            // In orizzontale la barra in alto non c'è: le schede principali lasciano spazio alla barra di stato.
            Row(Modifier.padding(padding).then(if (wide && currentTab != null) Modifier.statusBarsPadding() else Modifier)) {
            if (wide && currentTab != null) NavigationRail(header = { Avatar(profile, 42.dp, onClick = { scope.launch { drawer.open() } }) }) {
                Spacer(Modifier.weight(1f))
                Tab.entries.forEach { tab ->
                    NavigationRailItem(
                        selected = route == tab.route,
                        onClick = { goTab(tab) },
                        icon = { Icon(tab.icon, tab.label) },
                        label = { Text(tab.label) },
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            CompositionLocalProvider(LocalOpenPerson provides { id: Int -> nav.navigate("person/$id") }) {
                NavHost(
                    nav,
                    startDestination = Tab.Home.route,
                    modifier = Modifier.weight(1f),
                    // Come nell'app di Netflix: la scheda di un titolo sale dal basso sopra la pagina, che arretra e si scurisce;
                    // tornando indietro riscende. Le schede principali cambiano con una dissolvenza, il player sfuma dal nero.
                    enterTransition = {
                        when {
                            isPlayer(targetState) -> fadeIn(tween(350)) + scaleIn(tween(350), initialScale = 0.94f)
                            isTab(initialState) && isTab(targetState) -> fadeIn(tween(200))
                            else -> slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200))
                        }
                    },
                    exitTransition = {
                        when {
                            isPlayer(targetState) -> fadeOut(tween(350))
                            isTab(initialState) && isTab(targetState) -> fadeOut(tween(200))
                            else -> scaleOut(tween(420, easing = FastOutSlowInEasing), targetScale = 0.93f) + fadeOut(tween(420), targetAlpha = 0.4f)
                        }
                    },
                    popEnterTransition = {
                        if (isPlayer(initialState)) fadeIn(tween(350))
                        else scaleIn(tween(380, easing = FastOutSlowInEasing), initialScale = 0.93f) + fadeIn(tween(380), initialAlpha = 0.4f)
                    },
                    popExitTransition = {
                        if (isPlayer(initialState)) fadeOut(tween(350)) + scaleOut(tween(350), targetScale = 0.94f)
                        else slideOutVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(380))
                    },
                ) {
                    composable(Tab.Home.route) { HomeScreen(profile, openShow, openMovie, openPreview, openGame) }
                    composable(Tab.Search.route) { SearchScreen(openShow, openMovie, openPreview, openGame, openGamePreview) }
                    composable(Tab.Library.route) {
                        LibraryScreen(openShow, openMovie, openGame) {
                            nav.navigate(Tab.Search.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                    composable(Tab.Watch.route) {
                        WatchScreen(night, prefs, onOpen = { nav.navigate("watch/$it") }, onPlay = { nav.navigate("play/$it") })
                    }
                    composable("watch/m/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                        WatchMovieScreen(night, it.arguments?.getString("id").orEmpty(), { nav.popBackStack() }) { fid, fromStart -> nav.navigate("play/$fid?restart=$fromStart") }
                    }
                    composable("watch/s/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                        WatchSeriesScreen(night, it.arguments?.getString("id").orEmpty(), { nav.popBackStack() }) { nav.navigate("play/$it") }
                    }
                    composable(
                        "play/{fid}?restart={restart}",
                        listOf(navArgument("fid") { type = NavType.StringType }, navArgument("restart") { type = NavType.BoolType; defaultValue = false }),
                    ) {
                        val fid = it.arguments?.getString("fid").orEmpty()
                        PlayerScreen(
                            night, fid, it.arguments?.getBoolean("restart") == true, profile.subtitleMode,
                            onSubMode = { mode -> scope.launch { prefs.setSubtitleMode(mode) } },
                            onFinished = { night.syncToLibrary(repo, fid) },
                            // Cambio episodio: il nuovo player prende il posto di quello aperto, così Indietro torna alla scheda.
                            onPlay = { next -> nav.navigate("play/$next") { popUpTo("play/{fid}?restart={restart}") { inclusive = true } } },
                        ) { nav.popBackStack() }
                    }
                    composable(Tab.Stats.route) { StatsScreen(openShow) }
                    composable("settings") { SettingsScreen { nav.popBackStack() } }
                    composable("profile") { ProfileScreen(profile, prefs, { nav.popBackStack() }, openShow, openMovie, openGame) }
                    composable("person/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                        PersonScreen(it.arguments!!.getInt("id"), { nav.popBackStack() }, openShow, openMovie, openPreview)
                    }
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
                    composable("gamepreview/{extId}", listOf(navArgument("extId") { type = NavType.StringType })) {
                        GamePreviewScreen(it.arguments!!.getString("extId")!!.replaceFirst('_', ':'), { nav.popBackStack() }) { libId ->
                            nav.navigate("game/$libId") { popUpTo("gamepreview/{extId}") { inclusive = true } }
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
    }
}

private fun isTab(e: NavBackStackEntry) = Tab.entries.any { it.route == e.destination.route }
private fun isPlayer(e: NavBackStackEntry) = e.destination.route?.startsWith("play/") == true
