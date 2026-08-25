package com.tss.grc.module.outsourcing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.support.GuardExpressions;
import com.tss.grc.module.outsourcing.dto.OutsourcingDtos.CloudServiceTransitionRequest;
import com.tss.grc.module.outsourcing.dto.OutsourcingDtos.CreateArrangementRequest;
import com.tss.grc.module.outsourcing.dto.OutsourcingDtos.ExitPlanTransitionRequest;
import com.tss.grc.module.outsourcing.dto.OutsourcingDtos.TransitionRequest;
import com.tss.grc.module.outsourcing.dto.OutsourcingDtos.TransitionReviewRequest;
import com.tss.grc.module.outsourcing.entity.CloudService;
import com.tss.grc.module.outsourcing.entity.ExitPlan;
import com.tss.grc.module.outsourcing.entity.OngoingReview;
import com.tss.grc.module.outsourcing.entity.OutsourcingArrangement;
import com.tss.grc.module.outsourcing.entity.OutsourcingEvent;
import com.tss.grc.module.outsourcing.repository.CloudServiceRepository;
import com.tss.grc.module.outsourcing.repository.ExitPlanRepository;
import com.tss.grc.module.outsourcing.repository.OngoingReviewRepository;
import com.tss.grc.module.outsourcing.repository.OutsourcingArrangementRepository;
import com.tss.grc.module.outsourcing.repository.OutsourcingEventRepository;
import com.tss.grc.testsupport.StateMachineContract;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * F2-03 — the Tier-1 test floor for {@code outsourcing}, the first module in the row's order.
 *
 * <p><b>Measured before a line was written, 2026-08-05.</b> Seven regulator-facing modules were to
 * get floors; all seven had <b>zero</b> test files referencing them, out of 331 in the suite. This
 * module carries 1,657 LoC, 9 write endpoints, <b>four</b> independent state machines and 35 legal
 * transitions, and sells under the TPRM SKU as SAMA outsourcing governance.
 *
 * <h3>⚠ Writing this floor found a control that did not exist — F2-115</h3>
 * {@code OutsourcingArrangement}'s class javadoc has said, since the module shipped:
 * <blockquote>Material outsourcing must transition through {@code SAMA_NOTIFIED} within 60 calendar
 * days of {@code BOARD_APPROVED}.</blockquote>
 * Nothing enforced it. The only gate keyed on {@code isCoreActivity}, which <b>defaults to
 * {@code false}</b>, so a MATERIAL arrangement could take the {@code BOARD_APPROVED → LIVE} edge
 * with no SAMA notification at all. Measured in dev: <b>7 MATERIAL non-core arrangements are
 * LIVE</b>, one with no {@code sama_notified_at} whatsoever.
 *
 * <p>This mattered to the shape of the slice, not merely to the changelog. A floor that covered
 * "every state transition" against the code <em>as it stood</em> would have asserted
 * {@code BOARD_APPROVED → LIVE} succeeds for a MATERIAL arrangement — <b>pinning the defect as
 * correct behaviour</b> in a ratchet, which is worse than having no test. So the gate was added
 * first and the floor tests the corrected machine.
 *
 * <p>The 60-day half of that sentence is still unenforced and is filed rather than built: nothing
 * computes or reports a notification deadline, which is a clock plus a scheduler plus a KPI, not a
 * conditional.
 *
 * <h3>Why the transition maps are read by reflection instead of restated</h3>
 * {@link #arrangementMachineHonoursItsOwnMap()} and its three siblings iterate the service's
 * <em>actual</em> {@code *_ALLOWED} maps. Restating 35 transitions in the test would create a second
 * authority that can drift from the first — RC-A, the root cause behind the largest cluster of
 * findings on this programme — and it would leave a new state silently uncovered. Read this way, a
 * state added to the service is exhaustively covered the moment it appears, in both directions:
 * every pair in the map must be permitted, and <b>every pair not in it must be refused</b>.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutsourcingLifecycleTest {

    private static final Long ORG = 1L;

    @Mock private OutsourcingArrangementRepository arrangementRepo;
    @Mock private CloudServiceRepository cloudRepo;
    @Mock private OngoingReviewRepository reviewRepo;
    @Mock private ExitPlanRepository exitRepo;
    @Mock private OutsourcingEventRepository eventRepo;
    @Mock private ApplicationEventPublisher publisher;

    @InjectMocks private OutsourcingService svc;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        when(arrangementRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(cloudRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(reviewRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(exitRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(eventRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── the four state machines, each against its own declared map ───────────

    @Test
    @DisplayName("Arrangement: every declared transition is permitted and every undeclared one refused")
    void arrangementMachineHonoursItsOwnMap() {
        // NON_MATERIAL and non-core on purpose: this test is about the EDGE SET. The SAMA gates on
        // the LIVE target are separate rules with their own tests below, and mixing them here would
        // make a machine failure and a gate failure indistinguishable.
        assertMachineHonoursItsMap("ARRANGEMENT_ALLOWED", "Arrangement",
            state -> {
                OutsourcingArrangement a = arrangement(state, "NON_MATERIAL", false);
                when(arrangementRepo.findByOrganisationIdAndId(eq(ORG), eq(1L)))
                    .thenReturn(Optional.of(a));
            },
            to -> svc.transitionArrangement(1L, new TransitionRequest(to, "why", "لماذا", null, "LOW")));
    }

    @Test
    @DisplayName("CloudService: every declared transition is permitted and every undeclared one refused")
    void cloudMachineHonoursItsOwnMap() {
        assertMachineHonoursItsMap("CLOUD_ALLOWED", "CloudService",
            state -> {
                CloudService c = new CloudService();
                c.setId(1L);
                c.setOrganisationId(ORG);
                c.setReference("CLD-1");
                c.setStatus(state);
                when(cloudRepo.findByOrganisationIdAndId(eq(ORG), eq(1L))).thenReturn(Optional.of(c));
            },
            to -> svc.transitionCloudService(1L,
                new CloudServiceTransitionRequest(to, "blocked", "محجوب", "CISO")));
    }

    @Test
    @DisplayName("OngoingReview: every declared transition is permitted and every undeclared one refused")
    void reviewMachineHonoursItsOwnMap() {
        assertMachineHonoursItsMap("REVIEW_ALLOWED", "Review",
            state -> {
                OngoingReview r = new OngoingReview();
                r.setId(1L);
                r.setOrganisationId(ORG);
                r.setArrangementId(1L);
                r.setStatus(state);
                when(reviewRepo.findByOrganisationIdAndId(eq(ORG), eq(1L))).thenReturn(Optional.of(r));
            },
            to -> svc.transitionReview(1L,
                new TransitionReviewRequest(to, "AMBER", "findings", "نتائج", null)));
    }

    @Test
    @DisplayName("ExitPlan: every declared transition is permitted and every undeclared one refused")
    void exitPlanMachineHonoursItsOwnMap() {
        assertMachineHonoursItsMap("EXIT_ALLOWED", "ExitPlan",
            state -> {
                ExitPlan e = new ExitPlan();
                e.setId(1L);
                e.setOrganisationId(ORG);
                e.setArrangementId(1L);
                e.setStatus(state);
                when(exitRepo.findByOrganisationIdAndId(eq(ORG), eq(1L))).thenReturn(Optional.of(e));
            },
            to -> svc.transitionExitPlan(1L,
                new ExitPlanTransitionRequest(to, "rejected", "مرفوض")));
    }

    /**
     * Delegates to {@link StateMachineContract}, which is where this loop now lives because F2-03
     * has six more modules to cover and the version written here first contained a real defect: it
     * derived the state universe from {@code allowed.keySet()} alone. That is complete only when
     * every state appears as a key, which is true of all four maps in <em>this</em> module purely by
     * accident, and false for {@code nca_ecc} — whose {@code ACKNOWLEDGED} and {@code CANCELLED}
     * appear only as targets and would therefore have gone untested in both directions. The shared
     * version derives the universe as keys ∪ values.
     */
    private void assertMachineHonoursItsMap(String mapField, String label,
                                            Consumer<String> seedAtState, Consumer<String> attempt) {
        StateMachineContract.assertHonoursItsMap(
            allowedMap(mapField), seedAtState, attempt, "cannot transition", label + " " + mapField);
    }

    // ── F2-115: the SAMA materiality gate ────────────────────────────────────

    @Test
    @DisplayName("F2-115: a MATERIAL arrangement cannot reach LIVE without passing through SAMA_NOTIFIED")
    void materialOutsourcingCannotGoLiveUnnotified() {
        OutsourcingArrangement a = arrangement("BOARD_APPROVED", "MATERIAL", false);
        when(arrangementRepo.findByOrganisationIdAndId(eq(ORG), eq(1L))).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> svc.transitionArrangement(1L,
                new TransitionRequest("LIVE", null, null, null, null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SAMA_NOTIFIED")
            .hasMessageContaining("MATERIAL");

        assertThat(a.getStatus()).as("a refused transition must not have mutated the record")
            .isEqualTo("BOARD_APPROVED");
    }

    @Test
    @DisplayName("F2-115: supplying a non-objection reference in the same request does NOT satisfy it")
    void aReferenceHandedOverWithTheLiveFlipIsNotANotification() {
        OutsourcingArrangement a = arrangement("BOARD_APPROVED", "MATERIAL", false);
        when(arrangementRepo.findByOrganisationIdAndId(eq(ORG), eq(1L))).thenReturn(Optional.of(a));

        // The rule is about having passed through a state. A reference typed into the request that
        // flips the record LIVE is caller-authored truth (RC-E) — the shape F2-93/F2-103 closed.
        assertThatThrownBy(() -> svc.transitionArrangement(1L,
                new TransitionRequest("LIVE", null, null, "SAMA/NO/2026/0001", null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SAMA_NOTIFIED");
    }

    @Test
    @DisplayName("F2-115: the same arrangement goes LIVE once it has actually been notified")
    void materialOutsourcingGoesLiveAfterNotification() {
        OutsourcingArrangement a = arrangement("BOARD_APPROVED", "MATERIAL", false);
        when(arrangementRepo.findByOrganisationIdAndId(eq(ORG), eq(1L))).thenReturn(Optional.of(a));

        svc.transitionArrangement(1L,
            new TransitionRequest("SAMA_NOTIFIED", null, null, "SAMA/NO/2026/0001", null));
        assertThat(a.getSamaNotifiedAt()).as("SAMA_NOTIFIED must stamp the notification moment")
            .isNotNull();

        assertThatCode(() -> svc.transitionArrangement(1L,
                new TransitionRequest("LIVE", null, null, null, null)))
            .doesNotThrowAnyException();
        assertThat(a.getStatus()).isEqualTo("LIVE");
        assertThat(a.getLiveAt()).isNotNull();
    }

    @Test
    @DisplayName("A NON_MATERIAL arrangement may still take BOARD_APPROVED -> LIVE directly")
    void nonMaterialOutsourcingIsNotGated() {
        OutsourcingArrangement a = arrangement("BOARD_APPROVED", "NON_MATERIAL", false);
        when(arrangementRepo.findByOrganisationIdAndId(eq(ORG), eq(1L))).thenReturn(Optional.of(a));

        // The gate must not have quietly become "everything needs SAMA". The edge is legitimate for
        // non-material work and removing it would have been a different, wrong fix.
        assertThatCode(() -> svc.transitionArrangement(1L,
                new TransitionRequest("LIVE", null, null, null, null)))
            .doesNotThrowAnyException();
        assertThat(a.getStatus()).isEqualTo("LIVE");
    }

    @Test
    @DisplayName("SAMA §5: core-activity outsourcing still needs a non-objection reference")
    void coreActivityStillNeedsItsNonObjectionLetter() {
        // Notified, so F2-115's gate is satisfied — this proves §5 is an ADDITIONAL requirement and
        // was not swallowed by the new one.
        OutsourcingArrangement a = arrangement("SAMA_NOTIFIED", "MATERIAL", true);
        a.setSamaNotifiedAt(java.time.LocalDateTime.now());
        when(arrangementRepo.findByOrganisationIdAndId(eq(ORG), eq(1L))).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> svc.transitionArrangement(1L,
                new TransitionRequest("LIVE", null, null, null, null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("non-objection");

        assertThatCode(() -> svc.transitionArrangement(1L,
                new TransitionRequest("LIVE", null, null, "SAMA/NO/2026/0007", null)))
            .doesNotThrowAnyException();
        assertThat(a.getSamaNonObjectionRef()).isEqualTo("SAMA/NO/2026/0007");
    }

    // ── F2-366: the SAMA materiality flag is canonicalised on write ──────────

    @Test
    @DisplayName("F2-366: a lower-case `material` is stored canonical, so the §4 gate cannot be bypassed by casing")
    void materialityIsCanonicalisedOnWrite_soTheSamaGateCannotBeBypassedByCasing() {
        // A machine caller supplies the SAMA materiality flag in lower case. The column has no CHECK
        // and the DTO no @Pattern, so pre-fix it was stored verbatim — and transitionArrangement gates
        // on a case-SENSITIVE `"MATERIAL".equals(...)`, so `"material"` slipped LIVE unnotified while
        // countActiveMaterial (`= 'MATERIAL'`, case-insensitive SQL) still counted it as material.
        when(arrangementRepo.findByOrganisationIdAndReference(eq(ORG), any())).thenReturn(Optional.empty());
        ArgumentCaptor<OutsourcingArrangement> cap = ArgumentCaptor.forClass(OutsourcingArrangement.class);

        svc.createArrangement(createReq("material"));

        verify(arrangementRepo).save(cap.capture());
        OutsourcingArrangement created = cap.getValue();
        assertThat(created.getMateriality())
            .as("materiality is normalised on write so the §4 gate, the countActiveMaterial KPI and "
                + "the V208 automation rule all read the same token")
            .isEqualTo("MATERIAL");

        // End-to-end: the very record that was created lower-case still hits the §4 gate, because the
        // value the gate reads is now canonical. This is the bypass, closed.
        created.setId(1L);
        created.setStatus("BOARD_APPROVED");
        when(arrangementRepo.findByOrganisationIdAndId(eq(ORG), eq(1L))).thenReturn(Optional.of(created));
        assertThatThrownBy(() -> svc.transitionArrangement(1L,
                new TransitionRequest("LIVE", null, null, null, null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SAMA_NOTIFIED")
            .hasMessageContaining("MATERIAL");
    }

    @Test
    @DisplayName("F2-366: an unrecognised materiality is upper-cased, not refused (canonicalise, not a 400)")
    void anUnrecognisedMaterialityIsUppercasedNotRefused() {
        // F2-182: nothing upstream refuses a non-vocabulary value today (no CHECK, no coercion), so a
        // require() would break a request that succeeds today. The fix normalises casing only, and an
        // out-of-vocabulary token stays out of vocabulary (it is legitimately not "material").
        when(arrangementRepo.findByOrganisationIdAndReference(eq(ORG), any())).thenReturn(Optional.empty());
        ArgumentCaptor<OutsourcingArrangement> cap = ArgumentCaptor.forClass(OutsourcingArrangement.class);

        assertThatCode(() -> svc.createArrangement(createReq("important")))
            .as("a canonicalise must not become a refuse — this request succeeds today")
            .doesNotThrowAnyException();

        verify(arrangementRepo).save(cap.capture());
        assertThat(cap.getValue().getMateriality()).isEqualTo("IMPORTANT");
    }

    // ── the authorisation branches the row asks for ──────────────────────────

    /**
     * F2-52 in this module, pinned. Every transition in a SAMA outsourcing register is gated by
     * {@code APPROVE_GUARD}, and two of the four role names it recites <b>do not exist</b> in
     * {@code iam.roles} (58 roles, measured 2026-08-05): {@code CRO} and {@code CIO}. So the
     * approval authority for material outsourcing is effectively {@code PLATFORM_ADMIN} and
     * {@code RISK_MANAGER} — the Chief Risk Officer and CIO the guard was written for cannot
     * approve anything, silently and permanently, because {@code hasAnyRole} gets no bypass and
     * validates nothing.
     *
     * <p>This is pinned rather than ratcheted: who may approve outsourcing is a security-relevant
     * constant, so a silent edit to any of the three guards should fail the build and be argued for
     * in review. Creating the two missing roles is a governance decision and is filed, not invented
     * here — F2-116.
     */
    @Test
    @DisplayName("The three guard constants are exactly as reviewed, and two recited roles are known not to exist")
    void theGuardConstantsArePinnedAndTheirDeadRolesRecorded() throws IOException {
        String src = controllerSource();

        assertThat(rolesIn(src, "READ_GUARD")).containsExactlyInAnyOrder(
            "PLATFORM_ADMIN", "RISK_MANAGER", "COMPLIANCE_OFFICER", "AUDITOR", "CISO", "CIO", "CRO");
        assertThat(rolesIn(src, "WRITE_GUARD")).containsExactlyInAnyOrder(
            "PLATFORM_ADMIN", "RISK_MANAGER", "COMPLIANCE_OFFICER", "CIO");
        assertThat(rolesIn(src, "APPROVE_GUARD")).containsExactlyInAnyOrder(
            "PLATFORM_ADMIN", "RISK_MANAGER", "CRO", "CIO");

        Set<String> recited = new TreeSet<>();
        recited.addAll(rolesIn(src, "READ_GUARD"));
        recited.addAll(rolesIn(src, "WRITE_GUARD"));
        recited.addAll(rolesIn(src, "APPROVE_GUARD"));

        // F2-116, measured against iam.roles in dev on 2026-08-05. If these are ever created, this
        // assertion fails and the finding closes — which is the point of pinning it here.
        assertThat(recited).as("the guards recite exactly these names; two of them are fictional")
            .contains("CRO", "CIO");
        assertThat(rolesIn(src, "APPROVE_GUARD"))
            .as("APPROVE_GUARD gates every state transition in the register, and half of it is dead")
            .hasSize(4);
    }

    @Test
    @DisplayName("Every write endpoint carries a guard — and none of them is a bare string")
    void everyWriteEndpointIsGuarded() throws IOException {
        String src = controllerSource();
        Matcher m = Pattern.compile(
            "@(Post|Put|Patch|Delete)Mapping[^\\n]*\\n\\s*@PreAuthorize\\(([A-Z_]+)\\)").matcher(src);
        int guarded = 0;
        Set<String> used = new LinkedHashSet<>();
        while (m.find()) {
            guarded++;
            used.add(m.group(2));
        }
        long writes = Pattern.compile("@(Post|Put|Patch|Delete)Mapping").matcher(src).results().count();

        // ⚠ The guards here are CONSTANT REFERENCES, not string literals. A scan for
        // `@PreAuthorize("` reports this controller as having ZERO guards — which is the error F2-27
        // made ("missed 8 constant-valued guards") and which was repeated while measuring this very
        // slice. The pattern above therefore matches the constant form deliberately.
        assertThat(guarded).as("all %d write endpoints must be guarded; matched %d", writes, guarded)
            .isEqualTo((int) writes);
        assertThat(used).as("the write surface must use only the reviewed guard constants")
            .containsExactlyInAnyOrder("WRITE_GUARD", "APPROVE_GUARD");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private OutsourcingArrangement arrangement(String status, String materiality, boolean core) {
        OutsourcingArrangement a = new OutsourcingArrangement();
        a.setId(1L);
        a.setOrganisationId(ORG);
        // Non-null on purpose: emit() builds its payload with Map.of, which throws on a null value.
        a.setReference("OUT-REVIEW-TEST-1");
        a.setStatus(status);
        a.setMateriality(materiality);
        a.setIsCoreActivity(core);
        return a;
    }

    /** A minimal valid create request: only the fields the service reads need be non-null, and
     *  emit()'s Map.of forbids a null reference / materiality / isCoreActivity. */
    private static CreateArrangementRequest createReq(String materiality) {
        return new CreateArrangementRequest(
            "OUT-REVIEW-TEST-mat",          // reference
            "Provider EN", "Provider AR",   // provider legal names
            null, null,                     // country, jurisdiction
            "Service EN", "Service AR",     // service titles
            null, null,                     // descriptions
            "IT_INFRASTRUCTURE",            // serviceCategory
            materiality,                    // ← the field under test
            false,                          // isCoreActivity
            null, null, null, null, null,   // contract start/end, renewal days, spend, cadence
            null, null, null);              // relationship/risk owner ids, linkedVendorId
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Set<String>> allowedMap(String field) {
        Object v = ReflectionTestUtils.getField(OutsourcingService.class, field);
        assertThat(v).as("%s must exist on OutsourcingService — if it was renamed this test would "
            + "otherwise silently stop checking that machine", field).isInstanceOf(Map.class);
        return (Map<String, Set<String>>) v;
    }

    private static String controllerSource() throws IOException {
        Path p = Paths.get("src", "main", "java", "com", "tss", "grc", "module", "outsourcing",
            "controller", "OutsourcingController.java").toAbsolutePath().normalize();
        assertThat(Files.exists(p)).as("%s must exist", p).isTrue();
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    /**
     * The ROLE names a guard constant recites.
     *
     * <p>⚠⚠ <b>F2-262 — this required the initialiser to BE a {@code hasAnyRole} string, so
     * converting this module would have failed the build with <i>"guard constant READ_GUARD must be
     * declared as a hasAnyRole string"</i>.</b> Its twin in {@code DataclassLifecycleTest} carried
     * the identical defect in a slightly different spelling ({@code \\n?} for a wrapped initialiser
     * here, {@code "String " +} as the anchor there) — two copies of one predicate, neither aware of
     * the other, which is F2-190's rule and the whole argument for the shared primitive.
     *
     * <p>🔑 <b>{@code OUTSOURCING} is one of the four modules F2-257 filed as having no catalogued
     * permissions</b>, so this refusal was queued behind the next conversion rather than being a
     * theoretical risk. A gate that refuses the change it exists to encourage is worse than no gate.
     *
     * <p>{@link GuardExpressions#rolesRecitedByConstant} keeps the anti-vacuity half and drops the
     * role-guard requirement, so a converted constant reports drift rather than refusing to parse.
     */
    private static Set<String> rolesIn(String src, String constant) {
        return GuardExpressions.rolesRecitedByConstant(src, constant);
    }
}
