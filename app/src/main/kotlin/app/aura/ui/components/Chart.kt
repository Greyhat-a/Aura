package app.aura.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aura.ui.theme.AuraColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One data series; x is epoch millis. Only real, available samples are ever put in here. */
data class Series(val name: String, val color: Color, val pts: List<Pair<Long, Float>>)

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
val dateTimeFmt = SimpleDateFormat("dd MMM HH:mm", Locale.US)
val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.US)

fun fmtTime(ms: Long): String = timeFmt.format(Date(ms))
fun fmtDateTime(ms: Long): String = dateTimeFmt.format(Date(ms))

/**
 * @param style 0 = line, 1 = area, 2 = bars (user setting "graph appearance")
 * Drag/press on the chart to read exact values at a time.
 */
@Composable
fun Chart(
    series: List<Series>,
    modifier: Modifier = Modifier,
    style: Int = 1,
    unit: String = "",
    height: Dp = 140.dp,
    compact: Boolean = false,
    yMin: Float? = null,
    yMax: Float? = null,
    xFmt: (Long) -> String = ::fmtTime,
    decimals: Int = 1,
) {
    val all = series.flatMap { it.pts }
    if (all.size < 2) {
        Box(modifier.height(if (compact) 56.dp else height).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Collecting data…", color = AuraColors.Muted, fontSize = 12.sp)
        }
        return
    }
    val x0 = all.minOf { it.first }
    val x1 = all.maxOf { it.first }.coerceAtLeast(x0 + 1)
    var lo = all.minOf { it.second }.let { if (yMin != null) minOf(it, yMin) else it }
    var hi = all.maxOf { it.second }.let { if (yMax != null) maxOf(it, yMax) else it }
    if (hi - lo < 1e-3f) { hi += 0.5f; lo -= 0.5f }
    val pad = (hi - lo) * 0.08f
    if (yMin == null) lo -= pad
    if (yMax == null) hi += pad
    if (yMin == null && lo < 0 && all.minOf { it.second } >= 0) lo = 0f

    var touch by remember { mutableStateOf<Float?>(null) }
    val measurer = rememberTextMeasurer()
    val labelStyle = remember { TextStyle(color = AuraColors.Muted, fontSize = 10.sp) }
    val fmt = "%.${decimals}f"

    Column(modifier) {
        if (!compact) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                series.forEach { s ->
                    val shown = touch?.let { tx ->
                        val tms = x0 + ((x1 - x0) * tx).toLong()
                        s.pts.minByOrNull { kotlin.math.abs(it.first - tms) }
                    } ?: s.pts.lastOrNull()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(s.color, androidx.compose.foundation.shape.CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            buildString {
                                append(s.name); append(' ')
                                append(shown?.let { String.format(Locale.US, fmt, it.second) } ?: "–"); append(' '); append(unit)
                                if (touch != null && shown != null) append("  ·  ${xFmt(shown.first)}")
                            },
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        var widthPx by remember { mutableStateOf(1f) }
        Canvas(
            Modifier.fillMaxWidth().height(if (compact) 56.dp else height)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { touch = (it.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { touch = null }, onDragCancel = { touch = null },
                        onHorizontalDrag = { c, _ -> touch = (c.position.x / size.width).coerceIn(0f, 1f) },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = { touch = (it.x / size.width).coerceIn(0f, 1f); tryAwaitRelease(); touch = null })
                },
        ) {
            widthPx = size.width
            val leftPad = if (compact) 0f else 34.dp.toPx()
            val bottomPad = if (compact) 0f else 14.dp.toPx()
            val w = size.width - leftPad
            val h = size.height - bottomPad
            fun px(x: Long) = leftPad + w * ((x - x0).toFloat() / (x1 - x0))
            fun py(y: Float) = h - h * ((y - lo) / (hi - lo))

            if (!compact) {
                for (i in 0..3) {
                    val yy = h * i / 3f
                    drawLine(Color.White.copy(alpha = 0.07f), Offset(leftPad, yy), Offset(size.width, yy), 1.dp.toPx())
                    val v = hi - (hi - lo) * i / 3f
                    drawText(measurer, String.format(Locale.US, if (hi - lo > 20) "%.0f" else "%.1f", v), Offset(0f, (yy - 6.dp.toPx()).coerceAtLeast(0f)), labelStyle)
                }
                drawText(measurer, xFmt(x0), Offset(leftPad, h + 1.dp.toPx()), labelStyle)
                val endLabel = xFmt(x1)
                drawText(measurer, endLabel, Offset(size.width - 52.dp.toPx(), h + 1.dp.toPx()), labelStyle)
            }

            series.forEach { s ->
                if (s.pts.size < 2) return@forEach
                when (style) {
                    2 -> {
                        val bw = (w / s.pts.size).coerceIn(1.dp.toPx(), 12.dp.toPx()) * 0.7f
                        s.pts.forEach { p ->
                            val top = py(p.second)
                            drawRect(s.color.copy(alpha = 0.85f), Offset(px(p.first) - bw / 2, top), Size(bw, (h - top).coerceAtLeast(1f)))
                        }
                    }
                    else -> {
                        val path = Path()
                        s.pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(px(p.first), py(p.second)) else path.lineTo(px(p.first), py(p.second)) }
                        if (style == 1) {
                            val fill = Path().apply {
                                addPath(path); lineTo(px(s.pts.last().first), h); lineTo(px(s.pts.first().first), h); close()
                            }
                            drawPath(fill, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.35f), Color.Transparent), 0f, h))
                        }
                        drawPath(path, s.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }
            touch?.let { tx ->
                val tms = x0 + ((x1 - x0) * tx).toLong()
                drawLine(Color.White.copy(alpha = 0.5f), Offset(px(tms), 0f), Offset(px(tms), h), 1.dp.toPx())
                series.forEach { s ->
                    s.pts.minByOrNull { kotlin.math.abs(it.first - tms) }?.let {
                        drawCircle(s.color, 4.dp.toPx(), Offset(px(it.first), py(it.second)))
                    }
                }
            }
        }
    }
}
