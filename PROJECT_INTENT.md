# Adera SMS — Project Intent & Decisions
> This document exists so no future review confuses **intentional product decisions** with bugs.
> Update this whenever a new decision is made.

---

## What This App Is

**Adera SMS** is a missed-call auto-reply app built specifically for the Ethiopian market.  
- Sideloaded APK only — no Google Play Store
- Distributed via GitHub Releases
- Core SMS loop is 100% offline — no backend, no server, no cloud sync
- Firebase is used only for: analytics, crash reporting, remote config (cap/limit values), FCM push, and In-App Messaging (Pro upsell prompts)
- Target devices: Tecno, Infinix, Itel (budget dual-SIM Android 8+)

---

## Rules That Are Fixed (Never Change Without Owner Approval)

| Rule | Detail |
|---|---|
| **No local builds** | All builds happen on GitHub Actions only. Never run `./gradlew` locally. |
| **No SMS to short-codes** | Numbers with < 7 digits are silently ignored. No log entry. No popup. |
| **No popup on send** | SMS must send automatically — no system confirmation dialog ever |
| **No SKIPPED status** | Short-code calls are not logged at all. The `CallStatus` enum stays at 6 values. |
| **Only real phone numbers** | Must have 7+ digits. This covers Ethiopian +251 E.164 and local 09/07 format. |

---

## Intentional Design Decisions (Not Bugs)

### ✅ "New Template" button shows "Coming Soon"
The custom template creation feature (`saveCustomTemplate()`, `EditTemplateSheet`) is **fully coded and ready** but intentionally gated behind a "Coming Soon" dialog.

**Why:** Planned for a future version. The backend is pre-built so enabling it later requires only removing the dialog gate — no new logic needed.

**When to enable:** Change `TemplateEditorScreen.kt` line 169 from `showComingSoonDialog = true` to `templateToEdit = null; showEditSheet = true` and delete the Coming Soon dialog block.

---

### ✅ Firebase Analytics always active — `analyticsEnabled` DB field is reserved
`AnalyticsManager` fires analytics regardless of `analyticsEnabled` in DB. The field exists for a future settings toggle. Analytics is currently mandatory.

---

### ✅ `disableCoreService` in version.json is parsed but not yet wired
`UpdateChecker` reads `disableCoreService` but `HomeViewModel` doesn't act on it yet. Wiring it is a v1.1 TODO.

---

### ✅ Only 3 English + 3 Amharic presets
The code seeds 3+3. Number may increase after native speaker Amharic review before a major public release.

---

### ✅ Amharic presets not yet verified by a native speaker
Intentionally deferred until before a major public/TikTok promotion. Do not change Amharic text without owner approval.

---

### ✅ Cooldown uses SHA-256 hash — full number also stored
Both the full number (for display) and its SHA-256 hash (for cooldown matching) are stored. This is intentional.

---

### ✅ `START_STICKY` foreground service restarts after OEM kill
Users control it only via the master toggle. Restarting after kill is a core reliability feature.

---

### ✅ Single retry after 30 seconds in `SmsSenderWorker`
Intentional anti-spam design. No infinite retries.

---

### ✅ `ForceUpdateScreen` keeps the service running while blocking the UI
Users stay covered during the update process. Intentional.

---

## Feature Roadmap

| Feature | Version | Status |
|---|---|---|
| Custom template creation (New Template) | v1.1 | Code ready — just remove Coming Soon gate |
| Analytics opt-out toggle in Settings | v1.1 | `analyticsEnabled` field already in DB |
| Wire `disableCoreService` kill-switch | v1.1 | Parsed in `UpdateChecker`, not yet acted on |
| Per-contact custom reply rules | v1.1 | Not started |
| Activity log export (CSV) | v1.1 | Not started |
| Daily limit dialog reads Remote Config cap | v1.1 | Currently hardcodes "15" |
| Home screen widget | v1.2 | Not started |
| WhatsApp Business fallback | v1.2 | Not started |
| SMS delivery reports | v1.2 | Not started |

---

## Confirmed Bugs (Real — Must Fix)

| # | File | Lines | Issue |
|---|---|---|---|
| **CRASH-1** | `Converters.kt` | 13 | `CallStatus.valueOf()` crashes on unknown enum string in DB |
| **CRASH-2** | `HomeScreen.kt` + `ActivityLogScreen.kt` | 335–348, 70–95 | `contentResolver.query()` on main thread inside `remember{}` — ANR risk |
| **BUG-1** | `CallLogDao.kt` | 37 | Cooldown SQL includes `PENDING` — suppresses valid calls after worker crash |
| **BUG-2** | `SettingsViewModel.kt` | 59–64 | `clearAllData()` doesn't stop service or re-seed templates |
| **BUG-3** | `AderaSmsApplication` | seedDatabaseIfNeeded | No check that at least one template has `isDefault=1` at startup |
| **BUG-4** | `CallMonitorService.kt` | 138 | `callStates` uses `mutableMapOf` — not thread-safe across 3 threads |
| **BUG-5** | `ActivityLogScreen.kt` | 211 | Daily limit dialog hardcodes "15", reset countdown may be inaccurate |

---

## What Is NOT a Bug

| Thing | Why it's intentional |
|---|---|
| "Coming Soon" on New Template | Planned v1.1 — code pre-built, UI gate is deliberate |
| `analyticsEnabled` not read by `AnalyticsManager` | Opt-out toggle is a future feature |
| `disableCoreService` not wired | Kill-switch wiring is v1.1 TODO |
| 3+3 presets instead of 5+5 | Actual shipped count |
| Service keeps running on ForceUpdateScreen | Intentional — users stay covered |
| Single retry in `SmsSenderWorker` | Anti-spam design |
| No log entry for short-code calls | Owner decision: silently ignored |

