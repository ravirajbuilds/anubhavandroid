import sys
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient

API_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(API_DIR))

import catalog  # noqa: E402
import main  # noqa: E402

ROWS = [
    {"test_key": 2, "testname": "  LIPID PROFILE ", "rate": 800, "category_name": "Pathology"},
    {"test_key": 1, "testname": "CBC (Complete Blood Count)", "rate": 350.0, "category_name": " Pathology "},
    {"test_key": 3, "testname": "USG THYROID", "rate": 1700, "category_name": None},
]


def _reset_cache():
    catalog._cache = None
    catalog._cached_at = 0.0


class CatalogBuildTests(unittest.TestCase):
    def setUp(self):
        _reset_cache()

    def tearDown(self):
        _reset_cache()

    def test_rows_are_trimmed_sorted_and_typed(self):
        with patch.object(catalog, "neon_conn"), patch.object(catalog, "fetch_all", return_value=ROWS):
            data = catalog.get_catalog()

        self.assertEqual(data["count"], 3)
        self.assertEqual(
            [t["name"] for t in data["tests"]],
            ["CBC (Complete Blood Count)", "LIPID PROFILE", "USG THYROID"],
        )
        self.assertEqual(data["tests"][0]["price"], 350.0)
        self.assertEqual(data["tests"][1]["category"], "Pathology")
        # A null category must arrive as an empty string, not the literal "None".
        self.assertEqual(data["tests"][2]["category"], "")

    def test_version_is_stable_for_unchanged_data_and_moves_when_it_changes(self):
        with patch.object(catalog, "neon_conn"), patch.object(catalog, "fetch_all", return_value=ROWS):
            first = catalog.get_catalog()["version"]
            _reset_cache()
            again = catalog.get_catalog()["version"]

        self.assertEqual(first, again)

        changed = [dict(ROWS[0], rate=900)] + ROWS[1:]
        _reset_cache()
        with patch.object(catalog, "neon_conn"), patch.object(catalog, "fetch_all", return_value=changed):
            self.assertNotEqual(first, catalog.get_catalog()["version"])

    def test_result_is_cached_between_calls(self):
        with patch.object(catalog, "neon_conn"), patch.object(
            catalog, "fetch_all", return_value=ROWS
        ) as fetch:
            catalog.get_catalog()
            catalog.get_catalog()

        self.assertEqual(fetch.call_count, 1)

    def test_force_bypasses_the_cache(self):
        with patch.object(catalog, "neon_conn"), patch.object(
            catalog, "fetch_all", return_value=ROWS
        ) as fetch:
            catalog.get_catalog()
            catalog.get_catalog(force=True)

        self.assertEqual(fetch.call_count, 2)


class CatalogEndpointTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(main.app)
        _reset_cache()

    def tearDown(self):
        _reset_cache()

    def test_full_payload_when_the_phone_has_no_version(self):
        with patch.object(catalog, "neon_conn"), patch.object(catalog, "fetch_all", return_value=ROWS):
            response = self.client.get("/api/catalog")

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertFalse(body["unchanged"])
        self.assertEqual(len(body["tests"]), 3)

    def test_known_version_skips_the_payload(self):
        with patch.object(catalog, "neon_conn"), patch.object(catalog, "fetch_all", return_value=ROWS):
            version = self.client.get("/api/catalog").json()["version"]
            response = self.client.get("/api/catalog", params={"known_version": version})

        body = response.json()
        self.assertTrue(body["unchanged"])
        self.assertEqual(body["tests"], [])
        # The count still comes back so the app can tell a no-op sync from an empty one.
        self.assertEqual(body["count"], 3)

    def test_stale_version_gets_the_full_payload(self):
        with patch.object(catalog, "neon_conn"), patch.object(catalog, "fetch_all", return_value=ROWS):
            response = self.client.get("/api/catalog", params={"known_version": "not-the-version"})

        body = response.json()
        self.assertFalse(body["unchanged"])
        self.assertEqual(len(body["tests"]), 3)

    def test_database_failure_is_a_500_not_a_stack_trace(self):
        with patch.object(catalog, "neon_conn", side_effect=RuntimeError("db offline")):
            response = self.client.get("/api/catalog")

        self.assertEqual(response.status_code, 500)
        self.assertEqual(response.json()["detail"], "Internal server error")


if __name__ == "__main__":
    unittest.main()
