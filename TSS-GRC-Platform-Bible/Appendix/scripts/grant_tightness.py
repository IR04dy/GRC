# F2-13a — grant tightness, in both directions.
#
# A permission is a contract between two places that never see each other: the @PreAuthorize in
# the code, and the rows in iam.role_permissions. Either side alone looks fine. Read together they
# produce three failure modes, and this platform has had all three:
#
#   A. GUARDED, UNGRANTABLE — the code demands a permission no role holds (or that the catalogue
#      does not define). Fail-closed, so it never shows up as an incident: the endpoint just 403s
#      forever and the feature behind it is unreachable. PLATFORM_ADMIN's code bypass in
#      ModulePermissionEvaluator hides it from whoever tests as admin, which is everyone.
#      Found by hand twice: F2-23 (ADMIN:APPROVE, 59 committee methods) and F2-27 (AI:EXPORT).
#
#   B. GRANTED, UNRESTRICTIVE — the permission exists and the guard fires, but so many roles hold
#      it that passing is not evidence of anything. WORKFLOW:WRITE is held by 55 of 58 roles
#      including VIEWER and eight machine ingest accounts, so ApprovalRequestController#create
#      counts as "guarded" in the authorization ratchet while a read-only viewer sails through
#      (live-verified 200). The ratchet's own javadoc records the caveat: guarded is not restricted.
#
#   C. GRANTED, GUARDING NOTHING — catalogued, granted, referenced by no @PreAuthorize anywhere.
#      Harmless to the server and actively misleading to whoever administers roles: the grant
#      screen offers a checkbox that changes no behaviour.
#
# Inputs: the code side comes from PermissionCatalogueUsageTest, which extracts guards by
# REFLECTION over the compiled @PreAuthorize annotations rather than by grepping source — a source
# regex has under-counted a security surface in this codebase twice (F2-27 missed 8 constant-valued
# guards and a 40th file endpoint; F2-30 missed 54 field pairs). Run it first:
#
#   cd tss-grc-backend && mvn -o test -Dtest=PermissionCatalogueUsageTest
#   py TSS-GRC-Platform-Bible/Appendix/scripts/grant_tightness.py
#
# Read-only: it issues SELECTs and writes one markdown file. It changes no grants — tightening the
# grants this surfaces is F2-13b, which needs a per-tenant impact query per change.

import argparse
import collections
import os
import sys

try:
    import pyodbc
except ImportError:
    sys.exit("pyodbc is required: py -m pip install pyodbc")

# scripts/ -> Appendix/ -> TSS-GRC-Platform-Bible/ -> repo root
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
USAGE = os.path.join(ROOT, "tss-grc-backend", "target", "permission-usage.txt")
SITES = os.path.join(ROOT, "tss-grc-backend", "target", "permission-usage-sites.txt")
COMPOSED = os.path.join(ROOT, "tss-grc-backend", "target", "permission-usage-composed.txt")
REPORT = os.path.join(ROOT, "TSS-GRC-Platform-Bible", "Appendix", "GRANT_TIGHTNESS.md")
CONN = ("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
        "UID=sa;PWD=tss.123;TrustServerCertificate=yes")

# Actions that change state. EXPORT is deliberately not in this set and not treated as a read
# either: it extracts the whole register as a file, so it gets its own breadth check below. Before
# F2-27 that distinction was academic because no export endpoint enforced EXPORT; now 40 do, which
# turned every pre-existing EXPORT grant into a live data-extraction right nobody re-reviewed.
WRITEISH = ("WRITE", "APPROVE", "DELETE", "TRIAGE", "BREAK_GLASS")

# Personas that should never hold a write. Named explicitly rather than pattern-matched, because
# "AUDITOR" is not one of them: INTERNAL_AUDITOR and AUDITOR own audit engagements, findings and
# CAPA in this platform and legitimately write them. VIEWER and REGULATOR_VIEWER are the two roles
# whose entire purpose is to look.
READ_ONLY = ("VIEWER", "REGULATOR_VIEWER")

# Parties outside the tenant. A write here is a third party writing your GRC record of truth; an
# export is your register leaving the building.
EXTERNAL = ("EXTERNAL_AUDITOR", "REGULATOR_VIEWER", "SUPPLIER_USER")

# Non-human accounts. These authenticate from an integration, so a stolen key inherits whatever
# they hold — the reason to keep them at exactly what they need to POST.
MACHINE_SUFFIX = "_INGEST"

# Permissions that are satisfied only by ModulePermissionEvaluator's hardcoded PLATFORM_ADMIN
# bypass, by design: the module name IS the role. Reported separately from the dead guards in §A
# because the endpoints do work today — but they work for exactly one reason, and if that bypass
# were ever removed as a hardening step these would die silently.
BYPASS_ONLY_MODULES = ("PLATFORM_ADMIN",)


def read_lines(path, what):
    if not os.path.exists(path):
        sys.exit("missing %s (%s).\nRun: cd tss-grc-backend && mvn -o test "
                 "-Dtest=PermissionCatalogueUsageTest" % (path, what))
    with open(path, encoding="utf-8") as fh:
        return [ln.strip() for ln in fh if ln.strip()]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--usage", default=USAGE)
    ap.add_argument("--sites", default=SITES)
    ap.add_argument("--composed", default=COMPOSED)
    ap.add_argument("--md", default=REPORT)
    ap.add_argument("--breadth", type=float, default=0.5,
                    help="fraction of roles above which a write grant is called cosmetic")
    args = ap.parse_args()

    code_pairs = set(read_lines(args.usage, "code-side pair list"))
    sites_by_pair = collections.defaultdict(list)
    for line in read_lines(args.sites, "code-side guard sites"):
        site, _, pair = line.rpartition(" ")
        sites_by_pair[pair].append(site)
    composed = read_lines(args.composed, "guards ANDing a hasAnyRole leg")

    cn = pyodbc.connect(CONN)
    c = cn.cursor()

    c.execute("SELECT role_name, id FROM iam.roles WHERE is_deleted = 0")
    roles = dict(c.fetchall())
    total_roles = len(roles)

    c.execute("""SELECT p.module + ':' + p.action AS pair, COUNT(rp.role_id)
                 FROM iam.permissions p
                 LEFT JOIN iam.role_permissions rp ON rp.permission_id = p.id
                 GROUP BY p.module, p.action""")
    holders = dict(c.fetchall())

    c.execute("""SELECT r.role_name, p.module + ':' + p.action
                 FROM iam.roles r
                 JOIN iam.role_permissions rp ON rp.role_id = r.id
                 JOIN iam.permissions p ON p.id = rp.permission_id
                 WHERE r.is_deleted = 0""")
    held_by = collections.defaultdict(set)
    for role_name, pair in c.fetchall():
        held_by[role_name].add(pair)

    out = []

    def emit(line=""):
        print(line)
        out.append(line)

    action_of = lambda pair: pair.split(":", 1)[1]
    module_of = lambda pair: pair.split(":", 1)[0]

    emit("# Grant tightness — F2-13a")
    emit()
    emit("Generated by `Appendix/scripts/grant_tightness.py` from the compiled `@PreAuthorize` set")
    emit("(`PermissionCatalogueUsageTest`) joined to `iam.permissions` / `iam.role_permissions`.")
    emit()
    emit("| | count |")
    emit("|---|---|")
    emit("| roles (`iam.roles`, not deleted) | %d |" % total_roles)
    emit("| permissions catalogued (`iam.permissions`) | %d |" % len(holders))
    emit("| grants (`iam.role_permissions`) | %d |" % sum(holders.values()))
    emit("| distinct `MODULE:ACTION` pairs guarded in code | %d |" % len(code_pairs))
    emit("| guard sites | %d |" % sum(len(v) for v in sites_by_pair.values()))
    emit()
    composed_pairs = collections.Counter(s.rpartition(" ")[2] for s in composed)
    emit("**What this report cannot see.** It joins on the permission, so \"holds it\" equals")
    emit("\"gets through\" only where the permission is the whole guard. **%d of %d sites** AND a"
         % (len(composed), sum(len(v) for v in sites_by_pair.values())))
    emit("`hasAnyRole` leg onto the permission, and there holding it is necessary but not")
    emit("sufficient — most concentrated in %s."
         % ", ".join("`%s` (%d)" % (p, n) for p, n in composed_pairs.most_common(5)))
    emit("Nor does it model the `@RequireModule` SKU aspect, or `PLATFORM_ADMIN`'s code bypass")
    emit("except where noted. A same-line grep for these composed guards finds 3 files, not 73")
    emit("sites; the count above comes from reflection over the assembled annotation value.")
    emit()

    # ---------------------------------------------------------------- A: guarded, ungrantable
    emit("## A. Guarded but ungrantable — the feature is unreachable")
    emit()
    emit("Every endpoint below 403s for every persona except `PLATFORM_ADMIN`, which passes on")
    emit("`ModulePermissionEvaluator`'s hardcoded bypass rather than on a grant. Fail-closed, so")
    emit("nothing logs an error and nothing appears broken to whoever tests as admin.")
    emit()

    uncatalogued = sorted(p for p in code_pairs if p not in holders)
    unheld = sorted(p for p in code_pairs
                    if p in holders and holders[p] == 0 and module_of(p) not in BYPASS_ONLY_MODULES)
    bypass_only = sorted(p for p in code_pairs
                         if p in holders and holders[p] == 0 and module_of(p) in BYPASS_ONLY_MODULES)

    emit("### A1 — guarded on a permission the catalogue does not define (%d)" % len(uncatalogued))
    emit()
    if uncatalogued:
        emit("| permission | guard sites | endpoints |")
        emit("|---|---|---|")
        for p in uncatalogued:
            emit("| `%s` | %d | %s |" % (p, len(sites_by_pair[p]), ", ".join(sites_by_pair[p][:6])))
    else:
        emit("None. (This is the F2-23 class — `ADMIN:APPROVE` sat here for months.)")
    emit()

    emit("### A2 — catalogued, guarding real endpoints, held by zero roles (%d)" % len(unheld))
    emit()
    if unheld:
        emit("| permission | guard sites | endpoints |")
        emit("|---|---|---|")
        for p in unheld:
            emit("| `%s` | %d | %s |" % (p, len(sites_by_pair[p]), ", ".join(sites_by_pair[p][:6])))
    else:
        emit("None.")
    emit()

    emit("### A3 — satisfied only by the PLATFORM_ADMIN code bypass (%d, by design)" % len(bypass_only))
    emit()
    if bypass_only:
        emit("Intentional: the module name *is* the role, so these gates exist to say")
        emit("\"platform operator only\". They are listed because the catalogue rows are")
        emit("decorative — no tenant admin can ever grant them — and because removing the code")
        emit("bypass as a hardening step would silently close these endpoints.")
        emit()
        emit("| permission | guard sites | endpoints |")
        emit("|---|---|---|")
        for p in bypass_only:
            emit("| `%s` | %d | %s |" % (p, len(sites_by_pair[p]), ", ".join(sites_by_pair[p][:6])))
    else:
        emit("None.")
    emit()

    # ---------------------------------------------------------------- B: granted, unrestrictive
    threshold = int(total_roles * args.breadth)
    emit("## B. Granted but unrestrictive — the guard passes almost everyone")
    emit()
    emit("A guard on a permission most roles hold satisfies the authorization ratchet without")
    emit("restricting anyone. Threshold: more than %d of %d roles (%.0f%%)."
         % (threshold, total_roles, args.breadth * 100))
    emit()

    broad_writes = sorted(((p, holders[p]) for p in holders
                           if action_of(p) in WRITEISH and holders[p] > threshold),
                          key=lambda t: -t[1])
    emit("### B1 — state-changing permissions held by more than %.0f%% of roles (%d)"
         % (args.breadth * 100, len(broad_writes)))
    emit()
    if broad_writes:
        emit("| permission | roles | of | guarded sites | read-only holders |")
        emit("|---|---|---|---|---|")
        for p, n in broad_writes:
            ro = [r for r in READ_ONLY if p in held_by.get(r, ())]
            emit("| `%s` | **%d** | %d | %d | %s |"
                 % (p, n, total_roles, len(sites_by_pair.get(p, ())), ", ".join(ro) or "—"))
    else:
        emit("None.")
    emit()

    broad_exports = sorted(((p, holders[p]) for p in holders
                            if action_of(p) == "EXPORT" and holders[p] > threshold),
                           key=lambda t: -t[1])
    emit("### B2 — EXPORT held by more than %.0f%% of roles (%d)"
         % (args.breadth * 100, len(broad_exports)))
    emit()
    emit("F2-27 made these load-bearing. Until it shipped, `MODULE:EXPORT` gated nothing and the")
    emit("breadth of the grant did not matter; now it decides who can download a whole register")
    emit("as a file. No grant was re-reviewed when the meaning changed.")
    emit()
    if broad_exports:
        emit("| permission | roles | of | guarded sites | external / machine holders |")
        emit("|---|---|---|---|---|")
        for p, n in broad_exports:
            odd = [r for r in sorted(held_by) if p in held_by[r]
                   and (r in EXTERNAL or r in READ_ONLY or r.endswith(MACHINE_SUFFIX))]
            emit("| `%s` | **%d** | %d | %d | %s |"
                 % (p, n, total_roles, len(sites_by_pair.get(p, ())),
                    ", ".join(odd) if odd else "—"))
    else:
        emit("None.")
    emit()

    emit("### B3 — writes and exports held by the wrong kind of persona")
    emit()
    emit("| persona | class | state-changing held | EXPORT held |")
    emit("|---|---|---|---|")
    personas = [(r, "read-only") for r in READ_ONLY]
    personas += [(r, "external") for r in EXTERNAL if r not in READ_ONLY]
    personas += [(r, "machine") for r in sorted(roles) if r.endswith(MACHINE_SUFFIX)]
    persona_writes = 0
    for role, kind in personas:
        writes = sorted(p for p in held_by.get(role, ()) if action_of(p) in WRITEISH)
        exports = sorted(p for p in held_by.get(role, ()) if action_of(p) == "EXPORT")
        persona_writes += len(writes)
        emit("| `%s` | %s | %s | %s |"
             % (role, kind, ", ".join("`%s`" % w for w in writes) or "—",
                ", ".join("`%s`" % e for e in exports) or "—"))
    emit()
    emit("%d state-changing grants sit on %d personas that should hold none."
         % (persona_writes, len(personas)))
    emit()

    by_role_module = collections.defaultdict(set)
    for role, pairs_held in held_by.items():
        for p in pairs_held:
            by_role_module[(role, module_of(p))].add(action_of(p))
    asym = []
    for (role, mod), acts in sorted(by_role_module.items()):
        if "READ" in acts:
            continue
        escalating = sorted(acts & set(WRITEISH + ("EXPORT",)))
        if escalating:
            asym.append((role, mod, escalating))
    emit("### B4 — a module's actions held without that module's READ (%d)" % len(asym))
    emit()
    emit("Read this one carefully: **write-without-read is the correct shape for a machine**. An")
    emit("ingest account that POSTs findings and cannot read the register back is least privilege")
    emit("working, and the eight `*_INGEST` rows below are the pattern succeeding, not failing.")
    emit("The rows worth acting on are the human ones, where it usually means a grant was added")
    emit("action-by-action and the set drifted: `ORG_ADMIN` can write and delete cyber records it")
    emit("cannot list, and `AUTOMATION` has no `READ` in the catalogue at all, so the two roles")
    emit("that administer automation rules can never be granted read on them. The `BCM:EXPORT`")
    emit("rows are deliberate — V320 granted CFO and CISO the BCP binder download without")
    emit("widening BCM read — and `PLATFORM_ADMIN` is irrelevant here because it bypasses in code.")
    emit()
    emit("| persona | module | holds | READ? |")
    emit("|---|---|---|---|")
    for role, mod, escalating in asym:
        machine = role.endswith(MACHINE_SUFFIX)
        catalogued = ("%s:READ" % mod) in holders
        emit("| `%s`%s | `%s` | %s | %s |"
             % (role, " *(machine)*" if machine else "", mod,
                ", ".join("`%s`" % a for a in escalating),
                "not held" if catalogued else "**not in the catalogue**"))
    emit()

    # ---------------------------------------------------------------- C: granted, guards nothing
    orphans = sorted((p for p in holders if p not in code_pairs and holders[p] > 0),
                     key=lambda p: (-holders[p], p))
    emit("## C. Catalogued and granted, but guarding no endpoint (%d)" % len(orphans))
    emit()
    emit("No `@PreAuthorize` anywhere names these. Granting or revoking one changes nothing, so")
    emit("the role editor offers a checkbox that is pure theatre — and an auditor reading the role")
    emit("matrix will conclude a restriction exists where none does.")
    emit()
    emit("| permission | roles holding | note |")
    emit("|---|---|---|")
    for p in orphans:
        note = ""
        if action_of(p) == "EXPORT":
            note = "no file endpoint in this module"
        elif action_of(p) in WRITEISH:
            note = "**state-changing** — nothing enforces it"
        emit("| `%s` | %d | %s |" % (p, holders[p], note))
    emit()

    zero_and_dead = sorted(p for p in holders if p not in code_pairs and holders[p] == 0)
    emit("Catalogued, held by nobody, guarding nothing (%d): %s"
         % (len(zero_and_dead), ", ".join("`%s`" % p for p in zero_and_dead) or "—"))
    emit()

    # ---------------------------------------------------------------- per-module matrix
    emit("## D. Per-module matrix")
    emit()
    emit("`roles/sites` per cell — how many roles hold it, and how many endpoints it guards.")
    emit("A cell with roles and no sites is section C; a cell with sites and no roles is section A.")
    emit()
    actions = ["READ", "WRITE", "APPROVE", "DELETE", "EXPORT", "TRIAGE", "BREAK_GLASS"]
    modules = sorted({module_of(p) for p in list(holders) + list(code_pairs)})
    emit("| module | " + " | ".join(actions) + " |")
    emit("|---" * (len(actions) + 1) + "|")
    for m in modules:
        cells = []
        for a in actions:
            pair = "%s:%s" % (m, a)
            known = pair in holders
            sited = len(sites_by_pair.get(pair, ()))
            if not known and not sited:
                cells.append("·")
            else:
                cells.append("%s/%s" % (holders.get(pair, "—"), sited))
        emit("| `%s` | %s |" % (m, " | ".join(cells)))
    emit()

    emit("---")
    emit()
    emit("## What the first run found")
    emit()
    emit("This file is regenerated from live state, so a repaired finding disappears from it. The")
    emit("first run, 2026-07-26 on the dev tenant, found **one** section-A2 instance:")
    emit("`REPORTING:WRITE` guarding `ReportScheduleController#create`, `#update` and `#runNow`")
    emit("while held by zero of 57 roles — the entire write surface of scheduled reporting, 403 for")
    emit("every persona, invisible because `PLATFORM_ADMIN`'s code bypass masks it and fail-closed")
    emit("logs nothing. Repaired by **V321** (ORG_ADMIN + GRC_ADMINISTRATOR). Making those three")
    emit("endpoints reachable then exposed a second defect behind them: `ReportScheduleService`")
    emit("copied `name` and `description` but not `nameAr`/`descriptionAr`, so the API silently")
    emit("dropped both Arabic columns. Nobody could have hit it — no request had ever completed.")
    emit()
    emit("Tightening these grants is **F2-13b**, not this script: each change needs a per-tenant")
    emit("impact query first, because the same mistake has been made twice in the other")
    emit("direction — F2-27's plan pre-committed to an AND-chain that would have 403'd 8 of 18")
    emit("legitimate holders, and the grant table is what falsified it.")
    emit()
    emit("## What F2-13b has closed so far")
    emit()
    emit("**The first B1 row, 2026-07-29.** `WORKFLOW:WRITE` guarded")
    emit("`WorkflowRequestController#approve` and `#reject` while held by **55 of 57 roles**, so a")
    emit("VIEWER, a REGULATOR_VIEWER, an EXTERNAL_AUDITOR, a SUPPLIER_USER and all eight `*_INGEST`")
    emit("machine accounts could approve any workflow request — in the engine **14 modules** route")
    emit("their approvals through. `WORKFLOW:APPROVE` already existed and already guarded the same")
    emit("act in three sibling controllers (`ApprovalRequestController#transition`, both")
    emit("`ApprovalChainController` decisions, `WorkflowSubmissionController`), so this was an")
    emit("inconsistency rather than a missing feature. Fixed by moving the two guards and by")
    emit("**V324**, which grants `WORKFLOW:APPROVE` to the roles that already hold *some* module's")
    emit("APPROVE **and** already hold `WORKFLOW:WRITE`: 3 → 17 holders. Decision authority on a")
    emit("workflow request therefore went from 55 roles to 17, and the two guard sites moved out of")
    emit("the `WORKFLOW:WRITE` row above (18 → 16) into the `WORKFLOW:APPROVE` cell (3/4 → 17/6).")
    emit()
    emit("⚠ **This report caught the fix's own first draft.** The draft used only the")
    emit("already-an-approver-somewhere predicate, and regenerating the file added a section-B4 row:")
    emit("**CHANGE_MANAGER · WORKFLOW · APPROVE · READ not held** — a human persona holding no other")
    emit("WORKFLOW grant, given authority to decide records it could not list or open. B4 exists to")
    emit("say that write-without-read is correct for a *machine*; for a person it is a 403 halfway")
    emit("through a flow. Hence the second predicate, which also makes `WORKFLOW:APPROVE` holders a")
    emit("strict subset of `WORKFLOW:WRITE` holders. **A grant migration is exactly where the")
    emit("half-grant gets created, so V324 now throws if any holder lacks the module's READ.**")
    emit()
    emit("**Deliberately NOT changed**, so the remaining rows above are decisions rather than debt:")
    emit()
    emit("- `WORKFLOW:WRITE` stays at 55 of 57. After the split it means *participate* — create,")
    emit("  send, respond, submit, resubmit — and a SUPPLIER_USER answering a vendor attestation or")
    emit("  an ingest account raising a request needs exactly that. Revoking it to tidy the B1 row")
    emit("  would break real flows. The B3 count of 20 therefore stands unchanged, and B3's own")
    emit("  framing in B4 is why: for the eight machines these grants *are* least privilege.")
    emit("- `/review`, `/close`, `/reopen`, `/escalate` keep `WORKFLOW:WRITE`. `reviewer_id` and")
    emit("  `approver_id` are separate columns, so requiring APPROVE on `/review` would collapse a")
    emit("  two-stage gate into one. Whether finalising should need approval authority is a fair")
    emit("  question and not the reported defect; `WorkflowApproveAuthorityTest` pins all ten")
    emit("  lifecycle endpoints so the answer stays chosen rather than drifted into.")
    emit("- **Section C's dead permissions are untouched.** They guard nothing, so revoking them")
    emit("  changes no behaviour and only shrinks a checkbox list; each needs the endpoint question")
    emit("  answered first — is the permission dead, or is the *feature* missing its guard? That is")
    emit("  the A1/F2-23 failure mode in reverse.")

    with open(args.md, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")
    print()
    print("wrote %s" % args.md)


if __name__ == "__main__":
    main()
