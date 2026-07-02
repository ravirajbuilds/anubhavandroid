#!/usr/bin/env python3
"""Inspect Neon schema for AKTIV tables (no credentials printed)."""
import os
from pathlib import Path

def load_env():
    for name in (".env", "env"):
        p = Path(__file__).resolve().parent.parent / name
        if p.exists():
            for line in p.read_text().splitlines():
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    os.environ.setdefault(k.strip(), v.strip().strip('"'))

load_env()
url = os.environ.get("NEON_DATABASE_URL")
if not url:
    raise SystemExit("NEON_DATABASE_URL not set")

import psycopg2

conn = psycopg2.connect(url)
cur = conn.cursor()
cur.execute(
    "SELECT table_name FROM information_schema.tables "
    "WHERE table_schema='public' ORDER BY table_name"
)
tables = [r[0] for r in cur.fetchall()]
print(f"Found {len(tables)} tables")

targets = [
    "bill_head", "bill_test_dtls", "mast_test", "mast_refrdoctor",
    "mast_collcentre", "mast_patient", "apnt_head",
]
for t in targets:
    real = next((x for x in tables if x.lower() == t), None)
    if not real:
        print(f"\n=== {t.upper()} NOT FOUND ===")
        continue
    cur.execute(
        "SELECT column_name, data_type FROM information_schema.columns "
        "WHERE table_name=%s ORDER BY ordinal_position",
        (real,),
    )
    print(f"\n=== {real} ===")
    for col, dtype in cur.fetchall():
        print(f"  {col}: {dtype}")
    cur.execute(f'SELECT * FROM "{real}" LIMIT 1')
    row = cur.fetchone()
    if row:
        cols = [d[0] for d in cur.description]
        print("  SAMPLE:", dict(zip(cols, row)))

conn.close()
