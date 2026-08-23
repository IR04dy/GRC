package com.tss.grc.module.ethics.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ServerOwnedState;
import com.tss.grc.module.ethics.entity.Disclosure;
import com.tss.grc.module.ethics.entity.EthicsCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F2-306 — a business reference is settable while absent and immutable once set.
 *
 * <h3>What this is testing and why it had no test before</h3>
 *
 * {@code ClientSuppliedReferenceRatchetTest}'s business-reference leg is scoped to
 * {@code CREATE_ISH}, a verb list that deliberately excludes {@code update} because its claim is
 * <em>"a reference is chosen when a record is born"</em>. The <b>rename</b> question is therefore
 * outside its population by construction — and renaming is arguably the sharper harm: the record
 * exists, an auditor may already cite the code, and the citation breaks silently.
 *
 * <p>Measured across the tree: <b>44 update-path sites in 34 services</b> write a reference-shaped
 * field from the request. They split three ways, and only one kind is this defect — see
 * {@code ClientSuppliedReferenceRatchetTest}'s census leg. The two closed here are the kind-1
 * members in {@code ethics}: {@code EthicsCase#caseCode} and {@code Disclosure#disclosureCode}, both
 * reached through an {@code apply(target, src)} helper <b>shared by create and update</b>, and
 * neither minted by anything.
 *
 * <p>⚠ {@code DisclosureService} had <b>no test class at all</b>, and a path with no test is not a
 * path somebody decided was safe ([[F2-248]]). {@link com.tss.grc.module.ethics.service
 * .EthicsCaseServiceTest} covered the case register's lifecycle and never its reference.
 *
 * <p>These are unit tests on the primitive plus the two entity shapes rather than service tests,
 * deliberately: the services' {@code apply} helpers are private and both call sites pass the same
 * four arguments, so what is worth pinning is the RULE and the fact that a stored code cannot be
 * replaced. {@code EntityBoundPartialUpdateTest} already drives the services end to end.
 */
class ReferenceRenameContractTest {

    private static final String WHY = "Omit caseCode, or send the value already recorded.";

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(1L); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    @Test
    void aReferenceIsSettableWhileAbsent() {
        EthicsCase c = new EthicsCase();
        ServerOwnedState.requireImmutableReference(
            "ETH-2026-001", c.getCaseCode(), "caseCode", "Ethics case (new)", WHY);
        // no refusal, so the create may assign it
        c.setCaseCode("ETH-2026-001");
        assertThat(c.getCaseCode()).isEqualTo("ETH-2026-001");
    }

    @Test
    void anEchoIsAccepted() {
        // The ethics edit form spreads the whole record back, so refusing a present-but-identical
        // value would break the screen that works today -- the one failure a fix must never have.
        ServerOwnedState.requireImmutableReference(
            "ETH-2026-001", "ETH-2026-001", "caseCode", "Ethics case 7", WHY);
    }

    @Test
    void anEchoIsAcceptedCaseInsensitively() {
        // Every text column here collates case-insensitively (F2-179), so a caller round-tripping a
        // value that differs only in case is echoing, not renaming.
        ServerOwnedState.requireImmutableReference(
            "eth-2026-001", "ETH-2026-001", "caseCode", "Ethics case 7", WHY);
    }

    @Test
    void anOmittedReferenceLeavesTheStoredOneAlone() {
        ServerOwnedState.requireImmutableReference(
            null, "ETH-2026-001", "caseCode", "Ethics case 7", WHY);
        ServerOwnedState.requireImmutableReference(
            "   ", "ETH-2026-001", "caseCode", "Ethics case 7", WHY);
    }

    @Test
    void renamingAStoredReferenceIsRefused() {
        assertThatThrownBy(() -> ServerOwnedState.requireImmutableReference(
                "ETH-2026-999", "ETH-2026-001", "caseCode", "Ethics case 7", WHY))
            .isInstanceOf(IllegalArgumentException.class)
            // names the field ...
            .hasMessageContaining("caseCode")
            // ... the value already recorded, so the caller can see what they would have replaced ...
            .hasMessageContaining("ETH-2026-001")
            // ... and the consequence, which is the reason the rule exists.
            .hasMessageContaining("silently stop resolving")
            // ⚠ and it must NOT name a route. requireUnchanged's wording ends "That move goes
            // through <route>", and for a reference there IS no route -- naming one that does not
            // exist is F2-302's defect, in the same primitive.
            .hasMessageNotContainingAny("goes through", "cannot be moved to");
    }

    /**
     * The two entities this closes, asserted so a later slice cannot quietly re-add a plain setter
     * write: a fresh record has no code, which is what makes "settable while absent" the create path
     * and "immutable once set" the update path, with no flag threaded through the shared helper.
     */
    @Test
    void bothClosedEntitiesStartWithNoReference() {
        assertThat(new EthicsCase().getCaseCode()).isNull();
        assertThat(new Disclosure().getDisclosureCode()).isNull();
    }

    /**
     * F2-306/F2-307 — every closed site still calls the primitive, with the field it claims.
     *
     * <p>The rule's behaviour is unit-tested above. What that cannot see is whether a given SERVICE
     * still routes through it: four of the six sites live in services with four different mock sets,
     * and a behaviour test each would cost more than it proves. This leg is the proportionate
     * instrument, and it is the one that catches a later slice quietly restoring a plain setter
     * write — which is exactly how these sites came to exist, since every one of them was a
     * one-line {@code if (src.getX() != null) target.setX(src.getX())}.
     *
     * <p>⚠ Asserted per (file, field), not by counting call sites: a count cannot see one site
     * being replaced while another is added, and three of these files hold more than one reference
     * setter, only some of which are this kind ([[F2-306]]'s per-field subtraction lesson).
     */
    @Test
    void everyClosedSiteStillCallsThePrimitiveWithItsOwnField() throws java.io.IOException {
        java.nio.file.Path main = java.nio.file.Paths.get("src", "main", "java", "com", "tss", "grc",
                                                          "module").toAbsolutePath().normalize();
        java.util.Map<String, String> sites = new java.util.LinkedHashMap<>();
        sites.put("ethics/service/EthicsCaseService.java", "CaseCode");
        sites.put("ethics/service/DisclosureService.java", "DisclosureCode");
        sites.put("ndi/gap/service/NdiGapService.java", "GapCode");
        sites.put("compliance/service/AuditEngagementService.java", "EngagementCode");
        sites.put("change_mgmt/service/ChangeManagementService.java", "ChangeCode");
        // F2-308 — the last five. Two of them (ComplianceService, PolicyService) wrote the
        // caller's value UNCONDITIONALLY, so an omitted code also cleared it; the guard's
        // settable-while-absent shape closed that reset for free.
        sites.put("ndi/plan/service/NdiImprovementPlanService.java", "PlanCode");
        sites.put("ndi/reports/service/NdiReportService.java", "ReportCode");
        sites.put("bcm/service/CrisisCommsService.java", "Code");
        sites.put("compliance/service/ComplianceService.java", "Code");
        sites.put("compliance/service/PolicyService.java", "Code");

        java.util.List<String> missing = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, String> e : sites.entrySet()) {
            java.nio.file.Path p = main.resolve(e.getKey());
            if (!java.nio.file.Files.exists(p)) {
                missing.add(e.getKey() + " — file not found");
                continue;
            }
            String code = com.tss.grc.support.GuardExpressions.stripComments(
                java.nio.file.Files.readString(p));
            if (!code.contains("requireImmutableReference")) {
                missing.add(e.getKey() + " — no longer calls requireImmutableReference");
                continue;
            }
            // The getter checked is the TARGET entity's, not the request's: ComplianceService
            // binds a record (req.code()) while its entity still exposes getCode().
            if (!code.contains("get" + e.getValue() + "()")) {
                missing.add(e.getKey() + " — no longer reads get" + e.getValue() + "()");
            }
        }

        // NdiGapService holds TWO of these, so its file appears once above and must guard both.
        java.nio.file.Path gap = main.resolve("ndi/gap/service/NdiGapService.java");
        String gapCode = com.tss.grc.support.GuardExpressions.stripComments(
            java.nio.file.Files.readString(gap));
        long guards = gapCode.split("requireImmutableReference", -1).length - 1;
        if (guards < 2) {
            missing.add("ndi/gap/service/NdiGapService.java — guards " + guards
                        + " reference(s), expected 2 (gapCode and actionCode)");
        }

        assertThat(missing).as("""
            A closed rename site no longer routes through ServerOwnedState.requireImmutableReference.

            Every one of these began as a one-line `if (src.getX() != null) target.setX(src.getX())`,             so restoring that shape is a single-line change that no behaviour test in the owning             service would notice. If a site was deliberately reopened, lower             ClientSuppliedReferenceRatchetTest's UPDATE_PATH_REFERENCE_WRITES in the same commit             and say why.

            %s""".formatted(String.join("\n            ", missing)))
            .isEmpty();
    }

    /**
     * F2-309 — a reference this platform makes IMMUTABLE must also be UNIQUE per tenant.
     *
     * <h4>Why the pair matters more than either half</h4>
     *
     * Asking [[F2-267]]'s question of F2-306..F2-308's result gave the <b>opposite</b> answer to the
     * one F2-267 got: there 9 of 10 candidates already carried a unique index, so caller-naming was a
     * naming question rather than a defect. Here <b>8 of 9 had none at all</b>. And that combination
     * is worse than either half:
     *
     * <pre>   immutable + not unique  =  a duplicate is PERMANENT</pre>
     *
     * 🔑 <b>Before F2-306 a collision could be renamed away; after it, the guard that protects the
     * reference is exactly what prevents the repair.</b> V396 adds the nine indexes — tenant-scoped,
     * because a globally-unique code index would let one tenant's reference block another's, and
     * filtered on the eight nullable columns, because SQL Server treats NULLs as equal for
     * uniqueness so a plain index would permit at most ONE code-less row per tenant.
     *
     * <p>⚙ This is the schema-side view of the same set {@link #everyClosedSiteStillCallsThePrimitive
     * WithItsOwnField} holds in Java terms. Neither derives from the other — a Java field name does
     * not give you a column name — so the two are linked by ARITHMETIC instead: the number of
     * declared indexes must equal the number of guarded sites plus the one register that already had
     * uniqueness before this work ({@code compliance.policies}, {@code IX_policy_code_org}). Add a
     * guarded site without an index and the counts diverge.
     */
    @Test
    void everyImmutableReferenceIsAlsoUniquePerTenant() throws java.io.IOException {
        java.nio.file.Path dir = java.nio.file.Paths.get(
            "src", "main", "resources", "db", "migration", "sqlserver").toAbsolutePath().normalize();
        assertThat(java.nio.file.Files.isDirectory(dir)).as("migration folder at %s", dir).isTrue();

        // (schema.table, code column) — the registers whose reference F2-306..F2-308 froze.
        java.util.Map<String, String> frozen = new java.util.LinkedHashMap<>();
        frozen.put("ethics.cases", "case_code");
        frozen.put("ethics.disclosures", "disclosure_code");
        frozen.put("ndi.gap", "gap_code");
        frozen.put("ndi.corrective_action", "action_code");
        frozen.put("compliance.audit_engagements", "engagement_code");
        frozen.put("change_mgmt.change_requests", "change_code");
        frozen.put("ndi.improvement_plan", "plan_code");
        frozen.put("ndi.report_definition", "report_code");
        frozen.put("compliance.controls", "code");

        StringBuilder corpus = new StringBuilder();
        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.list(dir)) {
            for (java.nio.file.Path p : walk.filter(f -> f.toString().endsWith(".sql")).toList()) {
                corpus.append(java.nio.file.Files.readString(p)).append('\n');
            }
        }
        String sql = corpus.toString();

        java.util.List<String> missing = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, String> e : frozen.entrySet()) {
            // a UNIQUE index on that table, keyed organisation_id first, covering the code column
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "CREATE\\s+UNIQUE\\s+INDEX\\s+\\w+\\s+ON\\s+" + java.util.regex.Pattern.quote(e.getKey())
                + "\\s*\\(\\s*organisation_id\\s*,\\s*" + java.util.regex.Pattern.quote(e.getValue())
                + "\\s*\\)", java.util.regex.Pattern.CASE_INSENSITIVE);
            if (!p.matcher(sql).find()) {
                missing.add(e.getKey() + " (" + e.getValue() + ")");
            }
        }

        assertThat(missing).as("""
            A reference this platform makes IMMUTABLE has no tenant-scoped unique index.

            immutable + not unique = a duplicate is PERMANENT: before the reference was frozen a             collision could be renamed away, and afterwards the guard protecting it is exactly what             prevents the repair.

            Declare `CREATE UNIQUE INDEX ... ON <schema>.<table> (organisation_id, <code>)` —             tenant-scoped, because a global index would let one tenant's reference block another's,             and filtered on `WHERE <code> IS NOT NULL` if the column is nullable, because SQL Server             treats NULLs as equal for uniqueness.

            %s""".formatted(missing))
            .isEmpty();

        // Arithmetic linking this schema-side view to the Java-side site map: nine frozen registers
        // declared here, and the site map holds ten sites in nine files (NdiGapService holds two).
        assertThat(frozen).as(
            "The frozen-register list and the guarded-site map must move together. If a site was "
            + "added or removed, one of these two lists is now stale.")
            .hasSize(9);
    }

    /**
     * F2-310 — the frozen references are <b>deliberately operator-named</b>, and this leg exists so
     * nobody "fixes" that by minting them.
     *
     * <h4>The filed question, and why it is refuted rather than done</h4>
     *
     * [[F2-306]]–[[F2-309]] each filed the same follow-up: nothing mints these codes, so all are
     * caller-named at birth — [[F2-172]](d)'s question. Measured, the premise does not hold for this
     * population. Of the nine code fields, <b>seven have a create FORM FIELD</b> in the frontend
     * ({@code EthicsPage}, {@code CompliancePage}, {@code ChangesPage}, {@code NdiPage},
     * {@code ChangeModelCatalogue}), one is declarable only ({@code gapCode}) and one is sent by
     * code. 🔑 <b>So this platform's design is that the ORGANISATION names its own records, and
     * minting would be a product change rather than a fix</b> ([[F2-214]]).
     *
     * <h4>The rule that separates the two cases</h4>
     *
     * <pre>
     *   quoted to an OUTSIDE party      -> the server must mint it   (F2-172(d))
     *   the organisation naming its own -> the operator names it     (this population)
     * </pre>
     *
     * F2-172(d)'s actual subject is a <b>tracking code quoted to the public</b> — the intake
     * reference the whistleblowing and breach-report portals hand a reporter, which
     * {@code CaseTrackerPage} renders as {@code rec.referenceCode}. That one <b>is</b> server-minted,
     * through {@code ReferenceCodeMint}, and {@code ClientSuppliedReferenceRatchetTest} holds it at
     * zero. Checked: <b>none of the frozen twelve reaches an external party.</b> An operator-chosen
     * control code like {@code AC-2} or {@code POL-INFO-001} is a naming convention its organisation
     * owns, and a mint would overwrite it.
     *
     * <p>⚙ What makes the decision safe is [[F2-309]], not this leg: a caller-named code was a real
     * hazard while a duplicate was storable. With tenant-scoped uniqueness in place a collision is
     * refused at the index, so "the operator names it" costs nothing that uniqueness does not already
     * cover.
     */
    @Test
    void theFrozenReferencesAreDeliberatelyNotServerMinted() throws java.io.IOException {
        java.nio.file.Path main = java.nio.file.Paths.get("src", "main", "java", "com", "tss", "grc",
                                                          "module").toAbsolutePath().normalize();
        java.util.List<String> minted = new java.util.ArrayList<>();
        java.util.List<String> services = java.util.List.of(
            "ethics/service/EthicsCaseService.java",
            "ethics/service/DisclosureService.java",
            "ndi/gap/service/NdiGapService.java",
            "compliance/service/AuditEngagementService.java",
            "change_mgmt/service/ChangeManagementService.java",
            "ndi/plan/service/NdiImprovementPlanService.java",
            "ndi/reports/service/NdiReportService.java",
            "compliance/service/ComplianceService.java",
            "compliance/service/PolicyService.java");

        for (String rel : services) {
            java.nio.file.Path p = main.resolve(rel);
            assertThat(java.nio.file.Files.exists(p)).as("frozen-reference service missing: %s", rel)
                .isTrue();
            String code = com.tss.grc.support.GuardExpressions.stripComments(
                java.nio.file.Files.readString(p));
            if (code.contains("ReferenceCodeMint")) {
                minted.add(rel);
            }
        }

        assertThat(minted).as("""
            A frozen business reference is being server-minted, and that is a PRODUCT change rather             than a fix.

            Seven of these nine codes have a create FORM FIELD, so this platform's design is that the             organisation names its own records. A mint would overwrite an operator's naming             convention.

            The rule: a reference quoted to an OUTSIDE party must be minted (F2-172(d) — the intake             tracking code the public portals hand a reporter, which IS minted through             ReferenceCodeMint); a reference the organisation uses to name its own records must not             be. None of these reaches an external party.

            If a register's code genuinely becomes externally quoted, mint it AND say so here — do             not do it silently.

            %s""".formatted(minted))
            .isEmpty();

        // ...and the public-facing one really is minted, so this is a boundary rather than a blanket
        // refusal. Without this half the leg would read as "never mint anything".
        java.nio.file.Path intake = main.resolve("intake/service/IntakeService.java");
        assertThat(java.nio.file.Files.readString(intake)).as(
            "The intake tracking code is quoted to the public and MUST be server-minted. If this "
            + "stopped being true, the boundary this leg describes has moved and the rule above is "
            + "no longer the rule.")
            .contains("ReferenceCodeMint");
    }
}
