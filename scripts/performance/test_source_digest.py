import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from source_digest import source_digest


class SourceDigestTests(unittest.TestCase):
    def test_only_evidence_report_is_exempt(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            environment = dict(os.environ, GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull)

            def git(*args):
                return subprocess.check_output(["git", *args], cwd=root, env=environment,
                                               stderr=subprocess.PIPE, timeout=10)

            def commit():
                git("add", ".")
                git("-c", "user.name=Test", "-c", "user.email=test@example.invalid",
                    "-c", "commit.gpgsign=false", "commit", "-m", "test")
                return source_digest("HEAD", cwd=root)

            git("init", "--template=")
            (root / "app.kt").write_text("source")
            initial = commit()
            evidence = root / "performance-evidence"
            evidence.mkdir()
            (evidence / "merge.json").write_text("{}")
            self.assertEqual(initial, commit())
            (evidence / "merge.json").write_text('{"report": true}')
            self.assertEqual(initial, commit())
            (evidence / "other.json").write_text("{}")
            other = commit()
            self.assertNotEqual(initial, other)
            (root / "app.kt").rename(root / "renamed.kt")
            renamed = commit()
            self.assertNotEqual(other, renamed)
            (root / "renamed.kt").write_text("changed source")
            self.assertNotEqual(renamed, commit())

    def test_invalid_revision_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            script = Path(__file__).with_name("source_digest.py").resolve()
            result = subprocess.run([sys.executable, str(script), "invalid-revision"],
                                    cwd=directory, capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("not a git repository", result.stderr)
