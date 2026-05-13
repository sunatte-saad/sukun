# Changelog

All notable changes to Sukun are documented here.

---

## [Unreleased] — 2026-05-13

### Added
- **Reminder completion tracking** — reminders now record how many times they fired (`fireCount`) and how many times the user marked them done (`doneCount`). Both fields are persisted in the existing SharedPreferences JSON, so old reminder data is fully backwards-compatible.
- **"Done" notification action** — when a reminder notification appears, a "Done" button lets the user acknowledge it directly from the notification shade without opening the app. Tapping Done increments the done count and dismisses the notification.
- **Reminder analytics in UI** — each reminder card in the Reminders screen now shows a completion stat line (e.g. `5/8 done (62%)`) once the reminder has fired at least once. Hidden until first fire to avoid clutter on new reminders.
- **`ReminderDoneReceiver`** — new `BroadcastReceiver` that handles the Done notification action. Registered in `AndroidManifest.xml`.
- **`isLocationServicesEnabled()` extension** — utility function on `Context` that checks whether GPS or network location is enabled, with API-level branching for Android P+.

### Changed
- **Weather location flow in Settings** — tapping the weather location option now branches by source mode: device mode checks for location permission and, if granted, checks whether location services are enabled (showing a toast and opening system settings if not); manual mode shows the text input as before.
- **Weather display on home screen** — weather text is now single-line with fields separated by spaces instead of a two-line card layout. Removed the card background, padding, and min/max width constraints.
- **Weather and prayer fallback locations removed** — removed hardcoded fallback coordinates (London for weather, Mecca for prayer). Code now returns `null` when no location is configured rather than silently using a default.
- **Null-safe settings binding** — `binding.remindersManage` access uses safe-call (`?.`) to avoid crashes if the view is absent in a layout variant.
- **`.gitignore` fix** — changed `/build` to `build/` so all module build directories (including `app/build/`) are excluded. Removed all previously tracked `app/build/` artifacts from the repository index.

### Internal
- Added `Constants.Reminder.ACTION_DONE` and `BASE_DONE_REQUEST_CODE` for the Done broadcast.
- `Reminder.toJson()` / `fromJson()` updated to include `fireCount` and `doneCount` (optional fields with default 0 for backwards compatibility).
- `ReminderReceiver` now increments `fireCount` before showing the notification and no longer re-schedules the alarm separately — it reuses the already-loaded reminder object.

---

## How to contribute

- Branch from `master`, open a PR against `master`.
- No automated tests exist — manually verify on a device before requesting review.
- Build: `.\gradlew.bat :app:assembleDebug` (requires JDK 17).
- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
