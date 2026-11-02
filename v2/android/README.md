# Private Stasy Bell Android app — development only

Native Kotlin / Jetpack Compose owner app. No client registration or customer app.
Current screens: owner login; today; 8-day calendar list; clients; create manual confirmed visit.
API uses one atomic owner endpoint to create client and booking; no phone, bot or SMS needed.
Auth token lives **only in app process memory**; logout removes it. Android backups disabled.
Network security: HTTPS-only, no cleartext. API URL defaults to intentionally unusable `https://api.invalid/`.
For a development build pointing to an approved staging HTTPS API, use `-PstasyApiBaseUrl=https://approved-dev.example/`.
**Never use production API or store user passwords, client Excel files, signing keys or authentication secrets in public GitHub.**

### Build
`gradle :app:assembleDebug :app:testDebugUnitTest` inside `v2/android/`, with Java 17 and Android SDK platform 35. GitHub Actions runs these checks on PRs; no APK upload, no Play Store publishing and no deployment.

This is a first UI implementation, **not** a device-accepted APK. Upcoming: owner schedule editor, move/cancel/delete confirmation dialogs, biometric optional login, push notifications, offline read-only cache and financial screens.
