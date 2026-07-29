"""W0-7 — does the running application match the source tree you think you deployed?

    py deployment_identity.py                                  # dev, http://localhost:8084
    py deployment_identity.py --url http://localhost:8091      # prod management port
    py deployment_identity.py --url ... --expect-commit <sha>

Exit 0 when the running artifact matches this working tree, 1 when it does not, 2 when the endpoint
could not be read at all. Intended for a post-deploy gate and for answering, in one command, the
question this programme got wrong once already:

    master carried a fix for a defect failing 33-35x/day in production, while production served a jar
    from six days earlier. Nothing anyone could query would have said so.

What it compares:

  * packaged migration head + count  — the artifact fingerprint. Same head AND same count means the
    jar that is running was built from a tree with the same migrations as this one. Head alone is not
    enough: a rebase that drops a migration below the head leaves the head untouched.
  * applied migration head          — the database. `pending` is the deployment gap, and a non-empty
    pending list on a box you believe is current is the exact failure this exists to surface.
  * build.commit                    — only meaningful when the build passed -Dgit.commit; "unknown"
    is reported as unknown rather than treated as a match, because an absent value must never look
    like a passing check.
"""
import argparse
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

MIGRATION = re.compile(r"^V(\d+(?:[._]\d+)*)__.*\.sql$")
DEFAULT_URL = "http://localhost:8084"
# .../TSS-GRC-Platform-Bible/Appendix/scripts/this.py -> repo root
MIGRATION_DIR = (Path(__file__).resolve().parents[3]
                 / "tss-grc-backend" / "src" / "main" / "resources"
                 / "db" / "migration" / "sqlserver")


def version_key(v: str):
    return tuple(int(p) if p.isdigit() else 0 for p in v.replace("_", ".").split("."))


def tree_migrations() -> tuple[list[str], list[str]]:
    """(parseable versions ascending, filenames Flyway cannot version).

    The second list is not decoration. Adding a file named V300a__x.sql to the tree while running
    this probe produced a confident MATCH — it is invisible on BOTH sides, because neither counts a
    name Flyway cannot parse. That is arguably correct (Flyway skips it, so it is not a migration)
    but a silent match on a file someone believes is a migration is the wrong answer to give, so both
    sides now report the set and disagreement is flagged.
    """
    if not MIGRATION_DIR.is_dir():
        sys.exit(f"[2] migration folder not found at {MIGRATION_DIR}")
    found, dead = [], []
    for f in sorted(MIGRATION_DIR.iterdir()):
        if not f.name.endswith(".sql") or not f.name.startswith("V"):
            continue
        m = MIGRATION.match(f.name)
        if m:
            found.append(m.group(1).replace("_", "."))
        else:
            dead.append(f.name)
    return sorted(found, key=version_key), dead


def fetch_info(base: str) -> dict:
    try:
        with urllib.request.urlopen(base.rstrip("/") + "/actuator/info", timeout=10) as r:
            return json.loads(r.read())
    except urllib.error.HTTPError as e:
        sys.exit(f"[2] /actuator/info returned {e.code} — is `info` in "
                 f"management.endpoints.web.exposure.include?")
    except Exception as e:                                        # noqa: BLE001
        sys.exit(f"[2] could not reach {base}/actuator/info: {e}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default=DEFAULT_URL, help=f"base URL (default {DEFAULT_URL})")
    ap.add_argument("--expect-commit", help="fail unless build.commit matches this")
    args = ap.parse_args()

    info = fetch_info(args.url)
    mig = info.get("migrations")
    if not isinstance(mig, dict):
        sys.exit("[2] /actuator/info has no `migrations` section — the running jar predates W0-7, "
                 "which is itself the answer: it is not this build.")

    tree, tree_dead = tree_migrations()
    tree_head, tree_count = (tree[-1] if tree else None), len(tree)
    run_head, run_count = mig.get("packagedHead"), mig.get("packagedCount")

    build = info.get("build") or {}
    print(f"  running   {args.url}")
    print(f"  build     version={build.get('version', '?')}  time={build.get('time', '?')}  "
          f"commit={build.get('commit', 'absent')}  branch={build.get('branch', 'absent')}")
    print(f"  artifact  packagedHead=V{run_head}  count={run_count}")
    print(f"  this tree packagedHead=V{tree_head}  count={tree_count}")
    print(f"  database  appliedHead={mig.get('appliedHead')}  "
          f"historyAvailable={mig.get('historyAvailable')}")
    run_dead = mig.get("unversionedFiles") or []
    if run_dead or tree_dead:
        print(f"  dead      {len(run_dead)} file(s) Flyway cannot version and therefore never "
              f"applies: {', '.join(run_dead) or 'none in the artifact'}")

    problems = []
    if str(run_head) != str(tree_head):
        problems.append(f"packaged head differs: running V{run_head}, tree V{tree_head} — the "
                        f"running jar was NOT built from this tree")
    elif run_count != tree_count:
        # Same head, different count: a migration below the head was added or removed. This is the
        # case a head-only check waves through, which is why count is compared at all.
        problems.append(f"same head V{run_head} but {run_count} migrations vs {tree_count} in the "
                        f"tree — a migration below the head differs")

    if sorted(run_dead) != sorted(tree_dead):
        problems.append(f"unversioned-file sets differ: artifact {sorted(run_dead)} vs tree "
                        f"{sorted(tree_dead)} — one of them has a file the other does not, and "
                        f"neither counts it as a migration")

    pending = mig.get("pending") or []
    if pending:
        problems.append(f"{len(pending)} migration(s) packaged but NOT applied: "
                        f"{', '.join('V' + p for p in pending[:12])}"
                        f"{' ...' if len(pending) > 12 else ''} — they apply on the next restart, "
                        f"so this database is not yet at the artifact's level")

    if args.expect_commit:
        got = build.get("commit")
        if not got or got == "unknown":
            problems.append("build.commit is unknown — this jar was built without "
                            "-Dgit.commit, so it cannot be identified by commit")
        elif not (got.startswith(args.expect_commit) or args.expect_commit.startswith(got)):
            problems.append(f"build.commit {got} != expected {args.expect_commit}")

    print()
    if problems:
        print("  MISMATCH")
        for p in problems:
            print(f"    - {p}")
        return 1
    print("  MATCH — the running artifact was built from this tree and its database is at its level")
    return 0


if __name__ == "__main__":
    sys.exit(main())
