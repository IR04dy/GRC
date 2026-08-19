package com.tss.grc.common.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-242 — <b>a load by id with no tenant predicate, followed by a write.</b>
 *
 * <h2>Why the platform-wide backstop cannot answer this</h2>
 *
 * <p>{@link TenantWriteGuardInterceptor#decide} returns {@code false} the moment
 * {@code Objects.equals(oldOrg, newOrg)} — its own comment reads <em>"Tenant unchanged — the normal
 * case."</em> That is correct for the question it asks: it catches a row being <b>moved</b> between
 * tenants (F2-22b) and an insert <b>landing</b> in the wrong one (F2-216). <b>It guards the tenant
 * COLUMN. Writing another tenant's row without touching its {@code organisation_id} is outside its
 * question by construction</b>, and `application-prod.yml` describes the bean as the
 * <em>"platform-wide Hibernate-level cross-tenant write backstop … so a single missed per-service
 * guard cannot silently write across tenants"</em> — which is true of the two shapes it sees and
 * false of this one. So the per-service predicate is the only control there is, and nothing was
 * counting the places it is missing.
 *
 * <p>🔑 <b>The discriminator is not the unscoped load — it is whether the foreign key was verified
 * when it was STORED.</b> {@code RiskAcceptanceService#setRiskStatus} carried the identical
 * unscoped {@code findById(...).ifPresent(...save...)} shape and was <b>safe</b>, because
 * {@code request()} had already resolved that {@code riskId} inside the tenant before storing it.
 * {@code ScmService#startOnboarding} stored {@code req.getVendorId()} verbatim, and its three
 * transitions then dereferenced it — so the same shape was a live cross-tenant write. Probed live
 * as {@code ranalyst} (organisation 1, holding only {@code SCM:WRITE}) against a planted
 * organisation-2 victim: the create answered <b>201</b> and moved the foreign vendor
 * {@code REJECTED → IN_PROGRESS}; {@code /approve} took its {@code status} from <b>BLACKLISTED to
 * ACTIVE</b>. No audit row named the vendor, the attacker could not read it back, and the victim
 * tenant has no user to notice.
 *
 * <h2>What this gate measures, and what it deliberately does not</h2>
 *
 * <p>The <b>decidable</b> shape is the single-expression one:
 * {@code repo.findById(x).ifPresent(v -> { …setX(…); repo.save(v); })}. One statement, one
 * receiver, no dataflow — a regex over a brace-balanced argument settles it. The population is
 * <b>derived</b>: repository fields are read from each class's own declarations, each repository is
 * resolved to its entity, and an entity is in scope only if it carries a tenant column. Nothing is
 * listed by hand.
 *
 * <p>The broader <em>load-then-write-later</em> shape (an assignment, a setter somewhere below, a
 * save at the end) is a <b>dataflow</b> question a regex cannot answer — F2-182's rule — so it is
 * measured, printed and <b>filed</b> rather than asserted. Writing verdicts for code this slice has
 * not read is F2-188's failure mode, and its measured rate is five claims, five false.
 *
 * <h2>⚠ The detector bug that produced this gate, because it is the reusable part</h2>
 *
 * <p>The reconnaissance pass matched
 * {@code \.ifPresent\(([^;]{0,600}?)\)} — and <b>a lambda body is a statement list</b>, so a
 * character class excluding {@code ;} can never match the very shape being hunted. It reported
 * <b>0</b> on a tree holding seven, which is indistinguishable from a clean tree, and it
 * <b>under</b>-reports — the quiet direction. Hence {@link #balanced} and
 * {@link #theDetectorFiresOnTheLiteralDefectShape()}: the argument is extracted by a
 * paren-balanced walk, and a synthetic self-test drives the detector across the literal pre-fix
 * source so a rotted pattern fails loudly instead of reporting success (F2-195).
 */
class UnverifiedForeignKeyWriteContractTest {

    private static final Path MAIN =
        Path.of("src", "main", "java", "com", "tss", "grc");

    /**
     * The one site that legitimately loads a tenant-bearing entity by id and writes it with no
     * tenant predicate. <b>Read, not inferred</b>, and enforced in both directions: if it stops
     * being found by the scan, this map must lose its entry in the same commit.
     *
     * <p>⚠ A hard zero would fail on correct code here, which is the one thing a gate must never do
     * (F2-214) — so this is a named map with a reason, not an empty exemption set.
     */
    private static final Map<String, String> VERIFIED_BY_DESIGN = Map.of(
        "module/auth/LoginAttemptService.java#userRepository",
        "AUTHENTICATION, BEFORE ANY TENANT EXISTS. `onSuccess(Long userId)` is called from the "
        + "login flow with the id of the principal who has just authenticated — resolved by the "
        + "authenticator, never supplied by a caller — and TenantContext is not populated at that "
        + "point, so there is no tenant to scope to and `orgId()` would throw. Its sibling "
        + "`onFailure(String username)` is the same path by username. Scoping either one would "
        + "break login lockout bookkeeping, which is the opposite of a security fix."
    );

    /**
     * F2-243 — the seven load-then-write-later sites F2-242 measured, printed and filed WITHOUT
     * verdicts, now <b>read</b>. All seven are refuted: the reference is verified at every one.
     *
     * <p>🔑 <b>They were invisible to the residue detector because it looks for ONE spelling of the
     * predicate — {@code <var>.getOrganisationId()} on the loaded variable — and this tree verifies
     * a reference in at least four different ways.</b> That is why a detector reporting zero
     * defects over its own population is a statement about the detector, not about the code, and
     * why the number below can never be the assertion (F2-234).
     *
     * <p>Keyed {@code file#receiver} — the two {@code TprmService} sites and the two
     * {@code OutsourcingService} sites share a repository and a verdict, so seven sites are five
     * keys. Enforced in BOTH directions: a fixed site must lose its entry in the same commit, and a
     * verdict for something the scan no longer finds fails just as loudly (F2-199's direction).
     */
    private static final Map<String, String> LOAD_THEN_WRITE_VERDICTS = Map.of(
        "module/outsourcing/service/OutsourcingService.java#arrangementRepo",
        "REFUTED — VERIFIED AT STORE, by a tenant-scoped finder. `createReview` and "
        + "`createExitPlan` both call `loadArrangement(req.arrangementId())`, which is "
        + "`findByOrganisationIdAndId(org(), id)` and throws, before the id reaches the column; and "
        + "`transitionReview`/`transitionExitPlan` read that id off a row they loaded with "
        + "`findByOrganisationIdAndId` themselves. So the id being dereferenced is twice-verified "
        + "tenant data. This is F2-242's own negative control shape (RiskAcceptanceService).",

        "module/tprm/service/TprmService.java#assessmentRepo",
        "REFUTED — VERIFIED AT STORE, via the PARENT. `createAssessment` calls "
        + "`getVendor(req.getVendorId())` (a tenant filter that throws) and stamps the assessment's "
        + "organisation from the vendor it resolved, and `updateAssessment` writes no vendorId, so "
        + "assessment.organisationId can never diverge from its vendor's. `updateAssessment` and "
        + "`deleteAssessment` then guard with `getVendor(existing.getVendorId())` under a "
        + "SEC-TPRM-001 comment. ⚠ That comment's stated REASON is stale — it says "
        + "\"VendorAssessment has no organisation_id\" and the entity has declared one since "
        + "PERF-PG-002 added it as a denormalised tenant anchor. The guard is sound; the "
        + "justification for its shape is not, which is why the comment is corrected in the same "
        + "commit rather than left to mislead the next reader (F2-184).",

        "module/risk/service/RiskTreatmentService.java#treatments",
        "REFUTED — VERIFIED AT STORE, via the PARENT. `create(riskId, req)` calls "
        + "`ensureRiskInTenant(riskId)` before building the row, and `get`/`update`/`transition`/"
        + "`delete` each call `ensureRiskInTenant(t.getRiskId())` — a tenant comparison that "
        + "deliberately throws 404 rather than 400 so ids cannot be enumerated across tenants "
        + "(SEC-RISK-001). The predicate is on the PARENT risk, not on the treatment, so a scan "
        + "asking for `t.getOrganisationId()` cannot see it.",

        "module/iam/governance/service/AccessGovernanceService.java#roles",
        "REFUTED — THE ID IS NOT CALLER DATA. `createRole`'s revive branch loads `retiredId`, which "
        + "came from `roles.findRetiredRoleId(org, r.roleName())` — a native query whose WHERE "
        + "clause is `organisation_id = :org AND UPPER(role_name) = UPPER(:name) AND is_deleted = "
        + "1`. The caller supplies a NAME, never an id, and the tenant is in the query. The native "
        + "query exists because Role carries @Where(is_deleted = 0), so JPA cannot see the retired "
        + "row it has to revive.",

        "module/platform/service/NotificationService.java#notificationRepository",
        "REFUTED — SCOPED BY RECIPIENT, WHICH IS NARROWER THAN THE TENANT. `markAsRead(id, userId)` "
        + "filters `notif.getRecipientId().equals(userId)`, and its one caller passes "
        + "`user.getUserId()` from `@AuthenticationPrincipal` — so it is the authenticated reader's "
        + "own notification or nothing. A recipient belongs to exactly one organisation, so this "
        + "implies the tenant check rather than omitting it."
    );

    /**
     * F2-243 — the PLURAL form, {@code repo.findAllById(<a caller-supplied id list>)}, which the
     * singular detector cannot express by construction.
     *
     * <p>Six sites; five refuted because the ids come from rows the service already fetched
     * tenant-scoped, and the exemplar for the shape is already in the tree:
     * {@code PolicyManagementService#setControlLinks} does {@code findAllById(controlIds)} and then
     * {@code if (orgId.equals(c.getOrganisationId()))} on every row before using it.
     */
    private static final Map<String, String> PLURAL_LOAD_VERDICTS = Map.of(
        "module/iam/service/UserService.java#roleRepository",
        "FILED, LATENT — `assignRolesByNameOrId` has TWO branches that are not equivalent: the "
        + "roleNames branch resolves through `findByOrganisationIdOrOrganisationIdIsNull(orgId)`, "
        + "the roleIds branch is a bare `findAllById(roleIds)` with no tenant predicate. Latent "
        + "today because all 69 active roles in dev (58 in prod) are `organisation_id IS NULL` "
        + "global templates, so both branches return the same set; it goes live the moment any "
        + "tenant creates a bespoke role, which `AccessGovernanceService#createRole` stamps with "
        + "`organisation_id = org()`. Both write routes are `hasRole('PLATFORM_ADMIN')`, who is "
        + "already trans-tenant by design, so it buys an attacker nothing today. ⚠ `createIn`'s "
        + "javadoc explains why role resolution is tenant-correct and describes the roleNames "
        + "branch ONLY — F2-215's two-of-N, in the comment a reader consults first.",

        "module/ccm/service/CcmService.java#defRepo",
        "REFUTED — the ids come from SERVER ROWS. `listLatestRuns` collects `defIds` from runs "
        + "fetched by `runRepo.findRecent(orgId, …)`, so every id was already inside the tenant.",

        "module/evidence/service/EvidenceItemService.java#userRepository",
        "REFUTED — a DISPLAY-NAME lookup in a read-only batch mapper (`toResponses`), whose "
        + "`ownerIds` come from the items themselves; no write, and no id from the caller.",

        "module/policy/service/PolicyManagementService.java#controls",
        "REFUTED — `resolveControlsForPolicy` takes its ids from "
        + "`controlLinks.findByOrganisationIdAndPolicyDocumentId(org(), policyId)`. And the WRITE "
        + "path beside it, `setControlLinks`, is the exemplar for this whole shape: it filters "
        + "every row `if (orgId.equals(c.getOrganisationId()))` before adding it.",

        "module/risk/service/RiskService.java#riskCategoryRepository",
        "REFUTED — a display-name map for a read-only list projection. `catIds` are collected from "
        + "risks the tenant-scoped page query already returned, so every id is inside the tenant "
        + "before the batch load runs; the map is consumed only to label a row, and no write "
        + "follows it.",

        "module/risk/service/RiskService.java#userRepository",
        "REFUTED — the owner-name half of the same read-only projection, two lines below the "
        + "category half. `ownerIds` are collected from risks the tenant-scoped page query already "
        + "returned, the map is consumed only to render a display name, and no write follows. The "
        + "caller supplies a page request, never an id.",

        "module/admin/committee/service/CommitteeMemberRegistryService.java#users",
        "REFUTED — SCOPED IN THE LOOP. `linkage` batches the roster's linked user ids and admits "
        + "each row only `if (org.equals(u.getOrganisationId()))`, so an unlinked-or-foreign seat "
        + "falls out of the map and reads as unlinked rather than surfacing a foreign name.",

        "module/change_adapters/EvidenceAdapter.java#items",
        "REFUTED — SCOPED IN THE STREAM, deliberately and with provenance. It filters "
        + "`org.equals(e.getOrganisationId())` on the ITEM as well as on the link row, under a "
        + "comment citing F2-106: a legacy link can point outside the tenant, so such a row must "
        + "vanish from the read rather than surface a foreign title. The codebase already knew this "
        + "shape at one site — which is the argument for measuring the whole population rather than "
        + "trusting that the knowledge spread.",

        "module/cyber/asm/service/AttackSurfaceFindingService.java#assetRepo",
        "REFUTED — SCOPED IN THE STREAM. `mapWithAssets` filters "
        + "`orgId.equals(a.getOrganisationId())` before building the map, so a finding whose asset "
        + "is not the tenant's renders with no asset rather than another tenant's hostname.",

        "module/ethics/coi/service/CoiService.java#users",
        "REFUTED — SCOPED IN THE LOOP, and this gate caught the site in the commit that added it. "
        + "F2-244's `resolveSubjects` batches the recusal subjects for a page of rows and admits "
        + "each only `if (Objects.equals(u.getOrganisationId(), org()) && isActive)`, so an id that "
        + "resolves to another tenant's user falls out of the map and the response reports "
        + "`userResolved = false` — the disclosure — rather than surfacing a foreign person's name. "
        + "⚠ Worth recording that the gate fired on a NEW site rather than a legacy one: the "
        + "verdict-per-site rule F2-243 added (instead of a decrease-only count) is what made an "
        + "addition fail loudly rather than sit inside a tolerated total."
    );

    // ── the detector ────────────────────────────────────────────────────────────

    /** A single-expression load-and-write with no tenant predicate. */
    private record Site(String file, int line, String receiver, String entity, String body) {
        String key() { return file + "#" + receiver; }
    }

    private static final Pattern FIND_BY_ID = Pattern.compile("(\\w+)\\.findById\\s*\\(");
    private static final Pattern REPO_FIELD =
        Pattern.compile("private\\s+final\\s+(\\w*Repository)\\s+(\\w+)\\s*;");
    private static final Pattern REPO_ENTITY = Pattern.compile(
        "interface\\s+(\\w+)\\s+extends\\s+\\w*JpaRepository\\s*<\\s*([\\w.]+)\\s*,");
    private static final Pattern SETTER = Pattern.compile("\\.set[A-Z]\\w*\\s*\\(");
    private static final Pattern SAVE = Pattern.compile("\\.save\\w*\\s*\\(");
    private static final Pattern OWN_ORG_FIELD =
        Pattern.compile("\\bprivate\\s+Long\\s+organisationId\\b");
    private static final Pattern EXTENDS_BASE = Pattern.compile("\\bextends\\s+BaseEntity\\b");

    /**
     * F2-198's stripper, as ONE left-to-right alternation. Two sequential passes let a {@code /*}
     * inside a {@code //} prose comment run to the next closing delimiter and swallow real
     * declarations, which shrinks the population — and a smaller population reads exactly like a
     * clean tree. Replacing with spaces keeps every offset, so reported line numbers stay true.
     */
    static String stripComments(String src) {
        Matcher m = Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/").matcher(src);
        StringBuilder out = new StringBuilder(src);
        while (m.find()) {
            for (int i = m.start(); i < m.end(); i++) {
                if (out.charAt(i) != '\n' && out.charAt(i) != '\r') out.setCharAt(i, ' ');
            }
        }
        return out.toString();
    }

    /**
     * Return the argument text of the parenthesised group opening at {@code open}, or {@code null}
     * if it never closes. String and char literals are skipped so a {@code ")"} inside a message
     * cannot end the walk.
     */
    static String balanced(String src, int open) {
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'') {
                char q = c;
                i++;
                while (i < src.length() && src.charAt(i) != q) i += src.charAt(i) == '\\' ? 2 : 1;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return src.substring(open + 1, i);
            }
        }
        return null;
    }

    private record Corpus(Map<String, Boolean> entityHasTenant, Map<String, String> repoEntity) { }

    private static List<Path> javaFiles() {
        try (Stream<Path> s = Files.walk(MAIN)) {
            return s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return stripComments(Files.readString(p, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Corpus corpus() {
        Map<String, Boolean> entities = new TreeMap<>();
        Map<String, String> repos = new TreeMap<>();
        for (Path p : javaFiles()) {
            String src = read(p);
            String cls = p.getFileName().toString().replace(".java", "");
            if (src.contains("@Entity")) {
                entities.put(cls, OWN_ORG_FIELD.matcher(src).find()
                                  || EXTENDS_BASE.matcher(src).find());
            }
            Matcher m = REPO_ENTITY.matcher(src);
            while (m.find()) {
                String entity = m.group(2);
                repos.put(m.group(1), entity.substring(entity.lastIndexOf('.') + 1));
            }
        }
        return new Corpus(entities, repos);
    }

    /** Scan one source text; {@code label} is only used for reporting. */
    private static List<Site> scan(String label, String src, Corpus c) {
        Map<String, String> fieldToRepo = new LinkedHashMap<>();
        Matcher f = REPO_FIELD.matcher(src);
        while (f.find()) fieldToRepo.put(f.group(2), f.group(1));

        List<Site> found = new ArrayList<>();
        Matcher m = FIND_BY_ID.matcher(src);
        while (m.find()) {
            String receiver = m.group(1);
            String repo = fieldToRepo.get(receiver);
            if (repo == null) continue;                       // not a repository field of this class
            String entity = c.repoEntity().get(repo);
            if (entity == null) continue;                     // repository not resolved to an entity
            Boolean tenanted = c.entityHasTenant().get(entity);
            if (tenanted == null || !tenanted) continue;      // no tenant column: out of scope

            int open = m.end() - 1;
            String idArg = balanced(src, open);
            if (idArg == null) continue;
            int after = open + idArg.length() + 2;
            String tail = src.substring(after, Math.min(src.length(), after + 40));
            int ip = tail.indexOf(".ifPresent");
            if (ip < 0 || !tail.substring(0, ip).isBlank()) continue;   // not this shape
            int bodyOpen = src.indexOf('(', after + ip);
            String body = balanced(src, bodyOpen);
            if (body == null) continue;
            if (!SETTER.matcher(body).find() || !SAVE.matcher(body).find()) continue;

            // A tenant predicate may sit in the chain (`.filter(v -> v.getOrganisationId()…)`,
            // which lands between findById and ifPresent) or in the id expression itself
            // (`findByIdAndOrganisationId` is a different method and never reaches here).
            String chain = src.substring(after, bodyOpen);
            if (chain.contains("getOrganisationId") || body.contains("getOrganisationId")) continue;

            int line = (int) src.substring(0, m.start()).chars().filter(ch -> ch == '\n').count() + 1;
            found.add(new Site(label, line, receiver, entity, " ".join("", body.split("\\s+"))));
        }
        return found;
    }

    private static List<Site> scanTree() {
        Corpus c = corpus();
        List<Site> all = new ArrayList<>();
        for (Path p : javaFiles()) {
            String rel = MAIN.relativize(p).toString().replace('\\', '/');
            all.addAll(scan(rel, read(p), c));
        }
        return all;
    }

    // ── legs ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("no service loads a tenant-bearing row by id and writes it without a tenant predicate")
    void noUnscopedLoadWritesATenantBearingRow() {
        List<Site> sites = scanTree();
        List<String> unexplained = sites.stream()
            .filter(s -> !VERIFIED_BY_DESIGN.containsKey(s.key()))
            .map(s -> s.file() + ":" + s.line() + "  " + s.receiver() + ".findById(...) -> "
                      + s.entity() + "   body: "
                      + s.body().substring(0, Math.min(120, s.body().length())))
            .toList();

        assertThat(unexplained).as("""
            A load by id with no tenant predicate, followed by a write, on an entity that carries \
            organisation_id. TenantWriteGuardInterceptor cannot see this: it guards the tenant \
            COLUMN (a row MOVED between tenants, or an insert LANDING in the wrong one), and a \
            write to somebody else's row leaves organisation_id untouched.

            Fix it at the point the reference is STORED -- resolve the id inside the caller's \
            tenant before writing it to the column -- and scope this load as defence in depth. \
            ScmService#requireOwnVendor is the shape. If the site is genuinely correct, add it to \
            VERIFIED_BY_DESIGN with a reason naming WHY there is no tenant to scope to; the \
            reverse leg then holds you to it.""")
            .isEmpty();
    }

    @Test
    @DisplayName("every by-design exemption is still a site the scan finds")
    void everyExemptionIsStillReachedByTheScan() {
        Set<String> keys = new LinkedHashSet<>();
        for (Site s : scanTree()) keys.add(s.key());

        // The direction nobody writes first (F2-199): an exemption for something that is no longer
        // in the population reads exactly like an exemption for something that is, and its reason
        // -- however fluent -- is then documenting code that does not exist.
        assertThat(keys).as(
            "VERIFIED_BY_DESIGN entries must still name real, still-unscoped sites; a fixed one "
            + "must lose its entry in the same commit")
            .containsAll(VERIFIED_BY_DESIGN.keySet());
    }

    @Test
    @DisplayName("a by-design reason must say why there is no tenant to scope to")
    void everyReasonIsSubstantial() {
        VERIFIED_BY_DESIGN.forEach((site, reason) -> {
            assertThat(reason).as("reason for %s is a bare label", site).hasSizeGreaterThan(140);
            assertThat(reason).as("""
                the reason for %s must name the mechanism that removes the tenant question -- \
                "off-request", "no tenant", "authentication", or a verified-at-store argument. \
                A fluent sentence that merely restates the code reads as verification and is not \
                (F2-188).""", site)
                .containsAnyOf("TENANT", "tenant", "off-request", "AUTHENTICATION", "verified");
        });
    }

    @Test
    @DisplayName("the detector fires on the literal pre-fix source, and not on the fixed forms")
    void theDetectorFiresOnTheLiteralDefectShape() {
        Corpus c = corpus();

        // The exact line this slice removed from ScmService, and the exact fixed replacement.
        String pre = """
            class X {
              private final VendorRepository vendorRepo;
              void f(Long id) {
                vendorRepo.findById(id).ifPresent(v -> { v.setOnboardingStatus("IN_PROGRESS"); vendorRepo.save(v); });
              }
            }
            """;
        String post = """
            class X {
              private final VendorRepository vendorRepo;
              void f(Long id, Long orgId) {
                vendorRepo.findById(id)
                    .filter(v -> v.getOrganisationId().equals(orgId))
                    .ifPresent(v -> { v.setOnboardingStatus("IN_PROGRESS"); vendorRepo.save(v); });
              }
            }
            """;
        assertThat(scan("synthetic-pre", stripComments(pre), c))
            .as("the detector must fire on the shape F2-242 fixed -- the reconnaissance pass used "
              + "`[^;]{0,600}` for the lambda body, which CANNOT match a statement list, and so "
              + "reported 0 on a tree holding 7")
            .hasSize(1);
        assertThat(scan("synthetic-post", stripComments(post), c))
            .as("...and must NOT fire once the tenant predicate is in the chain")
            .isEmpty();

        // Two shapes it must never flag, so the invariant cannot be satisfied by over-matching.
        String readOnly = """
            class X {
              private final VendorRepository vendorRepo;
              String f(Long id) { return vendorRepo.findById(id).map(v -> v.getNameEn()).orElse(null); }
            }
            """;
        String noTenantColumn = """
            class X {
              private final EvidenceFrameworkRepository frameworkRepository;
              void f(Long id) {
                frameworkRepository.findById(id).ifPresent(v -> { v.setIsActive(false); frameworkRepository.save(v); });
              }
            }
            """;
        assertThat(scan("synthetic-read", stripComments(readOnly), c))
            .as("a read is not a cross-tenant WRITE").isEmpty();
        assertThat(scan("synthetic-untenanted", stripComments(noTenantColumn), c))
            .as("an entity with no organisation_id has no tenant to cross").isEmpty();
    }

    @Test
    @DisplayName("the comment stripper is one alternation, and keeps every offset")
    void theStripperIsOneLeftToRightAlternation() {
        // F2-198's exact trap: a `//` prose comment whose text contains `/*`. Written as two
        // sequential passes the block pattern runs from there to the next `*/` and eats the code
        // between -- 45% of App.tsx, in the instance that recorded this.
        String src = "int a = 1; // a comment mentioning /* a block opener\nint b = 2;\n/* real */ int c = 3;";
        String out = stripComments(src);
        assertThat(out).as("the declaration AFTER the prose comment must survive").contains("int b = 2;");
        assertThat(out).as("and the one after the real block comment too").contains("int c = 3;");
        assertThat(out).as("comment text itself must be gone").doesNotContain("block opener");
        assertThat(out.length()).as("offsets must be preserved so reported line numbers are true")
            .isEqualTo(src.length());
        assertThat(out.chars().filter(ch -> ch == '\n').count())
            .as("newlines must be preserved for the same reason")
            .isEqualTo(src.chars().filter(ch -> ch == '\n').count());
    }

    @Test
    @DisplayName("the population the scan resolves is floored, so a broken resolver fails loudly")
    void theResolvedPopulationIsFloored() {
        Corpus c = corpus();
        long tenanted = c.entityHasTenant().values().stream().filter(Boolean::booleanValue).count();

        // A floor must sit above the largest WRONG answer, not above zero (F2-215). The wrong
        // answers here are "the walk found no files" and "no repository resolved to an entity" --
        // both of which make the invariant vacuously green.
        assertThat(c.entityHasTenant()).as("@Entity classes discovered under src/main/java")
            .hasSizeGreaterThanOrEqualTo(380);
        assertThat(tenanted).as("entities carrying organisation_id (measured 376 at F2-242)")
            .isGreaterThanOrEqualTo(340);
        assertThat(c.repoEntity()).as("repositories resolved to their entity (measured 414)")
            .hasSizeGreaterThanOrEqualTo(380);
        assertThat(javaFiles()).as("source files walked").hasSizeGreaterThanOrEqualTo(1500);
    }

    @Test
    @DisplayName("the two closed registers really are closed, at the site")
    void theFixedSitesAreAssertedByName() {
        // Named explicitly because the invariant above passes just as well if these files were
        // DELETED. A hard zero over an empty population is the failure mode of every derived gate.
        String scm = read(MAIN.resolve("module/scm/service/ScmService.java"));
        assertThat(scm).as("the SCM vendor reference must be resolved inside the tenant")
            .containsPattern("private\\s+Vendor\\s+requireOwnVendor\\s*\\(");
        assertThat(scm).as("startOnboarding must resolve the vendor before storing the reference")
            .containsPattern("requireOwnVendor\\(req\\.getVendorId\\(\\)\\)");
        assertThat(scm).as("no vendor write in ScmService may go through an unpredicated findById")
            .doesNotContainPattern("vendorRepo\\.findById\\([^)]*\\)\\.ifPresent");
        assertThat(scm).as("the shared write helper must carry the tenant predicate")
            .containsPattern("filter\\(v -> v\\.getOrganisationId\\(\\)\\.equals\\(orgId\\)\\)");

        String evidence = read(MAIN.resolve("module/evidence/service/EvidenceItemService.java"));
        assertThat(evidence).as(
            "the nightly sweep must anchor on the ITEM's tenant, not on TenantContext, because it "
            + "runs across every organisation")
            .containsPattern(
                "filter\\(r -> r\\.getOrganisationId\\(\\)\\.equals\\(item\\.getOrganisationId\\(\\)\\)\\)");
        assertThat(evidence).as("and the create must refuse a request of another tenant")
            .containsPattern("Evidence request not found");
    }

    @Test
    @DisplayName("the load-then-write-later population is measured and filed, never asserted")
    void theDataflowShapeIsMeasuredAndFiled() {
        // F2-182: "is this reference verified before use?" is a dataflow question, and a regex can
        // only tell you where to go and read. So this leg prints the residue for the next slice
        // and asserts only that the measurement still runs -- it does NOT claim the sites are
        // defects, and it does NOT carry reasons for code nobody has read (F2-188).
        Pattern assign = Pattern.compile(
            "(\\w+)\\s+(\\w+)\\s*=\\s*(\\w+)\\.findById\\s*\\(");
        Corpus c = corpus();
        List<String> residue = new ArrayList<>();
        for (Path p : javaFiles()) {
            String src = read(p);
            Map<String, String> fields = new LinkedHashMap<>();
            Matcher f = REPO_FIELD.matcher(src);
            while (f.find()) fields.put(f.group(2), f.group(1));
            Matcher m = assign.matcher(src);
            while (m.find()) {
                // The receiver is often not a repository field at all (`opt.findById` on a local,
                // a nested builder). `repoEntity` is a TreeMap, so asking it for a null key throws
                // rather than answering absent -- guard first.
                String repo = fields.get(m.group(3));
                if (repo == null) continue;
                String entity = c.repoEntity().get(repo);
                if (entity == null || !Boolean.TRUE.equals(c.entityHasTenant().get(entity))) continue;
                int open = m.end() - 1;
                String id = balanced(src, open);
                if (id == null) continue;
                int after = open + id.length() + 2;
                String tail = src.substring(after, Math.min(src.length(), after + 260));
                if (tail.contains("getOrganisationId")) continue;   // scoped in the chain
                String var = m.group(2);
                String below = src.substring(after, Math.min(src.length(), after + 4000));
                boolean written = Pattern.compile(Pattern.quote(var) + "\\.set[A-Z]").matcher(below).find()
                    && Pattern.compile("\\.save\\w*\\(\\s*" + Pattern.quote(var) + "\\b").matcher(below).find();
                boolean laterCheck = below.length() > 0
                    && Pattern.compile(Pattern.quote(var) + "\\.getOrganisationId\\(\\)")
                        .matcher(below.substring(0, Math.min(1200, below.length()))).find();
                if (written && !laterCheck) {
                    int line = (int) src.substring(0, m.start()).chars()
                        .filter(ch -> ch == '\n').count() + 1;
                    residue.add(MAIN.relativize(p).toString().replace('\\', '/') + ":" + line
                                + "  " + m.group(3) + ".findById -> " + entity + " (as " + var + ")");
                }
            }
        }
        residue.sort(String::compareTo);
        System.out.println("[F2-243] load-then-write-later, no `<var>.getOrganisationId()` in the "
                           + "chain (ALL SEVEN ADJUDICATED -- see LOAD_THEN_WRITE_VERDICTS):");
        residue.forEach(r -> System.out.println("    " + r));

        // F2-243: every site now carries a verdict, so the FORWARD assertion is that nothing is
        // unadjudicated -- never that the count is small. F2-234's rule: a work list reaching zero
        // is exactly what a rotted detector produces, so the number cannot be the assertion.
        List<String> unadjudicated = residue.stream()
            .filter(r -> LOAD_THEN_WRITE_VERDICTS.keySet().stream().noneMatch(k -> r.startsWith(
                k.substring(0, k.indexOf('#')))
                && r.contains(k.substring(k.indexOf('#') + 1) + ".findById")))
            .toList();
        assertThat(unadjudicated).as("""
            A load-then-write-later site with no verdict. This is a DATAFLOW question a regex \
            cannot settle (F2-182), so read the route -- and read what stores the reference, not \
            just what dereferences it. F2-243 read all seven of F2-242's and refuted all seven: \
            the reference is verified at every one, by four different mechanisms this detector's \
            single spelling cannot see (a tenant-scoped finder, a helper resolving the parent, an \
            org-scoped native query, and a recipient identity narrower than the tenant). Add a \
            verdict naming WHICH mechanism, or fix the site.""")
            .isEmpty();

        assertThat(residue).as("anti-vacuity: a resolver that stops matching reports an empty "
            + "residue, which is indistinguishable from a swept tree")
            .isNotEmpty();
    }

    @Test
    @DisplayName("every load-then-write verdict still names a site the scan finds, and says how")
    void everyLoadThenWriteVerdictIsStillReachedAndSubstantial() {
        // The direction nobody writes first (F2-199). A verdict for something no longer in the
        // population reads exactly like a verdict for something that is -- and after F2-243 the
        // forward leg is satisfied by an EMPTY residue, so without this leg a detector that stopped
        // matching would report success on every site at once.
        List<String> found = new ArrayList<>();
        Pattern assign = Pattern.compile("(\\w+)\\s+(\\w+)\\s*=\\s*(\\w+)\\.findById\\s*\\(");
        for (Path p : javaFiles()) {
            String rel = MAIN.relativize(p).toString().replace('\\', '/');
            Matcher m = assign.matcher(read(p));
            while (m.find()) found.add(rel + "#" + m.group(3));
        }
        assertThat(found).as(
            "each adjudicated site must still be a `<Type> v = <repo>.findById(` the scan reaches")
            .containsAll(LOAD_THEN_WRITE_VERDICTS.keySet());

        LOAD_THEN_WRITE_VERDICTS.forEach((site, reason) -> {
            assertThat(reason).as("verdict for %s is a bare label", site).hasSizeGreaterThan(180);
            assertThat(reason).as("""
                the verdict for %s must open with REFUTED / FILED / VERIFIED and name the \
                mechanism. F2-188 measured what happens when a slice writes fluent reasons for \
                code it has not read: five claims, five false -- and fluency reads as \
                verification.""", site)
                .containsAnyOf("REFUTED", "FILED", "VERIFIED");
        });
    }

    @Test
    @DisplayName("the PLURAL findAllById form is adjudicated too — a singular detector cannot see it")
    void thePluralLoadFormIsAdjudicated() {
        // F2-243: `(\w+)\s+(\w+)\s*=\s*(\w+)\.findById\(` is singular by construction, so a
        // caller-supplied ID LIST sat outside every leg of this gate at once. Same shape as
        // F2-198's recurring finding: the invariant is real, its POPULATION is narrower than it
        // looks -- here narrowed by the arity of the finder.
        Corpus c = corpus();
        Pattern plural = Pattern.compile("(\\w+)\\.findAllById\\s*\\(");
        Set<String> found = new LinkedHashSet<>();
        for (Path p : javaFiles()) {
            String src = read(p);
            Map<String, String> fields = new LinkedHashMap<>();
            Matcher f = REPO_FIELD.matcher(src);
            while (f.find()) fields.put(f.group(2), f.group(1));
            Matcher m = plural.matcher(src);
            while (m.find()) {
                String repo = fields.get(m.group(1));
                if (repo == null) continue;
                String entity = c.repoEntity().get(repo);
                if (entity == null || !Boolean.TRUE.equals(c.entityHasTenant().get(entity))) continue;
                found.add(MAIN.relativize(p).toString().replace('\\', '/') + "#" + m.group(1));
            }
        }
        System.out.println("[F2-243] findAllById on a tenant-bearing entity: " + found.size()
                           + " receiver(s)");
        found.forEach(s -> System.out.println("    " + s));

        assertThat(found).as("""
            A `findAllById(<caller-supplied ids>)` on a tenant-bearing entity with no verdict. The \
            exemplar for this shape is already in the tree: PolicyManagementService#setControlLinks \
            filters every returned row `if (orgId.equals(c.getOrganisationId()))` before using it. \
            Either do that, or record why the ids cannot be the caller's.""")
            .containsExactlyInAnyOrderElementsOf(PLURAL_LOAD_VERDICTS.keySet());

        PLURAL_LOAD_VERDICTS.forEach((site, reason) -> {
            assertThat(reason).as("verdict for %s is a bare label", site).hasSizeGreaterThan(150);
            assertThat(reason).as("verdict for %s must open with REFUTED or FILED", site)
                .containsAnyOf("REFUTED", "FILED");
        });
    }
}
