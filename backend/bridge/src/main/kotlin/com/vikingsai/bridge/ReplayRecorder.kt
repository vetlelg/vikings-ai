package com.vikingsai.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

/**
 * Records all bridge envelopes to a JSON Lines file for replay.
 * Each line includes a relative timestamp (ms since session start) for replay timing.
 */
class ReplayRecorder(private val filePath: String) {

    private val log = LoggerFactory.getLogger(ReplayRecorder::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private var writer: PrintWriter? = null
    private var sessionStart = System.currentTimeMillis()

    init {
        openFile()
    }

    private fun openFile() {
        val file = File(filePath)
        file.parentFile?.mkdirs()
        writer = try {
            PrintWriter(FileWriter(file, false), true)
        } catch (e: Exception) {
            log.warn("Failed to open replay file $filePath: ${e.message}. Replay recording disabled.")
            null
        }
        sessionStart = System.currentTimeMillis()
        if (writer != null) {
            log.info("Recording replay to $filePath")
        }
    }

    fun record(envelopeJson: String) {
        val w = writer ?: return
        val relativeMs = System.currentTimeMillis() - sessionStart
        val line = json.encodeToString(
            ReplayLine.serializer(),
            ReplayLine(offsetMs = relativeMs, envelope = envelopeJson)
        )
        w.println(line)
    }

    fun getFilePath(): String = filePath

    fun close() {
        writer?.close()
        log.info("Replay recording closed")
    }
}

@Serializable
data class ReplayLine(
    val offsetMs: Long,
    val envelope: String
)
