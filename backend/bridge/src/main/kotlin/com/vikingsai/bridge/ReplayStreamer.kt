package com.vikingsai.bridge

import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Streams a recorded replay file over a WebSocket with original timing.
 */
class ReplayStreamer {

    private val log = LoggerFactory.getLogger(ReplayStreamer::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun stream(session: WebSocketSession, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            session.send(Frame.Text("""{"error":"No replay file found"}"""))
            session.close()
            return
        }

        log.info("Starting replay stream from $filePath")
        var lastOffsetMs = 0L

        file.useLines { lines ->
            for (line in lines) {
                if (line.isBlank()) continue
                try {
                    val replayLine = json.decodeFromString<ReplayLine>(line)
                    // Delay to match original timing
                    val waitMs = replayLine.offsetMs - lastOffsetMs
                    if (waitMs > 0) {
                        delay(waitMs.coerceAtMost(10000)) // cap at 10s to avoid long waits
                    }
                    lastOffsetMs = replayLine.offsetMs

                    session.send(Frame.Text(replayLine.envelope))
                } catch (e: Exception) {
                    log.warn("Failed to parse replay line: ${e.message}")
                }
            }
        }

        log.info("Replay stream complete")
        session.send(Frame.Text("""{"topic":"replay-end","timestamp":0,"payload":{}}"""))
    }
}
