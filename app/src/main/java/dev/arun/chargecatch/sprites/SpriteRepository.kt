package dev.arun.chargecatch.sprites

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Loads the 320 px sprites from assets/sprites/NNNN.png (shiny ones from assets/sprites/shiny/),
 * trimmed to their visible pixels so every Pokémon sits centered. Missing files return null and
 * the UI shows a placeholder, so the app runs before the sprites are copied in.
 */
class SpriteRepository private constructor(private val context: Context) {
    private val cache = LruCache<String, ImageBitmap>(32)

    /** Species numbers that have a sprite file, read once. */
    val availableIds: Set<Int> by lazy {
        runCatching { context.assets.list(SPRITE_DIR) }.getOrNull().orEmpty()
            .mapNotNull { name -> NAME.matchEntire(name)?.groupValues?.get(1)?.toInt() }
            .toSet()
    }

    private val shinyIds: Set<Int> by lazy {
        runCatching { context.assets.list("$SPRITE_DIR/shiny") }.getOrNull().orEmpty()
            .mapNotNull { name -> NAME.matchEntire(name)?.groupValues?.get(1)?.toInt() }
            .toSet()
    }

    fun load(speciesId: Int, shiny: Boolean = false): ImageBitmap? {
        val useShiny = shiny && speciesId in shinyIds
        val key = "$speciesId:$useShiny"
        cache.get(key)?.let { return it }
        val file = speciesId.toString().padStart(4, '0') + ".png"
        val path = if (useShiny) "$SPRITE_DIR/shiny/$file" else "$SPRITE_DIR/$file"
        val bitmap = runCatching {
            context.assets.open(path).use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inScaled = false })
            }
        }.getOrNull() ?: return null
        return trim(bitmap).asImageBitmap().also { cache.put(key, it) }
    }

    /** An item image from assets/items/, such as the ball (items/ball.png); null if missing. */
    fun loadItem(name: String): ImageBitmap? {
        val key = "item:$name"
        cache.get(key)?.let { return it }
        val bitmap = runCatching {
            context.assets.open("items/$name").use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inScaled = false })
            }
        }.getOrNull() ?: return null
        return trim(bitmap).asImageBitmap().also { cache.put(key, it) }
    }

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

    companion object {
        private const val SPRITE_DIR = "sprites"
        private val NAME = Regex("""(\d{4})\.png""")

        @Volatile
        private var instance: SpriteRepository? = null

        fun get(context: Context): SpriteRepository = instance ?: synchronized(this) {
            instance ?: SpriteRepository(context.applicationContext).also { instance = it }
        }
    }
}
