package app.gromp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gromp.data.Category
import app.gromp.data.TrendRange
import app.gromp.data.TrendsData
import app.gromp.ui.components.Bar
import app.gromp.ui.components.BarChart
import app.gromp.ui.components.GCard
import app.gromp.ui.components.HeatLegend
import app.gromp.ui.components.Heatmap
import app.gromp.ui.components.Kicker
import app.gromp.ui.components.LocalContainer
import app.gromp.ui.components.SectionTitle
import app.gromp.ui.components.StackedBar
import app.gromp.ui.components.rememberLoaded
import app.gromp.ui.theme.G
import app.gromp.util.Fmt

@Composable
fun TrendsScreen() {
    val c = LocalContainer.current
    var range by rememberSaveable { mutableStateOf(TrendRange.Week) }
    val data = rememberLoaded(range) { c.repository.loadTrends(range) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Trends", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = G.Text)
            RangePicker(range) { range = it }
        }
        if (data == null) return@Column
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (data.daysWithData < 2) {
                GCard(background = G.SageBg, border = G.SageLine, spacing = 6.dp) {
                    Text("Gromp is still collecting your history.", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = G.Text)
                    Text("Android only keeps a few days of detail, so Gromp saves a copy every hour. Trends fill in as the days go by.",
                        fontSize = 13.sp, lineHeight = 19.sp, color = G.SageSoft)
                }
            }
            DailyCard(data)
            Summary(data)
            GCard(spacing = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionTitle("When you reach for it")
                    Text("Last 7 days. Each square is one hour. Brighter means more minutes.", fontSize = 13.sp, color = G.Muted)
                }
                Heatmap(
                    rows = data.heat.map { it.label to it.hours },
                    futureFrom = data.heat.mapIndexedNotNull { i, r -> r.futureFrom?.let { i to it } }.toMap(),
                )
                HeatLegend()
            }
            if (data.categories.isNotEmpty()) Categories(data)
        }
    }
}

@Composable
private fun RangePicker(range: TrendRange, onPick: (TrendRange) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(G.Surface)
            .border(1.dp, G.Line, RoundedCornerShape(50))
            .padding(4.dp)
            .selectableGroup(),
    ) {
        TrendRange.entries.forEach { r ->
            val on = r == range
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (on) G.Sage else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onPick(r) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(r.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) G.Bg else G.Muted)
            }
        }
    }
}

@Composable
private fun DailyCard(d: TrendsData) {
    GCard(spacing = 18.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Kicker("Daily average")
                Text(Fmt.duration(d.avgMs), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = G.Text, letterSpacing = (-1).sp)
            }
            d.prevAvgMs?.let { prev ->
                val diff = d.avgMs - prev
                val less = diff < 0
                val label = when (d.range) { TrendRange.Week -> "last week"; TrendRange.Month -> "last month"; TrendRange.Year -> "last year" }
                Text(
                    "${if (less) "↓" else "↑"} ${Fmt.duration(kotlin.math.abs(diff))} vs $label",
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (less) G.SageBg else G.Amber.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (less) G.SageLight else G.Amber,
                )
            }
        }
        BarChart(
            bars = d.bars.map { Bar(it.label, it.ms, if (d.range == TrendRange.Week && it.hasData) Fmt.duration(it.ms) else "", it.highlight, it.hasData) },
            avg = d.avgMs,
            showTop = d.range == TrendRange.Week,
        )
    }
}

@Composable
private fun Summary(d: TrendsData) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat(Modifier.weight(1f), "Calmest", d.calmest?.first ?: "—", d.calmest?.let { Fmt.duration(it.second) } ?: "Need more days")
        Stat(Modifier.weight(1f), "Heaviest", d.heaviest?.first ?: "—", d.heaviest?.let { Fmt.duration(it.second) } ?: "")
        val pickDiff = d.prevAvgPickups?.let { d.avgPickups - it }
        Stat(
            Modifier.weight(1f), "Pickups/day", "${d.avgPickups}",
            when {
                pickDiff == null -> "so far"
                pickDiff < 0 -> "↓ ${-pickDiff} vs before"
                pickDiff > 0 -> "↑ $pickDiff vs before"
                else -> "Same as before"
            },
        )
    }
}

@Composable
private fun Stat(modifier: Modifier, label: String, value: String, sub: String) {
    GCard(modifier, radius = 20.dp, padding = PaddingValues(14.dp), spacing = 4.dp) {
        Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = G.Muted, maxLines = 1)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = G.Text, maxLines = 1)
        Text(sub, fontSize = 12.sp, color = G.Muted, maxLines = 1)
    }
}

private fun Category.color(): Color = when (this) {
    Category.Social -> G.Amber
    Category.Video -> G.Coral
    Category.Messaging -> G.Sage
    Category.Browsing -> G.Blue
    Category.Games -> G.Lilac
    Category.Music -> Color(0xFFE6D58C)
    Category.Productivity -> Color(0xFF8FC9C4)
    Category.Other -> G.Grey
}

@Composable
private fun Categories(d: TrendsData) {
    GCard(spacing = 16.dp) {
        SectionTitle("What kind of time")
        StackedBar(
            d.categories.map { it.pct.toFloat() to it.category.color() },
            description = d.categories.joinToString { "${it.category.label} ${it.pct} percent" },
        )
        Column {
            d.categories.forEach { cat ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(cat.category.color()))
                    Text(cat.category.label, modifier = Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = G.Text)
                    Text("${Fmt.duration(cat.msPerDay)}/day", fontSize = 13.sp, color = G.Muted)
                    Text("${cat.pct}%", modifier = Modifier.width(44.dp), textAlign = TextAlign.End, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = G.Text)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF23272C)))
            }
        }
    }
}
