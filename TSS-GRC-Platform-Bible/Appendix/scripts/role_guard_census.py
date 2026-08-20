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

# F2-253: this script prints an arrow and an em-dash, and a Windows console is cp1252 — so every
# run died in `emit` with UnicodeEncodeError before writing a line. "Nothing runs it" was literally
# true: it could not run here at all. Same remedy as F2-244's probe.
sys.stdout.reconfigure(encoding="utf-8")

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
    # One guard can emit both a role entry and a raw-authority entry (two legs, two spellings), so
    # the two buckets are joined by site to judge whether the whole expression is satisfiable.
    raw_by_site = collections.defaultdict(list)
    for bucket, site, _, names in raw:
        raw_by_site[(bucket, site)].extend(names)

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
    # F2-05 slice 5 corrected this script. A guard whose only surviving ROLE is PLATFORM_ADMIN was
    # reported as "administrator-only by attrition" even when it carried a LIVE authority leg — and
    # `board_pack`'s read guard did exactly that: five ghost roles, PLATFORM_ADMIN, then
    # `or hasAuthority('REPORTING_READ')`, an authority 20 roles hold INCLUDING VIEWER. The endpoint
    # was not narrow at all; the dead names made a wide guard look narrow, and this report agreed
    # with the appearance. It sent me into that slice believing reads were admin-only. So the live
    # authority leg is now checked FIRST and such sites get their own bucket: they are the opposite
    # defect and need the opposite fix (tighten, not open up).
    camouflaged = []
    for bucket, site, kind, names in pure:
        alive = [n for n in names if n in existing]
        live_authority = [a for a in raw_by_site.get((bucket, site), [])
                          if authority_holders.get(a, 0) > 0]
        if not alive and not live_authority:
            dead.append((bucket, site, names))
        elif not alive and live_authority:
            # Every role name is a ghost, yet the guard is open — entirely via the authority leg.
            camouflaged.append((bucket, site, names, live_authority,
                                max(authority_holders.get(a, 0) for a in live_authority)))
        elif all(holders.get(n, 0) == 0 for n in alive) and not live_authority:
            thin.append((bucket, site, names, alive))
        else:
            live.append((bucket, site, names, alive))
        # Camouflage also covers the case where PLATFORM_ADMIN is the lone surviving role: the
        # authority leg, not the role list, is what admits callers.
        if live_authority and not [n for n in alive if n != "PLATFORM_ADMIN"] \
                and any(n not in existing for n in names):
            if (bucket, site, names, live_authority,
                    max(authority_holders.get(a, 0) for a in live_authority)) not in camouflaged:
                camouflaged.append((bucket, site, names, live_authority,
                                    max(authority_holders.get(a, 0) for a in live_authority)))
            continue
        if alive and not [n for n in alive if n != "PLATFORM_ADMIN"]:
            # A guard can also reach for a second leg spelled as a raw authority rather than a role
            # name — `hasRole('PLATFORM_ADMIN') or hasAuthority('KB_WRITE')`. If that authority is
            # uncatalogued or held by nobody it is just as dead as a ghost role, and the endpoint is
            # administrator-only by attrition, not by decision. Counting it as deliberate because
            # the unsatisfiable name happened to be an authority is the same miscount this report
            # exists to prevent, one bucket over.
            dead_authority = [a for a in raw_by_site.get((bucket, site), [])
                              if authority_holders.get(a, 0) == 0]
            if len(names) == 1 and names[0] == "PLATFORM_ADMIN" and not dead_authority:
                admin_deliberate.append((bucket, site, names))
            else:
                admin_attrition.append((bucket, site, names,
                                        [n for n in names if n not in existing] + dead_authority))

    # ── F2-253: the bucket every other one here could not see ────────────────────────────────────
    #
    # Every bucket above is keyed on whether a role name EXISTS. `holders` was computed at the top
    # and then used in exactly one place — `thin`, which requires EVERY alive name to be unheld.
    # Almost every guard in this platform names PLATFORM_ADMIN, which is held, so `thin` can never
    # fire for them and `admin_attrition` cannot either, since it tests for absence. The question
    # this report exists to answer is "can anybody actually get through", and it was being asked of
    # existence: `admin_attrition` and `thin` both read 0 while 24 pure guards admitted nobody but
    # the platform administrator.
    #
    # A role that exists and is held by nobody fails EXACTLY like a ghost: hasAnyRole tests for the
    # granted authority ROLE_X, and an unassigned role grants it to no one. The difference is only
    # that an administrator can fix it without a jar — which is a remedy, not a defence, and says
    # nothing about the platform as shipped.
    admin_by_holders = []
    for bucket, site, kind, names in pure:
        if len(names) < 2:
            continue                                  # naming PLATFORM_ADMIN alone is a decision
        live_authority = [a for a in raw_by_site.get((bucket, site), [])
                          if authority_holders.get(a, 0) > 0]
        if live_authority:
            continue                                  # the authority leg admits callers (B3)
        held = [n for n in names if holders.get(n, 0) > 0]
        if held == ["PLATFORM_ADMIN"]:
            locked_out = [n for n in names
                          if n != "PLATFORM_ADMIN" and holders.get(n, 0) == 0]
            admin_by_holders.append((bucket, site, names, locked_out))

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
    emit("whose every named role is absent from `iam.roles` is therefore not tight; if that is the")
    emit("whole expression it is closed to everyone. Neither existing measurement can see that: the")
    emit("authorization ratchet counts a role guard as guarded, and `GRANT_TIGHTNESS.md` joins")
    emit("permissions, which a role-only guard names none of.")
    emit()
    emit("⚠ **Ghost roles cut both ways, and this report used to report only one of them.** When the")
    emit("guard is `hasAnyRole(…dead names…) or hasAuthority('MODULE_ACTION')`, the dead names close")
    emit("nothing — every caller arrives through the authority leg, and the endpoint is exactly as")
    emit("wide as that permission. The ghosts then act as *camouflage*: the source reads like a")
    emit("narrow, named audience while the real population may be twenty roles. Section B2 lists")
    emit("these separately because they need the **opposite** fix from an attrition site — tighten,")
    emit("do not open up. Read section B and B2 together before choosing a conversion target.")
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
    emit("| Pure guards **closed to everyone** (no named role exists, no live authority leg) | **%d** |"
         % len(dead))
    emit("| Pure guards **wide despite dead role names** — a live `hasAuthority` leg carries them "
         "(⚠ camouflage) | **%d** |" % len(camouflaged))
    emit("| Pure guards left admitting **PLATFORM_ADMIN alone, by attrition** | **%d** |"
         % len(admin_attrition))
    emit("| … versus guards that name PLATFORM_ADMIN and nothing else (deliberate) | %d |"
         % len(admin_deliberate))
    emit("| Pure guards **effectively PLATFORM_ADMIN-only — every other named role is HELD BY NOBODY** "
         "(F2-253) | **%d** |" % len(admin_by_holders))
    emit("| Pure guards whose surviving roles are held by no user | %d |" % len(thin))
    # ⚠ `live` is the `else` of dead/camouflaged/thin, so it counts a guard as reachable when ANY
    # named role is held — including PLATFORM_ADMIN alone. Read with B2b: `live` minus B2b is the
    # number reachable by somebody who is not the platform administrator. Labelling it "reachable"
    # unqualified is what let 24 administrator-only guards sit inside a figure that reads as health.
    emit("| Pure guards reachable by at least one seeded user (⚠ **includes** the B2b rows — "
         "admin-only counts as reachable here) | %d |" % len(live))
    emit("| … of those, reachable by a NON-administrator | %d |" % (len(live) - len(admin_by_holders)))
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
        emit("| Module | Guard site | Roles named | Unsatisfiable legs (absent role, or dead authority) |")
        emit("|---|---|---|---|")
        for bucket, site, names, ghosts in sorted(admin_attrition):
            emit("| `%s` | `%s` | %s | %s |" % (
                bucket, site,
                ", ".join("`%s`" % n for n in names),
                ", ".join("`%s`" % g for g in ghosts)))
    emit()

    emit("## B2b. Effectively PLATFORM_ADMIN-only — by HOLDER attrition (F2-253)")
    emit()
    emit("Section B2 asks whether a named role **exists**. This one asks whether anybody **holds**")
    emit("it, which is the question `hasAnyRole` actually puts: it tests for the granted authority")
    emit("`ROLE_X`, and a role assigned to no user grants it to nobody. A guard naming four roles of")
    emit("which only `PLATFORM_ADMIN` is held is administrator-only in exactly the way B2 describes —")
    emit("and B2 reported none of these, because every name in them is present in `iam.roles`.")
    emit()
    emit("An administrator can assign the missing role without a new jar, which is what separates")
    emit("this from section B. That is a remedy, not a defence: as shipped and as seeded, the persona")
    emit("each of these features was built for cannot use it, and nothing in the platform says so.")
    emit()
    if not admin_by_holders:
        emit("None.")
    else:
        emit("| Module | Guard site | Roles named | Named but held by nobody |")
        emit("|---|---|---|---|")
        for bucket, site, names, locked in sorted(admin_by_holders):
            emit("| `%s` | `%s` | %s | %s |" % (
                bucket, site,
                ", ".join("`%s`" % n for n in names),
                ", ".join("`%s`" % n for n in locked)))
    emit()

    emit("## B3. Wide despite dead role names — the camouflage sites")
    emit()
    emit("The inverse of B2, and the reason this report now prints both. Every site here recites at")
    emit("least one role that cannot be granted, yet is fully reachable, because a")
    emit("`hasAuthority('MODULE_ACTION')` leg admits everyone holding that permission. The role names")
    emit("contribute nothing except a misleading impression of narrowness. **The holder count is the")
    emit("real audience** — compare it against what the endpoint's own comment claims, because that")
    emit("is where the two disagree.")
    emit()
    if not camouflaged:
        emit("None. *(This bucket has fired before — see the note below — so an empty section here")
        emit("means the pattern is absent, not that the detector is broken.)*")
    else:
        emit("| Site | Roles recited | Live authority leg | Roles holding it |")
        emit("|---|---|---|---|")
        for bucket, site, names, auths, n in sorted(camouflaged, key=lambda x: -x[4]):
            emit("| `%s` | %s | %s | **%d** |"
                 % (site,
                    ", ".join("`%s`%s" % (x, "" if x in existing else " ⚰") for x in names),
                    ", ".join("`%s`" % a for a in auths), n))
        emit()
        emit("⚰ = absent from `iam.roles`.")
    emit()
    emit("**Provenance of this section.** `BoardPackController#list`/`#get` (F2-05 slice 5) were the")
    emit("first instance: five ghost roles plus `REPORTING_READ`, an authority **20 roles hold,")
    emit("including `VIEWER`** — directly beneath a comment stating reads are for \"a board/governance")
    emit("audience, not any authenticated principal\". Live: `viewer` read all 16 board packs, 200.")
    emit("Until that slice this script bucketed those sites as *PLATFORM_ADMIN-only by attrition* —")
    emit("it checked whether an authority leg was **dead** but never whether one was **alive** — and")
    emit("that miscount is what sent the slice in expecting a lockout. Fixed to")
    emit("`ADMIN:READ`/`ADMIN:WRITE`; the detector was re-verified by replaying the pre-fix guard")
    emit("through it, which is why the count below can be trusted when it reads zero.")
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
