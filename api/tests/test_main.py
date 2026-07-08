import sys
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient

API_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(API_DIR))

import main  # noqa: E402
from auth import AuthUser  # noqa: E402


class BackendSmokeTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(main.app)

    def test_health(self):
        response = self.client.get("/health")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["status"], "ok")

    def test_login_returns_authenticated_user(self):
        user = AuthUser(
            user_key=7,
            userid="reception",
            username="Reception",
        )

        with patch.object(main, "authenticate", return_value=user):
            response = self.client.post(
                "/api/auth/login",
                json={"userid": "reception", "password": "secret"},
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(
            response.json(),
            {
                "success": True,
                "user_key": 7,
                "userid": "reception",
                "username": "Reception",
            },
        )

    def test_login_rejects_invalid_credentials(self):
        with patch.object(main, "authenticate", side_effect=ValueError("bad login")):
            response = self.client.post(
                "/api/auth/login",
                json={"userid": "x", "password": "wrong"},
            )

        self.assertEqual(response.status_code, 401)
        self.assertEqual(response.json()["detail"], "bad login")

    def test_tests_endpoint_maps_backend_errors(self):
        with patch.object(main, "search_tests", side_effect=RuntimeError("db offline")):
            response = self.client.get("/api/tests")

        self.assertEqual(response.status_code, 500)
        self.assertEqual(response.json()["detail"], "Internal server error")

    def test_customer_profile_requires_identifier(self):
        response = self.client.get("/api/customer/profile")

        self.assertEqual(response.status_code, 400)

    def test_customer_profile_returns_profile(self):
        expected = {
            "found": True,
            "patient_name": "Test Patient",
            "phone": "9230755875",
        }

        with patch.object(main, "get_customer_profile", return_value=expected):
            response = self.client.get(
                "/api/customer/profile",
                params={"phone": "9230755875"},
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), expected)


if __name__ == "__main__":
    unittest.main()
