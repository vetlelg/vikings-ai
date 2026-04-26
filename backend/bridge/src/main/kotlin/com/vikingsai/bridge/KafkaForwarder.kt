package com.vikingsai.bridge

import com.vikingsai.common.kafka.Topics
import com.vikingsai.common.kafka.createConsumer
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Consumes agent decision topics from Kafka and forwards them to Phaser via WebSocket.
 * Also forwards commander orders so the spectator UI can display agent reasoning.
 */
class KafkaForwarder(
    private val bootstrapServers: String,
    private val sessions: ConcurrentHashMap.KeySetView<WebSocketSession, Boolean>,
    private val recorder: ReplayRecorder
) {
    private val log = LoggerFactory.getLogger(KafkaForwarder::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            log.info("Kafka forwarder starting...")

            val consumer = createConsumer(
                bootstrapServers = bootstrapServers,
                groupId = "bridge-${System.currentTimeMillis()}",
                topics = Topics.BRIDGE_FORWARD_TO_PHASER,
                fromBeginning = false
            )

            log.info("Kafka forwarder consuming: ${Topics.BRIDGE_FORWARD_TO_PHASER}")

            try {
                while (isActive) {
                    val records = consumer.poll(Duration.ofMillis(100))
                    for (record in records) {
                        try {
                            val payload: JsonElement = json.parseToJsonElement(record.value())
                            val envelope = BridgeEnvelope(
                                topic = record.topic(),
                                timestamp = record.timestamp(),
                                payload = payload
                            )
                            val envelopeJson = json.encodeToString(BridgeEnvelope.serializer(), envelope)

                            recorder.record(envelopeJson)

                            val deadSessions = mutableListOf<WebSocketSession>()
                            for (session in sessions) {
                                try {
                                    session.send(Frame.Text(envelopeJson))
                                } catch (e: Exception) {
                                    deadSessions.add(session)
                                }
                            }
                            deadSessions.forEach { sessions.remove(it) }

                            log.debug("Forwarded [${record.topic()}] to ${sessions.size} client(s)")
                        } catch (e: Exception) {
                            log.warn("Failed to process record from ${record.topic()}: ${e.message}")
                        }
                    }
                }
            } finally {
                consumer.close()
                log.info("Kafka forwarder stopped")
            }
        }
    }
}
