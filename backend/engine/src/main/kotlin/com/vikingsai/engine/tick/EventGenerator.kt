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

        // Spawn resource nodes periodically
        if (world.tick % 8 == 0 && world.tick > 0) {
            val resourceNodeCount = world.entities.count { it.type == EntityType.RESOURCE_NODE }
            if (resourceNodeCount < 15) {
                world.spawnResourceNodes(2)
            }
        }

        return events
    }
}
