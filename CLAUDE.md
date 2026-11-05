# CLAUDE.md

## Project

Open Screen Time is a screen time and parental controls app for custom Android ROMs, first targeting GrapheneOS. **Read `SPEC.md` before starting any task.** It is the source of truth for features, behavior, and phases.

## Working style

- Before writing code for a new feature, summarize your plan and wait for approval.
- Work in small, reviewable steps. One feature or fix per change.
- Follow the development phases in `SPEC.md` in order. Don't start system app integration (phase 3) until phases 1 and 2 are done.
- If something in `SPEC.md` is unclear or seems wrong, ask instead of guessing. If a decision changes, update `SPEC.md` in the same change.
- Explain changes in plain language. The project owner is not an experienced Android developer.

## Testing

- The project owner does not run the app locally. A friend tests builds on a real device.
- Every change must build successfully with Gradle from the command line and in GitHub Actions.
- Add or update automated tests for any logic you change, especially limits, downtime, and the PIN.
- Add or update screenshot tests for any screen you change, so the UI can be reviewed without a phone.
- In each pull request, include a short **How to test** section with steps a tester can follow on a device.

## Technical rules

- Kotlin and Jetpack Compose with Material 3. Match the look of AOSP Settings.
- Minimum Android 14 (API 34).
- Never add the INTERNET permission or any network code. All data stays on the device.
- No Google Play Services, Firebase, analytics, or other third-party services.
- Prefer AndroidX and the standard Android SDK. Add any other dependency only after asking.
- Use existing Android system APIs. Don't require changes to the Android framework.
- Keep the enforcement layer behind an interface so it can work in both system app mode and device-owner mode.
- Store the PIN only as a salted hash, using the Android keystore where available. Never log or display it.
- Never commit signing keys, keystores, or secrets.

## Code style

- Clear names and small functions over clever code.
- Comment why something is done when it isn't obvious, especially anything security related.
- Keep user-facing text in string resources, not hard-coded.
