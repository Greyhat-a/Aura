package app.aura.ui.screens

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aura.core.*
import app.aura.monitor.BatterySnapshot
import app.aura.ui.LocalCols
import app.aura.ui.LocalGraph
import app.aura.ui.components.*
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.LocalAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun policyText(code: Int) = when (code) {
    1 -> "Default"
    2 -> "Adaptive charging (AC)"
    3 -> "Adaptive charging (long-life)"
    else -> "Policy $code"
}

@Composable
fun ChargingScreen() {
    val g = LocalGraph.current
    val st by g.settings.state.collectAsStateWithLifecycle()
    val b by g.battery.state.collectAsStateWithLifecycle()
    val hist by g.hub.batteryHistory.collectAsStateWithLifecycle()
    val cols = LocalCols.current
    val accent = LocalAccent.current

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { ScreenTitle("Charging intelligence", "Everything below is read from this device's battery fuel gauge. Tap any value to see its data source.") }
        val s = b
        if (s == null) {
            item { EmptyHint("Waiting for the first battery sample…") }
            return@LazyColumn
        }

        item { Hero(s) }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Label("Battery-side measurements")
                MetricGrid(cols) {
                    item { m -> Metric("Level", "${s.levelPct} %", m, info = "EXTRA_LEVEL / EXTRA_SCALE") }
                    item { m -> Metric("Status", s.statusText, m, info = "EXTRA_STATUS") }
                    item { m -> Metric("Source", s.sourceText, m, info = "EXTRA_PLUGGED (USB / AC / wireless). Android does not report the adapter model or its rating.") }
                    item { m -> RMetric("Power (V × I)", s.powerMw, m, sub = "battery-side") { Fmt.power(it, st.powerMilliwatts) } }
                    item { m -> Metric("Voltage", if (s.voltageMv > 0) "${s.voltageMv} mV" else NA, m, info = "EXTRA_VOLTAGE") }
                    item { m ->
                        RMetric(
                            "Current", s.currentMa, m,
                            sub = s.currentMa.orNull()?.let { if (it >= 0) "into battery" else "out of battery" },
                        ) { String.format(Locale.US, "%+.0f mA", it) }
                    }
                    item { m -> RMetric("Avg current", s.avgCurrentMa, m) { String.format(Locale.US, "%+.0f mA", it) } }
                    item { m -> Metric("Temperature", Fmt.temp(s.tempC, st.tempF), m, info = "EXTRA_TEMPERATURE (battery thermistor, not CPU)") }
                    item { m -> Metric("Health (Android)", s.healthText, m, info = "EXTRA_HEALTH: a coarse category, not a capacity percentage") }
                    item { m -> RMetric("State of health", s.sohPct, m, sub = "OEM-provided") { "$it %" } }
                    item { m -> RMetric("Charge cycles", s.cycleCount, m) { "$it" } }
                    item { m -> Metric("Chemistry", s.technology ?: NA, m, info = "EXTRA_TECHNOLOGY") }
                    item { m -> RMetric("Charge counter", s.chargeMah, m, sub = "remaining") { String.format(Locale.US, "%.0f mAh", it) } }
                    item { m ->
                        Metric(
                            "Est. full capacity", s.estFullMah?.let { String.format(Locale.US, "%.0f mAh", it) } ?: NA, m,
                            sub = "rough, from counter ÷ level",
                            info = "Computed: charge counter × 100 / level, smoothed. ±several %.",
                        )
                    }
                    item { m -> RMetric("Charging policy", s.policy, m) { policyText(it) } }
                    item { m ->
                        Metric(
                            "Power class", s.chargeClass ?: NA, m, sub = "inferred",
                            info = "Inferred from battery-side watts only. Android does not expose the charging protocol (e.g. PD / Samsung Fast Charge).",
                        )
                    }
                    item { m -> RMetric("Full in", s.etaFull, m) { Fmt.duration(it) } }
                    item { m -> RMetric("Battery left", s.etaEmpty, m) { Fmt.duration(it) } }
                }
            }
        }

        item {
            NoteBox(
                "Battery-side vs charger-side: AURA measures what flows into the battery (fuel-gauge voltage × current). " +
                    "The wall adapter's output power is not exposed to apps, so it is never shown or guessed.",
            )
        }

        val recent = hist
        item {
            ChartCard(
                "Live charging speed", listOf(Series("Power", accent, recent.powerPts(st.powerMilliwatts))),
                unit = if (st.powerMilliwatts) "mW" else "W", style = st.graphStyle, decimals = if (st.powerMilliwatts) 0 else 2,
                note = "Last ${recent.size} samples. Negative = battery is discharging.",
            )
        }
        item { ChartCard("Voltage history", listOf(Series("Voltage", Color(0xFF9B8CFF), recent.voltPts())), "mV", st.graphStyle, decimals = 0) }
        item { ChartCard("Current fluctuation", listOf(Series("Current", AuraColors.Good, recent.currentPts())), "mA", st.graphStyle, decimals = 0) }
        item { ChartCard("Battery temperature", listOf(Series("Temp", AuraColors.Warn, recent.tempPts(st.tempF))), if (st.tempF) "°F" else "°C", st.graphStyle) }

        item { EfficiencyCard(s) }
    }
}

@Composable
private fun Hero(s: BatterySnapshot) {
    val accent = LocalAccent.current
    val shown by animateIntAsState(s.levelPct, tween(900), label = "pct")
    val color = when {
        s.charging -> AuraColors.Good
        s.levelPct <= 15 -> AuraColors.Bad
        s.levelPct <= 30 -> AuraColors.Warn
        else -> accent
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Ring(s.levelPct / 100f, color, Modifier.size(140.dp), stroke = 11.dp) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$shown", fontSize = 44.sp, fontWeight = FontWeight.Light)
                    Text("%", fontSize = 17.sp, color = AuraColors.Muted, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Pill(s.statusText, color)
                Text(s.sourceText, fontSize = 13.sp, color = AuraColors.Muted)
                s.powerMw.orNull()?.let { Text(String.format(Locale.US, "%.2f W", it / 1000f) + " battery-side", fontSize = 18.sp) }
                    ?: Text("Power not measurable", fontSize = 13.sp, color = AuraColors.Muted)
                val eta = if (s.charging || s.full) s.etaFull else s.etaEmpty
                Text((if (s.charging || s.full) "Full in " else "Remaining ") + eta.show { Fmt.duration(it) }, fontSize = 13.sp)
            }
        }
    }
}

private val sessionFmt = SimpleDateFormat("dd MMM HH:mm", Locale.US)

@Composable
private fun EfficiencyCard(s: BatterySnapshot) {
    val g = LocalGraph.current
    val st by g.settings.state.collectAsStateWithLifecycle()

    // Cross-check on the most recent charging session: ∫ current dt vs change of the charge counter.
    val chk by produceState<Pair<Analytics.Consistency?, Long?>>(null to null) {
        value = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val sess = g.db.dao().sessionsOnce(now - 7L * 86_400_000, now).lastOrNull() ?: return@withContext null to null
            val raw = g.db.dao().batteryRaw(sess.startTs, sess.endTs ?: now)
            Analytics.coulombConsistency(raw.map { Analytics.Sample(it.ts, it.currentMa, it.chargeMah) }) to sess.startTs
        }
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Label("Charging efficiency (estimate)")
        val pw = s.powerMw.orNull()
        val share = if (pw != null && s.charging) ChargeMath.batterySharePct(pw / 1000f, st.adapterW) else null
        MetricGrid(2) {
            item { m -> Metric("Battery-side power", pw?.let { Fmt.power(it, st.powerMilliwatts) } ?: NA, m) }
            item { m ->
                Metric(
                    "Share of your adapter rating",
                    share?.let { String.format(Locale.US, "%.0f %%", it) } ?: NA, m,
                    sub = if (st.adapterW > 0f) "lower bound · adapter ${st.adapterW} W" else "enter adapter watts in Settings",
                    info = "Battery-side W ÷ the adapter rating YOU entered. It is a lower bound: the tablet also powers itself and the adapter may not run at full rating.",
                )
            }
        }
        val c = chk.first
        Spacer(Modifier.height(6.dp))
        if (c != null) {
            val ok = c.ratioPct in 80f..120f
            Text(
                String.format(Locale.US, "Measurement self-check: charge counter rose %.0f mAh while current integrated to %.0f mAh → %.0f %% agreement", c.counterDeltaMah, c.integratedMah, c.ratioPct) +
                    " (session ${chk.second?.let { sessionFmt.format(Date(it)) }}).",
                fontSize = 12.sp, color = if (ok) AuraColors.Good else AuraColors.Warn, lineHeight = 16.sp,
            )
        } else {
            Text("Measurement self-check: needs a recent charging session with both a charge counter and current readings.", fontSize = 12.sp, color = AuraColors.Muted)
        }
        Spacer(Modifier.height(6.dp))
        NoteBox(
            "Limits: true charging efficiency = energy stored ÷ energy drawn from the wall. AURA only sees the battery side, so it cannot compute it. " +
                "The self-check validates AURA's current/voltage readings (≈100 % is coherent) and says nothing about your charger.",
            tone = AuraColors.Warn,
        )
    }
}
