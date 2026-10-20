package app.gromp.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Stroke icons in the same 24px, 2px-stroke style as the design. Tinted by the Icon composable. */
object GIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    val Today = icon("today", circle(12f, 12f, 9f), "M12 7v5l3 2")
    val Trends = icon("trends", "M4 20V10", "M10 20V4", "M16 20v-7", "M22 20H2")
    val Insights = icon("insights", "M12 3a6 6 0 0 0-3.5 10.9V17h7v-3.1A6 6 0 0 0 12 3Z", "M9.5 21h5")
    val Settings = icon(
        "settings", circle(12f, 12f, 3f), "M12 2v3", "M12 19v3", "M4.9 4.9l2.1 2.1", "M17 17l2.1 2.1",
        "M2 12h3", "M19 12h3", "M4.9 19.1l2.1-2.1", "M17 7l2.1-2.1",
    )
    val Lock = icon("lock", "M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2Z", "M8 11V7a4 4 0 0 1 8 0v4")
    val Shield = icon("shield", "M12 3 4 6v6c0 4.5 3.4 8.3 8 9 4.6-.7 8-4.5 8-9V6l-8-3Z")
    val Back = icon("back", "M15 18 9 12l6-6")
    val Chevron = icon("chevron", "M9 18l6-6-6-6")
    val Down = icon("down", "M12 5v14", "M19 12l-7 7-7-7")
    val Up = icon("up", "M12 19V5", "M5 12l7-7 7 7")
    val Phone = icon("phone", "M9 2h6a3 3 0 0 1 3 3v14a3 3 0 0 1-3 3H9a3 3 0 0 1-3-3V5a3 3 0 0 1 3-3Z", "M11 18h2")
    val NoWifi = icon("nowifi", "M2 2l20 20", "M8.5 16.5a5 5 0 0 1 7 0", "M5 12.9a10 10 0 0 1 5.2-2.8", "M19 12.9a10 10 0 0 0-2.3-1.6", "M12 20h.01")
    val Trash = icon("trash", "M3 6h18", "M8 6V4h8v2", "M6 6l1 14h10l1-14")
    val Arrow = icon("arrow", "M5 12h14", "M13 6l6 6-6 6")
    val Moon = icon("moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5Z")
    val Plus = icon("plus", "M12 5v14", "M5 12h14")
    val Bell = icon("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.9 1.9 0 0 0 3.4 0")
    val Battery = icon("battery", "M4 7h13a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2Z", "M22 11v2")
    val Download = icon("download", "M12 3v12", "M7 10l5 5 5-5", "M5 21h14")
    val Upload = icon("upload", "M12 21V9", "M7 14l5-5 5 5", "M5 3h14")
}
