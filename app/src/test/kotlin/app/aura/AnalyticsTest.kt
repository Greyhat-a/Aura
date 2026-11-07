package app.aura

import app.aura.core.Analytics
import app.aura.core.Csv
import org.junit.Assert.*
import org.junit.Test

class AnalyticsTest {
    private fun pts(n: Int, cap: Float, start: Long = 100_000L) =
        (0 until n).map { Analytics.CapPoint(start + it * 3L, cap + (it % 3) * 10f) }

    @Test fun degradation_needs_enough_data() {
        assertNull(Analytics.degradation(pts(5, 5000f), 5100))
        // 12 buckets but all within the same day
        assertNull(Analytics.degradation((0 until 12).map { Analytics.CapPoint(1000L + it, 5000f) }, 5100))
    }

    @Test fun degradation_reports_percentage_of_user_rating_only() {
        val d = Analytics.degradation(pts(60, 4900f), 5100)!!
        assertEquals(4910f, d.medianFullMah, 15f)
        assertEquals(96.3f, d.pctOfRated!!, 0.5f)
        assertNotEquals("High", d.confidence)
        val noRating = Analytics.degradation(pts(60, 4900f), 0)!!
        assertNull(noRating.pctOfRated)
        assertNull(noRating.ratedMah)
    }

    @Test fun coulomb_consistency_matches_when_gauge_is_coherent() {
        // 1 hour at 1500 mA, sampled every 30 s => 1500 mAh in; counter rises 1500 mAh.
        val s = (0..120).map { Analytics.Sample(it * 30_000L, 1500f, 2000f + it * 12.5f) }
        val c = Analytics.coulombConsistency(s)!!
        assertEquals(1500f, c.integratedMah, 1f)
        assertEquals(100f, c.ratioPct, 0.5f)
    }

    @Test fun coulomb_consistency_detects_inconsistent_counter_and_ignores_gaps() {
        val s = (0..120).map { Analytics.Sample(it * 30_000L, 1500f, 2000f + it * 6.25f) }
        assertEquals(50f, Analytics.coulombConsistency(s)!!.ratioPct, 0.5f)
        // sleep gap of 1 h between two samples must not be integrated
        val gap = listOf(Analytics.Sample(0, 1500f, 100f), Analytics.Sample(3_600_000L, 1500f, 1600f))
        assertNull(Analytics.coulombConsistency(gap))
        // tiny charge movement -> not enough to compare
        assertNull(Analytics.coulombConsistency(listOf(Analytics.Sample(0, 100f, 1f), Analytics.Sample(60_000, 100f, 2f))))
    }

    @Test fun temperature_warnings() {
        assertTrue(Analytics.temperatureWarnings(30f, 42f, "Good", 31f).isEmpty())
        assertEquals(3, Analytics.temperatureWarnings(46f, 42f, "Good", null).single().level)
        assertEquals(2, Analytics.temperatureWarnings(43f, 42f, "Good", null).single().level)
        assertEquals(1, Analytics.temperatureWarnings(30f, 42f, "Good", 41f).single().level)
        assertEquals(3, Analytics.temperatureWarnings(null, 42f, "Overheat", null).single().level)
        assertTrue(Analytics.temperatureWarnings(null, 42f, "Unknown", null).isEmpty())
    }

    @Test fun csv_escaping_and_formatting() {
        assertEquals("a,\"b,c\",\"he said \"\"hi\"\"\",,1,2.5\r\n", Csv.row("a", "b,c", "he said \"hi\"", null, true, 2.5f))
        assertEquals("3", Csv.cell(3.0f))
        assertEquals("0.1235", Csv.cell(0.123456))
    }
}
