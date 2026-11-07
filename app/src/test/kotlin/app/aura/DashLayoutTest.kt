package app.aura

import app.aura.core.CardCfg
import app.aura.core.DashCards
import org.junit.Assert.*
import org.junit.Test

class DashLayoutTest {
    private val base = DashCards.defaults

    @Test fun encode_decode_roundtrip() {
        val enc = DashCards.encode(base)
        assertEquals(base, DashCards.decode(enc))
    }

    @Test fun decode_drops_unknown_and_appends_new_hidden() {
        val l = DashCards.decode("BATTERY:2:1;NOPE:1:1;CPU:9:0")
        assertTrue(l.none { it.id == "NOPE" })
        assertEquals(3, l.first { it.id == "CPU" }.span)          // clamped to 1..3
        assertFalse(l.first { it.id == "CPU" }.visible)
        assertEquals(DashCards.defaults.size, l.size)
        assertFalse(l.first { it.id == "RAM" }.visible)           // introduced later -> hidden until user adds it
        assertEquals(DashCards.defaults, DashCards.decode(null))
    }

    @Test fun move_swaps_with_neighbouring_visible_card_skipping_hidden() {
        val l = listOf(CardCfg("A", 1, true), CardCfg("H", 1, false), CardCfg("B", 1, true))
            .let { it.map { c -> CardCfg(if (c.id == "A") "BATTERY" else if (c.id == "B") "CPU" else "RAM", 1, c.visible) } }
        val moved = DashCards.move(l, "CPU", -1)
        assertEquals(listOf("CPU", "RAM", "BATTERY"), moved.map { it.id })
        assertSame(l, DashCards.move(l, "BATTERY", -1))          // already first -> unchanged
    }

    @Test fun cycle_span_and_visibility() {
        var l = base
        l = DashCards.cycleSpan(l, "POWER"); assertEquals(2, l.first { it.id == "POWER" }.span)
        l = DashCards.cycleSpan(l, "POWER"); assertEquals(3, l.first { it.id == "POWER" }.span)
        l = DashCards.cycleSpan(l, "POWER"); assertEquals(1, l.first { it.id == "POWER" }.span)
        l = DashCards.setVisible(l, "POWER", false); assertFalse(l.first { it.id == "POWER" }.visible)
        l = DashCards.setVisible(l, "POWER", true); assertEquals("POWER", l.last().id)
        assertTrue(l.last().visible)
    }

    @Test fun effective_span() {
        assertEquals(1, DashCards.effectiveSpan(1, 4))
        assertEquals(2, DashCards.effectiveSpan(2, 4))
        assertEquals(4, DashCards.effectiveSpan(3, 4))
        assertEquals(2, DashCards.effectiveSpan(2, 2))
        assertEquals(2, DashCards.effectiveSpan(3, 2))
    }
}
