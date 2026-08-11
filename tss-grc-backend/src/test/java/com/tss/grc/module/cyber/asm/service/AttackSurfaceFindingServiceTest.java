package com.tss.grc.module.cyber.asm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.FindingRequest;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.FindingResponse;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.FindingTransitionRequest;
import com.tss.grc.module.cyber.asm.entity.AttackSurfaceAsset;
import com.tss.grc.module.cyber.asm.entity.AttackSurfaceFinding;
import com.tss.grc.module.cyber.asm.repository.AttackSurfaceAssetRepository;
import com.tss.grc.module.cyber.asm.repository.AttackSurfaceFindingRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AttackSurfaceFindingService} — Sprint 11 ASM.
 *
 * <p>Locks: record, lifecycle transitions, the closing-status stamp on
 * {@code closed_at}, capa linkage idempotency, and F8 event emission.
 */
@ExtendWith(MockitoExtension.class)
class AttackSurfaceFindingServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long ASSET_ID = 42L;
    private static final UUID ASSET_EX = UUID.randomUUID();
    private static final Long FINDING_ID = 100L;
    private static final UUID FINDING_EX = UUID.randomUUID();

    @Mock private AttackSurfaceFindingRepository findingRepo;
    @Mock private AttackSurfaceAssetRepository assetRepo;
    @Mock private AttackSurfaceAssetService assetService;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AttackSurfaceFindingService service;

    @BeforeEach
    void setUp() {
        service = new AttackSurfaceFindingService(findingRepo, assetRepo, assetService, audit, eventPublisher);
        TenantContext.setCurrentTenant(ORG_ID);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ─────────────────────────────────────────────────

    private static AttackSurfaceAsset asset() {
        AttackSurfaceAsset a = new AttackSurfaceAsset();
        a.setId(ASSET_ID);
        a.setExternalId(ASSET_EX);
        a.setOrganisationId(ORG_ID);
        a.setAssetCode("ASM-2026-001");
        a.setTitleEn("Banking portal");
        a.setAssetType("WEB_APPLICATION");
        a.setExposure("PUBLIC");
        a.setCriticality("CRITICAL");
        a.setLifecycleStage("APPROVED");
        a.setAssetOwnerUserId(1L);
        return a;
    }

    private static AttackSurfaceFinding finding(String status, String severity) {
        AttackSurfaceFinding f = new AttackSurfaceFinding();
        f.setId(FINDING_ID);
        f.setExternalId(FINDING_EX);
        f.setOrganisationId(ORG_ID);
        f.setAssetId(ASSET_ID);
        f.setFindingType("WEAK_CIPHER");
        f.setSeverity(severity);
        f.setSource("TENABLE");
        f.setStatus(status);
        f.setDescriptionEn("TLS 1.2 with CBC suite available");
        return f;
    }

    @SuppressWarnings("unchecked")
    private static <T> org.mockito.stubbing.Answer<T> assignId(AtomicLong seq) {
        return inv -> {
            Object arg = inv.getArgument(0);
            try {
                var getId = arg.getClass().getMethod("getId");
                if (getId.invoke(arg) == null) {
                    var setId = arg.getClass().getMethod("setId", Long.class);
                    setId.invoke(arg, seq.incrementAndGet());
                }
            } catch (Exception ignored) {}
            return (T) arg;
        };
    }

    // ─── Record ───────────────────────────────────────────────────

    @Test
    void recordFinding_persistsAndEmitsEvent() {
        when(assetService.mustGet(ASSET_EX)).thenReturn(asset());
        AtomicLong seq = new AtomicLong(900);
        when(findingRepo.save(any(AttackSurfaceFinding.class))).thenAnswer(assignId(seq));

        FindingRequest req = new FindingRequest(
                ASSET_EX, "WEAK_CIPHER", "HIGH", "TENABLE", "plugin-26928",
                "TLS 1.2 with CBC suite available", null, null, null);
        FindingResponse out = service.recordFinding(req);

        assertThat(out.status()).isEqualTo("OPEN");
        assertThat(out.severity()).isEqualTo("HIGH");
        assertThat(out.assetCode()).isEqualTo("ASM-2026-001");

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("ASM_FINDING_RECORDED");
        verify(audit).log(eq("FINDING_RECORDED"), eq("AttackSurfaceFinding"), anyLong(), anyString());
        verify(assetService, atLeastOnce()).recomputeRiskScore(ASSET_ID);
    }

    // ─── Transitions ──────────────────────────────────────────────

    @Test
    void transition_openToInvestigating_ok() {
        when(findingRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, FINDING_EX))
                .thenReturn(Optional.of(finding("OPEN", "HIGH")));
        when(findingRepo.save(any(AttackSurfaceFinding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assetRepo.findById(ASSET_ID)).thenReturn(Optional.of(asset()));

        FindingResponse out = service.transition(FINDING_EX, new FindingTransitionRequest("INVESTIGATING", "triaging"));
        assertThat(out.status()).isEqualTo("INVESTIGATING");
        // No closure stamp yet.
        assertThat(out.closedAt()).isNull();

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("ASM_FINDING_RECORDED");
    }

    @Test
    void transition_investigatingToFixed_stampsClosedAtAndEmitsClosedEvent() {
        when(findingRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, FINDING_EX))
                .thenReturn(Optional.of(finding("INVESTIGATING", "HIGH")));
        when(findingRepo.save(any(AttackSurfaceFinding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assetRepo.findById(ASSET_ID)).thenReturn(Optional.of(asset()));

        FindingResponse out = service.transition(FINDING_EX, new FindingTransitionRequest("FIXED", "cipher suite restricted"));
        assertThat(out.status()).isEqualTo("FIXED");
        assertThat(out.closedAt()).isNotNull();

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("ASM_FINDING_CLOSED");
        verify(assetService, atLeastOnce()).recomputeRiskScore(ASSET_ID);
    }

    @Test
    void transition_openToFalsePositive_terminalAndClosesOnly() {
        when(findingRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, FINDING_EX))
                .thenReturn(Optional.of(finding("OPEN", "MEDIUM")));
        when(findingRepo.save(any(AttackSurfaceFinding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assetRepo.findById(ASSET_ID)).thenReturn(Optional.of(asset()));

        FindingResponse out = service.transition(FINDING_EX, new FindingTransitionRequest("FALSE_POSITIVE", null));
        assertThat(out.status()).isEqualTo("FALSE_POSITIVE");
        assertThat(out.closedAt()).isNotNull();
    }

    @Test
    void transition_openToAcceptedRisk_emitsClosedEvent() {
        when(findingRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, FINDING_EX))
                .thenReturn(Optional.of(finding("OPEN", "MEDIUM")));
        when(findingRepo.save(any(AttackSurfaceFinding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assetRepo.findById(ASSET_ID)).thenReturn(Optional.of(asset()));

        FindingResponse out = service.transition(FINDING_EX, new FindingTransitionRequest("ACCEPTED_RISK", "risk accepted by CISO"));
        assertThat(out.status()).isEqualTo("ACCEPTED_RISK");
        assertThat(out.closedAt()).isNotNull();
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("ASM_FINDING_CLOSED");
    }

    @Test
    void transition_falsePositiveToOpen_rejected() {
        when(findingRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, FINDING_EX))
                .thenReturn(Optional.of(finding("FALSE_POSITIVE", "MEDIUM")));

        assertThatThrownBy(() -> service.transition(FINDING_EX, new FindingTransitionRequest("OPEN", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FALSE_POSITIVE → OPEN");
    }

    // ─── CAPA linkage ─────────────────────────────────────────────

    @Test
    void linkCapaTask_setsIdAndIsIdempotent() {
        AttackSurfaceFinding f = finding("OPEN", "HIGH");
        when(findingRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, FINDING_EX))
                .thenReturn(Optional.of(f));
        when(findingRepo.save(any(AttackSurfaceFinding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assetRepo.findById(ASSET_ID)).thenReturn(Optional.of(asset()));

        FindingResponse first = service.linkCapaTask(FINDING_EX, 999L);
        assertThat(first.capaTaskId()).isEqualTo(999L);

        // Calling again with the same id is a no-op.
        FindingResponse second = service.linkCapaTask(FINDING_EX, 999L);
        assertThat(second.capaTaskId()).isEqualTo(999L);

        verify(audit, atLeastOnce()).log(eq("FINDING_CAPA_LINKED"), eq("AttackSurfaceFinding"), anyLong(), anyString());
    }

    @Test
    void linkCapaTask_refusesOverwrite() {
        AttackSurfaceFinding f = finding("OPEN", "HIGH");
        f.setCapaTaskId(123L);
        when(findingRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, FINDING_EX))
                .thenReturn(Optional.of(f));

        assertThatThrownBy(() -> service.linkCapaTask(FINDING_EX, 456L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already linked to CAPA");
    }

    // ─── F2-182: severity, the open writer with the worst arithmetic behind it ──────────

    /**
     * ⚠ <b>Read this test first — it states the consequence in numbers rather than in prose.</b>
     *
     * <p>Nothing in this assertion is mocked or contrived: it reads the real weight map the real
     * asset-risk recompute uses, and shows that the case a caller could actually store scored the
     * parent asset at the platform's <em>lowest</em> band. An internet-facing asset with an open
     * critical finding read as 15/100. That is not a display bug and not an undercount in a KPI —
     * it is the risk number going quiet exactly when it should be loudest.
     */
    @Test
    void theArithmeticThatMadeThisWorthFixing() {
        var weights = AttackSurfaceAssetService.SEVERITY_WEIGHT;
        int low = weights.get("LOW");

        assertThat(weights.getOrDefault("critical", low)).as(
            "what a lower-case CRITICAL scored before the writer was closed — getOrDefault falls to "
            + "LOW, and pickHighestOpenSeverity skips the row for the same reason")
            .isEqualTo(low)
            .isEqualTo(15);
        assertThat(weights.getOrDefault("CRITICAL", low)).as("what it should score").isEqualTo(100);
    }

    @Test
    void recordFinding_normalisesTheSeverityCasingTheColumnCannotDistinguish() {
        when(assetService.mustGet(ASSET_EX)).thenReturn(asset());
        when(findingRepo.save(any(AttackSurfaceFinding.class))).thenAnswer(assignId(new AtomicLong(900)));

        FindingResponse out = service.recordFinding(new FindingRequest(
                ASSET_EX, "WEAK_CIPHER", "  critical  ", "TENABLE", "plugin-26928",
                "TLS 1.2 with CBC suite available", null, null, null));

        ArgumentCaptor<AttackSurfaceFinding> captor = ArgumentCaptor.forClass(AttackSurfaceFinding.class);
        verify(findingRepo).save(captor.capture());
        assertThat(captor.getValue().getSeverity()).as(
            "the STORED value, not just the response — leaving 'critical' in the column is what every "
            + "downstream case-sensitive reader trips over, and equalsIgnoreCase at each reader would "
            + "have left it there")
            .isEqualTo("CRITICAL");
        assertThat(out.severity()).isEqualTo("CRITICAL");
    }

    @Test
    void recordFinding_refusesASeverityTheColumnCouldNeverHold() {
        when(assetService.mustGet(ASSET_EX)).thenReturn(asset());

        assertThatThrownBy(() -> service.recordFinding(new FindingRequest(
                ASSET_EX, "WEAK_CIPHER", "SEV1", "TENABLE", null, "desc", null, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("severity")
            .hasMessageContaining("SEV1");

        verify(findingRepo, never()).save(any());
    }

    /**
     * ⚙ Why {@code require} and not {@code coerce} here, pinned so the reasoning survives the commit.
     *
     * <p>F2-179 established that the choice is per-field. The decisive fact for this column is that
     * {@code CK_asm_finding_severity} already refuses everything outside the vocabulary — so
     * <b>every value that succeeds today is a case-variant of a legal token</b>, and normalising
     * cannot break a request that currently works. It only converts a CHECK violation (which reached
     * the caller as a 409 about a reference that does not exist, before F2-180 fixed the message) into
     * a 400 naming the field. Coercing instead would have to pick a band, and the only defensible
     * fallback for an unknown security severity is the one that caused the defect.
     */
    @Test
    void everyVocabularyTokenIsWeightedAndOrdered() {
        assertThat(AttackSurfaceFindingService.SEVERITY.tokens()).as(
            "anti-vacuity before the comparison below")
            .hasSize(5);
        assertThat(AttackSurfaceAssetService.SEVERITY_WEIGHT.keySet()).as(
            "a token the scorer has no weight for silently becomes LOW; the two artefacts must agree "
            + "token for token, which is only checkable because the vocabulary is a real object")
            .containsExactlyInAnyOrderElementsOf(AttackSurfaceFindingService.SEVERITY.tokens());
    }
}
