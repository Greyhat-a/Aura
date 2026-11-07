package app.aura.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aura.ui.theme.AuraColors

@Composable
fun ChartCard(
    title: String,
    series: List<Series>,
    unit: String,
    style: Int,
    modifier: Modifier = Modifier,
    note: String? = null,
    height: Dp = 140.dp,
    yMin: Float? = null,
    yMax: Float? = null,
    decimals: Int = 1,
    xFmt: (Long) -> String = ::fmtTime,
) {
    GlassCard(modifier.fillMaxWidth()) {
        Label(title)
        Chart(
            series, Modifier.padding(top = 6.dp), style = style, unit = unit, height = height,
            yMin = yMin, yMax = yMax, decimals = decimals, xFmt = xFmt,
        )
        if (note != null) Text(note, fontSize = 10.sp, color = AuraColors.Muted, modifier = Modifier.padding(top = 4.dp))
    }
}
