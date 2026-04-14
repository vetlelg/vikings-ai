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
    var gameStatus: GameStatus = GameStatus.IN_PROGRESS

    val agents = mutableListOf<MutableAgent>()
    val entities = mutableListOf<MutableEntity>()
    val colonyResources = MutableColonyResources()

    private val width get() = grid[0].size
    private val height get() = grid.size

    companion object {
        const val VOYAGE_TIMBER = 50
        const val VOYAGE_IRON = 30
        const val VOYAGE_FURS = 20
    }

    val voyageGoal = ColonyResources(timber = VOYAGE_TIMBER, fish = 0, iron = VOYAGE_IRON, furs = VOYAGE_FURS)

    fun checkVoyageComplete(): Boolean =
        colonyResources.timber >= VOYAGE_TIMBER &&
        colonyResources.iron >= VOYAGE_IRON &&
        colonyResources.furs >= VOYAGE_FURS

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

    // --- Occupancy check ---

    fun isOccupied(pos: Position, excludeAgentName: String? = null, excludeEntityId: String? = null): Boolean {
        if (agents.any { it.status == AgentStatus.ALIVE && it.position == pos && it.name != excludeAgentName }) return true
        // Only threats (wolves, dragon) block movement — resource nodes don't
        if (entities.any { it.position == pos && it.id != excludeEntityId && it.type != EntityType.RESOURCE_NODE }) return true
        return false
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
        val newPos = Position(newX, newY)
        val terrain = grid[newY][newX]
        if (terrain == TerrainType.WATER) return false
        if (isOccupied(newPos, excludeAgentName = agent.name)) return false
        agent.position = newPos
        return true
    }

    // --- Resource gathering: AoE-style from adjacent resource sources ---

    fun tryGather(agent: MutableAgent): ResourceType? {
        // Find nearest adjacent resource source (dist <= 1)
        val source = entities
            .filter { it.type == EntityType.RESOURCE_NODE && it.health > 0 }
            .filter { manhattanDist(agent.position, it.position) <= 1 }
            .minByOrNull { manhattanDist(agent.position, it.position) }
            ?: return null

        val resource = when (source.subtype) {
            "tree" -> ResourceType.TIMBER
            "mine" -> ResourceType.IRON
            "fishing_spot" -> ResourceType.FISH
            "hunting_ground" -> ResourceType.FURS
            else -> return null
        }

        val gatherAmount = 2
        source.health -= gatherAmount
        agent.inventory[resource] = (agent.inventory[resource] ?: 0) + gatherAmount

        // Remove depleted source
        if (source.health <= 0) {
            entities.remove(source)
        }

        return resource
    }

    private fun manhattanDist(a: Position, b: Position): Int =
        Math.abs(a.x - b.x) + Math.abs(a.y - b.y)

    // --- Deposit to colony ---

    fun depositResources(agent: MutableAgent) {
        for ((resource, amount) in agent.inventory) {
            when (resource) {
                ResourceType.TIMBER -> colonyResources.timber += amount
                ResourceType.FISH -> colonyResources.fish += amount
                ResourceType.IRON -> colonyResources.iron += amount
                ResourceType.FURS -> colonyResources.furs += amount
            }
            agent.totalDeposited[resource] = (agent.totalDeposited[resource] ?: 0) + amount
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

    // --- Auto-retaliation when attacked by threats ---

    fun autoRetaliate(agent: MutableAgent, attacker: MutableEntity): Boolean {
        if (agent.status != AgentStatus.ALIVE) return false
        // Reduced damage — defensive response, not a full attack
        val damage = when (agent.role) {
            AgentRole.WARRIOR -> 25 + rng.nextInt(15)   // 25-39
            AgentRole.JARL -> 20 + rng.nextInt(10)       // 20-29
            else -> 8 + rng.nextInt(7)                    // 8-14
        }
        attacker.health -= damage
        agent.currentAction = ActionType.FIGHT
        return attacker.health <= 0
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
                        agent.deaths++
                    }
                }
                continue
            }
            // Heal when on village tiles
            if (grid[agent.position.y][agent.position.x] == TerrainType.VILLAGE && agent.health < 100) {
                agent.health = (agent.health + 8).coerceAtMost(100)
            }
            // Passive regen every 2 ticks
            if (agent.health < 100 && tick % 2 == 0) {
                agent.health = (agent.health + 2).coerceAtMost(100)
            }
        }
    }

    // --- Spawn resource sources (regrowth / replenishment) ---

    fun spawnResourceSources(count: Int = 2) {
        val occupied = entities.map { it.position }.toSet() + agents.map { it.position }.toSet()

        // Determine what the colony needs most
        data class SpawnDef(val subtype: String, val terrain: TerrainType, val health: Int, val adjacentTo: TerrainType? = null)
        val defs = listOf(
            SpawnDef("tree", TerrainType.FOREST, 10),
            SpawnDef("mine", TerrainType.GRASS, 8, adjacentTo = TerrainType.MOUNTAIN),
            SpawnDef("fishing_spot", TerrainType.BEACH, 12, adjacentTo = TerrainType.WATER),
            SpawnDef("hunting_ground", TerrainType.GRASS, 6)
        )

        var spawned = 0
        repeat(count) {
            if (spawned >= count) return
            val def = defs[rng.nextInt(defs.size)]

            val candidates = (0 until height).flatMap { y ->
                (0 until width).mapNotNull { x ->
                    val pos = Position(x, y)
                    if (pos in occupied) return@mapNotNull null
                    if (entities.any { it.position == pos }) return@mapNotNull null

                    val onTerrain = grid[y][x] == def.terrain
                    val adjOk = def.adjacentTo == null || listOf(
                        Position(x - 1, y), Position(x + 1, y),
                        Position(x, y - 1), Position(x, y + 1)
                    ).any { n ->
                        n.x in 0 until width && n.y in 0 until height && grid[n.y][n.x] == def.adjacentTo
                    }
                    if (onTerrain && adjOk) pos else null
                }
            }

            if (candidates.isNotEmpty()) {
                val pos = candidates.random(rng)
                entities.add(MutableEntity(
                    id = "${def.subtype}-${tick}-${entities.size}",
                    type = EntityType.RESOURCE_NODE,
                    position = pos,
                    subtype = def.subtype,
                    health = def.health,
                    maxHealth = def.health
                ))
                spawned++
            }
        }
    }

    // --- Replenish fishing spots over time ---

    fun replenishFishingSpots() {
        for (entity in entities) {
            if (entity.subtype == "fishing_spot" && entity.health < entity.maxHealth) {
                entity.health = (entity.health + 1).coerceAtMost(entity.maxHealth)
            }
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
                )},
            gameStatus = gameStatus,
            voyageGoal = voyageGoal
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
    var status: AgentStatus = AgentStatus.ALIVE,
    var currentAction: ActionType? = null,
    var currentDirection: String? = null,
    var currentTask: AgentTask? = null,
    var kills: Int = 0,
    var deaths: Int = 0,
    val totalDeposited: MutableMap<ResourceType, Int> = mutableMapOf()
) {
    fun toSnapshot() = AgentSnapshot(
        name = name,
        role = role,
        position = position,
        health = health,
        inventory = inventory.toMap(),
        status = status,
        currentAction = currentAction,
        currentDirection = currentDirection,
        currentTaskType = currentTask?.taskType,
        currentTaskReasoning = currentTask?.reasoning,
        kills = kills,
        deaths = deaths,
        totalDeposited = totalDeposited.toMap()
    )
}

data class MutableEntity(
    val id: String,
    val type: EntityType,
    var position: Position,
    val subtype: String? = null,
    var health: Int = 100,
    val maxHealth: Int = health
) {
    fun toSnapshot() = EntitySnapshot(
        id = id,
        type = type,
        position = position,
        subtype = subtype,
        remaining = health,
        capacity = maxHealth
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
