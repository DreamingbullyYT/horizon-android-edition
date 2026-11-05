package dev.horizon.launcher.ui2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.launcher.sdui.Registry

val LocalAccent = staticCompositionLocalOf { Color(0xFF7656FA) }
val Ink = Color.White
val Muted = Color(0xFFA7A3BD)
val Glass = Color.White.copy(alpha = 0.07f)
val GlassLine = Color.White.copy(alpha = 0.11f)

@Composable
fun Glyph(name: String, tint: Color = Ink, size: Int = 22) {
    Registry.icons[name]?.let { Icon(it, null, Modifier.size(size.dp), tint = tint) }
}

/** A tappable button whose whole surface is the tap target. [primary] fills with the accent. */
@Composable
fun Pill(
    text: String?,
    icon: String? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(if (primary) 18.dp else 999.dp),
        color = if (primary) accent else Glass,
        contentColor = Ink,
        border = if (primary) null else BorderStroke(1.dp, GlassLine),
        modifier = Modifier.defaultMinSize(minHeight = 48.dp, minWidth = 48.dp),
    ) {
        Row(
            Modifier.padding(horizontal = if (text == null) 12.dp else 18.dp, vertical = 10.dp),
            Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            Alignment.CenterVertically,
        ) {
            icon?.let { Glyph(it, size = 20) }
            text?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
    }
}

/** A glass panel; tappable when [onClick] is given. */
@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = Glass, border = BorderStroke(1.dp, GlassLine)) {
            Box(Modifier.padding(16.dp)) { content() }
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = Glass, border = BorderStroke(1.dp, GlassLine)) {
            Box(Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
fun Title(text: String) = Text(text, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)

@Composable
fun Label(text: String) = Text(text, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)

@Composable
fun Hint(text: String) = Text(text, color = Muted, fontSize = 12.sp)

@Composable
fun Empty(text: String) = Box(Modifier.padding(24.dp)) { Text(text, color = Muted, fontSize = 14.sp) }

@Composable
fun Header(title: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.padding(bottom = 12.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Title(title) }
        trailing()
    }
}
