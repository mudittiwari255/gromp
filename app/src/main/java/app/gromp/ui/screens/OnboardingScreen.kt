package app.gromp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.gromp.ui.components.GCard
import app.gromp.ui.components.GIcons
import app.gromp.ui.components.SmallPill
import app.gromp.ui.components.IconTile
import app.gromp.ui.components.PrimaryButton
import app.gromp.ui.theme.G
import app.gromp.util.Permissions

@Composable
fun OnboardingScreen(hasAccess: Boolean, onContinue: () -> Unit) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(G.Bg)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Canvas(Modifier.size(28.dp)) {
                    drawCircle(G.Sage, radius = size.minDimension / 2 - 2.dp.toPx(), style = Stroke(2.5.dp.toPx()))
                    drawCircle(G.Sage, radius = 4.dp.toPx())
                }
                Text("gromp", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = G.Text)
            }
            Canvas(Modifier.fillMaxWidth().height(200.dp).padding(top = 24.dp)) {
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(G.SageBg, 88.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                drawCircle(G.SageLine, 66.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                drawCircle(G.SageMid.copy(alpha = 0.7f), 44.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                drawCircle(G.Sage, 22.dp.toPx(), c)
                drawCircle(G.Amber, 5.dp.toPx(), Offset(c.x + 76.dp.toPx(), c.y - 40.dp.toPx()))
                drawCircle(G.Blue, 4.dp.toPx(), Offset(c.x - 62.dp.toPx(), c.y + 48.dp.toPx()))
                drawCircle(G.Lilac, 3.dp.toPx(), Offset(c.x + 52.dp.toPx(), c.y + 60.dp.toPx()))
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "See your phone time clearly. Then take it back.",
                fontSize = 32.sp, lineHeight = 37.sp, fontWeight = FontWeight.ExtraBold, color = G.Text, letterSpacing = (-0.8).sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Gromp needs Usage access to count which apps you open and for how long. That's all it reads.",
                fontSize = 16.sp, lineHeight = 24.sp, color = G.Muted,
            )
            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Promise(GIcons.Phone, "Stays on this phone", "Your history lives in a local database only.")
                Promise(GIcons.NoWifi, "No internet. No account.", "The app can't connect to anything, by design.")
                Promise(GIcons.Trash, "Delete it any time", "One tap wipes everything. Or export a backup file.")
            }
        }
        Spacer(Modifier.height(16.dp))
        if (hasAccess) {
            PrimaryButton("Let's go", onContinue, Modifier.fillMaxWidth())
        } else {
            if (asked) {
                GCard(spacing = 8.dp) {
                    Text("Switch greyed out?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Text)
                    Text(
                        "Android blocks this setting for apps installed outside an app store until you allow it:\n" +
                            "1. Open Gromp's App info below.\n" +
                            "2. Tap ⋮ (top right) › Allow restricted settings.\n" +
                            "3. Come back and grant usage access again.",
                        fontSize = 13.sp, lineHeight = 20.sp, color = G.Muted,
                    )
                    SmallPill("Open App info", { context.startActivity(Permissions.appInfoIntent(context)) }, filled = false)
                }
                Spacer(Modifier.height(12.dp))
            }
            PrimaryButton("Grant usage access", {
                asked = true
                context.startActivity(Permissions.usageAccessIntent(context))
            }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text(
                "Opens Android Settings. Find Gromp, switch it on, then come back.",
                fontSize = 12.sp, lineHeight = 18.sp, color = G.Faint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Promise(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconTile(icon)
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Text)
            Text(body, fontSize = 13.sp, lineHeight = 19.sp, color = G.Muted)
        }
    }
}
