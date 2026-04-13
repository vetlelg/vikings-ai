package com.vikingsai.engine.world

import com.vikingsai.common.model.Position
import com.vikingsai.common.model.TerrainType
import com.vikingsai.common.model.TerrainType.*
import kotlin.random.Random

/**
 * Generates a Viking coastline map at any size.
 *
 * Layout (x = col, y = row, origin top-left):
 *   - West edge: fjord (water) with irregular coastline
 *   - Adjacent to water: beach strip
 *   - North: mountain range
 *   - Center: village clearing
 *   - Scattered: forest clusters and grass plains
 */
object MapGenerator {

    fun generate(width: Int = 40, height: Int = 40, seed: Long = 42): List<List<TerrainType>> {
        val rng = Random(seed)
        val grid = Array(height) { Array(width) { GRASS } }

        // 1. Fjord — water along the west edge with irregular coastline
        for (y in 0 until height) {
            val baseWidth = (3 * width) / 20
            val waterWidth = baseWidth + (Math.sin(y * 0.7 + seed.toDouble() * 0.1) * 1.2).toInt().coerceIn(0, 2)
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

        // 3. Mountain range along the north (scales with height)
        val mountainRows = (height * 4) / 20
        for (y in 0 until mountainRows) {
            for (x in 0 until width) {
                if (grid[y][x] != WATER && grid[y][x] != BEACH) {
                    val fraction = y.toFloat() / mountainRows
                    val minX = (width * (3 + y)) / 20
                    val chance = 0.85f - fraction * 0.6f
                    if (x > minX && rng.nextFloat() < chance) grid[y][x] = MOUNTAIN
                }
            }
        }

        // 4. Village clearing in the center
        val villageRadius = 2 + width / 20  // grows slightly with map size
        val cx = width / 2
        val cy = height / 2
        for (y in (cy - villageRadius)..(cy + villageRadius)) {
            for (x in (cx - villageRadius)..(cx + villageRadius)) {
                if (x in 0 until width && y in 0 until height && grid[y][x] == GRASS) {
                    grid[y][x] = VILLAGE
                }
            }
        }

        // 5. Forest clusters scattered proportionally
        val forestCount = (width * height) / 40
        for (i in 0 until forestCount) {
            val fx = rng.nextInt(4, width - 2)
            val fy = rng.nextInt(mountainRows + 1, height - 1)
            val radius = rng.nextInt(1, 2 + width / 20)
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    val nx = fx + dx
                    val ny = fy + dy
                    if (nx in 0 until width && ny in 0 until height
                        && grid[ny][nx] == GRASS
                        && rng.nextFloat() < 0.65f
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
