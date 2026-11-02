#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
EVIDENCE="$ROOT/v2/android/acceptance-evidence"
mkdir -p "$EVIDENCE/screenshots"
collect() {
    adb logcat -d -v threadtime > "$EVIDENCE/logcat.txt" || true
    adb pull /sdcard/acceptance/. "$EVIDENCE/screenshots/" || true
}
trap collect EXIT
adb reverse tcp:8000 tcp:8000
adb logcat -c
cd "$ROOT/v2/android"
gradle :app:connectedDebugAndroidTest --no-daemon -PstasyApiBaseUrl=http://127.0.0.1:8000/
