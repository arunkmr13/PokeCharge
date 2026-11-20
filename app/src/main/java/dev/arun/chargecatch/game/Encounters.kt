package dev.arun.chargecatch.game

import android.content.Context
import dev.arun.chargecatch.data.AppDatabase
import dev.arun.chargecatch.data.Caught
import dev.arun.chargecatch.data.PendingEncounter
import dev.arun.chargecatch.data.SettingsRepository
import dev.arun.chargecatch.data.Species
import dev.arun.chargecatch.data.SpeciesRepository
import dev.arun.chargecatch.sprites.SpriteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class CatchResult(val species: Species, val shiny: Boolean, val isNew: Boolean, val registered: Int, val total: Int)

/**
 * Spawning and catching, shared by the screen saver, the preview and the "test encounter" button.
 */
object Encounters {
    private val engine = EncounterEngine()

    /** Puts a new Pokémon in the waiting slot, replacing nothing if one already waits. */
    suspend fun spawn(context: Context, minutesCharging: Int): PendingEncounter? = withContext(Dispatchers.IO) {
        val settingsRepo = SettingsRepository(context)
        if (settingsRepo.pending.first() != null) return@withContext null
        val region = settingsRepo.settings.first().region
        val available = SpriteRepository.get(context).availableIds
        val pool = SpeciesRepository.all(context).filter { species ->
            species.id <= region.maxId && (available.isEmpty() || species.id in available)
        }
        val species = engine.pick(pool, minutesCharging) ?: return@withContext null
        val encounter = PendingEncounter(
            speciesId = species.id,
            shiny = engine.rollShiny(),
            appearedAt = System.currentTimeMillis(),
            minutesCharging = minutesCharging
        )
        settingsRepo.setPending(encounter)
        encounter
    }

    fun shouldAppear(minutesSinceEncounter: Int): Boolean = engine.shouldAppear(minutesSinceEncounter)

    /** Catches the waiting Pokémon (always succeeds) and records it. */
    suspend fun catchPending(context: Context): CatchResult? = withContext(Dispatchers.IO) {
        val settingsRepo = SettingsRepository(context)
        val pending = settingsRepo.pending.first() ?: return@withContext null
        val species = SpeciesRepository.byId(context, pending.speciesId) ?: run {
            settingsRepo.setPending(null)
            return@withContext null
        }
        val dao = AppDatabase.get(context).caughtDao()
        val isNew = !dao.isRegistered(species.id)
        dao.insert(
            Caught(
                speciesId = species.id,
                shiny = pending.shiny,
                caughtAt = System.currentTimeMillis(),
                minutesCharging = pending.minutesCharging
            )
        )
        settingsRepo.setPending(null)
        val total = settingsRepo.settings.first().region.maxId
        CatchResult(species, pending.shiny, isNew, dao.registeredCount(), total)
    }
}
