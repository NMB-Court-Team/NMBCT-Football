package net.astrorbits.football.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import net.astrorbits.football.NMBCTFootball
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Optional

object ConfigPersistence {
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    fun <T> load(path: Path, codec: Codec<T>, default: T): T {
        if (!Files.exists(path)) {
            save(path, codec, default)
            return default
        }
        return try {
            val text = Files.readString(path)
            val element = JsonParser.parseString(text)
            codec.parse(JsonOps.INSTANCE, element).getOrThrow()
        } catch (e: Exception) {
            NMBCTFootball.LOGGER.warn("Failed to load config from {}, using defaults", path, e)
            default
        }
    }

    fun <T> save(path: Path, codec: Codec<T>, value: T) {
        try {
            val element = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow()
            val text = toPrettyJson(element)
            Files.writeString(
                path,
                text,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
        } catch (e: Exception) {
            NMBCTFootball.LOGGER.warn("Failed to save config to {}", path, e)
        }
    }

    /** 将 Codec 编码结果转为可写入磁盘的 JSON 文本，避免 Gson 反射 Optional 等类型。 */
    private fun toPrettyJson(encoded: Any): String {
        val element = unwrapJsonElement(encoded)
        return prettyGson.toJson(element)
    }

    private fun unwrapJsonElement(value: Any?): JsonElement {
        var current: Any? = value
        while (current is Optional<*>) {
            current = current.orElse(null)
        }
        if (current is JsonElement) {
            return current
        }
        throw IllegalStateException("Unexpected encoded config type: ${current?.let { it::class.qualifiedName } ?: "null"}")
    }
}
