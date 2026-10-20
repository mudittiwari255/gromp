package app.gromp.ui.screens

import app.gromp.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gromp.data.Insight
import app.gromp.data.InsightsData
import app.gromp.data.Tone
import app.gromp.ui.components.Dots
import app.gromp.ui.components.GCard
import app.gromp.ui.components.Kicker
import app.gromp.ui.components.LocalContainer
import app.gromp.ui.components.PrivacyFooter
import app.gromp.ui.components.SectionTitle
import app.gromp.ui.components.SmallPill
import app.gromp.ui.components.rememberLoaded
import app.gromp.ui.theme.G
import app.gromp.util.Fmt

@Composable
fun InsightsScreen(onFocusHours: () -> Unit, onReview: () -> Unit) {
    val c = LocalContainer.current
    val data = rememberLoaded { c.repository.loadInsights() }
    val spots = rememberLoaded { c.smart.weakSpots() }.orEmpty()
    val scores = rememberLoaded { c.smart.worthScores() }.orEmpty()
    val voice = c.prefs.smart.collectAsState().value.voice
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Insights", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = G.Text)
            Text("Patterns from your last 30 days, worked out on this phone.", fontSize = 14.sp, color = G.Muted)
        }
        if (data == null) return@Column
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReviewTeaser(onReview)
            data.featured?.let { f -> Featured(f, data) }
            if (spots.isNotEmpty()) WeakSpotsCard(spots, c.catalog::label, voice, if (BuildConfig.LOCKS) onFocusHours else null)
            if (scores.isNotEmpty()) WorthScoresCard(scores, c.catalog::label, voice)
            data.cards.forEach { InsightRow(it, onFocusHours) }
            if (data.featured == null && data.cards.isEmpty()) {
                GCard(spacing = 6.dp) {
                    Text("Not much to say yet.", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = G.Text)
                    Text("Insights appear after a day or two of use. Gromp only ever compares you with you.", fontSize = 13.sp, color = G.Muted)
                }
            }
            Goal(data)
        }
        PrivacyFooter("Insights come from simple on-device rules over your own history. Nothing is uploaded or shared.")
    }
}

@Composable
private fun Featured(f: Insight, d: InsightsData) {
    GCard(background = G.SageBg, border = G.SageLine, padding = PaddingValues(horizontal = 20.dp, vertical = 22.dp), spacing = 14.dp) {
        Kicker(f.kicker, G.Sage)
        Text(f.title, fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold, color = G.Text)
        d.featuredDots?.let { (short, total) -> Dots(short, total, "$short short visits out of $total") }
        Text(f.body, fontSize = 14.sp, lineHeight = 21.sp, color = G.SageSoft)
    }
}

private fun Tone.colors(): Pair<Color, Color> = when (this) {
    Tone.Sage -> G.SageBg to G.Sage
    Tone.Amber -> Color(0xFF2B2418) to G.Amber
    Tone.Blue -> Color(0xFF1B2430) to G.Blue
    Tone.Lilac -> Color(0xFF241F2E) to G.Lilac
}

@Composable
private fun InsightRow(i: Insight, onFocusHours: () -> Unit) {
    val (bg, fg) = i.tone.colors()
    GCard(radius = 24.dp, padding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
                Text(i.badge, fontSize = if (i.badge.length > 4) 12.sp else 15.sp, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(i.kicker.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = G.Muted)
                Text(i.title, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, color = G.Text)
                Text(i.body, fontSize = 13.sp, lineHeight = 19.sp, color = G.Muted)
                if (i.suggestsFocus && BuildConfig.LOCKS) SmallPill("Set focus hours", onFocusHours, filled = false)
            }
        }
    }
}

@Composable
private fun Goal(d: InsightsData) {
    val met = d.goalDays.count { it.met == true }
    val counted = d.goalDays.count { it.met != null }
    GCard(spacing = 14.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            SectionTitle("Under ${Fmt.duration(d.goalMin * 60_000L)} a day")
            Text("$met of $counted days", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = G.Sage)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            d.goalDays.forEach { g ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.semantics {
                        contentDescription = when (g.met) { true -> "Under limit"; false -> "Over limit"; null -> "No data" }
                    },
                ) {
                    val base = Modifier.size(36.dp).clip(CircleShape)
                    val circle = when {
                        g.met == true -> base.background(G.Sage)
                        g.isToday -> base.border(2.dp, G.Sage, CircleShape)
                        else -> base.border(2.dp, G.Off, CircleShape)
                    }
                    Box(circle, contentAlignment = Alignment.Center) {
                        if (g.met == true) Text("✓", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = G.Bg)
                    }
                    Text(g.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = G.Muted)
                }
            }
        }
        Text("No streaks to break. A filled circle means you stayed under your limit that day.", fontSize = 13.sp, lineHeight = 19.sp, color = G.Muted)
    }
}
