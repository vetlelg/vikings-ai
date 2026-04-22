package com.vikingsai.agent

import com.vikingsai.common.model.*
import kotlin.math.abs

/**
 * Rule-based observation generator. Each agent scans its nearby environment
 * and produces observations about notable things it sees.
 *
 * Observations are throttled: each type/position combination is only reported
 * once until conditions change. This prevents flooding the topic with
 * duplicate reports every tick.
 */
class ObservationGenerator(private val agentName: String) {

    // Track what we've already reported to avoid duplicates
    private val reportedResources = mutableSetOf<String>()  // "subtype-x-y"
    private val reportedThreats = mutableSetOf<String>()    // "type-id"
    private var lastAreaClearTick = -1

    companion object {
        private const val SCAN_RANGE = 8
        private const val RESOURCE_CLUSTER_MIN = 2
        private const val AREA_CLEAR_INTERVAL = 15
    }

    fun scan(self: AgentSnapshot, ws: WorldState): List<AgentObservation> {
        val observations = mutableListOf<AgentObservation>()

        // Scan for resource clusters nearby
        observations.addAll(scanResources(self, ws))

        // Scan for threats
        observations.addAll(scanThreats(self, ws))

        // Report area clear if no threats nearby (periodic)
        observations.addAll(scanAreaClear(self, ws))

        // Clean up stale tracked state
        pruneStaleReports(ws)

        return observations
    }

    private fun scanResources(self: AgentSnapshot, ws: WorldState): List<AgentObservation> {
        val observations = mutableListOf<AgentObservation>()

        // Group nearby resource nodes by subtype
        val nearbyResources = ws.entities.filter { e ->
            e.type == EntityType.RESOURCE_NODE &&
            (e.remaining ?: 0) > 0 &&
            manhattan(self.position, e.position) <= SCAN_RANGE
        }

        val clusters = nearbyResources.groupBy { it.subtype ?: "unknown" }

        for ((subtype, nodes) in clusters) {
            if (nodes.size >= RESOURCE_CLUSTER_MIN) {
                val center = nodes.minByOrNull { manhattan(self.position, it.position) }!!
                val key = "$subtype-${center.position.x}-${center.position.y}"
                if (key !in reportedResources) {
                    reportedResources.add(key)
                    val resourceName = when (subtype) {
                        "tree" -> "timber"
                        "mine" -> "iron"
                        "fishing_spot" -> "fish"
                        "hunting_ground" -> "furs"
                        else -> subtype
                    }
                    observations.add(AgentObservation(
                        tick = ws.tick,
                        agentName = agentName,
                        type = ObservationType.RESOURCE_FOUND,
                        description = "${nodes.size} $resourceName sources near (${center.position.x},${center.position.y})",
                        position = center.position
                    ))
                }
            }
        }

        // Check for depleted areas — resources we previously reported that are now gone
        val currentKeys = nearbyResources.map { e ->
            "${e.subtype ?: "unknown"}-${e.position.x}-${e.position.y}"
        }.toSet()

        val depleted = reportedResources.filter { key ->
            val parts = key.split("-")
            if (parts.size < 3) return@filter false
            val x = parts[parts.size - 2].toIntOrNull() ?: return@filter false
            val y = parts[parts.size - 1].toIntOrNull() ?: return@filter false
            val pos = Position(x, y)
            manhattan(self.position, pos) <= SCAN_RANGE && key !in currentKeys
        }

        for (key in depleted) {
            reportedResources.remove(key)
            val parts = key.split("-")
            val x = parts[parts.size - 2].toIntOrNull() ?: continue
            val y = parts[parts.size - 1].toIntOrNull() ?: continue
            val subtype = parts.dropLast(2).joinToString("-")
            val resourceName = when (subtype) {
                "tree" -> "timber"
                "mine" -> "iron"
                "fishing_spot" -> "fish"
                "hunting_ground" -> "furs"
                else -> subtype
            }
            observations.add(AgentObservation(
                tick = ws.tick,
                agentName = agentName,
                type = ObservationType.RESOURCE_DEPLETED,
                description = "$resourceName sources near ($x,$y) depleted",
                position = Position(x, y)
            ))
        }

        return observations
    }

    private fun scanThreats(self: AgentSnapshot, ws: WorldState): List<AgentObservation> {
        val observations = mutableListOf<AgentObservation>()

        for (threat in ws.threats) {
            if (manhattan(self.position, threat.position) <= SCAN_RANGE) {
                val key = "${threat.type}-${threat.id}"
                if (key !in reportedThreats) {
                    reportedThreats.add(key)
                    observations.add(AgentObservation(
                        tick = ws.tick,
                        agentName = agentName,
                        type = ObservationType.THREAT_SPOTTED,
                        description = "${threat.type} at (${threat.position.x},${threat.position.y})",
                        position = threat.position
                    ))
                }
            }
        }

        return observations
    }

    private fun scanAreaClear(self: AgentSnapshot, ws: WorldState): List<AgentObservation> {
        if (ws.tick - lastAreaClearTick < AREA_CLEAR_INTERVAL) return emptyList()

        val nearbyThreats = ws.threats.any { manhattan(self.position, it.position) <= SCAN_RANGE }
        if (!nearbyThreats && ws.threats.isNotEmpty()) {
            // There are threats somewhere, but not near us — report our area as clear
            lastAreaClearTick = ws.tick
            return listOf(AgentObservation(
                tick = ws.tick,
                agentName = agentName,
                type = ObservationType.AREA_CLEAR,
                description = "Area around (${self.position.x},${self.position.y}) clear of threats",
                position = self.position
            ))
        }

        return emptyList()
    }

    private fun pruneStaleReports(ws: WorldState) {
        // Remove tracked threats that no longer exist
        val activeThreatIds = ws.threats.map { "${it.type}-${it.id}" }.toSet()
        reportedThreats.retainAll(activeThreatIds)
    }

    private fun manhattan(a: Position, b: Position): Int =
        abs(a.x - b.x) + abs(a.y - b.y)
}
