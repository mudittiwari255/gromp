package app.gromp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gromp.ui.components.AppIcon
import app.gromp.ui.components.GCard
import app.gromp.ui.components.GIcons
import app.gromp.ui.components.GSwitch
import app.gromp.ui.components.ListRow
import app.gromp.ui.components.LocalContainer
import app.gromp.ui.components.SectionTitle
import app.gromp.ui.components.SmallPill
import app.gromp.ui.components.rememberLoaded
import app.gromp.ui.theme.G
import app.gromp.util.Fmt
import app.gromp.util.Permissions

@Composable
fun FocusHoursScreen(onBack: () -> Unit, onChooseApps: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val focus by c.prefs.focus.collectAsState()
    val rules by remember { c.repository.rulesFlow() }.collectAsState(initial = emptyList())
    val lockOn = rememberLoaded { Permissions.isLockServiceOn(context) } ?: true
    var editing by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    var showDisclosure by remember { mutableStateOf(false) }
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(GIcons.Back, contentDescription = "Back", tint = G.Text) }
            Text("Focus hours", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Text)
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Choose a stretch of the day when certain apps rest. Opening one shows a calm screen instead. You can still hold for 5 minutes if you truly need it.",
                fontSize = 14.sp, lineHeight = 21.sp, color = G.Muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
            if (!lockOn && focus.enabled) LocksOffBanner { showDisclosure = true }

            GCard(padding = PaddingValues(horizontal = 20.dp, vertical = 6.dp), spacing = 0.dp) {
                ListRow(
                    title = "Focus hours",
                    subtitle = if (focus.enabled) "On" else "Off",
                    trailing = {
                        GSwitch(focus.enabled) {
                            c.prefs.setFocus(focus.copy(enabled = it))
                            if (it && !Permissions.isLockServiceOn(context)) showDisclosure = true
                        }
                    },
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(G.Line))
                ListRow("Starts", null, onClick = { editing = true }) {
                    Text(Fmt.clockFull(focus.startMinute), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = G.Sage)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(G.Line))
                ListRow("Ends", if (focus.endMinute <= focus.startMinute) "The next morning" else null, onClick = { editing = false }) {
                    Text(Fmt.clockFull(focus.endMinute), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = G.Sage)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(G.Line))
                Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("On these days", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Text)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        days.forEachIndexed { i, d ->
                            val on = focus.days and (1 shl i) != 0
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (on) G.Sage else Color.Transparent)
                                    .border(2.dp, if (on) G.Sage else G.Off, CircleShape)
                                    .clickable { c.prefs.setFocus(focus.copy(days = focus.days xor (1 shl i))) }
                                    .semantics { role = Role.Checkbox; selected = on; contentDescription = dayNames[i] },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(d, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (on) G.Bg else G.Muted)
                            }
                        }
                    }
                }
            }

            GCard(spacing = 4.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Apps that rest")
                    SmallPill("Choose", onChooseApps, filled = false)
                }
                val resting = rules.filter { it.focusBlocked }
                if (resting.isEmpty()) {
                    Text("None yet. Pick the apps that pull you in at this time of day.", fontSize = 13.sp, color = G.Muted)
                }
                resting.forEach { r ->
                    ListRow(c.catalog.label(r.pkg), leading = { AppIcon(r.pkg, 36.dp, 10.dp) })
                }
            }
        }
    }

    editing?.let { start ->
        TimeDialog(
            title = if (start) "Focus starts" else "Focus ends",
            minute = if (start) focus.startMinute else focus.endMinute,
            onDismiss = { editing = null },
        ) { m ->
            c.prefs.setFocus(if (start) focus.copy(startMinute = m) else focus.copy(endMinute = m))
            editing = null
        }
    }
    if (showDisclosure) LocksDisclosureDialog { showDisclosure = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, minute: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = G.Surface,
        title = { Text(title, fontWeight = FontWeight.ExtraBold, color = G.Text) },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = G.Surface2,
                    selectorColor = G.Sage,
                    timeSelectorSelectedContainerColor = G.SageBg,
                    timeSelectorSelectedContentColor = G.Sage,
                    timeSelectorUnselectedContainerColor = G.Surface2,
                    timeSelectorUnselectedContentColor = G.Text,
                    periodSelectorSelectedContainerColor = G.SageBg,
                    periodSelectorSelectedContentColor = G.Sage,
                    periodSelectorUnselectedContentColor = G.Muted,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("Save", color = G.Sage, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = G.Muted) } },
    )
}
