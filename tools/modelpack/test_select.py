#!/usr/bin/env python3
"""Tests for the model selector. Run: python3 -m pytest tools/modelpack -q"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from model_select import CATALOGUE, GB, parse_props, select, usable_ram_budget  # noqa: E402


def test_budget_is_a_third_of_ram():
    assert usable_ram_budget(9) == 3 * GB


def test_small_phone_gets_a_small_model():
    result = select(ram_gb=3, storage_free_gb=16)
    assert result["ok"] is True
    assert result["gb"] <= 1.0


def test_large_phone_gets_the_best_quality_that_fits():
    result = select(ram_gb=16, storage_free_gb=200)
    assert result["ok"] is True
    assert result["model_id"] == "qwen3-8b"


def test_storage_can_be_the_binding_constraint():
    result = select(ram_gb=16, storage_free_gb=2.2)
    assert result["ok"] is True
    assert result["gb"] <= 2.2


def test_no_fit_is_reported_not_guessed():
    result = select(ram_gb=1, storage_free_gb=1)
    assert result["ok"] is False
    assert "reason" in result


def test_catalogue_has_no_duplicate_ids():
    ids = [c.model_id for c in CATALOGUE]
    assert len(ids) == len(set(ids))


def test_parse_props_reads_getprop_output():
    text = "[ro.product.model]: [Pixel 7]\n[ro.boot.mem_total_kb]: [8192000]\n"
    props = parse_props(text)
    assert props["ro.product.model"] == "Pixel 7"
    assert props["ro.boot.mem_total_kb"] == "8192000"
