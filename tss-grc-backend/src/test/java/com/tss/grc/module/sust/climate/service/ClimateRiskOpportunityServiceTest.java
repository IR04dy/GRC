package com.tss.grc.module.sust.climate.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.risk.dto.RiskLinkDtos;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskLink;
import com.tss.grc.module.risk.repository.RiskLinkRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import com.tss.grc.module.risk.service.RiskLinkService;
import com.tss.grc.module.sust.climate.entity.ClimateRiskOpportunity;
import com.tss.grc.module.sust.climate.repository.ClimateRiskOpportunityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for the partial-update "default clobber" on the Climate Risk
 * &amp; Opportunity register, plus the kind-immutability hardening.
 *
 * <p>{@code PUT /api/sustainability/climate-risks/{id}} binds the JPA entity
 * directly. The original defect: {@code kind} carried a field initialiser
 * ({@code = "RISK"}), so a PUT body that OMITTED kind deserialised it to
 * "RISK" (not null), {@code update()}'s {@code if (req.getKind() != null)}
 * guard fired, and editing an OPPORTUNITY row flipped it to RISK — then
 * {@code validateAxes} rejected the (valid-for-OPPORTUNITY) category with a
 * 400 "Category X not valid for kind RISK".
 *
 * <p>Two layers now prevent this: the "RISK" insert default lives in
 * {@code @PrePersist} (so an omitted field stays null), and {@code update()}
 * treats {@code kind} as immutable post-create (so it can never be flipped by
 * a PUT, even an explicit/mismatched one).
 */
@ExtendWith(MockitoExtension.class)
class ClimateRiskOpportunityServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long ID = 5L;

    @Mock private ClimateRiskOpportunityRepository repo;
    @Mock private AuditLogService audit;
    @Mock private RiskRepository riskRepo;
    @Mock private RiskLinkService riskLinkService;
    @Mock private RiskLinkRepository riskLinks;
    @InjectMocks private ClimateRiskOpportunityService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    /** A persisted OPPORTUNITY row — id 5 in the original repro. */
    private ClimateRiskOpportunity persistedOpportunity() {
        ClimateRiskOpportunity cur = new ClimateRiskOpportunity();
        cur.setId(ID);
        cur.setOrganisationId(ORG_ID);
        cur.setKind("OPPORTUNITY");
        cur.setCategory("RESOURCE_EFFICIENCY");
        cur.setTitleEn("Solar-powered branch network");
        cur.setTimeHorizon("MEDIUM");
        cur.setLikelihood((short) 3);
        cur.setImpact((short) 3);
        cur.setStatus("IDENTIFIED");
        return cur;
    }

    /** The exact repro body: edits an OPPORTUNITY and OMITS kind. */
    @Test
    void update_omittingKind_doesNotClobberToRisk() {
        when(repo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.of(persistedOpportunity()));
        when(repo.save(any(ClimateRiskOpportunity.class))).thenAnswer(inv -> inv.getArgument(0));

        ClimateRiskOpportunity req = new ClimateRiskOpportunity();
        req.setCategory("RESOURCE_EFFICIENCY");
        req.setTimeHorizon("MEDIUM");
        req.setTitleEn("Solar-powered branch network (phase 2)");
        req.setLikelihood((short) 4);
        req.setImpact((short) 5);
        // kind intentionally omitted — must stay OPPORTUNITY

        ClimateRiskOpportunity saved = service.update(ID, req);

        assertThat(saved.getKind()).isEqualTo("OPPORTUNITY");           // NOT flipped to RISK
        assertThat(saved.getCategory()).isEqualTo("RESOURCE_EFFICIENCY");
        assertThat(saved.getRiskScore()).isEqualTo((short) 20);         // 4 × 5 recomputed
    }

    /** kind is immutable post-create: an explicit, mismatched kind is ignored. */
    @Test
    void update_withMismatchedKind_isIgnored_andDoesNotThrow() {
        when(repo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.of(persistedOpportunity()));
        when(repo.save(any(ClimateRiskOpportunity.class))).thenAnswer(inv -> inv.getArgument(0));

        ClimateRiskOpportunity req = new ClimateRiskOpportunity();
        req.setKind("RISK");                      // attempt to flip — must be ignored
        req.setCategory("RESOURCE_EFFICIENCY");   // only valid while kind stays OPPORTUNITY
        req.setLikelihood((short) 2);
        req.setImpact((short) 2);

        assertThatCode(() -> {
            ClimateRiskOpportunity saved = service.update(ID, req);
            assertThat(saved.getKind()).isEqualTo("OPPORTUNITY");
        }).doesNotThrowAnyException();
    }

    /** Ordinary editable fields still apply on a partial update. */
    @Test
    void update_appliesEditableFields() {
        when(repo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.of(persistedOpportunity()));
        when(repo.save(any(ClimateRiskOpportunity.class))).thenAnswer(inv -> inv.getArgument(0));

        ClimateRiskOpportunity req = new ClimateRiskOpportunity();
        req.setTitleEn("Revised opportunity title");
        req.setLikelihood((short) 5);
        req.setImpact((short) 4);

        ClimateRiskOpportunity saved = service.update(ID, req);

        assertThat(saved.getTitleEn()).isEqualTo("Revised opportunity title");
        assertThat(saved.getRiskScore()).isEqualTo((short) 20);
        assertThat(saved.getKind()).isEqualTo("OPPORTUNITY");
    }

    // ── S-F — validated, bidirectional ERM link ─────────────────────

    private ClimateRiskOpportunity persistedRisk() {
        ClimateRiskOpportunity r = new ClimateRiskOpportunity();
        r.setId(ID);
        r.setOrganisationId(ORG_ID);
        r.setKind("RISK");
        r.setCategory("TRANSITION");
        r.setItemCode("CR-2026-007");
        r.setTitleEn("Carbon price shock on cement portfolio");
        r.setTimeHorizon("MEDIUM");
        r.setLikelihood((short) 4);
        r.setImpact((short) 5);
        r.setStatus("ASSESSED");
        return r;
    }

    @Test
    void linkErm_rejectsUnknownErmRisk() {
        when(repo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.of(persistedRisk()));
        when(riskRepo.findById(999L)).thenReturn(Optional.empty());   // no such ERM risk

        assertThatThrownBy(() -> service.linkErm(ID, Map.of("ermRiskId", 999)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not exist");
        verify(riskLinkService, never()).create(anyLong(), any());
    }

    @Test
    void linkErm_rejectsCrossTenantErmRisk() {
        when(repo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.of(persistedRisk()));
        Risk other = new Risk();
        other.setOrganisationId(999L);                                // different tenant
        when(riskRepo.findById(42L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.linkErm(ID, Map.of("ermRiskId", 42)))
            .isInstanceOf(IllegalArgumentException.class);
        verify(riskLinkService, never()).create(anyLong(), any());
    }

    @Test
    void linkErm_validRisk_setsPointerAndCreatesBidirectionalClimateLink() {
        when(repo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.of(persistedRisk()));
        when(repo.save(any(ClimateRiskOpportunity.class))).thenAnswer(inv -> inv.getArgument(0));
        Risk erm = new Risk();
        erm.setOrganisationId(ORG_ID);
        when(riskRepo.findById(42L)).thenReturn(Optional.of(erm));
        when(riskLinks.findByOrganisationIdAndLinkTypeAndTargetId(ORG_ID, "CLIMATE", ID))
            .thenReturn(List.of());                                   // none yet → create

        ClimateRiskOpportunity saved = service.linkErm(ID, Map.of("ermRiskId", 42));

        assertThat(saved.getRelatedErmRiskId()).isEqualTo(42L);
        ArgumentCaptor<RiskLinkDtos.CreateDto> cap = ArgumentCaptor.forClass(RiskLinkDtos.CreateDto.class);
        verify(riskLinkService).create(eq(42L), cap.capture());
        assertThat(cap.getValue().linkType()).isEqualTo("CLIMATE");
        assertThat(cap.getValue().targetId()).isEqualTo(ID);          // points back at this climate item
    }

    @Test
    void linkErm_nullId_unlinksAndRemovesBridgeLink() {
        ClimateRiskOpportunity cur = persistedRisk();
        cur.setRelatedErmRiskId(42L);
        when(repo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.of(cur));
        when(repo.save(any(ClimateRiskOpportunity.class))).thenAnswer(inv -> inv.getArgument(0));
        RiskLink existing = new RiskLink();
        existing.setId(77L);
        existing.setOrganisationId(ORG_ID);
        existing.setRiskId(42L);
        existing.setLinkType("CLIMATE");
        existing.setTargetId(ID);
        when(riskLinks.findByOrganisationIdAndLinkTypeAndTargetId(ORG_ID, "CLIMATE", ID))
            .thenReturn(List.of(existing));

        ClimateRiskOpportunity saved = service.linkErm(ID, Map.of());  // no ermRiskId → unlink

        assertThat(saved.getRelatedErmRiskId()).isNull();
        verify(riskLinkService).delete(77L);
        verify(riskLinkService, never()).create(anyLong(), any());
    }
}
