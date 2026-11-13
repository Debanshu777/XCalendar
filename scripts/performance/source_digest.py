#!/usr/bin/env python3
"""Hash a Git source tree, excluding only the merge evidence report itself.

Includes tracked file paths, modes and blob IDs (including CI and benchmark code).
An evidence-only commit preserves this digest; any other tracked change does not.
Untracked/dirty working files are deliberately not measured: benchmark clean commits.
"""
import argparse
import hashlib
import subprocess

EVIDENCE_PATH = b"performance-evidence/merge.json"


def source_digest(revision, cwd=None):
    # Resolve before passing to ls-tree; reject option injection and non-commit refs.
    commit = subprocess.check_output(
        ["git", "rev-parse", "--verify", "--end-of-options", revision + "^{commit}"],
        cwd=cwd, text=True).strip()
    tree = subprocess.check_output(["git", "ls-tree", "-r", "-z", "--full-tree", commit], cwd=cwd)
    records = [record for record in tree.split(b"\0") if record and
               record.split(b"\t", 1)[1] != EVIDENCE_PATH]
    return hashlib.sha256(b"xcalendar-source-v1\0" + b"\0".join(records)).hexdigest()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("revision")
    args = parser.parse_args()
    print(source_digest(args.revision))
