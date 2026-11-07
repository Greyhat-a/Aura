package app.aura.gl

import kotlin.math.*

private const val TAU = 6.2831855f
private val GREY = Rgb(0.35f, 0.42f, 0.55f)
private val CYAN = Rgb(0.15f, 0.85f, 1f)
private val AMBER = Rgb(1f, 0.72f, 0.15f)
private val RED = Rgb(1f, 0.22f, 0.15f)
private val GREEN = Rgb(0.2f, 1f, 0.55f)
private val MAGENTA = Rgb(1f, 0.4f, 0.9f)

private fun frac(x: Float) = x - floor(x)
private fun clamp01(x: Float) = x.coerceIn(0f, 1f)

/** Battery temperature -> colour: blue (cool) .. teal .. amber .. red (hot). */
fun tempColor(c: Float?): Rgb = when {
    c == null -> GREY
    c < 30f -> Rgb(0.1f, 0.65f, 1f)
    c < 38f -> Rgb(0.1f, 0.65f, 1f).mix(Rgb(0.1f, 0.95f, 0.75f), (c - 30f) / 8f)
    c < 42f -> Rgb(0.1f, 0.95f, 0.75f).mix(AMBER, (c - 38f) / 4f)
    c < 47f -> AMBER.mix(RED, (c - 42f) / 5f)
    else -> RED
}

/** Heat 0..1 -> blue, amber, red. */
fun heatColor(h: Float): Rgb = if (h < 0.5f) Rgb(0.1f, 0.6f, 1f).mix(AMBER, h * 2f) else AMBER.mix(RED, (h - 0.5f) * 2f)

// ------------------------------------------------------------------ A. Battery
class BatteryScene : GlScene {
    private var lvl = 0f
    private var flow = 0f
    private val n = 90

    override fun build(b: GlBatch, t: Float, dt: Float, d: SceneData) {
        val target = (d.batteryPct ?: 0f) / 100f
        lvl += (target - lvl) * min(1f, dt * 3f)
        val base = tempColor(d.tempC).let { if (d.charging) it.mix(GREEN, 0.3f) else it }
        val R = 0.78f; val H = 2.5f; val y0 = -H / 2; val segs = 32
        val top = y0 + lvl * H

        // glass shell
        for (i in 0 until segs) {
            val a0 = TAU * i / segs; val a1 = TAU * (i + 1) / segs
            b.quad(R * cos(a0), y0, R * sin(a0), R * cos(a1), y0, R * sin(a1), R * cos(a1), y0 + H, R * sin(a1), R * cos(a0), y0 + H, R * sin(a0), CYAN, 0.05f)
            if (i % 2 == 0) b.line(R * cos(a0), y0, R * sin(a0), R * cos(a0), y0 + H, R * sin(a0), CYAN, 0.35f)
        }
        for (k in 0..6) b.ringY(0f, y0 + H * k / 6f, 0f, R, segs, CYAN, if (k == 0 || k == 6) 0.7f else 0.25f)
        // terminal
        val tr = 0.3f
        for (i in 0 until segs) {
            val a0 = TAU * i / segs; val a1 = TAU * (i + 1) / segs
            b.quad(tr * cos(a0), y0 + H, tr * sin(a0), tr * cos(a1), y0 + H, tr * sin(a1), tr * cos(a1), y0 + H + 0.2f, tr * sin(a1), tr * cos(a0), y0 + H + 0.2f, tr * sin(a0), CYAN, 0.12f)
        }
        b.ringY(0f, y0 + H, 0f, tr, 20, CYAN, 0.8f); b.ringY(0f, y0 + H + 0.2f, 0f, tr, 20, CYAN, 0.8f)

        // liquid, level = real battery percentage
        if (lvl > 0.005f) {
            val rr = R * 0.93f
            for (i in 0 until segs) {
                val a0 = TAU * i / segs; val a1 = TAU * (i + 1) / segs
                val w0 = 0.03f * sin(t * 2.2f + a0 * 3f); val w1 = 0.03f * sin(t * 2.2f + a1 * 3f)
                b.quad(rr * cos(a0), y0 + 0.02f, rr * sin(a0), rr * cos(a1), y0 + 0.02f, rr * sin(a1),
                    rr * cos(a1), top + w1, rr * sin(a1), rr * cos(a0), top + w0, rr * sin(a0), base, 0.2f)
                b.tri(0f, top, 0f, rr * cos(a0), top + w0, rr * sin(a0), rr * cos(a1), top + w1, rr * sin(a1), base, 0.28f)
            }
            for (k in 1..3) {
                val r = rr * k / 3f
                var px = r; var pz = 0f; var py = top + 0.03f * sin(t * 2.2f)
                for (i in 1..28) {
                    val a = TAU * i / 28
                    val x = r * cos(a); val z = r * sin(a); val y = top + 0.03f * sin(t * 2.2f + a * 3f) * (0.4f + k * 0.2f)
                    b.line(px, py, pz, x, y, z, base.scale(1.3f), 0.8f)
                    px = x; pz = z; py = y
                }
            }
            // particles: flow upward while charging, slow downward drift while discharging
            val speed = if (d.charging) 0.25f + clamp01((d.powerW ?: 3f) / 15f) * 1.0f else -0.08f
            flow += dt * speed
            val lh = (top - y0 - 0.06f).coerceAtLeast(0.01f)
            for (i in 0 until n) {
                val ang = Hash.h(i, 1) * TAU + t * 0.25f
                val rad = sqrt(Hash.h(i, 2)) * rr * 0.85f
                val y = y0 + 0.03f + frac(Hash.h(i, 3) + flow) * lh
                b.point(rad * cos(ang), y, rad * sin(ang), 0.07f, base.scale(1.5f), if (d.charging) 0.9f else 0.35f)
            }
        }
    }
}

// ------------------------------------------------------------------ B. Charging
class ChargingScene : GlScene {
    private var flow = 0f
    private var flowI = 0f
    private var pulse = 0f

    override fun build(b: GlBatch, t: Float, dt: Float, d: SceneData) {
        val live = d.charging && d.powerW != null
        val pw = d.powerW?.coerceAtLeast(0f) ?: 0f
        val intensity = if (live) 0.3f + 0.7f * clamp01(pw / 15f) else 0.12f
        val speed = if (live) 0.12f + clamp01(pw / 15f) * 0.9f else 0.02f
        flow += dt * speed
        pulse += dt * (1.5f + speed * 6f)
        val col = if (live) CYAN else GREY

        // core orb: brightness = live power
        val orb = 0.22f + 0.05f * sin(pulse)
        b.sphereWire(0f, 0f, 0f, orb, 6, 8, 16, col.scale(1.2f), intensity)
        b.point(0f, 0f, 0f, 0.5f * intensity + 0.15f, col, 0.9f * intensity)

        // energy rings travel up the column; velocity ~ power
        for (k in 0 until 7) {
            val p = frac(k / 7f + flow)
            val y = -1.7f + 3.4f * p
            val a = sin(p * Math.PI.toFloat()) * intensity
            b.ringY(0f, y, 0f, 0.9f + 0.25f * sin(p * Math.PI.toFloat()), 40, col, a)
            b.ringY(0f, y, 0f, 0.82f + 0.2f * sin(p * Math.PI.toFloat()), 40, col, a * 0.5f)
        }

        // voltage stream: helix whose radius follows measured voltage (3.0 V .. 4.5 V)
        val vFrac = d.voltageMv?.let { clamp01((it - 3000f) / 1500f) }
        if (vFrac != null) {
            val rad = 1.05f + 0.5f * vFrac
            for (i in 0 until 60) {
                val p = frac(i / 60f + flow * 0.8f)
                val ang = i * 0.55f + t * 0.4f
                b.point(rad * cos(ang), -1.7f + 3.4f * p, rad * sin(ang), 0.06f, CYAN, 0.35f + 0.55f * vFrac)
            }
        }

        // current stream: particle count and speed follow measured current
        val iMa = d.currentMa?.let { abs(it) }
        if (iMa != null) {
            flowI += dt * (0.1f + min(iMa, 3000f) / 2500f) * (if (d.currentMa!! >= 0) 1f else -1f)
            val count = (6 + clamp01(iMa / 3000f) * 74f).toInt()
            for (i in 0 until count) {
                val lane = i % 4
                val ang = lane * TAU / 4f + 0.4f
                val p = frac(Hash.h(i, 7) + flowI)
                b.point(0.5f * cos(ang), -1.7f + 3.4f * p, 0.5f * sin(ang), 0.07f, AMBER, 0.85f)
            }
        }
    }
}

// ------------------------------------------------------------------ C. Network
class NetworkScene : GlScene {
    private class Packet(val from: Int, val down: Boolean, val dur: Float, var t: Float = 0f)

    private val nodeCount = 28
    private val R = 1.4f
    private val pos: Array<FloatArray> = Array(nodeCount) { i ->
        if (i == 0) floatArrayOf(0f, 0.2f * R, 0.98f * R)       // "this device" marker, fixed
        else {
            val lat = asin(2f * Hash.h(i, 11) - 1f)
            val lon = TAU * Hash.h(i, 12)
            floatArrayOf(R * cos(lat) * cos(lon), R * sin(lat), R * cos(lat) * sin(lon))
        }
    }
    private val packets = ArrayList<Packet>()
    private var accDown = 0f
    private var accUp = 0f
    private var seq = 0

    override fun build(b: GlBatch, t: Float, dt: Float, d: SceneData) {
        val online = d.connected
        val gc = if (online) Rgb(0.1f, 0.5f, 1f) else GREY
        for (k in -3..3) {
            val lat = k * 0.38f
            b.ringY(0f, R * sin(lat), 0f, R * cos(lat), 40, gc, 0.28f)
        }
        for (j in 0 until 12) {
            val lon = TAU * j / 12f
            var px = 0f; var py = R; var pz = 0f
            for (i in 1..30) {
                val phi = Math.PI.toFloat() * i / 30
                val x = R * sin(phi) * cos(lon); val y = R * cos(phi); val z = R * sin(phi) * sin(lon)
                b.line(px, py, pz, x, y, z, gc, 0.2f)
                px = x; py = y; pz = z
            }
        }
        val dev = pos[0]
        for (i in 1 until nodeCount) b.point(pos[i][0], pos[i][1], pos[i][2], 0.05f, if (online) CYAN else GREY, 0.7f)
        b.point(dev[0], dev[1], dev[2], 0.16f, Rgb(1f, 1f, 1f), 0.95f)

        // Packets are spawned from REAL measured traffic: rate = 8 * log10(1 + Mbps) per second (capped at 40/s).
        // Flight time = 0.5 s + measured latency / 120 (visual mapping, clamped to 0.5 - 4 s).
        val dur = (0.5f + (d.latencyMs ?: 20f) / 120f).coerceIn(0.5f, 4f)
        if (online && d.downMbps != null) accDown += dt * min(40f, 8f * log10(1f + d.downMbps))
        if (online && d.upMbps != null) accUp += dt * min(40f, 8f * log10(1f + d.upMbps))
        while (accDown >= 1f && packets.size < 160) {
            accDown -= 1f; packets += Packet(1 + (Hash.h(seq++, 5) * (nodeCount - 1)).toInt().coerceAtMost(nodeCount - 2), true, dur)
        }
        while (accUp >= 1f && packets.size < 160) {
            accUp -= 1f; packets += Packet(1 + (Hash.h(seq++, 6) * (nodeCount - 1)).toInt().coerceAtMost(nodeCount - 2), false, dur)
        }
        accDown = min(accDown, 2f); accUp = min(accUp, 2f)

        val iter = packets.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.t += dt / p.dur
            if (p.t >= 1f) { iter.remove(); continue }
            val n = pos[p.from]
            val col = if (p.down) CYAN else MAGENTA
            for (tr in 0..3) {
                val tt = p.t - tr * 0.03f
                if (tt < 0f) continue
                val s = if (p.down) tt else 1f - tt      // fraction of the way from the remote node to this device
                val x = n[0] + (dev[0] - n[0]) * s
                val y = n[1] + (dev[1] - n[1]) * s
                val z = n[2] + (dev[2] - n[2]) * s
                val len = sqrt(x * x + y * y + z * z).coerceAtLeast(0.001f)
                val lift = R + 0.4f * sin(Math.PI.toFloat() * s)
                b.point(x / len * lift, y / len * lift, z / len * lift, 0.075f - tr * 0.012f, col, 0.95f - tr * 0.22f)
            }
        }
    }
}

// ------------------------------------------------------------------ D. CPU
class CpuScene : GlScene {
    override val cameraDistance = 6.2f

    override fun build(b: GlBatch, t: Float, dt: Float, d: SceneData) {
        val n = d.coreActivity?.size ?: d.coreCount
        val cols = ceil(sqrt(n.toFloat())).toInt().coerceAtLeast(1)
        val rows = ceil(n / cols.toFloat()).toInt().coerceAtLeast(1)
        val cell = 2.0f / max(cols, rows)
        val heat = d.cpuHeat
        val chip = if (heat != null) heatColor(heat).mix(Rgb(0.4f, 0.6f, 0.9f), 0.6f) else GREY

        b.box(0f, -0.2f, 0f, 2.7f, 0.14f, 2.7f, chip, 0.12f, 0.8f)
        b.box(0f, -0.1f, 0f, 2.2f, 0.04f, 2.2f, chip, 0.1f, 0.5f)
        for (i in 0 until 14) {                         // pins
            val p = -1.2f + i * 2.4f / 13f
            b.line(p, -0.2f, 1.35f, p, -0.2f, 1.6f, chip, 0.6f); b.line(p, -0.2f, -1.35f, p, -0.2f, -1.6f, chip, 0.6f)
            b.line(1.35f, -0.2f, p, 1.6f, -0.2f, p, chip, 0.6f); b.line(-1.35f, -0.2f, p, -1.6f, -0.2f, p, chip, 0.6f)
        }
        for (i in 0 until n) {
            val cx = (i % cols - (cols - 1) / 2f) * cell * 1.05f
            val cz = (i / cols - (rows - 1) / 2f) * cell * 1.05f
            val act = d.coreActivity?.getOrNull(i) ?: if (d.coreActivity == null) d.cpuActivity else null
            if (act == null) {            // core offline / unreadable: dim outline only
                b.box(cx, 0.04f, cz, cell * 0.8f, 0.1f, cell * 0.8f, GREY, 0.05f, 0.35f)
                continue
            }
            val pulse = 1f + 0.1f * act * sin(t * (3f + 9f * act) + i)
            val h = (0.15f + 0.6f * act) * pulse
            val col = if (heat != null) heatColor(clamp01(heat * 0.7f + act * 0.3f)) else Rgb(0.1f, 0.65f, 1f).mix(CYAN, act)
            b.box(cx, -0.1f + h / 2, cz, cell * 0.8f, h, cell * 0.8f, col, 0.22f + 0.3f * act, 0.6f + 0.4f * act)
            b.point(cx, -0.1f + h + 0.05f, cz, 0.08f + 0.22f * act, col.scale(1.3f), 0.35f + 0.6f * act)
        }
    }
}

// ------------------------------------------------------------------ E. RAM
class RamScene : GlScene {
    private var shown = 0f
    private val cols = 20
    private val rows = 12

    override fun build(b: GlBatch, t: Float, dt: Float, d: SceneData) {
        val f = d.ramUsedFrac
        if (f != null) shown += (f - shown) * min(1f, dt * 3f)
        val total = cols * rows
        val usedCells = shown * total
        val full = floor(usedCells).toInt()
        val part = usedCells - full
        val col = when {
            d.lowMemory || shown > 0.88f -> RED
            shown > 0.7f -> AMBER
            else -> Rgb(0.1f, 0.65f, 1f)
        }
        val step = 0.2f
        for (i in 0 until total) {
            val x = (i % cols - (cols - 1) / 2f) * step
            val z = (i / cols - (rows - 1) / 2f) * step
            when {
                f == null -> b.box(x, 0.02f, z, 0.15f, 0.04f, 0.15f, GREY, 0.04f, 0.25f)
                i < full -> {
                    val h = 0.28f + 0.05f * sin(t * 2f + i * 0.7f)
                    b.box(x, h / 2, z, 0.15f, h, 0.15f, col, 0.2f, 0.65f)
                }
                i == full && part > 0.02f -> b.box(x, 0.14f * part, z, 0.15f, 0.28f * part, 0.15f, col, 0.2f, 0.7f)
                else -> b.box(x, 0.02f, z, 0.15f, 0.04f, 0.15f, Rgb(0.3f, 0.5f, 0.8f), 0.05f, 0.25f)
            }
        }
    }
}
