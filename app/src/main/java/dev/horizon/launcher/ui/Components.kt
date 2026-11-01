package dev.horizon.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Plate(
    modifier: Modifier = Modifier,
    color: Color = LocalHorizon.current.card.copy(alpha = 0.82f),
    borderColor: Color = LocalHorizon.current.stroke,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(pixelShape(10.dp))
            .background(color)
            .border(1.dp, borderColor, pixelShape(10.dp))
            .padding(contentPadding),
    ) {
        content()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = LocalHorizon.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .background(c.accentHi, pixelShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(),
            color = c.muted,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
fun ScreenHeader(kicker: String, title: String, subtitle: String, trailing: (@Composable () -> Unit)? = null) {
    val c = LocalHorizon.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    kicker.uppercase(),
                    color = c.accentHi,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(6.dp))
                Text(title, color = c.ink, style = MaterialTheme.typography.displayMedium)
            }
            trailing?.invoke()
        }
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = c.muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val c = LocalHorizon.current
    val shape = pixelShape(6.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.accentHi, c.accent)))
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text,
            color = c.onAccent,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
fun SoftButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val c = LocalHorizon.current
    val shape = pixelShape(6.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(c.cardHi.copy(alpha = 0.7f))
            .border(1.dp, c.stroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c.inkSoft, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = c.inkSoft, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalHorizon.current
    val shape = pixelShape(4.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) c.accent.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, if (selected) c.accentHi.copy(alpha = 0.65f) else c.stroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = if (selected) c.ink else c.muted,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
        )
    }
}

@Composable
fun Tag(text: String, color: Color = LocalHorizon.current.accentHi) {
    Row(
        modifier = Modifier
            .clip(pixelShape(3.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.4f), pixelShape(3.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color = LocalHorizon.current.accentHi, size: Int = 38) {
    val c = LocalHorizon.current
    Box(
        Modifier
            .size(size.dp)
            .clip(pixelShape(6.dp))
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.34f), pixelShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
fun InfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalHorizon.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = c.muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            Text(trailing, color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).background(color, CircleShape))
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    primary: String? = null,
    primaryIcon: ImageVector? = null,
    onPrimary: (() -> Unit)? = null,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val c = LocalHorizon.current
    Plate(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconBadge(icon, tint = c.accentHi, size = 54)
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                color = c.ink,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                color = c.muted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            if (primary != null && onPrimary != null) {
                Spacer(Modifier.height(18.dp))
                PrimaryButton(primary, icon = primaryIcon, onClick = onPrimary)
            }
            if (secondary != null && onSecondary != null) {
                Spacer(Modifier.height(10.dp))
                SoftButton(secondary, onClick = onSecondary)
            }
        }
    }
}
