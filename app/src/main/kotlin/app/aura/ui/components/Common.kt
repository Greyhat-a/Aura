package app.aura.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aura.core.NA
import app.aura.core.Reading
import app.aura.core.show
import app.aura.core.sourceOrReason
import app.aura.ui.LocalCols
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.LocalAccent
import app.aura.ui.theme.glass

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    pad: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier.glass()
    Column((if (onClick != null) base.clickable(onClick = onClick) else base).padding(pad), content = content)
}

@Composable
fun ScreenTitle(title: String, sub: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 8.dp, bottom = 6.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Light, color = AuraColors.Text)
        if (sub != null) Text(sub, fontSize = 12.sp, color = AuraColors.Muted, lineHeight = 16.sp)
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, fontSize = 10.sp, letterSpacing = 1.2.sp, color = AuraColors.Muted)
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier.padding(top = 14.dp, bottom = 2.dp), fontSize = 15.sp,
        fontWeight = FontWeight.Medium, color = LocalAccent.current,
    )
}

/** A labelled value. Tap to reveal where the number came from (or why it is unavailable). */
@Composable
fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    valueColor: Color = AuraColors.Text,
    info: String? = null,
    big: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val na = value == NA
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).let { if (info != null) it.clickable { open = !open } else it }.padding(vertical = 4.dp),
    ) {
        Label(label)
        Text(
            if (na) "Not available" else value,
            fontSize = if (na) 13.sp else if (big) 30.sp else 19.sp,
            fontWeight = if (big) FontWeight.Light else FontWeight.Normal,
            color = if (na) AuraColors.Muted else valueColor,
            maxLines = 2,
        )
        if (sub != null) Text(sub, fontSize = 11.sp, color = AuraColors.Muted)
        if (open && info != null) Text(info, fontSize = 10.sp, color = LocalAccent.current.copy(alpha = 0.85f), lineHeight = 13.sp)
    }
}

@Composable
fun <T> RMetric(
    label: String,
    r: Reading<T>,
    modifier: Modifier = Modifier,
    valueColor: Color = AuraColors.Text,
    sub: String? = null,
    big: Boolean = false,
    fmt: (T) -> String,
) = Metric(label, r.show(fmt), modifier, sub, valueColor, info = r.sourceOrReason(), big = big)

class GridScope {
    val items = mutableListOf<@Composable (Modifier) -> Unit>()
    fun item(c: @Composable (Modifier) -> Unit) { items += c }
}

/** Simple equal-width grid (no scrolling) used inside cards. */
@Composable
fun MetricGrid(cols: Int = LocalCols.current, build: GridScope.() -> Unit) {
    val s = GridScope().apply(build)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        s.items.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { it(Modifier.weight(1f)) }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun NoteBox(text: String, modifier: Modifier = Modifier, tone: Color = LocalAccent.current) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(tone.copy(alpha = 0.08f)),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(tone))
        Text(text, Modifier.padding(10.dp), fontSize = 12.sp, lineHeight = 16.sp, color = AuraColors.Text.copy(alpha = 0.85f))
    }
}

@Composable
fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(o, fontSize = 13.sp) })
        }
    }
}

@Composable
fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 15.sp, color = if (enabled) AuraColors.Text else AuraColors.Muted)
            if (sub != null) Text(sub, fontSize = 11.sp, color = AuraColors.Muted, lineHeight = 14.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun SliderRow(
    title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>,
    steps: Int = 0, enabled: Boolean = true, onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontSize = 13.sp, color = AuraColors.Muted)
            Text(valueText, fontSize = 13.sp, color = if (enabled) AuraColors.Text else AuraColors.Muted)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, steps = steps, enabled = enabled)
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier.clip(CircleShape).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 3.dp),
        fontSize = 11.sp, color = color,
    )
}

@Composable
fun UsageBar(frac: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val a by animateFloatAsState(frac.coerceIn(0f, 1f), tween(600), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
        Box(Modifier.fillMaxWidth(a).fillMaxHeight().clip(CircleShape).background(color))
    }
}

@Composable
fun Ring(
    frac: Float, color: Color, modifier: Modifier = Modifier, stroke: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val a by animateFloatAsState(frac.coerceIn(0f, 1f), tween(800), label = "ring")
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val d = size.minDimension - s
            val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            drawArc(Color.White.copy(alpha = 0.08f), -90f, 360f, false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
            if (a > 0.003f) {
                drawArc(color.copy(alpha = 0.25f), -90f, 360f * a, false, tl, Size(d, d), style = Stroke(s * 1.8f, cap = StrokeCap.Round))
                drawArc(color, -90f, 360f * a, false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

@Composable
fun AuraButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) AuraColors.Bad else LocalAccent.current, contentColor = AuraColors.Obsidian,
        ),
    ) { Text(text, fontWeight = FontWeight.Medium) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(text, color = LocalAccent.current) }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
        Text(text, color = AuraColors.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

// ------------------------------------------------------------------ icons (drawn, no icon library needed)

enum class Ic { HOME, BOLT, NET, CHIP, CUBE, HEART, CHART, BELL, GEAR, MORE }

@Composable
fun AuraIcon(ic: Ic, tint: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val st = Stroke(width = 1.8f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun o(x: Float, y: Float) = Offset(x * s, y * s)
        fun poly(vararg p: Float, close: Boolean = false): Path = Path().apply {
            moveTo(p[0] * s, p[1] * s)
            var i = 2
            while (i < p.size) { lineTo(p[i] * s, p[i + 1] * s); i += 2 }
            if (close) close()
        }
        when (ic) {
            Ic.HOME -> {
                drawPath(poly(3f, 11f, 12f, 4f, 21f, 11f), tint, style = st)
                drawPath(poly(6f, 10f, 6f, 20f, 18f, 20f, 18f, 10f), tint, style = st)
            }
            Ic.BOLT -> drawPath(poly(13f, 2f, 5f, 13f, 11f, 13f, 10f, 22f, 19f, 10f, 13f, 10f, close = true), tint, style = st)
            Ic.NET -> {
                drawCircle(tint, 9f * s, o(12f, 12f), style = st)
                drawOval(tint, o(7.5f, 3f), Size(9f * s, 18f * s), style = st)
                drawLine(tint, o(3f, 12f), o(21f, 12f), st.width, StrokeCap.Round)
            }
            Ic.CHIP -> {
                drawRoundRect(tint, o(6f, 6f), Size(12f * s, 12f * s), CornerRadius(2f * s), style = st)
                for (p in listOf(9f, 12f, 15f)) {
                    drawLine(tint, o(p, 2.5f), o(p, 6f), st.width, StrokeCap.Round); drawLine(tint, o(p, 18f), o(p, 21.5f), st.width, StrokeCap.Round)
                    drawLine(tint, o(2.5f, p), o(6f, p), st.width, StrokeCap.Round); drawLine(tint, o(18f, p), o(21.5f, p), st.width, StrokeCap.Round)
                }
            }
            Ic.CUBE -> {
                drawPath(poly(12f, 2.5f, 20.5f, 7f, 20.5f, 17f, 12f, 21.5f, 3.5f, 17f, 3.5f, 7f, close = true), tint, style = st)
                drawPath(poly(3.5f, 7f, 12f, 12f, 20.5f, 7f), tint, style = st)
                drawLine(tint, o(12f, 12f), o(12f, 21.5f), st.width, StrokeCap.Round)
            }
            Ic.HEART -> drawPath(poly(2f, 12f, 7f, 12f, 9.5f, 5f, 14f, 19f, 16.5f, 12f, 22f, 12f), tint, style = st)
            Ic.CHART -> {
                drawLine(tint, o(6f, 20f), o(6f, 12f), st.width * 1.6f, StrokeCap.Round)
                drawLine(tint, o(12f, 20f), o(12f, 5f), st.width * 1.6f, StrokeCap.Round)
                drawLine(tint, o(18f, 20f), o(18f, 9f), st.width * 1.6f, StrokeCap.Round)
            }
            Ic.BELL -> {
                drawArc(tint, 180f, 180f, false, o(6f, 4f), Size(12f * s, 12f * s), style = st)
                drawPath(poly(6f, 10f, 5f, 17f, 19f, 17f, 18f, 10f), tint, style = st)
                drawLine(tint, o(10f, 20.5f), o(14f, 20.5f), st.width, StrokeCap.Round)
            }
            Ic.GEAR -> {
                drawCircle(tint, 3.2f * s, o(12f, 12f), style = st)
                drawCircle(tint, 7.5f * s, o(12f, 12f), style = st)
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    val c = kotlin.math.cos(a).toFloat(); val sn = kotlin.math.sin(a).toFloat()
                    drawLine(tint, o(12f + 7.5f * c, 12f + 7.5f * sn), o(12f + 10f * c, 12f + 10f * sn), st.width, StrokeCap.Round)
                }
            }
            Ic.MORE -> for (x in listOf(5f, 12f, 19f)) drawCircle(tint, 1.8f * s, o(x, 12f))
        }
    }
}
