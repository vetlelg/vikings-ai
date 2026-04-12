package com.vikingsai.engine.entities

import com.vikingsai.common.model.*
import com.vikingsai.engine.world.MapGenerator
import com.vikingsai.engine.world.MutableEntity
import com.vikingsai.engine.world.WorldManager
import kotlin.random.Random

/**
 * Dragon roaming AI. The dragon moves toward the nearest agent
 * and is announced as a CRITICAL world event.
 */
class Dragon(private val world: WorldManager, private val rng: Random = Random.Default) {

    fun spawn(): WorldEvent? {
        if (world.entities.any { it.type == EntityType.DRAGON }) return null

        val walkable = MapGenerator.walkablePositions(world.grid)
        // Spawn at map edge, away from village
        val edgePositions = walkable.filter { it.x == 0 || it.x == 19 || it.y == 0 || it.y == 19 }
        val spawnPos = (edgePositions.ifEmpty { walkable }).random(rng)

        world.entities.add(MutableEntity(
            id = "dragon-${world.tick}",
            type = EntityType.DRAGON,
            position = spawnPos,
            health = 200
        ))

        return WorldEvent(
            tick = world.tick,
            eventType = EventType.DRAGON_SIGHTED,
            description = "A DRAGON has been spotted near the settlement! All Vikings must react!",
            severity = Severity.CRITICAL,
            affectedPositions = listOf(spawnPos)
        )
    }

    /**
     * Moves the dragon toward the nearest living agent.
     */
    fun update(): WorldEvent? {
        val dragon = world.entities.find { it.type == EntityType.DRAGON } ?: return null
        val aliveAgents = world.agents.filter { it.status == AgentStatus.ALIVE }
        if (aliveAgents.isEmpty()) return null

        val nearest = aliveAgents.minByOrNull { manhattanDist(dragon.position, it.position) } ?: return null
        val dx = nearest.position.x - dragon.position.x
        val dy = nearest.position.y - dragon.position.y

        // Move one step toward nearest agent
        val newPos = if (Math.abs(dx) > Math.abs(dy)) {
            Position(dragon.position.x + dx.coerceIn(-1, 1), dragon.position.y)
        } else {
            Position(dragon.position.x, dragon.position.y + dy.coerceIn(-1, 1))
        }

        // Clamp and check terrain
        val clampedPos = Position(
            newPos.x.coerceIn(0, world.grid[0].size - 1),
            newPos.y.coerceIn(0, world.grid.size - 1)
        )
        val terrain = world.grid[clampedPos.y][clampedPos.x]
        if (terrain != TerrainType.WATER) {
            dragon.position = clampedPos
        }

        // Check if dragon is near any agent (attacks automatically)
        val adjacentAgents = aliveAgents.filter { manhattanDist(dragon.position, it.position) <= 1 }
        for (agent in adjacentAgents) {
            val damage = 15 + rng.nextInt(10)
            agent.health -= damage
            if (agent.health <= 0) {
                agent.status = AgentStatus.DEAD
            }
        }

        return if (adjacentAgents.isNotEmpty()) {
            WorldEvent(
                tick = world.tick,
                eventType = EventType.COMBAT,
                description = "The dragon attacks ${adjacentAgents.joinToString { it.name }}!",
                severity = Severity.CRITICAL,
                affectedPositions = adjacentAgents.map { it.position } + dragon.position
            )
        } else null
    }

    private fun manhattanDist(a: Position, b: Position): Int =
        Math.abs(a.x - b.x) + Math.abs(a.y - b.y)
}
