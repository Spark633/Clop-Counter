package de.clopcounter.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.clopcounter.ui.screens.CalendarScreen
import de.clopcounter.ui.screens.DayScreen
import de.clopcounter.ui.screens.EntryDetailScreen
import de.clopcounter.ui.screens.HistoryScreen
import de.clopcounter.ui.screens.HomeScreen
import de.clopcounter.ui.screens.StatsScreen
import java.time.LocalDate

private enum class TopDestination(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Heute", Icons.Filled.Today),
    Calendar("calendar", "Kalender", Icons.Filled.CalendarMonth),
    History("history", "Verlauf", Icons.Filled.History),
    Stats("stats", "Statistik", Icons.Filled.BarChart),
}

@Composable
fun ClopNavigation(vm: ClopViewModel = viewModel()) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = TopDestination.entries.any { it.route == currentRoute }

    val openEntry: (Long) -> Unit = { nav.navigate("entry/$it") }
    val openDay: (LocalDate) -> Unit = { nav.navigate("day/${it.toEpochDay()}") }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopDestination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = { nav.navigateTopLevel(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = TopDestination.Home.route, modifier = Modifier.padding(padding)) {
            composable(TopDestination.Home.route) { HomeScreen(vm, onOpenEntry = openEntry) }
            composable(TopDestination.Calendar.route) { CalendarScreen(vm, onOpenDay = openDay) }
            composable(TopDestination.History.route) {
                HistoryScreen(vm, onOpenEntry = openEntry, onOpenDay = openDay)
            }
            composable(TopDestination.Stats.route) { StatsScreen(vm, onOpenDay = openDay) }
            composable(
                "day/{epochDay}",
                arguments = listOf(navArgument("epochDay") { type = NavType.LongType }),
            ) {
                DayScreen(
                    vm,
                    initialDate = LocalDate.ofEpochDay(it.arguments!!.getLong("epochDay")),
                    onBack = { nav.popBackStack() },
                    onOpenEntry = openEntry,
                )
            }
            composable(
                "entry/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                EntryDetailScreen(vm, entryId = it.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
