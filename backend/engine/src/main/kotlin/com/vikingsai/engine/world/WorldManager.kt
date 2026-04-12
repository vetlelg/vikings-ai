package com.vikingsai.engine.world

import com.vikingsai.common.model.*
import kotlin.random.Random

/**
 * Holds the mutable world state and applies changes each tick.
 */
class WorldManager(
    val grid: List<List<TerrainType>>,
    private val rng: Random = Random(System.currentTimeMillis())
) {
    var tick: Int = 0
        private set
    var timeOfDay: TimeOfDay = TimeOfDay.DAWN
        private set
    var weather: Weather = Weather.CLEAR
        private set

    val agents = mutableListOf<MutableAgent>()
    val entities = mutableListOf<MutableEntity>()
    val colonyResources = MutableColonyResources()

    private val width get() = grid[0].size
    private val height get() = grid.size

    fun advanceTick() {
        tick++
    }

    // --- Time of day: cycles every 20 ticks (DAWN 5, DAY 5, DUSK 5, NIGHT 5) ---

    fun updateTimeOfDay(): TimeOfDay? {
        val phase = (tick / 5) % 4
        val newTime = when (phase) {
            0 -> TimeOfDay.DAWN
            1 -> TimeOfDay.DAY
            2 -> TimeOfDay.DUSK
            else -> TimeOfDay.NIGHT
        }
        if (newTime != timeOfDay) {
            timeOfDay = newTime
            return newTime
        }
        return null
    }

    // --- Weather: random changes every ~30 ticks ---

    fun updateWeather(): Weather? {
        if (tick % 30 == 0 && tick > 0) {
            val newWeather = when {
                rng.nextFloat() < 0.5f -> Weather.CLEAR
                rng.nextFloat() < 0.7f -> Weather.SNOW
                else -> Weather.STORM
            }
            if (newWeather != weather) {
                weather = newWeather
                return newWeather
            }
        }
        return null
    }

    // --- Agent movement ---

    fun moveAgent(agent: MutableAgent, direction: String): Boolean {
        val (dx, dy) = when (direction.lowercase()) {
            "north" -> 0 to -1
            "south" -> 0 to 1
            "east" -> 1 to 0
            "west" -> -1 to 0
            else -> return false
        }
        val newX = (agent.position.x + dx).coerceIn(0, width - 1)
        val newY = (agent.position.y + dy).coerceIn(0, height - 1)
        val terrain = grid[newY][newX]
        if (terrain == TerrainType.WATER || terrain == TerrainType.MOUNTAIN) return false
        agent.position = Position(newX, newY)
        return true
    }

    // --- Resource gathering ---

    fun tryGather(agent: MutableAgent): ResourceType? {
        val terrain = grid[agent.position.y][agent.position.x]
        val resourceEntity = entities.find {
            it.type == EntityType.RESOURCE_NODE && it.position == agent.position
        }

        val resource = when {
            resourceEntity != null -> {
                val type = when (resourceEntity.subtype) {
                    "timber" -> ResourceType.TIMBER
                    "fish" -> ResourceType.FISH
                    "iron" -> ResourceType.IRON
                    "furs" -> ResourceType.FURS
                    else -> return null
                }
                entities.remove(resourceEntity)
                type
            }
            terrain == TerrainType.FOREST -> ResourceType.TIMBER
            terrain == TerrainType.WATER -> ResourceType.FISH // shouldn't happen (water not walkable)
            else -> return null
        }

        agent.inventory[resource] = (agent.inventory[resource] ?: 0) + 1
        return resource
    }

    // --- Deposit to colony ---

    fun depositResources(agent: MutableAgent) {
        for ((resource, amount) in agent.inventory) {
            when (resource) {
                ResourceType.TIMBER -> colonyResources.timber += amount
                ResourceType.FISH -> colonyResources.fish += amount
                ResourceType.IRON -> colonyResources.iron += amount
                ResourceType.FURS -> colonyResources.furs += amount
            }
        }
        agent.inventory.clear()
    }

    // --- Combat ---

    fun resolveCombat(agent: MutableAgent, target: MutableEntity): Boolean {
        // Agent attacks entity, simplified combat
        val damage = when (agent.role) {
            AgentRole.WARRIOR -> 40 + rng.nextInt(20)
            AgentRole.JARL -> 30 + rng.nextInt(15)
            else -> 15 + rng.nextInt(10)
        }
        target.health -= damage

        // Entity counterattacks
        val counterDamage = when (target.type) {
            EntityType.DRAGON -> 20 + rng.nextInt(15)
            EntityType.WOLF -> 10 + rng.nextInt(10)
            else -> 0
        }
        agent.health -= counterDamage

        if (agent.health <= 0) {
            agent.status = AgentStatus.DEAD
        }
        return target.health <= 0 // true if entity killed
    }

    // --- Health regen and respawn ---

    fun healAndRespawn() {
        for (agent in agents) {
            if (agent.status == AgentStatus.DEAD) {
                // Respawn after 10 ticks of being dead
                if (tick % 10 == 0) {
                    val villagePositions = MapGenerator.walkablePositions(grid)
                        .filter { grid[it.y][it.x] == TerrainType.VILLAGE }
                    if (villagePositions.isNotEmpty()) {
                        agent.position = villagePositions.random(rng)
                        agent.health = 100
                        agent.status = AgentStatus.ALIVE
                        agent.inventory.clear()
                    }
                }
                continue
            }
            // Slow heal when on village tiles
            if (grid[agent.position.y][agent.position.x] == TerrainType.VILLAGE && agent.health < 100) {
                agent.health = (agent.health + 5).coerceAtMost(100)
            }
            // Very slow passive regen
            if (agent.health < 100 && tick % 3 == 0) {
                agent.health = (agent.health + 1).coerceAtMost(100)
            }
        }
    }

    // --- Spawn resource nodes ---

    fun spawnResourceNodes(count: Int = 2) {
        val walkable = MapGenerator.walkablePositions(grid).filter { pos ->
            entities.none { it.position == pos } && agents.none { it.position == pos }
        }
        if (walkable.isEmpty()) return

        repeat(count.coerceAtMost(walkable.size)) {
            val pos = walkable.random(rng)
            val (subtype, terrainCheck) = when (rng.nextInt(4)) {
                0 -> "timber" to TerrainType.FOREST
                1 -> "fish" to TerrainType.BEACH
                2 -> "iron" to TerrainType.MOUNTAIN
                else -> "furs" to TerrainType.GRASS
            }
            // Place near appropriate terrain if possible, otherwise anywhere
            val nearbyPos = walkable.filter { p ->
                val neighbors = listOf(
                    Position(p.x - 1, p.y), Position(p.x + 1, p.y),
                    Position(p.x, p.y - 1), Position(p.x, p.y + 1)
                )
                neighbors.any { n ->
                    n.x in 0 until width && n.y in 0 until height && grid[n.y][n.x] == terrainCheck
                }
            }.ifEmpty { listOf(pos) }

            val finalPos = nearbyPos.random(rng)
            entities.add(MutableEntity(
                id = "resource-${tick}-${entities.size}",
                type = EntityType.RESOURCE_NODE,
                position = finalPos,
                subtype = subtype,
                health = 1
            ))
        }
    }

    // --- Snapshot for publishing ---

    fun toWorldState(): WorldState {
        return WorldState(
            tick = tick,
            grid = grid,
            agents = agents.map { it.toSnapshot() },
            entities = entities.map { it.toSnapshot() },
            colonyResources = colonyResources.toImmutable(),
            timeOfDay = timeOfDay,
            weather = weather,
            threats = entities
                .filter { it.type == EntityType.WOLF || it.type == EntityType.DRAGON }
                .map { ThreatSnapshot(
                    id = it.id,
                    type = it.type.name.lowercase(),
                    position = it.position,
                    severity = if (it.type == EntityType.DRAGON) Severity.CRITICAL else Severity.MEDIUM
                )}
        )
    }
}

// --- Mutable state classes (internal to engine) ---

data class MutableAgent(
    val name: String,
    val role: AgentRole,
    var position: Position,
    var health: Int = 100,
    val inventory: MutableMap<ResourceType, Int> = mutableMapOf(),
    var status: AgentStatus = AgentStatus.ALIVE
) {
    fun toSnapshot() = AgentSnapshot(
        name = name,
        role = role,
        position = position,
        health = health,
        inventory = inventory.toMap(),
        status = status
    )
}

data class MutableEntity(
    val id: String,
    val type: EntityType,
    var position: Position,
    val subtype: String? = null,
    var health: Int = 100
) {
    fun toSnapshot() = EntitySnapshot(
        id = id,
        type = type,
        position = position,
        subtype = subtype
    )
}

data class MutableColonyResources(
    var timber: Int = 0,
    var fish: Int = 0,
    var iron: Int = 0,
    var furs: Int = 0
) {
    fun toImmutable() = ColonyResources(timber, fish, iron, furs)
}
