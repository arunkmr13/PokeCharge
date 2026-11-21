package dev.arun.chargecatch.sprites

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sprites for all 649 Pokémon (and their shiny forms), trimmed to their visible pixels so every
 * Pokémon sits centered.
 *
 * Where they come from, in order:
 * 1. assets/sprites/NNNN.png (shiny in assets/sprites/shiny/), if you built the app with them.
 * 2. The phone's own storage, filled on first launch from PokeAPI's public Black/White sprites.
 *    They are 96 px pixel art, saved scaled to 320 px without smoothing so they stay crisp.
 *
 * Nothing is bundled in the repository or the APK. Missing sprites return null and the UI shows a
 * placeholder until the download reaches them.
 */
class SpriteRepository private constructor(private val context: Context) {
    private val cache = LruCache<String, ImageBitmap>(32)
    private val downloadDir = File(context.filesDir, SPRITE_DIR)
    private val shinyDownloadDir = File(downloadDir, "shiny")

    private val bundledIds: Set<Int> by lazy { listAssets(SPRITE_DIR) }
    private val bundledShinyIds: Set<Int> by lazy { listAssets("$SPRITE_DIR/shiny") }

    @Volatile private var downloadedIds: Set<Int> = listFiles(downloadDir)
    @Volatile private var downloadedShinyIds: Set<Int> = listFiles(shinyDownloadDir)

    private val _revision = MutableStateFlow(0)

    /** Goes up whenever new sprites land, so screens can reload ones that were missing. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val _download = MutableStateFlow(DownloadState())

    /** Progress of the first-launch download, for the Settings tab. */
    val download: StateFlow<DownloadState> = _download.asStateFlow()

    private val downloadLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Species numbers that have a regular sprite, bundled or downloaded. */
    val availableIds: Set<Int> get() = bundledIds + downloadedIds

    /** How many sprites (regular and shiny) are on the phone, out of [TOTAL_SPRITES]. */
    val spriteCount: Int get() = (bundledIds + downloadedIds).size + (bundledShinyIds + downloadedShinyIds).size

    fun load(speciesId: Int, shiny: Boolean = false): ImageBitmap? {
        val useShiny = shiny && (speciesId in bundledShinyIds || speciesId in downloadedShinyIds)
        val key = "$speciesId:$useShiny"
        cache.get(key)?.let { return it }
        val file = fileName(speciesId)
        val bitmap = if (useShiny) {
            if (speciesId in bundledShinyIds) decodeAsset("$SPRITE_DIR/shiny/$file") else decodeFile(File(shinyDownloadDir, file))
        } else {
            when (speciesId) {
                in bundledIds -> decodeAsset("$SPRITE_DIR/$file")
                in downloadedIds -> decodeFile(File(downloadDir, file))
                else -> null
            }
        } ?: return null
        return trim(bitmap).asImageBitmap().also { cache.put(key, it) }
    }

    /** An item image from assets/items/, such as the ball (items/ball.png); null if missing. */
    fun loadItem(name: String): ImageBitmap? {
        val key = "item:$name"
        cache.get(key)?.let { return it }
        val bitmap = decodeAsset("items/$name") ?: return null
        return trim(bitmap).asImageBitmap().also { cache.put(key, it) }
    }

    /**
     * Starts fetching every sprite the phone doesn't have yet, in the background. Safe to call
     * often: it does nothing when all are there or a download is already running.
     */
    fun downloadMissing() {
        if (_download.value.running) return
        scope.launch {
            downloadLock.withLock {
                val missing = (1..SPECIES_COUNT).filter { it !in bundledIds && it !in downloadedIds }.map { it to false } +
                    (1..SPECIES_COUNT).filter { it !in bundledShinyIds && it !in downloadedShinyIds }.map { it to true }
                if (missing.isEmpty()) return@withLock
                val done = AtomicInteger(0)
                val failed = AtomicInteger(0)
                _download.value = DownloadState(running = true, done = 0, total = missing.size)
                val permits = Semaphore(PARALLEL_DOWNLOADS)
                // Regular sprites come first, in Pokédex order, so the first Pokémon show up quickly
                missing.map { (id, shiny) ->
                    async {
                        permits.withPermit {
                            if (fetch(id, shiny)) {
                                markDownloaded(id, shiny)
                            } else {
                                failed.incrementAndGet()
                            }
                            val count = done.incrementAndGet()
                            _download.update { it.copy(done = count, failed = failed.get()) }
                            if (count % REVISION_EVERY == 0) _revision.update { it + 1 }
                        }
                    }
                }.awaitAll()
                _download.update { it.copy(running = false) }
                _revision.update { it + 1 }
            }
        }
    }

    private fun fetch(speciesId: Int, shiny: Boolean): Boolean = runCatching {
        val url = URL(if (shiny) "$BASE_URL/shiny/$speciesId.png" else "$BASE_URL/$speciesId.png")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "PokeCharge (Android app)")
        }
        try {
            if (connection.responseCode != 200) return@runCatching false
            val source = connection.inputStream.use { BitmapFactory.decodeStream(it) } ?: return@runCatching false
            // Nearest-neighbour scaling keeps the pixel art sharp
            val scaled = Bitmap.createScaledBitmap(source, SPRITE_SIZE, SPRITE_SIZE, false)
            val dir = if (shiny) shinyDownloadDir else downloadDir
            dir.mkdirs()
            val target = File(dir, fileName(speciesId))
            val partial = File(dir, "${target.name}.part")
            partial.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            partial.renameTo(target)
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    @Synchronized
    private fun markDownloaded(speciesId: Int, shiny: Boolean) {
        if (shiny) downloadedShinyIds = downloadedShinyIds + speciesId else downloadedIds = downloadedIds + speciesId
    }

    private fun decodeAsset(path: String): Bitmap? = runCatching {
        context.assets.open(path).use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inScaled = false })
        }
    }.getOrNull()

    private fun decodeFile(file: File): Bitmap? = runCatching {
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inScaled = false })
    }.getOrNull()

    private fun listAssets(dir: String): Set<Int> =
        runCatching { context.assets.list(dir) }.getOrNull().orEmpty()
            .mapNotNull { name -> NAME.matchEntire(name)?.groupValues?.get(1)?.toInt() }
            .toSet()

    private fun listFiles(dir: File): Set<Int> =
        dir.list().orEmpty()
            .mapNotNull { name -> NAME.matchEntire(name)?.groupValues?.get(1)?.toInt() }
            .toSet()

    private fun fileName(speciesId: Int) = speciesId.toString().padStart(4, '0') + ".png"

    private fun trim(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        var left = w
        var top = h
        var right = -1
        var bottom = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if ((pixels[y * w + x] ushr 24) != 0) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < left || bottom < top) return source
        return Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1)
    }

    data class DownloadState(
        val running: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val failed: Int = 0
    )

    companion object {
        const val SPECIES_COUNT = 649
        const val TOTAL_SPRITES = SPECIES_COUNT * 2

        private const val SPRITE_DIR = "sprites"
        private const val SPRITE_SIZE = 320
        private const val PARALLEL_DOWNLOADS = 6
        private const val REVISION_EVERY = 24
        private const val BASE_URL =
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/versions/generation-v/black-white"
        private val NAME = Regex("""(\d{4})\.png""")

        @Volatile
        private var instance: SpriteRepository? = null

        fun get(context: Context): SpriteRepository = instance ?: synchronized(this) {
            instance ?: SpriteRepository(context.applicationContext).also { instance = it }
        }
    }
}
