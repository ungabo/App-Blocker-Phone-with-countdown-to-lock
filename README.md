# Focus Blocker

Android focus and self-control app built with Kotlin, Jetpack Compose, Room, and an `AccessibilityService`-based app blocker.

## What's in this MVP

- Compose dashboard with quick-start buttons for Set A, Set B, and Set C
- Room-backed rule sets, rule apps, domains, and active sessions
- Seeded presets:
  - `Set A - Short Focus Block`
  - `Set B - Deep Work Block`
  - `Set C - Essentials Only`
- Installed app picker with app icons, search, and system-app toggle
- Simultaneous session support
- `ALLOW_ONLY` priority logic with emergency allowlist handling
- Full-screen blocked activity for blocked apps
- Accessibility service manifest wiring and config
- Boot receiver cleanup for expired sessions
- Settings scaffolding for future friction controls
- Domain storage and matching utilities, ready for the later VPN phase
- Entitlement manager stub for future billing/readiness work

## Not finished yet

- PIN lock and secure hash storage
- Home-screen widget / quick actions outside the app
- Local VPN website blocking
- Foreground notification and richer background maintenance
- Billing and Play Store compliance polish

## Open in Android Studio

1. Open this folder in Android Studio.
2. Let Gradle download the configured Android dependencies.
3. Sync the project.
4. Run on a real Android device.
5. Grant Accessibility permission from the in-app Permissions screen.

## Quick acceptance test

1. Open `Rule Sets`.
2. Edit `Set A - Short Focus Block`.
3. Choose apps and add YouTube or another distractor.
4. Save the rule set.
5. Go back to `Dashboard`.
6. Start `Set A`.
7. Open the blocked app on the device.
8. Confirm the blocked screen appears.

## Important note

This build was scaffolded in a workspace without a local Java/Android SDK toolchain, so I could not run a real Gradle build here. The project structure, manifest wiring, and Kotlin sources are in place for Android Studio to import and finish syncing on your machine.
