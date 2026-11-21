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
    val baby: Boolean = false,
    /** Category, e.g. "Scratch Cat Pokémon". */
    val genus: String = "",
    val types: List<String> = emptyList(),
    /** Height in decimetres and weight in hectograms, as PokeAPI stores them. */
    val heightDm: Int = 0,
    val weightHg: Int = 0,
    val evolvesFrom: Int? = null
) {
    val isSpecial: Boolean get() = legendary || mythical
    val number: String get() = id.toString().padStart(3, '0')
    val heightM: String get() = "%.1f m".format(heightDm / 10.0)
    val weightKg: String get() = "%.1f kg".format(weightHg / 10.0)

    /** How often it turns up, from its capture rate (the same weighting the encounters use). */
    val rarity: String
        get() = when {
            mythical -> "Mythical"
            legendary -> "Legendary"
            captureRate >= 150 -> "Common"
            captureRate >= 75 -> "Uncommon"
            captureRate >= 30 -> "Rare"
            else -> "Very rare"
        }
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

    /** Species that evolve from [id]. */
    fun evolutionsOf(context: Context, id: Int): List<Species> = all(context).filter { it.evolvesFrom == id }
}
