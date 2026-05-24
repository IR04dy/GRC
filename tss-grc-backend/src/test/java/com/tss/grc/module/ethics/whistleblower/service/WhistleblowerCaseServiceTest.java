package com.tss.grc.module.ethics.whistleblower.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.CaseSubmissionRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.CaseSubmissionResponse;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.CloseRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.InvestigationUpdateRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.PublicCaseResponse;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.ReporterReplyRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.TriageRequest;
import com.tss.grc.module.ethics.whistleblower.entity.WhistleblowerCase;
import com.tss.grc.module.ethics.whistleblower.entity.WhistleblowerCaseUpdate;
import com.tss.grc.module.ethics.whistleblower.repository.WhistleblowerCaseRepository;
import com.tss.grc.module.ethics.whistleblower.repository.WhistleblowerCaseUpdateRepository;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WhistleblowerCaseService} — Sprint 13.
 *
 * <p>Coverage:
 * <ul>
 *   <li>Public submission — anonymous + identified flavours, default-org fallback</li>
 *   <li>IP-non-logging via the anonymous audit path</li>
 *   <li>lookupByToken returns ONLY reporter-visible updates</li>
 *   <li>Reporter reply via token records an update with NULL actor</li>
 *   <li>Triage transitions, severity revision, investigator assignment</li>
 *   <li>Senior-exec flag fires WHISTLEBLOWER_SENIOR_EXEC_FLAGGED at submit AND triage</li>
 *   <li>Investigation lifecycle (start, note, visible-to-reporter)</li>
 *   <li>Close happy path + illegal-transition rejections (reopen, etc.)</li>
 *   <li>Tenant isolation on internal reads (loadOrThrow path)</li>
 *   <li>CAPA linking</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class WhistleblowerCaseServiceTest {

    private static final Long ORG_ID = 7L;
    private static final Long OTHER_ORG = 9L;

    @Mock private WhistleblowerCaseRepository caseRepo;
    @Mock private WhistleblowerCaseUpdateRepository updateRepo;
    @Mock private AuditLogService audit;
    @Mock private AuditLogRepository auditRepo;
    @Mock private ApplicationEventPublisher publisher;

    @InjectMocks private WhistleblowerCaseService service;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ──────────────────────────────────────────────────

    private static WhistleblowerCase existing(Long id, String status, boolean seniorExec) {
        WhistleblowerCase c = new WhistleblowerCase();
        c.setId(id);
        c.setOrganisationId(ORG_ID);
        c.setCaseCode("WB-2026-" + String.format("%03d", id));
        c.setSubmissionToken(UUID.randomUUID());
        c.setStatus(status);
        c.setCategory("FRAUD");
        c.setSeverity("MEDIUM");
        c.setSubjectEn("Subject");
        c.setSubjectAr("الموضوع");
        c.setNarrativeEn("Narrative");
        c.setNarrativeAr("السرد");
        c.setReporterType("ANONYMOUS");
        c.setIsAnonymous(true);
        c.setInvolvesSeniorExecutive(seniorExec);
        c.setSubmittedAt(java.time.LocalDateTime.now().minusDays(1));
        return c;
    }

    private static CaseSubmissionRequest anonReq() {
        return new CaseSubmissionRequest(
                "PUBLIC_PORTAL", "ANONYMOUS",
                null, null, null, null, true,
                "FRAUD", "HIGH",
                "Suspected expense fraud", "اشتباه في احتيال نفقات",
                "Detailed narrative in English",
                "السرد التفصيلي بالعربية",
                "Sales team", null, null, false);
    }

    private static CaseSubmissionRequest identifiedReq() {
        return new CaseSubmissionRequest(
                "INTERNAL_PORTAL", "EMPLOYEE",
                "Sara Al-Otaibi", "sara@bank.sa", "+9665XXXX",
                "EMP-04421", false,
                "HARASSMENT", "MEDIUM",
                "Inappropriate comments", "تعليقات غير لائقة",
                "Narrative", "السرد",
                "Desk supervisor", null, null, false);
    }

    private static CaseSubmissionRequest seniorExecReq() {
        return new CaseSubmissionRequest(
                "EXTERNAL_PARTNER", "VENDOR",
                null, "contact@vendor.sa", null, null, false,
                "CONFLICT_OF_INTEREST", "CRITICAL",
                "Procurement conflict", "تضارب في المشتريات",
                "Senior exec involved", "تورّط مسؤول تنفيذي",
                "Chief Procurement Officer", null, null, true);
    }

    // ─── Submission ────────────────────────────────────────────────

    @Test
    void submit_anonymousRequest_persistsTokenAndStripsContactFields() {
        when(caseRepo.save(any(WhistleblowerCase.class))).thenAnswer(inv -> {
            WhistleblowerCase c = inv.getArgument(0);
            c.setId(101L);
            return c;
        });
        when(caseRepo.countByOrganisationIdAndIsDeletedFalse(ORG_ID)).thenReturn(0L);

        CaseSubmissionResponse out = service.submitCase(anonReq());

        assertThat(out.caseCode()).startsWith("WB-").matches("WB-\\d{4}-\\d{3}");
        assertThat(out.submissionToken()).isNotNull();
        assertThat(out.status()).isEqualTo("RECEIVED");
        assertThat(out.retentionNoticeAr()).contains("رمز التتبّع");

        ArgumentCaptor<WhistleblowerCase> captor = ArgumentCaptor.forClass(WhistleblowerCase.class);
        verify(caseRepo).save(captor.capture());
        WhistleblowerCase saved = captor.getValue();
        assertThat(saved.getIsAnonymous()).isTrue();
        // Anonymous report — contact fields must NOT be persisted.
        assertThat(saved.getReporterName()).isNull();
        assertThat(saved.getReporterEmail()).isNull();
        assertThat(saved.getReporterPhone()).isNull();
        assertThat(saved.getReporterEmployeeId()).isNull();
    }

    @Test
    void submit_identifiedRequest_persistsContactFields() {
        when(caseRepo.save(any(WhistleblowerCase.class))).thenAnswer(inv -> {
            WhistleblowerCase c = inv.getArgument(0);
            c.setId(102L);
            return c;
        });
        when(caseRepo.countByOrganisationIdAndIsDeletedFalse(ORG_ID)).thenReturn(1L);

        service.submitCase(identifiedReq());

        ArgumentCaptor<WhistleblowerCase> captor = ArgumentCaptor.forClass(WhistleblowerCase.class);
        verify(caseRepo).save(captor.capture());
        WhistleblowerCase saved = captor.getValue();
        assertThat(saved.getIsAnonymous()).isFalse();
        assertThat(saved.getReporterName()).isEqualTo("Sara Al-Otaibi");
        assertThat(saved.getReporterEmail()).isEqualTo("sara@bank.sa");
        assertThat(saved.getReporterEmployeeId()).isEqualTo("EMP-04421");
        assertThat(saved.getReporterType()).isEqualTo("EMPLOYEE");
    }

    @Test
    void submit_seniorExecutiveFlag_firesAdditionalEscalationEvent() {
        when(caseRepo.save(any(WhistleblowerCase.class))).thenAnswer(inv -> {
            WhistleblowerCase c = inv.getArgument(0);
            c.setId(103L);
            return c;
        });
        when(caseRepo.countByOrganisationIdAndIsDeletedFalse(ORG_ID)).thenReturn(2L);

        service.submitCase(seniorExecReq());

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, times(2)).publishEvent(captor.capture());
        List<DomainEvent> events = captor.getAllValues();

        assertThat(events).extracting(DomainEvent::triggerEvent)
                .containsExactly("WHISTLEBLOWER_CASE_SUBMITTED",
                                  "WHISTLEBLOWER_SENIOR_EXEC_FLAGGED");
        assertThat(events.get(1).payload())
                .containsEntry("involvesSeniorExecutive", "true");
    }

    @Test
    void submit_publicPortal_writesAnonymousAuditWithoutIp() {
        when(caseRepo.save(any(WhistleblowerCase.class))).thenAnswer(inv -> {
            WhistleblowerCase c = inv.getArgument(0);
            c.setId(104L);
            return c;
        });
        when(caseRepo.countByOrganisationIdAndIsDeletedFalse(ORG_ID)).thenReturn(3L);

        service.submitCase(anonReq());

        // Anonymous path goes through auditRepo.save() — NOT audit.log().
        // The log() path is what would auto-pull IP / user-agent.
        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditRepo).save(captor.capture());
        AuditLogEntry entry = captor.getValue();
        assertThat(entry.getIpAddress()).isNull();
        assertThat(entry.getUserAgent()).isNull();
        assertThat(entry.getSourceEndpoint()).isNull();
        assertThat(entry.getUserUsername()).isNull();
        assertThat(entry.getActionType()).isEqualTo("CREATE");
        assertThat(entry.getEntityType()).isEqualTo("WhistleblowerCase");
        verify(audit, never()).log(any(), any(), any(), any());
    }

    @Test
    void submit_fallsBackToDefaultOrgWhenNoTenantContext() {
        // No tenant context set — simulate true unauthenticated public path.
        TenantContext.clear();
        when(caseRepo.save(any(WhistleblowerCase.class))).thenAnswer(inv -> {
            WhistleblowerCase c = inv.getArgument(0);
            c.setId(105L);
            return c;
        });
        when(caseRepo.countByOrganisationIdAndIsDeletedFalse(
                WhistleblowerCaseService.PUBLIC_PORTAL_DEFAULT_ORG)).thenReturn(0L);

        service.submitCase(anonReq());

        ArgumentCaptor<WhistleblowerCase> captor = ArgumentCaptor.forClass(WhistleblowerCase.class);
        verify(caseRepo).save(captor.capture());
        assertThat(captor.getValue().getOrganisationId())
                .isEqualTo(WhistleblowerCaseService.PUBLIC_PORTAL_DEFAULT_ORG);
    }

    // ─── Public lookup / reply via token ───────────────────────────

    @Test
    void lookupByToken_returnsOnlyReporterVisibleUpdates() {
        WhistleblowerCase c = existing(201L, "UNDER_INVESTIGATION", false);
        UUID token = c.getSubmissionToken();
        when(caseRepo.findBySubmissionTokenAndIsDeletedFalse(token)).thenReturn(Optional.of(c));

        WhistleblowerCaseUpdate visible = new WhistleblowerCaseUpdate();
        visible.setOrganisationId(ORG_ID);
        visible.setCaseId(201L);
        visible.setUpdateType("TRIAGE_DECISION");
        visible.setIsVisibleToReporter(true);
        visible.setBodyEn("Visible message");
        visible.setBodyAr("رسالة مرئية");
        visible.setActorUserId(42L);

        when(updateRepo.findByCaseIdAndIsVisibleToReporterTrueAndIsDeletedFalseOrderByCreatedAtAsc(201L))
                .thenReturn(List.of(visible));

        PublicCaseResponse out = service.lookupByToken(token);

        assertThat(out.caseCode()).isEqualTo("WB-2026-201");
        assertThat(out.status()).isEqualTo("UNDER_INVESTIGATION");
        assertThat(out.updates()).hasSize(1);
        assertThat(out.updates().get(0).bodyEn()).isEqualTo("Visible message");
        // The query itself filters by is_visible_to_reporter=true; we
        // verify it's the only call.
        verify(updateRepo).findByCaseIdAndIsVisibleToReporterTrueAndIsDeletedFalseOrderByCreatedAtAsc(201L);
        verify(updateRepo, never()).findByOrganisationIdAndCaseIdAndIsDeletedFalseOrderByCreatedAtAsc(any(), any());
    }

    @Test
    void lookupByToken_unknownToken_throws() {
        UUID token = UUID.randomUUID();
        when(caseRepo.findBySubmissionTokenAndIsDeletedFalse(token)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.lookupByToken(token))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
                .hasMessageContaining("Invalid tracking token");
    }

    @Test
    void reportByToken_persistsUpdateWithNullActor() {
        WhistleblowerCase c = existing(202L, "TRIAGE", false);
        UUID token = c.getSubmissionToken();
        when(caseRepo.findBySubmissionTokenAndIsDeletedFalse(token)).thenReturn(Optional.of(c));
        when(updateRepo.findByCaseIdAndIsVisibleToReporterTrueAndIsDeletedFalseOrderByCreatedAtAsc(202L))
                .thenReturn(List.of());

        service.reportByToken(token, new ReporterReplyRequest(
                "Additional detail in English", "تفاصيل إضافية بالعربية"));

        ArgumentCaptor<WhistleblowerCaseUpdate> captor = ArgumentCaptor.forClass(WhistleblowerCaseUpdate.class);
        verify(updateRepo).save(captor.capture());
        WhistleblowerCaseUpdate u = captor.getValue();
        assertThat(u.getActorUserId()).isNull();
        assertThat(u.getUpdateType()).isEqualTo("REPORTER_REPLY");
        assertThat(u.getIsVisibleToReporter()).isTrue();
        assertThat(u.getBodyEn()).isEqualTo("Additional detail in English");

        // Reporter replies are also audited via the anonymous path.
        verify(auditRepo).save(any(AuditLogEntry.class));
        verify(audit, never()).log(any(), any(), any(), any());
    }

    // ─── Triage ────────────────────────────────────────────────────

    @Test
    void triage_movesReceivedToTriageAndRecordsDecision() {
        WhistleblowerCase c = existing(301L, "RECEIVED", false);
        when(caseRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 301L))
                .thenReturn(Optional.of(c));
        when(caseRepo.save(any(WhistleblowerCase.class))).thenAnswer(inv -> inv.getArgument(0));

        service.triage(301L, new TriageRequest(
                "Proceed to investigation. Assign to HR Audit.",
                "يُمضى به إلى التحقيق. إحالته إلى مراجعة الموارد البشرية.",
                "HIGH", 88L, null), 42L);

        ArgumentCaptor<WhistleblowerCase> captor = ArgumentCaptor.forClass(WhistleblowerCase.class);
        verify(caseRepo).save(captor.capture());
        WhistleblowerCase saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("TRIAGE");
        assertThat(saved.getSeverity()).isEqualTo("HIGH");
        assertThat(saved.getAssignedInvestigatorUserId()).isEqualTo(88L);
        assertThat(saved.getTriageByUserId()).isEqualTo(42L);
        assertThat(saved.getTriageAt()).isNotNull();

        verify(updateRepo).save(any(WhistleblowerCaseUpdate.class));

        ArgumentCaptor<DomainEvent> events = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(events.capture());
        assertThat(events.getAllValues()).extracting(DomainEvent::triggerEvent)
                .contains("WHISTLEBLOWER_TRIAGE_DECIDED");
    }

    @Test
    void triage_settingSeniorExecForFirstTime_firesEscalationEvent() {
        WhistleblowerCase c = existing(302L, "RECEIVED", false);
        when(caseRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 302L))
                .thenReturn(Optional.of(c));
        when(caseRepo.save(any(WhistleblowerCase.class))).thenAnswer(inv -> inv.getArgument(0));

        service.triage(302L, new TriageRequest(
                "Senior executive named — escalate to Audit Committee Chair.",
                "تورّط مسؤول تنفيذي — تصعيد لرئيس لجنة المراجعة.",
                "CRITICAL", null, true), 42L);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(captor.capture());
        assertThat(captor.getAllValues()).extracting(DomainEvent::triggerEvent)
                .contains("WHISTLEBLOWER_TRIAGE_DECIDED",
                          "WHISTLEBLOWER_SENIOR_EXEC_FLAGGED");
    }

    // ─── Investigation lifecycle ───────────────────────────────────

    @Test
    void startInvestigation_movesTriageToUnderInvestigation() {
        WhistleblowerCase c = existing(401L, "TRIAGE", false);
        when(caseRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 401L))
                .thenReturn(Optional.of(c));
        when(caseRepo.save(any(WhistleblowerCase.class))).thenAnswer(inv -> inv.getArgument(0));

        service.startInvestigation(401L, 88L, "We will follow up shortly", "سنتواصل قريباً", 42L);

        ArgumentCaptor<WhistleblowerCase> captor = ArgumentCaptor.forClass(WhistleblowerCase.class);
        verify(caseRepo).save(captor.capture());
        WhistleblowerCase saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("UNDER_INVESTIGATION");
        assertThat(saved.getAssignedInvestigatorUserId()).isEqualTo(88L);
        assertThat(saved.getInvestigationStartedAt()).isNotNull();

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("WHISTLEBLOWER_INVESTIGATION_STARTED");
    }

    @Test
    void startInvestigation_fromReceivedIsBlocked() {
        WhistleblowerCase c = existing(402L, "RECEIVED", false);
        when(caseRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 402L))
                .thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.startInvestigation(402L, 88L, null, null, 42L))
                .isInstanceOf(IllegalTransitionException.class);
        verify(caseRepo, never()).save(any());
    }

    @Test
    void investigationUpdate_internalNoteNotVisibleByDefault() {
        WhistleblowerCase c = existing(403L, "UNDER_INVESTIGATION", false);
        when(caseRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 403L))
                .thenReturn(Optional.of(c));
        when(updateRepo.save(any(WhistleblowerCaseUpdate.class))).thenAnswer(inv -> {
            WhistleblowerCaseUpdate u = inv.getArgument(0);
            u.setId(901L);
            return u;
        });

        var resp = service.investigationUpdate(403L, new InvestigationUpdateRequest(
                "Interviewed witness 1. No corroboration of the most serious claim yet.",
                "تمت مقابلة الشاهد الأول. لا توجد بعد قرائن مؤيّدة للأدّعاء الأكثر خطورة.",
                false, "INVESTIGATION_NOTE"), 42L);

        assertThat(resp.isVisibleToReporter()).isFalse();
        assertThat(resp.updateType()).isEqualTo("INVESTIGATION_NOTE");
    }

    @Test
    void investigationUpdate_findingRecordedCanBeVisible() {
        WhistleblowerCase c = existing(404L, "UNDER_INVESTIGATION", false);
        when(caseRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 404L))
                .thenReturn(Optional.of(c));
        when(updateRepo.save(any(WhistleblowerCaseUpdate.class))).thenAnswer(inv -> {
            WhistleblowerCaseUpdate u = inv.getArgument(0);
            u.setId(902L);
            return u;
        });

        var resp = service.investigationUpdate(404L, new InvestigationUpdateRequest(
                "Finding: the allegation regarding the expense duplicates is substantiated.",
                "النتيجة: ثبت ازدواج مطالبات النفقات.",
                true, "FINDING_RECORDED"), 42L);

        assertThat(resp.isVisibleToReporter()).isTrue();
        assertThat(resp.updateType()).isEqualTo("FINDING_RECORDED");
    }

    @Test
    void investigationUpdate_fromReceivedIsBlocked() {
        WhistleblowerCase c = existing(405L, "RECEIVED", false);
        when(caseRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 405L))
                .thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.investigationUpdate(405L,
                new InvestigationUpdateRequest("body", "السرد", false, "INVESTIGATION_NOTE"), 42L))
                .isInstanceOf(IllegalTransitionException.class);
    }

    // ─── Close ─────────────────────────────────────────────────────

    @Test
    void close_substantiatedToClosedRemediated_persistsClosureAndCapaLink() {
        WhistleblowerCase c = existing(501L, "SUBSTANTIATED", false);
        when(caseRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
                .thenReturn(Optional.of(c));
        when(caseRepo.save(any(WhistleblowerCase.class))).thenAnswer(inv -> inv.getArgument(0));

        service.close(501L, new CloseRequest(
                "CLOSED_REMEDIATED",
                "Disciplinary action taken; expense-claim policy updated.",
                "اتُّخذ إجراء تأديبي؛ وحُدّثت سياسة مطالبات النفقات.",
                777L), 42L);

        ArgumentCaptor<WhistleblowerCase> captor = ArgumentCaptor.forClass(WhistleblowerCase.class);
        verify(caseRepo).save(captor.capture());
        WhistleblowerCase saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("CLOSED_REMEDIATED");
        assertThat(saved.getLinkedCapaTaskId()).isEqualTo(777L);
        assertThat(saved.getClosedAt()).isNotNull();

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("WHISTLEBLOWER_CASE_CLOSED");
    }

    @Test
    void close_reopenFromClosedIsRejected() {
        WhistleblowerCase c = existing(502L, "CLOSED_REMEDIATED", false);
        when(caseRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 502L))
                .thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.close(502L, new CloseRequest(
                "UNDER_INVESTIGATION", "Reopen", "إعادة فتح", null), 42L))
                .isInstanceOf(IllegalTransitionException.class);
        verify(caseRepo, never()).save(any());
    }

    @Test
    void close_illegalDirectFromReceivedRejected() {
        WhistleblowerCase c = existing(503L, "RECEIVED", false);
        when(caseRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 503L))
                .thenReturn(Optional.of(c));
        // Cannot jump RECEIVED → CLOSED_REMEDIATED without going through triage/invest.
        assertThatThrownBy(() -> service.close(503L, new CloseRequest(
                "CLOSED_REMEDIATED", "skip", "تخطي", null), 42L))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void close_triageToClosedNoAction_isAllowed() {
        // Out-of-scope claims should close cleanly from TRIAGE.
        WhistleblowerCase c = existing(504L, "TRIAGE", false);
        when(caseRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 504L))
                .thenReturn(Optional.of(c));
        when(caseRepo.save(any(WhistleblowerCase.class))).thenAnswer(inv -> inv.getArgument(0));

        service.close(504L, new CloseRequest(
                "CLOSED_NO_ACTION",
                "Report falls outside the scope of the ethics policy — referred to operational complaints.",
                "خارج نطاق سياسة الأخلاقيات — يُحال إلى قناة الشكاوى التشغيلية.",
                null), 42L);

        ArgumentCaptor<WhistleblowerCase> captor = ArgumentCaptor.forClass(WhistleblowerCase.class);
        verify(caseRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CLOSED_NO_ACTION");
    }

    // ─── CAPA ──────────────────────────────────────────────────────

    @Test
    void escalateToCapa_linksTaskIdWithoutChangingStatus() {
        WhistleblowerCase c = existing(601L, "UNDER_INVESTIGATION", false);
        when(caseRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 601L))
                .thenReturn(Optional.of(c));
        when(caseRepo.save(any(WhistleblowerCase.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = service.escalateToCapa(601L, 999L);

        assertThat(resp.linkedCapaTaskId()).isEqualTo(999L);
        assertThat(resp.status()).isEqualTo("UNDER_INVESTIGATION");
    }

    // ─── Tenant isolation ──────────────────────────────────────────

    @Test
    void get_inDifferentTenant_throws() {
        TenantContext.setCurrentTenant(OTHER_ORG);
        when(caseRepo.findByOrganisationIdAndIdAndIsDeletedFalse(OTHER_ORG, 701L))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(701L))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    @Test
    void list_callsTenantScopedRepository() {
        when(caseRepo.findByOrganisationIdAndIsDeletedFalseOrderBySubmittedAtDesc(ORG_ID))
                .thenReturn(List.of(existing(801L, "RECEIVED", false)));

        var out = service.list();
        assertThat(out).hasSize(1);
        verify(caseRepo).findByOrganisationIdAndIsDeletedFalseOrderBySubmittedAtDesc(ORG_ID);
        verify(caseRepo, never()).findByOrganisationIdAndIsDeletedFalseOrderBySubmittedAtDesc(eq(OTHER_ORG));
    }
}
