package com.vikingsai.common.llm

import kotlinx.coroutines.delay

class StubProvider(private val delayMs: Long = 500) : LlmProvider {

    private val directions = listOf("north", "south", "east", "west")

    private val actions = listOf(
        """{"action":"move","direction":"%DIR%","reasoning":"I should explore the area."}""",
        """{"action":"gather","reasoning":"There are resources nearby to collect."}""",
        """{"action":"patrol","direction":"%DIR%","reasoning":"I need to keep the settlement safe."}""",
        """{"action":"idle","reasoning":"I will rest and observe for now."}""",
        """{"action":"move","direction":"%DIR%","reasoning":"Moving to a better position."}""",
        """{"action":"gather","reasoning":"The colony needs more supplies."}""",
        """{"action":"move","direction":"%DIR%","reasoning":"Heading toward the forest for timber."}""",
        """{"action":"fight","reasoning":"A threat approaches — I must defend the settlement!"}""",
        """{"action":"build","reasoning":"Time to deposit resources at the village."}""",
        """{"action":"move","direction":"%DIR%","reasoning":"Patrolling the perimeter."}"""
    )

    private val sagaNarrations = listOf(
        "The wind whispers through the fjord as the Vikings toil beneath an iron sky. Odin watches from his throne in Asgard.",
        "Smoke rises from the village hearth. The settlers move with purpose, each step a verse in the saga of their new home.",
        "The mountains stand as silent sentinels while Bjorn surveys his domain. The gods have blessed this coastline.",
        "Astrid sharpens her blade by firelight. The wolves will learn to fear the steel of the North.",
        "Erik casts his nets into the dark waters of the fjord. The sea provides, as it always has, as it always will.",
        "Ingrid measures timber with a practiced eye. Every beam she lays is a prayer to the Allfather for strength.",
        "Night descends upon the settlement like a raven's wing. The stars above mirror the embers below.",
        "A storm gathers beyond the mountains. The Vikings know well — the gods test those they favor most.",
        "The colony grows stronger with each passing day. Soon, songs of this settlement will echo in the great halls.",
        "Dawn breaks golden over the fjord. Another day begins in this land of ice and iron and unyielding will.",
        "The forest yields its timber grudgingly, but the Northmen are patient. They have carved kingdoms from less.",
        "Wolves howl in the distance. Let them come — the shield wall of the North has never broken.",
    )

    override suspend fun complete(systemPrompt: String, userMessage: String, maxTokens: Int): String {
        delay(delayMs)

        // Detect if this is a Skald narration request (no JSON expected)
        if (userMessage.contains("Write a brief, dramatic narration") ||
            userMessage.contains("Norse saga") ||
            systemPrompt.contains("Skald")
        ) {
            return sagaNarrations.random()
        }

        val action = actions.random()
        return action.replace("%DIR%", directions.random())
    }
}
