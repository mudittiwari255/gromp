package app.gromp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gromp.ui.components.GCard
import app.gromp.ui.components.LocalContainer
import app.gromp.ui.components.SmallPill
import app.gromp.ui.theme.G
import app.gromp.util.Permissions

/**
 * Prominent disclosure shown before sending the user to Accessibility settings.
 * Play policy requires saying plainly what the service does and doesn't do.
 */
@Composable
fun LocksDisclosureDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = LocalContainer.current.prefs
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = G.Surface,
        title = { Text("Turn on Gromp locks", fontWeight = FontWeight.ExtraBold, color = G.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Locks use Android's Accessibility service so Gromp can notice when an app you chose comes to the front, and show your pause, limit or focus screen.", color = G.Text, fontSize = 15.sp)
                Text("Gromp does not read what's on your screen, does not see what you type, and has no internet access. It only checks which app just opened.", color = G.Muted, fontSize = 14.sp)
                Text("On the next screen, find Gromp locks under Installed or Downloaded apps and switch it on.", color = G.Muted, fontSize = 14.sp)
                Text("If it's greyed out: Settings › Apps › Gromp › ⋮ › Allow restricted settings, then try again.", color = G.Muted, fontSize = 14.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                prefs.locksDisclosureAccepted = true
                onDismiss()
                context.startActivity(Permissions.accessibilityIntent())
            }) { Text("I understand, open settings", color = G.Sage, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now", color = G.Muted) } },
    )
}

/** Shown wherever locks are configured but the service is off. */
@Composable
fun LocksOffBanner(onTurnOn: () -> Unit) {
    GCard(background = G.Amber.copy(alpha = 0.10f), border = G.Amber.copy(alpha = 0.35f), spacing = 8.dp) {
        Text("Locks are switched off", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Text)
        Text("Your rules are saved, but Gromp can't show them until the locks service is on.", fontSize = 13.sp, color = G.Muted)
        SmallPill("Turn on locks", onTurnOn, filled = true)
    }
}

/** Shown in the Lite edition where locks would otherwise be. */
@Composable
fun LiteEditionCard() {
    GCard(spacing = 8.dp) {
        Text("Lite edition", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Text)
        Text(
            "Locks and notification counting need Accessibility and notification access, which Android blocks for apps " +
                "installed from a browser in some regions. Install the Full edition over USB (adb) or from an app store to use them. " +
                "Export a backup below before switching editions.",
            fontSize = 13.sp, lineHeight = 19.sp, color = G.Muted,
        )
    }
}
