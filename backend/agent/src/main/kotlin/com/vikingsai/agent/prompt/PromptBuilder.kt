package com.vikingsai.agent.prompt

import com.vikingsai.common.model.*
import kotlin.math.abs

object PromptBuilder {

    fun buildSystemPrompt(name: String, role: AgentRole, personality: String): String {
        return """
You are $name, a Viking ${role.name.lowercase()} in a Norse settlement on a fjord coastline.

$personality

WORLD RULES:
- You are on a grid. Terrain types: GRASS, FOREST, WATER (impassable), MOUNTAIN, BEACH, VILLAGE.
- The colony has shared resources: timber, fish, iron, furs.
- Resource sources: trees (timber), mines (iron), fishing spots (fish), hunting grounds (furs). They deplete over time.
- Threats include wolves, rival raiders, and a dragon.
- Time cycles through DAWN, DAY, DUSK, NIGHT. Weather can be CLEAR, SNOW, or STORM.
- COLONY GOAL: Build a longship. The colony needs 50 timber, 30 iron, and 20 furs deposited at the village.

YOU ASSIGN TASKS, NOT INDIVIDUAL ACTIONS. The engine will execute your task mechanically over multiple ticks — pathfinding, gathering, returning. You only need to decide WHAT to do, not HOW.

RESPOND WITH EXACTLY ONE JSON TASK. Available tasks:
- {"taskType":"gather","targetResourceType":"TIMBER|IRON|FISH|FURS","reasoning":"why"} — move to nearest source and gather until inventory full
- {"taskType":"deposit","reasoning":"why"} — return to village and deposit all resources
- {"taskType":"fight","reasoning":"why"} — move toward nearest threat and attack
- {"taskType":"flee","reasoning":"why"} — run away from danger toward safety
- {"taskType":"idle","reasoning":"why"} — rest and observe

PRIORITY RULES (follow in order):
1. SURVIVE: If a threat is nearby (dist <= 4), fighters should FIGHT, others should FLEE.
2. HEAL: If health is below 30, choose IDLE to rest.
3. DEPOSIT: If carrying resources, choose DEPOSIT.
4. GATHER: Follow your role's resource priorities.

Keep "reasoning" SHORT — at most 10 words.
RESPOND WITH ONLY THE JSON. No markdown, no explanation outside the JSON.
        """.trimIndent()
    }

    fun buildUserPrompt(
        name: String,
        worldState: WorldState,
        recentEvents: List<WorldEvent>,
        directive: JarlDirective? = null,
        peerObservations: List<AgentObservation> = emptyList()
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
        if (agent.currentTaskType != null) {
            sb.appendLine("Current task: ${agent.currentTaskType}${if (agent.currentTaskReasoning != null) " — ${agent.currentTaskReasoning}" else ""}")
        } else {
            sb.appendLine("Current task: NONE — you need a new task!")
        }
        sb.appendLine()

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
        // Nearest resource source
        worldState.entities.filter { it.type == EntityType.RESOURCE_NODE && it.remaining > 0 }
            .minByOrNull { abs(it.position.x - agent.position.x) + abs(it.position.y - agent.position.y) }
            ?.let {
                sb.appendLine("Nearest resource source: ${it.subtype ?: "unknown"} (${it.remaining}/${it.capacity}) at (${it.position.x}, ${it.position.y}) ${directionTo(agent.position, it.position)}")
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
                val capacityInfo = if (e.type == EntityType.RESOURCE_NODE && e.capacity > 0) " remaining=${e.remaining}/${e.capacity}" else ""
                sb.appendLine("${e.type}${if (e.subtype != null) "(${e.subtype})" else ""} at (${e.position.x},${e.position.y}) dist=$dist$capacityInfo")
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

        // Jarl's directive (strategic context from colony leader)
        if (directive != null) {
            sb.appendLine("=== JARL'S DIRECTIVE (Tick ${directive.tick}) ===")
            sb.appendLine("Assessment: ${directive.assessment}")
            val myAssignment = directive.assignments.find { it.agentName == name }
            if (myAssignment != null) {
                sb.appendLine("YOUR ORDERS: ${myAssignment.directive}")
            }
            sb.appendLine("Consider the Jarl's orders, but override them if your local situation demands it (e.g., immediate threats, critical health).")
            sb.appendLine()
        }

        // Peer observations (shared knowledge from other agents)
        val recentObs = peerObservations.filter { worldState.tick - it.tick <= 20 }.takeLast(8)
        if (recentObs.isNotEmpty()) {
            sb.appendLine("=== SHARED OBSERVATIONS ===")
            for (obs in recentObs) {
                sb.appendLine("[Tick ${obs.tick}] ${obs.agentName}: ${obs.description}")
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

    fun buildJarlStrategicSystemPrompt(): String {
        return """
You are Bjorn the Jarl — strategic commander of a Viking settlement. You are issuing a COLONY-WIDE DIRECTIVE.

Your job is to assess the colony's overall situation and assign priorities to each Viking:
- Astrid (Warrior): Best at combat. Can also gather furs.
- Erik (Fisherman): Gathers fish and furs. Should flee from threats.
- Ingrid (Shipbuilder): Gathers timber and iron. Should flee from threats.

COLONY GOAL: Build a longship requiring 50 timber, 30 iron, and 20 furs deposited at the village.

RESPOND WITH EXACTLY THIS JSON FORMAT:
{
  "assessment": "1-2 sentence colony situation summary",
  "assignments": [
    {"agentName": "Astrid", "directive": "short order"},
    {"agentName": "Erik", "directive": "short order"},
    {"agentName": "Ingrid", "directive": "short order"}
  ]
}

RULES:
- Focus on what the colony NEEDS MOST right now. Check resource progress.
- If threats are active, assign Astrid to fight and non-combatants to safety.
- Keep directives SHORT (under 10 words each).
- Keep assessment SHORT (1-2 sentences).
- RESPOND WITH ONLY THE JSON.
        """.trimIndent()
    }

    fun buildJarlStrategicUserPrompt(
        worldState: WorldState,
        recentEvents: List<WorldEvent>,
        fieldReports: List<AgentObservation> = emptyList()
    ): String {
        val sb = StringBuilder()

        sb.appendLine("=== COLONY STATUS ===")
        sb.appendLine("Tick: ${worldState.tick} | Time: ${worldState.timeOfDay} | Weather: ${worldState.weather}")
        sb.appendLine()

        // Resource progress toward longship
        sb.appendLine("=== LONGSHIP PROGRESS ===")
        sb.appendLine("Timber: ${worldState.colonyResources.timber}/${worldState.voyageGoal.timber}${if (worldState.colonyResources.timber >= worldState.voyageGoal.timber) " DONE" else ""}")
        sb.appendLine("Iron: ${worldState.colonyResources.iron}/${worldState.voyageGoal.iron}${if (worldState.colonyResources.iron >= worldState.voyageGoal.iron) " DONE" else ""}")
        sb.appendLine("Furs: ${worldState.colonyResources.furs}/${worldState.voyageGoal.furs}${if (worldState.colonyResources.furs >= worldState.voyageGoal.furs) " DONE" else ""}")
        sb.appendLine("Fish (food): ${worldState.colonyResources.fish}")
        sb.appendLine()

        // All agents and what they're doing
        sb.appendLine("=== VIKINGS ===")
        for (a in worldState.agents) {
            val taskInfo = if (a.currentTaskType != null) "${a.currentTaskType}" else "no task"
            val invInfo = if (a.inventory.isNotEmpty()) "carrying: ${a.inventory.entries.joinToString { "${it.value} ${it.key}" }}" else "empty inv"
            sb.appendLine("${a.name} (${a.role}) HP=${a.health} ${a.status} | $taskInfo | $invInfo")
        }
        sb.appendLine()

        // Active threats
        if (worldState.threats.isNotEmpty()) {
            sb.appendLine("=== ACTIVE THREATS ===")
            for (t in worldState.threats) {
                sb.appendLine("${t.type.uppercase()} at (${t.position.x},${t.position.y}) severity=${t.severity}")
            }
            sb.appendLine()
        }

        // Recent significant events
        val significantEvents = recentEvents.filter {
            it.eventType.name in setOf("DRAGON_SIGHTED", "DRAGON_DEFEATED", "RAID_INCOMING",
                "AGENT_DIED", "WOLF_SPOTTED", "LONGSHIP_COMPLETE")
        }.takeLast(5)
        if (significantEvents.isNotEmpty()) {
            sb.appendLine("=== RECENT SIGNIFICANT EVENTS ===")
            for (e in significantEvents) {
                sb.appendLine("[${e.eventType}] ${e.description}")
            }
            sb.appendLine()
        }

        // Available resource nodes by type
        val resourceCounts = worldState.entities
            .filter { it.type == EntityType.RESOURCE_NODE && (it.remaining ?: 0) > 0 }
            .groupBy { it.subtype ?: "unknown" }
            .mapValues { it.value.size }
        sb.appendLine("=== AVAILABLE RESOURCE SOURCES ===")
        sb.appendLine("Trees (timber): ${resourceCounts["tree"] ?: 0}")
        sb.appendLine("Mines (iron): ${resourceCounts["mine"] ?: 0}")
        sb.appendLine("Fishing spots (fish): ${resourceCounts["fishing_spot"] ?: 0}")
        sb.appendLine("Hunting grounds (furs): ${resourceCounts["hunting_ground"] ?: 0}")

        // Field reports from agents (distributed observations)
        val recentReports = fieldReports.filter { worldState.tick - it.tick <= 20 }.takeLast(6)
        if (recentReports.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("=== FIELD REPORTS ===")
            for (r in recentReports) {
                sb.appendLine("[Tick ${r.tick}] ${r.agentName}: ${r.description}")
            }
        }

        return sb.toString()
    }
}
