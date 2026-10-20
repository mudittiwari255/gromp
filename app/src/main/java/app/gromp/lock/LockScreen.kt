package app.gromp.lock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gromp.data.Category
import app.gromp.smart.Copy
import app.gromp.smart.Cost
import app.gromp.smart.IntentKind
import app.gromp.smart.Trade
import app.gromp.smart.Voice
import app.gromp.ui.components.PrimaryButton
import app.gromp.ui.theme.G
import app.gromp.util.Fmt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    request: LockRequest,
    label: String,
    icon: ImageBitmap?,
    snoozesToday: Int,
    voice: Voice = Voice.Honest,
    trades: List<Trade> = emptyList(),
    /** Today's time in this app, for cost lines. */
    todayMs: Long = 0,
    onLeave: () -> Unit,
    onContinue: () -> Unit,
    onIntention: (IntentKind) -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(G.Bg)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        when (request.mode) {
            LockMode.Pause -> PauseContent(
                label, icon, onLeave, onContinue,
                cost = if (voice == Voice.Tough && todayMs >= 10 * 60_000L) Cost.todayLine(todayMs, trades, voice, app = label) else null,
            )
            LockMode.Limit -> LimitContent(
                request, label, icon, snoozesToday, onLeave, onContinue,
                cost = Cost.todayLine(request.usedMs, trades, voice, app = request.category?.let { categoryName(it).lowercase() + " apps" } ?: label),
            )
            LockMode.Focus -> FocusContent(request, label, icon, onLeave, onContinue)
            LockMode.Intention -> IntentionContent(label, icon, voice, trades, todayMs, onLeave, onIntention)
            LockMode.Drift -> {
                val c = Copy.drift(label, request.intent ?: IntentKind.Bored, request.minutes, voice)
                CheckInContent(label, icon, c, CheckInArt.Compass, "${request.minutes}m", onLeave, onContinue)
            }
            LockMode.Binge -> {
                val c = Copy.binge(label, request.minutes, request.cost, voice)
                CheckInContent(label, icon, c, CheckInArt.Ring, "${request.minutes}m", onLeave, onContinue)
            }
            LockMode.Loop -> {
                val c = Copy.loop(request.loopApps, request.loopSpanMs, voice)
                CheckInContent(label, icon, c, CheckInArt.Loop, "${request.loopApps}", onLeave, onContinue)
            }
        }
    }
}

@Composable
private fun AppBadge(label: String, icon: ImageBitmap?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)))
        }
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Muted)
    }
}

// ---------------- Pause ----------------

@Composable
private fun PauseContent(label: String, icon: ImageBitmap?, onLeave: () -> Unit, onContinue: () -> Unit, cost: String? = null) {
    var secondsLeft by remember { mutableIntStateOf(5) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) { delay(1_000); secondsLeft-- }
    }
    val breath = rememberInfiniteTransition(label = "breath")
    val scale by breath.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val phase = if (secondsLeft > 3) "Breathe in…" else "…and out"

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        AppBadge(label, icon)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(260.dp).border(2.dp, G.SageBg, CircleShape))
            Box(Modifier.size(200.dp).border(2.dp, G.SageLine, CircleShape))
            Box(Modifier.size(150.dp).scale(scale).background(G.Sage.copy(alpha = 0.9f), CircleShape))
        }
        Spacer(Modifier.height(32.dp))
        Text(
            if (secondsLeft > 0) phase else "Do you still want to open $label?",
            fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = G.Text, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (secondsLeft > 0) "Take one slow breath. $secondsLeft…" else "No pressure either way. You chose to pause here.",
            fontSize = 15.sp, color = G.Muted, textAlign = TextAlign.Center,
        )
        if (cost != null && secondsLeft == 0) {
            Spacer(Modifier.height(12.dp))
            Text(cost, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Amber, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(visible = secondsLeft == 0, enter = fadeIn(tween(400))) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Not now, close it", onLeave, Modifier.fillMaxWidth())
                QuietButton("Open $label", onContinue)
            }
        }
        if (secondsLeft > 0) Spacer(Modifier.height(124.dp))
    }
}

// ---------------- Limit ----------------

@Composable
private fun LimitContent(
    r: LockRequest, label: String, icon: ImageBitmap?, snoozes: Int, onLeave: () -> Unit, onContinue: () -> Unit,
    cost: String? = null,
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        AppBadge(label, icon)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(220.dp)) {
                val stroke = 16.dp.toPx()
                val inset = stroke / 2
                drawArc(G.Amber, -90f, 360f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Fmt.duration(r.usedMs), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = G.Text)
                Text("of your ${Fmt.duration(r.limitMin * 60_000L)}", fontSize = 13.sp, color = G.Muted)
            }
        }
        Spacer(Modifier.height(32.dp))
        val catLabel = r.category?.let { categoryName(it) }
        Text(
            if (catLabel != null) "That's today's ${catLabel.lowercase()} time." else "That's today's $label time.",
            fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = G.Text, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                catLabel != null && snoozes == 0 ->
                    "$label shares a ${Fmt.duration(r.limitMin * 60_000L)} daily limit with your other ${catLabel.lowercase()} apps. Tomorrow starts fresh."
                snoozes == 0 -> "You set this limit for a reason. Tomorrow starts fresh."
                else -> "You've taken $snoozes extra ${if (snoozes == 1) "break" else "breaks"} today. That's okay, just notice it."
            },
            fontSize = 15.sp, color = G.Muted, textAlign = TextAlign.Center,
        )
        if (cost != null) {
            Spacer(Modifier.height(12.dp))
            Text(cost, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = G.Amber, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton("I'm done for today", onLeave, Modifier.fillMaxWidth())
            HoldButton("Hold for 5 more minutes", holdMs = 3_000, onComplete = onContinue)
        }
    }
}

// ---------------- Focus ----------------

@Composable
private fun FocusContent(r: LockRequest, label: String, icon: ImageBitmap?, onLeave: () -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        AppBadge(label, icon)
        Spacer(Modifier.weight(1f))
        Canvas(Modifier.size(180.dp)) {
            // A crescent moon: calm, not a padlock.
            drawCircle(G.Lilac, radius = size.minDimension / 2.6f)
            drawCircle(G.Bg, radius = size.minDimension / 2.6f, center = Offset(size.width * 0.64f, size.height * 0.40f))
        }
        Spacer(Modifier.height(28.dp))
        Text("$label is resting.", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = G.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Focus hours run until ${Fmt.clockFull(r.focusEnd)}. Whatever's in there will still be there then.",
            fontSize = 15.sp, color = G.Muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton("Okay, close it", onLeave, Modifier.fillMaxWidth())
            HoldButton("Hold to open for 5 minutes", holdMs = 5_000, onComplete = onContinue)
        }
    }
}

private fun categoryName(name: String): String = runCatching { Category.valueOf(name).label }.getOrDefault(name)

// ---------------- Intention ----------------

@Composable
private fun IntentionContent(
    label: String, icon: ImageBitmap?, voice: Voice, trades: List<Trade>, todayMs: Long,
    onLeave: () -> Unit, onPick: (IntentKind) -> Unit,
) {
    val copy = Copy.intention(label, voice)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        AppBadge(label, icon)
        Spacer(Modifier.weight(1f))
        Text(copy.title, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, color = G.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(copy.body, fontSize = 15.sp, color = G.Muted, textAlign = TextAlign.Center)
        if (voice == Voice.Tough && todayMs >= 10 * 60_000L) {
            Spacer(Modifier.height(12.dp))
            Text(Cost.todayLine(todayMs, trades, voice, app = label), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = G.Amber, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IntentKind.entries.forEach { k -> QuietButton(k.label, { onPick(k) }) }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(copy.leave, onLeave, Modifier.fillMaxWidth())
    }
}

// ---------------- Drift, binge, loop ----------------

private enum class CheckInArt { Ring, Compass, Loop }

@Composable
private fun CheckInContent(
    label: String, icon: ImageBitmap?, copy: Copy.CheckIn, art: CheckInArt, figure: String,
    onLeave: () -> Unit, onStay: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        AppBadge(label, icon)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(200.dp)) {
                val stroke = 12.dp.toPx()
                when (art) {
                    CheckInArt.Ring -> drawCircle(G.Amber, radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
                    CheckInArt.Compass -> {
                        drawCircle(G.Blue, radius = size.minDimension / 2 - stroke, style = Stroke(stroke / 2))
                        drawLine(G.Blue, Offset(size.width / 2, size.height * 0.28f), Offset(size.width / 2, size.height * 0.72f), stroke / 2, StrokeCap.Round)
                    }
                    CheckInArt.Loop -> {
                        val r = size.minDimension / 2 - stroke
                        drawArc(G.Lilac, 20f, 300f, false, Offset(stroke, stroke), Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
            }
            Text(figure, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = G.Text)
        }
        Spacer(Modifier.height(28.dp))
        Text(copy.title, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, color = G.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(copy.body, fontSize = 15.sp, lineHeight = 22.sp, color = G.Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(copy.leave, onLeave, Modifier.fillMaxWidth())
            QuietButton(copy.stay, onStay)
        }
    }
}

// ---------------- Buttons ----------------

@Composable
private fun QuietButton(text: String, action: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, G.Off, RoundedCornerShape(28.dp))
            .semantics { role = Role.Button; onClick(label = text) { action(); true } }
            .pointerInput(Unit) { detectTapGestures(onTap = { action() }) },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = G.Text)
    }
}

/** A deliberately slow button: the user has to hold it for [holdMs] to go on. */
@Composable
private fun HoldButton(text: String, holdMs: Int, onComplete: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var done by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, G.Off, RoundedCornerShape(28.dp))
            .semantics { role = Role.Button; onClick(label = text) { onComplete(); true } }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    if (done) return@detectTapGestures
                    val job = scope.launch {
                        progress.animateTo(1f, tween(((1f - progress.value) * holdMs).toInt(), easing = LinearEasing))
                        done = true
                        onComplete()
                    }
                    tryAwaitRelease()
                    if (!done) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(250)) }
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(G.Surface2, size = Size(size.width * progress.value, size.height))
        }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Muted)
    }
}
