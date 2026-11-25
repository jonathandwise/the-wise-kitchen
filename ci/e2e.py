#!/usr/bin/env python3
"""Runs Wise Kitchen on a phone emulator and takes screenshots of the main screens.

Usage: e2e.py <results-dir> <apk>
Opens screens through launch intents (extras: open, screen, query, theme, textScale), waits for
the app's own log lines (tag "WiseKitchen"), and writes pass/fail checks to <results-dir>/report.txt.
"""
import os
import subprocess
import sys
import time

OUT = sys.argv[1]
APK = sys.argv[2]
PKG = "com.wisekitchen.app"
ACT = f"{PKG}/.ui.MainActivity"

os.makedirs(OUT, exist_ok=True)
checks = []
shots = 0


def sh(cmd, timeout=120):
    try:
        return subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout, errors="replace").stdout
    except subprocess.TimeoutExpired:
        return ""


def say(msg):
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


def shot(name):
    global shots
    shots += 1
    path = os.path.join(OUT, f"{shots:02d}-{name}.png")
    subprocess.run(f"adb exec-out screencap -p > '{path}'", shell=True, timeout=60)
    say(f"screenshot {os.path.basename(path)}")


def app_log():
    return [l for l in sh("adb logcat -d -v time -s WiseKitchen:I").splitlines() if "WiseKitchen" in l]


def wait_log(text, timeout=30, since=0):
    end = time.time() + timeout
    while time.time() < end:
        for line in app_log()[since:]:
            if text in line:
                return line
        time.sleep(1)
    return None


def check(name, ok, detail=""):
    checks.append((name, bool(ok), detail))
    say(f"{'PASS' if ok else 'FAIL'}: {name} {detail}")


def start(**extras):
    args = " ".join(
        f"--ef {k} {v}" if isinstance(v, float) else f"--es {k} '{v}'" for k, v in extras.items()
    )
    sh(f"adb shell am start -n {ACT} {args}")


def swipe_up():
    sh("adb shell input swipe 540 1900 540 700 400")
    time.sleep(1.2)


def open_item(item_id, name, scrolls=1, **extras):
    n = len(app_log())
    start(open=item_id, **extras)
    ok = wait_log(f"opened {item_id}", since=n)
    check(f"opens {item_id}", ok)
    time.sleep(2)
    shot(name)
    for i in range(scrolls):
        swipe_up()
        shot(f"{name}-scrolled{i + 1}")


def main():
    sh("adb wait-for-device")
    sh("adb shell settings put global window_animation_scale 0")
    out = sh(f"adb install -r '{APK}'", timeout=240)
    check("installs", "Success" in out, out.strip()[-200:])
    sh("adb logcat -c")

    sh(f"adb shell am start -W -n {ACT}")
    check("loads recipes", wait_log("recipes loaded", timeout=40))
    time.sleep(4)
    shot("home")
    swipe_up()
    shot("home-scrolled")

    open_item("air-fryer-chicken-tenders", "recipe-tenders", scrolls=2, textScale=1.25)
    open_item("garlic-herb-butter-roast-chicken", "recipe-big-text", scrolls=1, textScale=2.0)
    open_item("dim-sum-chicken-feet-pressure-cooker", "recipe-substeps", scrolls=2, textScale=1.25)
    open_item("sous-vide-cheat-sheet", "guide-tables", scrolls=2)

    start(screen="chapter/poultry")
    time.sleep(3)
    shot("chapter-poultry")

    start(screen="search", query="miso")
    time.sleep(3)
    shot("search-miso")
    sh("adb shell input keyevent 4")  # hide keyboard
    time.sleep(1)

    open_item("peking-duck", "recipe-dark", scrolls=1, theme="DARK")
    start(screen="settings", theme="DARK")
    time.sleep(3)
    shot("settings-dark")
    start(screen="settings", theme="LIGHT")
    time.sleep(3)
    shot("settings-light")

    crashes = [l for l in sh("adb logcat -d -b crash").splitlines() if PKG in l or "FATAL" in l]
    check("no crashes", not crashes, " | ".join(crashes[:5]))
    log = app_log()
    check("checked GitHub for recipes", any("recipes up to date" in l or "recipes updated" in l or "recipes check failed" in l for l in log))

    with open(os.path.join(OUT, "app-log.txt"), "w") as f:
        f.write("\n".join(log))
    with open(os.path.join(OUT, "logcat.txt"), "w") as f:
        f.write(sh("adb logcat -d -v time *:W"))
    with open(os.path.join(OUT, "report.txt"), "w") as f:
        for name, ok, detail in checks:
            f.write(f"{'PASS' if ok else 'FAIL'}  {name}  {detail}\n")
    failed = [c for c in checks if not c[1]]
    say(f"{len(checks) - len(failed)}/{len(checks)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
