package com.tss.grc.module.sust.ghg.service;

import com.tss.grc.module.evidence.entity.EvidenceItem;
import com.tss.grc.module.evidence.repository.EvidenceItemRepository;
import com.tss.grc.module.sust.evidence.controller.SustEvidenceLinkController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-243 — <b>a four-eyes gate that consumes a foreign key must RESOLVE it.</b>
 *
 * <p>CLAUDE.md lists <em>"GHG verify needs evidence"</em> among the platform's server-enforced
 * four-eyes gates. The check was:
 *
 * <pre>{@code
 *   if ("VERIFIED".equals(to)
 *           && cur.getEvidenceItemId() == null
 *           && (cur.getEvidenceRef() == null || cur.getEvidenceRef().isBlank())) throw …
 * }</pre>
 *
 * <p><b>A presence test on an id is not a check on the thing it names.</b> Probed live as
 * {@code so_officer} (SUSTAINABILITY_OFFICER, holding SUST WRITE — {@code admin} proves nothing
 * about authorization) against records the probe planted itself: a GHG figure citing the register's
 * one <b>REJECTED</b> evidence item verified with HTTP 200 and stored {@code verified = true}, and so
 * did one citing an <b>EXPIRED</b> item. Measured identical in dev and prod, <b>51 of the 78 live
 * evidence items are not APPROVED</b> (EXPIRED 31, PENDING_REVIEW 15, DRAFT 4, REJECTED 1) — so the
 * gate built to consume the evidence module's four-eyes review and its nightly staleness sweep was
 * discharged by their rejected output, on an ISAE 3410 assurance input.
 *
 * <h2>The asymmetry, inside one method, three lines apart</h2>
 *
 * <p>{@code update} wrote two caller-supplied foreign keys. {@code emissionFactorId} was resolved by
 * {@code applyDerivation} with {@code findByIdAndOrganisationId} <em>and a hard throw</em>;
 * {@code evidenceItemId} was stored raw. <b>The reference feeding a number was verified and the one
 * discharging a control was not</b> — F2-215's two-of-N at its shortest range.
 *
 * <h2>Two questions, two places</h2>
 *
 * <p>🔑 <b>The TENANT is verified where the reference is STORED</b> (F2-242's discriminator), <b>and
 * the STATE where it is CONSUMED</b> — because an item can be approved on the day it is attached and
 * expired by the time somebody verifies against it. That division is the reusable rule, and it is
 * why the fix is in two places rather than one.
 *
 * <p>The store side is closed structurally rather than by a predicate: {@code evidence_item_id} had
 * <b>two writers</b>, and only {@code SustEvidenceLinkService.link()} (the declared S-C bridge)
 * resolved the item in-tenant, recorded the {@code EvidenceLink} the assurance package is built
 * from, and wrote a LINK_EVIDENCE audit row. A record could therefore be VERIFIED citing an item
 * {@code buildPackage()} would not contain. So the bridge is now the single writer and this
 * endpoint accepts only an <b>echo</b> of the stored value (F2-231/F2-234: a round-trip PUT must keep
 * working, a change must not).
 */
class Rule2EvidenceResolutionContractTest {

    private static final Path GHG_SERVICE = Path.of(
        "src", "main", "java", "com", "tss", "grc", "module", "sust", "ghg", "service",
        "GhgEmissionRecordService.java");
    private static final Path CREATE_REQUEST = Path.of(
        "src", "main", "java", "com", "tss", "grc", "module", "sust", "ghg", "dto",
        "GhgCreateRequest.java");
    private static final Path BRIDGE = Path.of(
        "src", "main", "java", "com", "tss", "grc", "module", "sust", "evidence", "service",
        "SustEvidenceLinkService.java");

    /** F2-198's stripper as ONE alternation, newlines preserved so line numbers stay true. */
    private static String read(Path p) {
        try {
            String src = Files.readString(p, StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/").matcher(src);
            StringBuilder out = new StringBuilder(src);
            while (m.find()) {
                for (int i = m.start(); i < m.end(); i++) {
                    if (out.charAt(i) != '\n' && out.charAt(i) != '\r') out.setCharAt(i, ' ');
                }
            }
            return out.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The detector: does Rule 2 decide on the id's PRESENCE alone?
     *
     * <p>Deliberately the shape and not a spelling — {@code getEvidenceItemId() == null} appearing
     * anywhere inside a VERIFIED guard. A ban on the exact literal this slice deleted would be
     * satisfied by any rewrite of it, which is the mistake F2-205 recorded in a gate written
     * immediately after quoting the rule.
     */
    private static final Pattern PRESENCE_ONLY_RULE2 = Pattern.compile(
        "\"VERIFIED\"[^;{}]{0,120}getEvidenceItemId\\s*\\(\\s*\\)\\s*==\\s*null");

    /** The fixed shape: the id is handed to the evidence module to resolve. */
    private static final Pattern RESOLVES = Pattern.compile(
        "findUsableAsEvidence\\s*\\(");

    @Test
    @DisplayName("Rule 2 resolves the evidence item it cites, rather than counting it")
    void rule2ResolvesTheItemItCites() {
        String src = read(GHG_SERVICE);

        assertThat(PRESENCE_ONLY_RULE2.matcher(src).find()).as("""
            Rule 2 decides VERIFIED on whether evidenceItemId is non-null. That is a presence test \
            on an id, not a check on the thing it names: FK_ghg_evidence_item refuses a NONEXISTENT \
            id, so every other number the caller can send is one the gate accepts -- including the \
            one REJECTED evidence item and any of the 31 EXPIRED ones. Resolve it through \
            EvidenceItemRepository#findUsableAsEvidence.""")
            .isFalse();

        assertThat(RESOLVES.matcher(src).find()).as(
            "Rule 2 must call findUsableAsEvidence -- asserted as a CALL SHAPE, because a token "
            + "check is satisfied by the name appearing in an import or a comment (F2-194/F2-205)")
            .isTrue();
    }

    @Test
    @DisplayName("the store side lets the bridge own the linkage — an echo passes, a change does not")
    void theStoreSideDoesNotWriteTheLinkageRaw() {
        String src = read(GHG_SERVICE);

        assertThat(src).as("""
            update() must not copy evidenceItemId out of the request. evidence_item_id had two \
            writers and only SustEvidenceLinkService.link() resolved the item in-tenant, recorded \
            the EvidenceLink the ISAE 3410 assurance package is built from, and wrote a \
            LINK_EVIDENCE audit row -- so a record could be VERIFIED citing an item that package \
            does not contain. F2-188/F2-189/F2-191's shape: the duplicate agrees on the column and \
            differs on the parts nothing asserts.""")
            .doesNotContain("cur.setEvidenceItemId(req.getEvidenceItemId())");

        assertThat(src).as("the refusal must name the endpoint that owns the linkage, so the caller "
            + "is sent somewhere (F2-180) rather than merely refused")
            .contains("requireEvidenceLinkageIsUnchanged")
            .contains("SustEvidenceLinkController.PATH");

        assertThat(src).as("""
            An ECHO of the stored value must be accepted: this route binds the whole entity, so a \
            round-trip edit (GET, change one field, PUT it all back) sends the id it just read. \
            Refusing that would break a working request, which is the one thing a gate-driven fix \
            must not do (F2-214/F2-231).""")
            .contains("requested.equals(cur.getEvidenceItemId())");

        assertThat(src).as("the bridge remains the writer, so the create path is untouched by this")
            .doesNotContain("evidenceItems.save(");
    }

    @Test
    @DisplayName("the path the refusal cites RESOLVES to a real mapped route")
    void theCitedPathResolvesToARealRoute() {
        // ⚠ My first draft of that message spelled the bridge `/api/sustainability/evidence/link`.
        // It does not exist — the route is `/api/sustainability/evidence-links` — and the assertion
        // above could not tell, because a literal-matching check cannot resolve a route. The live
        // probe found it, by receiving 400 from a path it had been told to use. 🔑 A diagnostic
        // whose only job is to send the caller somewhere is worthless if the somewhere is invented;
        // this is the guessed-path error this programme has recorded since F2-198, arriving inside a
        // fix's own error text. So the citation is now a CONSTANT and this leg resolves it.
        assertThat(SustEvidenceLinkController.class.getAnnotation(RequestMapping.class).value())
            .as("the constant the refusal cites must be the controller's own mapping")
            .containsExactly(SustEvidenceLinkController.PATH);
        assertThat(SustEvidenceLinkController.PATH)
            .as("and it must look like the route it is").startsWith("/api/");
    }

    @Test
    @DisplayName("the create is closed by its bound type, and that is asserted rather than assumed")
    void theCreateIsClosedByItsBoundType() {
        // F2-233's fourth and strongest wire-closing mechanism: a field the bound type does not
        // declare can never arrive. GhgCreateRequest declares evidenceRef and not evidenceItemId,
        // so adding a refusal in create() would be an INERT construct -- worse than none, because
        // it answers the question a reader was about to ask (F2-184). Asserted here so the closure
        // is a fact somebody has to keep, not an accident nobody noticed.
        String dto = read(CREATE_REQUEST);
        assertThat(dto).as("""
            GhgCreateRequest has gained an evidenceItemId component. The create path then accepts a \
            caller-supplied evidence reference with no resolution, and Rule 2's store-side closure \
            is gone. Either resolve it in create() the way transition() now does, or attach evidence \
            through the bridge.""")
            .doesNotContain("evidenceItemId");
        assertThat(dto).as("anti-vacuity: this is the right file (it does declare the legacy "
            + "free-text evidenceRef, which the create legitimately accepts)")
            .contains("evidenceRef");
    }

    @Test
    @DisplayName("the bridge still resolves the item inside the caller's tenant")
    void theBridgeStillResolvesInTenant() {
        // The store-side fix hands the whole linkage to the bridge, so the bridge's own tenant
        // predicate becomes load-bearing for a second caller. If it were ever relaxed, the fix
        // above would silently stop being a fix.
        assertThat(read(BRIDGE)).as(
            "SustEvidenceLinkService.link must keep resolving the evidence item in the caller's "
            + "organisation -- it is now the only writer of the FK")
            .contains("orgId.equals(e.getOrganisationId())");
    }

    @Test
    @DisplayName("the usability predicate and the nightly expiry query agree on APPROVED")
    void thePredicateAndTheExpirySweepAgreeOnTheVocabulary() throws Exception {
        // A JPQL @Query takes a compile-time constant, so findApprovedExpiredAllOrgs cannot
        // literally reference EvidenceItem.APPROVED. Two copies of one vocabulary is this
        // programme's most-repeated defect (F2-196, F2-205, F2-229) -- so they are compared by
        // reflection instead of trusted to stay in step.
        String jpql = EvidenceItemRepository.class
            .getMethod("findApprovedExpiredAllOrgs", LocalDate.class)
            .getAnnotation(Query.class).value();
        assertThat(jpql).as(
            "the nightly APPROVED -> EXPIRED sweep and EvidenceItem.APPROVED must name the same "
            + "state; if the sweep's literal changes, this predicate is measuring a state nothing "
            + "produces")
            .contains("'" + EvidenceItem.APPROVED + "'");
        assertThat(jpql).as("and it must still be the expiry query -- validTo is half its meaning")
            .contains("validTo");
    }

    @Test
    @DisplayName("isUsableAsEvidence: APPROVED and in date, and nothing else")
    void theUsabilityPredicateIsTheOneInclusion() {
        LocalDate today = LocalDate.of(2026, 8, 19);

        assertThat(item(EvidenceItem.APPROVED, null).isUsableAsEvidence(today))
            .as("APPROVED with no expiry is usable").isTrue();
        assertThat(item(EvidenceItem.APPROVED, today).isUsableAsEvidence(today))
            .as("valid THROUGH today is usable -- the boundary is inclusive").isTrue();
        assertThat(item(EvidenceItem.APPROVED, today.minusDays(1)).isUsableAsEvidence(today))
            .as("""
                APPROVED but lapsed yesterday is NOT usable. The date is checked as well as the \
                status because the APPROVED -> EXPIRED flip is done by a nightly scheduler, so \
                between lapse and sweep an item reads APPROVED while its validity has gone.""")
            .isFalse();

        // The four states that are not evidence. This is an INCLUSION of exactly one state, not an
        // exclusion list: evidence_items.status has no CHECK constraint, and an exclusion list
        // silently admits every token nobody remembered -- for a four-eyes gate, the unsafe
        // direction (F2-196).
        for (String notEvidence : new String[] {"REJECTED", "EXPIRED", "PENDING_REVIEW", "DRAFT"}) {
            assertThat(item(notEvidence, null).isUsableAsEvidence(today))
                .as("%s must not discharge an evidence requirement", notEvidence).isFalse();
        }
        assertThat(item("SOME_STATE_NOBODY_DECLARED", null).isUsableAsEvidence(today))
            .as("an unrecognised status must fail closed, which an exclusion list would not")
            .isFalse();
    }

    @Test
    @DisplayName("the detector fires on the literal pre-fix shape and not on the fixed one")
    void theDetectorFiresOnTheLiteralPreFixShape() {
        // F2-195's synthetic self-test. Every assertion above passed on the first run, which is
        // also what a rotted regex looks like -- so drive the detector across the exact source it
        // was written for.
        String preFix = """
            if ("VERIFIED".equals(to)
                    && cur.getEvidenceItemId() == null
                    && (cur.getEvidenceRef() == null || cur.getEvidenceRef().isBlank())) {
                throw new IllegalArgumentException("GHG verification requires evidence (Rule 2)");
            }
            """;
        assertThat(PRESENCE_ONLY_RULE2.matcher(preFix).find())
            .as("the detector must fire on the shape it was written for").isTrue();
        assertThat(RESOLVES.matcher(preFix).find())
            .as("and the pre-fix source must not satisfy the resolution requirement").isFalse();

        String fixed = """
            if ("VERIFIED".equals(to)) requireRule2Evidence(cur);
            private void requireRule2Evidence(GhgEmissionRecord cur) {
                String ref = cur.getEvidenceRef();
                if (ref != null && !ref.isBlank()) return;
                Long itemId = cur.getEvidenceItemId();
                if (itemId == null) throw new IllegalArgumentException("requires evidence");
                evidenceItems.findUsableAsEvidence(itemId, org(), LocalDate.now()).orElseThrow();
            }
            """;
        assertThat(PRESENCE_ONLY_RULE2.matcher(fixed).find()).as("""
            and it must NOT fire on the fixed shape. The fixed form still asks `itemId == null`, \
            because a record with no evidence at all has to be refused before anything is resolved \
            -- so the detector has to distinguish that from deciding VERIFIED on presence alone. If \
            this fails, the ban is too wide and would refuse correct code.""")
            .isFalse();
        assertThat(RESOLVES.matcher(fixed).find()).as("the fixed shape resolves").isTrue();
    }

    private static EvidenceItem item(String status, LocalDate validTo) {
        EvidenceItem e = new EvidenceItem();
        e.setStatus(status);
        e.setValidTo(validTo);
        return e;
    }
}
