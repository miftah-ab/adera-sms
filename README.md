<div align="center">

<img src="https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white"/>
<img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white"/>
<img src="https://img.shields.io/github/actions/workflow/status/miftah-ab/adera-sms/build.yml?style=for-the-badge&label=CI%20Build&logo=github-actions&logoColor=white"/>
<img src="https://img.shields.io/badge/Version-1.0.0-F9A825?style=for-the-badge"/>
<img src="https://img.shields.io/badge/License-MIT-2E7D32?style=for-the-badge"/>
<img src="https://img.shields.io/badge/No%20Ads-No%20Tracking-1B5E20?style=for-the-badge"/>

<br/><br/>

# 📵 Adera SMS

### *"No more 'sorry I missed your call' — send it before they ask."*
**"የጠፋ ጥሪ፣ ያመለጠ እድል"** · *A missed call, a missed opportunity*

A lightweight, offline-first Android app that automatically sends a customizable SMS reply  
when you miss a call — built for Ethiopian users, tested on budget devices.

[**⬇ Download APK**](https://github.com/miftah-ab/adera-sms/releases/latest) · [**📋 Report Bug**](https://github.com/miftah-ab/adera-sms/issues) · [**💡 Request Feature**](https://github.com/miftah-ab/adera-sms/issues)

</div>

---

## 📖 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Screenshots](#-screenshots)
- [Architecture](#-architecture)
- [Getting Started](#-getting-started)
  - [Download & Install](#download--install-sideload)
  - [Build from Source](#build-from-source)
  - [GitHub Actions CI/CD](#github-actions-cicd)
- [Configuration](#-configuration)
- [Privacy & Data Policy](#-privacy--data-policy)
- [Roadmap](#-roadmap)
- [Contributing](#-contributing)
- [License](#-license)

---

## 🌍 Overview

**Adera SMS** is a missed-call auto-reply application designed from the ground up for real-world conditions in Ethiopia and the broader East African market.

Unlike global competitors (Smarter, Auto Message), Adera SMS is built for:

| Reality | Adera SMS approach |
|---|---|
| Dual-SIM devices (Tecno, Infinix, Itel) | Per-SIM listener, 3-level SIM fallback chain |
| Aggressive OEM battery killers | Foreground service + battery whitelist guided setup |
| Offline-first / no reliable internet | 100% local — no server, no cloud sync |
| Sideload distribution (no Play Store) | GitHub Releases + in-app update checker |
| Amharic + English UI | Full Ethiopic font support, 5 presets in each language |
| Low-end hardware | Dark mode, system font, minimal memory footprint |

---

## ✨ Features

### Core (v1.0)

- **🔁 Automatic SMS Reply** — Sends a customizable message the moment a call is missed
- **📱 Dual-SIM Aware** — Detects which SIM received the missed call and sends from it
- **⏰ Quiet Hours** — Configurable do-not-reply window (supports overnight ranges, e.g. 23:00–06:00)
- **🔄 10-Minute Cooldown** — Suppresses duplicate replies to the same caller within 10 minutes
- **📋 Template Library** — 5 English + 5 Amharic presets; unlimited custom templates
- **📊 Activity Log** — Full masked history with status chips (Sent / Failed / Quiet Hours / Cooldown)
- **🔒 Privacy-First** — Phone numbers stored only as SHA-256 hashes; full number never written to disk
- **🔋 Battery Survival** — OEM-specific battery guide (Tecno, Infinix, Samsung, Xiaomi, Huawei, Oppo)
- **⬆ In-App Updates** — Checks GitHub Pages endpoint for new versions; forced update if below minimum
- **🌐 Works Offline** — Core auto-reply loop has zero network dependencies

### Planned (v1.1+)

- Per-contact custom rules
- Activity log export (CSV)
- SMS read confirmation (delivery report)
- Widget for home-screen quick toggle
- WhatsApp Business fallback integration

---

## 📸 Screenshots

> *(Install the APK on a physical device and screenshots will be added here)*

| Onboarding | Home | Templates | Activity Log | Settings |
|---|---|---|---|---|
| 3-slide intro + permission primer | Master toggle + active template | English & Amharic presets | Masked numbers + status chips | OEM battery guide + update check |

---

## 🏗 Architecture

```
adera-sms/
├── .github/workflows/build.yml       # CI/CD — debug on push, release APK on tag
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/adera/sms/
│       │   ├── AderaSmsApplication.kt     # App init: channels + DB seed
│       │   ├── MainActivity.kt            # Single activity, NavHost
│       │   ├── data/
│       │   │   ├── entity/                # Room entities (MessageTemplate, CallLogEntry, AppSettings)
│       │   │   ├── dao/                   # TemplateDao, CallLogDao, SettingsDao
│       │   │   └── AppDatabase.kt         # Room database singleton
│       │   ├── service/
│       │   │   ├── CallMonitorService.kt  # Foreground service, telephony listener
│       │   │   └── SmsSenderWorker.kt     # WorkManager worker, retry, SIM selection
│       │   ├── receiver/
│       │   │   └── BootReceiver.kt        # Restart service after reboot
│       │   ├── update/
│       │   │   └── UpdateChecker.kt       # HTTP fetch version.json, forced update logic
│       │   ├── analytics/
│       │   │   └── AnalyticsManager.kt    # Opt-in stub (Firebase-ready)
│       │   └── ui/
│       │       ├── theme/                 # Color, Type, Theme (Material 3 dark)
│       │       ├── navigation/            # Screen sealed class, NavGraph
│       │       ├── onboarding/            # 3-slide intro + permission explainer
│       │       ├── home/                  # HomeScreen + HomeViewModel
│       │       ├── templates/             # TemplateEditorScreen + TemplateViewModel
│       │       ├── activitylog/           # ActivityLogScreen + ActivityLogViewModel
│       │       ├── settings/              # SettingsScreen, QuietHoursScreen, SettingsViewModel
│       │       └── update/                # ForceUpdateScreen (blocking)
│       └── res/
│           ├── values/                    # strings.xml, colors.xml, themes.xml
│           └── values-am/                 # Amharic strings (verified by native speaker needed)
└── update-endpoint/version.json          # Host on GitHub Pages for update checks
```

### Technology Choices

| Layer | Technology | Why |
|---|---|---|
| Language | Kotlin | Official Android language |
| UI | Jetpack Compose + Material 3 | Modern, dark-mode-first, declarative |
| Database | Room (SQLite) | Fully offline, zero backend |
| Background Work | WorkManager | Survives process kill, retry built-in |
| Call Detection | TelephonyCallback (API 31+) + PhoneStateListener (API 26–30) | Covers all target device OS versions |
| Navigation | Compose NavHost | Single-activity, type-safe routes |
| HTTP | HttpURLConnection + JSONObject | No Retrofit needed for a single endpoint |
| CI/CD | GitHub Actions | Free, reproducible, tag-triggered releases |

### Call State Machine

```
Phone rings
     │
     ▼
 RINGING ──────────────────────── IDLE  ←── Missed call detected
     │                                           │
     ▼                                           ▼
 OFFHOOK ──────────────────────── IDLE       Check gates:
  (answered)                                  1. autoReplyEnabled?
                                              2. Not in quiet hours?
                                              3. Not in 10-min cooldown?
                                                    │
                                                    ▼
                                         Enqueue SmsSenderWorker
                                         Write PENDING log entry
                                                    │
                                              Send SMS via SIM
                                         Update log → SENT / FAILED
```

---

## 🚀 Getting Started

### Download & Install (Sideload)

1. Go to [Releases](https://github.com/miftah-ab/adera-sms/releases/latest)
2. Download `app-release.apk`
3. On your Android phone: **Settings → Security → Install unknown apps** → allow your browser or Files app
4. Open the downloaded APK and tap **Install**
5. Grant the 3 requested permissions (explained in the onboarding screen)

**Minimum Android:** 8.0 Oreo (API 26)  
**Tested on:** Tecno Spark, Infinix Hot, Samsung Galaxy A-series, Xiaomi Redmi

---

### Build from Source

#### Prerequisites

| Tool | Version |
|---|---|
| Android Studio | Hedgehog 2023.1.1+ |
| JDK | 17 (Temurin recommended) |
| Android SDK | API 34 (compile), API 26 (min) |

#### Steps

```bash
# 1. Clone the repository
git clone https://github.com/miftah-ab/adera-sms.git
cd adera-sms

# 2. Generate the Gradle wrapper JAR (required once — not committed to git)
gradle wrapper --gradle-version 8.6

# 3. Open in Android Studio, or build from CLI:
./gradlew assembleDebug

# 4. Install on a connected physical device (no emulator support for telephony)
./gradlew installDebug
```

> ⚠️ **Physical device required** — the telephony listener does not fire on emulators.  
> Test by calling your device from another phone while Adera SMS is running.

---

### GitHub Actions CI/CD

The pipeline in [`.github/workflows/build.yml`](.github/workflows/build.yml) runs automatically:

| Trigger | What happens |
|---|---|
| Push to `main` or `develop` | Debug APK built and uploaded as artifact |
| Pull request to `main` | Debug APK built (not published) |
| Tag `v*.*.*` (e.g. `v1.1.0`) | Signed release APK built + GitHub Release created |

#### Setting up signing (one-time)

```bash
# 1. Generate a keystore (keep this file SAFE — never commit it)
keytool -genkey -v -keystore keystore.jks -alias adera \
        -keyalg RSA -keysize 2048 -validity 10000

# 2. Base64-encode it
# macOS / Linux:
base64 keystore.jks | pbcopy

# Windows:
certutil -encode keystore.jks keystore.b64
# Copy the contents of keystore.b64 (excluding header/footer lines)
```

Then add these **4 GitHub Actions secrets** under  
`Repository → Settings → Secrets and variables → Actions → New repository secret`:

| Secret name | Value |
|---|---|
| `SIGNING_KEY_BASE64` | Base64-encoded keystore.jks |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | `adera` (or your alias) |
| `SIGNING_KEY_PASSWORD` | Key password |

#### Publishing a release

```bash
# Bump versionCode and versionName in app/build.gradle.kts, then:
git tag v1.1.0
git push origin v1.1.0
# → CI builds, signs, and creates the GitHub Release automatically
```

After each release, update `update-endpoint/version.json` with the new `versionCode`  
and push to GitHub Pages so existing users get the in-app update notification.

---

## ⚙️ Configuration

### Quiet Hours

Navigate to **Settings → Quiet Hours**. Set a start and end time.  
Both same-day (e.g. 08:00–20:00) and overnight (e.g. 23:00–06:00) ranges are supported.  
Set start = end = 00:00 to disable.

### Templates

Navigate to **Templates** from the Home screen.  
Select from 5 English or 5 Amharic presets, or tap **+** to write a custom message.  
Messages over 160 characters will be split into multiple SMS segments.

### Update Endpoint

Host `update-endpoint/version.json` on GitHub Pages and update `UpdateChecker.VERSION_ENDPOINT` in  
[`UpdateChecker.kt`](app/src/main/java/com/adera/sms/update/UpdateChecker.kt).

```json
{
  "latestVersionCode": 2,
  "minSupportedVersionCode": 1,
  "downloadUrl": "https://github.com/miftah-ab/adera-sms/releases/latest/download/app-release.apk",
  "releaseNotes": "Bug fixes and Tecno battery improvements",
  "disableCoreService": false
}
```

---

## 🔒 Privacy & Data Policy

Adera SMS is **100% offline** for its core function. Here is exactly what data is and is not stored:

| Data | Stored | Where | Why |
|---|---|---|---|
| Full phone number | ❌ Never | — | Privacy — only exists transiently in memory |
| Masked number (e.g. `091•••42`) | ✅ Yes | Local SQLite only | Displayed in Activity Log |
| SHA-256 hash of full number | ✅ Yes | Local SQLite only | Per-number 10-minute cooldown logic |
| SMS template text | ✅ Yes | Local SQLite only | User-configured |
| Settings (toggle, quiet hours) | ✅ Yes | Local SQLite only | App configuration |
| Analytics events | Optional | None (stub in v1) | Opt-in only; no data sent anywhere in v1 |

**No data ever leaves the device.** No backend server. No cloud sync. No third-party SDKs that phone home.

---

## 📅 Roadmap

| Version | Milestone | Status |
|---|---|---|
| **v1.0** | Core loop + onboarding + template editor + activity log + quiet hours + update checker | ✅ Complete |
| **v1.1** | Per-contact rules · Log export (CSV) · Delivery reports | 🔜 Planned |
| **v1.2** | Home screen widget · WhatsApp fallback intent | 🔜 Planned |
| **v2.0** | Firebase Crashlytics · Optional contact name display · Scheduled replies | 🔜 Planned |

---

## 🤝 Contributing

Contributions are welcome — especially:

- **Amharic translation review** — the current Amharic templates need verification by a native speaker
- **Bug reports** on Tecno / Infinix / Itel devices (OEM-specific battery and SIM issues)
- **New language presets** (Oromo, Tigrinya, Somali) for a future release

### Contribution process

```bash
# 1. Fork the repo and create a branch
git checkout -b feature/your-feature-name

# 2. Build and test on a physical device
./gradlew assembleDebug && adb install app/build/outputs/apk/debug/app-debug.apk

# 3. Open a pull request — CI will build automatically
```

Please keep PRs focused. One feature or fix per PR.  
Follow the existing code style (Kotlin official style guide, no Hilt, no Retrofit).

---

## 📄 License

```
MIT License

Copyright (c) 2026 Miftah

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

<div align="center">

**Built with ❤️ for Ethiopia**

*"No more 'sorry I missed your call' — send it before they ask."*

[⬆ Back to top](#-adera-sms)

</div>
