package io.github.akiig.moodlog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        Tab(tr("Log"), Icons.Filled.CalendarMonth),
        Tab(tr("Charts"), Icons.AutoMirrored.Outlined.ShowChart),
        Tab(tr("Dreams"), Icons.Filled.Bedtime),
        Tab(tr("Events"), Icons.Filled.Flag),
        Tab(tr("Settings"), Icons.Filled.Settings),
    )

    // after an update: "What's new" once. A brand-new install (nothing logged yet) gets the tour.
    val context = androidx.compose.ui.platform.LocalContext.current
    var whatsNewSince by remember { mutableStateOf<String?>(null) }
    var showWhatsNew by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val s = app.store
        val fresh = s.entries.isEmpty() && s.events.isEmpty() && s.dreams.isEmpty()
        val current = io.github.akiig.moodlog.BuildConfigVersion.name(context)
        val since = io.github.akiig.moodlog.WhatsNew.since(app.prefs.lastSeenVersion, current, app.prefs.tourDone || !fresh)
        app.prefs.lastSeenVersion = current
        if (!app.prefs.tourDone && fresh) tour.start()
        else if (since != null && !app.openToday) {
            // coming from a version that didn't record this: mark only this version as new
            whatsNewSince = if (since == "0") io.github.akiig.moodlog.WhatsNew.load(context).getOrNull(1)?.version ?: "0" else since
            showWhatsNew = true
        }
    }

    // the reminder notification was tapped: today's page, with a new entry open
    var addNow by remember { mutableStateOf(false) }
    LaunchedEffect(app.openToday) {
        if (app.openToday) {
            app.openToday = false
            tour.step = null
            tab = 0; showPrivacy = false
            openDay = io.github.akiig.moodlog.Day.today
            addNow = true
        }
    }

    BackHandler(enabled = openDay != null || showPrivacy || tab != 0) {
        when {
            showPrivacy -> showPrivacy = false
            openDay != null -> { openDay = null; addNow = false }
            else -> tab = 0
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                0 -> if (openDay != null) androidx.compose.runtime.key(openDay, addNow) {
                        DayScreen(openDay!!, startAdding = addNow) { openDay = null; addNow = false }
                     }
                     else LogScreen { addNow = false; openDay = it }
                1 -> ChartsScreen()
                2 -> DreamsScreen()
                3 -> EventsScreen()
                else -> if (showPrivacy) PrivacyScreen(back = tr("Settings")) { showPrivacy = false }
                        else SettingsScreen(onPrivacy = { showPrivacy = true },
                                            onWhatsNew = { whatsNewSince = null; showWhatsNew = true })
            }
            TabBar(tabs, tab, Modifier.align(Alignment.BottomCenter).onGloballyPositioned { tour.tabBar = it.boundsInRoot() }) {
                if (tab == it) { openDay = null; showPrivacy = false }
                tab = it
            }
            if (showWhatsNew) WhatsNewDialog(whatsNewSince) { showWhatsNew = false }
            if (tour.step != null) {
                TourOverlay(tour, onTab = { tab = it; openDay = null; showPrivacy = false }) {
                    tour.step = null
                    app.prefs.tourDone = true
                    tab = 0
                }
            }
        }
    }
}

/** The floating, rounded tab bar of the iPhone app. */
@Composable
private fun TabBar(tabs: List<Tab>, selected: Int, modifier: Modifier, onSelect: (Int) -> Unit) {
    Box(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
        .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(32.dp), ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.25f))
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            tabs.forEachIndexed { i, t ->
                val on = i == selected
                val ink by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, label = "ink")
                val pill by animateColorAsState(if (on) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent, label = "pill")
                Column(Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).background(pill)
                    .clickable { onSelect(i) }
                    .semantics { this.selected = on; role = Role.Tab }
                    .padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(t.icon, null, Modifier.size(24.dp), tint = ink)
                    FittingText(t.title, ink, if (on) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}

/** One line of text that gets smaller instead of being cut ("Impostazioni" with large text). */
@Composable
fun FittingText(text: String, color: Color, weight: FontWeight, start: TextUnit = 11.sp) {
    var size by remember(text) { mutableStateOf(start) }
    Text(text, color = color, fontSize = size, fontWeight = weight, maxLines = 1, softWrap = false,
        onTextLayout = { if (it.hasVisualOverflow && size.value > 7f) size = (size.value - 0.5f).sp })
}
