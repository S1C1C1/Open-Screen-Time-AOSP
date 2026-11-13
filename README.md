# Open Screen Time

An open source screen time and parental controls system app for custom Android ROMs. Tracks per-app usage, sets app limits and downtime, and locks restrictions behind a separate passcode.

GrapheneOS is the first target. See [SPEC.md](SPEC.md) for the full plan.

**Status:** phase 1 is complete (dashboard, limits, downtime, always allowed apps, PIN and block screen previews). Phase 2 (real blocking, restrictions and self-protection in device-owner mode) is built and ready for device testing.

## Privacy

All data stays on the phone. The app has no internet permission, and an automated test fails the build if one is ever added. No analytics, crash reporting, Google Play Services or other third-party services.

## Download

Get the latest release from the [Releases page](https://github.com/S1C1C1/Open-Screen-Time-AOSP/releases):

1. On the phone, open the latest release and download the `.apk` file under **Assets**.
2. Open it and allow installs from your browser or file manager when Android asks.

Requires Android 14 or newer. Releases are signed with the project's release key, so each new release installs over the last one and keeps your settings. Each release's notes list the key's fingerprint for checking the download.

To really block apps, the app must be set up as the phone's device owner: see [Testing real blocking](#testing-real-blocking).

## Getting a test build

Test builds are built from every change, before it becomes a release. They're signed with a different key from releases, so a phone can't switch between the two without uninstalling (which deletes the app's data).


Every push and pull request is built by GitHub Actions.

1. Open the **Actions** tab on GitHub and pick the latest **Build** run for the branch or pull request.
2. Under **Artifacts**, download `open-screen-time-debug-…`. It's a zip file containing `app-debug.apk`.
3. Copy the APK to the phone and open it to install. Android will ask you to allow installing apps from that source.

Requires Android 14 or newer.

## Testing real blocking

Phase 1 builds can only preview block screens. To test real blocking (phase 2), the app has to be made the phone's **device owner**:

- [docs/device-owner-setup.md](docs/device-owner-setup.md): how to set up a test phone.
- [docs/phase-2-test-checklist.md](docs/phase-2-test-checklist.md): what to test, with a results table.
- [docs/device-owner-limitations.md](docs/device-owner-limitations.md): what device-owner mode can't protect.

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

## Making a release

Releases are built and published by the **Release** workflow on GitHub, signed with a release key that only exists as GitHub secrets. Only a repository admin can do this.

### One-time setup: the release key

Do this once, on a computer with Java installed. **Keep the key and its passwords safe and backed up.** If the key is lost, future releases can't update existing installs: everyone would have to uninstall (losing their data) and install again.

1. Create the key. It asks for a password (use a long one) and some name details; the alias is `release`:

   ```bash
   keytool -genkeypair -keystore open-screen-time-release.keystore -alias release -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Turn it into text that GitHub can store:

   ```bash
   base64 -w0 open-screen-time-release.keystore > release.keystore.txt
   ```

   On macOS, use `base64 -i open-screen-time-release.keystore -o release.keystore.txt` instead.

3. On GitHub, go to **Settings → Secrets and variables → Actions** and add four **repository secrets**:

   | Name | Value |
   |---|---|
   | `RELEASE_KEYSTORE_BASE64` | the contents of `release.keystore.txt` |
   | `RELEASE_KEYSTORE_PASSWORD` | the password you chose |
   | `RELEASE_KEY_ALIAS` | `release` |
   | `RELEASE_KEY_PASSWORD` | the same password (keytool uses one password for both unless you chose otherwise) |

4. Delete `release.keystore.txt`. Move `open-screen-time-release.keystore` somewhere safe outside the repository, with a backup copy (for example a password manager or an encrypted USB drive). Never commit it: `.gitignore` already blocks `*.keystore` files.

This key is separate from the test build key in [Test build signing key](#test-build-signing-key). Don't reuse one for the other.

### Publishing a release

1. Make sure `versionName` and `versionCode` in `app/build.gradle.kts` are set for the new release (raise both for every release after the first), and that `docs/releases/<versionName>.md` has its release notes. Merge that to `main`.
2. On GitHub, open **Actions → Release → Run workflow**, choose the `main` branch, and run it.
3. It runs every check, builds and signs the APK, and publishes it on the Releases page as `v<versionName>`. If a secret is missing or the version was already released, it stops and says why.

## License

Apache 2.0. See [LICENSE](LICENSE).
