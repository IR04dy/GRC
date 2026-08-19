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
        System.out.println("[F2-242] load-then-write-later, no tenant predicate resolved "
                           + "(FILED, unread -- a dataflow question):");
        residue.forEach(r -> System.out.println("    " + r));

        // Decrease-only, and deliberately NOT zero: these are unread. The number exists so the
        // next slice knows the size of what it inherited, and so a detector that stops matching
        // fails here rather than reporting success.
        assertThat(residue).as(
            "F2-242 measured 7 sites of this shape and read none of them; the two it DID read "
            + "(RiskAcceptanceService, EvidenceItemService) were reached through the decidable "
            + "single-expression shape. Read the route and probe it before writing a reason.")
            .hasSizeLessThanOrEqualTo(7);
        assertThat(residue).as("anti-vacuity: a resolver that stops matching reports an empty "
            + "residue, which is indistinguishable from a swept tree")
            .isNotEmpty();
    }
}
