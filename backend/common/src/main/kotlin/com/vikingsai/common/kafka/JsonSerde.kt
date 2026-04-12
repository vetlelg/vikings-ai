package com.vikingsai.common.kafka

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer

val GameJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

class JsonKafkaSerializer<T>(private val serializer: KSerializer<T>) : Serializer<T> {
    override fun serialize(topic: String, data: T?): ByteArray? {
        if (data == null) return null
        return GameJson.encodeToString(serializer, data).toByteArray(Charsets.UTF_8)
    }
}

class JsonKafkaDeserializer<T>(private val serializer: KSerializer<T>) : Deserializer<T> {
    override fun deserialize(topic: String, data: ByteArray?): T? {
        if (data == null) return null
        return GameJson.decodeFromString(serializer, String(data, Charsets.UTF_8))
    }
}
