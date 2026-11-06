# Phase 2 test checklist (device-owner mode)

This checklist covers every item in the `SPEC.md` test checklist, plus the phase 2 additions. Work through it on a test phone set up as described in [device-owner-setup.md](device-owner-setup.md).

**How to record results:** copy this file, fill in the **Result** column with **Pass**, **Fail** or **Skipped**, and add notes, especially for failures: what you did, what you expected, and what happened. Send it back, or paste it into a GitHub issue.

## Before you start

- [ ] The phone runs GrapheneOS (or another Android 14+ build). Write down the version: ____________
- [ ] Settings → **Blocking** in Screen time says **Active (device owner)**.
- [ ] Usage access is allowed for Screen time.
- [ ] Notifications are allowed for Screen time.
- [ ] A screen time PIN is set. It's used throughout as "the PIN".
- [ ] **When time is up** is set to **Strict**, unless a test says otherwise.

Tip: restrictions on **Developer options and debugging** stop `adb`. Use **Allow for 15 minutes** before installing a new build.

## Results

| # | Test | Steps | Expected | Result | Notes |
|---|---|---|---|---|---|
| 1 | Usage times are roughly right | Use one app for exactly 10 minutes (use a timer). Open Screen time. | That app shows about 10 minutes more than before, within about a minute. | | |
| 2 | Home screen isn't counted | Stay on the home screen for 5 minutes without opening apps. | Today's total doesn't grow by 5 minutes; the launcher isn't listed. | | |
| 3 | 5-minute warning | Set a 15-minute limit on an app and use it. | At about 10 minutes, a "5 min left on … today" notification appears, once. | | |
| 4 | Limit blocks — Strict | Keep using the app from test 3. | At 15 minutes, within a few seconds, the block screen covers it. "Close app" goes home. The app icon then shows Android's "paused" message. | | |
| 5 | Limit blocks — Grace | Switch **When time is up** to **Grace** (PIN). Reach a limit on another app. | Block screen offers **One more minute**. It gives one minute, then blocks again; it isn't offered again that day. | | |
| 6 | Limit blocks — Reminder | Switch to **Reminder** (PIN). Reach a limit on another app. | A reminder appears; the app is **not** paused; **Ignore for 15 minutes** lets you continue; after 15 minutes it reminds again. | | |
| 7 | PIN gives more time | On a paused app's page in Screen time, tap **Get more time** → PIN → **15 minutes**. | The app opens straight away; after 15 minutes it's blocked again. | | |
| 8 | Wrong PINs are throttled | Enter a wrong PIN 5 times. | After the 5th, "Try again in 1:00" counts down and the number pad is off. Restarting the app doesn't skip the wait. | | |
| 9 | Limits reset at midnight | Leave an app blocked by its limit until after midnight. | Shortly after midnight, the app opens again and today starts at zero. | | |
| 10 | Downtime warning | Set downtime to start in 7 minutes. | About 5 minutes before, a "Downtime starts at …" notification appears. | | |
| 11 | Downtime blocks all but always allowed | Wait for downtime to start. Try several apps. | Every app except always allowed ones (Phone, Settings, …) is paused; an open app gets the block screen. | | |
| 12 | Downtime ends on schedule | Wait for the end time. | All apps open again within a few seconds of the end time. | | |
| 13 | **Emergency calling during downtime** | During downtime, open the Phone app and the lock screen's emergency call. **Don't actually call** — dial a non-emergency number to check the dialer works, and confirm the emergency dialer opens. | Both work. Emergency calling is shown as "Can't be removed" in **Always allowed**. | | |
| 14 | **Emergency calling with everything blocked** | Turn off usage access for Screen time (this pauses all apps except always allowed). Check the dialer and emergency dialer as in 13. Turn usage access back on. | Both still work. | | |
| 15 | Installs need the PIN | Turn on **App installs and uninstalls**. Try installing an APK and uninstalling an app. | Both are refused. **Allow for 15 minutes** (PIN) lets them through; afterwards they're refused again. | | |
| 16 | Developer options need the PIN | Turn on **Developer options and debugging**. Try to open Developer options and use `adb devices`. | Developer options are greyed out; adb doesn't see the phone. **Allow for 15 minutes** (PIN) brings both back. | | |
| 17 | Users need the PIN | Turn on **Adding or switching users**. Try adding a user, guest, or private space (Android 15+), and switching users. | All refused. | | |
| 18 | Date and time need the PIN | Turn on **Date, time and time zone**. Open Settings → System → Date & time. | Settings are greyed out. | | |
| 19 | Screen time can't be removed | Try to uninstall, disable, force-stop and clear storage for Screen time. | All refused or unavailable. | | |
| 20 | Safe mode is blocked | Hold power, long-press **Power off**/**Restart**. | Safe mode isn't offered, or the phone starts normally. | | |
| 21 | Factory reset still works | Open Settings → System → Reset options. **Don't reset** unless you're done testing. | **Erase all data** is available. | | |
| 22 | Restart keeps everything | With a limit reached, downtime set, and restrictions on, restart the phone. | After restart: blocked apps are still paused, restrictions still on, "Screen time is on" notification is back. | | |
| 23 | Old history is deleted | Set **Keep usage history** to **1 week**. After 8+ days of use, look at last week's chart. | Days older than 7 aren't kept (earlier weeks' data is gone). | | |
| 24 | Removing device owner undoes everything | Settings → **Blocking** → **Remove device owner** (PIN). | All apps unpaused, restrictions lifted, Screen time can be uninstalled. | | |

## Battery and everyday use

| Question | Answer |
|---|---|
| After a normal day, how much battery does Android's Settings → Battery say Screen time used? | |
| Did the "Screen time is on" notification ever disappear while limits or downtime were set? | |
| Anything confusing, slow, or surprising? | |
