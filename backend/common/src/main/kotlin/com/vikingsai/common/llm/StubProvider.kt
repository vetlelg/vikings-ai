package com.vikingsai.common.llm

import kotlinx.coroutines.delay

class StubProvider(private val delayMs: Long = 500) : LlmProvider {

    override suspend fun complete(systemPrompt: String, userMessage: String, maxTokens: Int): String {
        delay(delayMs)

        // Jarl strategic directive
        if (systemPrompt.contains("COLONY-WIDE DIRECTIVE")) {
            return generateDirective(userMessage)
        }

        val ctx = parseContext(userMessage)
        val role = detectRole(systemPrompt)

        return decideTask(role, ctx)
    }

    private data class StubContext(
        val health: Int,
        val inventoryCount: Int,
        val hasInventory: Boolean,
        val onVillage: Boolean,
        val nearestThreatDist: Int?,
        val currentTaskType: String?,
    )

    private fun parseContext(userMessage: String): StubContext {
        val health = Regex("""Health: (\d+)/""").find(userMessage)
            ?.groupValues?.get(1)?.toIntOrNull() ?: 100

        val inventoryLine = Regex("""Inventory: (.+)""").find(userMessage)
            ?.groupValues?.get(1) ?: "empty"
        val hasInventory = inventoryLine != "empty"
        val inventoryCount = if (hasInventory) {
            Regex("""(\d+)""").findAll(inventoryLine).sumOf { it.value.toIntOrNull() ?: 0 }
        } else 0

        val terrain = Regex("""Standing on: (\w+)""").find(userMessage)
            ?.groupValues?.get(1) ?: "GRASS"
        val onVillage = terrain == "VILLAGE"

        val threatSection = userMessage.substringAfter("=== ACTIVE THREATS ===", "")
            .substringBefore("===")
        val threatMatch = Regex("""dist=(\d+)""").find(threatSection)
        val nearestThreatDist = threatMatch?.groupValues?.get(1)?.toIntOrNull()

        val currentTaskType = Regex("""Current task: (\w+)""").find(userMessage)
            ?.groupValues?.get(1)

        return StubContext(health, inventoryCount, hasInventory, onVillage, nearestThreatDist, currentTaskType)
    }

    private fun detectRole(systemPrompt: String): String = when {
        systemPrompt.contains("warrior", ignoreCase = true) -> "WARRIOR"
        systemPrompt.contains("jarl", ignoreCase = true) -> "JARL"
        systemPrompt.contains("fisherman", ignoreCase = true) -> "FISHERMAN"
        systemPrompt.contains("shipbuilder", ignoreCase = true) -> "SHIPBUILDER"
        else -> "UNKNOWN"
    }

    private fun decideTask(role: String, ctx: StubContext): String {
        // Priority 1: Fight if threat is close and agent is a fighter
        if (ctx.nearestThreatDist != null && ctx.nearestThreatDist <= 4) {
            if (role == "WARRIOR" || role == "JARL") {
                return task("fight", reasoning = "Threat nearby — engaging!")
            }
            return task("flee", reasoning = "Danger close — retreating!")
        }

        // Priority 2: Low health — idle to heal
        if (ctx.health < 30) {
            return task("idle", reasoning = "Wounded — resting to recover.")
        }

        // Priority 3: Deposit if carrying resources
        if (ctx.hasInventory && ctx.inventoryCount >= 2) {
            return task("deposit", reasoning = "Returning resources to village.")
        }

        // Priority 4: Role-based gathering/activity
        return when (role) {
            "WARRIOR" -> {
                if (ctx.nearestThreatDist != null && ctx.nearestThreatDist <= 12) {
                    task("fight", reasoning = "Hunting the threat!")
                } else {
                    task("gather", targetResource = "FURS", reasoning = "No threats — gathering furs.")
                }
            }
            "JARL" -> task("gather", targetResource = "IRON", reasoning = "Gathering iron for the longship.")
            "FISHERMAN" -> task("gather", targetResource = "FISH", reasoning = "Casting nets in the fjord.")
            "SHIPBUILDER" -> task("gather", targetResource = "TIMBER", reasoning = "Chopping timber for the longship.")
            else -> task("idle", reasoning = "Observing the settlement.")
        }
    }

    private fun task(type: String, targetResource: String? = null, reasoning: String): String {
        val resourcePart = if (targetResource != null) ""","targetResourceType":"$targetResource"""" else ""
        return """{"taskType":"$type"$resourcePart,"reasoning":"$reasoning"}"""
    }

    private fun generateDirective(userMessage: String): String {
        // Parse colony resource progress
        val timber = Regex("""Timber: (\d+)/(\d+)""").find(userMessage)
        val iron = Regex("""Iron: (\d+)/(\d+)""").find(userMessage)
        val furs = Regex("""Furs: (\d+)/(\d+)""").find(userMessage)

        val timberCurrent = timber?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val timberGoal = timber?.groupValues?.get(2)?.toIntOrNull() ?: 50
        val ironCurrent = iron?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val ironGoal = iron?.groupValues?.get(2)?.toIntOrNull() ?: 30
        val fursCurrent = furs?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val fursGoal = furs?.groupValues?.get(2)?.toIntOrNull() ?: 20

        val hasThreats = userMessage.contains("=== ACTIVE THREATS ===")

        // Determine what the colony needs most
        val timberPct = timberCurrent.toFloat() / timberGoal
        val ironPct = ironCurrent.toFloat() / ironGoal
        val fursPct = fursCurrent.toFloat() / fursGoal

        if (hasThreats) {
            return """{"assessment":"Threats detected — defending the settlement takes priority.","assignments":[{"agentName":"Astrid","directive":"Engage all threats immediately"},{"agentName":"Erik","directive":"Fall back to the village for safety"},{"agentName":"Ingrid","directive":"Fall back to the village for safety"}]}"""
        }

        // Find the most-needed resource
        data class Need(val name: String, val pct: Float, val astridOrder: String, val erikOrder: String, val ingridOrder: String)
        val needs = listOf(
            Need("timber", timberPct, "Gather furs from hunting grounds", "Gather fish from the fjord", "Prioritize timber from the forests"),
            Need("iron", ironPct, "Gather furs from hunting grounds", "Gather fish from the fjord", "Prioritize iron from the mines"),
            Need("furs", fursPct, "Gather furs from hunting grounds", "Gather furs from hunting grounds", "Gather timber from the forests")
        ).filter { it.pct < 1.0f }

        if (needs.isEmpty()) {
            return """{"assessment":"All longship resources gathered — the voyage awaits!","assignments":[{"agentName":"Astrid","directive":"Patrol for remaining threats"},{"agentName":"Erik","directive":"Continue fishing for food stores"},{"agentName":"Ingrid","directive":"Deposit any remaining resources"}]}"""
        }

        val mostNeeded = needs.minByOrNull { it.pct }!!
        val assessment = when {
            mostNeeded.pct < 0.3f -> "Colony urgently needs more ${mostNeeded.name} for the longship."
            mostNeeded.pct < 0.7f -> "Longship progress steady. Focus on ${mostNeeded.name} gathering."
            else -> "Longship nearly complete. Final push for ${mostNeeded.name}."
        }

        return """{"assessment":"$assessment","assignments":[{"agentName":"Astrid","directive":"${mostNeeded.astridOrder}"},{"agentName":"Erik","directive":"${mostNeeded.erikOrder}"},{"agentName":"Ingrid","directive":"${mostNeeded.ingridOrder}"}]}"""
    }
}
