package app.gromp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gromp.ui.theme.G
import app.gromp.util.Fmt

/** Progress ring toward the daily limit. Turns amber once the limit is passed. */
@Composable
fun GoalRing(fraction: Float, accent: Color, modifier: Modifier = Modifier, size: Dp = 220.dp, description: String) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(fraction) { anim.animateTo(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing)) }
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val stroke = 16.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(G.Track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        if (anim.value > 0f) {
            drawArc(accent, -90f, 360f * anim.value, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/** 24 hourly bars; hours still to come are dashed outlines. */
@Composable
fun HourBars(hours: LongArray, currentHour: Int, accent: Color, modifier: Modifier = Modifier) {
    val max = hours.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val peak = hours.indices.maxByOrNull { hours[it] } ?: 0
    val desc = "Usage by hour. Busiest hour starts at ${Fmt.clockFull(peak * 60)}."
    Canvas(modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = desc }) {
        val gap = 4.dp.toPx()
        val w = (size.width - gap * 23) / 24
        val maxH = size.height - 4.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
        for (i in 0 until 24) {
            val x = i * (w + gap)
            if (i > currentHour) {
                val h = 24.dp.toPx()
                drawRoundRect(G.Dash, Offset(x + 0.5f, size.height - h), Size(w - 1f, h - 1f), CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = dash))
                continue
            }
            val v = hours[i]
            val h = if (v == 0L) 4.dp.toPx() else (v.toFloat() / max * maxH).coerceAtLeast(4.dp.toPx())
            val color = when {
                v == 0L -> G.Track
                i == peak -> accent
                else -> G.SageMid
            }
            drawRoundRect(color, Offset(x, size.height - h), Size(w, h), CornerRadius(4.dp.toPx()))
        }
        drawLine(G.Line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
    }
}

@Composable
fun AxisLabels(labels: List<String>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { Text(it, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = G.Faint) }
    }
}

data class Bar(val label: String, val value: Long, val top: String, val highlight: Boolean, val hasData: Boolean)

/** Vertical bars with an average line. Labels above and below each bar. */
@Composable
fun BarChart(bars: List<Bar>, avg: Long, modifier: Modifier = Modifier, showTop: Boolean = true) {
    val max = (bars.maxOfOrNull { it.value } ?: 0L).coerceAtLeast(avg).coerceAtLeast(1L)
    val chartH = 112.dp
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(chartH + if (showTop) 22.dp else 4.dp)) {
            Row(
                Modifier.fillMaxWidth().fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(if (bars.size > 12) 3.dp else 10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEach { b ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (showTop && b.top.isNotEmpty()) {
                            Text(b.top, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = G.Muted, maxLines = 1)
                            Box(Modifier.height(6.dp))
                        }
                        val h = if (b.value == 0L) 4.dp else chartH * (b.value.toFloat() / max)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(h.coerceAtLeast(4.dp))
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                .background(
                                    when {
                                        b.highlight -> G.Sage
                                        !b.hasData -> G.Track
                                        else -> G.Off
                                    },
                                ),
                        )
                    }
                }
            }
            if (avg > 0) {
                Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                    val y = size.height - chartH.toPx() * (avg.toFloat() / max)
                    drawLine(
                        Color(0xFF5A6068), Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(if (bars.size > 12) 3.dp else 10.dp)) {
            bars.forEach { b ->
                Text(
                    b.label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                    fontSize = 12.sp, fontWeight = if (b.highlight) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (b.highlight) G.Text else G.Muted,
                )
            }
        }
    }
}

/** 7 rows × 24 hours. Brighter squares mean more minutes. */
@Composable
fun Heatmap(rows: List<Pair<String, LongArray>>, futureFrom: Map<Int, Int>, modifier: Modifier = Modifier) {
    val max = rows.maxOfOrNull { r -> r.second.maxOrNull() ?: 0L }?.coerceAtLeast(1L) ?: 1L
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEachIndexed { ri, (label, hours) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, modifier = Modifier.width(30.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = G.Muted)
                val future = futureFrom[ri]
                Canvas(Modifier.weight(1f).height(16.dp)) {
                    val gap = 3.dp.toPx()
                    val w = (size.width - gap * 23) / 24
                    for (h in 0 until 24) {
                        val x = h * (w + gap)
                        if (future != null && h >= future) {
                            drawRoundRect(G.Off, Offset(x + 0.5f, 0.5f), Size(w - 1f, size.height - 1f), CornerRadius(3.dp.toPx()),
                                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))))
                            continue
                        }
                        val f = hours[h].toFloat() / max
                        val level = when {
                            hours[h] < 2 * 60_000 -> 0
                            f < 0.25f -> 1
                            f < 0.5f -> 2
                            f < 0.75f -> 3
                            else -> 4
                        }
                        drawRoundRect(G.Heat[level], Offset(x, 0f), Size(w, size.height), CornerRadius(3.dp.toPx()))
                    }
                }
            }
        }
        AxisLabels(listOf("12a", "6a", "12p", "6p", "11p"), Modifier.padding(start = 33.dp, top = 4.dp))
    }
}

@Composable
fun HeatLegend() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Less", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = G.Muted)
        G.Heat.forEach { Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(it)) }
        Text("More", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = G.Muted)
    }
}

/** Area line over [values] with a dashed average line. */
@Composable
fun Sparkline(values: List<Long>, avg: Long, color: Color, modifier: Modifier = Modifier, description: String) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(avg).coerceAtLeast(1L).toFloat() * 1.1f
    Canvas(modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = description }) {
        if (values.size < 2) return@Canvas
        val pad = 6.dp.toPx()
        fun y(v: Long) = size.height - pad - (v / max) * (size.height - 2 * pad)
        val step = (size.width - pad) / (values.size - 1)
        val line = Path()
        values.forEachIndexed { i, v -> if (i == 0) line.moveTo(0f, y(v)) else line.lineTo(i * step, y(v)) }
        val area = Path().apply {
            addPath(line)
            lineTo((values.size - 1) * step, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, color.copy(alpha = 0.12f))
        drawLine(Color(0xFF5A6068), Offset(0f, y(avg)), Offset(size.width, y(avg)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 4.dp.toPx(), Offset((values.size - 1) * step, y(values.last())))
    }
}

/** One horizontal bar split into colored shares. */
@Composable
fun StackedBar(parts: List<Pair<Float, Color>>, description: String) {
    Row(
        Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp)).semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        parts.filter { it.first > 0f }.forEach { (f, c) -> Box(Modifier.weight(f).fillMaxHeight().background(c)) }
    }
}

/** Thin progress bar used in app lists. */
@Composable
fun MeterBar(fraction: Float, color: Color, height: Dp = 6.dp) {
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(G.Track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(height / 2)).background(color))
    }
}

@Composable
fun Dots(filled: Int, total: Int, description: String) {
    val n = total.coerceAtMost(120)
    val f = if (total > 120) (filled * 120 / total) else filled
    val cols = 15
    Column(Modifier.fillMaxWidth().semantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (0 until n).chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { i ->
                    Canvas(Modifier.weight(1f).height(16.dp)) {
                        drawCircle(if (i < f) G.Sage else G.SageLine, radius = minOf(size.width, size.height) / 2)
                    }
                }
                repeat(cols - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
