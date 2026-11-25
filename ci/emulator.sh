#!/bin/bash
# Boots an Android 13 phone emulator, runs ci/e2e.py, and keeps every step's output in results/.
# Usage: ci/emulator.sh <apk>
set -u
mkdir -p results
LOG=results/emulator-setup.txt
exec > >(tee -a "$LOG") 2>&1

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
export PATH="$SDK/cmdline-tools/latest/bin:$SDK/emulator:$SDK/platform-tools:$PATH"
IMG="system-images;android-33;google_apis;x86_64"
echo "SDK=$SDK"; ls -l /dev/kvm || echo "no /dev/kvm"

yes | sdkmanager --licenses > /dev/null 2>&1
sdkmanager --install "platform-tools" "emulator" "$IMG" | tail -3 || { echo "sdkmanager failed"; exit 1; }
echo no | avdmanager create avd -n phone -k "$IMG" -d pixel_6 --force || { echo "avdmanager failed"; exit 1; }

nohup emulator -avd phone -no-window -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect \
  -camera-back none -memory 3072 > results/emulator-log.txt 2>&1 &
EMU=$!

adb start-server
echo "waiting for boot..."
for i in $(seq 1 180); do
  if ! kill -0 $EMU 2>/dev/null; then echo "emulator exited"; tail -40 results/emulator-log.txt; exit 1; fi
  if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then echo "booted after $((i*5))s"; break; fi
  sleep 5
done
[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] || { echo "boot timed out"; tail -40 results/emulator-log.txt; exit 1; }
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell input keyevent 82
sleep 5

python3 -u ci/e2e.py results "$1"
STATUS=$?
adb emu kill || true
exit $STATUS
