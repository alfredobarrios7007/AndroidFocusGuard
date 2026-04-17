# FocusGuard 🎯

A minimalist Android productivity app that locks you out of distracting apps for a defined period — so you can do deep work without fighting yourself.

---

## Purpose

Most productivity failures aren't about motivation — they're about friction. FocusGuard removes the option to mindlessly open Instagram, YouTube, or any other app while you're supposed to be working. Instead of relying on willpower, it forces a moment of intentional reflection before letting you break your focus session.

When you try to open a blocked app, the screen asks:

> *"Do you really want to break your goal? Is what you're about to do really worth it?"*

That single pause is often enough to make you reconsider.

---

## Features

- Set a focus session from **1 to 240 minutes** (up to 4 hours)
- **Blocks all apps** during the session except incoming phone calls
- Intercepts app launches in real time via Android's Accessibility Service
- If you try to break focus, you're asked to reflect before proceeding
- On reflection, you can choose to:
  - **Restart the clock** — reset the timer to the full original duration
  - **Keep the current time** — continue where you left off
  - **Unlock** — temporarily disable blocking (you made a conscious choice)
- Persistent **foreground notification** shows remaining time at all times
- **Multi-language support** — English, Spanish, French, and Portuguese; follows the device locale automatically
- Works across all major Android OEMs (Samsung, Xiaomi, OnePlus, Motorola, etc.)

---

## Architecture

The app is written entirely in **Kotlin** and targets Android API 26+. It follows a simple, pragmatic architecture with no third-party frameworks — just Android SDK components wired together through shared state.

```
com.focusguard.app
├── FocusStateManager.kt              — Shared state (SharedPreferences singleton)
├── AppBlockerAccessibilityService.kt — Detects app launches, triggers block screen
├── FocusTimerService.kt              — Foreground countdown timer service
├── MainActivity.kt                   — Main UI: pick duration, start/stop session
└── InterruptionActivity.kt           — Block screen: reflect, unlock, or restart
```

### Component responsibilities

#### `FocusStateManager`
The single source of truth for the focus session. It wraps a `SharedPreferences` file and exposes simple read/write methods for the session state: whether focus is active, the end timestamp, the original duration, and whether the user has temporarily unlocked the phone. Because it's a singleton backed by persistent storage, all components — the service, the accessibility service, and both activities — share the same state across process boundaries.

#### `AppBlockerAccessibilityService`
Listens for `TYPE_WINDOW_STATE_CHANGED` accessibility events, which fire whenever a new app window comes to the foreground. On each event it checks the package name against an allowlist (phone/dialer apps and the FocusGuard app itself) and against a list of system UI prefixes (launchers, System UI). If the package is not on either list and a focus session is active and not unlocked, it immediately launches `InterruptionActivity` as a new task, covering the blocked app.

A 2-second debounce prevents the interruption screen from firing repeatedly for the same package in quick succession.

#### `FocusTimerService`
A `Service` running in the foreground with a sticky notification. It owns the `CountDownTimer` and is the authoritative clock for the session. It handles three intents:

- `ACTION_START` — starts a new session with a given duration in minutes
- `ACTION_RESTART` — cancels the current countdown and starts a fresh one with the original duration (used by `InterruptionActivity` when the user chooses "Restart the clock")
- `ACTION_STOP` — cancels the timer, clears state, and stops the service

Every second it broadcasts a `BROADCAST_TIMER_TICK` intent so `MainActivity` can update its live display without polling.

#### `MainActivity`
The entry point. Provides a `NumberPicker` (1–240 minutes), a start/stop button, and a live timer display fed by the broadcast from `FocusTimerService`. It also detects whether the Accessibility Service is enabled and shows a persistent warning banner if not, guiding the user to `Settings → Accessibility` to enable it.

#### `InterruptionActivity`
A full-screen activity with two phases:

1. **Reflection phase** — displays the motivational question with a prominent "Stay Focused" button (No) and a subdued "Let me in" button (Yes). The back button is disabled via `OnBackPressedCallback` so the user cannot bypass this screen.
2. **Decision phase** — shown if the user taps "No". Offers "Restart the clock" (sends `ACTION_RESTART` to the service) or "Keep current time" (simply closes the screen).

If the user taps "Yes", `FocusStateManager.setUnlocked(true)` is called and the screen closes, allowing `AppBlockerAccessibilityService` to stop intercepting until the session ends or the app is reopened.

### Data flow

```
User taps Start
      │
      ▼
MainActivity ──► startForegroundService(ACTION_START) ──► FocusTimerService
                                                               │
                                          FocusStateManager ◄─┤ writes session state
                                                               │
                                          BROADCAST_TIMER_TICK ──► MainActivity (UI update)

User opens another app
      │
      ▼
AppBlockerAccessibilityService (TYPE_WINDOW_STATE_CHANGED)
      │ reads FocusStateManager.isActive() && !isUnlocked()
      ▼
InterruptionActivity
      │
      ├── "No" → Decision screen
      │          ├── "Restart" ──► startService(ACTION_RESTART) ──► FocusTimerService
      │          └── "Stay"   ──► finish() (timer continues unchanged)
      │
      └── "Yes" ──► FocusStateManager.setUnlocked(true) ──► finish()
```

---

## Localization

All user-visible strings live in `res/values/strings.xml`. The app ships with translations for:

| Locale | Folder |
|--------|--------|
| English (default) | `res/values/` |
| Spanish | `res/values-es/` |
| French | `res/values-fr/` |
| Portuguese | `res/values-pt/` |

Android selects the right strings automatically based on the device language — no code changes required. To add another language, create a new `res/values-<locale>/strings.xml` file and translate all keys from the default `strings.xml`.

---

## Setup

### Requirements

- Android Studio Hedgehog (2023.1.1) or later
- Android device running API 26 (Android 8.0) or higher
- A physical device is strongly recommended — app blocking does not work meaningfully on an emulator

### Build and install

1. Open Android Studio and select **File → Open**, then choose the `FocusGuard` folder.
2. Wait for the Gradle sync to complete.
3. Connect your Android device via USB with developer mode and USB debugging enabled.
4. Press **Run** (▶) or use `Shift+F10`.

### Enable the Accessibility Service (required)

The app cannot block other apps without the Accessibility Service being active. On first launch:

1. Tap **"Enable Accessibility Service"** in the app.
2. In the system settings screen that opens, find **"Focus Guard — App Blocker"** and tap it.
3. Toggle it on and confirm the permission dialog.
4. Return to the app — the warning banner will disappear.

> **Note:** Some OEMs (Xiaomi, Huawei, OnePlus) may kill accessibility services when the app is in the background. If blocking stops working, check the battery optimization settings and add FocusGuard to the "unrestricted" or "no restrictions" list.

---

## Permissions

| Permission | Why it's needed |
|---|---|
| `FOREGROUND_SERVICE` | Required to run the timer service while the app is in the background |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Required by Android 14+ for foreground services that don't fit a standard category |
| `POST_NOTIFICATIONS` | Displays the persistent countdown notification (Android 13+) |
| `BIND_ACCESSIBILITY_SERVICE` | Grants the Accessibility Service the right to observe window changes |

The app does **not** request internet access, read your contacts, access your camera, or collect any data. Everything stays on-device.

---

## Known limitations

- **Accessibility Service must be re-enabled after some device restarts** on certain OEMs.
- The block is not root-level — a determined user can always go to Settings and disable the Accessibility Service. This app works by creating friction and prompting reflection, not by enforcing hard locks.
- Some launchers may not trigger `TYPE_WINDOW_STATE_CHANGED` consistently. If the home screen bypasses the blocker, add your launcher's package to `SYSTEM_ALLOWED_PREFIXES` in `AppBlockerAccessibilityService`.
