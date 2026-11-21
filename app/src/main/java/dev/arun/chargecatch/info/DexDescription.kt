package dev.arun.chargecatch.info

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The in-game Pokédex description for a species, downloaded from PokeAPI the first time its
 * details are opened and then kept on the phone, so it works offline afterwards. Prefers the
 * Black/White-era wording to match the Gen 5 sprites.
 */
object DexDescription {
    private const val PREFS = "dex_descriptions"
    private val json = Json { ignoreUnknownKeys = true }
    private val PREFERRED_VERSIONS = listOf(
        "black-2", "white-2", "black", "white", "heartgold", "soulsilver", "platinum", "diamond", "pearl"
    )

    fun cached(context: Context, speciesId: Int): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(speciesId.toString(), null)

    /** The description, from the phone if saved, otherwise from PokeAPI; null when offline. */
    suspend fun get(context: Context, speciesId: Int): String? = withContext(Dispatchers.IO) {
        cached(context, speciesId)?.let { return@withContext it }
        val text = runCatching { fetch(speciesId) }.getOrNull() ?: return@withContext null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(speciesId.toString(), text)
            .apply()
        text
    }

    private fun fetch(speciesId: Int): String? {
        val connection = (URL("https://pokeapi.co/api/v2/pokemon-species/$speciesId/").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "ChargeCatch (personal Android app)")
        }
        try {
            if (connection.responseCode != 200) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val entries = json.parseToJsonElement(body).jsonObject["flavor_text_entries"] as? JsonArray ?: return null
            val english = entries.mapNotNull { element ->
                val entry = element as? JsonObject ?: return@mapNotNull null
                val language = (entry["language"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
                if (language != "en") return@mapNotNull null
                val version = (entry["version"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
                val text = entry["flavor_text"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                version to text
            }
            if (english.isEmpty()) return null
            val chosen = PREFERRED_VERSIONS.firstNotNullOfOrNull { wanted -> english.firstOrNull { it.first == wanted } }
                ?: english.last()
            return clean(chosen.second)
        } finally {
            connection.disconnect()
        }
    }

    /** The games' text has hard line breaks, page breaks and soft hyphens; flatten them. */
    private fun clean(text: String): String = text
        .replace("­\n", "")
        .replace("­", "")
        .replace('\n', ' ')
        .replace('\u000C', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}
