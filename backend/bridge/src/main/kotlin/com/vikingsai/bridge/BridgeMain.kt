package com.vikingsai.bridge

import com.vikingsai.common.config.GameConfig
import com.vikingsai.common.kafka.Topics
import com.vikingsai.common.kafka.createProducer
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

private val json = Json { ignoreUnknownKeys = true }

fun main() {
    val log = LoggerFactory.getLogger("BridgeMain")

    log.info("RTS AI — WebSocket Bridge starting...")

    val kafkaConfig = GameConfig.kafka
    val bridgeConfig = GameConfig.bridge

    val sessions = ConcurrentHashMap.newKeySet<WebSocketSession>()
    val recorder = ReplayRecorder(bridgeConfig.replayFile)

    // Forwarder: Kafka agent output → WebSocket (Phaser)
    val forwarder = KafkaForwarder(kafkaConfig.bootstrapServers, sessions, recorder)

    // Producer: WebSocket (Phaser) → Kafka
    val stateProducer = createProducer(kafkaConfig.bootstrapServers)

    val server = embeddedServer(Netty, host = "0.0.0.0", port = bridgeConfig.port) {
        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 30.seconds
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }

        routing {
            webSocket("/ws") {
                sessions.add(this)
                log.info("Phaser client connected. Total clients: ${sessions.size}")

                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            handlePhaserMessage(frame.readText(), stateProducer, log)
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
                val replayStreamer = ReplayStreamer()
                try {
                    replayStreamer.stream(this, recorder.getFilePath())
                } catch (e: Exception) {
                    log.debug("Replay client disconnected: ${e.message}")
                }
            }
        }

        launch {
            forwarder.start(this)
        }
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("Shutting down bridge...")
        recorder.close()
        stateProducer.close()
        server.stop(1000, 5000)
    })

    log.info("Bridge listening on ws://localhost:${bridgeConfig.port}/ws")
    server.start(wait = true)
}

/**
 * Handles messages from Phaser. Messages are envelopes with a "topic" field
 * that determines which Kafka topic they get published to.
 *
 * Expected format: { "topic": "game-state", "payload": { ... } }
 */
private fun handlePhaserMessage(
    text: String,
    producer: org.apache.kafka.clients.producer.KafkaProducer<String, String>,
    log: org.slf4j.Logger
) {
    try {
        val envelope = json.decodeFromString<JsonObject>(text)
        val topic = envelope["topic"]?.jsonPrimitive?.content ?: return
        val payload = envelope["payload"]?.toString() ?: return

        when (topic) {
            Topics.GAME_STATE -> {
                producer.send(ProducerRecord(Topics.GAME_STATE, "state", payload))
            }
            Topics.GAME_EVENTS -> {
                producer.send(ProducerRecord(Topics.GAME_EVENTS, "event", payload))
            }
            else -> {
                log.warn("Unknown topic from Phaser: $topic")
            }
        }
    } catch (e: Exception) {
        log.warn("Failed to handle Phaser message: ${e.message}")
    }
}
