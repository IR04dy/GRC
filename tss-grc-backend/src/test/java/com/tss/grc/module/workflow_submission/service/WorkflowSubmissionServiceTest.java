package com.tss.grc.module.workflow_submission.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.workflow_submission.entity.WorkflowSubmission;
import com.tss.grc.module.workflow_submission.repository.WorkflowSubmissionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage for the generic wizard ledger (V125 workflow_submissions).
 *
 * <ul>
 *   <li>{@code create()} stamps the organisation, defaults status to SUBMITTED,
 *       validates the four required fields, and preserves the typed envelope</li>
 *   <li>{@code review()} maps the four supported decisions, rejects unknowns,
 *       and refuses to mutate a row from another tenant (404)</li>
 *   <li>{@code list*()} variants all funnel through the right tenant-scoped
 *       repository finder</li>
 * </ul>
 *
 * <p>These tests guard against the most likely regressions: a cross-tenant
 * leak in review(), a default-status drift, and a silent validation pass on
 * blank required fields.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowSubmissionServiceTest {

    private static final Long ORG_ID      = 7L;
    private static final Long OTHER_ORG   = 99L;
    private static final Long SUBMITTER   = 1001L;   // owner of the submission
    private static final Long REVIEWER    = 2002L;   // distinct checker (maker-checker)

    @Mock private WorkflowSubmissionRepository repo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private WorkflowSubmissionService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(repo.save(any(WorkflowSubmission.class))).thenAnswer(inv -> {
            WorkflowSubmission s = inv.getArgument(0);
            if (s.getId() == null) s.setId(123L);
            return s;
        });
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ── create() ────────────────────────────────────────────────────────

    @Test void create_persistsEnvelopeWithCurrentTenant() {
        var body = new WorkflowSubmissionService.CreateBody(
            "governance-board-pack", "GOVERNANCE",
            "Board pack Q2 2026 (BOARD)", "ملف المجلس",
            "9 sections · approvals: CRO+CISO+CEO · watermarked", null,
            null, null,
            "{\"quarter\":\"Q2 2026\",\"audience\":\"BOARD\"}");

        var saved = service.create(body, SUBMITTER);

        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getWorkflowId()).isEqualTo("governance-board-pack");
        assertThat(saved.getWorkflowModule()).isEqualTo("GOVERNANCE");
        assertThat(saved.getStatus()).isEqualTo("SUBMITTED");        // default
        assertThat(saved.getTitleEn()).isEqualTo("Board pack Q2 2026 (BOARD)");
        assertThat(saved.getSummaryEn()).contains("9 sections");
        assertThat(saved.getPayloadJson()).contains("Q2 2026");
        assertThat(saved.getSubmittedAt()).isNotNull();
    }

    @Test void create_honoursExplicitStatusAndRegulatoryAnchor() {
        var body = new WorkflowSubmissionService.CreateBody(
            "pdpl-cross-border", "PDPL",
            "Transfer: Acme Inc. (UAE)", null,
            "SCC · STANDARD · LT_10K · SDO=SDO-2026-0042 · review every 12m", null,
            "PDPL Art. 29", "APPROVED",
            "{\"recipientName\":\"Acme Inc.\"}");

        var saved = service.create(body, SUBMITTER);

        assertThat(saved.getStatus()).isEqualTo("APPROVED");
        assertThat(saved.getRegulatoryAnchor()).isEqualTo("PDPL Art. 29");
    }

    @Test void create_blankWorkflowId_rejects() {
        var body = new WorkflowSubmissionService.CreateBody(
            "", "ERM", "x", null, null, null, null, null, "{}");
        assertThatThrownBy(() -> service.create(body, SUBMITTER))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("workflowId");
    }

    @Test void create_blankModule_rejects() {
        var body = new WorkflowSubmissionService.CreateBody(
            "erm-risk-treatment", "", "x", null, null, null, null, null, "{}");
        assertThatThrownBy(() -> service.create(body, SUBMITTER))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("workflowModule");
    }

    @Test void create_blankTitle_rejects() {
        var body = new WorkflowSubmissionService.CreateBody(
            "erm-risk-treatment", "ERM", " ", null, null, null, null, null, "{}");
        assertThatThrownBy(() -> service.create(body, SUBMITTER))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("titleEn");
    }

    @Test void create_blankPayload_rejects() {
        var body = new WorkflowSubmissionService.CreateBody(
            "erm-risk-treatment", "ERM", "Some title", null, null, null, null, null, "");
        assertThatThrownBy(() -> service.create(body, SUBMITTER))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("payloadJson");
    }

    // ── review() — state machine ───────────────────────────────────────

    @Test void review_approveSetsApprovedStatus() {
        var existing = sampleSaved(ORG_ID, 555L, "SUBMITTED");
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.of(existing));

        var out = service.review(555L, new WorkflowSubmissionService.ReviewBody("APPROVE", "Looks good"), REVIEWER);

        assertThat(out.getStatus()).isEqualTo("APPROVED");
        assertThat(out.getReviewerNote()).isEqualTo("Looks good");
        assertThat(out.getReviewedBy()).isEqualTo(REVIEWER);
        assertThat(out.getReviewedAt()).isNotNull();
    }

    @Test void review_rejectSetsRejectedStatus() {
        var existing = sampleSaved(ORG_ID, 555L, "SUBMITTED");
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.of(existing));

        var out = service.review(555L, new WorkflowSubmissionService.ReviewBody("reject", null), REVIEWER);

        assertThat(out.getStatus()).isEqualTo("REJECTED");
    }

    @Test void review_closeSetsClosedStatus() {
        var existing = sampleSaved(ORG_ID, 555L, "APPROVED");
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.of(existing));

        var out = service.review(555L, new WorkflowSubmissionService.ReviewBody("close", null), REVIEWER);

        assertThat(out.getStatus()).isEqualTo("CLOSED");
    }

    @Test void review_underReviewIsSupported() {
        var existing = sampleSaved(ORG_ID, 555L, "SUBMITTED");
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.of(existing));

        var out = service.review(555L, new WorkflowSubmissionService.ReviewBody("UNDER_REVIEW", null), REVIEWER);

        assertThat(out.getStatus()).isEqualTo("UNDER_REVIEW");
    }

    @Test void review_unknownDecisionRejected() {
        var existing = sampleSaved(ORG_ID, 555L, "SUBMITTED");
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.review(555L,
                new WorkflowSubmissionService.ReviewBody("DEFER", null), REVIEWER))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DEFER");
    }

    @Test void review_otherTenantsRow_throwsNotFound() {
        // Repository must not return rows from other tenants — this is the
        // canonical cross-tenant FK leak guard.
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.review(555L,
                new WorkflowSubmissionService.ReviewBody("APPROVE", null), REVIEWER))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("555");
    }

    // ── Segregation of duties (maker-checker) ──────────────────────────

    @Test void create_stampsSubmitterFromAuthenticatedPrincipal() {
        // SEC-IAM-004: the submitter is server-stamped from the caller so the
        // self-review guard has a trustworthy owner to compare against.
        var body = new WorkflowSubmissionService.CreateBody(
            "erm-risk-acceptance", "ERM", "Accept above-appetite risk", null,
            null, null, null, null, "{}");

        var saved = service.create(body, SUBMITTER);

        assertThat(saved.getSubmittedBy()).isEqualTo(SUBMITTER);
    }

    @Test void review_bySubmitter_rejectedAsSelfReview() {
        // SEC-IAM-004: a user must not approve / reject / close their own
        // submission. The owner is SUBMITTER; reviewing as SUBMITTER fails.
        var existing = sampleSaved(ORG_ID, 555L, "SUBMITTED");
        existing.setSubmittedBy(SUBMITTER);
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.review(555L,
                new WorkflowSubmissionService.ReviewBody("APPROVE", null), SUBMITTER))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Segregation of duties");

        // The self-review attempt must not mutate the row or fire an event.
        verify(repo, never()).save(any(WorkflowSubmission.class));
        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
    }

    @Test void review_byDifferentUser_thanSubmitter_succeeds() {
        // SEC-IAM-004: a distinct reviewer (the checker) is allowed.
        var existing = sampleSaved(ORG_ID, 555L, "SUBMITTED");
        existing.setSubmittedBy(SUBMITTER);
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.of(existing));

        var out = service.review(555L,
            new WorkflowSubmissionService.ReviewBody("APPROVE", null), REVIEWER);

        assertThat(out.getStatus()).isEqualTo("APPROVED");
        assertThat(out.getReviewedBy()).isEqualTo(REVIEWER);
    }

    // ── list*() — tenant-scoped finders ────────────────────────────────

    @Test void list_usesOrgScopedFinder() {
        when(repo.findByOrganisationIdOrderBySubmittedAtDesc(ORG_ID))
            .thenReturn(java.util.List.of(sampleSaved(ORG_ID, 1L, "SUBMITTED")));

        var rows = service.list();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getOrganisationId()).isEqualTo(ORG_ID);
    }

    @Test void listByWorkflowId_filtersByCatalogId() {
        when(repo.findByOrganisationIdAndWorkflowIdOrderBySubmittedAtDesc(ORG_ID, "incident-rca"))
            .thenReturn(java.util.List.of(sampleSaved(ORG_ID, 11L, "APPROVED")));

        var rows = service.listByWorkflowId("incident-rca");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getId()).isEqualTo(11L);
    }

    @Test void listByModule_filtersByBucket() {
        when(repo.findByOrganisationIdAndWorkflowModuleOrderBySubmittedAtDesc(ORG_ID, "GOVERNANCE"))
            .thenReturn(java.util.List.of(sampleSaved(ORG_ID, 22L, "SUBMITTED")));

        var rows = service.listByModule("GOVERNANCE");

        assertThat(rows).hasSize(1);
    }

    @Test void get_otherTenantsRow_throwsNotFound() {
        when(repo.findByOrganisationIdAndId(ORG_ID, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(999L))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test void noTenant_throwsIllegalState() {
        TenantContext.clear();
        var body = new WorkflowSubmissionService.CreateBody(
            "x", "ERM", "x", null, null, null, null, null, "{}");

        assertThatThrownBy(() -> service.create(body, SUBMITTER))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("tenant");
    }

    // ── Audit trail ────────────────────────────────────────────────────

    @Test void create_writesAuditLogEntry() {
        var body = new WorkflowSubmissionService.CreateBody(
            "governance-board-pack", "GOVERNANCE",
            "Board pack Q2 2026", null, null, null,
            "PDPL Art. 23", null,
            "{\"q\":\"Q2 2026\"}");

        service.create(body, SUBMITTER);

        ArgumentCaptor<String> action  = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(audit).log(action.capture(), eq("WorkflowSubmission"),
                          anyLong(), message.capture());
        assertThat(action.getValue()).isEqualTo("CREATE");
        assertThat(message.getValue()).contains("workflow=governance-board-pack");
        assertThat(message.getValue()).contains("module=GOVERNANCE");
        assertThat(message.getValue()).contains("status=SUBMITTED");
        assertThat(message.getValue()).contains("anchor=PDPL Art. 23");
    }

    @Test void review_writesAuditLogEntryWithStatusTransition() {
        var existing = sampleSaved(ORG_ID, 555L, "SUBMITTED");
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.of(existing));

        service.review(555L, new WorkflowSubmissionService.ReviewBody("APPROVE", "Looks good"), REVIEWER);

        ArgumentCaptor<String> action  = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(audit).log(action.capture(), eq("WorkflowSubmission"),
                          eq(555L), message.capture());
        assertThat(action.getValue()).isEqualTo("APPROVED");
        assertThat(message.getValue()).contains("SUBMITTED → APPROVED");
        assertThat(message.getValue()).contains("workflow=erm-risk-treatment");
        assertThat(message.getValue()).contains("note=Looks good");
    }

    @Test void review_blankNoteOmittedFromAuditMessage() {
        var existing = sampleSaved(ORG_ID, 555L, "SUBMITTED");
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.of(existing));

        service.review(555L, new WorkflowSubmissionService.ReviewBody("REJECT", null), REVIEWER);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("REJECTED"), eq("WorkflowSubmission"),
                          eq(555L), message.capture());
        assertThat(message.getValue()).doesNotContain("note=");
    }

    @Test void review_otherTenant_notFound_noAuditLogged() {
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.review(555L,
                new WorkflowSubmissionService.ReviewBody("APPROVE", null), REVIEWER))
            .isInstanceOf(EntityNotFoundException.class);

        // Cross-tenant attempt must NOT leave an audit trail under our tenant
        verify(audit, never()).log(anyString(), anyString(), anyLong(), anyString());
        // … and must NOT fire an F8 event under our tenant either
        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
    }

    // ── F8 automation events ──────────────────────────────────────────

    @Test void create_publishesWorkflowSubmissionCreatedEvent() {
        var body = new WorkflowSubmissionService.CreateBody(
            "pdpl-breach", "PDPL",
            "Personal data breach — STC partner", null, null, null,
            "PDPL Art. 23", null,
            "{\"discoveredAt\":\"2026-05-20T08:00:00Z\"}");

        service.create(body, SUBMITTER);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        DomainEvent ev = captor.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("WORKFLOW_SUBMISSION_CREATED");
        assertThat(ev.entityType()).isEqualTo("WorkflowSubmission");
        assertThat(ev.organisationId()).isEqualTo(ORG_ID);
        // Payload carries the fields F8 rule conditions match against.
        assertThat(ev.payload()).containsEntry("workflowId", "pdpl-breach");
        assertThat(ev.payload()).containsEntry("workflowModule", "PDPL");
        assertThat(ev.payload()).containsEntry("status", "SUBMITTED");
        assertThat(ev.payload()).containsEntry("regulatoryAnchor", "PDPL Art. 23");
    }

    @Test void review_publishesWorkflowSubmissionReviewedEvent() {
        var existing = sampleSaved(ORG_ID, 555L, "SUBMITTED");
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.of(existing));

        service.review(555L, new WorkflowSubmissionService.ReviewBody("APPROVE", null), REVIEWER);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        DomainEvent ev = captor.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("WORKFLOW_SUBMISSION_REVIEWED");
        assertThat(ev.payload()).containsEntry("status", "APPROVED");
        assertThat(ev.payload()).containsEntry("previousStatus", "SUBMITTED");
        // Rule conditions can route on the wizard id too.
        assertThat(ev.payload()).containsEntry("workflowId", "erm-risk-treatment");
    }

    @Test void create_payloadNeverHasNullValues() {
        // The F8 engine's equality check would NPE on null payload values.
        // Confirm nulls are coerced to "" before they hit the event bus.
        var body = new WorkflowSubmissionService.CreateBody(
            "erm-risk-treatment", "ERM", "x", null, null, null,
            null /* no anchor */, null,
            "{}");

        service.create(body, SUBMITTER);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().payload().get("regulatoryAnchor")).isEqualTo("");
    }

    // ── countsByWorkflowId() — Hub per-wizard aggregate ────────────────
    //
    // Locks the JPQL projection wiring so the (workflowId, total, open,
    // approved) tuple ordering stays exactly as the Hub badge expects.
    // Subtle: the repo method returns List<Object[]> with the SUM(CASE WHEN)
    // values as java.lang.Number; SQL Server can hand back either Long or
    // Integer depending on the column type the driver picked, so the
    // service must accept both. The Hub badge fails silently (shows wrong
    // counts, no exception) if any of this drifts.

    @Test void countsByWorkflowId_mapsRepoRowsToTypedRecord() {
        // Two wizards have submissions; per-bucket SUMs come straight from
        // the JPQL projection. Service must preserve order and types.
        when(repo.countsByWorkflowId(ORG_ID)).thenReturn(java.util.List.<Object[]>of(
            new Object[] { "pdpl-breach",          5L, 2L, 3L },
            new Object[] { "governance-board-pack", 1L, 0L, 1L }
        ));

        var counts = service.countsByWorkflowId();

        assertThat(counts).hasSize(2);
        var byId = counts.stream().collect(
            java.util.stream.Collectors.toMap(
                WorkflowSubmissionService.WorkflowCount::workflowId, c -> c));
        assertThat(byId.get("pdpl-breach").total()).isEqualTo(5L);
        assertThat(byId.get("pdpl-breach").open()).isEqualTo(2L);
        assertThat(byId.get("pdpl-breach").approved()).isEqualTo(3L);
        assertThat(byId.get("governance-board-pack").total()).isEqualTo(1L);
        assertThat(byId.get("governance-board-pack").open()).isEqualTo(0L);
        assertThat(byId.get("governance-board-pack").approved()).isEqualTo(1L);
    }

    @Test void countsByWorkflowId_acceptsIntegerNumbersFromDriver() {
        // SQL Server's JDBC driver can hand SUM(CASE WHEN ...) back as
        // Integer rather than Long for small magnitudes. The service maps
        // via ((Number) row[i]).longValue() so both shapes must work — if
        // someone "simplifies" the cast to (Long) the badge breaks for
        // every tenant under a few hundred rows.
        when(repo.countsByWorkflowId(ORG_ID)).thenReturn(java.util.List.<Object[]>of(
            new Object[] { "erm-risk-treatment", Integer.valueOf(7), Integer.valueOf(2), Integer.valueOf(4) }
        ));

        var counts = service.countsByWorkflowId();

        assertThat(counts).hasSize(1);
        assertThat(counts.get(0).total()).isEqualTo(7L);
        assertThat(counts.get(0).open()).isEqualTo(2L);
        assertThat(counts.get(0).approved()).isEqualTo(4L);
    }

    @Test void countsByWorkflowId_emptyResult_returnsEmptyList() {
        // A tenant that has never created a submission must get an empty
        // list, not null and not an exception — the Hub renders no badges.
        when(repo.countsByWorkflowId(ORG_ID)).thenReturn(java.util.List.<Object[]>of());

        var counts = service.countsByWorkflowId();

        assertThat(counts).isEmpty();
    }

    @Test void countsByWorkflowId_isTenantScoped() {
        // Same JVM, two tenants asking in sequence. Each must see only
        // their own aggregates — the JPQL filters on :orgId, and the
        // service must pass the current TenantContext's id (not the
        // process-wide default, not the previously-queried tenant).
        when(repo.countsByWorkflowId(ORG_ID)).thenReturn(java.util.List.<Object[]>of(
            new Object[] { "pdpl-breach", 3L, 1L, 2L }
        ));
        when(repo.countsByWorkflowId(OTHER_ORG)).thenReturn(java.util.List.<Object[]>of(
            new Object[] { "erm-risk-treatment", 9L, 4L, 5L }
        ));

        var ours = service.countsByWorkflowId();
        TenantContext.setCurrentTenant(OTHER_ORG);
        var theirs = service.countsByWorkflowId();

        assertThat(ours).hasSize(1);
        assertThat(ours.get(0).workflowId()).isEqualTo("pdpl-breach");
        assertThat(theirs).hasSize(1);
        assertThat(theirs.get(0).workflowId()).isEqualTo("erm-risk-treatment");

        // Belt-and-braces: each invocation must have hit the repo with
        // the right orgId — otherwise a regression where the service
        // caches one tenant's response under a class field would slip
        // through the "results look right" check above.
        verify(repo).countsByWorkflowId(ORG_ID);
        verify(repo).countsByWorkflowId(OTHER_ORG);
    }

    @Test void countsByWorkflowId_noTenant_throwsIllegalState() {
        // Same tenant-required posture as create/review: every read
        // (including this aggregate) must refuse to run without an
        // explicit TenantContext. Prevents accidental "global counts"
        // in a poorly-written cron or admin script.
        TenantContext.clear();

        assertThatThrownBy(() -> service.countsByWorkflowId())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("tenant");
    }

    // ── linkSpawnedRequest() — G1 submission↔engine link ──────────────

    @Test void linkSpawnedRequest_setsPrimaryWhenNull() {
        var existing = sampleSaved(ORG_ID, 555L, "APPROVED");
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.of(existing));

        var out = service.linkSpawnedRequest(555L, 4242L);

        assertThat(out.getSpawnedRequestId()).isEqualTo(4242L);
        verify(repo).save(existing);
        verify(audit).log(eq("LINK_REQUEST"), eq("WorkflowSubmission"), eq(555L), anyString());
    }

    @Test void linkSpawnedRequest_idempotent_doesNotOverwrite() {
        var existing = sampleSaved(ORG_ID, 555L, "APPROVED");
        existing.setSpawnedRequestId(111L);
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.of(existing));

        var out = service.linkSpawnedRequest(555L, 999L);

        assertThat(out.getSpawnedRequestId()).isEqualTo(111L);   // first link wins
        verify(repo, never()).save(any(WorkflowSubmission.class));
    }

    @Test void linkSpawnedRequest_otherTenantsRow_throwsNotFound() {
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.linkSpawnedRequest(555L, 4242L))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private WorkflowSubmission sampleSaved(Long org, Long id, String status) {
        var s = new WorkflowSubmission();
        s.setId(id);
        s.setOrganisationId(org);
        s.setWorkflowId("erm-risk-treatment");
        s.setWorkflowModule("ERM");
        s.setTitleEn("Sample");
        s.setStatus(status);
        s.setPayloadJson("{}");
        return s;
    }
}
