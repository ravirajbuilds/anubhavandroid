#!/usr/bin/env python3
"""
Sync AKTIV SQL Server tables to Neon Postgres.
Run from repo root: python etl/sync.py
Requires etl/.env or root .env / env with MSSQL_* and NEON_DATABASE_URL.
"""
from __future__ import annotations

import logging
import os
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "api"))

from config import load_env, mssql_config, neon_url  # noqa: E402

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    handlers=[logging.StreamHandler(), logging.FileHandler(ROOT / "etl.log")],
)
log = logging.getLogger("etl")

TABLES = [
    "BILL_HEAD",
    "BILL_DTLS",
    "BILL_TEST_DTLS",
    "BILL_TERM_DTLS",
    "BILL_INC_DOCTOR",
    "BILL_INC_COLLCENTRE",
    "RECEIPT_HEAD",
    "REFUND_HEAD",
    "MAST_PATIENT",
    "MAST_PATIENT_CATEGORY",
    "MAST_REFRDOCTOR",
    "MAST_COLLCENTRE",
    "MAST_ORGANISATION",
    "MAST_INVESTIGATOR",
    "MAST_STAFF",
    "HR_MAST_STAFF",
    "MAST_TEST",
    "MAST_TEST_CATEGORY",
    "MAST_DEPARTMENT",
    "MAST_SUBDEPARTMENT",
    "OPD_BOOKING_HEAD",
    "OPD_BOOKING_DTLS",
    "APNT_HEAD",
    "APNT_DTLS",
    "APNT_RECEIPT_HEAD",
    "SYS_BRANCH",
    "SYS_COMPANY",
]


def pg_table_name(mssql_table: str) -> str:
    return mssql_table.lower()


def sync_table(mssql_cur, pg_cur, table: str) -> int:
    mssql_cur.execute(f"SELECT * FROM {table}")
    rows = mssql_cur.fetchall()
    if not rows:
        pg_cur.execute(f'TRUNCATE TABLE "{pg_table_name(table)}"')
        return 0

    cols = [d[0] for d in mssql_cur.description]
    pg_table = pg_table_name(table)
    col_list = ", ".join(f'"{c.lower()}"' for c in cols)
    placeholders = ", ".join(["%s"] * len(cols))

    pg_cur.execute(f'TRUNCATE TABLE "{pg_table}"')
    insert_sql = f'INSERT INTO "{pg_table}" ({col_list}) VALUES ({placeholders})'

    batch_size = 500
    for i in range(0, len(rows), batch_size):
        batch = rows[i : i + batch_size]
        for row in batch:
            pg_cur.execute(insert_sql, row)

    return len(rows)


def main() -> int:
    load_env()
    for var in ("MSSQL_HOST", "MSSQL_DB", "MSSQL_USER", "MSSQL_PASSWORD", "NEON_DATABASE_URL"):
        if not os.environ.get(var):
            log.error(
                "missing env vars: MSSQL_HOST, MSSQL_DB, MSSQL_USER, "
                "MSSQL_PASSWORD, NEON_DATABASE_URL. Copy env.example to .env and fill it in."
            )
            return 1

    try:
        import pymssql
        import psycopg2
    except ImportError:
        log.error("pymssql or psycopg2 not installed. Run: pip install -r etl/requirements.txt")
        return 1

    cfg = mssql_config()
    log.info("connecting to SQL Server %s:%s / %s ...", cfg["server"], cfg["port"], cfg["database"])
    mssql = pymssql.connect(
        server=cfg["server"],
        port=cfg["port"],
        user=cfg["user"],
        password=cfg["password"],
        database=cfg["database"],
        tds_version=cfg["tds_version"],
    )
    log.info("SQL Server connected.")

    log.info("connecting to Neon Postgres ...")
    pg = psycopg2.connect(neon_url())
    log.info("Neon connected.")

    started = time.time()
    log.info("sync started: %d table(s)", len(TABLES))
    ok = 0
    total_rows = 0

    mssql_cur = mssql.cursor()
    pg_cur = pg.cursor()
    for table in TABLES:
        t0 = time.time()
        try:
            count = sync_table(mssql_cur, pg_cur, table)
            pg.commit()
            ok += 1
            total_rows += count
            log.info("  %-24s %7d rows  (%.1fs)", table, count, time.time() - t0)
        except Exception as exc:
            pg.rollback()
            log.error("  %-24s FAILED: %s", table, exc)

    mssql.close()
    pg.close()
    elapsed = time.time() - started
    log.info(
        "sync %s: %d/%d tables, %d rows, %.1fs",
        "success" if ok == len(TABLES) else "partial",
        ok,
        len(TABLES),
        total_rows,
        elapsed,
    )
    return 0 if ok == len(TABLES) else 1


if __name__ == "__main__":
    raise SystemExit(main())
