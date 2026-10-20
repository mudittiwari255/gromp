package app.gromp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gromp.BuildConfig
import app.gromp.data.Category
import app.gromp.data.SmartSettings
import app.gromp.smart.Trade
import app.gromp.smart.Voice
import app.gromp.ui.components.GCard
import app.gromp.ui.components.GIcons
import app.gromp.ui.components.GSwitch
import app.gromp.ui.components.Kicker
import app.gromp.ui.components.ListRow
import app.gromp.ui.components.LocalContainer
import app.gromp.ui.components.rememberLoaded
import app.gromp.ui.theme.G
import app.gromp.util.Fmt

@Composable
fun SmartSettingsScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val s by c.prefs.smart.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val canNotify = rememberLoaded(refresh) { c.notifier.canPost() } ?: true
    var floorDialog by remember { mutableStateOf(false) }
    fun update(f: (SmartSettings) -> SmartSettings) = c.prefs.updateSmart(f)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(GIcons.Back, contentDescription = "Back", tint = G.Text) }
            Text("Smart nudges", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Text)
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!canNotify) NotificationsCard { refresh++ }

            Label("How should Gromp talk to you?")
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Voice.entries.forEach { v ->
                    val on = v == s.voice
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (on) G.SageBg else G.Surface)
                            .border(1.dp, if (on) G.Sage else G.Line, RoundedCornerShape(20.dp))
                            .selectable(selected = on, role = Role.RadioButton) { update { it.copy(voice = v) } }
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(v.label, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (on) G.Sage else G.Text)
                        Text(v.blurb, fontSize = 13.sp, color = G.Muted)
                        Text("Binge check-in after ${v.bingeAfterMin} minutes", fontSize = 12.sp, color = G.Faint)
                    }
                }
            }

            Label("What would you rather do with your time?")
            Text("Gromp uses these to show what your screen time really cost.", fontSize = 13.sp, color = G.Muted, modifier = Modifier.padding(horizontal = 4.dp))
            Chips(Trade.entries.map { it.label }, s.trades.map { it.label }.toSet()) { label ->
                val t = Trade.entries.first { it.label == label }
                update { it.copy(trades = if (t in it.trades) it.trades - t else it.trades + t) }
            }

            Label("Which apps pull you in?")
            Text("Check-ins and worth-it questions apply to apps in these categories.", fontSize = 13.sp, color = G.Muted, modifier = Modifier.padding(horizontal = 4.dp))
            Chips(Category.entries.map { it.label }, s.pullCategories.map { it.label }.toSet()) { label ->
                val cat = Category.entries.first { it.label == label }
                update { it.copy(pullCategories = if (cat in it.pullCategories) it.pullCategories - cat else it.pullCategories + cat) }
            }

            Label("Check-ins while you use an app")
            GCard(padding = PaddingValues(horizontal = 20.dp, vertical = 6.dp), spacing = 0.dp) {
                if (!BuildConfig.LOCKS) {
                    Text(
                        "These need the Full edition, because they work through the locks service.",
                        fontSize = 13.sp, color = G.Muted, modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                Toggle("Ask what you're there for", "Before opening a pull app. Gromp checks back if you drift.", s.intentions, BuildConfig.LOCKS) {
                    update { s2 -> s2.copy(intentions = it) }
                }
                Divider()
                Toggle("Binge check-ins", "After ${s.voice.bingeAfterMin} minutes straight in one app.", s.bingeChecks, BuildConfig.LOCKS) {
                    update { s2 -> s2.copy(bingeChecks = it) }
                }
                Divider()
                Toggle("Scroll-loop check-ins", "When you hop between 3+ apps within a few minutes.", s.loopChecks, BuildConfig.LOCKS) {
                    update { s2 -> s2.copy(loopChecks = it) }
                }
            }

            Label("Learning and notifications")
            GCard(padding = PaddingValues(horizontal = 20.dp, vertical = 6.dp), spacing = 0.dp) {
                Toggle("Was it worth it?", "Asks after sessions of 10+ minutes.", s.worthPrompts, true) { update { s2 -> s2.copy(worthPrompts = it) } }
                Divider()
                Toggle("Weak-spot heads-ups", "A nudge 10 to 45 minutes before your usual slump.", s.headsUps, true) { update { s2 -> s2.copy(headsUps = it) } }
                Divider()
                Toggle("Sunday review", "Your week in review, Sunday at 7 pm.", s.weeklyReview, true) { update { s2 -> s2.copy(weeklyReview = it) } }
            }

            Label("Goals that taper")
            GCard(padding = PaddingValues(horizontal = 20.dp, vertical = 6.dp), spacing = 0.dp) {
                Toggle(
                    "Lower my limit after good weeks",
                    "Stay under your limit on most days and it drops by about 7% the next week. Bad weeks never make it stricter.",
                    s.taper, true,
                ) { update { s2 -> s2.copy(taper = it) } }
                if (s.taper) {
                    Divider()
                    ListRow(
                        title = "Never below",
                        subtitle = "The lowest your limit will go",
                        onClick = { floorDialog = true },
                        trailing = { Text(Fmt.duration(s.taperFloorMin * 60_000L), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Sage) },
                    )
                }
            }
            Box(Modifier.height(16.dp))
        }
    }

    if (floorDialog) {
        LimitDialog(s.taperFloorMin, onDismiss = { floorDialog = false }) { m ->
            floorDialog = false
            if (m != null) update { it.copy(taperFloorMin = m) }
        }
    }
}

@Composable
private fun Label(text: String) {
    Box(Modifier.padding(start = 4.dp, top = 8.dp)) { Kicker(text) }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(G.Line))

@Composable
private fun Toggle(title: String, subtitle: String, value: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = value, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (enabled) G.Text else G.Faint)
            Text(subtitle, fontSize = 13.sp, lineHeight = 18.sp, color = G.Muted)
        }
        GSwitch(value && enabled) { if (enabled) onChange(it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(all: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        all.forEach { label ->
            val on = label in selected
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (on) G.Sage else Color.Transparent)
                    .border(1.dp, if (on) G.Sage else G.Off, RoundedCornerShape(22.dp))
                    .toggleable(value = on, role = Role.Checkbox) { onToggle(label) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) G.Bg else G.Text)
            }
        }
    }
}
