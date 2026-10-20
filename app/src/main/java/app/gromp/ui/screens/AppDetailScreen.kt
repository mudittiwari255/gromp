package app.gromp.ui.screens

import app.gromp.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gromp.data.db.LockRule
import app.gromp.ui.components.AppIcon
import app.gromp.ui.components.GCard
import app.gromp.ui.components.GIcons
import app.gromp.ui.components.GSwitch
import app.gromp.ui.components.Kicker
import app.gromp.ui.components.ListRow
import app.gromp.ui.components.LocalContainer
import app.gromp.ui.components.MeterBar
import app.gromp.ui.components.SecondaryButton
import app.gromp.ui.components.SectionTitle
import app.gromp.ui.components.Sparkline
import app.gromp.ui.components.TrendLine
import app.gromp.ui.components.rememberLoaded
import app.gromp.ui.theme.G
import app.gromp.util.Fmt
import app.gromp.util.Permissions
import kotlinx.coroutines.launch

@Composable
fun AppDetailScreen(pkg: String, onBack: () -> Unit, onFocusHours: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val data = rememberLoaded(pkg, version) { c.repository.loadApp(pkg) }
    val lockOn = rememberLoaded(version) { Permissions.isLockServiceOn(context) } ?: true
    var showLimit by remember { mutableStateOf(false) }
    var showDisclosure by remember { mutableStateOf(false) }
    val label = remember(pkg) { c.catalog.label(pkg) }

    fun save(rule: LockRule) {
        scope.launch {
            c.repository.saveRule(rule)
            version++
            if (!Permissions.isLockServiceOn(context) && (rule.pauseEnabled || rule.focusBlocked || rule.dailyLimitMin != null)) {
                showDisclosure = true
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(GIcons.Back, contentDescription = "Back", tint = G.Text) }
            Text("App detail", modifier = Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Text)
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppIcon(pkg, size = 64.dp, radius = 20.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = G.Text)
                if (data != null) Text("${data.category.label} · ${data.shareOfToday}% of today", fontSize = 13.sp, color = G.Muted)
            }
        }
        if (data == null) return@Column
        val rule = data.rule ?: LockRule(pkg)

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GCard(spacing = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Kicker("Today")
                    Text(Fmt.duration(data.todayMs), fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = G.Text, letterSpacing = (-1.2).sp)
                    TrendLine(data.usualMs?.let { data.todayMs - it }, suffix = "than your daily average")
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(G.Line))
                Row(Modifier.fillMaxWidth()) {
                    Mini(Modifier.weight(1f), "${data.opens}", "opens")
                    Mini(Modifier.weight(1f), Fmt.durationSeconds(data.avgSessionMs), "avg session")
                    Mini(Modifier.weight(1f), Fmt.duration(data.longestMs), "longest")
                }
            }

            if (data.opens > 0) {
                GCard(spacing = 14.dp) {
                    SectionTitle("How long each visit lasted")
                    val labels = listOf("Under 1 min", "1–5 min", "5–15 min", "Over 15 min")
                    val colors = listOf(G.SageMid, G.Sage, G.Amber, G.Coral)
                    val max = data.buckets.maxOrNull()?.coerceAtLeast(1) ?: 1
                    labels.forEachIndexed { i, l ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(l, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = G.Text)
                                Text("${data.buckets[i]} visits", fontSize = 14.sp, color = G.Muted)
                            }
                            MeterBar(data.buckets[i].toFloat() / max, colors[i], height = 10.dp)
                        }
                    }
                    if (data.todayMs > 0 && data.longestMs * 2 > data.todayMs) {
                        Text(
                            "One long visit made up ${data.longestMs * 100 / data.todayMs}% of today's time here.",
                            fontSize = 13.sp, lineHeight = 19.sp, color = G.Muted,
                        )
                    }
                }
            }

            GCard(spacing = 12.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    SectionTitle("Last 30 days")
                    Text("avg ${Fmt.duration(data.avgMs)}", fontSize = 13.sp, color = G.Muted)
                }
                Sparkline(data.series, data.avgMs, G.Amber, description = "Daily time in $label over the last 30 days")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Fmt.shortDate(data.seriesStart), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = G.Faint)
                    Text("Today", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = G.Faint)
                }
            }

            if (!BuildConfig.LOCKS) LiteEditionCard()
            if (BuildConfig.LOCKS && !lockOn && data.rule != null) LocksOffBanner { showDisclosure = true }

            if (BuildConfig.LOCKS) GCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp), spacing = 0.dp) {
                ListRow(
                    title = "Daily limit",
                    subtitle = "A gentle nudge, not a lock",
                    trailing = {
                        SecondaryButton(rule.dailyLimitMin?.let { "${Fmt.duration(it * 60_000L)} · Edit" } ?: "Set", { showLimit = true })
                    },
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(G.Line))
                ListRow(
                    title = "Breathe before opening",
                    subtitle = "A 5-second pause screen each time",
                    trailing = { GSwitch(rule.pauseEnabled) { save(rule.copy(pauseEnabled = it)) } },
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(G.Line))
                ListRow(
                    title = "Rest during focus hours",
                    subtitle = if (c.prefs.focus.value.enabled) "Blocked ${Fmt.clockFull(c.prefs.focus.value.startMinute)} to ${Fmt.clockFull(c.prefs.focus.value.endMinute)}"
                    else "Set your focus hours in Settings",
                    trailing = { GSwitch(rule.focusBlocked) { save(rule.copy(focusBlocked = it)) } },
                )
                if (rule.focusBlocked && !c.prefs.focus.value.enabled) {
                    Text(
                        "Choose focus hours", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = G.Sage,
                        modifier = Modifier.padding(bottom = 12.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onFocusHours).padding(vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (showLimit) {
            LimitDialog(rule.dailyLimitMin, onDismiss = { showLimit = false }) { min ->
                showLimit = false
                save(rule.copy(dailyLimitMin = min))
            }
        }
    }
    if (showDisclosure) LocksDisclosureDialog { showDisclosure = false; version++ }
}

@Composable
private fun Mini(modifier: Modifier, value: String, label: String) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = G.Text)
        Text(label, fontSize = 12.sp, color = G.Muted)
    }
}

@Composable
private fun LimitDialog(current: Int?, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    val options = listOf<Int?>(15, 30, 45, 60, 90, 120, 180, null)
    var choice by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = G.Surface,
        title = { Text("Daily limit", fontWeight = FontWeight.ExtraBold, color = G.Text) },
        text = {
            Column {
                Text("When you reach it, Gromp shows a calm stop screen. You can always hold for 5 more minutes.", fontSize = 14.sp, color = G.Muted)
                Spacer(Modifier.height(8.dp))
                options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable { choice = o },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == o, onClick = { choice = o }, colors = RadioButtonDefaults.colors(selectedColor = G.Sage))
                        Text(o?.let { Fmt.duration(it * 60_000L) } ?: "No limit", fontSize = 15.sp, color = G.Text)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(choice) }) { Text("Save", color = G.Sage, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = G.Muted) } },
    )
}
