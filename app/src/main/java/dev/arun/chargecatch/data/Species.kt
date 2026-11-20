package dev.arun.chargecatch.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One Pokémon species, from the bundled assets/species.json (PokeAPI data, #1–649).
 */
@Serializable
data class Species(
    val id: Int,
    val name: String,
    val gen: Int,
    val captureRate: Int,
    val legendary: Boolean = false,
    val mythical: Boolean = false,
    val baby: Boolean = false
) {
    val isSpecial: Boolean get() = legendary || mythical
    val number: String get() = id.toString().padStart(3, '0')
}

/**
 * Region caps for the "Pokémon pool" setting: the highest National Dex number per region.
 */
enum class Region(val label: String, val maxId: Int) {
    KANTO("Kanto", 151),
    JOHTO("Johto", 251),
    HOENN("Hoenn", 386),
    SINNOH("Sinnoh", 493),
    UNOVA("Unova", 649)
}

object SpeciesRepository {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cache: List<Species>? = null

    fun all(context: Context): List<Species> = cache ?: synchronized(this) {
        cache ?: context.applicationContext.assets.open("species.json")
            .bufferedReader()
            .use { json.decodeFromString<List<Species>>(it.readText()) }
            .also { cache = it }
    }

    fun byId(context: Context, id: Int): Species? = all(context).getOrNull(id - 1)?.takeIf { it.id == id }
}
