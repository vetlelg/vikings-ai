package com.vikingsai.bridge

import com.vikingsai.common.config.GameConfig
import com.vikingsai.common.kafka.GameJson
import com.vikingsai.common.kafka.Topics
import com.vikingsai.common.kafka.createProducer
import com.vikingsai.common.model.EventType
import com.vikingsai.common.model.Severity
import com.vikingsai.common.model.WorldEvent
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

fun main() {
    val log = LoggerFactory.getLogger("BridgeMain")

    log.info("Viking Settlement — WebSocket Bridge starting...")

    val kafkaConfig = GameConfig.kafka
    val bridgeConfig = GameConfig.bridge

    val sessions = ConcurrentHashMap.newKeySet<WebSocketSession>()
    val recorder = ReplayRecorder(bridgeConfig.replayFile)
    val forwarder = KafkaForwarder(kafkaConfig.bootstrapServers, sessions, recorder)
    val replayStreamer = ReplayStreamer()

    // Producer for forwarding commands from frontend to Kafka
    val commandProducer = createProducer(kafkaConfig.bootstrapServers)

    val server = embeddedServer(Netty, port = bridgeConfig.port) {
        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 30.seconds
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }

        install(CORS) {
            allowHost("localhost:3000")
            allowHost("localhost:5173")
            allowHost("127.0.0.1:3000")
            allowHost("127.0.0.1:5173")
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
        }

        routing {
            webSocket("/ws") {
                sessions.add(this)
                log.info("Client connected. Total clients: ${sessions.size}")

                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            handleCommand(frame.readText(), commandProducer, log)
                        }
                    }
                } catch (e: Exception) {
                    log.debug("Client disconnected: ${e.message}")
                } finally {
                    sessions.remove(this)
                    log.info("Client disconnected. Total clients: ${sessions.size}")
                }
            }

            webSocket("/replay") {
                log.info("Replay client connected")
                try {
                    replayStreamer.stream(this, recorder.getFilePath())
                } catch (e: Exception) {
                    log.debug("Replay client disconnected: ${e.message}")
                }
            }
        }

        // Start Kafka forwarder as a background coroutine
        launch {
            forwarder.start(this)
        }
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("Shutting down bridge...")
        recorder.close()
        commandProducer.close()
        server.stop(1000, 5000)
    })

    log.info("Bridge listening on ws://localhost:${bridgeConfig.port}/ws")
    server.start(wait = true)
}

private fun handleCommand(
    text: String,
    producer: org.apache.kafka.clients.producer.KafkaProducer<String, String>,
    log: org.slf4j.Logger
) {
    try {
        val command = GameJson.decodeFromString<WorldCommand>(text)
        log.info("Received command: ${command.command}")

        val event = when (command.command) {
            "spawn_dragon" -> WorldEvent(
                tick = -1, // engine will use current tick
                eventType = EventType.DRAGON_SIGHTED,
                description = "A player has summoned a dragon upon the settlement!",
                severity = Severity.CRITICAL
            )
            "start_winter" -> WorldEvent(
                tick = -1,
                eventType = EventType.WEATHER_CHANGE,
                description = "A player-invoked winter storm descends upon the land!",
                severity = Severity.MEDIUM
            )
            "rival_raid" -> WorldEvent(
                tick = -1,
                eventType = EventType.RAID_INCOMING,
                description = "A player has directed a rival clan to attack!",
                severity = Severity.HIGH
            )
            else -> {
                log.warn("Unknown command: ${command.command}")
                return
            }
        }

        val json = GameJson.encodeToString(WorldEvent.serializer(), event)
        producer.send(ProducerRecord(Topics.WORLD_EVENTS, event.eventType.name, json))
    } catch (e: Exception) {
        log.warn("Failed to parse command: ${e.message}")
    }
}
