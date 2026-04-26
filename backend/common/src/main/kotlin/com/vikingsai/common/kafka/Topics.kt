package com.vikingsai.common.kafka

object Topics {
    const val GAME_STATE = "game-state"
    const val GAME_EVENTS = "game-events"
    const val COMMANDER_ORDERS_RED = "commander-orders.red"
    const val COMMANDER_ORDERS_BLUE = "commander-orders.blue"
    const val UNIT_DECISIONS_RED = "unit-decisions.red"
    const val UNIT_DECISIONS_BLUE = "unit-decisions.blue"
    const val UNIT_OBSERVATIONS_RED = "unit-observations.red"
    const val UNIT_OBSERVATIONS_BLUE = "unit-observations.blue"

    fun commanderOrders(faction: String) = "commander-orders.$faction"
    fun unitDecisions(faction: String) = "unit-decisions.$faction"
    fun unitObservations(faction: String) = "unit-observations.$faction"

    val ALL = listOf(
        GAME_STATE, GAME_EVENTS,
        COMMANDER_ORDERS_RED, COMMANDER_ORDERS_BLUE,
        UNIT_DECISIONS_RED, UNIT_DECISIONS_BLUE,
        UNIT_OBSERVATIONS_RED, UNIT_OBSERVATIONS_BLUE,
    )

    val AGENT_OUTPUT = listOf(
        COMMANDER_ORDERS_RED, COMMANDER_ORDERS_BLUE,
        UNIT_DECISIONS_RED, UNIT_DECISIONS_BLUE,
        UNIT_OBSERVATIONS_RED, UNIT_OBSERVATIONS_BLUE,
    )

    val BRIDGE_FORWARD_TO_PHASER = listOf(
        COMMANDER_ORDERS_RED, COMMANDER_ORDERS_BLUE,
        UNIT_DECISIONS_RED, UNIT_DECISIONS_BLUE,
    )
}
