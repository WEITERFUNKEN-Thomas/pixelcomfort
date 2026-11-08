package com.noirdraco.pixelcomfort.aod

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class AodData(
    val automation: Boolean = true,
    val rules: List<Rule> = emptyList(),
    val engine: EngineState = EngineState(),
) {
    /** The monitor service only needs to run while this is true. */
    val shouldRun: Boolean get() = automation && rules.any { it.enabled }
}

object AodDataSerializer : Serializer<AodData> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val defaultValue = AodData()

    override suspend fun readFrom(input: InputStream): AodData = try {
        json.decodeFromString(AodData.serializer(), input.readBytes().decodeToString())
    } catch (e: IllegalArgumentException) {
        // SerializationException is an IllegalArgumentException
        throw CorruptionException("Cannot read app data", e)
    }

    override suspend fun writeTo(t: AodData, output: OutputStream) {
        output.write(json.encodeToString(AodData.serializer(), t).encodeToByteArray())
    }
}
