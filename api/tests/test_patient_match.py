import sys
import types
import unittest
from datetime import date
from pathlib import Path

API_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(API_DIR))

# Stub the DB/config layers so patient_match imports without a DB driver (psycopg2);
# the functions under test are pure and never touch the database.
if "db" not in sys.modules:
    _db = types.ModuleType("db")
    _db.fetch_all = lambda *a, **k: []
    _db.mssql_conn = None
    _db.neon_conn = None
    sys.modules["db"] = _db
if "config" not in sys.modules:
    _cfg = types.ModuleType("config")
    _cfg.load_env = lambda *a, **k: None
    sys.modules["config"] = _cfg

import patient_match as pm  # noqa: E402


def _row(bill_no, billdate, name="PRITI DAS", phone=""):
    return {"bill_no": bill_no, "billdate": billdate, "patientname": name, "phone": phone}


class BillMonthTests(unittest.TestCase):
    def test_bill_month_extracts_year_month(self):
        self.assertEqual(pm._bill_month("2024/01/ALC/003"), "2024/01")
        self.assertEqual(pm._bill_month("2024/12/ALC/117"), "2024/12")

    def test_bill_month_blank_for_malformed(self):
        self.assertEqual(pm._bill_month(""), "")
        self.assertEqual(pm._bill_month("ALC/3"), "")

    def test_bill_serial_strips_prefix_and_padding(self):
        self.assertEqual(pm._bill_serial("2024/01/ALC/003"), "3")
        self.assertEqual(pm._bill_serial("2024/02/ALC/3"), "3")


class SerialMonthScopingTests(unittest.TestCase):
    """ALC serials reset to 001 every month; a serial+name must not resolve to a
    same-serial bill from a *different* month when the bill date is known."""

    def test_wrong_month_serial_rejected_when_date_given(self):
        # Patient asks for January bill (serial 3); a February bill also has serial 3.
        feb = _row("2024/02/ALC/003", "15/02/2024")
        score = pm._score_row(
            feb, name="PRITI DAS", phone_n="", phone_valid=False,
            serial="3", bdate=date(2024, 1, 20), month_prefix="2024/01",
        )
        self.assertEqual(score, 1)  # name only — serial does NOT count (wrong month)

    def test_correct_month_serial_counts(self):
        jan = _row("2024/01/ALC/003", "20/01/2024")
        score = pm._score_row(
            jan, name="PRITI DAS", phone_n="", phone_valid=False,
            serial="3", bdate=date(2024, 1, 20), month_prefix="2024/01",
        )
        self.assertEqual(score, 2)  # name + in-month serial

    def test_serial_without_date_is_month_agnostic(self):
        # No date -> month unknown -> serial still matches (backward compatible path).
        feb = _row("2024/02/ALC/003", "15/02/2024")
        score = pm._score_row(
            feb, name="PRITI DAS", phone_n="", phone_valid=False,
            serial="3", bdate=None, month_prefix=None,
        )
        self.assertEqual(score, 2)

    def test_exact_date_still_counts_as_bill_factor(self):
        jan = _row("2024/01/ALC/007", "20/01/2024")
        score = pm._score_row(
            jan, name="PRITI DAS", phone_n="", phone_valid=False,
            serial="", bdate=date(2024, 1, 20), month_prefix="2024/01",
        )
        self.assertEqual(score, 2)  # name + exact bill date


if __name__ == "__main__":
    unittest.main()
