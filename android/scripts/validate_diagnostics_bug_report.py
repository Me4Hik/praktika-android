# -*- coding: utf-8 -*-
"""Accelerated-only validation for diagnostics bug report MVP."""
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

# 14.08.2026 DB Refactoring cursor by Me4Hik START - make diagnostics validator portable
ROOT = Path(__file__).resolve().parents[1]


def resolve_adb() -> str:
    which = shutil.which("adb")
    if which:
        return which
    for env_name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        root = os.environ.get(env_name)
        if root:
            candidate = Path(root) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
            if candidate.exists():
                return str(candidate)
    local_props = ROOT / "local.properties"
    if local_props.exists():
        for line in local_props.read_text(encoding="utf-8").splitlines():
            if line.strip().startswith("sdk.dir="):
                raw = line.split("=", 1)[1].strip()
                decoded = raw.replace("\\\\", "\\").replace("\\:", ":")
                candidate = Path(decoded) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
                if candidate.exists():
                    return str(candidate)
    raise FileNotFoundError(
        "adb not found. Install platform-tools and set PATH, ANDROID_HOME, ANDROID_SDK_ROOT, or sdk.dir in local.properties."
    )


ADB = resolve_adb()
PKG = "com.me4hik.praktika.accelerated"
ACTIVITY = f"{PKG}/com.me4hik.praktika.MainActivity"
OUT_DIR = ROOT / "app" / "build" / "outputs" / "diagnostics-validation"
# 14.08.2026 DB Refactoring cursor by Me4Hik END

def adb(*args: str, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run([ADB, *args], check=check, capture_output=True, text=True)


def unlock_device() -> None:
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    time.sleep(0.5)
    adb("shell", "wm", "dismiss-keyguard", check=False)
    adb("shell", "input", "swipe", "720", "2500", "720", "900", "300", check=False)
    time.sleep(1)


def scroll_down() -> None:
    adb("shell", "input", "swipe", "720", "2200", "720", "900", "400")
    time.sleep(1.5)


def ui_dump() -> str:
    local_ui = OUT_DIR / "_ui.xml"
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    adb("pull", "/sdcard/ui.xml", str(local_ui))
    return local_ui.read_text(encoding="utf-8")


def tap_text(text: str, xml: str) -> bool:
    pattern = rf'text="{re.escape(text)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    match = re.search(pattern, xml)
    if not match:
        return False
    x = (int(match.group(1)) + int(match.group(3))) // 2
    y = (int(match.group(2)) + int(match.group(4))) // 2
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(2)
    return True


def tap_content_desc(desc: str, xml: str) -> bool:
    pattern = rf'content-desc="{re.escape(desc)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    match = re.search(pattern, xml)
    if not match:
        return False
    x = (int(match.group(1)) + int(match.group(3))) // 2
    y = (int(match.group(2)) + int(match.group(4))) // 2
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(2)
    return True


def type_text(text: str) -> None:
    escaped = text.replace(" ", "%s")
    adb("shell", "input", "text", escaped)
    time.sleep(1)


def pull_diagnostics() -> dict:
    events_path = OUT_DIR / "events.jsonl"
    reports_dir = OUT_DIR / "reports"
    reports_dir.mkdir(parents=True, exist_ok=True)
    adb("shell", "run-as", PKG, "cat", "files/diagnostics/events.jsonl", check=False)
    result = adb("shell", "run-as", PKG, "cat", "files/diagnostics/events.jsonl", check=False)
    if result.returncode == 0 and result.stdout.strip():
        events_path.write_text(result.stdout, encoding="utf-8")
    report_list = adb("shell", "run-as", PKG, "ls", "files/diagnostics/reports", check=False)
    report_files = []
    if report_list.returncode == 0:
        for name in report_list.stdout.strip().split():
            if name.startswith("report-"):
                report_files.append(name)
                pulled = adb(
                    "shell", "run-as", PKG, "cat", f"files/diagnostics/reports/{name}",
                    check=False,
                )
                if pulled.returncode == 0:
                    (reports_dir / name).write_text(pulled.stdout, encoding="utf-8")
    return {
        "events_file_exists": events_path.exists() and events_path.stat().st_size > 0,
        "event_count": len([line for line in events_path.read_text(encoding="utf-8").splitlines() if line.strip()]) if events_path.exists() else 0,
        "event_names": [
            json.loads(line).get("name")
            for line in events_path.read_text(encoding="utf-8").splitlines()
            if line.strip()
        ] if events_path.exists() else [],
        "report_files": report_files,
    }


def open_settings(xml: str) -> str:
    if tap_text("Настройки", xml):
        time.sleep(2)
        return ui_dump()
    if tap_content_desc("Настройки", xml):
        return ui_dump()
    adb("shell", "input", "tap", "720", "1800")
    time.sleep(2)
    return ui_dump()


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    # 14.08.2026 DB Refactoring cursor by Me4Hik START - make diagnostics validator portable
    apk = ROOT / "app" / "build" / "outputs" / "apk" / "accelerated" / "debug" / "app-accelerated-debug.apk"
    # 14.08.2026 DB Refactoring cursor by Me4Hik END
    if not apk.exists():
        print(f"FAIL: APK not found at {apk}", file=sys.stderr)
        sys.exit(1)

    adb("install", "-r", str(apk))
    unlock_device()
    adb("shell", "am", "force-stop", PKG)
    time.sleep(1)
    adb("shell", "am", "start", "-n", ACTIVITY)
    time.sleep(6)
    unlock_device()

    xml = ui_dump()
    if PKG not in xml:
        unlock_device()
        time.sleep(2)
        xml = ui_dump()

    xml = open_settings(xml)
    if "Сообщить о проблеме" not in xml:
        scroll_down()
        xml = ui_dump()

    if not tap_text("Сообщить о проблеме", xml):
        print("FAIL: bug report row not found", file=sys.stderr)
        sys.exit(1)
    xml = ui_dump()
    if not tap_text("Отмена", xml):
        print("FAIL: cancel button not found", file=sys.stderr)
        sys.exit(1)

    xml = ui_dump()
    if not tap_text("Сообщить о проблеме", xml):
        print("FAIL: bug report row not found on second open", file=sys.stderr)
        sys.exit(1)
    xml = ui_dump()
    adb("shell", "input", "tap", "540", "900")
    time.sleep(1)
    type_text("diag test comment")
    xml = ui_dump()
    if not tap_text("Отправить", xml):
        print("FAIL: send button not found", file=sys.stderr)
        sys.exit(1)
    time.sleep(3)

    diag = pull_diagnostics()
    print(json.dumps(diag, ensure_ascii=False, indent=2))

    required_events = {"process_start", "activity_create", "screen_open"}
    found = set(diag["event_names"])
    missing = required_events - found
    if missing:
        print(f"WARN: missing expected events: {sorted(missing)}")

    if not diag["report_files"]:
        print("FAIL: no local report file created", file=sys.stderr)
        sys.exit(1)

    print("OK: diagnostics bug report validation completed")


if __name__ == "__main__":
    main()
