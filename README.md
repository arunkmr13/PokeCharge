# Charge Catch

A Pokémon screen saver for the Pixel 10 that runs only while charging: a Glyph-style dot ring
shows the charge, and after time on the charger a Pokémon appears. Tap to catch it.

Personal, sideloaded fan project. Not affiliated with Nintendo, The Pokémon Company or Game Freak.

## Sprites

The sprites aren't in the repository. Put the Gen5 PNGs here:

```
app/src/main/assets/sprites/0001.png … 0649.png
app/src/main/assets/sprites/shiny/0001.png … (optional)
```

Without them the app runs with numbered placeholders.

## Set up on the phone

1. Run the app from Android Studio on the Pixel 10.
2. Settings > Display & touch > Screen saver: pick **Charge Catch**, set **When to show** to
   *While charging*. (The app's "Open Pixel screen saver settings" button goes there.)
3. Plug in and let the screen time out.

Use **Preview the screen saver** and **Spawn a test encounter** in the app to try it without
waiting for a charge.

## How it fits together

| File | Role |
|---|---|
| `dream/ChargingDreamService.kt` | The screen saver (Android `DreamService`), hosts the Compose UI |
| `ScreenSaverController.kt` | Wake/hide state, tap to wake, lift to check, minute ticks, catching |
| `battery/BatteryMonitor.kt` | Charge level, plug type and time to full |
| `wake/LiftDetector.kt` | Tilt detection from the gravity sensor |
| `game/EncounterEngine.kt`, `game/Encounters.kt` | When a Pokémon appears, which one, shiny odds, catching |
| `sprites/SpriteRepository.kt` | Loads and trims sprites, drawn pixel-sharp |
| `ui/DotMatrix.kt`, `ui/ScreenSaverScreen.kt` | The 25×25 dot ring, dot digits and the screen |
| `data/` | Species list (PokeAPI), caught Pokémon (Room), settings (DataStore) |
| `MainActivity.kt` | Pokédex and settings |
