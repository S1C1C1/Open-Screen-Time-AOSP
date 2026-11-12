# Phase 2 test checklist (device-owner mode)

This checklist covers every item in the `SPEC.md` test checklist, plus the phase 2 additions, app groups, app schedules, and the fixes from the code reviews. Work through it on a test phone set up as described in [device-owner-setup.md](device-owner-setup.md).

Tests 1–24 are the original phase 2 tests. Tests 25 onwards cover what was added later; if you already ran 1–24 on an earlier build, start at 25.

**How to record results:** copy this file, fill in the **Result** column with **Pass**, **Fail** or **Skipped**, and add notes, especially for failures: what you did, what you expected, and what happened. Send it back, or paste it into a GitHub issue.

## Before you start

- [ ] The phone runs GrapheneOS (or another Android 14+ build). Write down the version: ____________
- [ ] Settings → **Blocking** in Screen time says **Active (device owner)**.
- [ ] Usage access is allowed for Screen time.
- [ ] Notifications are allowed for Screen time.
- [ ] A screen time PIN is set. It's used throughout as "the PIN". Setting it also switches on the **Date, time and time zone** restriction (test 25).
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
| 18 | Date and time need the PIN | Check **Date, time and time zone** is on (setting a PIN switches it on). Open Settings → System → Date & time. | Settings are greyed out. | | |
| 19 | Screen time can't be removed | Try to uninstall, disable, force-stop and clear storage for Screen time. | All refused or unavailable. | | |
| 20 | Safe mode is blocked | Hold power, long-press **Power off**/**Restart**. | Safe mode isn't offered, or the phone starts normally. | | |
| 21 | Factory reset still works | Open Settings → System → Reset options. **Don't reset** unless you're done testing. | **Erase all data** is available. | | |
| 22 | Restart keeps everything | With a limit reached, downtime set, and restrictions on, restart the phone. | After restart: blocked apps are still paused, restrictions still on, "Screen time is on" notification is back. | | |
| 22a | No gap after a restart | Make a schedule that starts 3 minutes from now. Turn the phone off before it starts, wait until after the start time, turn it on and unlock. Open the app quickly. | The app is paused right away, never usable. A few seconds after the unlock, apps the rules allow open normally again. The keyboard works on the lock screen throughout. | | |
| 23 | Old history is deleted | Set **Keep usage history** to **1 week**. After 8+ days of use, look at last week's chart. | Days older than 7 aren't kept (earlier weeks' data is gone). | | |
| 24 | Removing device owner undoes everything | Settings → **Blocking** → **Remove device owner** (PIN). | All apps unpaused, restrictions lifted, Screen time can be uninstalled. | | |

**Do test 24 last**, after the tests below, since it turns blocking off.

## App groups, app schedules and review fixes

| # | Test | Steps | Expected | Result | Notes |
|---|---|---|---|---|---|
| 25 | PIN switches on the date restriction | When you set the PIN for the first time (on a fresh setup), then open **Restrictions**. If your PIN was set on an earlier build, skip this: that PIN leaves the restriction as it was. | **Date, time and time zone** is on. Switching it off asks for the PIN. | | |
| 26 | Group shared limit | **App groups** → **New group** "Test" with two apps and a 15-minute **Shared daily limit**. Use the two apps for about 15 minutes in total, split between them. | At about 10 minutes, "5 min left on Test today". At 15 minutes both apps are blocked, and the block screen mentions "Test". | | |
| 27 | Group suggestions | Open **App groups**. | If 2 or more apps share a category (for example social or games), a suggestion is listed. Tapping it fills in those apps. | | |
| 28 | Schedule presets | Open **App schedules**. Tap **Bedtime**. | Every day, 9:00 PM, "Ends (next day)" 6:00 AM. After choosing an app and saving, Bedtime moves to your list and Settings shows "1 on". | | |
| 29 | Schedule blocks and unblocks on time | **New schedule** "Test": today only, starting 6 minutes from now, ending 10 minutes after that, with one app. | About 1 minute later, "Test starts at …". At the start the app is blocked: "… is blocked by Test until …", with the right end time. At the end it opens normally. | | |
| 30 | Schedule blocks a group's apps | Make a schedule that's on now and blocks the group from test 26 (after midnight, so the group limit isn't reached). | Both group apps are blocked by the schedule. The app's page lists the schedule "through" the group and "On now, until …". | | |
| 31 | Extra time only lifts its own block | Give an app a 15-minute limit and turn on Downtime starting 20 minutes from now. Reach the limit, then **Enter PIN** → **Rest of the day**. Wait for downtime. | The app opens after the PIN, then is blocked again when downtime starts, this time by downtime. | | |
| 32 | "Until the schedule ends" | While the test 29 schedule is on, on its blocked app use **Enter PIN** → **Until the schedule ends**. | The app opens and stays open; the schedule's end time shown was correct (not midnight). | | |
| 33 | Staying in an app past midnight | Shortly before midnight, open an app that has a limit and keep using it without leaving. | After midnight its time keeps counting towards the new day, and it's blocked when the new day's limit is reached. | | |
| 34 | Keyboard and screen reader keep working | Turn on downtime. Type somewhere, and lock and unlock the phone. If you use a screen reader (TalkBack or another), try it during downtime too. | The keyboard works everywhere, including the lock screen. The screen reader keeps working. | | |
| 35 | Accessibility can't be used to escape a limit | Pick an ordinary app that offers an accessibility service (for example an automation or password app). Turn its service on in Settings → Accessibility. Give the app a 15-minute limit and reach it. | The app is blocked like any other app. | | |
| 36 | A second launcher can be blocked | Install a second launcher app, but don't make it the default. Give it a 15-minute limit and use it. | Its time is counted and it's blocked at the limit. The home screen you actually use is never blocked. | | |
| 37 | Preview block screen saves nothing | Set **When time is up** to **Grace**. On an app's page tap **Preview block screen** → **One more minute**. | The app's page is unchanged (no "unlocked until …"). A real block later that day still offers **One more minute**. | | |
| 38 | **Picture-in-picture video** | Give a video app (for example NewPipe or a browser playing video) a 15-minute limit. Start a video and press Home so it shrinks into a small floating window. Let it play for 20 minutes. | Note what happens: does the time count towards the limit, and is the app blocked? **This one is to find out, not a known pass:** please describe exactly what you see. | | |
| 39 | Restart during a schedule | During the test 29 schedule, restart, unlock, and open Screen time straight away. | Apps the rules allow open within a few seconds; the scheduled app stays blocked. | | |

## Battery and everyday use

| Question | Answer |
|---|---|
| After a normal day, how much battery does Android's Settings → Battery say Screen time used? | |
| Did the "Screen time is on" notification ever disappear while limits or downtime were set? | |
| Anything confusing, slow, or surprising? | |
