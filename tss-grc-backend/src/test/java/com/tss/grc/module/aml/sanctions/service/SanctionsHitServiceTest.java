package com.tss.grc.module.aml.sanctions.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.HitEscalateRequest;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.HitResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.HitReviewRequest;
import com.tss.grc.module.aml.sanctions.entity.ScreeningCheck;
import com.tss.grc.module.aml.sanctions.entity.ScreeningHit;
import com.tss.grc.module.aml.sanctions.repository.SanctionsListEntryRepository;
import com.tss.grc.module.aml.sanctions.repository.SanctionsListRepository;
import com.tss.grc.module.aml.sanctions.repository.ScreeningCheckRepository;
import com.tss.grc.module.aml.sanctions.repository.ScreeningHitRepository;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 13 — {@link SanctionsHitService} unit tests.
 *
 * <p>Covers:
 * <ul>
 *   <li>review transitions (PENDING → TRUE/FALSE positive, ESCALATED)</li>
 *   <li>illegal transitions rejected</li>
 *   <li>escalate fires SANCTIONS_HIT_ESCALATED</li>
 *   <li>review with TRUE_POSITIVE promotes parent check to CONFIRMED_HIT</li>
 *   <li>CAPA task link captured on review</li>
 *   <li>SLA breach detection — mock clock, hit older than 24h → sla_breach=1
 *       + SANCTIONS_HIT_SLA_BREACHED event</li>
 *   <li>SLA breach detection — hit younger than 24h → no breach</li>
 *   <li>F8 events emitted on close (SANCTIONS_HIT_CLOSED)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SanctionsHitServiceTest {

    private static final Long ORG_ID = 7L;
    private static final Long HIT_ID = 900L;
    private static final Long CHECK_ID = 500L;

    @Mock private ScreeningHitRepository hitRepo;
    @Mock private ScreeningCheckRepository checkRepo;
    @Mock private SanctionsListEntryRepository entryRepo;
    @Mock private SanctionsListRepository listRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private SanctionsHitService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        service = new SanctionsHitService(hitRepo, checkRepo, entryRepo, listRepo, audit, publisher);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    private static ScreeningHit hit(String status) {
        ScreeningHit h = new ScreeningHit();
        h.setId(HIT_ID);
        h.setOrganisationId(ORG_ID);
        h.setCheckId(CHECK_ID);
        h.setListEntryId(11L);
        h.setMatchScore(BigDecimal.valueOf(92.00));
        h.setMatchType("FUZZY_NAME");
        h.setStatus(status);
        h.setSlaBreach(false);
        h.setCreatedAt(LocalDateTime.of(2026, 5, 24, 10, 0));
        return h;
    }

    private static ScreeningCheck parentCheck() {
        ScreeningCheck c = new ScreeningCheck();
        c.setId(CHECK_ID);
        c.setOrganisationId(ORG_ID);
        c.setCheckCode("SCR-2026-000006");
        c.setSubjectNameEn("Ali Hassan Mahmoud");
        c.setResult("POTENTIAL_HIT");
        return c;
    }

    // ─── reviewHit ───────────────────────────────────────────────────

    @Test void reviewHit_pendingToFalsePositive_succeeds() {
        when(hitRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, HIT_ID))
            .thenReturn(Optional.of(hit("PENDING_REVIEW")));
        when(hitRepo.save(any(ScreeningHit.class))).thenAnswer(inv -> inv.getArgument(0));

        f223AuthAs("aml.officer", 42L);
        HitResponse out = service.reviewHit(HIT_ID,
            new HitReviewRequest("FALSE_POSITIVE",
                "Common Arab name — different DOB, different country, different ID. Not a match.",
                "اسم عربي شائع — تاريخ ميلاد وبلد ووثيقة هوية مختلفة. ليس تطابقاً.",
                null));
        assertThat(out.status()).isEqualTo("FALSE_POSITIVE");
        // F2-223 — the reviewer is the authenticated principal, not a body field.
        assertThat(out.reviewerUserId()).isEqualTo(42L);
        assertThat(out.dispositionEn()).containsIgnoringCase("not a match");

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("SANCTIONS_HIT_REVIEWED");
    }

    @Test void reviewHit_pendingToTruePositive_promotesParentToConfirmed() {
        when(hitRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, HIT_ID))
            .thenReturn(Optional.of(hit("PENDING_REVIEW")));
        when(hitRepo.save(any(ScreeningHit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(checkRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, CHECK_ID))
            .thenReturn(Optional.of(parentCheck()));
        when(checkRepo.save(any(ScreeningCheck.class))).thenAnswer(inv -> inv.getArgument(0));

        HitResponse out = service.reviewHit(HIT_ID,
            new HitReviewRequest("TRUE_POSITIVE", "confirmed", "مؤكَّد", null));
        assertThat(out.status()).isEqualTo("TRUE_POSITIVE");

        ArgumentCaptor<ScreeningCheck> chk = ArgumentCaptor.forClass(ScreeningCheck.class);
        verify(checkRepo).save(chk.capture());
        assertThat(chk.getValue().getResult()).isEqualTo("CONFIRMED_HIT");
    }

    @Test void reviewHit_capaTaskLink_captured() {
        when(hitRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, HIT_ID))
            .thenReturn(Optional.of(hit("PENDING_REVIEW")));
        when(hitRepo.save(any(ScreeningHit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(checkRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, CHECK_ID))
            .thenReturn(Optional.of(parentCheck()));
        when(checkRepo.save(any(ScreeningCheck.class))).thenAnswer(inv -> inv.getArgument(0));

        HitResponse out = service.reviewHit(HIT_ID,
            new HitReviewRequest("TRUE_POSITIVE", "x", "س", 12345L));
        assertThat(out.capaTaskId()).isEqualTo(12345L);
    }

    @Test void reviewHit_illegalTransitionRejected() {
        when(hitRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, HIT_ID))
            .thenReturn(Optional.of(hit("CLOSED")));

        assertThatThrownBy(() -> service.reviewHit(HIT_ID,
            new HitReviewRequest("PENDING_REVIEW", null, null, null)))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void reviewHit_closedTransitionEmitsClosedEvent() {
        when(hitRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, HIT_ID))
            .thenReturn(Optional.of(hit("FALSE_POSITIVE")));
        when(hitRepo.save(any(ScreeningHit.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reviewHit(HIT_ID,
            new HitReviewRequest("CLOSED", "case closed", "أُغلقت القضية", null));

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues()).extracting(DomainEvent::triggerEvent)
            .contains("SANCTIONS_HIT_CLOSED");
    }

    // ─── escalate ────────────────────────────────────────────────────

    @Test void escalate_pendingToEscalated_succeeds() {
        when(hitRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, HIT_ID))
            .thenReturn(Optional.of(hit("PENDING_REVIEW")));
        when(hitRepo.save(any(ScreeningHit.class))).thenAnswer(inv -> inv.getArgument(0));

        HitResponse out = service.escalate(HIT_ID,
            new HitEscalateRequest(99L, "Strong match: composite name + DOB", "تطابق قوي"));
        assertThat(out.status()).isEqualTo("ESCALATED");
        assertThat(out.escalatedToUserId()).isEqualTo(99L);
        assertThat(out.escalatedAt()).isNotNull();

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("SANCTIONS_HIT_ESCALATED");
    }

    @Test void escalate_fromClosed_rejected() {
        when(hitRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, HIT_ID))
            .thenReturn(Optional.of(hit("CLOSED")));
        assertThatThrownBy(() -> service.escalate(HIT_ID,
            new HitEscalateRequest(99L, "x", "س")))
            .isInstanceOf(IllegalTransitionException.class);
    }

    // ─── SLA breach detection ────────────────────────────────────────

    @Test void detectSlaBreaches_marksOverdueHit() {
        // Fixed clock at 2026-05-25 12:00 UTC — hit created on 2026-05-24 10:00 is 26 h old
        Clock fixed = Clock.fixed(
            Instant.parse("2026-05-25T12:00:00Z"), ZoneId.of("UTC"));
        service.setClock(fixed);

        ScreeningHit overdue = hit("PENDING_REVIEW"); // createdAt = 2026-05-24 10:00
        when(hitRepo.findByOrganisationIdAndStatusAndSlaBreachFalseAndCreatedAtBeforeAndIsDeletedFalse(
            org.mockito.ArgumentMatchers.eq(ORG_ID),
            org.mockito.ArgumentMatchers.eq("PENDING_REVIEW"),
            any()))
            .thenReturn(List.of(overdue));
        when(hitRepo.save(any(ScreeningHit.class))).thenAnswer(inv -> inv.getArgument(0));

        int n = service.detectSlaBreachesForOrg(ORG_ID);
        assertThat(n).isEqualTo(1);

        ArgumentCaptor<ScreeningHit> capt = ArgumentCaptor.forClass(ScreeningHit.class);
        verify(hitRepo).save(capt.capture());
        assertThat(capt.getValue().getSlaBreach()).isTrue();

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("SANCTIONS_HIT_SLA_BREACHED");
    }

    @Test void detectSlaBreaches_noOverdueHits_returnsZero() {
        Clock fixed = Clock.fixed(
            Instant.parse("2026-05-25T12:00:00Z"), ZoneId.of("UTC"));
        service.setClock(fixed);
        when(hitRepo.findByOrganisationIdAndStatusAndSlaBreachFalseAndCreatedAtBeforeAndIsDeletedFalse(
            org.mockito.ArgumentMatchers.eq(ORG_ID),
            org.mockito.ArgumentMatchers.eq("PENDING_REVIEW"),
            any()))
            .thenReturn(List.of());
        int n = service.detectSlaBreachesForOrg(ORG_ID);
        assertThat(n).isZero();
    }

    @Test void listByStatus_filtersAndMaterialises() {
        when(hitRepo.findByOrganisationIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(
            ORG_ID, "PENDING_REVIEW"))
            .thenReturn(List.of(hit("PENDING_REVIEW")));
        when(entryRepo.findById(anyLong())).thenReturn(Optional.empty());
        List<HitResponse> out = service.listByStatus("PENDING_REVIEW");
        assertThat(out).hasSize(1);
        assertThat(out.get(0).status()).isEqualTo("PENDING_REVIEW");
    }

    /**
     * F2-223 — a real principal in the SecurityContext, because the service now takes the actor
     * from {@code CurrentActor} rather than from the request. {@code CurrentActor} requires the
     * principal to be a {@link com.tss.grc.security.GrcUserDetails}, so a bare username token
     * would resolve to null and the assertion would be about nothing.
     */
    private static void f223AuthAs(String username, Long userId) {
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .setAuthentication(new org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken(
                    new com.tss.grc.security.GrcUserDetails(
                        userId, 1L, username, "x", true, true, java.util.List.of()),
                    "x", java.util.List.of()));
    }
}
