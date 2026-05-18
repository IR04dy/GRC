package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.*;
import com.tss.grc.module.bcm.cycle.repository.*;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
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
 * Lock BCP plan builder behaviour:
 *
 * <ul>
 *   <li>create requires titleEn + valid planType</li>
 *   <li>create populates default sections from the catalogue (12 BCP / 12 DRP)</li>
 *   <li>create assigns a plan code with type prefix</li>
 *   <li>state machine: legal + illegal transitions enforced</li>
 *   <li>APPROVED transition snapshots sections to BcpVersion + bumps currentVersion</li>
 *   <li>APPROVED emits BCM_PLAN_APPROVED event</li>
 *   <li>locked statuses (APPROVED/PUBLISHED/ARCHIVED) reject edits</li>
 *   <li>section edit rejected on locked plan</li>
 *   <li>generateFromBia requires APPROVED/ACTIVE BIA</li>
 *   <li>generateFromBia copies RTO/RPO/MTPD from the BIA</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BcpPlanServiceTest {

    private static final Long ORG_ID = 111L;

    @Mock private BcpPlanRepository plans;
    @Mock private BcpSectionRepository sections;
    @Mock private BcpVersionRepository versions;
    @Mock private BcmBiaAssessmentRepository bias;
    /**
     * Added with Tier-2 #11 (plan distribution + acknowledgement). snapshot()
     * calls acks.markStaleBelow on every version bump; without this @Mock
     * the field would stay null and any test that flips a plan to APPROVED
     * NPEs at the auto-mark-stale hook.
     */
    @Mock private BcpPlanAcknowledgementRepository acks;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private BcpPlanService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(plans.save(any(BcpPlan.class))).thenAnswer(inv -> {
            BcpPlan p = inv.getArgument(0);
            if (p.getId() == null) p.setId(7L);
            return p;
        });
        when(sections.save(any(BcpSection.class))).thenAnswer(inv -> {
            BcpSection s = inv.getArgument(0);
            if (s.getId() == null) s.setId(System.nanoTime());
            return s;
        });
        when(versions.save(any(BcpVersion.class))).thenAnswer(inv -> {
            BcpVersion v = inv.getArgument(0);
            if (v.getId() == null) v.setId(System.nanoTime());
            return v;
        });
        when(plans.findByOrganisationIdOrderByIdDesc(eq(ORG_ID), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of()));
        // No existing versions by default — snapshot starts at version 1
        when(versions.findByOrganisationIdAndPlanIdOrderByVersionNumberDesc(eq(ORG_ID), any()))
            .thenReturn(List.of());
    }
    @AfterEach void tearDown() { TenantContext.clear(); }

    private static BcpPlan plan(String type, String status) {
        BcpPlan p = new BcpPlan();
        p.setId(7L); p.setOrganisationId(ORG_ID);
        p.setPlanCode("BCP-2026-000001");
        p.setTitleEn("Demo plan");
        p.setPlanType(type);
        p.setStatus(status);
        p.setCurrentVersion(1);
        return p;
    }

    // ─── Create ─────────────────────────────────────────────

    @Test void create_requiresTitle() {
        BcpPlan p = new BcpPlan(); p.setPlanType("BCP");
        assertThatThrownBy(() -> service.create(p))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("titleEn");
    }

    @Test void create_rejectsUnknownPlanType() {
        BcpPlan p = new BcpPlan(); p.setTitleEn("x"); p.setPlanType("BANANA");
        assertThatThrownBy(() -> service.create(p))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("planType");
    }

    @Test void create_bcp_buildsTwelveDefaultSections() {
        BcpPlan p = new BcpPlan(); p.setTitleEn("Plan"); p.setPlanType("BCP");
        service.create(p);

        ArgumentCaptor<BcpSection> cap = ArgumentCaptor.forClass(BcpSection.class);
        org.mockito.Mockito.verify(sections, org.mockito.Mockito.times(BcpPlanService.BCP_SECTIONS.size()))
            .save(cap.capture());
        assertThat(cap.getAllValues())
            .extracting(BcpSection::getSectionKey)
            .containsExactlyElementsOf(BcpPlanService.BCP_SECTIONS.stream()
                .map(BcpPlanService.SectionDef::key).toList());
    }

    @Test void create_drp_buildsDrpCatalogue() {
        BcpPlan p = new BcpPlan(); p.setTitleEn("DR Plan"); p.setPlanType("DRP");
        service.create(p);
        org.mockito.Mockito.verify(sections, org.mockito.Mockito.times(BcpPlanService.DRP_SECTIONS.size()))
            .save(any(BcpSection.class));
    }

    @Test void create_assignsPlanCodeWithTypePrefix() {
        BcpPlan p = new BcpPlan(); p.setTitleEn("Plan"); p.setPlanType("DRP");
        BcpPlan saved = service.create(p);
        assertThat(saved.getPlanCode()).startsWith("DRP-");
    }

    // ─── State machine ──────────────────────────────────────

    @Test void transition_legalPath_DRAFTtoUNDER_REVIEW() {
        BcpPlan p = plan("BCP", "DRAFT");
        when(plans.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(p));
        BcpPlan out = service.transition(7L, "UNDER_REVIEW", "manager");
        assertThat(out.getStatus()).isEqualTo("UNDER_REVIEW");
    }

    @Test void transition_illegal_PUBLISHEDfromDRAFT_rejected() {
        BcpPlan p = plan("BCP", "DRAFT");
        when(plans.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.transition(7L, "PUBLISHED", "x"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Illegal");
    }

    @Test void transition_APPROVED_snapshotsAndBumpsVersion() {
        BcpPlan p = plan("BCP", "UNDER_REVIEW");
        when(plans.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(p));
        when(sections.findByOrganisationIdAndPlanIdOrderBySectionOrderAsc(ORG_ID, 7L))
            .thenReturn(List.of());

        BcpPlan out = service.transition(7L, "APPROVED", "manager");
        assertThat(out.getStatus()).isEqualTo("APPROVED");
        assertThat(out.getApprovedAt()).isNotNull();
        assertThat(out.getApprovedBy()).isEqualTo("manager");
        assertThat(out.getCurrentVersion()).isEqualTo(1);

        ArgumentCaptor<BcpVersion> cap = ArgumentCaptor.forClass(BcpVersion.class);
        org.mockito.Mockito.verify(versions).save(cap.capture());
        assertThat(cap.getValue().getVersionNumber()).isEqualTo(1);
        assertThat(cap.getValue().getBodyJson()).contains("\"plan\"").contains("\"sections\"");
    }

    @Test void transition_APPROVED_emitsEvent() {
        BcpPlan p = plan("BCP", "UNDER_REVIEW");
        when(plans.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(p));
        when(sections.findByOrganisationIdAndPlanIdOrderBySectionOrderAsc(ORG_ID, 7L)).thenReturn(List.of());

        service.transition(7L, "APPROVED", "manager");

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        org.mockito.Mockito.verify(eventPublisher, atLeastOnce()).publishEvent(cap.capture());
        assertThat(cap.getAllValues()).extracting(DomainEvent::triggerEvent)
            .contains("BCM_PLAN_APPROVED");
    }

    @Test void transition_DRAFTfromAPPROVED_bumpsCurrentVersionPointer() {
        BcpPlan p = plan("BCP", "APPROVED");
        p.setCurrentVersion(3);
        when(plans.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(p));
        BcpPlan out = service.transition(7L, "DRAFT", "manager");
        assertThat(out.getStatus()).isEqualTo("DRAFT");
        assertThat(out.getCurrentVersion()).isEqualTo(4);  // bumped on reopen
    }

    // ─── Edit lock ──────────────────────────────────────────

    @Test void update_cannotEditApprovedPlan() {
        BcpPlan p = plan("BCP", "APPROVED");
        when(plans.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(p));
        BcpPlan patch = new BcpPlan(); patch.setTitleEn("change");
        assertThatThrownBy(() -> service.update(7L, patch))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("APPROVED");
    }

    @Test void editSection_rejectedOnLockedPlan() {
        BcpSection s = new BcpSection();
        s.setId(99L); s.setOrganisationId(ORG_ID); s.setPlanId(7L); s.setSectionKey("overview");
        when(sections.findByIdAndOrganisationId(99L, ORG_ID)).thenReturn(Optional.of(s));
        when(plans.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(plan("BCP", "PUBLISHED")));
        assertThatThrownBy(() -> service.updateSection(99L, "x", null, "editor"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PUBLISHED");
    }

    // ─── Generate from BIA ──────────────────────────────────

    @Test void generateFromBia_requiresApprovedOrActive() {
        BcmBiaAssessment bia = new BcmBiaAssessment();
        bia.setId(5L); bia.setOrganisationId(ORG_ID); bia.setBiaCode("BIA-2026-000001");
        bia.setApprovalStatus("DRAFT");
        when(bias.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(bia));

        assertThatThrownBy(() -> service.generateFromBia(5L, "BCP", null, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("APPROVED or ACTIVE");
    }

    @Test void generateFromBia_copiesRtoRpoMtpd() {
        BcmBiaAssessment bia = new BcmBiaAssessment();
        bia.setId(5L); bia.setOrganisationId(ORG_ID); bia.setBiaCode("BIA-2026-000001");
        bia.setApprovalStatus("APPROVED");
        bia.setMtpdHours(24); bia.setRtoHours(8); bia.setRpoHours(4);
        bia.setServiceId(99L);
        when(bias.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(bia));
        when(bias.save(any(BcmBiaAssessment.class))).thenAnswer(i -> i.getArgument(0));
        when(sections.findByOrganisationIdAndPlanIdAndSectionKey(eq(ORG_ID), any(), eq("overview")))
            .thenReturn(Optional.empty());

        BcpPlan saved = service.generateFromBia(5L, "BCP", null, null);
        assertThat(saved.getRtoHours()).isEqualTo(8);
        assertThat(saved.getRpoHours()).isEqualTo(4);
        assertThat(saved.getMtpdHours()).isEqualTo(24);
        assertThat(saved.getSourceBiaId()).isEqualTo(5L);
        assertThat(saved.getServiceId()).isEqualTo(99L);
        assertThat(bia.getGeneratedBcpId()).isEqualTo(7L);
    }

    @Test void generateFromBia_rejectsUnknownPlanType() {
        BcmBiaAssessment bia = new BcmBiaAssessment();
        bia.setId(5L); bia.setOrganisationId(ORG_ID); bia.setApprovalStatus("APPROVED");
        when(bias.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(bia));

        assertThatThrownBy(() -> service.generateFromBia(5L, "BANANA", null, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("planType");
    }

    // ─── Version diff ───────────────────────────────────────

    @Test void diff_returnsBothPayloads() {
        when(plans.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(plan("BCP", "PUBLISHED")));
        BcpVersion v1 = new BcpVersion();
        v1.setVersionNumber(1); v1.setBodyJson("{\"plan\":{\"version\":1}}");
        BcpVersion v3 = new BcpVersion();
        v3.setVersionNumber(3); v3.setBodyJson("{\"plan\":{\"version\":3}}");
        when(versions.findByOrganisationIdAndPlanIdAndVersionNumber(ORG_ID, 7L, 1)).thenReturn(Optional.of(v1));
        when(versions.findByOrganisationIdAndPlanIdAndVersionNumber(ORG_ID, 7L, 3)).thenReturn(Optional.of(v3));

        BcpPlanService.VersionDiff d = service.diff(7L, 1, 3);
        assertThat(d.from()).isEqualTo(1);
        assertThat(d.to()).isEqualTo(3);
        assertThat(d.fromJson()).contains("\"version\":1");
        assertThat(d.toJson()).contains("\"version\":3");
    }

    @Test void diff_unknownVersion_throws() {
        when(plans.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(plan("BCP", "PUBLISHED")));
        when(versions.findByOrganisationIdAndPlanIdAndVersionNumber(ORG_ID, 7L, 99)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.diff(7L, 99, 1))
            .isInstanceOf(EntityNotFoundException.class);
    }
}
