package app.gromp.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gromp.BuildConfig
import app.gromp.data.db.LockRule
import app.gromp.smart.Experiment
import app.gromp.smart.ExperimentKind
import app.gromp.smart.Review
import app.gromp.smart.Taper
import app.gromp.ui.components.Bar
import app.gromp.ui.components.BarChart
import app.gromp.ui.components.GCard
import app.gromp.ui.components.GIcons
import app.gromp.ui.components.Kicker
import app.gromp.ui.components.LocalContainer
import app.gromp.ui.components.PrimaryButton
import app.gromp.ui.components.SectionTitle
import app.gromp.ui.components.SmallPill
import app.gromp.ui.components.rememberLoaded
import app.gromp.ui.theme.G
import app.gromp.util.Fmt
import kotlinx.coroutines.launch

/** The weekly review: this week so far, or last week. */
@Composable
fun ReviewScreen(onBack: () -> Unit, onFocusHours: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lastWeek by rememberSaveable { mutableStateOf(false) }
    val monday = Taper.mondayOf(c.repository.today()).let { if (lastWeek) it.minusDays(7) else it }
    val review = rememberLoaded(monday) { c.smart.review(monday) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(GIcons.Back, contentDescription = "Back", tint = G.Text) }
            Text("Weekly review", modifier = Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Text)
            SmallPill(if (lastWeek) "This week" else "Last week", { lastWeek = !lastWeek }, filled = false)
        }
        if (review == null) return@Column
        ReviewContent(
            review = review,
            isCurrentWeek = !lastWeek,
            label = c.catalog::label,
            canSetLimits = BuildConfig.LOCKS,
            onExperiment = { e ->
                when (e.kind) {
                    ExperimentKind.FocusHours -> onFocusHours()
                    ExperimentKind.AppLimit -> scope.launch {
                        val pkg = e.pkg ?: return@launch
                        val rule = c.repository.rules().firstOrNull { it.pkg == pkg } ?: LockRule(pkg)
                        c.repository.saveRule(rule.copy(dailyLimitMin = e.limitMin))
                        Toast.makeText(context, "${c.catalog.label(pkg)} limit set to ${Fmt.duration((e.limitMin ?: 0) * 60_000L)}", Toast.LENGTH_SHORT).show()
                    }
                    ExperimentKind.MuteNotifications -> e.pkg?.let { pkg ->
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                    else -> Toast.makeText(context, "Good luck. Gromp will show how it went next Sunday.", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

/** Everything the review shows, free of data loading so it can be previewed and UI-tested. */
@Composable
fun ReviewContent(
    review: Review,
    isCurrentWeek: Boolean,
    label: (String) -> String,
    canSetLimits: Boolean,
    onExperiment: (Experiment) -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Kicker(
                (if (isCurrentWeek) "This week so far · " else "") +
                    "${Fmt.shortDate(review.weekStart)} to ${Fmt.shortDate(review.weekStart.plusDays(6))}",
            )
            Text(review.headline, modifier = Modifier.semantics { heading() }, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, color = G.Text)
        }

        GCard(spacing = 14.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Kicker("Total")
                    Text(Fmt.duration(review.totalMs), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = G.Text)
                }
                review.prevWeekMs?.let { Text("last week ${Fmt.duration(it)}", fontSize = 13.sp, color = G.Muted) }
            }
            val byDay = review.days.associateBy { it.day }
            BarChart(
                bars = (0..6).map { i ->
                    val d = review.weekStart.plusDays(i.toLong())
                    val ms = byDay[d]?.ms ?: 0L
                    Bar(Fmt.weekday(d).take(1), ms, Fmt.duration(ms).takeIf { ms > 0 } ?: "", false, ms > 0)
                },
                avg = 0,
                showTop = false,
            )
            Text(review.costLine, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = G.Amber)
        }

        if (review.wins.isNotEmpty()) ListCard("Wins", review.wins, G.Sage)
        if (review.slips.isNotEmpty()) ListCard("Slips", review.slips, G.Amber)

        GCard(spacing = 8.dp) {
            SectionTitle("Your limit")
            Text(review.goalLine, fontSize = 14.sp, color = G.Text)
            review.nextGoalLine?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = G.Sage) }
            review.checkInLine?.let { Text(it, fontSize = 13.sp, color = G.Muted) }
            review.worthLine?.let { Text(it, fontSize = 13.sp, color = G.Muted) }
        }

        if (review.topApps.isNotEmpty()) {
            GCard(spacing = 10.dp) {
                SectionTitle("Top apps")
                review.topApps.forEach { a ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label(a.pkg), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = G.Text)
                        val change = a.prevMs?.let { p ->
                            val d = a.ms - p
                            when {
                                d > 5 * 60_000 -> "  ↑ ${Fmt.duration(d)}"
                                d < -5 * 60_000 -> "  ↓ ${Fmt.duration(-d)}"
                                else -> ""
                            }
                        } ?: ""
                        Text(Fmt.duration(a.ms) + change, fontSize = 14.sp, color = G.Muted)
                    }
                }
            }
        }

        val e = review.experiment
        GCard(background = G.SageBg, border = G.SageLine, spacing = 10.dp) {
            Kicker("Try this next week", G.Sage)
            Text(e.title, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, color = G.Text)
            Text(e.body, fontSize = 14.sp, lineHeight = 21.sp, color = G.SageSoft)
            val action = when (e.kind) {
                ExperimentKind.FocusHours -> if (canSetLimits) "Set focus hours" else null
                ExperimentKind.AppLimit -> if (canSetLimits) "Set the limit" else null
                ExperimentKind.MuteNotifications -> "Open notification settings"
                else -> "I'll try it"
            }
            if (action != null) PrimaryButton(action, { onExperiment(e) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ListCard(title: String, items: List<String>, color: androidx.compose.ui.graphics.Color) {
    GCard(spacing = 10.dp) {
        SectionTitle(title)
        items.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(color)
                Text(line, fontSize = 14.sp, lineHeight = 20.sp, color = G.Text)
            }
        }
    }
}
