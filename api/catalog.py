"""Full diagnostic-test catalog, served to the app.

The app used to ship the catalog as a bundled asset, which meant a price change
needed a Play Store release. It now syncs from here and caches the result in a
local SQLite table, so search runs in SQL on the phone and the network is only
touched when the catalog actually changed.

The payload is version-stamped: the app sends the version it already holds and
gets `unchanged: true` back when there is nothing to download.
"""
from __future__ import annotations

import hashlib
import json
import threading
import time
from typing import Any

from db import fetch_all, neon_conn

# The catalog moves at most a few times a month; re-querying Neon per phone is
# pure waste, so hold the built payload in process for a while.
CACHE_TTL_SECONDS = 15 * 60

_lock = threading.Lock()
_cache: dict[str, Any] | None = None
_cached_at: float = 0.0

CATALOG_SQL = """
    SELECT DISTINCT ON (mt.test_key)
        mt.test_key,
        mt.testname,
        COALESCE(bd.rate, 0) AS rate,
        mtc.category AS category_name
    FROM mast_test mt
    LEFT JOIN LATERAL (
        SELECT rate
        FROM bill_dtls
        WHERE test_key = mt.test_key
        ORDER BY bill_key DESC
        LIMIT 1
    ) bd ON TRUE
    LEFT JOIN mast_test_category mtc ON mtc.category_key = mt.category_key
    WHERE (mt.inactive IS NULL OR mt.inactive = 0)
      AND mt.testname IS NOT NULL
      AND btrim(mt.testname) <> ''
    ORDER BY mt.test_key, mt.testname
"""


def _build() -> dict[str, Any]:
    with neon_conn() as conn, conn.cursor() as cur:
        rows = fetch_all(cur, CATALOG_SQL)

    tests = [
        {
            "key": int(row["test_key"]),
            "name": (row["testname"] or "").strip(),
            "category": (row["category_name"] or "").strip(),
            "price": float(row["rate"] or 0),
        }
        for row in rows
    ]
    tests.sort(key=lambda t: t["name"].lower())

    # Version is a digest of the payload itself, so it changes exactly when the
    # catalog does — no separate bookkeeping to keep in step.
    digest = hashlib.sha1(
        json.dumps(tests, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()[:16]
    return {"version": digest, "count": len(tests), "tests": tests}


def get_catalog(force: bool = False) -> dict[str, Any]:
    global _cache, _cached_at
    with _lock:
        fresh = _cache is not None and (time.monotonic() - _cached_at) < CACHE_TTL_SECONDS
        if fresh and not force:
            return _cache
        built = _build()
        _cache = built
        _cached_at = time.monotonic()
        return built
