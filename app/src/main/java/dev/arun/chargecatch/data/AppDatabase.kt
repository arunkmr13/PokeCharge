package dev.arun.chargecatch.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Every Pokémon caught, one row per catch (duplicates allowed).
 */
@Entity(tableName = "caught")
data class Caught(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val speciesId: Int,
    val shiny: Boolean,
    val caughtAt: Long,
    val minutesCharging: Int
)

/**
 * The Pokédex view of a species: how many caught and whether any was shiny.
 */
data class DexEntry(val speciesId: Int, val count: Int, val anyShiny: Boolean)

@Dao
interface CaughtDao {
    @Insert
    suspend fun insert(caught: Caught): Long

    @Query("SELECT speciesId, COUNT(*) AS count, MAX(shiny) AS anyShiny FROM caught GROUP BY speciesId")
    fun watchDex(): Flow<List<DexEntry>>

    @Query("SELECT COUNT(DISTINCT speciesId) FROM caught")
    fun watchRegisteredCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT speciesId) FROM caught")
    suspend fun registeredCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM caught WHERE speciesId = :speciesId)")
    suspend fun isRegistered(speciesId: Int): Boolean
}

@Database(entities = [Caught::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun caughtDao(): CaughtDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "charge_catch.db")
                .build()
                .also { instance = it }
        }
    }
}
