"""Hermetic tests; no network, credentials, Gradle, or GitHub mutations."""
import importlib.util
import pathlib
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("monitor", pathlib.Path(__file__).with_name("maintenance-monitor.py"))
assert SPEC is not None and SPEC.loader is not None
monitor = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(monitor)
A = "a" * 40
B = "b" * 40


class MonitorTests(unittest.TestCase):
    def test_changes_compare_content_not_clock(self):
        before = {"fork_head": A, "upstream_head": A, "public_advisory_ids": ["GHSA-abcd-efgh-ijkl"]}
        self.assertEqual(monitor.compare(before, dict(before)), {
            "fork_changed": False, "upstream_changed": False,
            "new_public_advisory_ids": [], "removed_public_advisory_ids": [],
        })
        after = {"fork_head": A, "upstream_head": B, "public_advisory_ids": ["GHSA-new1-new2-new3"]}
        result = monitor.compare(before, after)
        self.assertTrue(result["upstream_changed"])
        self.assertFalse(result["fork_changed"])
        self.assertEqual(result["new_public_advisory_ids"], ["GHSA-new1-new2-new3"])
        self.assertEqual(result["removed_public_advisory_ids"], ["GHSA-abcd-efgh-ijkl"])

    def test_api_errors_and_malformed_data_fail_closed(self):
        with patch.object(monitor, "command", side_effect=monitor.MonitorError("API failure")):
            with self.assertRaises(monitor.MonitorError):
                monitor.snapshot("owner/fork", "owner/upstream")
        with patch.object(monitor, "api", side_effect=[{"object": {"sha": A}}, {"object": {"sha": B}}, [{}]]):
            with self.assertRaises(monitor.MonitorError):
                monitor.snapshot("owner/fork", "owner/upstream")
        with patch.object(monitor, "api", side_effect=[{"object": {"sha": A}}, {"object": {"sha": B}}, [{}] * 100]):
            with self.assertRaises(monitor.MonitorError):
                monitor.snapshot("owner/fork", "owner/upstream")

    def test_no_external_error_details(self):
        with patch.object(monitor.subprocess, "run") as run:
            run.return_value.returncode = 1
            run.return_value.stderr = "token=secret"
            with self.assertRaises(monitor.MonitorError) as failure:
                monitor.command("gh", "api", "example")
            self.assertNotIn("secret", str(failure.exception))


if __name__ == "__main__":
    unittest.main()
