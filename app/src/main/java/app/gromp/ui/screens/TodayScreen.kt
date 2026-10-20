package app.gromp.ui.screens

import app.gromp.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gromp.data.Insight
import app.gromp.data.TodayData
import app.gromp.ui.components.AppIcon
import app.gromp.ui.components.AxisLabels
import app.gromp.ui.components.GCard
import app.gromp.ui.components.GIcons
import app.gromp.ui.components.GoalRing
import app.gromp.ui.components.HourBars
import app.gromp.ui.components.Kicker
import app.gromp.ui.components.LocalContainer
import app.gromp.ui.components.MeterBar
import app.gromp.ui.components.PrivacyFooter
import app.gromp.ui.components.SectionTitle
import app.gromp.ui.components.SmallPill
import app.gromp.ui.components.TrendLine
import app.gromp.ui.components.rememberLoaded
import app.gromp.ui.components.tileColor
import app.gromp.ui.theme.G
import app.gromp.util.Fmt
import kotlin.math.abs

@Composable
fun TodayScreen(onOpenApp: (String) -> Unit, onSeeTrends: () -> Unit, onFocusHours: () -> Unit) {
    val c = LocalContainer.current
    val data = rememberLoaded { c.repository.loadToday() }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding(),
    ) {
        if (data == null) {
            LoadingHeader()
            return@Column
        }
        Header(data)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(4.dp))
            Hero(data)
            Kpis(data)
            HourCard(data)
            if (data.apps.isNotEmpty()) TopApps(data, onOpenApp, onSeeTrends)
            data.insight?.let { InsightCard(it, onFocusHours) }
        }
        PrivacyFooter("Gromp has no internet permission. Everything here was counted and stored on this phone.")
    }
}

@Composable
private fun LoadingHeader() {
    Column(Modifier.padding(20.dp)) {
        Text("Counting your day…", fontSize = 15.sp, color = G.Muted)
    }
}

@Composable
private fun Header(d: TodayData) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Kicker(Fmt.longDate(d.date))
            Text(Fmt.greeting(d.nowMinute / 60), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = G.Text, letterSpacing = (-0.5).sp)
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(G.Surface)
                .border(1.dp, G.Line, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(GIcons.Lock, contentDescription = null, tint = G.Sage, modifier = Modifier.size(14.dp))
            Text("On-device", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = G.Muted)
        }
    }
}

@Composable
private fun Hero(d: TodayData) {
    val fraction = if (d.goalMs > 0) d.totalMs.toFloat() / d.goalMs else 0f
    val over = d.totalMs > d.goalMs
    GCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 24.dp), spacing = 18.dp) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GoalRing(
                fraction = fraction,
                accent = if (over) G.Amber else G.Sage,
                description = "${Fmt.duration(d.totalMs)} of a ${Fmt.duration(d.goalMs)} daily limit used",
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Kicker("Screen time")
                Text(Fmt.duration(d.totalMs), fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, color = G.Text, letterSpacing = (-1.2).sp)
                Text(
                    if (over) "${Fmt.duration(d.totalMs - d.goalMs)} past your ${Fmt.duration(d.goalMs)} limit" else "of your ${Fmt.duration(d.goalMs)} limit",
                    fontSize = 13.sp, color = G.Muted,
                )
            }
        }
        val usual = d.usualSoFarMs
        val (text, up) = when {
            usual == null -> "Gromp is learning your usual day" to null
            abs(d.totalMs - usual) < 5 * 60_000 -> "Right on pace with your ${d.usualLabel}" to null
            d.totalMs < usual -> "${Fmt.minutes(usual - d.totalMs)} less than your ${d.usualLabel}" to false
            else -> "${Fmt.minutes(d.totalMs - usual)} more than your ${d.usualLabel}" to true
        }
        Row(
            Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(14.dp))
                .background(if (up == true) G.Amber.copy(alpha = 0.14f) else G.SageBg)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val color = if (up == true) G.Amber else G.SageLight
            if (up != null) Icon(if (up) GIcons.Up else GIcons.Down, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
private fun Kpis(d: TodayData) {
    val c = LocalContainer.current
    val items = listOf(
        Triple("Pickups", "${d.pickups}", d.avgPickups?.let { "Average ${it} a day" } ?: "Unlocks so far today"),
        Triple(
            "First pickup",
            d.firstPickupMinute?.let { Fmt.clock(it) } ?: "—",
            d.firstPickupMinute?.let { first ->
                val usual = d.usualFirstPickupMinute
                val diff = if (usual == null) 0 else first - usual
                Fmt.amPm(first) + when {
                    usual == null || abs(diff) < 5 -> ""
                    diff > 0 -> " · $diff min later than usual"
                    else -> " · ${-diff} min earlier than usual"
                }
            } ?: "Not yet",
        ),
        Triple(
            "Longest session",
            if (d.longestMs > 0) Fmt.duration(d.longestMs) else "—",
            d.longestPkg?.let { pkg -> c.catalog.label(pkg) + (d.longestStart?.let { ", from ${Fmt.timeOf(it)}" } ?: "") } ?: "Nothing yet",
        ),
        Triple(
            "Notifications",
            d.notifications?.toString() ?: "—",
            when {
                d.notifications == null -> if (BuildConfig.LOCKS) "Turn on in Settings" else "Full edition only"
                d.topNotifPkg != null -> "${c.catalog.label(d.topNotifPkg)} sent ${d.topNotifCount}"
                else -> "Quiet so far"
            },
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value, sub) ->
                    GCard(Modifier.weight(1f).heightIn(min = 104.dp), radius = 22.dp, padding = androidx.compose.foundation.layout.PaddingValues(16.dp), spacing = 6.dp) {
                        Kicker(label)
                        Text(value, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = G.Text)
                        Text(sub, fontSize = 12.sp, lineHeight = 16.sp, color = G.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun HourCard(d: TodayData) {
    val hour = d.nowMinute / 60
    val peak = d.hourly.indices.maxByOrNull { d.hourly[it] }?.takeIf { d.hourly[it] > 0 }
    GCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            SectionTitle("Your day, hour by hour")
            if (peak != null) Text("Peak ${Fmt.clock(peak * 60)}–${Fmt.clockFull((peak + 1) * 60)}", fontSize = 12.sp, color = G.Muted)
        }
        Column {
            HourBars(d.hourly, hour, G.Sage)
            AxisLabels(listOf("12 am", "6 am", "12 pm", "6 pm", "11 pm"), Modifier.padding(top = 8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(filled = true, "Used")
            Legend(filled = false, "Still to come")
        }
    }
}

@Composable
private fun Legend(filled: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(10.dp).clip(RoundedCornerShape(3.dp))
                .then(if (filled) Modifier.background(G.Sage) else Modifier.border(1.dp, G.Dash, RoundedCornerShape(3.dp))),
        )
        Text(text, fontSize = 12.sp, color = G.Muted)
    }
}

@Composable
private fun TopApps(d: TodayData, onOpenApp: (String) -> Unit, onSeeAll: () -> Unit) {
    val c = LocalContainer.current
    val max = d.apps.maxOf { it.ms }.coerceAtLeast(1L)
    GCard(spacing = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Where the time went")
            Text(
                "See all", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = G.Sage,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSeeAll).padding(8.dp),
            )
        }
        d.apps.forEach { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenApp(a.pkg) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIcon(a.pkg)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(c.catalog.label(a.pkg), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Text, maxLines = 1)
                        Text(Fmt.duration(a.ms), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Text)
                    }
                    MeterBar(a.ms.toFloat() / max, tileColor(a.pkg))
                    TrendLine(a.usualMs?.let { a.ms - it })
                }
            }
        }
    }
}

@Composable
fun InsightCard(i: Insight, onFocusHours: () -> Unit) {
    GCard(background = G.SageBg, border = G.SageLine, spacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(GIcons.Insights, contentDescription = null, tint = G.Sage, modifier = Modifier.size(16.dp))
            Kicker(i.kicker, G.Sage)
        }
        Text(i.title, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = G.Text)
        Text(i.body, fontSize = 14.sp, lineHeight = 21.sp, color = G.SageSoft)
        if (i.suggestsFocus && BuildConfig.LOCKS) {
            Spacer(Modifier.height(2.dp))
            SmallPill("Set focus hours", onFocusHours, filled = true)
        }
    }
}
