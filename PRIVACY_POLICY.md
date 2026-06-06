# Privacy Policy — Sukun Minimalistic Launcher

**Effective date:** June 6, 2026  
**Last updated:** June 6, 2026

---

## 1. Who we are

This Privacy Policy applies to **Sukun Minimalistic Launcher** (“**Sukun**”, “**the app**”, “**we**”, “**us**”, “**our**”).

| | |
|---|---|
| **App name** | Sukun Minimalistic Launcher |
| **Android package** | `sukun.minimalist.app.launcher.com` |
| **Developer** | sunatte-saad (as shown on the Google Play store listing) |
| **Type of app** | Android home-screen launcher |

This policy explains what information the app accesses, how that information is used, whether it is shared, and what choices you have. It is intended to meet [Google Play’s User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311) and to align with the **Data safety** section you will complete in Google Play Console.

**Important:** Sukun is built to keep your data on your device. We do **not** operate backend servers that collect, store, or profile your personal information. We do **not** sell your data.

---

## 2. Summary

- **No ads. No analytics SDKs. No tracking.**
- Launcher settings, notes, todos, reminders, prayer logs, and usage summaries are stored **locally on your device**.
- **Location** is used only if you turn on optional weather or prayer-time features and choose device or manual location.
- **Usage access** is used only if you turn on optional screen time or app cooldown features.
- **Accessibility** is optional and used only for double-tap lock, home-button recents, and focus mode.
- Network requests are made **only for optional features you enable** (weather, wallpapers) or when **you choose** to open a search engine or external link.

---

## 3. Information the app accesses and collects

Google Play requires us to disclose how the app handles user data. Below is a complete list based on how Sukun actually works.

### 3.1 Data we do **not** collect

Sukun does **not** collect, and we do **not** receive on our servers:

- Name, email address, phone number, postal address, or account login credentials
- Contacts, SMS, call logs, photos, videos, or general file contents
- Browsing history (except when you deliberately open an external browser or search engine)
- Payment or financial information
- Advertising identifiers or analytics profiles
- Health or fitness data

We do not operate a user account system and do not ask you to sign in.

### 3.2 Data stored locally on your device

The following data may be stored in app-private storage (for example, SharedPreferences) on your device to provide launcher and optional features:

| Data type | Examples | Why it is stored |
|-----------|----------|------------------|
| **App preferences** | Theme, language, text size, layout, gesture settings | Personalize the launcher |
| **Installed-app information** | App names, package names, icons, hidden-app list, renamed labels, home shortcuts | Core launcher functionality |
| **Focus & productivity data** | Focus mode settings, allowed/blocked apps, cooldown limits, daily open counts and session duration for apps you configure | Focus mode and app cooldown features |
| **Notes and tasks** | Daily notes, todo items | Personal productivity features |
| **Reminders** | Titles, messages, schedules, fire count, done count | Local reminder notifications |
| **Prayer-related data** | Location label, latitude/longitude, next prayer time, optional prayer completion logs (prayer name, date, timestamp) | Prayer times and optional prayer analytics |
| **Weather cache** | Location label, coordinates, temperature, condition text | Home-screen weather display |
| **Wallpaper settings** | Daily wallpaper preference, cached image URL | Optional wallpaper feature |
| **Audio file references** | URI of a custom azan or chime file you choose | Play a sound you selected |
| **Onboarding flags** | First-open timestamps, dialog shown state | First-run experience |

**Prayer logs:** If you mark a prayer as completed, Sukun stores a local log entry. Entries are deduplicated per prayer per day and **automatically deleted after two years**.

**App cooldown data:** Stored only for apps you configure. Daily usage counters reset each calendar day.

This local data is **not transmitted to us**.

### 3.3 Location data

Sukun requests location permission **only** for optional weather and prayer-time features.

| Mode | What happens |
|------|----------------|
| **Manual location** | You type a city or place name. Sukun may send that text to Open-Meteo’s geocoding API to resolve coordinates. GPS is not required. |
| **Device location** | With your permission, Sukun reads your device’s current approximate or precise location to calculate prayer times or fetch weather. Location is processed on-device and may be sent to Open-Meteo for weather lookup. |
| **Google weather source** | Sukun opens Google Search in your browser; no weather API call is made by Sukun in that mode. |
| **Feature off** | If weather and prayer times are disabled, Sukun does not access location. |

**We do not track your location over time, build a location history, or receive location data on our servers.**

You can disable location access at any time in Android **Settings → Apps → Sukun → Permissions → Location**, or by turning off weather and prayer features in Sukun settings.

### 3.4 App activity and usage data

If you grant **Usage access** (`PACKAGE_USAGE_STATS`), Sukun reads Android usage statistics **on your device only** to:

- Show today’s total screen time on the home screen (optional)
- Track open counts and session duration for **app cooldown** rules you configure (optional)

This data is **not uploaded to us**. You can revoke access in Android **Settings → Apps → Special app access → Usage access**.

As a launcher, Sukun also reads the list of installed applications on your device so the app drawer and home screen can function. This list is used locally and is **not sent to us**.

### 3.5 Accessibility data

If you enable Sukun’s **optional accessibility service**, the app may observe accessibility events on your device to:

- Lock the screen on double tap (supported Android versions)
- Open recent apps from the home button
- Enforce focus mode by detecting the foreground app package name

Accessibility data is processed **only on your device**. Sukun **does not** send accessibility events, screen content, or window text to us or to third parties.

You can disable the service at any time in Android **Settings → Accessibility**.

### 3.6 Data sent to third-party services (from your device)

When you use certain optional features, your device contacts third-party services **directly**. We do not proxy this traffic through our servers.

| Service | When it is used | Data that may be sent |
|---------|-----------------|------------------------|
| **Open-Meteo** (`geocoding-api.open-meteo.com`, `api.open-meteo.com`) | Optional weather enabled | City name and/or coordinates |
| **Unsplash** (`images.unsplash.com`) | Optional daily wallpaper enabled | Standard HTTP request to download an image |
| **DuckDuckGo** (`duck.co`) | You search from the app drawer | Your search query |
| **Google Search** | You choose Google weather source or open a Google search action | Handled in your browser under Google’s policies |
| **Amazon** | You open an optional affiliate support link | Handled on Amazon’s website under Amazon’s policies |

Each third party has its own privacy policy. We do not control how they handle data.

### 3.7 Device administrator (optional)

On older Android versions, Sukun may use the **Device administrator** permission as a fallback to lock the screen. This permission is used only for locking and is not used to collect data.

---

## 4. How we use information

We use information only to:

- Operate the launcher (show apps, shortcuts, gestures, wallpapers you choose)
- Save your preferences and optional content (notes, todos, reminders)
- Calculate and display optional prayer times and weather
- Send **local notifications** for reminders, prayer times, and optional hourly chime
- Enforce optional focus mode and app cooldown rules you configure
- Play optional azan or chime audio you enable

We do **not** use your data for advertising, marketing profiles, or selling to data brokers.

---

## 5. How we share information

We do **not** sell, rent, or trade your personal information.

Information may leave your device only in these cases:

1. **Third-party services you use** — weather APIs, wallpaper downloads, search engines, or affiliate links, as described in Section 3.6
2. **Actions you initiate** — for example, sharing the app via Android’s share sheet
3. **Android backup or device transfer** — if enabled on your device, some app data may be included in Google backup or device-to-device transfer under your Google/account settings (see Section 8)

We do not share data with advertisers or analytics providers.

---

## 6. Legal bases (EEA / UK users)

Where applicable privacy law requires a legal basis:

- **Performance of a service you request** — providing launcher functionality and optional features you enable
- **Your consent** — location, notifications, usage access, accessibility, and device administrator permissions, which you grant or revoke in Android Settings
- **Legitimate interests** — maintaining a secure, functional local app without server-side profiling

You may withdraw consent for permissions at any time in Android Settings.

---

## 7. Data retention and deletion

| Data | Retention |
|------|-----------|
| App preferences and launcher data | Until you change or remove it, or uninstall the app |
| Prayer completion logs | Up to **2 years**, then automatically deleted |
| App cooldown daily usage | Current calendar day only |
| Weather and prayer cache | Until refreshed or cleared |
| Custom audio URI references | Until you change or remove them |

**How to delete your data:**

- Remove individual content inside the app (reminders, notes, prayer logs where supported)
- **Settings → Apps → Sukun → Storage → Clear data** (Android)
- **Uninstall Sukun** — removes app-private data subject to Android behavior on your device

Because we do not operate servers, we cannot delete data from a central account. Deletion is controlled by you on your device.

---

## 8. Backup and device transfer

Sukun has `allowBackup="true"`. Depending on your device and Google account settings, Android may include some app data in:

- **Google cloud backup**
- **Device-to-device transfer**

That process is managed by Android and Google, not by Sukun servers. Review **Settings → System → Backup** (or your device manufacturer’s equivalent) to control backup.

---

## 9. Security

Sukun processes sensitive launcher, wellbeing, and optional religious-practice data **on your device**. We do not maintain a central database of user activity.

Network requests for optional features use HTTPS where supported by the third-party service. No security method is perfect; protect your device with a screen lock and keep Android updated.

---

## 10. Your choices and controls

You are in control of Sukun’s data access:

| Feature | How to control it |
|---------|-------------------|
| Location | Android permission settings; disable weather/prayer in Sukun |
| Notifications | Android notification settings; disable reminders/prayer/chime in Sukun |
| Usage access | Android **Usage access** settings; disable screen time/cooldown in Sukun |
| Accessibility | Android **Accessibility** settings |
| Weather / wallpapers / search | Disable in Sukun or do not use those actions |
| All local data | Clear app storage or uninstall |

---

## 11. Children’s privacy

Sukun is a general-audience app and is **not directed to children under 13** (or the applicable age in your country). We do not knowingly collect personal information from children. If you believe a child has used a third-party service opened from Sukun, contact us using the details in Section 14.

---

## 12. International users

Sukun is available in multiple countries. Data is processed primarily on your device. If you are in the European Economic Area, United Kingdom, Switzerland, or other regions with privacy laws, you may have rights to access, correct, delete, or restrict processing of personal data.

Because Sukun stores data locally and we do not operate a user database, the main way to exercise deletion rights is to clear app data or uninstall the app. For questions, contact us in Section 14.

---

## 13. Changes to this policy

We may update this Privacy Policy from time to time. When we do, we will change the **Last updated** date at the top of this document. If changes are material, we may also note them in the app’s release notes or repository changelog.

Your continued use of Sukun after an update means you accept the revised policy. If you do not agree, please stop using the app and uninstall it.

---

## 14. Contact us

If you have questions, concerns, or privacy-related requests about Sukun, contact us at:

- **GitHub Issues:** [https://github.com/sunatte-saad/sukun/issues](https://github.com/sunatte-saad/sukun/issues)

We aim to respond to reasonable privacy inquiries in a timely manner.

---

## 15. Open source

Sukun is open source. You can review the source code to verify how the app handles data:

**[https://github.com/sunatte-saad/sukun](https://github.com/sunatte-saad/sukun)**

---

## 16. Google Play Data safety — quick reference

Use this section when completing the **Data safety** form in Google Play Console. Adjust answers if your enabled features differ.

| Play Console topic | Sukun’s answer |
|--------------------|----------------|
| **Does your app collect or share user data?** | The app processes data on-device. Optional features may send location or search queries to third parties listed in Section 3.6. We do not collect data on our own servers. |
| **Is data encrypted in transit?** | Yes, for HTTPS requests to third-party APIs |
| **Can users request data deletion?** | Yes — clear app data or uninstall (Section 7) |
| **Location** | Optional; approximate and precise location for weather/prayer when enabled |
| **App activity** | Optional usage stats for screen time/cooldown; installed apps for launcher (local only) |
| **Personal info** | Not collected by the developer |
| **Financial info** | Not collected |
| **Photos / videos** | Not collected |
| **Audio files** | Optional user-selected custom azan/chime URI stored locally only |
| **Files and docs** | Not collected |
| **Calendar, contacts, messages** | Not collected |
| **App info / crash logs** | No third-party crash or analytics SDK |
| **Device or other IDs** | Not collected by the developer for tracking |
| **Data shared with third parties** | Optional weather (Open-Meteo), wallpapers (Unsplash), user-initiated search (DuckDuckGo/Google), optional affiliate link (Amazon) |
| **Purpose** | App functionality only; not advertising or analytics |

If anything in this table conflicts with your actual Play Console declarations, the **Data safety form and this Privacy Policy must match**.
