#!/usr/bin/env python3
"""Pick a model quantisation for a phone.

The app can only see RAM and cores at runtime. This tool sees the whole release
catalogue and answers the question the app cannot: which file should this device
download in the first place.

Usage:
    python3 select.py --ram-gb 8 --storage-free-gb 40
    python3 select.py --device-props props.txt
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass, asdict

GB = 1024 ** 3


@dataclass(frozen=True)
class Candidate:
    model_id: str
    params_b: float
    quant: str
    bytes: int
    quality: float  # relative, 0..1, hand scored

    @property
    def gb(self) -> float:
        return self.bytes / GB


CATALOGUE: tuple[Candidate, ...] = (
    Candidate("qwen3-0.6b", 0.6, "Q4_K_M", int(0.45 * GB), 0.55),
    Candidate("qwen3-1.7b", 1.7, "Q4_K_M", int(1.10 * GB), 0.68),
    Candidate("llama-3.2-3b", 3.0, "Q4_K_M", int(2.00 * GB), 0.76),
    Candidate("gemma-3-4b", 4.0, "Q4_K_M", int(2.50 * GB), 0.82),
    Candidate("qwen3-8b", 8.0, "Q4_K_M", int(5.00 * GB), 0.90),
)


def usable_ram_budget(ram_gb: float) -> int:
    """Bytes a model may occupy.

    A third of physical RAM. Less than that and Android's low-memory killer starts
    reaping the app mid-generation, which users report as "the app crashed".
    """
    return int(ram_gb * GB / 3)


def select(ram_gb: float, storage_free_gb: float) -> dict:
    budget = usable_ram_budget(ram_gb)
    storage = int(storage_free_gb * GB)

    fitting = [c for c in CATALOGUE if c.bytes <= budget and c.bytes <= storage]
    if not fitting:
        return {
            "ok": False,
            "reason": "no model fits; on-device is not viable on this device",
            "ram_gb": ram_gb,
            "storage_free_gb": storage_free_gb,
            "budget_bytes": budget,
        }

    best = max(fitting, key=lambda c: (c.quality, -c.bytes))
    return {
        "ok": True,
        "model_id": best.model_id,
        "quant": best.quant,
        "bytes": best.bytes,
        "gb": round(best.gb, 2),
        "quality": best.quality,
        "alternatives": [
            {"model_id": c.model_id, "gb": round(c.gb, 2), "quality": c.quality}
            for c in sorted(fitting, key=lambda c: -c.quality)
            if c.model_id != best.model_id
        ],
        "reason": f"largest model within a third of {ram_gb} GB of RAM",
    }


def parse_props(text: str) -> dict:
    """Reads `adb shell getprop` output into a dict."""
    props: dict[str, str] = {}
    for line in text.splitlines():
        line = line.strip()
        if not line.startswith("[") or "]" not in line:
            continue
        key, _, rest = line.partition("]: [")
        props[key.lstrip("[")] = rest.rstrip("]")
    return props


def main() -> int:
    parser = argparse.ArgumentParser(description="choose an on-device model")
    parser.add_argument("--ram-gb", type=float, default=8.0)
    parser.add_argument("--storage-free-gb", type=float, default=32.0)
    parser.add_argument("--device-props", help="file containing getprop output")
    args = parser.parse_args()

    ram_gb = args.ram_gb
    if args.device_props:
        with open(args.device_props, encoding="utf-8") as handle:
            props = parse_props(handle.read())
        mem_kb = props.get("ro.boot.mem_total_kb") or props.get("ro.total.ram")
        if mem_kb and mem_kb.isdigit():
            ram_gb = int(mem_kb) / 1024 / 1024

    print(json.dumps(select(ram_gb, args.storage_free_gb), indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
