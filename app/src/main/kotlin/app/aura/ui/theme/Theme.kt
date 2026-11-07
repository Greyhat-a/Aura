package app.aura.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object AuraColors {
    val Obsidian = Color(0xFF05070C)
    val Surface = Color(0xFF0B0F17)
    val Text = Color(0xFFE8EEF7)
    val Muted = Color(0xFF8A94A6)
    val Good = Color(0xFF2EE6A6)
    val Warn = Color(0xFFFFB020)
    val Bad = Color(0xFFFF4D4D)
}

val LocalAccent = compositionLocalOf { Color(0xFF00B4FF) }

@Composable
fun AuraTheme(accent: Color, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent, onPrimary = AuraColors.Obsidian, secondary = accent,
        background = AuraColors.Obsidian, onBackground = AuraColors.Text,
        surface = AuraColors.Surface, onSurface = AuraColors.Text,
        surfaceVariant = Color(0xFF141A26), onSurfaceVariant = AuraColors.Muted,
        outline = Color(0x33FFFFFF),
    )
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * Glassmorphism: translucent gradient fill + bright upper-left edge. (True background blur of the content behind a
 * card is not used: it is costly on a mid-range GPU and would fight the continuously rendering 3D surface.)
 */
fun Modifier.glass(radius: Int = 22): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.035f))))
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f))), shape)
}
