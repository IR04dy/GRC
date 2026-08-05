# -*- coding: utf-8 -*-
"""Seed the DEV/DEMO voting roster for the CAB and ECAB authority gates.

THE ERROR THIS FIXES, MEASURED 2026-08-05 IN DEV
================================================
Every boot logs, and every CAB-gated approval fails on:

    [change-governance] 6 authority gate(s) demand a committee but none can authorise —
    approvals routed to them now FAIL CLOSED (ADR-017 D-2)

That warning is the platform working. The fault is the data: ``admin.committee_gate_map``
points both gates at the only two committees in organisation 1 that have **zero** member
terms::

    CAB  -> committee 13  "IT Steering Committee"   0 terms, 0 active voters
    ECAB -> committee 12  "Executive Committee"     0 terms, 0 active voters

meanwhile Board of Directors carries 6 active voters, Risk Management 5, BCM Steering 5,
Compliance 4, Audit 3. The map was written on 2026-07-25 pointing at committees nobody ever
staffed, so ``ChangeGovernanceHealthService``'s configured-predicate — which requires at
least one ``renewal_status='ACTIVE'`` **and** ``is_voting_member=1`` term — has never been
satisfiable for either gate.

The blast radius is the whole change-approval path: **11 of the 12** authorisation-matrix
cells in organisation 1 route to CAB or ECAB. Only ``STANDARD/LOW`` is AUTO. So in dev, no
NORMAL, EMERGENCY, or non-LOW STANDARD change could be approved at all — which is how this
surfaced: it refused CE-15's own live probe at ``CAB_APPROVED`` with a 409.

WHY THIS IS A SEED SCRIPT AND NOT A FLYWAY MIGRATION
====================================================
Identical reasoning to ``seed_committee_member_links.py`` (F2-26), and to V357's refusal to
backfill: **a committee seat is a governance fact established by a board resolution.** A
migration runs on every tenant, so inserting people onto a customer's Change Advisory Board
would fabricate appointments that no resolution ever made — the exact defect V315, V316 and
V357 each refuse, and that UNIFIED_IMPLEMENTATION_PLAN §7 forbids outright.

In production the remedy is administrative and already built: map the gate to a committee
that *is* staffed, or appoint members to the mapped one. F2-96 made the platform say which
of the three faults applies (``ROSTER_EMPTY`` here, not ``NOT_MAPPED``) precisely so the
operator is sent to the right screen. This script does that appointment for the **dev
database only**, so the CAB path is demonstrable.

⚠ WHAT THIS SCRIPT DOES DIFFERENTLY FROM THE F2-26 ONE
=======================================================
That one *links* existing seats to users. These two committees have no seats at all, so this
one *creates* terms — and creates them **already linked** (``member_user_id`` set). That is
CE-15's lesson applied at the point of writing rather than repaired afterwards: all 37
existing dev roster rows carry ``member_user_id NULL``, so nothing that compares a voter to
an acting principal can decide anything about them. A seat that is not a user cannot
participate in quorum-with-recusal, in self-review refusal, or in "did this member vote
themselves". Seeding unlinked seats would have cleared the warning and left the gate unable
to answer any question about who voted.

Seats: the three personas that actually hold ``CHANGE:APPROVE`` in ``iam.role_permissions``
— rmanager, compliance, so_officer. Three distinct linked voters per gate, so the two cells
requiring ``min_approvals=2`` can be met by two different people. ⚠ Deliberately NOT
``admin``: it holds no CHANGE:APPROVE grant and passes only because PLATFORM_ADMIN bypasses
in code, so a roster built on admin would prove nothing about authorisation.

Idempotent: re-running changes nothing. Safe to run repeatedly.
"""
import sys

import pyodbc

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

CS = ("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost,1433;DATABASE=TSS_GRC;"
      "UID=sa;PWD=tss.123;TrustServerCertificate=yes")
ORG = 1

# (committee_id, gate, [(username, role, is_chair)])
# The chair is seated first on each so `quorum_requires_chair` (S6c — shipped inert, and
# switchable per tenant) can be satisfied if a tenant ever turns it on. Without a chair on the
# roster, enabling that flag would make the gate unquorate and fail closed all over again.
ROSTER = [
    (13, "CAB", [
        ("rmanager",   "CAB Chair",              True),
        ("compliance", "CAB Member — Compliance", False),
        ("so_officer", "CAB Member — Security",   False),
    ]),
    (12, "ECAB", [
        ("so_officer", "ECAB Chair",              True),
        ("rmanager",   "ECAB Member — Risk",      False),
        ("compliance", "ECAB Member — Compliance", False),
    ]),
]


def main():
    cx = pyodbc.connect(CS, autocommit=False)
    cu = cx.cursor()

    cu.execute("SELECT id, username, full_name_en FROM iam.users "
               "WHERE organisation_id = ? AND is_deleted = 0", ORG)
    users = {r[1]: (r[0], r[2]) for r in cu.fetchall()}

    print("=== before ===")
    show_state(cu)

    created = skipped = 0
    for committee_id, gate, seats in ROSTER:
        cu.execute("SELECT name_en, status FROM admin.committees WHERE id = ? AND organisation_id = ?",
                   committee_id, ORG)
        row = cu.fetchone()
        if not row:
            raise SystemExit("committee %d does not exist in org %d — refusing to guess" % (committee_id, ORG))
        name, status = row
        if status == "DISSOLVED":
            raise SystemExit("committee %d (%s) is DISSOLVED — appointing to it would be wrong; "
                             "re-map the %s gate instead" % (committee_id, name, gate))
        print("\n%s -> committee %d (%s)" % (gate, committee_id, name))

        for username, role, is_chair in seats:
            if username not in users:
                raise SystemExit("dev user '%s' not found — refusing to invent a member" % username)
            user_id, full_name = users[username]

            # Idempotency key: one ACTIVE term per (committee, user). A second row for the same
            # person is a real thing in governance (a renewed term), so this must not key on the
            # name alone or a re-run would look like a re-appointment.
            cu.execute("""SELECT COUNT(*) FROM admin.committee_member_terms
                           WHERE committee_id = ? AND organisation_id = ?
                             AND member_user_id = ? AND renewal_status = 'ACTIVE'""",
                       committee_id, ORG, user_id)
            if cu.fetchone()[0]:
                print("    = %-12s already seated (%s)" % (username, role))
                skipped += 1
                continue

            cu.execute("""INSERT INTO admin.committee_member_terms
                            (organisation_id, committee_id, member_name, member_user_id, role,
                             is_voting_member, is_independent, appointed_at, term_starts_at,
                             term_ends_at, term_number, renewal_status, created_at,
                             no_platform_user)
                          VALUES (?, ?, ?, ?, ?, 1, 0, CAST(GETDATE() AS DATE),
                                  CAST(GETDATE() AS DATE), DATEADD(year, 3, CAST(GETDATE() AS DATE)),
                                  1, 'ACTIVE', SYSDATETIME(), 0)""",
                       ORG, committee_id, full_name or username, user_id, role)
            print("    + %-12s %-26s user_id=%s%s"
                  % (username, role, user_id, "  (chair)" if is_chair else ""))
            created += 1

    cx.commit()
    print("\ncreated %d term(s), %d already present" % (created, skipped))

    print("\n=== after ===")
    ok = show_state(cu)
    cx.close()
    if not ok:
        raise SystemExit("a gate is still unconfigured — see above")
    print("\nboth authority gates can now authorise; the ADR-017 D-2 fail-closed warning "
          "should not reappear on the next boot")


def show_state(cu):
    """Print each gate against the service's own configured-predicate, verbatim."""
    all_ok = True
    for gate in ("CAB", "ECAB"):
        cu.execute("""SELECT g.committee_id, c.name_en,
                        (SELECT COUNT(*) FROM admin.committee_member_terms t
                          WHERE t.committee_id = c.id AND t.organisation_id = c.organisation_id
                            AND t.renewal_status = 'ACTIVE' AND t.is_voting_member = 1),
                        (SELECT COUNT(*) FROM admin.committee_member_terms t
                          WHERE t.committee_id = c.id AND t.organisation_id = c.organisation_id
                            AND t.renewal_status = 'ACTIVE' AND t.is_voting_member = 1
                            AND t.member_user_id IS NOT NULL)
                      FROM admin.committee_gate_map g
                      JOIN admin.committees c ON c.id = g.committee_id
                                             AND c.organisation_id = g.organisation_id
                      WHERE g.organisation_id = ? AND g.gate = ? AND g.is_deleted = 0
                        AND c.is_deleted = 0 AND c.status <> 'DISSOLVED'""", ORG, gate)
        row = cu.fetchone()
        if not row:
            print("  %-5s NOT MAPPED to a live committee" % gate)
            all_ok = False
            continue
        cid, name, voters, linked = row
        ok = voters > 0
        all_ok &= ok
        print("  %-5s -> %-3s %-34s active voters=%-3s linked to a user=%-3s %s"
              % (gate, cid, name[:34], voters, linked,
                 "CONFIGURED" if ok else "FAILS CLOSED (ROSTER_EMPTY)"))
    return all_ok


if __name__ == "__main__":
    main()
