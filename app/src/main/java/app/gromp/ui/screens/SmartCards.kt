package app.gromp.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gromp.data.TodaySmart
import app.gromp.smart.LongSession
import app.gromp.smart.Voice
import app.gromp.smart.WeakSpot
import app.gromp.smart.WeakSpots
import app.gromp.smart.Worth
import app.gromp.smart.WorthScore
import app.gromp.ui.components.AppIcon
import app.gromp.ui.components.GCard
import app.gromp.ui.components.GIcons
import app.gromp.ui.components.Kicker
import app.gromp.ui.components.MeterBar
import app.gromp.ui.components.SectionTitle
import app.gromp.ui.components.SmallPill
import app.gromp.ui.theme.G
import app.gromp.util.Fmt

/** "The real cost": today's time in things you'd rather do, plus the yearly projection. */
@Composable
fun RealCostCard(s: TodaySmart, onPickTrades: () -> Unit) {
    GCard(background = G.Amber.copy(alpha = 0.08f), border = G.Amber.copy(alpha = 0.30f), spacing = 10.dp) {
        Kicker("The real cost", G.Amber)
        Text(s.costLine, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, color = G.Text)
        s.yearLine?.let { Text(it, fontSize = 14.sp, lineHeight = 21.sp, color = G.Muted) }
        if (!s.tradesChosen) {
            Text("Tell Gromp what you'd rather do with your time, so it can put this in your terms.", fontSize = 13.sp, color = G.Muted)
            SmallPill("Choose what matters", onPickTrades, filled = false)
        }
    }
}

@Composable
fun TaperBanner(line: String) {
    GCard(background = G.SageBg, border = G.SageLine, spacing = 4.dp) {
        Kicker("Your limit came down", G.Sage)
        Text("$line this week. You earned it with a good week.", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Text)
    }
}

/** Long pull-app sessions from today, each with one-tap worth it / not worth it. */
@Composable
fun WorthCard(sessions: List<LongSession>, label: (String) -> String, onRate: (LongSession, Boolean) -> Unit) {
    GCard(spacing = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle("Was it worth it?")
            Text("One tap each. Your answers decide where limits should go.", fontSize = 13.sp, color = G.Muted)
        }
        sessions.take(3).forEach { s ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(s.pkg, 36.dp, 10.dp)
                    Column(Modifier.weight(1f)) {
                        Text(label(s.pkg), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Text)
                        Text("${Fmt.timeOf(s.start)} to ${Fmt.timeOf(s.end)} · ${Fmt.duration(s.durationMs)}", fontSize = 12.sp, color = G.Muted)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallPill("Worth it", { onRate(s, true) }, filled = false)
                    SmallPill("Not worth it", { onRate(s, false) }, filled = false)
                }
            }
        }
    }
}

/** Asks for notification permission (Android 13+) for the smart notifications. */
@Composable
fun NotificationsCard(onDone: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }
    GCard(spacing = 8.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(GIcons.Bell, null, tint = G.Sage, modifier = Modifier.size(18.dp))
            Text("Let Gromp nudge you", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Text)
        }
        Text(
            "Worth-it questions after long sessions, a heads-up before your weak spots, and your Sunday review all arrive as notifications.",
            fontSize = 13.sp, lineHeight = 19.sp, color = G.Muted,
        )
        SmallPill("Allow notifications", {
            if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else onDone()
        }, filled = true)
    }
}

@Composable
fun WeakSpotsCard(spots: List<WeakSpot>, label: (String) -> String, voice: Voice, onFocusHours: (() -> Unit)?) {
    GCard(spacing = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle("Your weak spots")
            Text(
                when (voice) {
                    Voice.Gentle -> "Times of day that tend to pull you in. Gromp gives you a heads-up before them."
                    Voice.Honest -> "When your phone time reliably spikes, from the last four weeks."
                    Voice.Tough -> "Your habits, on a schedule. Gromp will warn you before each one."
                },
                fontSize = 13.sp, lineHeight = 19.sp, color = G.Muted,
            )
        }
        spots.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(s.pkg, 36.dp, 10.dp)
                Column(Modifier.weight(1f)) {
                    Text(WeakSpots.describe(s), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Text)
                    Text("${Fmt.duration(s.avgMs)} of ${label(s.pkg)} on average · ${(s.hitRate * 100).toInt()}% of days", fontSize = 12.sp, color = G.Muted)
                }
            }
        }
        if (onFocusHours != null) SmallPill("Set focus hours", onFocusHours, filled = false)
    }
}

@Composable
fun WorthScoresCard(scores: List<WorthScore>, label: (String) -> String, voice: Voice) {
    GCard(spacing = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle("Worth it, by your own rating")
            scores.firstOrNull()?.let { Text(Worth.scoreLine(it, label(it.pkg), voice), fontSize = 13.sp, lineHeight = 19.sp, color = G.Muted) }
        }
        scores.take(5).forEach { s ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label(s.pkg), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = G.Text)
                    Text("${s.pct}% · ${s.total} ratings", fontSize = 13.sp, color = G.Muted)
                }
                MeterBar(s.pct / 100f, if (s.pct >= 50) G.Sage else G.Amber, height = 8.dp)
            }
        }
    }
}

@Composable
fun ReviewTeaser(onOpen: () -> Unit) {
    GCard(background = G.SageBg, border = G.SageLine, spacing = 8.dp) {
        Kicker("Weekly review", G.Sage)
        Text("Your week, wins and slips, and one thing to try next.", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = G.Text)
        SmallPill("Open review", onOpen, filled = true)
    }
}

@Composable
internal fun Dot(color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
}
