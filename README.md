# Sukun Minimalistic Launcher

Sukun Minimalistic Launcher is a minimal Android launcher built around a calm home screen, low visual noise, and quick access to the features you actually use every day.

## Highlights

- A clean home screen with a large day-ring clock, date, and quick app access
- Optional weather and prayer information on the home screen without turning it into a dashboard
- Focus mode with quick presets, custom durations, and transparent prompts that stay visually consistent with the launcher
- Gesture controls for actions like swipe shortcuts, double tap actions, and optional recents access from the home button
- Appearance controls for theme, text size, keyboard behavior, status bar visibility, and app drawer fast scrolling
- Support for daily notes, daily wallpaper, Private Space, and other lightweight quality-of-life features

## Screenshots

### Home screen

The home screen keeps the layout intentionally quiet. Instead of filling the page with widgets, it gives you:

- a large day-ring clock and date
- a short list of selected apps
- optional weather and prayer information
- a wallpaper-led design with low visual clutter

<p align="center">
  <a href="screenshots/Media%20(8).jpg">
    <img src="screenshots/Media%20(8).jpg" alt="Home screen with day ring clock, weather, prayer time, and app shortcuts" width="280" />
  </a>
</p>

### Home screen settings

Sukun lets you tune the home screen without turning settings into a maze. This part of the app covers:

- number of apps shown on the home screen
- clock style and day timing
- date and app icon visibility
- daily notes
- weather source, location, and units
- prayer time visibility and related options

<p align="center">
  <a href="screenshots/Media%20(7).jpg">
    <img src="screenshots/Media%20(7).jpg" alt="Home screen settings with clock, weather, notes, and prayer options" width="280" />
  </a>
</p>

### Appearance and gestures

Appearance and gesture settings stay simple and readable while still exposing useful controls such as:

- auto-show keyboard
- app drawer fast scroller
- daily wallpaper and status bar visibility
- theme mode and text size
- swipe-left and swipe-right app shortcuts
- double tap behavior and optional recents access

<p align="center">
  <a href="screenshots/Media%20(4).jpg">
    <img src="screenshots/Media%20(4).jpg" alt="Appearance and gestures settings including fast scroller, theme, and swipe actions" width="280" />
  </a>
</p>

### Focus mode

Focus mode is designed to be quick to start and easy to understand. You can:

- start a common preset in one tap
- choose a fully custom duration
- keep the dialog visually consistent with the launcher instead of switching to a heavy system-style sheet

<p align="center">
  <a href="screenshots/Media%20(6).jpg">
    <img src="screenshots/Media%20(6).jpg" alt="Focus mode preset duration dialog" width="260" />
  </a>
  <a href="screenshots/Media%20(5).jpg">
    <img src="screenshots/Media%20(5).jpg" alt="Custom focus mode duration dialog" width="260" />
  </a>
</p>

### Minimal home variant

The same home screen can stay even cleaner depending on your settings, with distractions reduced while keeping the core launcher actions within reach.

<p align="center">
  <a href="screenshots/Media%20(3).jpg">
    <img src="screenshots/Media%20(3).jpg" alt="Minimal home screen variant" width="280" />
  </a>
</p>

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

- App name: `Sukun Minimalistic Launcher`
- Android application ID: `sukun.minimalist.app.launcher.com`
- Namespace: `sukun.minimalist.app.launcher.com`

## Notes

- Some public-facing links are intentionally blank until the new project URLs are finalized.
- The existing git remote is expected to be replaced with your own repository URL in a follow-up step.

## License

[GNU GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html)
