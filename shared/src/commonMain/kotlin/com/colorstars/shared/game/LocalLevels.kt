package com.colorstars.shared.game

/**
 * Locally curated level artwork. Each character is a playable stained-glass fragment:
 * B=blue, G=gold, L=green, P=pink, V=purple, .=transparent.
 *
 * The owl is a compact stained-glass adaptation of the second concept illustration.
 * Future authored levels can be registered here without changing game progression.
 */
data class LocalLevel(val boardRows: Int, val boardColumns: Int, val glass: List<GlassPiece>)

object LocalLevels {
    private val owl = listOf(
        "VP....PV", // ear tufts
        "VPBBBBPV", // crown
        "GGGGGGGG", // golden face
        "GGVGGVGG", // eyes
        "BGGGGGGB", // cheeks and beak
        "BBPPPPBB", // wings and chest
        ".BVVVVB.", // violet feathers
        "LL.GG.LL", // feet and leaves
    )

    /** Only finished authored levels are counted; all subsequent levels are generated. */
    const val LAST_LOCAL_LEVEL = 1

    fun get(level: Int): LocalLevel? = when (level) {
        1 -> fromRows(owl)
        else -> null
    }

    private fun fromRows(rows: List<String>): LocalLevel {
        require(rows.isNotEmpty() && rows.all { it.length == rows.first().length })
        val glass = buildList {
            rows.forEachIndexed { row, line ->
                line.forEachIndexed { column, symbol ->
                    val color = when (symbol) {
                        'B' -> StarColor.BLUE
                        'G' -> StarColor.GOLD
                        'L' -> StarColor.GREEN
                        'P' -> StarColor.PINK
                        'V' -> StarColor.PURPLE
                        '.' -> null
                        else -> error("Unsupported level color: $symbol")
                    }
                    if (color != null) add(GlassPiece(size, row, column, color))
                }
            }
        }
        return LocalLevel(rows.size, rows.first().length, glass)
    }
}
