package com.vikingsai.engine.entities

import com.vikingsai.common.model.*
import com.vikingsai.engine.world.MapGenerator
import com.vikingsai.engine.world.MutableEntity
import com.vikingsai.engine.world.WorldManager
import kotlin.random.Random

/**
 * Manages wolves, raids, and dragon scheduling.
 */
class ThreatManager(
    private val world: WorldManager,
    private val dragon: Dragon,
    private val rng: Random = Random.Default
) {
    // Dragon appears every ~60 ticks, stays until defeated or ~30 ticks
    private var dragonScheduledTick = 60 + rng.nextInt(20)
    private var dragonDespawnTick = -1

    // Wolves spawn every ~15 ticks
    private var nextWolfTick = 10 + rng.nextInt(10)

    // Raids every ~80 ticks
    private var nextRaidTick = 80 + rng.nextInt(30)

    fun update(): List<WorldEvent> {
        val events = mutableListOf<WorldEvent>()

        // Dragon scheduling
        if (world.tick >= dragonScheduledTick && world.entities.none { it.type == EntityType.DRAGON }) {
            dragon.spawn()?.let { events.add(it) }
            dragonDespawnTick = world.tick + 30 + rng.nextInt(20)
            dragonScheduledTick = world.tick + 80 + rng.nextInt(40)
        }

        // Dragon despawn if it's been around too long
        if (dragonDespawnTick > 0 && world.tick >= dragonDespawnTick) {
            world.entities.removeAll { it.type == EntityType.DRAGON }
            dragonDespawnTick = -1
        }

        // Dragon movement/combat
        dragon.update()?.let { events.add(it) }

        // Wolf spawning
        if (world.tick >= nextWolfTick) {
            spawnWolf()?.let { events.add(it) }
            nextWolfTick = world.tick + 12 + rng.nextInt(10)
        }

        // Wolf AI: move toward nearest agent
        updateWolves()

        // Raids
        if (world.tick >= nextRaidTick) {
            events.addAll(spawnRaid())
            nextRaidTick = world.tick + 70 + rng.nextInt(40)
        }

        return events
    }

    private fun spawnWolf(): WorldEvent? {
        val maxWolves = 3
        if (world.entities.count { it.type == EntityType.WOLF } >= maxWolves) return null

        val walkable = MapGenerator.walkablePositions(world.grid)
        val edgePositions = walkable.filter {
            it.x <= 2 || it.x >= 17 || it.y <= 2 || it.y >= 17
        }
        val pos = (edgePositions.ifEmpty { walkable }).random(rng)

        world.entities.add(MutableEntity(
            id = "wolf-${world.tick}",
            type = EntityType.WOLF,
            position = pos,
            health = 50
        ))

        return WorldEvent(
            tick = world.tick,
            eventType = EventType.WOLF_SPOTTED,
            description = "A wolf has been spotted near the settlement!",
            severity = Severity.MEDIUM,
            affectedPositions = listOf(pos)
        )
    }

    private fun updateWolves() {
        val wolves = world.entities.filter { it.type == EntityType.WOLF }
        val aliveAgents = world.agents.filter { it.status == AgentStatus.ALIVE }

        for (wolf in wolves) {
            if (aliveAgents.isEmpty()) break
            val nearest = aliveAgents.minByOrNull { manhattanDist(wolf.position, it.position) } ?: continue

            // Only move toward agents if within detection range
            if (manhattanDist(wolf.position, nearest.position) > 8) continue

            val dx = nearest.position.x - wolf.position.x
            val dy = nearest.position.y - wolf.position.y
            val newPos = if (Math.abs(dx) > Math.abs(dy)) {
                Position(wolf.position.x + dx.coerceIn(-1, 1), wolf.position.y)
            } else {
                Position(wolf.position.x, wolf.position.y + dy.coerceIn(-1, 1))
            }

            val clampedPos = Position(
                newPos.x.coerceIn(0, world.grid[0].size - 1),
                newPos.y.coerceIn(0, world.grid.size - 1)
            )
            val terrain = world.grid[clampedPos.y][clampedPos.x]
            if (terrain != TerrainType.WATER && terrain != TerrainType.MOUNTAIN) {
                wolf.position = clampedPos
            }

            // Wolf attacks if adjacent
            if (manhattanDist(wolf.position, nearest.position) <= 1) {
                val damage = 5 + rng.nextInt(8)
                nearest.health -= damage
                if (nearest.health <= 0) {
                    nearest.status = AgentStatus.DEAD
                }
            }
        }
    }

    private fun spawnRaid(): List<WorldEvent> {
        val events = mutableListOf<WorldEvent>()
        val walkable = MapGenerator.walkablePositions(world.grid)
        val edgePositions = walkable.filter { it.x >= 17 || it.y >= 17 }
        val pos = (edgePositions.ifEmpty { walkable }).random(rng)

        events.add(WorldEvent(
            tick = world.tick,
            eventType = EventType.RAID_INCOMING,
            description = "A rival clan is raiding the settlement!",
            severity = Severity.HIGH,
            affectedPositions = listOf(pos)
        ))

        // Spawn 2 wolves as "raiders" (simplified: raiders act like wolves)
        repeat(2) { i ->
            val offset = Position(
                (pos.x + rng.nextInt(-1, 2)).coerceIn(0, world.grid[0].size - 1),
                (pos.y + rng.nextInt(-1, 2)).coerceIn(0, world.grid.size - 1)
            )
            world.entities.add(MutableEntity(
                id = "raider-${world.tick}-$i",
                type = EntityType.WOLF,
                position = offset,
                health = 60
            ))
        }

        return events
    }

    private fun manhattanDist(a: Position, b: Position): Int =
        Math.abs(a.x - b.x) + Math.abs(a.y - b.y)
}
