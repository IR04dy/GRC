# F2-05 — the role-guard census: what a role-name guard actually authorises.
#
# The code side comes from RoleGuardConversionRatchetTest, which extracts guards by REFLECTION
# over the compiled @PreAuthorize annotations (a source regex has under-counted a security surface
# in this repository three times). This script supplies the half that needs a database: whether the
# role names those guards recite exist at all.
#
#   cd tss-grc-backend && mvn -o test -Dtest=RoleGuardConversionRatchetTest
#   py TSS-GRC-Platform-Bible/Appendix/scripts/role_guard_census.py
#
# WHY THE JOIN MATTERS. hasAnyRole('X') tests for the granted authority ROLE_X. There is no
# fallback and — unlike hasPermission — no PLATFORM_ADMIN bypass: ModulePermissionEvaluator is
# never called, so the platform administrator gets through a role guard only if the guard names a
# role they hold. Therefore a guard whose every named role is absent from iam.roles is not "tight".
# It is CLOSED, for everyone, permanently, and it fails the way dead authorisation always fails in
# this platform: silently, with a 403 that looks like the guard working.
#
# Neither existing measurement can see this. The authorization ratchet counts a role guard as
# guarded. GRANT_TIGHTNESS.md joins @PreAuthorize permissions against iam.role_permissions, and a
# role-only guard names no permission, so it appears on neither side of that join.
#
# Read-only: SELECTs plus one markdown file. It changes no roles and no grants.

import argparse
import collections
import os
import sys

try:
    import pyodbc
except ImportError:
    sys.exit("pyodbc is required: py -m pip install pyodbc")

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
SITES = os.path.join(ROOT, "tss-grc-backend", "target", "role-guard-sites.txt")
REPORT = os.path.join(ROOT, "TSS-GRC-Platform-Bible", "Appendix", "ROLE_GUARD_CENSUS.md")
CONN = ("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
        "UID=sa;PWD=tss.123;TrustServerCertificate=yes")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sites", default=SITES)
    ap.add_argument("--md", default=REPORT)
    args = ap.parse_args()

    if not os.path.exists(args.sites):
        sys.exit("missing %s.\nRun: cd tss-grc-backend && mvn -o test "
                 "-Dtest=RoleGuardConversionRatchetTest" % args.sites)

    sites = []  # (bucket, site, kind, [names])
    with open(args.sites, encoding="utf-8") as fh:
        for ln in fh:
            ln = ln.strip()
            if not ln:
                continue
            bucket, site, kind, names = ln.split(" ", 3)
            sites.append((bucket, site, kind, names.split("|")))

    cn = pyodbc.connect(CONN)
    c = cn.cursor()

    c.execute("SELECT role_name FROM iam.roles WHERE is_deleted = 0")
    existing = {r[0] for r in c.fetchall()}

    # A role that exists but is held by no user is reachable in principle (an administrator can
    # assign it) — distinguished from one that cannot be assigned because it does not exist.
    c.execute("""SELECT r.role_name, COUNT(DISTINCT ur.user_id)
                 FROM iam.roles r
                 LEFT JOIN iam.user_roles ur ON ur.role_id = r.id
                 WHERE r.is_deleted = 0 GROUP BY r.role_name""")
    holders = dict(c.fetchall())

    c.execute("SELECT module + ':' + action FROM iam.permissions")
    catalogued = {r[0] for r in c.fetchall()}

    c.execute("""SELECT p.module + '_' + p.action, COUNT(DISTINCT rp.role_id)
                 FROM iam.permissions p
                 LEFT JOIN iam.role_permissions rp ON rp.permission_id = p.id
                 GROUP BY p.module, p.action""")
    authority_holders = dict(c.fetchall())

    c.execute("""SELECT p.module + '_' + p.action
                 FROM iam.roles r
                 JOIN iam.role_permissions rp ON rp.role_id = r.id
                 JOIN iam.permissions p ON p.id = rp.permission_id
                 WHERE r.role_name = 'PLATFORM_ADMIN' AND r.is_deleted = 0""")
    admin_authorities = {r[0] for r in c.fetchall()}

    role_sites = [s for s in sites if s[2] in ("PURE", "COMPOSED")]
    pure = [s for s in role_sites if s[2] == "PURE"]
    raw = [s for s in sites if s[2] == "RAW_AUTHORITY"]

    named = collections.Counter()
    for _, _, _, names in role_sites:
        for n in names:
            named[n] += 1
    ghost = sorted(n for n in named if n not in existing)

    # The finding: a PURE guard with no surviving role leg is closed to everyone.
    dead, thin, live = [], [], []
    # ...and the one a step short of that, which is where this platform actually sits: every real
    # name has fallen away EXCEPT PLATFORM_ADMIN, so the endpoint is administrator-only. Split by
    # intent, because the two look identical in the code and mean opposite things — a guard that
    # names PLATFORM_ADMIN and nothing else is a decision; one that names four roles and keeps only
    # PLATFORM_ADMIN is attrition, and the module's own persona is locked out of its own feature.
    admin_deliberate, admin_attrition = [], []
    for bucket, site, kind, names in pure:
        alive = [n for n in names if n in existing]
        if not alive:
            dead.append((bucket, site, names))
        elif all(holders.get(n, 0) == 0 for n in alive):
            thin.append((bucket, site, names, alive))
        else:
            live.append((bucket, site, names, alive))
        if alive and not [n for n in alive if n != "PLATFORM_ADMIN"]:
            if len(names) == 1 and names[0] == "PLATFORM_ADMIN":
                admin_deliberate.append((bucket, site, names))
            else:
                admin_attrition.append((bucket, site, names,
                                        [n for n in names if n not in existing]))

    by_module = collections.Counter(b for b, _, _, _ in pure)

    out = []

    def emit(line=""):
        print(line)
        out.append(line)

    emit("# Role-guard census — what `hasAnyRole` actually authorises")
    emit()
    emit("Generated by `Appendix/scripts/role_guard_census.py` from the compiled `@PreAuthorize`")
    emit("set (`RoleGuardConversionRatchetTest` → `target/role-guard-sites.txt`) joined against the")
    emit("live `iam.roles` / `iam.role_permissions`. Do not hand-edit: the next run overwrites it.")
    emit("Narrative belongs in the script.")
    emit()
    emit("`hasAnyRole('X')` tests for the granted authority `ROLE_X`. There is no fallback and no")
    emit("PLATFORM_ADMIN bypass — `ModulePermissionEvaluator` is never called, so the platform")
    emit("administrator passes a role guard only when the guard names a role they hold. A guard")
    emit("whose every named role is absent from `iam.roles` is therefore not tight; it is closed to")
    emit("everyone. Neither existing measurement can see that: the authorization ratchet counts a")
    emit("role guard as guarded, and `GRANT_TIGHTNESS.md` joins permissions, which a role-only")
    emit("guard names none of.")
    emit()
    emit("| Measure | Count |")
    emit("|---|---|")
    emit("| Guard sites with a role leg | %d |" % len(role_sites))
    emit("| … **pure** (no `hasPermission` leg) — F2-05 conversion scope | **%d** |" % len(pure))
    emit("| … composed (role leg ANDed onto a permission — already tight) | %d |"
         % (len(role_sites) - len(pure)))
    emit("| Module packages holding pure role guards | %d |" % len(by_module))
    emit("| Distinct role names recited by any role guard | %d |" % len(named))
    emit("| … **that do not exist in `iam.roles`** | **%d** |" % len(ghost))
    emit("| Pure guards **closed to everyone** (no named role exists) | **%d** |" % len(dead))
    emit("| Pure guards left admitting **PLATFORM_ADMIN alone, by attrition** | **%d** |"
         % len(admin_attrition))
    emit("| … versus guards that name PLATFORM_ADMIN and nothing else (deliberate) | %d |"
         % len(admin_deliberate))
    emit("| Pure guards whose surviving roles are held by no user | %d |" % len(thin))
    emit("| Pure guards reachable by at least one seeded user | %d |" % len(live))
    emit("| `hasAuthority('MODULE_ACTION')` sites (permission spelled longhand) | %d |" % len(raw))
    emit()

    emit("## A. Role names no role table contains")
    emit()
    if not ghost:
        emit("None — every recited role name exists.")
    else:
        emit("Each of these is a permanently false OR leg. Where it is the *only* leg, section B")
        emit("shows what that closes.")
        emit()
        emit("| Role name recited in a guard | Guard sites naming it |")
        emit("|---|---|")
        for n in sorted(ghost, key=lambda x: (-named[x], x)):
            emit("| `%s` | %d |" % (n, named[n]))
    emit()

    emit("## B. Pure role guards closed to everyone")
    emit()
    if not dead:
        emit("None.")
    else:
        emit("Every role name in these guards is absent from `iam.roles`, and a role guard has no")
        emit("administrator bypass. No credential the platform can issue reaches these endpoints.")
        emit()
        emit("| Module | Guard site | Roles named (none exist) |")
        emit("|---|---|---|")
        for bucket, site, names in sorted(dead):
            emit("| `%s` | `%s` | %s |" % (bucket, site, ", ".join("`%s`" % n for n in names)))
    emit()

    emit("## B2. Pure role guards left admitting PLATFORM_ADMIN alone — by attrition")
    emit()
    emit("These are section B one notch weaker, and they are where this platform actually sits.")
    emit("The guard names several roles; all but `PLATFORM_ADMIN` are absent from `iam.roles`; so the")
    emit("feature is administrator-only, and the persona the module was built for cannot use it —")
    emit("silently, because a 403 from a role guard is indistinguishable from a 403 that was meant.")
    emit()
    emit("Guards that name `PLATFORM_ADMIN` **and nothing else** are excluded: that is a stated")
    emit("decision (settings reset, token revocation, platform administration) and belongs in a")
    emit("different conversation. The distinction is invisible in the code, which is why it is")
    emit("computed here rather than eyeballed.")
    emit()
    if not admin_attrition:
        emit("None.")
    else:
        emit("| Module | Guard site | Roles named | Of those, absent from `iam.roles` |")
        emit("|---|---|---|---|")
        for bucket, site, names, ghosts in sorted(admin_attrition):
            emit("| `%s` | `%s` | %s | %s |" % (
                bucket, site,
                ", ".join("`%s`" % n for n in names),
                ", ".join("`%s`" % g for g in ghosts)))
    emit()

    emit("## C. Pure role guards whose roles exist but nobody holds")
    emit()
    if not thin:
        emit("None.")
    else:
        emit("Reachable in principle — an administrator can assign the role — but no seeded user")
        emit("has it, so nothing in dev or in a fresh tenant exercises them.")
        emit()
        emit("| Module | Guard site | Surviving roles (0 users each) |")
        emit("|---|---|---|")
        for bucket, site, names, alive in sorted(thin):
            emit("| `%s` | `%s` | %s |" % (bucket, site, ", ".join("`%s`" % n for n in alive)))
    emit()

    emit("## D. Conversion backlog by module")
    emit()
    emit("F2-05 is sized S per module. Order by count, but read section B first: a module carrying")
    emit("closed guards is a correctness fix, not only a modelling one.")
    emit()
    emit("| Module package | Pure role guards | Closed to everyone |")
    emit("|---|---|---|")
    dead_by_module = collections.Counter(b for b, _, _ in dead)
    for bucket, n in by_module.most_common():
        emit("| `%s` | %d | %s |" % (bucket, n, dead_by_module.get(bucket, "") or ""))
    emit()

    emit("## E. Permissions spelled as raw authorities")
    emit()
    emit("`hasAuthority('KB_WRITE')` reads like `hasPermission('KB','WRITE')` and is not the same")
    emit("expression: `ModulePermissionEvaluator` is never invoked, so its hardcoded PLATFORM_ADMIN")
    emit("bypass does not apply. The platform administrator passes only if PLATFORM_ADMIN holds that")
    emit("permission as an actual grant row — which is a different question from the one the other")
    emit("1,400 guards ask, and is answered per row below.")
    emit()
    emit("| Module | Guard site | Authority | Catalogued | Role holders | PLATFORM_ADMIN holds it |")
    emit("|---|---|---|---|---|---|")
    for bucket, site, _, names in sorted(raw):
        for a in names:
            pair = a.replace("_", ":", 1) if ":" not in a else a
            # MODULE_ACTION → MODULE:ACTION needs the LAST underscore, not the first
            if "_" in a:
                mod, _, act = a.rpartition("_")
                pair = mod + ":" + act
            emit("| `%s` | `%s` | `%s` | %s | %s | %s |" % (
                bucket, site, a,
                "yes" if pair in catalogued else "**NO**",
                authority_holders.get(a, 0),
                "yes" if a in admin_authorities else "**no**"))
    emit()

    with open(args.md, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")
    print()
    print("wrote %s" % args.md)
    cn.close()


if __name__ == "__main__":
    main()
