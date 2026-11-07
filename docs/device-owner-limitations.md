# What device-owner mode can't protect

`SPEC.md` asks for the weaknesses of device-owner mode to be written down. This is that list, in plain language. Most of these go away, or get smaller, in **system app mode** (phase 3), where Screen time is built into the ROM.

**Short version:** device-owner mode is good enough for testing and for families comfortable with a computer-based setup. It isn't something an ordinary user can set up from the phone alone, and a determined, technical person with a computer can work around parts of it, unless debugging is restricted.

## Setup and removal

- **Setup needs a computer and a factory reset.** A phone can only be given a device owner right after a reset, with no accounts added, using `adb`. This is fine for testing, but not for most families. System app mode needs no setup.
- **Factory reset always removes everything.** This is deliberate (see the spec's "Forgotten PIN and factory reset"). Anyone who can factory reset the phone can remove Screen time, but they also lose all data on it, which a parent would notice.

## Usage tracking

- **Usage access can't be locked on.** Android doesn't let a device owner prevent someone from switching off Screen time's usage access in Settings. If it's switched off, Screen time **fails safe**: every app except always allowed ones is paused until it's switched back on. So switching it off doesn't give more time, but it does stop the phone being useful.
- **Checks happen every few seconds, not instantly.** Usage is checked every 30 seconds while the screen is on, and more often near a limit or downtime. A warning can be up to 30 seconds late, and blocking can be a few seconds late.
- **Nothing is checked while the screen is off.** Checking resumes the moment the screen turns on.
- **Apps are briefly paused after every restart.** To close the gap before the rules can be read, all apps except protected ones (phone, emergency calling, Settings, home screen, keyboards, accessibility services) are paused as the phone starts, and unpaused a few seconds after the first unlock. This happens even when no rules are set.
- **Android may stop the background service.** If the phone is very short on memory, Android can stop the tracking service. It restarts by itself, at the latest when the app is opened or the phone restarts. Apps that were already paused stay paused in the meantime.

## Blocking

- **Some apps can't be paused.** Android refuses to pause certain apps, for example the phone app, the home screen, and some core system apps. They stay usable. Screen time also never pauses itself or always allowed apps.
- **Android's own "paused" message.** When someone taps a paused app, Android shows its standard "app paused" message with Screen time's short text. It isn't the Screen time block screen. To get more time, the user opens Screen time and taps **Get more time** on the app's page.
- **Always allowed apps can open other things.** For example, links from Settings or the phone app could open a web page in a browser. A browser that's paused won't open, but the always allowed apps themselves stay usable, including Settings.
- **Without a PIN, nothing is locked.** Anyone can change limits or give themselves more time until a PIN is set. This is by design, so adults can use Screen time as a gentle nudge.

## Clock and time

- **Extra time, the grace minute and ignored reminders use the phone's clock.** If **Date, time and time zone** isn't restricted, changing the clock could stretch or shorten them, and could shift when downtime starts and ends. Turn that restriction on for a child's phone. PIN waits and restriction lifts aren't affected, because they use the phone's time-since-start clock.

## With a computer (`adb`)

- **If debugging is allowed, a computer can undo some things.** With **Developer options and debugging** not restricted, someone with a computer and `adb` could, for example, switch off usage access (which triggers the fail safe), force-stop the app (the service then restarts when the app opens or the phone restarts), or change settings. **Keep Developer options and debugging restricted on a child's phone.** Only allow it for 15 minutes when installing a test build.
- **Removing the device owner needs the app.** `adb` can't remove Screen time as device owner, because it isn't a test-only app. Only **Remove device owner** in the app (with the PIN) or a factory reset can.

## Other users and profiles

- **Only the main user is covered.** Screen time manages the phone's main user. With **Adding or switching users** restricted, no other users, guests or private spaces can be added. If the phone already had other users before setup, device-owner setup wouldn't have been allowed.
- **No app clone profiles restriction.** Android has no public restriction for "app clone" profiles. GrapheneOS doesn't offer them, so this matters only on other Android builds.

## Updates

- **App updates wait while installs are restricted.** Android's install restriction also stops updates. Use **Allow for 15 minutes** to let app store updates through.
- **Test builds need the same signing key.** A device-owner app can't be uninstalled, so a build signed with a different key can't be installed over it. The phone then needs **Remove device owner** or a factory reset. See "Test build signing key" in the README.
