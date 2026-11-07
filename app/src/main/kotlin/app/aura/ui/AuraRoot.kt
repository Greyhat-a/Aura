package app.aura.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aura.ui.components.*
import app.aura.ui.screens.*
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.AuraTheme
import app.aura.ui.theme.glass

enum class Screen(val title: String, val ic: Ic) {
    DASH("Dashboard", Ic.HOME), CHARGE("Charging", Ic.BOLT), NET("Network", Ic.NET), HW("Hardware", Ic.CHIP),
    VIZ("3D Lab", Ic.CUBE), HEALTH("Battery health", Ic.HEART), HISTORY("History", Ic.CHART),
    ALERTS("Alerts", Ic.BELL), SETTINGS("Settings", Ic.GEAR), MORE("More", Ic.MORE),
}

private val compactTabs = listOf(Screen.DASH, Screen.CHARGE, Screen.NET, Screen.VIZ, Screen.MORE)
private val moreScreens = listOf(Screen.HW, Screen.HEALTH, Screen.HISTORY, Screen.ALERTS, Screen.SETTINGS)

@Composable
fun AuraRoot() {
    val g = LocalGraph.current
    val st by g.settings.state.collectAsStateWithLifecycle()
    val accent = Color(st.accent)
    AuraTheme(accent) {
        var screen by rememberSaveable { mutableStateOf(Screen.DASH) }
        BoxWithConstraints(Modifier.fillMaxSize().background(AuraColors.Obsidian)) {
            val wide = maxWidth >= 600.dp
            val contentWidth = maxWidth - if (wide) 84.dp else 0.dp
            val cols = when { contentWidth >= 900.dp -> 4; contentWidth >= 560.dp -> 3; else -> 2 }
            Backdrop(accent)

            BackHandler(enabled = screen != Screen.DASH) {
                screen = if (!wide && screen in moreScreens) Screen.MORE else Screen.DASH
            }

            CompositionLocalProvider(LocalCols provides cols) {
                Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    if (wide) Rail(screen) { screen = it }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            Crossfade(targetState = screen, animationSpec = tween(200), label = "screen") { s ->
                                Box(Modifier.fillMaxSize().padding(horizontal = 14.dp)) { ScreenContent(s) { screen = it } }
                            }
                        }
                        if (!wide) BottomTabs(screen) { screen = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(s: Screen, go: (Screen) -> Unit) = when (s) {
    Screen.DASH -> DashboardScreen(go)
    Screen.CHARGE -> ChargingScreen()
    Screen.NET -> NetworkScreen()
    Screen.HW -> HardwareScreen()
    Screen.VIZ -> Visuals3DScreen()
    Screen.HEALTH -> HealthScreen()
    Screen.HISTORY -> HistoryScreen()
    Screen.ALERTS -> AlertsScreen()
    Screen.SETTINGS -> SettingsScreen()
    Screen.MORE -> MoreScreen(go)
}

@Composable
private fun Backdrop(accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val r = size.maxDimension * 0.7f
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.20f), Color.Transparent), Offset(size.width * 0.9f, size.height * 0.02f), r),
            r, Offset(size.width * 0.9f, size.height * 0.02f),
        )
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF6A3DFF).copy(alpha = 0.10f), Color.Transparent), Offset(size.width * 0.05f, size.height * 0.95f), r),
            r, Offset(size.width * 0.05f, size.height * 0.95f),
        )
    }
}

@Composable
private fun Rail(current: Screen, onSelect: (Screen) -> Unit) {
    val accent = app.aura.ui.theme.LocalAccent.current
    Column(
        Modifier.width(84.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("AURA", fontSize = 15.sp, color = accent, letterSpacing = 3.sp, modifier = Modifier.padding(bottom = 8.dp))
        Screen.values().filter { it != Screen.MORE }.forEach { s ->
            val sel = s == current
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (sel) accent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(s) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AuraIcon(s.ic, if (sel) accent else AuraColors.Muted)
                Text(s.title, fontSize = 10.sp, color = if (sel) AuraColors.Text else AuraColors.Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BottomTabs(current: Screen, onSelect: (Screen) -> Unit) {
    val accent = app.aura.ui.theme.LocalAccent.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).glass(24).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        compactTabs.forEach { s ->
            val sel = s == current || (s == Screen.MORE && current in moreScreens)
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { onSelect(s) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AuraIcon(s.ic, if (sel) accent else AuraColors.Muted)
                Text(s.title, fontSize = 10.sp, color = if (sel) AuraColors.Text else AuraColors.Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MoreScreen(go: (Screen) -> Unit) {
    val accent = app.aura.ui.theme.LocalAccent.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { ScreenTitle("More") }
        items(moreScreens) { s ->
            GlassCard(Modifier.fillMaxWidth(), onClick = { go(s) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AuraIcon(s.ic, accent, Modifier.size(28.dp))
                    Text(s.title, fontSize = 17.sp)
                }
            }
        }
    }
}
