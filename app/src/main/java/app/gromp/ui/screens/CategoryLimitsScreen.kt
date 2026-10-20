package app.gromp.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gromp.data.Category
import app.gromp.ui.components.GCard
import app.gromp.ui.components.GIcons
import app.gromp.ui.components.LocalContainer
import app.gromp.ui.components.MeterBar
import app.gromp.ui.components.SecondaryButton
import app.gromp.ui.components.rememberLoaded
import app.gromp.ui.theme.G
import app.gromp.util.Fmt
import app.gromp.util.Permissions
import kotlinx.coroutines.launch

/** Shared daily limits per category: e.g. all social apps together get 1h. */
@Composable
fun CategoryLimitsScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val limits by remember { c.repository.categoryRulesFlow() }.collectAsState(initial = emptyList())
    val today = rememberLoaded { c.repository.categoryToday() } ?: emptyMap()
    val lockOn = rememberLoaded { Permissions.isLockServiceOn(context) } ?: true
    var editing by remember { mutableStateOf<Category?>(null) }
    var showDisclosure by remember { mutableStateOf(false) }
    val byCategory = limits.associate { it.category to it.dailyLimitMin }

    // Categories you actually use come first.
    val ordered = Category.entries.sortedByDescending { cat -> today[cat]?.sumOf { it.second } ?: 0L }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(GIcons.Back, contentDescription = "Back", tint = G.Text) }
            Text("Category limits", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Text)
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "One daily limit shared by every app in a category. Set Social to 1h and Instagram, X, Reddit and the rest all draw from the same hour.",
                fontSize = 14.sp, lineHeight = 21.sp, color = G.Muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
            if (!lockOn && limits.isNotEmpty()) LocksOffBanner { showDisclosure = true }

            ordered.forEach { cat ->
                val apps = today[cat].orEmpty()
                val usedMs = apps.sumOf { it.second }
                val limit = byCategory[cat.name]
                GCard(radius = 24.dp, padding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), spacing = 10.dp) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(cat.label, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = G.Text)
                            Text(
                                if (limit != null) "${Fmt.duration(usedMs)} of ${Fmt.duration(limit * 60_000L)} today"
                                else "${Fmt.duration(usedMs)} today",
                                fontSize = 13.sp, color = if (limit != null && usedMs >= limit * 60_000L) G.Amber else G.Muted,
                            )
                        }
                        SecondaryButton(
                            limit?.let { "${Fmt.duration(it * 60_000L)} · Edit" } ?: "Set limit",
                            { editing = cat },
                        )
                    }
                    if (limit != null) {
                        MeterBar(usedMs.toFloat() / (limit * 60_000L), if (usedMs >= limit * 60_000L) G.Amber else G.Sage)
                    }
                    if (apps.isNotEmpty()) {
                        val names = apps.sortedByDescending { it.second }.map { c.catalog.label(it.first) }
                        Text(
                            names.take(4).joinToString(", ") + if (names.size > 4) " and ${names.size - 4} more" else "",
                            fontSize = 12.sp, lineHeight = 17.sp, color = G.Faint,
                        )
                    }
                }
            }
            Box(Modifier.height(16.dp).background(G.Bg))
        }
    }

    editing?.let { cat ->
        LimitDialog(byCategory[cat.name], onDismiss = { editing = null }) { min ->
            editing = null
            scope.launch {
                c.repository.setCategoryLimit(cat, min)
                if (min != null && !Permissions.isLockServiceOn(context)) showDisclosure = true
            }
        }
    }
    if (showDisclosure) LocksDisclosureDialog { showDisclosure = false }
}
