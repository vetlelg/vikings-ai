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
    // Dragon appears every ~100 ticks, stays until defeated or ~30 ticks
    private var dragonScheduledTick = 100 + rng.nextInt(30)
    private var dragonDespawnTick = -1

    // Wolves spawn every ~25 ticks, first one delayed
    private var nextWolfTick = 30 + rng.nextInt(15)

    // Raids every ~100 ticks
    private var nextRaidTick = 100 + rng.nextInt(40)

    fun update(): List<WorldEvent> {
        val events = mutableListOf<WorldEvent>()

        // Dragon scheduling
        if (world.tick >= dragonScheduledTick && world.entities.none { it.type == EntityType.DRAGON }) {
            dragon.spawn()?.let { events.add(it) }
            dragonDespawnTick = world.tick + 30 + rng.nextInt(20)
            dragonScheduledTick = world.tick + 120 + rng.nextInt(40)
        }

        // Dragon despawn if it's been around too long
        if (dragonDespawnTick > 0 && world.tick >= dragonDespawnTick) {
            world.entities.removeAll { it.type == EntityType.DRAGON }
            dragonDespawnTick = -1
        }

        // Dragon movement/combat
        events.addAll(dragon.update())

        // Wolf spawning
        if (world.tick >= nextWolfTick) {
            spawnWolf()?.let { events.add(it) }
            nextWolfTick = world.tick + 20 + rng.nextInt(15)
        }

        // Wolf AI: move toward nearest agent, attack + get retaliated
        events.addAll(updateWolves())

        // Raids
        if (world.tick >= nextRaidTick) {
            events.addAll(spawnRaid())
            nextRaidTick = world.tick + 100 + rng.nextInt(40)
        }

        return events
    }

    private fun spawnWolf(): WorldEvent? {
        val maxWolves = 3
        if (world.entities.count { it.type == EntityType.WOLF } >= maxWolves) return null

        val walkable = MapGenerator.walkablePositions(world.grid)
        val maxX = world.grid[0].size - 1
        val maxY = world.grid.size - 1
        val edgePositions = walkable.filter {
            it.x <= 2 || it.x >= maxX - 2 || it.y <= 2 || it.y >= maxY - 2
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

    private fun updateWolves(): List<WorldEvent> {
        val events = mutableListOf<WorldEvent>()
        val wolves = world.entities.filter { it.type == EntityType.WOLF }
        val aliveAgents = world.agents.filter { it.status == AgentStatus.ALIVE }
        val deadWolves = mutableListOf<MutableEntity>()

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
            if (terrain != TerrainType.WATER
                && !world.isOccupied(clampedPos, excludeEntityId = wolf.id)) {
                wolf.position = clampedPos
            }

            // Wolf attacks if adjacent
            if (manhattanDist(wolf.position, nearest.position) <= 1) {
                val damage = 5 + rng.nextInt(8)
                nearest.health -= damage

                events.add(WorldEvent(
                    tick = world.tick,
                    eventType = EventType.COMBAT,
                    description = "A wolf attacks ${nearest.name}! (${nearest.name} HP: ${nearest.health})",
                    severity = Severity.HIGH,
                    affectedPositions = listOf(wolf.position, nearest.position)
                ))

                if (nearest.health <= 0) {
                    nearest.status = AgentStatus.DEAD
                    events.add(WorldEvent(
                        tick = world.tick,
                        eventType = EventType.AGENT_DIED,
                        description = "${nearest.name} has been slain by a wolf!",
                        severity = Severity.CRITICAL,
                        affectedPositions = listOf(nearest.position)
                    ))
                } else {
                    // Agent auto-retaliates
                    val killed = world.autoRetaliate(nearest, wolf)
                    if (killed) {
                        nearest.kills++
                        deadWolves.add(wolf)
                        events.add(WorldEvent(
                            tick = world.tick,
                            eventType = EventType.COMBAT,
                            description = "${nearest.name} fights back and slays the wolf!",
                            severity = Severity.HIGH,
                            affectedPositions = listOf(wolf.position)
                        ))
                    }
                }
            }
        }

        // Remove dead wolves after iteration
        world.entities.removeAll { it in deadWolves }

        return events
    }

    private fun spawnRaid(): List<WorldEvent> {
        val events = mutableListOf<WorldEvent>()
        val walkable = MapGenerator.walkablePositions(world.grid)
        val rMaxX = world.grid[0].size - 3
        val rMaxY = world.grid.size - 3
        val edgePositions = walkable.filter { it.x >= rMaxX || it.y >= rMaxY }
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
