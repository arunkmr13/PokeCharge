package dev.arun.chargecatch.game

import dev.arun.chargecatch.data.Species
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Decides which Pokémon appears. (When is a fixed rhythm, set in the controller.)
 *
 * Weighted by the species' capture rate, so common Pokémon are common. The longer the phone has
 * been charging, the flatter the weights get, so rare ones turn up on long (overnight) charges.
 * Legendary and mythical Pokémon are extra rare, and any Pokémon is shiny 1 time in 512.
 */
class EncounterEngine(private val random: Random = Random.Default) {

    /**
     * Picks a species from [pool] (already limited to the chosen region and to available sprites).
     */
    fun pick(pool: List<Species>, minutesCharging: Int): Species? {
        if (pool.isEmpty()) return null
        val flatten = 0.5 * min(1.0, minutesCharging / FLATTEN_OVER_MINUTES)
        val weights = pool.map { species ->
            val base = species.captureRate.coerceAtLeast(1).toDouble().pow(1.0 - flatten)
            if (species.isSpecial) base * SPECIAL_FACTOR else base
        }
        var roll = random.nextDouble() * weights.sum()
        pool.forEachIndexed { index, species ->
            roll -= weights[index]
            if (roll <= 0) return species
        }
        return pool.last()
    }

    fun rollShiny(): Boolean = random.nextInt(SHINY_ODDS) == 0

    companion object {
        const val SHINY_ODDS = 512
        private const val FLATTEN_OVER_MINUTES = 180.0
        private const val SPECIAL_FACTOR = 0.2
    }
}
