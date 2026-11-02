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

### One-time local emulator acceptance (not production)
- Debug artifacts in the GitHub Actions run now use `http://127.0.0.1:8000/` (via ADB reverse) and are kept only for **3 days** under the name `stasy-bell-v2-emulator-debug`. Extract the `app-debug.apk` from the artifact. Do not install it on a real customer device.
- On a trusted workstation with Docker Desktop and Android emulator + adb, from repository root run `bash v2/android/scripts/run-local-emulator.sh /private/path/app-debug.apk`. It creates only a brand-new, explicitly marked emulator database under private `v2/.env`, starts backend locally, runs migrations, installs APK and performs `adb reverse`. It refuses any unmarked environment and keeps the public booking switch OFF.
- For first-time login create the local owner once with `python -m app.bootstrap_owner` inside the API container using secret environment credentials. Do not share those credentials here. No real customer/finance workbook should be used.
- Test calendar working windows, manual visit without phone, moving/cancelling/deleting, closed-day blocking, finance actual cash, inventory. Changing a working window with an existing conflicting booking must return 409.
- Release Android manifest keeps HTTP disabled. Only debug build allows loopback HTTP. Use an approved HTTPS staging backend to test actual Telegram/VK later.
