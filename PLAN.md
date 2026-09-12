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
  - [x] 2.2 Full-screen gate, escape hatch, 5-minute grants
  - [x] 2.3 Wait escalates with how often the app was opened today
  - [x] Resume control, so a pause can be lifted early

  Measured on device: **218ms** from app launch to gate on screen — fast enough
  that the app behind it is never visible.

  The escape hatch is a faint dot held for 5 seconds, not an invisible corner.
  An escape you cannot find when you genuinely need the phone is not an escape.

  It stands Kai down for **2 minutes**, not an hour. An hour was the first
  guess and it was far too generous: a ten-second errand — sending an OTP,
  answering one message — should not cost a whole afternoon of protection,
  and a punitive escape is what trains you to uninstall the app instead.
  Every use is logged, so the length gets set from evidence rather than guesswork.

  **The wait grows with repetition:** 8s base, +7s per repeat that day, capped
  at 90s. The 1st open costs 8 seconds, the 6th costs 43, the 13th onward costs
  90. Opening an app at all is not the problem; reaching for it for the tenth
  time is. The gate also names the count — "6th time today" — because the
  number itself is the argument.

- [x] **Stage 3 — Truths** — done
  - [x] 3.1 Editor for your own statements, seeded with replaceable starters
  - [x] 3.2 Gate shows the least-recently-seen one, so none wears out
  - [x] 3.3 Past 5 opens in a day, the statement must be typed out to continue

  Paste and autofill are refused on that field. Typing it is the mechanism;
  pasting would satisfy the check while skipping the point entirely.

  Keyboards, launchers and anything without a launcher activity are never
  gated. An IME comes to the foreground like any app, and gating one makes it
  impossible to type — including typing the statement the gate is asking for.

- [x] **Stage 4 — Redirect** — done
  - [x] 4.1 Editor for off-phone actions, each with the minutes it shuts the phone
  - [x] 4.2 "Not now" offers one instead of dumping you at the home screen
  - [x] 4.3 Committing locks the gated apps for that action's minutes

  Turning an app down used to lead to the home screen, which is where the
  scrolling restarts. Now it leads somewhere.

  A lock outranks a grant: having said you were doing something else, a
  five-minute pass bought earlier does not let you back in. The escape hatch
  still clears it — this is friction, not a cage.

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
