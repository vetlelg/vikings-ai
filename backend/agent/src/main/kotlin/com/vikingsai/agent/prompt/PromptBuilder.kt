package com.vikingsai.agent.prompt

import com.vikingsai.common.model.*
import kotlin.math.abs

object PromptBuilder {

    fun buildSystemPrompt(name: String, role: AgentRole, personality: String): String {
        return """
You are $name, a Viking ${role.name.lowercase()} in a Norse settlement on a fjord coastline.

$personality

WORLD RULES:
- You are on a grid. Terrain types: GRASS, FOREST, WATER (impassable), MOUNTAIN (impassable), BEACH, VILLAGE.
- The colony has shared resources: timber, fish, iron, furs.
- Threats include wolves, rival raiders, and a dragon.
- Time cycles through DAWN, DAY, DUSK, NIGHT. Weather can be CLEAR, SNOW, or STORM.
- COLONY GOAL: Build a longship. The colony needs 50 timber, 30 iron, and 20 furs deposited at the village. Gather resources and deposit them with the "build" action on VILLAGE tiles.

RESPOND WITH EXACTLY ONE JSON ACTION. Available actions:
- {"action":"move","direction":"north|south|east|west","reasoning":"why"}
- {"action":"gather","reasoning":"why"} — pick up resources at your location (works on FOREST for timber, or resource nodes)
- {"action":"fight","reasoning":"why"} — attack nearest threat
- {"action":"build","reasoning":"why"} — deposit resources at VILLAGE
- {"action":"patrol","direction":"north|south|east|west","reasoning":"why"} — patrol an area
- {"action":"flee","direction":"north|south|east|west","reasoning":"why"} — run from danger
- {"action":"speak","reasoning":"what you say"} — speak to the settlement
- {"action":"idle","reasoning":"why"}

Keep the "reasoning" field SHORT — at most 10 words, like a quick thought.
RESPOND WITH ONLY THE JSON. No markdown, no explanation outside the JSON.
        """.trimIndent()
    }

    fun buildUserPrompt(
        name: String,
        worldState: WorldState,
        recentEvents: List<WorldEvent>,
        recentActions: List<AgentAction> = emptyList()
    ): String {
        val agent = worldState.agents.find { it.name == name }
            ?: return "You are not in the world. Wait."

        val sb = StringBuilder()

        // Agent status
        sb.appendLine("=== YOUR STATUS ===")
        sb.appendLine("Position: (${agent.position.x}, ${agent.position.y})")
        sb.appendLine("Health: ${agent.health}/100")
        sb.appendLine("Inventory: ${if (agent.inventory.isEmpty()) "empty" else agent.inventory.entries.joinToString { "${it.value} ${it.key.name.lowercase()}" }}")
        sb.appendLine("Standing on: ${worldState.grid[agent.position.y][agent.position.x]}")
        sb.appendLine()

        // Recent actions (short-term memory)
        if (recentActions.isNotEmpty()) {
            sb.appendLine("=== YOUR RECENT ACTIONS ===")
            for (a in recentActions) {
                sb.appendLine("Tick ${a.tick}: ${a.action}${if (a.direction != null) " ${a.direction}" else ""} — ${a.reasoning}")
            }
            sb.appendLine()
        }

        // World conditions
        sb.appendLine("=== WORLD ===")
        sb.appendLine("Tick: ${worldState.tick} | Time: ${worldState.timeOfDay} | Weather: ${worldState.weather}")
        sb.appendLine("Colony resources: timber=${worldState.colonyResources.timber}, fish=${worldState.colonyResources.fish}, iron=${worldState.colonyResources.iron}, furs=${worldState.colonyResources.furs}")
        sb.appendLine("Longship progress: ${worldState.colonyResources.timber}/${worldState.voyageGoal.timber} timber, ${worldState.colonyResources.iron}/${worldState.voyageGoal.iron} iron, ${worldState.colonyResources.furs}/${worldState.voyageGoal.furs} furs")
        sb.appendLine()

        // Key landmarks — direction and distance to important locations
        sb.appendLine("=== KEY LOCATIONS ===")
        val width = worldState.grid[0].size
        val height = worldState.grid.size
        val villageCenterX = width / 2
        val villageCenterY = height / 2
        sb.appendLine("Village center: (${villageCenterX}, ${villageCenterY}) ${directionTo(agent.position, Position(villageCenterX, villageCenterY))}")
        findNearest(agent.position, worldState.grid, TerrainType.FOREST)?.let {
            sb.appendLine("Nearest forest: (${it.x}, ${it.y}) ${directionTo(agent.position, it)}")
        }
        findNearest(agent.position, worldState.grid, TerrainType.BEACH)?.let {
            sb.appendLine("Nearest beach: (${it.x}, ${it.y}) ${directionTo(agent.position, it)}")
        }
        // Nearest resource node
        worldState.entities.filter { it.type == EntityType.RESOURCE_NODE }
            .minByOrNull { abs(it.position.x - agent.position.x) + abs(it.position.y - agent.position.y) }
            ?.let {
                val dist = abs(it.position.x - agent.position.x) + abs(it.position.y - agent.position.y)
                sb.appendLine("Nearest resource node: ${it.subtype ?: "unknown"} at (${it.position.x}, ${it.position.y}) ${directionTo(agent.position, it.position)}")
            }
        sb.appendLine()

        // Nearby terrain (11x11 around agent)
        sb.appendLine("=== NEARBY TERRAIN (11x11) ===")
        for (dy in -5..5) {
            val row = StringBuilder()
            for (dx in -5..5) {
                val nx = agent.position.x + dx
                val ny = agent.position.y + dy
                if (nx in 0 until width && ny in 0 until height) {
                    val t = worldState.grid[ny][nx].name.take(3)
                    row.append(if (dx == 0 && dy == 0) "[$t]" else " $t ")
                } else {
                    row.append(" ?? ")
                }
            }
            sb.appendLine(row)
        }
        sb.appendLine()

        // Other agents
        sb.appendLine("=== OTHER VIKINGS ===")
        for (other in worldState.agents.filter { it.name != name }) {
            val dist = abs(other.position.x - agent.position.x) + abs(other.position.y - agent.position.y)
            sb.appendLine("${other.name} (${other.role}) at (${other.position.x},${other.position.y}) dist=$dist HP=${other.health} ${other.status}")
        }
        sb.appendLine()

        // Nearby entities (expanded range)
        val nearbyEntities = worldState.entities.filter { e ->
            abs(e.position.x - agent.position.x) + abs(e.position.y - agent.position.y) <= 15
        }
        if (nearbyEntities.isNotEmpty()) {
            sb.appendLine("=== NEARBY ENTITIES ===")
            for (e in nearbyEntities) {
                val dist = abs(e.position.x - agent.position.x) + abs(e.position.y - agent.position.y)
                sb.appendLine("${e.type}${if (e.subtype != null) "(${e.subtype})" else ""} at (${e.position.x},${e.position.y}) dist=$dist")
            }
            sb.appendLine()
        }

        // Threats
        if (worldState.threats.isNotEmpty()) {
            sb.appendLine("=== ACTIVE THREATS ===")
            for (t in worldState.threats) {
                val dist = abs(t.position.x - agent.position.x) + abs(t.position.y - agent.position.y)
                sb.appendLine("${t.type.uppercase()} at (${t.position.x},${t.position.y}) dist=$dist severity=${t.severity}")
            }
            sb.appendLine()
        }

        // Recent events
        if (recentEvents.isNotEmpty()) {
            sb.appendLine("=== RECENT EVENTS ===")
            for (e in recentEvents.takeLast(5)) {
                sb.appendLine("[${e.eventType}] ${e.description}")
            }
        }

        return sb.toString()
    }

    private fun directionTo(from: Position, to: Position): String {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dist = abs(dx) + abs(dy)
        if (dist == 0) return "dist=0 (here)"
        val dirs = mutableListOf<String>()
        if (dy < 0) dirs.add("north") else if (dy > 0) dirs.add("south")
        if (dx < 0) dirs.add("west") else if (dx > 0) dirs.add("east")
        return "dist=$dist ${dirs.joinToString("-")}"
    }

    private fun findNearest(from: Position, grid: List<List<TerrainType>>, terrain: TerrainType): Position? {
        var best: Position? = null
        var bestDist = Int.MAX_VALUE
        for (y in grid.indices) {
            for (x in grid[y].indices) {
                if (grid[y][x] == terrain) {
                    val d = abs(x - from.x) + abs(y - from.y)
                    if (d < bestDist) {
                        bestDist = d
                        best = Position(x, y)
                    }
                }
            }
        }
        return best
    }

    fun buildSkaldUserPrompt(
        worldState: WorldState,
        recentEvents: List<WorldEvent>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("=== WORLD STATE ===")
        sb.appendLine("Tick: ${worldState.tick} | Time: ${worldState.timeOfDay} | Weather: ${worldState.weather}")
        sb.appendLine("Colony: timber=${worldState.colonyResources.timber}, fish=${worldState.colonyResources.fish}, iron=${worldState.colonyResources.iron}, furs=${worldState.colonyResources.furs}")
        sb.appendLine("Longship progress: ${worldState.colonyResources.timber}/${worldState.voyageGoal.timber} timber, ${worldState.colonyResources.iron}/${worldState.voyageGoal.iron} iron, ${worldState.colonyResources.furs}/${worldState.voyageGoal.furs} furs")
        sb.appendLine()

        sb.appendLine("=== VIKINGS ===")
        for (a in worldState.agents) {
            sb.appendLine("${a.name} (${a.role}) at (${a.position.x},${a.position.y}) HP=${a.health} ${a.status}")
        }
        sb.appendLine()

        if (worldState.threats.isNotEmpty()) {
            sb.appendLine("=== THREATS ===")
            for (t in worldState.threats) {
                sb.appendLine("${t.type.uppercase()} at (${t.position.x},${t.position.y}) severity=${t.severity}")
            }
            sb.appendLine()
        }

        sb.appendLine("=== RECENT EVENTS ===")
        if (recentEvents.isEmpty()) {
            sb.appendLine("The settlement is quiet.")
        } else {
            for (e in recentEvents.takeLast(8)) {
                sb.appendLine("[${e.eventType}] ${e.description}")
            }
        }

        sb.appendLine()
        sb.appendLine("Write a brief, dramatic narration (1-3 sentences) of what is happening in the settlement. Write in the style of a Norse saga. Do not use JSON. Just write the narration text directly.")

        return sb.toString()
    }
}
