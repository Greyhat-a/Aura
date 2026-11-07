package app.aura

import app.aura.core.ChargeMath
import org.junit.Assert.*
import org.junit.Test

class ChargeMathTest {
    @Test fun power_is_voltage_times_current() {
        assertEquals(8000f, ChargeMath.powerMw(4000, 2000f), 0.01f)
    }

    @Test fun full_capacity_estimate() {
        assertEquals(5000f, ChargeMath.estFullMah(2500f, 50)!!, 0.01f)
        assertNull(ChargeMath.estFullMah(0f, 50))
        assertNull(ChargeMath.estFullMah(100f, 5))
    }

    @Test fun eta_full() {
        // 1000 mAh left at 1000 mA = 1 h
        assertEquals(3_600_000L, ChargeMath.etaFullMs(5000f, 4000f, 1000f))
        assertNull(ChargeMath.etaFullMs(5000f, 4000f, 10f))
    }

    @Test fun eta_empty() {
        assertEquals(7_200_000L, ChargeMath.etaEmptyMs(2000f, 1000f))
        assertNull(ChargeMath.etaEmptyMs(2000f, 5f))
    }

    @Test fun median_and_jitter() {
        assertEquals(3f, ChargeMath.median(listOf(5f, 1f, 3f))!!, 0f)
        assertEquals(2.5f, ChargeMath.median(listOf(1f, 2f, 3f, 4f))!!, 0f)
        assertEquals(10f, ChargeMath.jitterMs(listOf(10f, 20f, 10f))!!, 0.001f)
        assertNull(ChargeMath.jitterMs(listOf(10f)))
    }

    @Test fun calibrator_standard_uA_positive_when_charging() {
        val c = ChargeMath.CurrentCalibrator()
        var last = 0f
        repeat(20) { last = c.normalise(1_500_000, charging = true, discharging = false) }
        assertEquals(1500f, last, 0.01f)
        assertFalse(c.flipped)
        assertFalse(c.inMilliamps)
    }

    @Test fun calibrator_detects_flipped_sign() {
        val c = ChargeMath.CurrentCalibrator()
        var last = 0f
        repeat(20) { last = c.normalise(-1_500_000, charging = true, discharging = false) }
        assertTrue(c.flipped)
        assertEquals(1500f, last, 0.01f)
    }

    @Test fun calibrator_detects_milliamps() {
        val c = ChargeMath.CurrentCalibrator()
        var last = 0f
        repeat(20) { last = c.normalise(800, charging = true, discharging = false) }
        assertTrue(c.inMilliamps)
        assertEquals(800f, last, 0.01f)
    }

    @Test fun adapter_share_is_lower_bound_and_requires_rating() {
        assertNull(ChargeMath.batterySharePct(5f, 0f))
        assertEquals(50f, ChargeMath.batterySharePct(5f, 10f)!!, 0.01f)
    }
}
