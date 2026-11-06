# Open Screen Time

An open source screen time and parental controls system app for custom Android ROMs. Tracks per-app usage, sets app limits and downtime, and locks restrictions behind a separate passcode.

GrapheneOS is the first target. See [SPEC.md](SPEC.md) for the full plan.

**Status:** phase 1 is complete (dashboard, limits, downtime, always allowed apps, PIN and block screen previews). Phase 2, real blocking in device-owner mode, is in progress.

## Privacy

All data stays on the phone. The app has no internet permission, and an automated test fails the build if one is ever added. No analytics, crash reporting, Google Play Services or other third-party services.

## Getting a test build

Every push and pull request is built by GitHub Actions.

1. Open the **Actions** tab on GitHub and pick the latest **Build** run for the branch or pull request.
2. Under **Artifacts**, download `open-screen-time-debug-…`. It's a zip file containing `app-debug.apk`.
3. Copy the APK to the phone and open it to install. Android will ask you to allow installing apps from that source.

Requires Android 14 or newer.

## Testing real blocking

Phase 1 builds can only preview block screens. To test real blocking (phase 2), the app has to be made the phone's **device owner**. See [docs/device-owner-setup.md](docs/device-owner-setup.md).

## Building on a computer

You need JDK 21 and the Android SDK (Android Studio includes both).

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Run every check that GitHub Actions runs:

```bash
./gradlew lintDebug verifyRoborazziDebug assembleDebug
```

## Screenshot tests

Screens are checked with [Roborazzi](https://github.com/takahirom/roborazzi) screenshot tests, which run on a computer through Robolectric without a phone. The reference images live in `app/src/test/screenshots/`, so pull requests show exactly how each screen changed.

After changing a screen on purpose, update the reference images and commit them:

```bash
./gradlew recordRoborazziDebug
```

If the images no longer match, the build fails and the run's `screenshot-differences` artifact shows the old image, the new one and the difference.

## Test build signing key

By default, each GitHub Actions build is signed with a new temporary key. Android refuses to install a build signed with a different key over an existing one, so testers would have to uninstall the app (and lose its data) before each new build.

To avoid that, give GitHub one fixed **debug** key. It's only for test builds, never for releases. Only a repository admin can do this, and it only needs doing once.

1. On a computer with Java installed, create the key:

   ```bash
   keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
   ```

2. Turn it into text that GitHub can store:

   ```bash
   base64 -w0 debug.keystore > debug.keystore.txt
   ```

   On macOS, use `base64 -i debug.keystore -o debug.keystore.txt` instead.

3. On GitHub, go to **Settings → Secrets and variables → Actions → New repository secret**. Name it `DEBUG_KEYSTORE_BASE64` and paste the contents of `debug.keystore.txt`.
4. Delete `debug.keystore.txt`. Keep `debug.keystore` somewhere safe outside the repository, or delete it too: you can always make a new one, but testers would then need to uninstall once.

Never commit the key file. `.gitignore` already blocks `*.keystore` files.

After this, the first build with the new key still needs one uninstall. Later builds install over it.

## License

Apache 2.0. See [LICENSE](LICENSE).
