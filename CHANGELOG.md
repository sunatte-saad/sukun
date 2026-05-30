# Changelog

All notable changes to Sukun are documented here.

---

## [Unreleased] — 2026-05-30

### Added
- **Prayer notifications with "Mark as Prayed" action** — a high-priority notification is shown at each prayer time with a "Prayed ✓" action button. Tapping the button logs the prayer and dismisses the notification. Notifications fire independently of the azan setting.
- **Home-screen prayer mark** — long-pressing the prayer text on the home screen shows a confirmation dialog to mark that prayer as prayed.
- **Prayer analytics screen** — new screen accessible from Settings → Prayer Times → Prayer Analytics. Shows today's prayers (✓/–), per-prayer counts for the current month (e.g. `Fajr  12 / 13`), and the same breakdown for the current year, with totals.
- **Prayer log storage** — each marked prayer is persisted as a JSON entry (prayer key, date, timestamp) in SharedPreferences. Entries are deduplicated per prayer+day and automatically pruned after two years.
- **`PrayerMarkReceiver`** — new `BroadcastReceiver` that handles the "Prayed ✓" notification action. Registered in `AndroidManifest.xml`.
- **`PrayerAnalyticsFragment`** — new fragment wired into the navigation graph and accessible from the prayer section of Settings.
- **Reminder completion tracking** — reminders now record how many times they fired (`fireCount`) and how many times the user marked them done (`doneCount`). Both fields are persisted in the existing SharedPreferences JSON, so old reminder data is fully backwards-compatible.
- **"Done" notification action** — when a reminder notification appears, a "Done" button lets the user acknowledge it directly from the notification shade without opening the app. Tapping Done increments the done count and dismisses the notification.
- **Reminder analytics in UI** — each reminder card in the Reminders screen now shows a completion stat line (e.g. `5/8 done (62%)`) once the reminder has fired at least once. Hidden until first fire to avoid clutter on new reminders.
- **`ReminderDoneReceiver`** — new `BroadcastReceiver` that handles the Done notification action. Registered in `AndroidManifest.xml`.
- **`isLocationServicesEnabled()` extension** — utility function on `Context` that checks whether GPS or network location is enabled, with API-level branching for Android P+.
- **Language selection feature** — runtime app language localization with support for multiple locales. Users can select their preferred language from Settings.
- **Ambient light theme** — new theme mode that automatically switches between light and dark themes based on device ambient light sensor.
- **Reminders feature** — new reminders/tasks functionality with settings management, notifications, and completion tracking.
- **Cooldown feature** — new cooldown configuration with warning dialogs to help manage app usage and focus.
- **Chinese translations** — complete translations for Simplified and Traditional Chinese with rounded dialog drawable.

### Changed
- **Weather settings consolidated into a bottom sheet** — the three separate weather settings rows (source, location, units) plus the on/off toggle have been merged into a single "Weather" row that opens a blurred bottom sheet. The sheet has a transparent background with blur-behind (API 31+) and shows an on/off toggle, source chips (Manual / Device / Google), location input, and unit chips (°C / °F). The settings row shows a live summary (e.g. `Device · °C`) when weather is enabled.
- **Prayer settings consolidated into a bottom sheet** — all prayer settings rows (on/off, source, location, azan sound, custom azan file, analytics) previously spread across the settings list have been merged into a single "Prayer times" row that opens a blurred bottom sheet. The sheet embeds the full prayer analytics (today / month / year breakdowns) inline rather than navigating to a separate screen. The settings row shows a live summary (e.g. `Manual · Makkah`) when prayer is enabled.
- **Prayer alarm scheduling decoupled from azan** — the prayer reminder alarm now fires whenever prayer times are enabled, regardless of whether the azan sound is on. The azan plays only if `azanEnabled` is true; the notification always appears.
- **Weather location flow in Settings** — tapping the weather location option now branches by source mode: device mode checks for location permission and, if granted, checks whether location services are enabled (showing a toast and opening system settings if not); manual mode shows the text input as before.
- **Weather display on home screen** — weather text now shows `temperature  condition` only (e.g. `22°C  Partly cloudy`). The location label and precipitation text have been removed from the display string so the temperature is always visible on the single-line view.
- **Weather and prayer fallback locations removed** — removed hardcoded fallback coordinates (London for weather, Mecca for prayer). Code now returns `null` when no location is configured rather than silently using a default.
- **Null-safe settings binding** — `binding.remindersManage` access uses safe-call (`?.`) to avoid crashes if the view is absent in a layout variant.
- **`.gitignore` fix** — changed `/build` to `build/` so all module build directories (including `app/build/`) are excluded. Removed all previously tracked `app/build/` artifacts from the repository index.
- **HomeFragment layout adjustments** — enhanced layout for better text size scaling and improved visual hierarchy.
- **Prayer logging functionality** — enhanced with improved UI interactions for marking prayers and tracking completion.
- **Accessibility service** — enhanced focus mode handling with improved event tracking.
- **Screen time calculation** — refactored to use coroutines for improved performance and error handling.
- **Preferences and settings UI** — simplified for better user experience and maintainability.
- **Text size thresholds** — adjusted with added elevation to layouts for improved UI clarity.
- **Divider styling** — updated background color in settings layout for improved UI consistency.
- **Launcher icons** — updated with enhanced UI theme handling.

### Removed
- **Hindi language option** — removed from available languages list.

### Internal
- `PrayerReminderScheduler.scheduleNextReminder()` no longer requires `azanEnabled`; the gate is now `showPrayerOnHome` only.
- `MainViewModel.loadPrayerState()` and `refreshPrayerData()` updated to match — schedule whenever prayers are on, cancel only when they're off.
- `PrayerBootReceiver` updated to reschedule on boot whenever prayers are enabled (previously skipped if azan was off).
- `Prefs.logPrayer()` and `Prefs.getPrayerLogs()` added for prayer log persistence using a compact JSON format (`p`/`d`/`t` keys).
- Added `Constants.Prayer.MARK_NOTIFICATION_ID`, `MARK_REQUEST_CODE`, and `Constants.PRAYER_MARK_ACTION`.
- Added `Constants.Reminder.ACTION_DONE` and `BASE_DONE_REQUEST_CODE` for the Done broadcast.
- `Reminder.toJson()` / `fromJson()` updated to include `fireCount` and `doneCount` (optional fields with default 0 for backwards compatibility).
- `ReminderReceiver` now increments `fireCount` before showing the notification and no longer re-schedules the alarm separately — it reuses the already-loaded reminder object.

---

## How to contribute

- Branch from `master`, open a PR against `master`.
- No automated tests exist — manually verify on a device before requesting review.
- Build: `.\gradlew.bat :app:assembleDebug` (requires JDK 17).
- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
