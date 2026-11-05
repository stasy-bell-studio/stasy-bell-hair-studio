# Private Stasy Bell Android app — development only

Native Kotlin / Jetpack Compose owner app. No client registration or customer app.
Current screens: owner login; dashboard; day/week/month calendar; clients; manual booking and visit changes; services; finance; working schedule; materials; basic settings.
API uses one atomic owner endpoint to create client and booking; no phone, bot or SMS needed.
In this feature branch, the 30-minute access token stays in memory; only a rotating 30-day refresh credential is encrypted with Android Keystore. Optional BiometricPrompt unlock is configured in Settings. Logout revokes the server device session when reachable. Android backups remain disabled. See [OWNER_SESSIONS.md](OWNER_SESSIONS.md).
Network security: HTTPS-only for release; ADB-reversed loopback HTTP permitted in debug only. API URL defaults to intentionally unusable `https://api.invalid/`.
For a development build pointing to an approved staging HTTPS API, use `-PstasyApiBaseUrl=https://approved-dev.example/`.
**Never use production API or store user passwords, client Excel files, signing keys or authentication secrets in public GitHub.**

### Build
`gradle :app:assembleDebug :app:testDebugUnitTest` inside `v2/android/`, with Java 17 and Android SDK platform 35. GitHub Actions runs these checks on PRs; no APK upload, no Play Store publishing and no deployment.

The current Android UI has passed synthetic Android 15 emulator acceptance, and the earlier APK was manually explored on a physical Samsung S20. This branch changes authorization and **has not yet been tested on the physical phone**. Missing capabilities include complete client history, full owner reports, offline read-only cache, external push notifications and infrastructure status. A usable session outside USB needs a separately approved HTTPS staging backend. Backend migration `0007_owner_sessions` is required before installing the new authorization-enabled APK; keep the existing PostgreSQL volume and private environment file.

### One-time local emulator acceptance (not production)
- Debug artifacts in the GitHub Actions run now use `http://127.0.0.1:8000/` (via ADB reverse) and are kept only for **3 days** under the name `stasy-bell-v2-emulator-debug`. Extract the `app-debug.apk` from the artifact. Do not install it on a real customer device.
- On a trusted workstation with Docker Desktop and Android emulator + adb, from repository root run `bash v2/android/scripts/run-local-emulator.sh /private/path/app-debug.apk`. It creates only a brand-new, explicitly marked emulator database under private `v2/.env`, starts backend locally, runs migrations, installs APK and performs `adb reverse`. It refuses any unmarked environment and keeps the public booking switch OFF.
- For first-time login create the local owner once with `python -m app.bootstrap_owner` inside the API container using secret environment credentials. Do not share those credentials here. No real customer/finance workbook should be used.
- Test calendar working windows, manual visit without phone, moving/cancelling/deleting, closed-day blocking, finance actual cash, inventory. Changing a working window with an existing conflicting booking must return 409.
- Release Android manifest keeps HTTP disabled. Only debug build allows loopback HTTP. Use an approved HTTPS staging backend to test actual Telegram/VK later.
