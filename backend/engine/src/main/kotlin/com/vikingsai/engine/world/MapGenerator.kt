package com.vikingsai.engine.world

import com.vikingsai.common.model.Position
import com.vikingsai.common.model.TerrainType
import com.vikingsai.common.model.TerrainType.*
import kotlin.random.Random

/**
 * Generates a 20x20 Viking coastline map.
 *
 * Layout (x = col, y = row, origin top-left):
 *   - West edge (x=0..2): fjord (water) with irregular coastline
 *   - x=2..4: beach strip along the water's edge
 *   - North (y=0..3): mountain range
 *   - Center (x=8..12, y=8..12): village clearing
 *   - Scattered: forest clusters and grass plains
 */
object MapGenerator {

    fun generate(width: Int = 20, height: Int = 20, seed: Long = 42): List<List<TerrainType>> {
        val rng = Random(seed)
        val grid = Array(height) { Array(width) { GRASS } }

        // 1. Fjord — water along the west edge with irregular coastline
        for (y in 0 until height) {
            val waterWidth = 2 + (Math.sin(y * 0.7 + seed.toDouble() * 0.1) * 1.2).toInt().coerceIn(0, 2)
            for (x in 0 until waterWidth.coerceAtMost(width)) {
                grid[y][x] = WATER
            }
        }

        // 2. Beach — strip next to water
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (grid[y][x] == WATER) continue
                val hasWaterNeighbor = neighbors(x, y, width, height).any { (nx, ny) -> grid[ny][nx] == WATER }
                if (hasWaterNeighbor) {
                    grid[y][x] = BEACH
                }
            }
        }

        // 3. Mountain range along the north
        for (y in 0..3) {
            for (x in 0 until width) {
                if (grid[y][x] != WATER && grid[y][x] != BEACH) {
                    val isMountain = when (y) {
                        0 -> x > 4 && rng.nextFloat() < 0.8f
                        1 -> x > 3 && rng.nextFloat() < 0.7f
                        2 -> x > 5 && rng.nextFloat() < 0.4f
                        3 -> x > 6 && rng.nextFloat() < 0.2f
                        else -> false
                    }
                    if (isMountain) grid[y][x] = MOUNTAIN
                }
            }
        }

        // 4. Village clearing in the center
        for (y in 8..12) {
            for (x in 8..12) {
                if (grid[y][x] == GRASS) {
                    grid[y][x] = VILLAGE
                }
            }
        }

        // 5. Forest clusters scattered around
        val forestSeeds = listOf(
            Position(5, 6), Position(14, 5), Position(6, 14),
            Position(16, 10), Position(12, 16), Position(4, 10),
            Position(17, 3), Position(15, 15), Position(7, 18)
        )
        for (center in forestSeeds) {
            val radius = rng.nextInt(1, 4)
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    val nx = center.x + dx
                    val ny = center.y + dy
                    if (nx in 0 until width && ny in 0 until height
                        && grid[ny][nx] == GRASS
                        && rng.nextFloat() < 0.7f
                    ) {
                        grid[ny][nx] = FOREST
                    }
                }
            }
        }

        return grid.map { it.toList() }
    }

    /**
     * Returns walkable positions (not water, not mountain) for spawning agents/entities.
     */
    fun walkablePositions(grid: List<List<TerrainType>>): List<Position> {
        val positions = mutableListOf<Position>()
        for (y in grid.indices) {
            for (x in grid[y].indices) {
                if (grid[y][x] != WATER && grid[y][x] != MOUNTAIN) {
                    positions.add(Position(x, y))
                }
            }
        }
        return positions
    }

    private fun neighbors(x: Int, y: Int, width: Int, height: Int): List<Pair<Int, Int>> {
        return listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)
            .filter { (nx, ny) -> nx in 0 until width && ny in 0 until height }
    }
}
