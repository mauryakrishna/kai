# Kai — plan & progress

A personal Android app to break the doom-scroll reflex: raise the barrier to
picking up the phone, and push me toward something away from it.

Single user. Single device. Sideloaded. No backend, no accounts, no network.

---

## Decisions made

| Decision | Choice |
|---|---|
| Enforcement | **Friction only.** Every gate is escapable, just expensive. Nothing is ever truly locked. |
| Scope | **Everything except an allowlist.** The whole phone is gated; a few tools are let through. |
| Device | Motorola Edge 50 Neo, Android 15 (API 35) |
| Distribution | `./gradlew installDebug` over USB. No Play Store. |

---

## Tech

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin | Standard for Android |
| UI | Jetpack Compose (from Stage 2) | Stage 0/1 use plain views to keep the first builds boring |
| App detection | `AccessibilityService` | Sees foreground app changes instantly; the system keeps it alive, so **no foreground service and no permanent notification needed** |
| Gate screen | Full-screen `Activity` + `SYSTEM_ALERT_WINDOW` | Accessibility + overlay permission are both background-activity-launch exemptions on Android 15 |
| Storage | Room (event log) + DataStore (settings) | Local only |
| Scheduling | WorkManager | Daily reckoning, weekly ratchet |
| Build | Gradle 8.11.1, AGP 8.7.3, Kotlin 2.0.21 | minSdk 31, target/compileSdk 35 |

---

## Environment

Everything lives in `~/android-dev/`. No sudo, nothing in `/Library`, no Homebrew.
Remove it all with `rm -rf ~/android-dev`.

| Tool | Version | Path |
|---|---|---|
| JDK | Zulu 21.0.12.1 LTS | `~/android-dev/zulu21` |
| Android SDK | platform 35, build-tools 35 | `~/android-dev/sdk` |
| adb | 37.0.1 | `~/android-dev/sdk/platform-tools` |
| Gradle | 8.11.1 (checksum verified) | `~/android-dev/gradle-8.11.1` |

Two machine-local files make this work from any terminal (neither is in the project):
- `~/.gradle/gradle.properties` → `org.gradle.java.home`
- `~/Library/Java/JavaVirtualMachines/zulu-21.jdk` → symlink, so `/usr/bin/java` resolves

Build: `./gradlew assembleDebug`   Install: `./gradlew installDebug`

### Network gotcha
Homebrew is unusable on this laptop: `raw.githubusercontent.com` takes ~5s/connect and
`formulae.brew.sh` times out. Gradle's CDN redirects to GitHub assets, which kept truncating
the download — it came from an Aliyun mirror instead, verified against Gradle's official
SHA-256. `dl.google.com` and Maven Central are fast, so normal builds are unaffected.

---

## Stages

- [x] **Stage 0 — Setup** — done
  - [x] Toolchain installed and verified
  - [x] Project skeleton builds (2.2MB APK in 31s)
  - [x] Installed and verified on the phone

- [x] **Stage 1 — Measure.** No blocking at all; just find out the real number. — done
  - [x] 1.1 AccessibilityService detecting foreground app (deduped, systemui filtered)
  - [x] 1.2 Room DB logging every app switch (`kai.db`, table `app_events`)
  - [x] 1.3 Screen showing today's opens and per-app time

  - [x] 1.4 Backfill from `UsageStatsManager` — no week of waiting needed

  Duration is derived as the gap to the next event, so nothing half-open survives a crash.

### Baseline

Measured 2026-09-13 from `UsageStatsManager`. The actual figures live in
`BASELINE.md`, which is gitignored — this repo is public and the baseline is
a detailed picture of one person's phone habits.

What matters for the design: the heaviest use is a **web browser**, not a
social app, so gating named social apps would have missed the problem
entirely. A second app shows a different shape — very frequent opens, little
time — which needs different friction from a long scroll.

- [x] **Stage 2 — Gate + escape hatch** — done
  - [x] 2.1 Allowlist picker (launcher locked on) + arm switch, off by default
  - [x] 2.2 Full-screen gate, 10s countdown, escape hatch, 5-minute grants
  - [x] Resume control, so a pause can be lifted early

  Measured on device: **218ms** from app launch to gate on screen — fast enough
  that the app behind it is never visible.

  The escape hatch is a faint dot held for 5 seconds, not an invisible corner.
  An escape you cannot find when you genuinely need the phone is not an escape.

- [ ] **Stage 3 — Truths**
  - [ ] My own statements, editable
  - [ ] Gate shows least-recently-seen one
  - [ ] Type-to-continue past a threshold

- [ ] **Stage 4 — Redirect**
  - [ ] Off-phone action list
  - [ ] "Doing it" → lock timer

- [ ] **Stage 5 — Ledger**
  - [ ] 9pm daily reckoning notification
  - [ ] Weekly review screen

- [ ] **Stage 6 — Ratchet**
  - [ ] Budgets derived from Stage 1 baseline
  - [ ] Escalating friction
  - [ ] Night rule
  - [ ] Relapse detection

**Pause after Stage 2** and live with it for about a week. Stage 6 tunes against that
real baseline and can't be built honestly before the data exists.

---

## Safety rules

Gating the whole phone means a bug can make the device unusable. Non-negotiable:
1. The launcher is always allowlisted, or you can never get home.
2. A hidden escape hatch ships **before** the gate is armed.
3. Settings → Accessibility → off always works. That's the OS's hatch and can't be removed —
   which is fine, because friction-only is the point.

---

## Notes

- No git in this project for now, by choice.
