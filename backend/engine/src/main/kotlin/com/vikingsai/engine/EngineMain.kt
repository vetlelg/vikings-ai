package com.vikingsai.engine

import com.vikingsai.common.config.GameConfig
import com.vikingsai.common.kafka.Topics
import com.vikingsai.common.kafka.createConsumer
import com.vikingsai.common.kafka.createProducer
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory

private fun retryKafkaConnect(bootstrapServers: String): KafkaProducer<String, String> {
    val log = LoggerFactory.getLogger("EngineMain")
    for (attempt in 1..10) {
        try {
            val p = createProducer(bootstrapServers)
            p.partitionsFor(Topics.WORLD_STATE)
            log.info("Connected to Kafka broker")
            return p
        } catch (e: Exception) {
            log.warn("Kafka not ready (attempt $attempt/10): ${e.message}")
            Thread.sleep(2000L * attempt)
        }
    }
    throw RuntimeException("Failed to connect to Kafka after 10 attempts")
}

fun main() {
    val log = LoggerFactory.getLogger("EngineMain")

    log.info("Viking Settlement — Game Engine starting...")

    val kafkaConfig = GameConfig.kafka
    val engineConfig = GameConfig.engine

    log.info("Kafka: ${kafkaConfig.bootstrapServers}")
    log.info("Grid: ${engineConfig.gridWidth}x${engineConfig.gridHeight}")
    log.info("Tick rate: ${engineConfig.tickRateMs}ms")

    // Retry Kafka connection with backoff
    val producer = retryKafkaConnect(kafkaConfig.bootstrapServers)

    val taskConsumer = createConsumer(
        bootstrapServers = kafkaConfig.bootstrapServers,
        groupId = "engine",
        topics = listOf(Topics.AGENT_TASKS)
    )

    val gameLoop = GameLoop(
        producer = producer,
        taskConsumer = taskConsumer,
        tickRateMs = engineConfig.tickRateMs,
        gridWidth = engineConfig.gridWidth,
        gridHeight = engineConfig.gridHeight
    )

    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("Shutting down engine...")
        producer.close()
        taskConsumer.close()
    })

    runBlocking {
        gameLoop.run()
    }
}
