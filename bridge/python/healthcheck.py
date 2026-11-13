#!/usr/bin/env python3
"""Pre-flight checks for the bundled runtime.

The app calls this after extraction. It answers one question: can this device
actually run the agent, or are we about to fail at the first prompt?
"""

from __future__ import annotations

import json
import os
import shutil
import socket
import sys
from pathlib import Path

MIN_FREE_BYTES = 250 * 1024 * 1024


def check_binary(path: str) -> dict:
    resolved = shutil.which(path) or (path if os.path.isfile(path) else None)
    return {"name": path, "ok": bool(resolved), "path": resolved}


def check_free_space(root: Path) -> dict:
    usage = shutil.disk_usage(root)
    return {
        "name": "free_space",
        "ok": usage.free >= MIN_FREE_BYTES,
        "free_mb": usage.free // (1024 * 1024),
        "required_mb": MIN_FREE_BYTES // (1024 * 1024),
    }


def check_writable(root: Path) -> dict:
    probe = root / ".ocm-write-probe"
    try:
        probe.write_text("ok")
        probe.unlink()
        return {"name": "writable", "ok": True}
    except OSError as exc:
        return {"name": "writable", "ok": False, "error": str(exc)}


def check_loopback() -> dict:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.bind(("127.0.0.1", 0))
        return {"name": "loopback", "ok": True}
    except OSError as exc:
        return {"name": "loopback", "ok": False, "error": str(exc)}


def main() -> int:
    root = Path(os.environ.get("OCM_WORK", "/root/work"))
    root.mkdir(parents=True, exist_ok=True)

    results = [
        check_binary("node"),
        check_binary("python3"),
        check_binary("git"),
        check_writable(root),
        check_free_space(root),
        check_loopback(),
    ]

    ok = all(item["ok"] for item in results)
    print(json.dumps({"ok": ok, "checks": results}, indent=2))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
