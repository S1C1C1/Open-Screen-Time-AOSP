# Setting up a test phone for blocking (device-owner mode)

A regular Android app isn't allowed to block other apps. To test real blocking before Open Screen Time is built into a ROM (phase 3), we make it the phone's **device owner**. This is the same mechanism companies use to manage work phones. It gives the app the power to pause apps, apply restrictions and protect itself.

Android only allows this on a phone that has just been reset and has **no accounts** on it.

> **Use a test phone.** Step 1 erases everything on it.

## What you need

- A phone (or emulator) running Android 14 or newer. GrapheneOS is the main target.
- A computer with Android's `adb` tool. It comes with Android Studio, or as the small "SDK Platform Tools" download.
- A USB cable, or wireless debugging on the same Wi-Fi network.
- The latest test APK from the project's GitHub Actions page (**Actions** → latest **Build** → **Artifacts**).

## Steps

1. **Factory reset the phone.** On GrapheneOS: Settings → System → Reset options → Erase all data.
2. **Go through the setup screens without adding any account.** Skip any sign-in. Accounts of any kind stop the next steps from working.
3. **Turn on USB debugging.**
   1. Go to Settings → About phone and tap **Build number** seven times.
   2. Go to Settings → System → Developer options and turn on **USB debugging**.
   3. Connect the phone to the computer and accept the "Allow USB debugging?" prompt.
4. **Install the app** from the computer:

   ```bash
   adb install app-debug.apk
   ```

5. **Open Screen time on the phone** and go through its welcome screens. Allow usage access when asked.
6. **Make it the device owner** from the computer:

   ```bash
   adb shell dpm set-device-owner org.openscreentime/.admin.ScreenTimeAdmin
   ```

   It should answer with `Success: Device owner set to package ...`.
7. **Check in the app:** Settings → **Blocking** should say **Active (device owner)**.

You can now add accounts and apps as normal.

## Installing newer test builds

Install new builds over the old one without uninstalling:

```bash
adb install -r app-debug.apk
```

This only works if every build is signed with the same key. See "Test build signing key" in the README. A device-owner app can't be uninstalled, so if the key changes, you'll need to remove the device owner first (below) or factory reset.

## Removing it

- **In the app:** Settings → **Blocking** → **Remove device owner**. This asks for the PIN if one is set. Blocking and restrictions stop, and the app can then be uninstalled normally.
- **Or factory reset the phone.** Factory reset is never blocked.

## If something goes wrong

| Message from `adb` | What it means |
|---|---|
| `Not allowed to set the device owner because there are already some accounts on the device` | An account was added. Remove all accounts in Settings → Passwords & accounts, or factory reset and skip sign-in. |
| `Not allowed to set the device owner because there are already several users on the device` | A second user or guest exists. Remove it, or factory reset. |
| `Unknown admin` or `Bad admin` | The app isn't installed, or the APK is older than phase 2. Install the latest build. |
| `device owner is already set` | Another app is already device owner. Factory reset. |
| `no devices/emulators found` | USB debugging isn't on, or the "Allow USB debugging?" prompt wasn't accepted. |
