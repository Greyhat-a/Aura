package app.aura.core

/** One dashboard card: id, width (1 = small, 2 = medium, 3 = full row) and visibility. */
data class CardCfg(val id: String, val span: Int, val visible: Boolean)

object DashCards {
    val defaults: List<CardCfg> = listOf(
        CardCfg("BATTERY", 2, true), CardCfg("POWER", 1, true),
        CardCfg("DOWNUP", 1, true), CardCfg("CPU", 1, true), CardCfg("RAM", 1, true),
        CardCfg("TEMP", 1, true), CardCfg("CONN", 1, true), CardCfg("UPTIME", 1, true),
        CardCfg("STORAGE", 2, true),
        CardCfg("G_POWER", 2, true), CardCfg("G_TEMP", 2, true),
        CardCfg("G_CPU", 2, false), CardCfg("G_NET", 2, false), CardCfg("G_VOLT", 2, false),
    )
    val titles = mapOf(
        "BATTERY" to "Battery", "POWER" to "Charging power", "DOWNUP" to "Live traffic",
        "CPU" to "CPU", "RAM" to "RAM", "TEMP" to "Battery temperature", "CONN" to "Connection",
        "UPTIME" to "Uptime", "STORAGE" to "Storage", "G_POWER" to "Power graph",
        "G_TEMP" to "Temperature graph", "G_CPU" to "CPU graph", "G_NET" to "Traffic graph",
        "G_VOLT" to "Voltage graph",
    )

    fun encode(l: List<CardCfg>) = l.joinToString(";") { "${it.id}:${it.span}:${if (it.visible) 1 else 0}" }

    /** Decodes a stored layout; unknown ids are dropped, newly introduced ids are appended hidden. */
    fun decode(s: String?): List<CardCfg> {
        if (s.isNullOrBlank()) return defaults
        val out = s.split(";").mapNotNull {
            val p = it.split(":")
            if (p.size != 3 || p[0] !in titles) null
            else CardCfg(p[0], p[1].toIntOrNull()?.coerceIn(1, 3) ?: 1, p[2] == "1")
        }.distinctBy { it.id }.toMutableList()
        defaults.filter { d -> out.none { it.id == d.id } }.forEach { out += it.copy(visible = false) }
        return out
    }

    /** Moves a visible card one visible position earlier (dir = -1) or later (dir = +1). */
    fun move(list: List<CardCfg>, id: String, dir: Int): List<CardCfg> {
        val vis = list.filter { it.visible }
        val i = vis.indexOfFirst { it.id == id }
        val j = i + dir
        if (i < 0 || j !in vis.indices) return list
        val a = list.indexOfFirst { it.id == id }
        val b = list.indexOfFirst { it.id == vis[j].id }
        val m = list.toMutableList()
        m[a] = list[b]; m[b] = list[a]
        return m
    }

    /** Small -> medium -> full row -> small. */
    fun cycleSpan(list: List<CardCfg>, id: String): List<CardCfg> =
        list.map { if (it.id == id) it.copy(span = it.span % 3 + 1) else it }

    fun setVisible(list: List<CardCfg>, id: String, visible: Boolean): List<CardCfg> {
        val target = list.firstOrNull { it.id == id } ?: return list
        // A card that is shown again goes to the end of the visible cards.
        return if (visible && !target.visible) list.filter { it.id != id } + target.copy(visible = true)
        else list.map { if (it.id == id) it.copy(visible = visible) else it }
    }

    /** Grid span actually used for [span] on a grid with [cols] columns. */
    fun effectiveSpan(span: Int, cols: Int): Int = when (span) {
        1 -> 1
        2 -> minOf(2, cols)
        else -> cols
    }
}
