package dev.arun.chargecatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import dev.arun.chargecatch.ui.ScreenSaverScreen

/**
 * The screen saver UI in a normal full-screen activity, to try it without plugging in or waiting
 * for the screen to time out. Encounters still need the minute ticks, so use "Spawn a test
 * encounter" in the app to see one right away.
 */
class PreviewActivity : ComponentActivity() {
    private var controller: ScreenSaverController? = null

    override fun onDestroy() {
        controller?.stop()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val saverController = ScreenSaverController(this, lifecycleScope, requireCharging = false)
        controller = saverController
        saverController.start()
        setContent { ScreenSaverScreen(saverController, onExit = { finish() }) }
    }
}
