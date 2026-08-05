package com.tss.grc.module.dataclass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.dataclass.dto.DataclassDtos.DashboardResponse;
import com.tss.grc.module.dataclass.dto.DataclassDtos.TransitionAssetRequest;
import com.tss.grc.module.dataclass.dto.DataclassDtos.TransitionExceptionRequest;
import com.tss.grc.module.dataclass.dto.DataclassDtos.TransitionFlowRequest;
import com.tss.grc.module.dataclass.entity.DataAsset;
import com.tss.grc.module.dataclass.entity.DataException;
import com.tss.grc.module.dataclass.entity.DataFlow;
import com.tss.grc.module.dataclass.entity.DataclassEvent;
import com.tss.grc.module.dataclass.repository.DataAssetRepository;
import com.tss.grc.module.dataclass.repository.DataExceptionRepository;
import com.tss.grc.module.dataclass.repository.DataFlowRepository;
import com.tss.grc.module.dataclass.repository.DataclassEventRepository;
import com.tss.grc.testsupport.StateMachineContract;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * F2-03(f) — the Tier-1 floor for {@code dataclass} (PDPL data classification). Sixth in the row's
 * order and the smallest at 990 LoC: three well-formed state machines, no deadline claim in any
 * javadoc, and a real PDPL Art.7 gate on cross-border transfer.
 *
 * <h3>⚠ F2-128 — three lapsed control waivers, invisible by construction</h3>
 * {@code EXCEPTION_ALLOWED} declares {@code ACTIVE → EXPIRED}, and <b>nothing anywhere sets
 * {@code EXPIRED}</b> — the same shape as F2-125 in {@code openbanking}. But here it is <b>manifest,
 * not latent</b>: measured 2026-08-06, <b>3 of the 4 ACTIVE exceptions are past their own
 * {@code valid_until}</b> by 46–54 days, and all three waive a data-protection control — two
 * {@code MASKING_WAIVER} and one {@code ENCRYPTION_AT_REST_WAIVER}.
 *
 * <p>The sharp part is not that nobody thought about expiry. Somebody wrote a window, and made it
 * half-open in the wrong direction:
 * <pre>
 *   .filter(e -&gt; "ACTIVE".equals(e.getStatus()))
 *   .filter(e -&gt; e.getValidUntil() != null
 *           &amp;&amp; !e.getValidUntil().isBefore(today)          // ← excludes everything already lapsed
 *           &amp;&amp; !e.getValidUntil().isAfter(today.plusDays(30)))
 * </pre>
 * That was the only code in the module reading {@code valid_until}. So a lapsed waiver appeared in
 * <b>no metric at all</b>: excluded from "expiring soon" precisely <em>because</em> it had expired, and
 * counted inside "active exceptions" as though still valid. The dashboard read
 * <em>4 active, 0 expiring</em>.
 *
 * <p><b>What this slice changed, and what it deliberately did not.</b> A separate
 * {@code exceptionsLapsed} count now reports the population, with a matching UI tile — because a
 * metric nobody renders is this programme's own "a ratchet nobody runs is a comment". Nothing changes
 * state: whether a lapsed exception should be <em>swept</em> to EXPIRED or <em>derived</em> as expired
 * on read is the same open decision as F2-125, and it should be taken once for both modules rather
 * than differently in each. Making the population visible needs no such decision.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataclassLifecycleTest {

    private static final Long ORG = 1L;

    @Mock private DataAssetRepository assetRepo;
    @Mock private DataFlowRepository flowRepo;
    @Mock private DataExceptionRepository excRepo;
    @Mock private DataclassEventRepository eventRepo;
    @Mock private ApplicationEventPublisher publisher;

    @InjectMocks private DataclassService svc;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        when(assetRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(flowRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(excRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(eventRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        // A flow's transition reads its asset's classification for the PDPL Art.7 gate, so the asset
        // lookup must be available on the flow path too.
        when(assetRepo.findByOrganisationIdAndId(eq(ORG), any()))
            .thenReturn(Optional.of(asset("IN_USE", "INTERNAL")));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── the three machines ───────────────────────────────────────────────────

    @Test
    @DisplayName("Asset: every declared transition permitted, every undeclared pair refused")
    void assetMachineHonoursItsOwnMap() {
        StateMachineContract.assertHonoursItsMap(
            map("ASSET_ALLOWED"),
            state -> when(assetRepo.findByOrganisationIdAndId(eq(ORG), eq(1L)))
                .thenReturn(Optional.of(asset(state, "INTERNAL"))),
            to -> svc.transitionAsset(1L, new TransitionAssetRequest(to, "why", "لماذا")),
            "cannot transition", "DataAsset ASSET_ALLOWED");
    }

    @Test
    @DisplayName("Flow: every declared transition permitted, every undeclared pair refused")
    void flowMachineHonoursItsOwnMap() {
        StateMachineContract.assertHonoursItsMap(
            map("FLOW_ALLOWED"),
            state -> when(flowRepo.findByOrganisationIdAndId(eq(ORG), eq(1L)))
                .thenReturn(Optional.of(flow(state, "SA"))),
            to -> svc.transitionFlow(1L, new TransitionFlowRequest(to, "why", "لماذا", null)),
            "cannot transition", "DataFlow FLOW_ALLOWED");
    }

    @Test
    @DisplayName("Exception: every declared transition permitted, every undeclared pair refused")
    void exceptionMachineHonoursItsOwnMap() {
        StateMachineContract.assertHonoursItsMap(
            map("EXCEPTION_ALLOWED"),
            state -> when(excRepo.findByOrganisationIdAndId(eq(ORG), eq(1L)))
                .thenReturn(Optional.of(exception(state, LocalDate.now().plusDays(30)))),
            to -> svc.transitionException(1L, new TransitionExceptionRequest(to, "no", "لا")),
            "cannot transition", "DataException EXCEPTION_ALLOWED");
    }

    // ── the PDPL Art.7 gate, which is a real control ─────────────────────────

    /**
     * ⚠ The gate is on <b>APPROVED</b>, not on ACTIVE. My first version of this test drove
     * {@code APPROVED → ACTIVE} and failed with "expecting code to raise a throwable" — which sent me
     * to read the placement, and it is <b>sound</b>: {@code FLOW_ALLOWED} makes
     * {@code PROPOSED → APPROVED} the only entry to the approved-and-onward region, so every ACTIVE
     * flow has passed the gate, and {@code SUSPENDED → ACTIVE} reactivates a flow whose reference is
     * already on the row. Checking at approval rather than at activation is the right single point.
     */
    @Test
    @DisplayName("PDPL Art.7: a cross-border flow of CONFIDENTIAL data cannot be APPROVED without an authorisation ref")
    void crossBorderSensitiveFlowNeedsAnArticleSevenReference() {
        when(assetRepo.findByOrganisationIdAndId(eq(ORG), any()))
            .thenReturn(Optional.of(asset("IN_USE", "CONFIDENTIAL")));
        DataFlow f = flow("PROPOSED", "IE");
        when(flowRepo.findByOrganisationIdAndId(eq(ORG), eq(1L))).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> svc.transitionFlow(1L,
                new TransitionFlowRequest("APPROVED", null, null, null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PDPL Art.7");
        assertThat(f.getStatus()).as("a refused approval must not mutate the record")
            .isEqualTo("PROPOSED");

        // The reference is an external SDAIA authorisation number, so accepting it from the caller is
        // correct — unlike an actor or an outcome, the platform is not its source. Same reasoning as
        // sharia's charity receipt (F2-121).
        assertThatCode(() -> svc.transitionFlow(1L,
                new TransitionFlowRequest("APPROVED", null, null, "SDAIA-XB-2026-0042")))
            .doesNotThrowAnyException();
        assertThat(f.getPdplArt7TransferRef()).isEqualTo("SDAIA-XB-2026-0042");
    }

    @Test
    @DisplayName("PDPL Art.7 does not over-reach: domestic, or non-sensitive, needs no reference")
    void theArticleSevenGateIsCorrectlyNarrow() {
        // Domestic transfer of CONFIDENTIAL data — Art.7 governs cross-border, so no ref required.
        when(assetRepo.findByOrganisationIdAndId(eq(ORG), any()))
            .thenReturn(Optional.of(asset("IN_USE", "CONFIDENTIAL")));
        DataFlow domestic = flow("PROPOSED", "SA");
        when(flowRepo.findByOrganisationIdAndId(eq(ORG), eq(1L))).thenReturn(Optional.of(domestic));
        assertThatCode(() -> svc.transitionFlow(1L,
            new TransitionFlowRequest("APPROVED", null, null, null))).doesNotThrowAnyException();

        // Cross-border transfer of INTERNAL data — not a sensitive classification.
        when(assetRepo.findByOrganisationIdAndId(eq(ORG), any()))
            .thenReturn(Optional.of(asset("IN_USE", "INTERNAL")));
        DataFlow crossBorderPublic = flow("PROPOSED", "IE");
        when(flowRepo.findByOrganisationIdAndId(eq(ORG), eq(1L)))
            .thenReturn(Optional.of(crossBorderPublic));
        assertThatCode(() -> svc.transitionFlow(1L,
            new TransitionFlowRequest("APPROVED", null, null, null))).doesNotThrowAnyException();
    }

    /**
     * F2-129. The Art.7 obligation attaches to the <b>data's</b> sensitivity, and sensitivity is
     * mutable: {@code ASSET_ALLOWED} makes {@code IN_USE → RECLASSIFY → CLASSIFIED → APPROVED → IN_USE}
     * a first-class path. The flow gate runs once, at flow approval, and nothing re-examines a running
     * flow when its asset is later reclassified upward. So a flow approved while its asset was INTERNAL
     * keeps transferring after the asset becomes CONFIDENTIAL, with no authorisation reference.
     *
     * <p>⚠ <b>Latent, and said plainly: 0 rows today.</b> Both ACTIVE cross-border flows in dev are
     * correct — the CONFIDENTIAL one carries {@code PDPL-ART7-2024-014}, and the other is INTERNAL and
     * needs none. Recorded as mechanism so the gap is documented without overstating it.
     */
    @Test
    @DisplayName("F2-129: reclassifying an asset upward does not re-examine its already-approved flows")
    void reclassificationDoesNotRecheckArticleSeven() {
        String svcSrc = source("service", "DataclassService.java");

        // The asset machine really does allow an upward reclassification of a live asset.
        assertThat(map("ASSET_ALLOWED").get("IN_USE")).contains("RECLASSIFY");
        assertThat(map("ASSET_ALLOWED").get("RECLASSIFY")).contains("CLASSIFIED");

        // And the classify/transition paths for an ASSET never look at its flows.
        int assetBlockStart = svcSrc.indexOf("public AssetResponse transitionAsset");
        int assetBlockEnd = svcSrc.indexOf("public FlowResponse", assetBlockStart);
        String assetBlock = svcSrc.substring(assetBlockStart, assetBlockEnd);
        assertThat(assetBlock).as(
            "F2-129 — if the asset path now consults flowRepo, an upward reclassification may finally "
            + "re-examine its transfers: verify it and close the finding")
            .doesNotContain("flowRepo");
    }

    // ── F2-128 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F2-128: a lapsed-but-ACTIVE exception is counted, and is NOT in the expiring-soon window")
    void lapsedExceptionsAreCountedSeparatelyFromExpiringSoon() {
        DataException lapsed = exception("ACTIVE", LocalDate.now().minusDays(54));
        DataException lapsedToo = exception("ACTIVE", LocalDate.now().minusDays(1));
        DataException soon = exception("ACTIVE", LocalDate.now().plusDays(10));
        DataException healthy = exception("ACTIVE", LocalDate.now().plusDays(200));
        DataException revoked = exception("REVOKED", LocalDate.now().minusDays(99));

        when(excRepo.findByOrganisationIdOrderByCreatedAtDesc(eq(ORG)))
            .thenReturn(List.of(lapsed, lapsedToo, soon, healthy, revoked));
        when(assetRepo.findByOrganisationIdOrderByCreatedAtDesc(eq(ORG))).thenReturn(List.of());
        when(flowRepo.findByOrganisationIdOrderByCreatedAtDesc(eq(ORG))).thenReturn(List.of());

        DashboardResponse d = svc.dashboard();

        assertThat(d.exceptionsLapsed()).as(
            "two ACTIVE exceptions are past valid_until; the REVOKED one is already terminated and "
            + "must not be counted as a live waiver").isEqualTo(2L);
        assertThat(d.exceptionsExpiringNext30d()).as(
            "the expiring window starts at today, so it sees only the one due in 10 days — which is "
            + "exactly why the lapsed pair needed a metric of their own").isEqualTo(1L);
    }

    /**
     * F2-128's tripwire. Asserts the <b>absence</b> of any producer for {@code EXPIRED}: when a sweep
     * or a derived read lands, this fails and that is the signal to close the finding — and to close
     * F2-125 with it, since the decision is shared.
     */
    @Test
    @DisplayName("F2-128: EXPIRED is a declared target with no producer anywhere in the module")
    void nothingEverExpiresAnException() {
        String svcSrc = source("service", "DataclassService.java");

        assertThat(map("EXCEPTION_ALLOWED").get("ACTIVE"))
            .as("the machine declares the transition").contains("EXPIRED");

        long writers = svcSrc.lines()
            .filter(l -> l.contains("setStatus(\"EXPIRED\")")).count();
        assertThat(writers).as(
            "F2-128 — no code path expires an exception. If one now exists, verify it and close both "
            + "this finding and F2-125 in openbanking, which is the same decision.")
            .isZero();
        assertThat(svcSrc).as("and no scheduler sweeps them")
            .doesNotContain("Schedul");
    }

    // ── the authorisation branches ───────────────────────────────────────────

    /**
     * The fourth measurement of F2-116's shape, and the mildest of the four: of the seven role names
     * these guards recite, only {@code CIO} is absent from {@code iam.roles}. <b>{@code DPO} exists</b>
     * — so unlike {@code sharia} (F2-120) and {@code openbanking} (F2-127), the officer who actually
     * owns data classification under PDPL can both write and approve. Recorded rather than filed as a
     * new number: it is one more instance of the cause F2-127 already argues.
     */
    @Test
    @DisplayName("The guards are as reviewed; DPO exists and CIO does not")
    void theGuardConstantsArePinned() {
        String ctl = source("controller", "DataclassController.java");

        assertThat(rolesIn(ctl, "READ")).containsExactlyInAnyOrder(
            "PLATFORM_ADMIN", "COMPLIANCE_OFFICER", "AUDITOR", "DPO", "CISO", "CIO", "RISK_MANAGER");
        assertThat(rolesIn(ctl, "WRITE")).containsExactlyInAnyOrder(
            "PLATFORM_ADMIN", "COMPLIANCE_OFFICER", "DPO", "CIO");
        assertThat(rolesIn(ctl, "APPROVE")).containsExactlyInAnyOrder(
            "PLATFORM_ADMIN", "DPO", "CISO", "CIO");

        assertThat(rolesIn(ctl, "WRITE")).as(
            "DPO must remain in WRITE and APPROVE — it is the one specialist role in this family that "
            + "exists, and it is the reason this module still functions for its intended owner")
            .contains("DPO");
        assertThat(rolesIn(ctl, "APPROVE")).contains("DPO");
    }

    @Test
    @DisplayName("Every one of the 13 mappings carries a guard")
    void everyEndpointIsGuarded() {
        String ctl = source("controller", "DataclassController.java");
        long mappings = ctl.lines()
            .filter(l -> l.contains("@GetMapping") || l.contains("@PostMapping")
                      || l.contains("@PutMapping") || l.contains("@DeleteMapping")).count();
        long guards = ctl.lines().filter(l -> l.contains("@PreAuthorize")).count();
        assertThat(mappings).isEqualTo(13L);
        assertThat(guards).isEqualTo(mappings);
        assertThat(ctl).as("the entitlement gate must name the SKU that sells this module")
            .contains("@RequireModule(\"PDPL\")");
    }

    // ── provenance ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Every transition writes a timeline event, which is why dev's zero events means seeded rows")
    void transitionsWriteTheirTimeline() {
        when(excRepo.findByOrganisationIdAndId(eq(ORG), eq(1L)))
            .thenReturn(Optional.of(exception("REQUESTED", LocalDate.now().plusDays(30))));

        svc.transitionException(1L, new TransitionExceptionRequest("APPROVED", null, null));

        org.mockito.ArgumentCaptor<DataclassEvent> cap =
            org.mockito.ArgumentCaptor.forClass(DataclassEvent.class);
        org.mockito.Mockito.verify(eventRepo, org.mockito.Mockito.atLeastOnce()).save(cap.capture());
        assertThat(cap.getValue().getStatusAfter()).isEqualTo("APPROVED");
        // dataclass.dataclass_event holds 0 rows against 45 records in the other three tables, so
        // every one of those records was written below the service. Worth knowing before reading any
        // of this module's history as evidence of use.
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private DataAsset asset(String status, String classification) {
        DataAsset a = new DataAsset();
        a.setId(1L);
        a.setOrganisationId(ORG);
        a.setReference("DA-REVIEW-TEST-1");
        a.setClassification(classification);
        a.setStatus(status);
        return a;
    }

    private DataFlow flow(String status, String destinationCountry) {
        DataFlow f = new DataFlow();
        f.setId(1L);
        f.setOrganisationId(ORG);
        f.setReference("DF-REVIEW-TEST-1");
        f.setDataAssetId(1L);
        f.setDestinationCountry(destinationCountry);
        f.setStatus(status);
        return f;
    }

    private DataException exception(String status, LocalDate validUntil) {
        DataException e = new DataException();
        e.setId(1L);
        e.setOrganisationId(ORG);
        e.setReference("EXC-REVIEW-TEST-1");
        e.setDataAssetId(1L);
        e.setExceptionType("MASKING_WAIVER");
        e.setValidFrom(LocalDate.now().minusDays(120));
        e.setValidUntil(validUntil);
        e.setStatus(status);
        return e;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Set<String>> map(String field) {
        Object v = ReflectionTestUtils.getField(DataclassService.class, field);
        assertThat(v).as("DataclassService.%s must exist — a rename would make this class stop "
            + "checking that machine", field).isInstanceOf(Map.class);
        return (Map<String, Set<String>>) v;
    }

    private static String source(String pkg, String file) {
        try {
            return Files.readString(Paths.get("src", "main", "java", "com", "tss", "grc", "module",
                "dataclass", pkg, file).toAbsolutePath().normalize(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("cannot read " + pkg + "/" + file, e);
        }
    }

    private static Set<String> rolesIn(String src, String constant) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "String " + constant + "\\s*=\\s*\"hasAnyRole\\(([^)]*)\\)\"").matcher(src);
        assertThat(m.find()).as("guard constant %s must be a hasAnyRole string — a pattern that "
            + "matches nothing passes forever", constant).isTrue();
        Set<String> out = new TreeSet<>();
        for (String part : m.group(1).split(",")) {
            String r = part.trim().replace("'", "");
            if (!r.isEmpty()) {
                out.add(r);
            }
        }
        return out;
    }
}
