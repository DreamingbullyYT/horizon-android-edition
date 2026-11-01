package dev.horizon.launcher.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.launcher.R

/** The "Night" palette, from crates/horizon-theme/src/night.rs. */
data class HorizonColors(
    val page: Color = Color(0xFF0E0B26),
    val side: Color = Color(0xFF0A0820),
    val card: Color = Color(0xFF15112E),
    val cardHi: Color = Color(0xFF231C4A),
    val ink: Color = Color(0xFFF4F2FF),
    val inkSoft: Color = Color(0xFFCFC9EA),
    val muted: Color = Color(0xFFA59FC6),
    val accent: Color = Color(0xFF7656FA),
    val accentHi: Color = Color(0xFFA08CFF),
    val onAccent: Color = Color(0xFFFFFFFF),
    val success: Color = Color(0xFF5FD39A),
    val warn: Color = Color(0xFFF5B45A),
    val danger: Color = Color(0xFFFF8A78),
    val stroke: Color = Color(0x1FB9ACFF),
    val sky: List<Color> = listOf(
        Color(0xFF07061A),
        Color(0xFF140F38),
        Color(0xFF2A1656),
        Color(0xFF5B2A78),
        Color(0xFFB0477D),
        Color(0xFFF08A5D),
    ),
)

enum class Accent(val label: String, val primary: Color, val hi: Color) {
    Violet("Violet", Color(0xFF7656FA), Color(0xFFA08CFF)),
    Blue("Blue", Color(0xFF4F6BFF), Color(0xFF8FA4FF)),
    Teal("Teal", Color(0xFF2FB7A9), Color(0xFF6FE0D4)),
    Green("Green", Color(0xFF3FBF6F), Color(0xFF7CE0A0)),
    Amber("Amber", Color(0xFFE0A02E), Color(0xFFF5C56F)),
    Red("Red", Color(0xFFE85C5C), Color(0xFFFF9B9B)),
    Pink("Pink", Color(0xFFE86BB0), Color(0xFFFFA8D2)),
}

/** A plate cuts its corners in one 45° step, like the Night design's pixel corners. */
class PixelCornerShape(private val cut: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val path = Path().apply {
            moveTo(c, 0f)
            lineTo(size.width - c, 0f)
            lineTo(size.width, c)
            lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height)
            lineTo(c, size.height)
            lineTo(0f, size.height - c)
            lineTo(0f, c)
            close()
        }
        return Outline.Generic(path)
    }
}

fun pixelShape(cut: Dp = 10.dp): Shape = PixelCornerShape(cut)

val Bricolage = FontFamily(
    Font(R.font.bricolage_regular, FontWeight.Normal),
    Font(R.font.bricolage_medium, FontWeight.Medium),
    Font(R.font.bricolage_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_bold, FontWeight.Bold),
)

val BricolageDisplay = FontFamily(
    Font(R.font.bricolage_display_bold, FontWeight.Bold),
    Font(R.font.bricolage_bold, FontWeight.Bold),
)

val Pixel = FontFamily(
    Font(R.font.pixelify_medium, FontWeight.Medium),
    Font(R.font.pixelify_semibold, FontWeight.SemiBold),
    Font(R.font.pixelify_bold, FontWeight.Bold),
)

val Mono = FontFamily(Font(R.font.geist_mono, FontWeight.Normal))

val HorizonTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = BricolageDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.8).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = BricolageDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 23.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
    ),
)

val LocalHorizon = staticCompositionLocalOf { HorizonColors() }

@Composable
fun HorizonTheme(accent: Accent = Accent.Violet, content: @Composable () -> Unit) {
    val base = HorizonColors()
    val colors = remember(accent) {
        base.copy(accent = accent.primary, accentHi = accent.hi)
    }
    val scheme = remember(accent) {
        darkColorScheme(
            primary = accent.primary,
            onPrimary = Color.White,
            primaryContainer = accent.primary.copy(alpha = 0.22f),
            onPrimaryContainer = accent.hi,
            secondary = accent.hi,
            onSecondary = Color(0xFF120E2C),
            background = base.page,
            onBackground = base.ink,
            surface = base.card,
            onSurface = base.ink,
            surfaceVariant = base.cardHi,
            onSurfaceVariant = base.inkSoft,
            outline = base.stroke,
            error = base.danger,
            onError = Color(0xFF2A0A05),
        )
    }
    CompositionLocalProvider(LocalHorizon provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HorizonTypography,
            shapes = Shapes(
                small = RoundedCornerShape(6.dp),
                medium = RoundedCornerShape(10.dp),
                large = RoundedCornerShape(14.dp),
            ),
            content = content,
        )
    }
}
