# -*- coding: utf-8 -*-
"""
licence_coverage.py — does every tenant hold every ratified SKU?

W2-0 / ADR-018. Run against dev and production; exits non-zero on any gap.

WHY THIS EXISTS AS A SCRIPT AND NOT A TEST
------------------------------------------
The answer lives in `platform.organisation_licenses`, and the JUnit suite has no
database. The same split as `grant_tightness.py` (guards vs grants) and
`export_grant_policy.py` (EXPORT vs READ): the code half is a ratchet in the
suite, the data half is a script here.

AND WHY IT IS NOT OPTIONAL
--------------------------
`LicenseGuardAspect` is fail-closed: a tenant with no row for a module gets
`ModuleDisabledException`, not a warning. F2-04 will annotate ~2,138 endpoints
against these codes, so from that commit onwards **a missing row is a 403 for a
paying customer**, and there is no screen anywhere that shows an operator which
rows are missing.

The gap is structural rather than accidental, which is why a one-off migration
does not close it. Measured 2026-08-01: **nothing in the codebase creates an
organisation.** `OrganisationController` exposes `accessible` and `switch/{id}`
and no POST; `OrganisationRepository` is a bare `JpaRepository` whose eight
consumers all read a name for an export header. Organisations 2 and 3 exist
because the Python bootstrap inserted them. So V344 grandfathered the tenants
that exist, and the next tenant — however it arrives — will hold nothing until
someone runs this and notices (F2-81).

USAGE
    py licence_coverage.py [--db TSS_GRC] [--server localhost,1433] [--fix-sql]

    --fix-sql   print the INSERT statements that would close the gaps, instead
                of just naming them. Nothing is written; review before running.
"""
import argparse
import sys

try:
    import pyodbc
except ImportError:
    sys.exit("pyodbc is required:  py -m pip install pyodbc")

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

# ADR-018 §3. Keep in step with LicensingService.SELLABLE_MODULES and
# SkuCatalogueRatchetTest.RATIFIED — three transcriptions, and the suite asserts
# the first two against each other.
RATIFIED = [
    "RISK", "COMPLIANCE", "AUDIT_LC", "CYBER", "SOCOPS", "BCM", "PDPL", "TPRM",
    "ESG", "ETHICS", "SAUDI_FS", "DATA_AI_GOV", "BOARD", "ACCESS_GOV", "CHANGE",
]

# ADR-018 §5 / R4 — de-licensed into the mandatory Base Platform. A row for one of
# these is legacy residue, not an entitlement, and must never be created afresh.
BASE_RETIRED = ["EVIDENCE", "REPORTING", "AUDIT_LOG", "PLATFORM_ADMIN",
                "CONTROL_LIB", "POSTURE", "AUTOMATION"]

# ADR-018 §5 — merged into a sibling SKU. A holder must also hold the successor (R3).
MERGED = {"POLICY": "COMPLIANCE", "REGULATORY": "COMPLIANCE", "SCM": "TPRM",
          "INCIDENT": "SOCOPS", "NDI": "DATA_AI_GOV"}

GRANDFATHER_PREFIX = "Grandfather grant (W2-0"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", default="TSS_GRC")
    ap.add_argument("--server", default="localhost,1433")
    ap.add_argument("--user", default="sa")
    ap.add_argument("--password", default="tss.123")
    ap.add_argument("--fix-sql", action="store_true")
    a = ap.parse_args()

    cn = pyodbc.connect(
        f"DRIVER={{ODBC Driver 17 for SQL Server}};SERVER={a.server};DATABASE={a.db};"
        f"UID={a.user};PWD={a.password};TrustServerCertificate=yes", timeout=15)
    c = cn.cursor()

    c.execute("SELECT id, name_en, ISNULL(is_active, 0) FROM platform.organisations ORDER BY id")
    orgs = [(r[0], r[1], bool(r[2])) for r in c.fetchall()]
    c.execute("""SELECT organisation_id, module_code, tier, ISNULL(enabled, 0),
                        valid_from, valid_to, ISNULL(notes, '')
                 FROM platform.organisation_licenses""")
    rows = c.fetchall()
    cn.close()

    print(f"=== {a.db} @ {a.server} ===")
    print(f"organisations : {len(orgs)}  ({sum(1 for o in orgs if o[2])} active)")
    print(f"licence rows  : {len(rows)}")
    if not orgs:
        # Anti-vacuity. With no tenants every "every tenant holds X" loop below is
        # empty and the script would exit 0 having checked nothing.
        print("\nFAIL  platform.organisations is empty — nothing was checked.")
        return 2
    if not rows:
        print("\nFAIL  platform.organisation_licenses is empty — every tenant is blacked out.")
        return 2

    held = {}          # org -> {code: (tier, enabled, from, to, notes)}
    for org, code, tier, enabled, vf, vt, notes in rows:
        held.setdefault(org, {})[code] = (tier, bool(enabled), vf, vt, notes)

    problems, fixes = [], []
    print()
    for org_id, name, active in orgs:
        mine = held.get(org_id, {})
        missing = [k for k in RATIFIED if k not in mine]
        # A row that exists but is disabled or out of window is not an entitlement:
        # enabledModulesFor() applies exactly these three filters before the aspect
        # ever sees the code, so "the row is there" is not the question.
        inert = [k for k in RATIFIED if k in mine and not usable(mine[k])]
        base = [k for k in BASE_RETIRED if k in mine]
        orphan_merges = [f"{k}->{v}" for k, v in MERGED.items() if k in mine and v not in mine]
        granted = sum(1 for k in mine if mine[k][4].startswith(GRANDFATHER_PREFIX))

        flag = "OK  " if not (missing or inert or orphan_merges) else "GAP "
        print(f"{flag}org {org_id} — {name}{'' if active else '  (INACTIVE)'}")
        print(f"       {len(mine)} rows, {len(RATIFIED) - len(missing)}/{len(RATIFIED)} ratified SKUs, "
              f"{granted} grandfathered, {len(base)} legacy Base residue")
        if missing:
            problems.append(f"org {org_id} is missing {len(missing)} ratified SKU(s): {', '.join(missing)}")
            print(f"       MISSING  {', '.join(missing)}")
            for code in missing:
                fixes.append(
                    "INSERT INTO platform.organisation_licenses "
                    "(organisation_id, module_code, tier, enabled, valid_from, notes) VALUES "
                    f"({org_id}, '{code}', 'ENTERPRISE', 1, CAST(SYSUTCDATETIME() AS DATE), "
                    "N'Grandfather grant (W2-0 / ADR-018) — granted administratively, not sold. "
                    "A commercial tightening starts here.');")
        if inert:
            problems.append(f"org {org_id} holds {len(inert)} ratified SKU(s) as unusable rows: {', '.join(inert)}")
            print(f"       INERT    {', '.join(inert)}   (disabled or outside valid_from/valid_to)")
        if orphan_merges:
            problems.append(f"org {org_id} holds a retired code without its successor: {', '.join(orphan_merges)}")
            print(f"       ADR-018 R3 BREACH  {', '.join(orphan_merges)}")
        if base:
            print(f"       note: legacy Base rows present (harmless, not an entitlement): {', '.join(base)}")

    print()
    if problems:
        print(f"FAIL  {len(problems)} problem(s):")
        for p in problems:
            print(f"  - {p}")
        if a.fix_sql and fixes:
            print(f"\n-- {len(fixes)} statement(s) that would close the MISSING gaps. Review first.")
            for f in fixes:
                print(f)
        elif fixes:
            print(f"\n({len(fixes)} INSERT(s) would close the missing rows — re-run with --fix-sql to print them.)")
        return 1

    print("PASS  every organisation holds every ratified SKU as a usable row, and every retired "
          "code a tenant still holds has its successor.")
    return 0


def usable(entry) -> bool:
    """Mirror LicensingService.enabledModulesFor: enabled, started, not expired."""
    import datetime
    _tier, enabled, vf, vt, _notes = entry
    if not enabled:
        return False
    today = datetime.date.today()
    if vf is not None and vf > today:
        return False
    if vt is not None and vt < today:
        return False
    return True


if __name__ == "__main__":
    sys.exit(main())
