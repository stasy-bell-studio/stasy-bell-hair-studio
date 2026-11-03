#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
EVIDENCE="$ROOT/v2/android/acceptance-evidence"
mkdir -p "$EVIDENCE/screenshots"
collect() {
    adb logcat -d -v threadtime > "$EVIDENCE/logcat.txt" || true
    adb pull /sdcard/acceptance/. "$EVIDENCE/screenshots/" || true
    adb shell wm size reset || true
    adb shell wm density reset || true
    adb shell settings put system font_scale 1.0 || true
}
trap collect EXIT
adb reverse tcp:8000 tcp:8000
adb logcat -c
cd "$ROOT/v2/android"
gradle :app:testDebugUnitTest :app:connectedDebugAndroidTest --no-daemon -PstasyApiBaseUrl=http://127.0.0.1:8000/
python "$ROOT/v2/android/scripts/verify-cloud-state.py" | tee "$EVIDENCE/backend-verification.txt"
# The successful journey ends on the login screen. Capture it at the requested
# viewport and font-scale combinations; these images require visual review.
adb shell wm density 160
for size in 320x640 360x800 393x852 430x932; do
    adb shell wm size "$size"
    for scale in 1.0 1.3 1.5; do
        adb shell settings put system font_scale "$scale"
        sleep 2
        adb exec-out screencap -p > "$EVIDENCE/screenshots/login-${size}-font-${scale}.png"
    done
done
