package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmBiaAssessment;
import com.tss.grc.module.bcm.cycle.repository.BcmBiaAssessmentRepository;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.when;

/**
 * BIA Assessment lock — audit Tier-1 #3.
 *
 * <ul>
 *   <li>create requires service_id OR process_id</li>
 *   <li>RTO ≤ MTPD blocks approval</li>
 *   <li>approving without MTPD blocked</li>
 *   <li>cannot edit APPROVED BIA</li>
 *   <li>criticality calculator picks the right bucket</li>
 *   <li>national_sovereign ≥ 4 forces SOVEREIGN</li>
 *   <li>APPROVE emits BIA_APPROVED + (when applicable) BIA_CRITICALITY_SOVEREIGN</li>
 *   <li>illegal transitions rejected (incl. terminal ARCHIVED)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BcmBiaAssessmentServiceTest {

    private static final Long ORG_ID = 41L;

    @Mock private BcmBiaAssessmentRepository repo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private BcmBiaAssessmentService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(repo.save(any(BcmBiaAssessment.class))).thenAnswer(inv -> {
            BcmBiaAssessment b = inv.getArgument(0);
            if (b.getId() == null) b.setId(123L);
            return b;
        });
        when(repo.findByOrganisationIdOrderByIdDesc(eq(ORG_ID), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of()));
    }
    @AfterEach void tearDown() { TenantContext.clear(); }

    private static BcmBiaAssessment bia(String status) {
        BcmBiaAssessment b = new BcmBiaAssessment();
        b.setId(1L); b.setOrganisationId(ORG_ID);
        b.setBiaCode("BIA-2026-000001");
        b.setServiceId(99L);
        b.setApprovalStatus(status);
        return b;
    }

    // ─── Scope validation ──────────────────────────────────────

    @Test void create_requiresServiceOrProcess() {
        BcmBiaAssessment b = new BcmBiaAssessment();
        // no serviceId, no processId
        assertThatThrownBy(() -> service.create(b))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("serviceId or processId");
    }

    @Test void create_acceptsServiceOnly() {
        BcmBiaAssessment b = new BcmBiaAssessment();
        b.setServiceId(7L);
        BcmBiaAssessment saved = service.create(b);
        assertThat(saved.getBiaCode()).startsWith("BIA-");
    }

    // ─── RTO ≤ MTPD ─────────────────────────────────────────────

    @Test void create_rtoGreaterThanMtpd_rejected() {
        BcmBiaAssessment b = bia("DRAFT"); b.setId(null);
        b.setMtpdHours(24); b.setRtoHours(48);
        assertThatThrownBy(() -> service.create(b))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("rtoHours");
    }

    @Test void create_negativeHours_rejected() {
        BcmBiaAssessment b = bia("DRAFT"); b.setId(null);
        b.setMtpdHours(-1);
        assertThatThrownBy(() -> service.create(b))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── Approval gate ─────────────────────────────────────────

    @Test void transition_APPROVED_requiresMtpd() {
        BcmBiaAssessment b = bia("UNDER_REVIEW");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));
        assertThatThrownBy(() -> service.transition(1L, "APPROVED"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mtpdHours");
    }

    @Test void transition_APPROVED_blocksWhenRtoExceedsMtpd() {
        BcmBiaAssessment b = bia("UNDER_REVIEW");
        b.setMtpdHours(8); b.setRtoHours(24);
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));
        assertThatThrownBy(() -> service.transition(1L, "APPROVED"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("rtoHours");
    }

    @Test void transition_APPROVED_succeedsAndEmitsEvent() {
        BcmBiaAssessment b = bia("UNDER_REVIEW");
        b.setMtpdHours(24); b.setRtoHours(8);
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));

        BcmBiaAssessment out = service.transition(1L, "APPROVED");
        assertThat(out.getApprovalStatus()).isEqualTo("APPROVED");
        assertThat(out.getApprovedAt()).isNotNull();

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify_eventPublisher(cap);
        assertThat(cap.getAllValues()).extracting(DomainEvent::triggerEvent).contains("BIA_APPROVED");
    }

    // ─── Edit lock ────────────────────────────────────────────

    @Test void update_cannotEditApprovedBia() {
        BcmBiaAssessment b = bia("APPROVED");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));

        BcmBiaAssessment patch = new BcmBiaAssessment();
        patch.setDescription("change");
        assertThatThrownBy(() -> service.update(1L, patch))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("APPROVED");
    }

    @Test void update_cannotChangeStatus() {
        BcmBiaAssessment b = bia("DRAFT");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));

        BcmBiaAssessment patch = new BcmBiaAssessment();
        patch.setApprovalStatus("APPROVED");
        assertThatThrownBy(() -> service.update(1L, patch))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("transition");
    }

    // ─── Criticality calculator ───────────────────────────────

    @Test void calculateCriticality_maxOfThree_yieldsCRITICAL() {
        BcmBiaAssessment b = bia("DRAFT");
        b.setImpactTimeBandsJson("""
            {"0-2h":{"regulatory":1,"financial":2},
             "2-4h":{"regulatory":3,"financial":4,"reputation":2},
             "4-8h":{"regulatory":5,"financial":4}}
            """);
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));

        BcmBiaAssessment out = service.calculateCriticality(1L);
        assertThat(out.getOverallCriticality()).isEqualTo("CRITICAL");
        assertThat(out.getImpactDimensionsJson()).contains("\"regulatory\":5");
        assertThat(out.getImpactDimensionsJson()).contains("\"financial\":4");
    }

    @Test void calculateCriticality_nationalSovereign4_forcesSOVEREIGN() {
        BcmBiaAssessment b = bia("DRAFT");
        b.setImpactTimeBandsJson("""
            {"0-2h":{"national_sovereign":4,"financial":2}}
            """);
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));

        BcmBiaAssessment out = service.calculateCriticality(1L);
        assertThat(out.getOverallCriticality()).isEqualTo("SOVEREIGN");
    }

    @Test void calculateCriticality_lowScores_yieldLOW() {
        BcmBiaAssessment b = bia("DRAFT");
        b.setImpactTimeBandsJson("""
            {"0-2h":{"financial":1,"operational":2}}
            """);
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));

        BcmBiaAssessment out = service.calculateCriticality(1L);
        assertThat(out.getOverallCriticality()).isEqualTo("LOW");
    }

    @Test void calculateCriticality_malformedJson_throws() {
        BcmBiaAssessment b = bia("DRAFT");
        b.setImpactTimeBandsJson("not json");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.calculateCriticality(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("malformed");
    }

    @Test void approve_SOVEREIGN_emitsBothEvents() {
        BcmBiaAssessment b = bia("UNDER_REVIEW");
        b.setMtpdHours(24); b.setRtoHours(8);
        b.setImpactTimeBandsJson("{\"0-2h\":{\"national_sovereign\":5}}");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));

        service.transition(1L, "APPROVED");

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify_eventPublisher(cap);
        assertThat(cap.getAllValues()).extracting(DomainEvent::triggerEvent)
            .contains("BIA_APPROVED", "BIA_CRITICALITY_SOVEREIGN");
    }

    // ─── State machine ─────────────────────────────────────────

    @Test void transition_ARCHIVEDfromARCHIVED_rejected() {
        BcmBiaAssessment b = bia("ARCHIVED");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));
        assertThatThrownBy(() -> service.transition(1L, "DRAFT"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Illegal");
    }

    @Test void transition_legalApprovalLoop_CHANGES_REQUIRED_to_DRAFT() {
        BcmBiaAssessment b = bia("CHANGES_REQUIRED");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));
        BcmBiaAssessment out = service.transition(1L, "DRAFT");
        assertThat(out.getApprovalStatus()).isEqualTo("DRAFT");
    }

    private void verify_eventPublisher(ArgumentCaptor<DomainEvent> cap) {
        org.mockito.Mockito.verify(eventPublisher, atLeastOnce()).publishEvent(cap.capture());
    }
}
