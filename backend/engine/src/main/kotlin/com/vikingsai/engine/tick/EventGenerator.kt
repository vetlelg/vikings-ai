package com.vikingsai.engine.tick

import com.vikingsai.common.model.*
import com.vikingsai.engine.world.WorldManager

/**
 * Generates world events for time-of-day changes, weather changes,
 * and periodic resource spawning.
 */
class EventGenerator(private val world: WorldManager) {

    fun generateTickEvents(): List<WorldEvent> {
        val events = mutableListOf<WorldEvent>()

        // Time of day transitions
        val newTime = world.updateTimeOfDay()
        if (newTime != null) {
            val (eventType, description) = when (newTime) {
                TimeOfDay.DAWN -> EventType.DAWN_BREAKING to "Dawn breaks over the fjord. A new day begins."
                TimeOfDay.DAY -> EventType.DAWN_BREAKING to "The sun rises high. The settlement stirs with activity."
                TimeOfDay.DUSK -> EventType.NIGHT_FALLING to "Dusk settles over the mountains. Shadows grow long."
                TimeOfDay.NIGHT -> EventType.NIGHT_FALLING to "Night falls. The settlement hunkers down against the darkness."
            }
            events.add(WorldEvent(
                tick = world.tick,
                eventType = eventType,
                description = description,
                severity = Severity.LOW
            ))
        }

        // Weather changes
        val newWeather = world.updateWeather()
        if (newWeather != null) {
            val description = when (newWeather) {
                Weather.CLEAR -> "The skies clear. A calm wind blows from the fjord."
                Weather.SNOW -> "Snow begins to fall, blanketing the settlement in white."
                Weather.STORM -> "A fierce storm rolls in! The wind howls through the village."
            }
            events.add(WorldEvent(
                tick = world.tick,
                eventType = EventType.WEATHER_CHANGE,
                description = description,
                severity = if (newWeather == Weather.STORM) Severity.MEDIUM else Severity.LOW
            ))
        }

        // Regrow resource sources periodically (trees regrow, new sources appear)
        if (world.tick % 15 == 0 && world.tick > 0) {
            val resourceCount = world.entities.count { it.type == EntityType.RESOURCE_NODE }
            if (resourceCount < 80) {
                world.spawnResourceSources(2)
            }
        }

        // Replenish fishing spots every 10 ticks (fish return)
        if (world.tick % 10 == 0 && world.tick > 0) {
            world.replenishFishingSpots()
        }

        // Check voyage completion
        if (world.gameStatus == GameStatus.IN_PROGRESS && world.checkVoyageComplete()) {
            world.gameStatus = GameStatus.VICTORY
            // Deduct the resources used to build the longship
            world.colonyResources.timber -= WorldManager.VOYAGE_TIMBER
            world.colonyResources.iron -= WorldManager.VOYAGE_IRON
            world.colonyResources.furs -= WorldManager.VOYAGE_FURS
            events.add(WorldEvent(
                tick = world.tick,
                eventType = EventType.LONGSHIP_COMPLETE,
                description = "The longship is complete! The Vikings have gathered enough timber, iron, and furs to build their great vessel. The settlement celebrates!",
                severity = Severity.CRITICAL
            ))
        }

        return events
    }
}
