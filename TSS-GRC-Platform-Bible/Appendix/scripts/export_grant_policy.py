# F2-61 -- does the EXPORT grant table obey the tier rule ADR-017 D-10 ratified?
#
# D-10's rule is one sentence: "EXPORT follows READ for the same module, except where the export is a
# bulk extract of personal data or board-confidential material." F2-27 gave 26 `MODULE:EXPORT`
# permissions teeth on 40 file handlers. Nobody then asked whether the grant table agreed with the
# rule, and grant_tightness.py cannot answer it: that script compares the GUARD side to the GRANT
# side. This one compares two actions of the same module to each other, which is a different axis and
# the one the tier rule is actually about.
#
# Per module there are exactly three relations, and only one of them is a defect:
#
#   EXPORT == READ   conforming. The rule describes the grants; nothing to do.
#   EXPORT  < READ   tighter than the rule. Never a hole. May be deliberate (a module whose export
#                    carries more than its screen) or may be drift; either way it fails safe.
#   EXPORT  > READ   *** THE INVERSION *** a role can extract the whole register as a file while
#                    being refused the screen that shows one record of it. F2-27's ratified premise
#                    was "viewing one record does not confer extracting the register". This is its
#                    converse, and for the affected roles the FILE IS THE ONLY WAY TO SEE THE DATA.
#                    Live-proven 2026-08-01: so_officer (CISO, no BCM:READ) got 403 on
#                    GET /api/v1/bcm/plans and 200 + 38,043 bytes of real PDF on
#                    GET /api/v1/bcm/plans/7/export.pdf, plus a 55,107-byte evidence-package ZIP
#                    from the endpoint its own source calls the most sensitive export on the platform.
#
# A module can also be CROSSED (roles on both sides), which is the inversion plus drift together.
#
# The exception list below is the ONLY thing standing between an asymmetry and a non-zero exit. Adding
# an entry is a policy act: it must name the ADR clause that permits it. That is deliberate -- an
# undocumented asymmetry should break this script, and a documented one should be readable here
# without opening the ADR.
#
#   py TSS-GRC-Platform-Bible/Appendix/scripts/export_grant_policy.py
#   py TSS-GRC-Platform-Bible/Appendix/scripts/export_grant_policy.py --database TSS_GRC_Deployment
#
# Read-only: SELECTs only. Revoking what it surfaces is F2-61(c), module-sized, with a before/after
# probe each -- never one migration (ADR-017 D-10c).

import argparse
import sys

try:
    import pyodbc
except ImportError:
    sys.exit("pyodbc is required: py -m pip install pyodbc")

# Windows consoles default to cp1252; the report carries arrows and dashes.
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

DEV = "TSS_GRC"
PROD = "TSS_GRC_Deployment"

# ── The documented exceptions, each with the clause that permits it ─────────────────────────────────
# Keyed (module, role) for role-level asymmetries; (module, "*") for a module whose EXPORT has no READ
# counterpart at all. The value is the authority, not a rationalisation: if you cannot cite a clause,
# the entry does not belong here and the asymmetry is a finding.
DOCUMENTED = {
    ("AI", "AUDITOR"):
        "ADR-017 D-10c(ii) -- the AI export guard is an AND with hasAnyRole(...,'AUDITOR',...), so the "
        "permission alone is not sufficient; V320 granted AI:EXPORT to AUDITOR explicitly to avoid "
        "403'ing a role the endpoint had always allowed. Reasoned and recorded in MlModelController.",
    ("BOARD", "*"):
        "ADR-017 D-11(a) -- board reads are authorised by ADMIN:READ, so BOARD:READ is deliberately "
        "not catalogued. The tier rule is UNSATISFIABLE here, not merely unsatisfied: there is no "
        "same-module READ population for EXPORT to follow. D-10c(i) supplies the fallback.",
    ("BCM", "CFO"):
        "ADR-017 D-10c(iii) -- V320 Part 3 granted BCM:EXPORT to CFO for ONE endpoint, "
        "BcpBinderController#download, whose guard ANDs its own hasAnyRole list naming CFO. The "
        "grant is harmless now because every OTHER BCM export guard also demands BCM:READ "
        "(F2-61, 2026-08-01), so the permission cannot be exercised anywhere the tier rule "
        "would be broken. The asymmetry survives in the grant table and is unreachable in code.",
    ("BCM", "CISO"):
        "ADR-017 D-10c(iii) -- as CFO above. Live-proven 2026-08-01: so_officer (CISO) now gets 403 "
        "on all three composed BCM exports and the binder guard still admits it.",
}

# Generic personas: read-only seats and single-object custody roles. Named as data so the policy can
# cite the population instead of an adjective.
GENERIC_EXPLICIT = ("VIEWER",)
GENERIC_SUFFIX = "_OWNER"


def connect(db):
    return pyodbc.connect(
        "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost,1433;DATABASE=%s;"
        "UID=sa;PWD=tss.123;TrustServerCertificate=yes" % db, autocommit=True)


def grants(cur):
    """{(module, action): {role_name}} over every live role's grants."""
    cur.execute("""
        SELECT p.module, p.action, r.role_name
          FROM iam.role_permissions rp
          JOIN iam.permissions p ON p.id = rp.permission_id
          JOIN iam.roles       r ON r.id = rp.role_id
         WHERE ISNULL(r.is_deleted, 0) = 0
    """)
    out = {}
    for module, action, role in cur.fetchall():
        out.setdefault((module, action), set()).add(role)
    return out


def audit(db):
    """Returns (undocumented_asymmetries, findings_text). Non-empty first element = non-zero exit."""
    cur = connect(db).cursor()
    print("=" * 100)
    print("EXPORT grant policy -- %s" % db)
    print("=" * 100)

    cur.execute("SELECT COUNT(*) FROM iam.roles WHERE ISNULL(is_deleted,0)=0")
    n_roles = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM iam.permissions WHERE action = 'EXPORT'")
    n_catalogued = cur.fetchone()[0]
    g = grants(cur)
    granted = sorted(m for (m, a) in g if a == "EXPORT")

    print("%d live roles · %d EXPORT permissions catalogued · %d of them granted to at least one role"
          % (n_roles, n_catalogued, len(granted)))
    if not granted:
        # An empty read must never be reportable as clean. This platform has already shipped one
        # measurement whose own definition dissolved and then read green forever (F2-72's comparator).
        print("\n!! ANTI-VACUITY FAILURE: zero EXPORT grants read. The query is wrong, not the grants.")
        return ["anti-vacuity: no EXPORT grants read"], []

    undocumented, conforming, tighter, inverted = [], [], [], []
    print("\n%-14s %7s %6s   %-16s %s"
          % ("MODULE", "EXPORT", "READ", "RELATION", "holds EXPORT, NOT READ"))
    for m in granted:
        e, r = g.get((m, "EXPORT"), set()), g.get((m, "READ"), set())
        extra, missing = sorted(e - r), sorted(r - e)
        module_wide = ("%s" % m, "*") in DOCUMENTED or (m, "*") in DOCUMENTED
        rel = ("EXPORT > READ" if extra and not missing else
               "CROSSED" if extra and missing else
               "EXPORT < READ" if missing else "EXPORT == READ")
        print("%-14s %7d %6d   %-16s %s" % (m, len(e), len(r), rel, ", ".join(extra) or ""))
        if extra:
            inverted.append(m)
            for role in extra:
                if (m, role) in DOCUMENTED or module_wide:
                    continue
                undocumented.append("%s:EXPORT held by %s with no %s:READ" % (m, role, m))
        elif missing:
            tighter.append(m)
        else:
            conforming.append(m)

    print("\n  conforming (EXPORT == READ)      %2d  %s" % (len(conforming), ", ".join(conforming)))
    print("  tighter than the rule            %2d  %s" % (len(tighter), ", ".join(tighter)))
    print("  INVERTED (EXPORT wider)          %2d  %s" % (len(inverted), ", ".join(inverted) or "none"))
    for m in inverted:
        key = (m, "*")
        if key in DOCUMENTED:
            print("      %s -- DOCUMENTED: %s" % (m, DOCUMENTED[key]))
        else:
            for role in sorted(g.get((m, "EXPORT"), set()) - g.get((m, "READ"), set())):
                tag = DOCUMENTED.get((m, role))
                print("      %s / %-18s %s" % (m, role, ("DOCUMENTED: " + tag) if tag
                                               else "*** UNDOCUMENTED -- this is a finding ***"))

    # ── the contradiction the amendment exists to resolve ──────────────────────────────────────────
    # "EXPORT follows READ" is a two-way rule. Read as a ceiling it removes the inversions above. Read
    # as a floor it GRANTS export wherever a generic persona already holds READ -- which is nearly
    # everywhere for VIEWER. Both readings are faithful to the sentence, and they disagree.
    generic = set(GENERIC_EXPLICIT)
    cur.execute("SELECT role_name FROM iam.roles WHERE ISNULL(is_deleted,0)=0")
    generic.update(rn for (rn,) in cur.fetchall() if rn.endswith(GENERIC_SUFFIX))
    catalogued_exports = set()
    cur.execute("SELECT module FROM iam.permissions WHERE action = 'EXPORT'")
    catalogued_exports.update(m for (m,) in cur.fetchall())

    print("\n  generic personas -- what a FLOOR reading of the tier rule would add (%d roles)"
          % len(generic))
    floor_total = 0
    for role in sorted(generic):
        has_r = {m for (m, a), s in g.items() if a == "READ" and role in s}
        has_e = {m for (m, a), s in g.items() if a == "EXPORT" and role in s}
        # A rule cannot grant a permission the catalogue does not define. SIGNATURE:READ exists and
        # SIGNATURE:EXPORT does not, so counting it as a gain would invent an exposure.
        gain = sorted((has_r - has_e) & catalogued_exports)
        phantom = sorted((has_r - has_e) - catalogued_exports)
        floor_total += len(gain)
        print("    %-20s READ %2d  EXPORT %2d  floor reading ADDS %d%s%s"
              % (role, len(has_r), len(has_e), len(gain),
                 ("  -> " + ", ".join(gain)) if gain else "",
                 ("   [not catalogued, cannot be granted: %s]" % ", ".join(phantom)) if phantom else ""))
    print("    ── a floor reading would add %d grants across %d generic personas"
          % (floor_total, len(generic)))

    dist = sorted(((len(g[(m, "EXPORT")]), m) for m in granted), reverse=True)
    half = [("%s %d" % (m, n)) for n, m in dist if n >= n_roles / 2]
    print("\n  breadth, widest first: %s" % "  ".join("%s %d" % (m, n) for n, m in dist))
    print("  held by half the roles or more (%d+): %s" % ((n_roles + 1) // 2, ", ".join(half) or "none"))
    return undocumented, inverted


ap = argparse.ArgumentParser(description="F2-61 -- audit the EXPORT grant table against ADR-017 D-10")
ap.add_argument("--database", help="single database; default audits dev and prod")
args = ap.parse_args()

targets = [args.database] if args.database else [DEV, PROD]
problems = []
for db in targets:
    try:
        found, _ = audit(db)
    except pyodbc.Error as ex:
        print("  cannot read %s: %s" % (db, str(ex)[:160]))
        problems.append("%s unreadable" % db)
        continue
    problems.extend("%s: %s" % (db, p) for p in found)
    print()

if problems:
    print("UNDOCUMENTED ASYMMETRIES: %d" % len(problems))
    for p in problems:
        print("  - %s" % p)
    print("\nEither revoke the grant (F2-61(c)) or add it to DOCUMENTED with the ADR clause that "
          "permits it. A silent third option is what produced F2-61.")
else:
    print("Every EXPORT/READ asymmetry is documented against an ADR-017 clause.")
sys.exit(1 if problems else 0)
