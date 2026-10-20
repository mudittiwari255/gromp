package app.gromp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.gromp.AppContainer
import app.gromp.ui.theme.G

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No container") }

/**
 * Loads data with [load] and reloads it whenever the screen comes back into view,
 * so numbers are fresh after the user returns from another app.
 */
@Composable
fun <T> rememberLoaded(vararg keys: Any?, load: suspend () -> T): T? {
    var value by remember(*keys) { mutableStateOf<T?>(null) }
    var resumes by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumes++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(resumes, *keys) { value = runCatching { load() }.getOrNull() ?: value }
    return value
}

@Composable
fun GCard(
    modifier: Modifier = Modifier,
    background: Color = G.Surface,
    border: Color = G.Line,
    radius: Dp = 28.dp,
    padding: PaddingValues = PaddingValues(20.dp),
    spacing: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(radius))
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.semantics { heading() }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = G.Text)
}

@Composable
fun Kicker(text: String, color: Color = G.Muted) {
    Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, color = color)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = G.Sage, contentColor = G.Bg, disabledContainerColor = G.Off),
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = G.Text) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, G.Off),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun SmallPill(text: String, onClick: () -> Unit, filled: Boolean) {
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(22.dp),
        colors = if (filled) ButtonDefaults.buttonColors(containerColor = G.Sage, contentColor = G.Bg)
        else ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = G.SageLight),
        border = if (filled) null else BorderStroke(1.dp, G.SageLine),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun GSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = G.Sage, checkedThumbColor = G.Text,
            uncheckedTrackColor = G.Off, uncheckedThumbColor = G.Text, uncheckedBorderColor = G.Off,
        ),
    )
}

/** The app's real icon when installed, else a colored letter tile like the design. */
@Composable
fun AppIcon(pkg: String, size: Dp = 40.dp, radius: Dp = 12.dp) {
    val catalog = LocalContainer.current.catalog
    val bmp = remember(pkg) { catalog.icon(pkg) }
    if (bmp != null) {
        Image(bmp, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(radius)))
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(radius)).background(tileColor(pkg)),
            contentAlignment = Alignment.Center,
        ) {
            Text(catalog.label(pkg).take(1).uppercase(), fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.ExtraBold, color = G.Bg)
        }
    }
}

fun tileColor(pkg: String): Color = G.Tiles[(pkg.hashCode() and 0x7fffffff) % G.Tiles.size]

@Composable
fun IconTile(icon: ImageVector, tint: Color = G.Sage, bg: Color = G.SageBg, size: Dp = 36.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/** A tappable settings-style row, at least 64dp tall. */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Text)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = G.Muted, lineHeight = 18.sp)
        }
        trailing()
    }
}

@Composable
fun PrivacyFooter(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(GIcons.Shield, contentDescription = null, tint = G.Muted, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 12.sp, lineHeight = 18.sp, color = G.Muted)
    }
}

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "Today", GIcons.Today),
    Trends("trends", "Trends", GIcons.Trends),
    Insights("insights", "Insights", GIcons.Insights),
    Settings("settings", "Settings", GIcons.Settings),
}

@Composable
fun BottomNav(current: String?, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(G.Nav)) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(G.Line))
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Tab.entries.forEach { tab ->
            val on = current == tab.route
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) G.SageBg else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Icon(tab.icon, contentDescription = null, tint = if (on) G.Sage else G.Muted, modifier = Modifier.size(22.dp))
                Text(tab.label, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) G.Sage else G.Muted)
            }
        }
    }
    }
}

@Composable
fun TrendLine(diffMs: Long?, thresholdMs: Long = 3 * 60_000L, suffix: String = "than usual") {
    val (text, color) = when {
        diffMs == null -> "New this week" to G.Muted
        diffMs > thresholdMs -> "↑ ${app.gromp.util.Fmt.minutes(diffMs)} more $suffix" to G.Amber
        diffMs < -thresholdMs -> "↓ ${app.gromp.util.Fmt.minutes(diffMs)} less $suffix" to G.Sage
        else -> "About the same as usual" to G.Muted
    }
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
}
