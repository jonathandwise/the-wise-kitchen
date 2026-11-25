#!/bin/bash
# Runs the emulator test and keeps its output in results/e2e-log.txt (read from the test-results branch).
set -o pipefail
mkdir -p results
{
  echo "boot_completed=$(adb shell getprop sys.boot_completed)"
  echo "sdk=$(adb shell getprop ro.build.version.sdk) size=$(adb shell wm size)"
} >> results/e2e-log.txt 2>&1
python3 -u ci/e2e.py results "$1" 2>&1 | tee -a results/e2e-log.txt
