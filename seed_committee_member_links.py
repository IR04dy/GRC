# -*- coding: utf-8 -*-
"""Seed the DEV/DEMO roster→platform-user links (finding F2-26).

WHY THIS IS A SEED SCRIPT AND NOT A FLYWAY MIGRATION
====================================================
Every one of the 31 seeded committee roster rows has ``member_user_id NULL``, so
``CommitteeResolutionService.recordVote`` can never match a member to their own seat and
every vote has to be secretariat-entered with ``on_behalf = 1``. The self-vote path that
ADR-014 unlocked is therefore undemonstrable on the dev dataset.

The obvious fix — populate the column in V318 — would be a defect, not a fix:

* A migration runs on **every tenant**. Matching the roster string "CISO" to whichever
  user holds that job title, or "Mohammed Al-Ghamdi" to a user of that name, would invent
  who sits on a real customer's board committee. Seat identity is a governance fact
  established by a board resolution; the platform must not guess it. V315 refuses to
  backfill vote provenance and V316 refuses to backfill approval provenance for exactly
  this reason, and UNIFIED_IMPLEMENTATION_PLAN §7 forbids it outright.
* Name matching is the weakness DF-3 *removed* from the vote path. Re-introducing it as a
  resolution mechanism would undo that fix while appearing to complete it.

So V318 ships the schema (the third state + the index) and links nobody. Production links
are made one seat at a time by the secretariat through
``POST /api/v1/committees/registry/terms/{id}/link-user``, each one audited. This script
does the same thing for the **dev database only**, so the demo can show a member voting as
themselves — and it lives outside the artefact so it can never run against a customer.

WHAT IT DOES
============
Links the seats that correspond to real dev personas, matching the mapping recorded in the
F2-26 register entry:

    CISO seats   -> so_officer   (holds CISO + SECURITY_ANALYST; CISO has ADMIN:APPROVE
                                  after V317, so this is the seat that makes the self-vote
                                  path demonstrable)
    CCO seat     -> compliance   (COMPLIANCE_OFFICER + GRC_ADMINISTRATOR)
    CRO seats    -> rmanager     (RISK_MANAGER)

and declares the one genuinely-external seat as such, with a basis, so the linkage report
shows all three states rather than a single blended percentage:

    'External Auditor' (COM-AUDIT, standing invitee) -> EXTERNAL

Deliberately NOT seated: ``admin``. It is PLATFORM_ADMIN — a system account, not a
governance identity — and seating it would let the platform's own break-glass account cast
member votes.

Also deliberately not touched: the named individuals ("Abdullah Al-Saud",
"Mohammed Al-Ghamdi", "Layla Al-Otaibi", …). There is no dev user for them, and inventing
one would be the very thing this script exists to avoid. They stay UNLINKED, which is
honest, and the linkage report will say so.

Idempotent: re-running re-asserts the same links and reports "unchanged".

    Usage:  py seed_committee_member_links.py [--undo]
"""
from __future__ import annotations

import sys

import pyodbc

sys.stdout.reconfigure(encoding="utf-8")

_CONN_CANDIDATES = [
    "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
    "UID=sa;PWD=tss.123;Unicode_Results=Yes;",
    "DRIVER={ODBC Driver 18 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
    "UID=sa;PWD=tss.123;Encrypt=no;TrustServerCertificate=yes;Unicode_Results=Yes;",
]


def connect() -> pyodbc.Connection:
    last = None
    for cs in _CONN_CANDIDATES:
        try:
            return pyodbc.connect(cs, autocommit=True)
        except Exception as exc:  # noqa: BLE001
            last = exc
    raise SystemExit(f"Could not connect to SQL Server: {last}")


ORG = 1

# (roster member_name, dev username). Matched on the exact seat name, per committee-agnostic
# job title — a seat named "CISO" is the CISO's seat on whichever committee it appears.
SEAT_TO_USER = [
    ("CISO", "so_officer"),
    ("CCO",  "compliance"),
    ("CRO",  "rmanager"),
]

# (roster member_name, why this seat will never have a platform login)
EXTERNAL_SEATS = [
    ("External Auditor",
     "External audit firm partner — attends under CMA Corporate Governance Regulation "
     "Article 54 and holds no platform account."),
]


def main(undo: bool = False) -> None:
    cn = connect()
    c = cn.cursor()

    if undo:
        c.execute(
            "UPDATE admin.committee_member_terms "
            "SET member_user_id = NULL, no_platform_user = 0, no_platform_user_basis_en = NULL "
            "WHERE organisation_id = ?", ORG)
        print(f"undone — {c.rowcount} row(s) reset to UNLINKED")
        return

    users = {row[1]: row[0] for row in c.execute(
        "SELECT id, username FROM iam.users WHERE organisation_id = ? AND is_deleted = 0", ORG)}

    linked = skipped = unchanged = 0

    for seat_name, username in SEAT_TO_USER:
        uid = users.get(username)
        if uid is None:
            print(f"  ! no dev user '{username}' — '{seat_name}' left UNLINKED")
            continue

        rows = list(c.execute(
            "SELECT t.id, t.committee_id, c.code, t.member_user_id "
            "FROM admin.committee_member_terms t "
            "JOIN admin.committees c ON c.id = t.committee_id "
            "WHERE t.organisation_id = ? AND t.member_name = ? AND t.renewal_status = 'ACTIVE' "
            "ORDER BY t.committee_id", ORG, seat_name))
        if not rows:
            print(f"  · no ACTIVE seat named '{seat_name}'")
            continue

        # The service refuses two ACTIVE seats for one user on the SAME committee (it would
        # double-count them toward quorum). Across different committees it is normal — the
        # CISO legitimately sits on both the risk and BCM committees — so this mirrors the
        # service's rule rather than blanket-skipping duplicates.
        seen_committees: set[int] = set()
        for term_id, committee_id, code, current in rows:
            if committee_id in seen_committees:
                print(f"  ! '{seat_name}' appears twice on {code} — seat {term_id} left "
                      f"UNLINKED (two seats would count {username} twice toward quorum)")
                skipped += 1
                continue
            seen_committees.add(committee_id)

            if current == uid:
                unchanged += 1
                continue
            c.execute(
                "UPDATE admin.committee_member_terms "
                "SET member_user_id = ?, no_platform_user = 0, no_platform_user_basis_en = NULL "
                "WHERE id = ? AND organisation_id = ?", uid, term_id, ORG)
            print(f"  ✓ {code:16} '{seat_name}' -> {username} (user {uid}, term {term_id})")
            linked += 1

    for seat_name, basis in EXTERNAL_SEATS:
        c.execute(
            "UPDATE admin.committee_member_terms "
            "SET member_user_id = NULL, no_platform_user = 1, no_platform_user_basis_en = ? "
            "WHERE organisation_id = ? AND member_name = ? AND renewal_status = 'ACTIVE' "
            "  AND (no_platform_user = 0 OR no_platform_user_basis_en IS NULL)",
            basis, ORG, seat_name)
        if c.rowcount:
            print(f"  ✓ '{seat_name}' -> EXTERNAL ({c.rowcount} seat(s))")

    print(f"\nlinked {linked} · unchanged {unchanged} · skipped {skipped}")

    print("\n=== linkage per committee (what the report will show) ===")
    for row in c.execute("""
        SELECT c.code,
               COUNT(*)                                                        AS seats,
               SUM(CASE WHEN t.member_user_id IS NOT NULL THEN 1 ELSE 0 END)   AS linked,
               SUM(CAST(t.no_platform_user AS INT))                            AS ext,
               SUM(CASE WHEN t.member_user_id IS NULL AND t.no_platform_user = 0
                        THEN 1 ELSE 0 END)                                     AS unlinked,
               SUM(CASE WHEN t.is_voting_member = 1 AND t.member_user_id IS NOT NULL
                        THEN 1 ELSE 0 END)                                     AS voting_linked,
               SUM(CASE WHEN t.is_voting_member = 1 THEN 1 ELSE 0 END)          AS voting
        FROM admin.committee_member_terms t
        JOIN admin.committees c ON c.id = t.committee_id
        WHERE t.organisation_id = ? AND t.renewal_status = 'ACTIVE'
        GROUP BY c.code ORDER BY c.code""", ORG):
        print(f"  {row[0]:16} seats={row[1]:2}  linked={row[2]}  external={row[3]}  "
              f"unlinked={row[4]}  voting_linked={row[5]}/{row[6]}")


if __name__ == "__main__":
    main(undo="--undo" in sys.argv)
