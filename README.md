# Sukun

Sukun is a minimal Android launcher built around a calm home screen, low visual noise, and quick access to the features you actually use every day.

## Highlights

- A clean home screen with a large day-ring clock, date, and quick app access
- Optional weather and prayer information on the home screen without turning it into a dashboard
- Focus mode with quick presets, custom durations, and transparent prompts that stay visually consistent with the launcher
- Gesture controls for actions like swipe shortcuts, double tap actions, and optional recents access from the home button
- Appearance controls for theme, text size, keyboard behavior, status bar visibility, and app drawer fast scrolling
- Support for daily notes, daily wallpaper, Private Space, and other lightweight quality-of-life features

## Screenshots

### Home screen

The home screen keeps the layout intentionally quiet: a large circular clock, date, selected apps, and optional contextual information like weather and prayer times.

![Home screen with day ring clock, weather, prayer time, and app shortcuts](screenshots/Media%20(8).jpg)

### Home screen settings

Sukun lets you tune the home screen without overwhelming the interface. The settings shown here cover app count, clock style, day start and end hours, date visibility, app icons, daily notes, weather, and prayer configuration.

![Home screen settings with clock, weather, notes, and prayer options](screenshots/Media%20(7).jpg)

### Appearance and gestures

Appearance and gesture settings stay simple and readable while still exposing useful launcher controls like auto-show keyboard, app drawer fast scroller, theme mode, text size, swipe shortcuts, double tap action, and home-button recents behavior.

![Appearance and gestures settings including fast scroller, theme, and swipe actions](screenshots/Media%20(4).jpg)

### Focus mode

Focus mode is designed to be quick to start and easy to understand. You can launch it from a preset duration picker or choose a custom duration, both using the same translucent dialog style shown in the screenshots.

![Focus mode preset duration dialog](screenshots/Media%20(6).jpg)

![Custom focus mode duration dialog](screenshots/Media%20(5).jpg)

### Minimal home variant

The same home screen can stay even cleaner depending on your settings, with distractions reduced while keeping the main launcher actions within reach.

![Minimal home screen variant](screenshots/Media%20(3).jpg)

## Download

- Debug APK: [`artifacts/sukun-debug.apk`](artifacts/sukun-debug.apk)

## Development

### Requirements

- Android Studio with a current Android SDK
- JDK 17

### Build

```bash
./gradlew :app:assembleDebug
```

On Windows, use:

```powershell
.\gradlew.bat :app:assembleDebug
```

## Project identity

- App name: `Sukun`
- Android application ID: `app.sukun`
- Namespace: `app.sukun`

## Notes

- Some public-facing links are intentionally blank until the new project URLs are finalized.
- The existing git remote is expected to be replaced with your own repository URL in a follow-up step.

## License

[GNU GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html)
