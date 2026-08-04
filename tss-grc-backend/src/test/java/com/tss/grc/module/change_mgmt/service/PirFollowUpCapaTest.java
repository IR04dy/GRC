package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.module.change_mgmt.entity.ChangeLink;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.repository.ChangeLinkRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.change_mgmt.spi.CapaPort;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S7d / CE-7 — <b>a PIR follow-up becomes tracked work instead of free text going nowhere.</b>
 *
 * <p><b>The gap, measured 2026-08-04 in both databases.</b> CH-11 gave the change record a
 * post-implementation review with a mandatory-PIR gate on closure, and {@code pir_follow_up} was
 * where the reviewer wrote what happens next. Nothing read it:
 *
 * <pre>
 *   change_requests            pir_follow_up populated:   0 dev / 0 prod   (no PIR field ever used)
 *   capa.tasks                 origin_type='change_pir':  0 dev / 0 prod
 *   capa.tasks origin types    8, from 8 other modules (control_test, rca, audit_finding,
 *                              risk_treatment, dpia, incident, inspection_finding,
 *                              processor_assessment) — every module but this one
 * </pre>
 *
 * <p>So a reviewer's stated commitment sat in a text column that no rollup, no overdue sweep and no
 * committee pack ever read — while eight other modules' remediation was tracked, chased and
 * reported. The change module was the one that wrote its follow-ups down and forgot them.
 *
 * <p><b>Why {@code capa.tasks} and not the store the plan named.</b> The S7 row says
 * "{@code CHANGE_PIR} in {@code ck_ca_source}", i.e. {@code sust.corrective_action}. That table is
 * behind {@code @RequireModule("ESG")}, its auto-open notifies {@code SUSTAINABILITY_OFFICER} and
 * dates the action from a sustainability setting, and all eleven of its source values are ESG-domain.
 * A CHANGE-only tenant would have received corrective actions into a module it cannot open. The
 * reasoning is on {@link CapaPort} in full; the consequence for this test is that
 * <b>the slice needs no migration</b> — {@code capa.tasks.origin_type} carries no CHECK constraint.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PirFollowUpCapaTest {

    @Mock private ChangeRequestRepository   changeRepo;
    @Mock private ChangeLinkRepository      linkRepo;
    @Mock private AuditLogService           audit;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private CapaPort                  capaPort;

    private ChangeManagementService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(1L);
        service = new ChangeManagementService(changeRepo, null, null, linkRepo, null, audit, publisher);
        ReflectionTestUtils.setField(service, "capaPort", capaPort);
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(i -> i.getArgument(0));
        when(linkRepo.findByChangeRequestId(any())).thenReturn(List.of());
        when(capaPort.raiseFromChangePir(anyString(), anyString(), anyString(), anyString(),
                                        anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(Optional.of(new CapaPort.RaisedCapa(77L, "CAPA-2026-0077")));
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    private static ChangeRequest reviewed(String followUp, Boolean objectivesMet) {
        ChangeRequest c = new ChangeRequest();
        c.setId(9L);
        c.setOrganisationId(1L);
        c.setChangeCode("CHG-2026-0009");
        c.setTitleEn("Upgrade the payment gateway");
        c.setTitleAr("ترقية بوابة المدفوعات");
        c.setChangeType("NORMAL");
        c.setRiskLevel("HIGH");
        c.setStatus("POST_REVIEW");
        c.setPirSummary("Deployed, with a 20-minute overrun.");
        c.setPirObjectivesMet(objectivesMet);
        c.setPirFollowUp(followUp);
        c.setChangeOwnerName("Platform Owner");
        return c;
    }

    // ── The hook ────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a recorded follow-up raises a CAPA carrying the reviewer's own words, and back-links it")
    void aFollowUpBecomesATrackedCapa() {
        ChangeRequest c = reviewed("Add a pre-flight connectivity check to the runbook.", true);
        when(changeRepo.findById(9L)).thenReturn(Optional.of(c));

        service.update(9L, new ChangeRequest());

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> titleEn = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> titleAr = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> descEn = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> descAr = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> capaType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> severity = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> owner = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDate> target = ArgumentCaptor.forClass(LocalDate.class);
        verify(capaPort).raiseFromChangePir(code.capture(), titleEn.capture(), titleAr.capture(),
            descEn.capture(), descAr.capture(), capaType.capture(), severity.capture(),
            owner.capture(), target.capture());

        assertThat(code.getValue()).as("origin_id must be the change code, so the tracker is "
            + "queryable by change without the CAPA module knowing what a change is")
            .isEqualTo("CHG-2026-0009");
        assertThat(titleEn.getValue()).contains("CHG-2026-0009");
        assertThat(titleAr.getValue())
            .as("house rule 7 — a user-visible record carries both languages, and title_ar is "
              + "NOT NULL on capa.tasks, so a missing Arabic title is a 500 and not a cosmetic gap")
            .isNotBlank()
            .containsPattern("[\\u0600-\\u06FF]");
        assertThat(descEn.getValue())
            .as("the CAPA must carry the reviewer's sentence — a CAPA that only says 'see the "
              + "change' sends its owner back to the record they were escalated from")
            .contains("Add a pre-flight connectivity check to the runbook.");
        assertThat(descAr.getValue()).containsPattern("[\\u0600-\\u06FF]");
        assertThat(severity.getValue())
            .as("severity mirrors the change's risk — the existing mapRiskToSeverity, not a second one")
            .isEqualTo("HIGH");
        assertThat(owner.getValue()).isEqualTo("Platform Owner");
        assertThat(target.getValue())
            .as("a CAPA with no due date never appears in an overdue rollup, which is most of what "
              + "a tracker is for")
            .isEqualTo(LocalDate.now().plusDays(ChangeManagementService.PIR_FOLLOW_UP_TARGET_DAYS));

        ArgumentCaptor<ChangeLink> link = ArgumentCaptor.forClass(ChangeLink.class);
        verify(linkRepo).save(link.capture());
        assertThat(link.getValue().getEntityType()).isEqualTo("CAPA");
        assertThat(link.getValue().getLinkType()).isEqualTo("FOLLOW_UP");
        assertThat(link.getValue().getEntityId()).isEqualTo(77L);
        assertThat(link.getValue().getChangeRequestId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("no follow-up recorded → no CAPA (the hook never invents a commitment nobody made)")
    void silenceRaisesNothing() {
        for (String blank : List.of("", "   ")) {
            ChangeRequest c = reviewed(blank, true);
            when(changeRepo.findById(9L)).thenReturn(Optional.of(c));
            service.update(9L, new ChangeRequest());
        }
        ChangeRequest none = reviewed(null, true);
        when(changeRepo.findById(9L)).thenReturn(Optional.of(none));
        service.update(9L, new ChangeRequest());

        verify(capaPort, never()).raiseFromChangePir(anyString(), anyString(), anyString(),
            anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("idempotent — an existing CAPA/FOLLOW_UP link means the change already has its action")
    void oneCapaPerChangeNotOnePerEdit() {
        ChangeRequest c = reviewed("Tighten the rollback rehearsal.", false);
        when(changeRepo.findById(9L)).thenReturn(Optional.of(c));
        ChangeLink existing = new ChangeLink();
        existing.setChangeRequestId(9L);
        existing.setEntityType("CAPA");
        existing.setLinkType("FOLLOW_UP");
        existing.setEntityId(77L);
        when(linkRepo.findByChangeRequestId(9L)).thenReturn(List.of(existing));

        service.update(9L, new ChangeRequest());

        verify(capaPort, never()).raiseFromChangePir(anyString(), anyString(), anyString(),
            anyString(), anyString(), anyString(), anyString(), anyString(), any());
        verify(linkRepo, never()).save(any(ChangeLink.class));
    }

    @Test
    @DisplayName("an unrelated link is not mistaken for the marker")
    void theDedupMarkerIsTheWholePairNotEitherHalf() {
        ChangeRequest c = reviewed("Document the new failover step.", true);
        when(changeRepo.findById(9L)).thenReturn(Optional.of(c));
        // CH-13's rollback incident is CAPA-shaped in neither field; a manually-linked CAPA with a
        // different link type, and a FOLLOW_UP link to something else, are the near misses that a
        // one-field check would swallow — and swallowing one means the change silently gets no CAPA.
        ChangeLink incident = new ChangeLink();
        incident.setEntityType("INCIDENT"); incident.setLinkType("TRIGGERED_BY");
        ChangeLink manualCapa = new ChangeLink();
        manualCapa.setEntityType("CAPA"); manualCapa.setLinkType("REMEDIATES");
        ChangeLink otherFollowUp = new ChangeLink();
        otherFollowUp.setEntityType("RISK"); otherFollowUp.setLinkType("FOLLOW_UP");
        when(linkRepo.findByChangeRequestId(9L))
            .thenReturn(List.of(incident, manualCapa, otherFollowUp));

        service.update(9L, new ChangeRequest());

        verify(capaPort).raiseFromChangePir(anyString(), anyString(), anyString(), anyString(),
            anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("the POST_REVIEW transition raises it too — both PIR entry points, not just the edit")
    void theTransitionRaisesItAsWell() {
        ChangeRequest c = reviewed(null, null);
        c.setStatus("IMPLEMENTED");
        when(changeRepo.findById(9L)).thenReturn(Optional.of(c));

        Map<String, Object> body = new HashMap<>();
        body.put("target", "POST_REVIEW");
        body.put("pirSummary", "Delivered.");
        body.put("pirFollowUp", "Re-run the load test with production data volumes.");
        service.transition(9L, "POST_REVIEW", body);

        ArgumentCaptor<String> descEn = ArgumentCaptor.forClass(String.class);
        verify(capaPort).raiseFromChangePir(anyString(), anyString(), anyString(), descEn.capture(),
            anyString(), anyString(), anyString(), anyString(), any());
        assertThat(descEn.getValue()).contains("Re-run the load test with production data volumes.");
    }

    @Test
    @DisplayName("an absent CAPA tracker no-ops — losing the derived record must not block the review")
    void theHookIsOptional() {
        ReflectionTestUtils.setField(service, "capaPort", null);
        ChangeRequest c = reviewed("Add monitoring on the new queue.", true);
        when(changeRepo.findById(9L)).thenReturn(Optional.of(c));

        service.update(9L, new ChangeRequest());

        verify(changeRepo).save(any(ChangeRequest.class));
        verify(linkRepo, never()).save(any(ChangeLink.class));
    }

    // ── The classification ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("objectives met → PREVENTIVE; not met or unstated → CORRECTIVE")
    void theClassificationComesFromWhatTheReviewerRecorded() {
        assertThat(ChangeManagementService.pirCapaType(Boolean.TRUE)).isEqualTo("PREVENTIVE");
        assertThat(ChangeManagementService.pirCapaType(Boolean.FALSE)).isEqualTo("CORRECTIVE");
        assertThat(ChangeManagementService.pirCapaType(null))
            .as("unset means the reviewer did not say, and 'we do not know whether it worked' is "
              + "not evidence that it did — so an unstated objective must not classify as the "
              + "gentler of the two")
            .isEqualTo("CORRECTIVE");
    }

    // ── The gate that makes the hook reachable ──────────────────────────────────────────────────

    @Test
    @DisplayName("⚠ a change that failed its objectives cannot close with no follow-up")
    void afailedChangeMayNotCloseSilently() {
        ChangeRequest c = reviewed(null, false);
        when(changeRepo.findById(9L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.transition(9L, "CLOSED", Map.of("target", "CLOSED")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("did not meet its objectives")
            .hasMessageContaining("follow-up");
        verify(changeRepo, never()).save(any(ChangeRequest.class));
    }

    @Test
    @DisplayName("…and the gate does not exempt STANDARD, because it fires on a volunteered finding")
    void theGateIsConditionedOnTheFindingNotOnTheChangeType() {
        // The mandatory-PIR-summary gate exempts STANDARD changes: pre-approved and low-risk, they
        // need no retrospective. That reason does not reach this gate — if someone recorded a
        // review AND recorded that it failed, the exemption's premise is already gone.
        ChangeRequest c = reviewed(null, false);
        c.setChangeType("STANDARD");
        c.setPirSummary(null);   // and the summary gate would have let this through
        when(changeRepo.findById(9L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.transition(9L, "CLOSED", Map.of("target", "CLOSED")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("did not meet its objectives");
    }

    @Test
    @DisplayName("a failed change WITH a follow-up closes — the gate asks for a statement, not for approval")
    void statingTheFollowUpUnblocksClosure() {
        ChangeRequest c = reviewed("Rolled back; the vendor patch ships next sprint.", false);
        when(changeRepo.findById(9L)).thenReturn(Optional.of(c));

        service.transition(9L, "CLOSED", Map.of("target", "CLOSED"));

        ArgumentCaptor<ChangeRequest> saved = ArgumentCaptor.forClass(ChangeRequest.class);
        verify(changeRepo).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("CLOSED");
        assertThat(saved.getValue().getClosureDate()).isNotNull();
    }

    @Test
    @DisplayName("a successful change closes with no follow-up — the gate is narrow by design")
    void successNeedsNoFollowUp() {
        ChangeRequest c = reviewed(null, true);
        when(changeRepo.findById(9L)).thenReturn(Optional.of(c));

        service.transition(9L, "CLOSED", Map.of("target", "CLOSED"));

        verify(changeRepo).save(any(ChangeRequest.class));
    }

    @Test
    @DisplayName("an unstated objective does not block closure either — silence is not a failure")
    void anUnsetObjectiveIsNotAFailedOne() {
        // pirCapaType treats null as corrective (fail-safe on classification), and the gate must
        // NOT copy that: 15 of 17 dev changes have no PIR at all, so a gate that fired on null
        // would refuse to close almost every change either database holds — the F2-92/S6c shape.
        ChangeRequest c = reviewed(null, null);
        when(changeRepo.findById(9L)).thenReturn(Optional.of(c));

        service.transition(9L, "CLOSED", Map.of("target", "CLOSED"));

        verify(changeRepo).save(any(ChangeRequest.class));
    }
}
