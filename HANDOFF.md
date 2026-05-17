# Focus Blocker Handoff

## Project Location

`C:\Users\Gabe\Documents\Codex\Projects\GabeApp - Android Focus Blocker`

Android package/application ID: `com.gabe.focusblocker`

Latest built test APK:

`C:\Users\Gabe\Documents\Codex\Projects\GabeApp - Android Focus Blocker\FocusBlocker-system-safe-widget-presets-debug.apk`

## Current Status

This is a sideloaded Android focus/self-control app built in Kotlin with Jetpack Compose. The current app supports editable rule sets, delayed countdown locks, active app blocking through an `AccessibilityService`, local Room persistence, a resizable home-screen widget, basic website/domain blocking scaffolding through `VpnService`, and local notification countdowns.

The current Git repo has only one committed baseline (`594e6bb initial push`). Most recent work is uncommitted and includes the countdown workflow, widget changes, system-app safety, database migrations, and generated APKs.

## Tech Stack

- Kotlin
- Jetpack Compose / Material 3
- Room database
- DataStore preferences
- WorkManager for scheduled countdown starts
- Android `AccessibilityService` for app blocking
- Android `VpnService` for local DNS/domain blocking attempt
- Traditional `AppWidgetProvider` / `RemoteViews` widget
- Target SDK 34, min SDK 28

## Build Notes

The project uses the Unity-installed Android SDK path in `local.properties`:

`C:\Program Files\Unity\Hub\Editor\6000.0.8f1\Editor\Data\PlaybackEngines\AndroidPlayer\SDK`

Useful PowerShell build command:

```powershell
$env:GRADLE_USER_HOME=(Resolve-Path '.gradle-user-home').Path
$env:JAVA_HOME='C:\Program Files\Unity\Hub\Editor\6000.0.8f1\Editor\Data\PlaybackEngines\AndroidPlayer\OpenJDK'
$env:ANDROID_HOME='C:\Program Files\Unity\Hub\Editor\6000.0.8f1\Editor\Data\PlaybackEngines\AndroidPlayer\SDK'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
.\gradlew.bat assembleDebug --no-daemon --stacktrace
```

If Gradle wrapper has trouble, the previous successful build used Unity's Gradle launcher directly:

```powershell
$env:GRADLE_USER_HOME=(Resolve-Path '.gradle-user-home').Path
$env:JAVA_HOME='C:\Program Files\Unity\Hub\Editor\6000.0.8f1\Editor\Data\PlaybackEngines\AndroidPlayer\OpenJDK'
$env:ANDROID_HOME='C:\Program Files\Unity\Hub\Editor\6000.0.8f1\Editor\Data\PlaybackEngines\AndroidPlayer\SDK'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
& 'C:\Program Files\Unity\Hub\Editor\6000.0.8f1\Editor\Data\PlaybackEngines\AndroidPlayer\OpenJDK\bin\java.exe' -classpath 'C:\Program Files\Unity\Hub\Editor\6000.0.8f1\Editor\Data\PlaybackEngines\AndroidPlayer\Tools\gradle\lib\gradle-launcher-8.4.jar' org.gradle.launcher.GradleMain assembleDebug --no-daemon --stacktrace
```

## Core Behavior

- User can create/edit rule sets.
- Seeded presets are:
  - `Set A - Short Focus Block`
  - `Set B - Deep Work Block`
  - `Set C - Essentials Only`
- Rule modes:
  - `BLOCK_LIST`: selected apps/domains are blocked.
  - `ALLOW_ONLY`: selected apps/domains are allowed, with emergency/system packages still allowed.
- Dashboard is countdown-first:
  - Select rule set.
  - Select delay: `Now`, `5`, `10`, `20`, `30` minutes, or type a custom minute count.
  - Select lock duration: `5`, `15`, `30`, `60`, `90` minutes, or type custom.
  - Tap start countdown.
- A scheduled countdown becomes an active lock via `ScheduledLockWorker`.
- Ending active locks requires typing a random 12-character challenge.
- Canceling/deleting a countdown before it starts does not require the challenge.
- If a scheduled lock for the same rule set overlaps an active lock, the active session is extended instead of duplicating it.
- Different rule sets can still run simultaneously.

## System App Safety

System apps are ignored by the blocker by default at the rule-engine level.

Important behavior:

- Installed app picker has a `Show system apps` toggle.
- System apps are labeled as always allowed.
- System apps generally cannot be newly selected in the picker.
- If an old rule set already contains a system app, it can still be deselected.
- `BlockingRepository.evaluatePackage()` returns allowed for system packages through `PackageUtils.isSystemPackage(...)`.
- This also applies in `ALLOW_ONLY` mode: allow-only means selected apps plus system apps/emergency packages.

Main files:

- `app/src/main/java/com/gabe/focusblocker/repository/BlockingRepository.kt`
- `app/src/main/java/com/gabe/focusblocker/util/PackageUtils.kt`
- `app/src/main/java/com/gabe/focusblocker/ui/FocusBlockerApp.kt`

## Widget Behavior

Widget implementation:

- `app/src/main/java/com/gabe/focusblocker/widget/FocusWidgetProvider.kt`
- `app/src/main/java/com/gabe/focusblocker/widget/WidgetActionReceiver.kt`
- `app/src/main/java/com/gabe/focusblocker/widget/RecentWidgetPresets.kt`
- Layout: `app/src/main/res/layout/focus_widget.xml`
- Metadata: `app/src/main/res/xml/focus_widget_info.xml`

Current widget features:

- Resizable widget with a shorter compact mode.
- Up to 6 rule-set selection buttons.
- Rule sets only appear if `showInWidget = true`.
- Each rule set editor has a `Show in widget` switch.
- Widget has explicit `Start`; it does not auto-start after selections.
- Widget has `Refresh` and `Clear` countdown buttons.
- Widget shows status for:
  - selected set/delay/duration
  - countdowns until lock starts
  - active locks until lock ends
- Top row shows last 2 scheduled lock presets as quick buttons, e.g. `Set A in 5m for 30m`.
- Tapping a recent preset immediately schedules that same lock again.

If widget layout appears stale after installing a new APK, remove and re-add the widget.

## Data Model / Database

Room database version is currently `3`.

Entities include:

- `RuleSetEntity`
- `RuleAppEntity`
- `RuleDomainEntity`
- `ActiveSessionEntity`
- `ScheduledLockEntity`

Current migrations:

- `MIGRATION_1_2`: adds `scheduled_locks`.
- `MIGRATION_2_3`: adds `rule_sets.showInWidget INTEGER NOT NULL DEFAULT 1`.

Main DB files:

- `app/src/main/java/com/gabe/focusblocker/data/AppDatabase.kt`
- `app/src/main/java/com/gabe/focusblocker/data/entity/RuleSetEntity.kt`
- `app/src/main/java/com/gabe/focusblocker/data/entity/ScheduledLockEntity.kt`
- `app/src/main/java/com/gabe/focusblocker/data/dao/RuleSetDao.kt`
- `app/src/main/java/com/gabe/focusblocker/data/dao/ScheduledLockDao.kt`

## App Blocking

App blocking uses `AppBlockAccessibilityService`.

Behavior:

- Watches foreground/window accessibility events.
- Ignores own package and `com.android.systemui`.
- Debounces repeated blocks.
- Checks package decisions through `BlockingRepository`.
- Opens `BlockedActivity` for blocked apps.
- Also tries to read browser address-bar text for domain fallback.

Files:

- `app/src/main/java/com/gabe/focusblocker/AppBlockAccessibilityService.kt`
- `app/src/main/java/com/gabe/focusblocker/BlockedActivity.kt`
- `app/src/main/res/xml/accessibility_service_config.xml`

Permissions user must enable:

- Android Accessibility permission for app blocking.
- Notification permission for countdown/active notifications on Android 13+.
- VPN permission for website/domain filtering.

There is an in-app Permissions screen with exact accessibility setup instructions.

## Website / Domain Blocking

There is a local `VpnService` DNS-filtering attempt:

- `app/src/main/java/com/gabe/focusblocker/vpn/DomainBlockVpnService.kt`
- `app/src/main/java/com/gabe/focusblocker/vpn/DnsPacketCodec.kt`
- domain matching utilities in `DomainUtils`.

Known limitations:

- Android Private DNS, Chrome Secure DNS/DoH, cached DNS, and app-specific networking can bypass local DNS filtering.
- The app does not and should not do HTTPS MITM.
- Chrome fullscreen pages may not expose the URL to Accessibility until browser UI is visible.
- When Accessibility detects a blocked browser domain, it attempts to redirect the browser to Google before showing the block screen, but Android does not allow a normal app to reliably close another app's tab.

## Notifications

`SessionNotificationHelper` keeps a notification active when there are scheduled countdowns or active locks.

It includes countdown text for:

- time until scheduled locks start
- time until active locks end

Android only supports one chronometer target per notification, so the chronometer prioritizes the nearest active lock end if present; otherwise the next scheduled start.

## Settings / PIN / Challenge

There is older PIN infrastructure through DataStore and `PinManager`, but active-session ending currently uses the requested random 12-character challenge instead of PIN.

Current challenge behavior:

- Required to end one active session.
- Required to end all active sessions.
- Not required to delete/cancel a scheduled countdown before it starts.

Files:

- `app/src/main/java/com/gabe/focusblocker/engine/PinManager.kt`
- `app/src/main/java/com/gabe/focusblocker/repository/SettingsRepository.kt`
- challenge dialog in `FocusBlockerApp.kt`

## Important User Preferences / Product Direction

The user wants a personal sideloaded Android self-control app, not a parental-control or surveillance app.

Strong product preferences:

- Countdown-first workflow is the main purpose.
- User chooses a rule set, delay until lock, and lock duration.
- Quick buttons matter.
- Widget should be useful without opening the app.
- Do not put stop/end controls on the widget.
- Active lock ending should have friction.
- Scheduled countdowns should remain editable/cancelable before they start.
- System apps should generally never be blocked.
- Allow-only mode should allow selected apps plus system apps.
- Website blocking is important but has platform limitations; be transparent.

## Current Known Gaps / Next Good Tasks

High-value next tasks:

- Update README because it is stale and still says widget/VPN/notifications are unfinished.
- Add tests for `RuleEngine`, `BlockingRepository`, `DomainUtils`, and scheduled-lock overlap behavior.
- Add a real widget configuration screen if more than 6 widget-visible sets are needed.
- Improve widget layout responsiveness with multiple layout resources by size, if RemoteViews compact mode is not enough.
- Improve website blocking reliability instructions: tell user to disable Android Private DNS and Chrome Secure DNS for testing.
- Add notification action to open app/permissions only, not stop sessions.
- Consider replacing old PIN UI with challenge-based wording, or clearly separate PIN from challenge behavior.
- Add Room migration tests before more schema changes.

## Latest APK

Use this APK for current testing:

`FocusBlocker-system-safe-widget-presets-debug.apk`

Generated from a successful `assembleDebug` build. Size observed: `63692832` bytes.
