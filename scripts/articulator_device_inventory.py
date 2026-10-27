#!/usr/bin/env python3
"""Collect read-only device inventory; this does not certify camera behavior."""

import argparse
import datetime
import json
from pathlib import Path
import shutil
import subprocess


def capture(command):
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=25)
        return {"command": command, "exit_code": result.returncode,
                "stdout": result.stdout, "stderr": result.stderr}
    except subprocess.TimeoutExpired:
        return {"command": command, "status": "timed_out_after_25_seconds"}
    except OSError as error:
        return {"command": command, "status": "unavailable", "reason": str(error)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path,
                        default=Path("build/reports/articulator-device/inventory.json"))
    args = parser.parse_args()
    adb = shutil.which("adb")
    if adb is None:
        candidate = Path.home() / "Library/Android/sdk/platform-tools/adb"
        if candidate.is_file():
            adb = str(candidate)
    commands = [["git", "rev-parse", "HEAD"], ["git", "status", "--short"],
                ["xcrun", "devicectl", "list", "devices", "--timeout", "15"],
                [adb or "adb", "devices", "-l"]]
    report = {
        "captured_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "scope": "Inventory only. No app interaction or hardware checks are implied.",
        "privacy": "May contain device names and identifiers; keep this local.",
        "results": [capture(command) for command in commands],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(f"Inventory written to {args.output}. Camera and microphone checks still require observation.")


if __name__ == "__main__":
    main()
