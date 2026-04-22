package com.vikingsai.agent

import com.vikingsai.common.model.*
import kotlin.math.abs

/**
 * Fast, rule-based decision layer for urgent/predictable situations.
 *
 * The reactive layer runs before the LLM. If a rule matches, the agent
 * publishes a task immediately — no LLM call, no latency. This mirrors
 * the subsumption architecture pattern: a fast reactive layer handles
 * survival, while the slower deliberative layer (LLM) handles strategy.
 *
 * Rules (checked in priority order):
 * 1. Threat nearby + combatant role  → FIGHT
 * 2. Threat nearby + non-combatant   → FLEE
 * 3. Health critical                  → IDLE (rest to heal)
 */
object ReactiveLayer {

    private const val THREAT_RANGE = 4

    private val combatRoles = setOf(AgentRole.WARRIOR, AgentRole.JARL)

    /**
     * Returns a reactive task if a rule matches, or null if the situation
     * requires deliberation (LLM).
     */
    fun evaluate(self: AgentSnapshot, ws: WorldState, role: AgentRole): AgentTask? {
        val tick = ws.tick

        // Find nearest threat
        val nearestThreat = ws.threats.minByOrNull { manhattan(self.position, it.position) }
        val threatDist = nearestThreat?.let { manhattan(self.position, it.position) }

        // Rule 1 & 2: Nearby threat — combatants fight, others flee
        if (nearestThreat != null && threatDist != null && threatDist <= THREAT_RANGE) {
            if (role in combatRoles) {
                return AgentTask(
                    tick = tick,
                    agentName = self.name,
                    taskType = TaskType.FIGHT,
                    reasoning = "[reactive] ${nearestThreat.type} spotted $threatDist tiles away — engaging"
                )
            } else {
                return AgentTask(
                    tick = tick,
                    agentName = self.name,
                    taskType = TaskType.FLEE,
                    reasoning = "[reactive] ${nearestThreat.type} spotted $threatDist tiles away — retreating"
                )
            }
        }

        // Rule 3: Health critical — rest
        if (self.health < 30) {
            return AgentTask(
                tick = tick,
                agentName = self.name,
                taskType = TaskType.IDLE,
                reasoning = "[reactive] Health critical (${self.health}) — resting"
            )
        }

        // No reactive rule matched — fall through to deliberative layer (LLM)
        return null
    }

    private fun manhattan(a: Position, b: Position): Int =
        abs(a.x - b.x) + abs(a.y - b.y)
}
