package app.gromp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.gromp.R

/** Design tokens, matching the Gromp canvas. */
object G {
    val Bg = Color(0xFF101214)
    val Surface = Color(0xFF191C20)
    val Surface2 = Color(0xFF22262B)
    val Nav = Color(0xFF15171A)
    val Line = Color(0xFF2C3036)
    val Track = Color(0xFF262A30)
    val Text = Color(0xFFF1EEE8)
    val Muted = Color(0xFFA3A9B1)
    val Faint = Color(0xFF8A919A)
    val Sage = Color(0xFF9EDBB8)
    val SageLight = Color(0xFFC4EBD4)
    val SageMid = Color(0xFF5E9C7A)
    val SageSoft = Color(0xFFC4D6CB)
    val SageBg = Color(0xFF1F2A25)
    val SageLine = Color(0xFF2E4A3C)
    val Amber = Color(0xFFF4B66A)
    val Coral = Color(0xFFF2A08A)
    val Blue = Color(0xFF9CC4F2)
    val Lilac = Color(0xFFC9B6F2)
    val Grey = Color(0xFF6B7178)
    val Dash = Color(0xFF4A5058)
    val Off = Color(0xFF3A3F46)

    /** Sequential heat scale, dark to bright. */
    val Heat = listOf(Color(0xFF1C2124), Color(0xFF24392F), Color(0xFF3A6650), SageMid, Sage)

    /** Tile colors for apps, picked by a stable hash of the package name. */
    val Tiles = listOf(Amber, Coral, Sage, Blue, Lilac)
}

val Manrope = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal),
    Font(R.font.manrope_500, FontWeight.Medium),
    Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_700, FontWeight.Bold),
    Font(R.font.manrope_800, FontWeight.ExtraBold),
)

private val base = TextStyle(fontFamily = Manrope, color = G.Text)

private val GrompTypography = Typography(
    displayLarge = base.copy(fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp),
    headlineLarge = base.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = base.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
    titleMedium = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = base.copy(fontSize = 13.sp, lineHeight = 19.sp, color = G.Muted),
    labelLarge = base.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
    labelMedium = base.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = G.Muted),
    labelSmall = base.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = G.Faint),
)

@Composable
fun GrompTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = G.Sage,
            onPrimary = G.Bg,
            background = G.Bg,
            onBackground = G.Text,
            surface = G.Surface,
            onSurface = G.Text,
            surfaceVariant = G.Surface2,
            onSurfaceVariant = G.Muted,
            outline = G.Line,
            secondary = G.Amber,
            onSecondary = G.Bg,
            surfaceContainerHigh = G.Surface2,
            surfaceContainer = G.Surface,
        ),
        typography = GrompTypography,
        content = content,
    )
}
