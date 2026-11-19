package io.github.akiig.moodlog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.akiig.moodlog.LocalApp
import io.github.akiig.moodlog.tr

private data class Tab(val title: String, val icon: ImageVector)

@Composable
fun MoodlogApp() {
    val app = LocalApp.current
    val tour = app.tour
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openDay by rememberSaveable { mutableStateOf<String?>(null) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }

    val tabs = listOf(
        Tab(tr("Log"), Icons.Outlined.CalendarMonth),
        Tab(tr("Charts"), Icons.AutoMirrored.Outlined.ShowChart),
        Tab(tr("Dreams"), Icons.Outlined.Bedtime),
        Tab(tr("Events"), Icons.Outlined.Flag),
        Tab(tr("Settings"), Icons.Outlined.Settings),
    )

    // a brand-new install (nothing logged yet): show the welcome tour once
    LaunchedEffect(Unit) {
        val s = app.store
        if (!app.prefs.tourDone && s.entries.isEmpty() && s.events.isEmpty() && s.dreams.isEmpty()) tour.start()
    }

    BackHandler(enabled = openDay != null || showPrivacy || tab != 0) {
        when {
            showPrivacy -> showPrivacy = false
            openDay != null -> openDay = null
            else -> tab = 0
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                NavigationBar(Modifier.onGloballyPositioned { tour.tabBar = it.boundsInRoot() }) {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = {
                                if (tab == i) { openDay = null; showPrivacy = false }
                                tab = i
                            },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = {
                                Text(t.title, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible,
                                    style = MaterialTheme.typography.labelSmall)
                            },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    0 -> if (openDay != null) DayScreen(openDay!!) { openDay = null }
                         else LogScreen { openDay = it }
                    1 -> ChartsScreen()
                    2 -> DreamsScreen()
                    3 -> EventsScreen()
                    else -> if (showPrivacy) PrivacyScreen { showPrivacy = false }
                            else SettingsScreen(onPrivacy = { showPrivacy = true })
                }
            }
        }
        if (tour.step != null) {
            TourOverlay(tour, onTab = { tab = it; openDay = null; showPrivacy = false }) {
                tour.step = null
                app.prefs.tourDone = true
                tab = 0
            }
        }
    }
}
