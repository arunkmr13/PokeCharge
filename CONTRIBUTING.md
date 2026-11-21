# Contributing to PokéCharge

Thanks for your interest! Bug reports, ideas and pull requests are all welcome.

## Before you start

- **Bugs:** open an issue using the bug report template. Include the device model, Android version and steps to reproduce.
- **Features:** open an issue first so we can agree on the approach before you write code.
- **Sprites:** don't commit Pokémon sprite files. `app/src/main/assets/sprites/` is in `.gitignore` on purpose.

## Development setup

1. Install the latest stable Android Studio and JDK 17.
2. Clone the repo and open it in Android Studio.
3. Run the app once while online; it downloads the sprites by itself. To skip that, put sprites in `app/src/main/assets/sprites/` (see the README).
4. Run on a device or emulator with Android 15 (API 35) or later.

Use **Preview the screen saver** and **Spawn a test encounter** in the app to test without waiting for a charge.

## Pull requests

1. Create a branch from `main`, for example `feat/home-widget` or `fix/lift-detector`.
2. Keep each PR focused on one change.
3. Make sure `./gradlew assembleDebug` passes.
4. Write commit messages in the imperative mood: "Add home-screen widget", not "Added widget".

## Releasing (maintainer)

1. Raise `versionCode` by 1 and set `versionName` in `app/build.gradle.kts`, then commit and push.
2. Tag and push: `git tag v1.0.1 && git push origin v1.0.1`.
3. GitHub Actions builds the APK, signs it with the key from the `RELEASE_KEYSTORE_BASE64` and `RELEASE_KEYSTORE_PASSWORD` secrets, and publishes the release.

Every release must use the same key, or phones can't update without uninstalling.
5. Fill in the PR template, and add screenshots for UI changes.

## Code style

- Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html) (Android Studio's default formatter).
- Keep screen saver logic in `ScreenSaverController` and UI in `ui/`.
- Add KDoc to new public classes, like the existing files have.
