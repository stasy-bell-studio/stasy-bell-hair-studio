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
# Configure before instrumentation launches Compose. Changing density/font from
# a running Compose test previously stalled its configuration transition.
adb shell wm size 393x852
adb shell wm density 160
adb shell settings put system font_scale 1.0
cd "$ROOT/v2/android"
timeout 12m gradle :app:connectedDebugAndroidTest --no-daemon -PstasyApiBaseUrl=http://127.0.0.1:8000/ \
    -Pandroid.testInstrumentationRunnerArguments.class=com.stasybell.os.CloudAcceptanceTest#ownerJourney
python "$ROOT/v2/android/scripts/verify-cloud-state.py" | tee "$EVIDENCE/backend-verification.txt"
cp -R app/build/outputs/androidTest-results "$EVIDENCE/owner-journey-results"
python "$ROOT/v2/android/scripts/seed-visual-review.py" | tee "$EVIDENCE/visual-fixtures.txt"
timeout 5m gradle :app:connectedDebugAndroidTest --no-daemon -PstasyApiBaseUrl=http://127.0.0.1:8000/ \
    -Pandroid.testInstrumentationRunnerArguments.class=com.stasybell.os.VisualReviewTest
