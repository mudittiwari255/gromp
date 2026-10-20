package app.gromp.ui.screens

import app.gromp.BuildConfig
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gromp.BuildConfigInfo
import app.gromp.ui.components.AppIcon
import app.gromp.ui.components.GCard
import app.gromp.ui.components.GIcons
import app.gromp.ui.components.IconTile
import app.gromp.ui.components.Kicker
import app.gromp.ui.components.ListRow
import app.gromp.ui.components.LocalContainer
import app.gromp.ui.components.SmallPill
import app.gromp.ui.components.rememberLoaded
import app.gromp.ui.theme.G
import app.gromp.util.Fmt
import app.gromp.util.Permissions
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen(
    onOpenApp: (String) -> Unit,
    onAddLock: () -> Unit,
    onFocusHours: () -> Unit,
    onCategoryLimits: () -> Unit,
    onWiped: () -> Unit,
) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val goal by c.prefs.dailyGoalMin.collectAsState()
    val focus by c.prefs.focus.collectAsState()
    val rules by remember { c.repository.rulesFlow() }.collectAsState(initial = emptyList())
    val categoryRules by remember { c.repository.categoryRulesFlow() }.collectAsState(initial = emptyList())
    val status = rememberLoaded(refresh) {
        Triple(Permissions.hasUsageAccess(context), Permissions.hasNotificationAccess(context), Permissions.isLockServiceOn(context))
    }
    var showGoal by remember { mutableStateOf(false) }
    var showWipe by remember { mutableStateOf(false) }
    var showDisclosure by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching { context.contentResolver.openOutputStream(uri)?.use { c.backup.export(it) } }.isSuccess
            Toast.makeText(context, if (ok) "Backup saved" else "Couldn't save the backup", Toast.LENGTH_SHORT).show()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = runCatching { context.contentResolver.openInputStream(uri)?.use { c.backup.import(it) } }
            Toast.makeText(context, if (r.isSuccess) "Backup restored" else (r.exceptionOrNull()?.message ?: "Couldn't read that file"), Toast.LENGTH_LONG).show()
            refresh++
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
        Text("Settings", modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = G.Text)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Section("Your limit") {
                ListRow(
                    title = "Daily screen time limit",
                    subtitle = "Used for the ring on Today and your goal days",
                    onClick = { showGoal = true },
                    trailing = { Text(Fmt.duration(goal * 60_000L), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Sage) },
                )
            }

            if (!BuildConfig.LOCKS) LiteEditionCard() else Section("Locks") {
                val on = status?.third ?: false
                ListRow(
                    title = if (on) "Locks are on" else "Locks are off",
                    subtitle = if (on) "Pause, limit and focus screens will appear" else "Needed for pause, limit and focus screens",
                    leading = { IconTile(GIcons.Lock, if (on) G.Sage else G.Amber, if (on) G.SageBg else G.Amber.copy(alpha = 0.14f)) },
                    onClick = { if (on) context.startActivity(Permissions.accessibilityIntent()) else showDisclosure = true },
                    trailing = { if (!on) SmallPill("Turn on", { showDisclosure = true }, filled = true) },
                )
                Divider()
                ListRow(
                    title = "Focus hours",
                    subtitle = if (focus.enabled) "${Fmt.clockFull(focus.startMinute)} to ${Fmt.clockFull(focus.endMinute)} · ${rules.count { it.focusBlocked }} apps rest"
                    else "Off",
                    leading = { IconTile(GIcons.Moon, G.Lilac, androidx.compose.ui.graphics.Color(0xFF241F2E)) },
                    onClick = onFocusHours,
                    trailing = { Icon(GIcons.Chevron, null, tint = G.Muted, modifier = Modifier.size(20.dp)) },
                )
                Divider()
                ListRow(
                    title = "Category limits",
                    subtitle = if (categoryRules.isEmpty()) "Share one daily limit across Social, Video, Games…"
                    else categoryRules.joinToString(" · ") { r ->
                        val name = runCatching { app.gromp.data.Category.valueOf(r.category).label }.getOrDefault(r.category)
                        "$name ${Fmt.duration(r.dailyLimitMin * 60_000L)}"
                    },
                    leading = { IconTile(GIcons.Trends, G.Amber, androidx.compose.ui.graphics.Color(0xFF2B2418)) },
                    onClick = onCategoryLimits,
                    trailing = { Icon(GIcons.Chevron, null, tint = G.Muted, modifier = Modifier.size(20.dp)) },
                )
                rules.filter { it.dailyLimitMin != null || it.pauseEnabled }.forEach { r ->
                    Divider()
                    ListRow(
                        title = c.catalog.label(r.pkg),
                        subtitle = listOfNotNull(
                            r.dailyLimitMin?.let { "${Fmt.duration(it * 60_000L)} a day" },
                            if (r.pauseEnabled) "pause first" else null,
                            if (r.focusBlocked) "rests in focus hours" else null,
                        ).joinToString(" · ").replaceFirstChar { it.uppercase() },
                        leading = { AppIcon(r.pkg, 36.dp, 10.dp) },
                        onClick = { onOpenApp(r.pkg) },
                        trailing = { Icon(GIcons.Chevron, null, tint = G.Muted, modifier = Modifier.size(20.dp)) },
                    )
                }
                Divider()
                ListRow(
                    title = "Add a lock",
                    subtitle = "Pick an app to pause, limit or rest",
                    leading = { IconTile(GIcons.Plus) },
                    onClick = onAddLock,
                )
            }

            Section("Permissions") {
                PermRow("Usage access", "Counts app time. Required.", status?.first) { context.startActivity(Permissions.usageAccessIntent(context)) }
                if (BuildConfig.LOCKS) {
                    Divider()
                    PermRow("Notification counting", "Counts notifications per app. Never reads them.", status?.second) {
                        context.startActivity(Permissions.notificationAccessIntent())
                    }
                }
                Divider()
                ListRow(
                    title = "Battery optimisation",
                    subtitle = "On Xiaomi, Oppo, Vivo, Realme and Samsung, allow Gromp to run in the background (and enable Autostart) so history and locks keep working.",
                    leading = { IconTile(GIcons.Battery, G.Blue, androidx.compose.ui.graphics.Color(0xFF1B2430)) },
                    onClick = { runCatching { context.startActivity(Permissions.batteryIntent()) } },
                )
            }

            Section("Your data") {
                ListRow(
                    title = "Export a backup",
                    subtitle = "Saves a JSON file wherever you choose",
                    leading = { IconTile(GIcons.Download) },
                    onClick = { exporter.launch("gromp-backup-${LocalDate.now()}.json") },
                )
                Divider()
                ListRow(
                    title = "Restore a backup",
                    subtitle = "Replaces what's on this phone",
                    leading = { IconTile(GIcons.Upload) },
                    onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
                Divider()
                ListRow(
                    title = "Delete everything",
                    subtitle = "Wipes history, locks and settings from this phone",
                    leading = { IconTile(GIcons.Trash, G.Coral, G.Coral.copy(alpha = 0.12f)) },
                    onClick = { showWipe = true },
                )
            }

            GCard(spacing = 6.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(GIcons.Shield, null, tint = G.Sage, modifier = Modifier.size(18.dp))
                    Text("Gromp ${BuildConfigInfo.versionName(context)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Text)
                }
                Text(
                    "This app has no internet permission, no account and no analytics. It cannot send anything anywhere. " +
                        "Manrope typeface © The Manrope Project Authors, SIL Open Font License 1.1.",
                    fontSize = 13.sp, lineHeight = 19.sp, color = G.Muted,
                )
            }
            Box(Modifier.height(16.dp))
        }
    }

    if (showGoal) GoalDialog(goal, { showGoal = false }) { c.prefs.setDailyGoal(it); showGoal = false }
    if (showDisclosure) LocksDisclosureDialog { showDisclosure = false; refresh++ }
    if (showWipe) {
        AlertDialog(
            onDismissRequest = { showWipe = false },
            containerColor = G.Surface,
            title = { Text("Delete everything?", fontWeight = FontWeight.ExtraBold, color = G.Text) },
            text = { Text("All history, locks and settings on this phone will be erased. This can't be undone. Export a backup first if you might want it.", color = G.Muted) },
            confirmButton = {
                TextButton(onClick = {
                    showWipe = false
                    scope.launch { c.repository.wipe(); onWiped() }
                }) { Text("Delete", color = G.Coral, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showWipe = false }) { Text("Keep my data", color = G.Muted) } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.padding(start = 8.dp, top = 8.dp)) { Kicker(title) }
        GCard(padding = PaddingValues(horizontal = 20.dp, vertical = 6.dp), spacing = 0.dp) { content() }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(G.Line))

@Composable
private fun PermRow(title: String, subtitle: String, granted: Boolean?, onClick: () -> Unit) {
    ListRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        trailing = {
            Text(
                when (granted) { true -> "On"; false -> "Off"; null -> "" },
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (granted == true) G.Sage else G.Amber,
            )
        },
    )
}

@Composable
private fun GoalDialog(current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val options = listOf(60, 120, 180, 240, 300, 360)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = G.Surface,
        title = { Text("Daily limit", fontWeight = FontWeight.ExtraBold, color = G.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pick something honest. You can change it any time.", color = G.Muted, fontSize = 14.sp)
                options.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { o -> SmallPill(Fmt.duration(o * 60_000L), { onPick(o) }, filled = o == current) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = G.Sage, fontWeight = FontWeight.Bold) } },
    )
}
