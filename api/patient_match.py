"""
Fuzzy 2-of-3 patient verification + report view links for the customer portal.

Login (no OTP): patient gives three things and any TWO must match a bill —
  1. Patient name  (fuzzy: tolerates typos / missing middle name)
  2. Bill No  OR  Bill Date   (this pair counts as ONE factor)
  3. Phone number
Phone is often mistyped, so name is a real fallback: name + (bill no|date) still logs in.
Reads LIVE MSSQL (fresh reports; the API runs on the clinic PC).
"""
from __future__ import annotations

import json
import os
import re
import urllib.parse
from datetime import date, datetime
from typing import Any, Optional

from config import load_env
from db import fetch_all, mssql_conn, neon_conn


def report_view_base() -> str:
    load_env()
    return os.environ.get(
        "REPORT_VIEW_BASE", "https://report.anubhavlifecare.in/AKTIV"
    ).rstrip("/")


# ---------------------------------------------------------------- fuzzy name match
def _lev(a: str, b: str) -> int:
    a, b = a.lower(), b.lower()
    n, m = len(a), len(b)
    if n == 0:
        return m
    if m == 0:
        return n
    prev = list(range(m + 1))
    for i in range(1, n + 1):
        cur = [i] + [0] * m
        for j in range(1, m + 1):
            cost = 0 if a[i - 1] == b[j - 1] else 1
            cur[j] = min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
        prev = cur
    return prev[m]


def _sim(a: str, b: str) -> float:
    if not a or not b:
        return 0.0
    if a == b:
        return 1.0
    return 1.0 - _lev(a, b) / max(len(a), len(b))


def _tokens(s: str) -> list[str]:
    return [t for t in re.sub(r"[^a-zA-Z]+", " ", s or "").lower().split() if t]


def name_score(inp: str, db: str) -> float:
    """Fraction of input name tokens that fuzzily match a DB token.

    Tolerates typos (PRITI~PREETI) and a missing/extra middle name. Short tokens
    (initials like "P", "K") must match EXACTLY, else single letters match anything.
    """
    ti, td = _tokens(inp), _tokens(db)
    if not ti or not td:
        return 0.0
    hit = 0
    for x in ti:
        best = 0.0
        for y in td:
            s = 1.0 if x == y else (0.0 if (len(x) <= 2 or len(y) <= 2) else _sim(x, y))
            if s > best:
                best = s
        if best >= 0.6:
            hit += 1
    return round(hit / len(ti), 2)


# ---------------------------------------------------------------- helpers
def _norm_phone(p: Optional[str]) -> str:
    return re.sub(r"\D", "", p or "")[-10:]


_JUNK_PHONES = {
    "9999999999", "0000000000", "1111111111", "1234567890", "9876543210", "8888888888",
}


def _is_junk_phone(p: str) -> bool:
    d = _norm_phone(p)
    return len(d) < 10 or len(set(d)) == 1 or d in _JUNK_PHONES


def _bill_serial(bill_no: Optional[str]) -> str:
    return (bill_no or "").split("/")[-1].strip().lstrip("0")


def _parse_date(s: Optional[str]) -> Optional[date]:
    if not s:
        return None
    s = str(s).strip()
    for fmt in ("%Y-%m-%d", "%d/%m/%Y", "%d-%m-%Y"):
        try:
            return datetime.strptime(s, fmt).date()
        except ValueError:
            continue
    return None


def _view_item(name, bill_key, category_key, report_key, bill_no) -> dict:
    return {
        "BILL_KEY": str(bill_key),
        "CATEGORY_KEY": str(category_key),
        "REPORT_KEY": str(report_key),
        "ATTACHMENT": "0",
        "BILL_NO": str(bill_no or ""),
        "PATIENTNAME": str(name or ""),
    }


def build_view_link(name: str, bill_key, category_key, report_key, bill_no) -> str:
    return build_collated_view_link(name, bill_no, [_view_item(name, bill_key, category_key, report_key, bill_no)])


def build_collated_view_link(name: str, bill_no, items: list[dict]) -> str:
    """One LabReportPrint URL for a whole bill — the server renders ALL the given
    reports into a SINGLE collated PDF (this is how 'multiple PDFs' become one)."""
    q = "u=%s&d=0&p=0&pm=1&mp=1&json=%s" % (
        urllib.parse.quote(name or ""),
        urllib.parse.quote(json.dumps(items)),
    )
    return f"{report_view_base()}/LabReportPrint.aspx?{q}"


# ---------------------------------------------------------------- verification
NAME_MATCH_THRESHOLD = 0.6


def verify_customer(
    name: str,
    phone: str,
    bill_no: str = "",
    bill_date: Optional[str] = None,
) -> dict[str, Any]:
    """2-of-3 match. Returns the patient's matching bills and the canonical phone
    (recovered from a matched bill so the rest of the portal can key off phone)."""
    name = (name or "").strip()
    phone_n = _norm_phone(phone)
    phone_valid = bool(phone_n) and not _is_junk_phone(phone_n)
    serial = _bill_serial(bill_no)
    bdate = _parse_date(bill_date)

    clauses, params = [], []
    if phone_valid:
        clauses.append("RIGHT(REPLACE(REPLACE(REPLACE(ISNULL(PHONE,''),' ',''),'-',''),'+',''),10) = %s")
        params.append(phone_n)
    if bdate:
        clauses.append("CAST(BILLDATE AS date) = %s")
        params.append(bdate)
    if serial:
        clauses.append("BILL_NO LIKE %s")
        params.append("%/" + serial)
    if not clauses:
        return {"matched": False, "reason": "insufficient_input", "patient_name": "", "phone": phone_n, "bills": []}

    where = " OR ".join(clauses)
    sql = (
        "SELECT TOP 500 BILL_KEY AS bill_key, RTRIM(BILL_NO) AS bill_no, RTRIM(ISNULL(PHONE,'')) AS phone, "
        "RTRIM(ISNULL(PATIENTNAME,'')) AS patientname, CONVERT(varchar, BILLDATE, 103) AS billdate "
        f"FROM BILL_HEAD WHERE {where} ORDER BY BILL_KEY DESC"
    )
    with mssql_conn() as conn, conn.cursor() as cur:
        rows = fetch_all(cur, sql, tuple(params))

    matched, seen = [], set()
    canonical_phone = phone_n
    for r in rows:
        f_phone = 1 if (phone_valid and _norm_phone(r["phone"]) == phone_n) else 0
        f_bill = 1 if (
            (serial and _bill_serial(r["bill_no"]) == serial)
            or (bdate and r["billdate"] == bdate.strftime("%d/%m/%Y"))
        ) else 0
        f_name = 1 if (name and name_score(name, r["patientname"]) >= NAME_MATCH_THRESHOLD) else 0
        if (f_phone + f_bill + f_name) >= 2 and r["bill_key"] not in seen:
            seen.add(r["bill_key"])
            if not _is_junk_phone(r["phone"]):
                canonical_phone = _norm_phone(r["phone"]) or canonical_phone
            matched.append({
                "bill_key": r["bill_key"],
                "bill_no": r["bill_no"],
                "bill_date": r["billdate"],
                "patient_name": r["patientname"].strip(),
            })

    return {
        "matched": bool(matched),
        "patient_name": matched[0]["patient_name"] if matched else "",
        "phone": canonical_phone,
        "bills": matched,
    }


# ---------------------------------------------------------------- all-history list (My Reports)
_PHONE10 = r"right(regexp_replace(coalesce(%s,''),'\D','','g'),10)"


def customer_history(phone: str) -> dict[str, Any]:
    """All AKTIV visits under this phone since the clinic's founding (2022), from the
    STATIC Neon mirror — fast and works even when the clinic PC is off. Names may differ
    (relatives share a phone). Each visit carries a COLLATED view link (one PDF for the
    whole bill); the PDF itself is only fetched when the patient taps View."""
    ph = _norm_phone(phone)
    if len(ph) < 10:
        return {"phone": phone, "visits": []}

    ph_col = _PHONE10 % "h.phone"
    with neon_conn() as conn, conn.cursor() as cur:
        bills = fetch_all(
            cur,
            f"""
            SELECT h.bill_key,
                   rtrim(coalesce(h.patientname,'')) AS patient_name,
                   to_char(h.billdate,'DD Mon YYYY')  AS bill_date,
                   h.billdate                          AS raw_date,
                   rtrim(coalesce(h.bill_no,''))       AS bill_no,
                   string_agg(DISTINCT rtrim(t.testname), ', ') AS tests
            FROM bill_head h
            JOIN bill_test_dtls d ON d.bill_key = h.bill_key
            LEFT JOIN mast_test t ON t.test_key = d.test_key
            WHERE {ph_col} = %s
            GROUP BY h.bill_key, h.patientname, h.billdate, h.bill_no
            ORDER BY h.billdate DESC, h.bill_key DESC
            """,
            (ph,),
        )
        # confirmed report items (for the collated view link), all bills in one shot
        item_col = _PHONE10 % "h.phone"
        items = fetch_all(
            cur,
            f"""
            SELECT d.bill_key, d.category_key, d.report_key
            FROM bill_test_dtls d
            JOIN bill_head h ON h.bill_key = d.bill_key
            WHERE {item_col} = %s
              AND coalesce(d.report_key,0) > 0
              AND lower(d.confirm_report::text) IN ('1','true','t')
            """,
            (ph,),
        )

    by_bill: dict[Any, list[dict]] = {}
    for it in items:
        by_bill.setdefault(it["bill_key"], []).append(it)

    visits = []
    for b in bills:
        ready_items = [
            _view_item(b["patient_name"], b["bill_key"], it["category_key"], it["report_key"], b["bill_no"])
            for it in by_bill.get(b["bill_key"], [])
        ]
        visits.append({
            "bill_key": b["bill_key"],
            "patient_name": b["patient_name"],
            "bill_date": b["bill_date"],
            "bill_no": b["bill_no"],            # the ALC number
            "tests": b["tests"] or "",
            "ready": bool(ready_items),
            # collated single-PDF link (fetched only on tap); None until any report is ready
            "view_link": build_collated_view_link(b["patient_name"], b["bill_no"], ready_items) if ready_items else None,
        })
    return {"phone": ph, "count": len(visits), "visits": visits}
