# Open Screen Time — Specification

## Overview

Open Screen Time is an open source screen time and parental controls app for custom Android ROMs. It tracks how long each app is used, lets the user set app time limits and a downtime schedule, and locks those settings, plus selected device restrictions, behind a separate screen time PIN that is different from the phone's unlock code.

It is designed to ship as a **privileged system app** built into a ROM, with GrapheneOS as the first target. A secondary goal is to also run as a **device-owner app** on phones whose ROM does not include it.

## Goals

- Give custom ROM users a built-in alternative to iOS Screen Time and Google's Digital Wellbeing.
- Serve two audiences equally:
  - **Parents** setting firm limits on a child's phone.
  - **Adults** who want friction against apps they find tempting.
- Be hard to bypass from within the running OS when restrictions are enabled.
- Keep all data on the device. The app has no network access.
- Be easy for ROM maintainers to include, with minimal changes to their own code.

## Non-goals for v1

- Managing a phone remotely from another phone (planned for later).
- Website or content filtering.
- Blocking factory reset (see **Forgotten PIN and factory reset**).
- Supporting Android versions older than 14.

## Users and setup

1. On first launch, the user sees an introduction and chooses whether to turn on screen time tracking.
2. Tracking and the dashboard work without a PIN.
3. Setting a screen time PIN is required before limits, downtime, or restrictions can be locked. The setup screen should explain that this PIN is different from the phone's unlock code.
4. Whoever knows the PIN controls the settings. For a child's phone, that's the parent; for an adult, it can be a friend or partner, or the adult themselves.

## Features

### 1. Usage dashboard

- Total screen time for today, with a daily bar chart for the current week. The week is the calendar week, starting on the first day used by the phone's region (for example Sunday in the US, Monday in most of Europe).
- Tapping a day in the chart shows that day's total and app list. Coming back to the app returns to today.
- Time on the home screen (launcher) is not counted, matching Digital Wellbeing and iOS Screen Time. Time in the screen time app itself is counted.
- An app counts as used while one of its screens is in the foreground. Turning the screen off or locking the phone ends use. Two apps in split screen are both counted. Use crossing midnight is split between the two days.
- A list of apps sorted by usage, showing time used today.
- Tapping an app shows its daily usage over the week, its daily average for the week so far (including today), and its current limit, if any.
- Optional extras, deferred until after phase 1: number of unlocks, notifications per app (needs an extra special permission).
- **History length is chosen by the user** in settings: 1 week, 4 weeks, 3 months (90 days), or 1 year. The default is 4 weeks. The chosen number of days includes today.

### 2. Per-app time limits

- Set a daily time limit for any app, in 15-minute steps, up to 23h 45m.
- Optionally use different limits on weekdays and weekends.
- Show a notification 5 minutes before a limit is reached. *(Phase 2: needs background usage monitoring.)*
- When the limit is reached, the app is blocked (see **Limit behavior**).
- Limits reset at midnight local time.

### 3. Downtime schedule

- Set a recurring time window, such as 9:00 PM–7:00 AM, during which all apps except **always allowed** apps are blocked.
- Optionally use different schedules per day of the week.
- Show a notification 5 minutes before downtime starts. *(Phase 2.)*

### 4. Always allowed apps

- A list of apps never blocked by limits or downtime.
- Defaults: Phone (dialer), emergency calling, and Settings.
- Editable, but changes require the PIN once a PIN is set.
- Emergency calling must always work and cannot be removed from the list.

### 5. Limit behavior (configurable)

When a limit or downtime blocks an app, the person who set the limit chooses one of these modes:

| Mode | Behavior |
|---|---|
| **Strict** | App stays blocked. Only entering the PIN unlocks more time. |
| **Grace** | User may tap "One more minute" once per app per day; after that, the PIN is required. |
| **Reminder** | A warning screen appears, but the user can choose to ignore it and continue. Intended for adults who want a nudge rather than a lock. |

When the PIN is entered on the block screen, the user can choose to unlock for 15 minutes, 1 hour, or the rest of the day.

### 6. Restrictions

When enabled, each of these requires the PIN to change or use:

- **App installs and uninstalls**, including installs from app stores and APK files.
- **Developer options and ADB**, including USB debugging and wireless debugging.
- **Adding or switching users**, including guest mode and new profiles.
- **Changing the date, time, or time zone.**

Each restriction can be turned on or off individually.

### 7. Self-protection

These are always enforced once a PIN is set, regardless of the options above:

- The screen time app cannot be disabled, force-stopped, or uninstalled for the user, and its data cannot be cleared.
- Its permissions cannot be revoked.
- Its settings cannot be changed without the PIN.
- Turning off screen time entirely requires the PIN.

### 8. Screen time PIN

- A 4–6 digit PIN.
- Stored securely as a salted hash, using Android's hardware-backed keystore where available. It is never stored in plain text.
- Wrong attempts are throttled with increasing delays to prevent guessing. After wrong attempts in a row: 1–4, no wait; 5, 1 minute; 6, 5 minutes; 7, 15 minutes; 8, 1 hour; 9, 4 hours; 10 or more, 24 hours each. A correct PIN resets the count. During a wait, no PIN is checked, not even the correct one.
- One wrong-attempt count is shared by every place that asks for the PIN. It is saved before the result is shown, so closing the app can't undo it.
- Waits are timed by the phone's time since start, so changing the date, time or time zone doesn't shorten them. If the phone restarts during a wait, the wait starts over at full length.
- Storage: PBKDF2-HMAC-SHA256 with a random 16-byte salt and 100,000 rounds, then sealed with an HMAC-SHA256 key kept in the Android hardware keystore (StrongBox when available). The key can't be exported, so saved data can't be used to guess the PIN on another device.
- Changing the PIN requires the current PIN.

### 9. Forgotten PIN and factory reset

- **Factory reset is intentionally never blocked.** It is the guaranteed way to recover a phone if the PIN is forgotten. Blocking it could leave the phone permanently unusable.
- A factory reset erases the screen time settings along with everything else on the phone.
- The PIN setup screen must clearly warn: "If you forget this PIN, the only way to remove it is to factory reset the phone, which erases all data."
- An optional recovery code may be added later (see Open questions). Factory reset remains available either way.

## Integration modes

### System app mode (primary)

- Ships as a privileged system app in the ROM's system image.
- Gets its permissions through a privileged-permission allowlist file supplied with the app.
- ROMs integrate it by adding the app to their build and including the allowlist file. Optionally they add a **Screen time and parental controls** entry to the main Settings list.
- The ROM marks the app as non-disableable.

### Device-owner mode (secondary goal)

- The same app can be set as the device owner with an ADB command on a freshly reset phone.
- It uses Android's device policy APIs for suspending apps and applying restrictions.
- Some protections may be weaker than in system app mode. These should be documented.
- **Build system app mode first.** Design the code so the enforcement layer can be swapped between the two modes.

## Settings integration

- A main Settings entry titled **Screen time and parental controls** whose subtitle shows today's total and the number of active limits.
- On each app's info page, show that app's usage today and an **App limit** option, if the ROM chooses to integrate this.
- Restricted settings, such as developer options, show a lock icon and ask for the PIN when tapped.
- Searchable in Settings for terms like "screen time," "app limits," and "parental controls."

## Privacy

- All data stays on the device. The app has **no network permission**.
- No analytics, crash reporting, or third-party services.
- No Google Play Services dependency.
- Usage data is stored in the app's private storage and deleted according to the user's history setting.
- Android keeps detailed usage events for only a few days, so the app saves daily totals per app in its own database. It recalculates the last 7 days when it opens and every 3 hours in the background, and never lowers a saved total. The same background job deletes history older than the setting.

## Technical requirements

- Language: Kotlin.
- UI: Jetpack Compose, following Material 3 and matching the look of AOSP Settings.
- Minimum Android version: **14** (API 34). Target the newest Android version GrapheneOS ships, currently **Android 17 (API 37)**.
- App ID (package name): `org.openscreentime`. This must not change once builds are in use.
- License: Apache 2.0.
- Use existing Android system APIs, such as usage stats, package suspension, and user restrictions, rather than modifying the Android framework.
- Any version-specific behavior is handled in code, not with separate branches.
- Must build from the command line with Gradle, and through GitHub Actions on every push.
- Include automated tests for limit, downtime, and PIN logic, plus screenshot tests for the main screens so the UI can be reviewed without a phone.
- Usage history is stored with **Room** (AndroidX), which uses Google's **KSP** build tool. Background work uses **WorkManager** (AndroidX). WorkManager's network-state permission is removed from the app.
- Screenshot tests use **Roborazzi** with **Robolectric**, both test-only libraries that are never included in the app. Robolectric runs tests on Android 16 (API 36) until it fully supports Android 17.
- GitHub Actions builds a debug APK for every push and pull request. Builds can use a fixed debug signing key stored as a GitHub secret, so testers can install new builds over old ones. Never commit the key.

## Development phases

1. **App foundation (no special privileges).** Usage dashboard, limits and downtime screens, PIN setup, and block screens. Built and tested as a regular app. GitHub Actions produces an APK for each change. A regular app can't block other apps, so block screens are only shown through a preview button in this phase. The rules that decide what to block are built and tested here, behind the enforcement interface.
2. **Enforcement.** App suspension, restrictions, and self-protection, tested in device-owner mode on a test phone. Also the 5-minute warning notifications before a limit or downtime, which need background usage monitoring.
3. **System app integration.** Privileged-permission files, build integration for GrapheneOS, and Settings integration. Tested in a full GrapheneOS build.
4. **Polish and proposal.** Documentation, a test report, and a proposal to the GrapheneOS team.

## Test checklist

Testers should confirm each item on a real device or a GrapheneOS emulator build:

- [ ] Usage times in the dashboard roughly match actual use.
- [ ] A 15-minute limit blocks the app at 15 minutes, in each limit mode.
- [ ] Limits reset at midnight.
- [ ] Downtime blocks all apps except always allowed apps, and ends on schedule.
- [ ] Emergency calling works during downtime and when every other app is blocked.
- [ ] The PIN unlocks more time, and wrong PINs are throttled.
- [ ] With restrictions on, installing or uninstalling an app asks for the PIN.
- [ ] Developer options and USB debugging cannot be enabled without the PIN.
- [ ] Adding a user or guest cannot be done without the PIN.
- [ ] Changing the date, time, or time zone cannot be done without the PIN.
- [ ] The screen time app cannot be disabled, force-stopped, uninstalled, or have its data cleared.
- [ ] Rebooting the phone keeps all limits, downtime, and restrictions in effect.
- [ ] Usage history older than the chosen setting is deleted.

## Open questions

- **Recovery code.** Should there also be an optional one-time recovery code shown at setup, so a forgotten PIN doesn't always require a factory reset? Factory reset stays available regardless.
- **Safe mode.** In system app mode, safe mode does not stop the screen time app, since it only disables user-installed apps. In device-owner mode it might, so safe mode may need to be blocked there. Confirm during phase 2 testing.
- **Remote management.** How a parent might manage a child's phone from their own phone in a future version, without breaking the no-network rule for the app itself.
- **GrapheneOS preferences.** How the GrapheneOS team would want this integrated, including whether they prefer a system app, a different design for the PIN, or other changes. Ask before phase 3.
