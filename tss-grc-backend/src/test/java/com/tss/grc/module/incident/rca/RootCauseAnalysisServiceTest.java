package com.tss.grc.module.incident.rca;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.capa.dto.CapaTaskDto;
import com.tss.grc.module.capa.service.CapaTaskService;
import com.tss.grc.module.incident.rca.dto.RcaDtos.CreateRequest;
import com.tss.grc.module.incident.rca.dto.RcaDtos.RejectRequest;
import com.tss.grc.module.incident.rca.dto.RcaDtos.UpdateRequest;
import com.tss.grc.module.incident.rca.entity.RootCauseAnalysis;
import com.tss.grc.module.incident.rca.repository.RootCauseAnalysisRepository;
import com.tss.grc.module.incident.rca.service.RootCauseAnalysisService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * RCA state machine contract:
 *   DRAFT     → IN_REVIEW (submit)
 *   IN_REVIEW → APPROVED  (approve, stamps approver + timestamp)
 *             → REJECTED  (reject, requires reason)
 *   APPROVED, REJECTED → terminal
 *
 * Plus: update() only legal from DRAFT; tenant-scoped 404 surfaces as
 * EntityNotFoundException.
 */
@ExtendWith(MockitoExtension.class)
class RootCauseAnalysisServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long RCA_ID = 42L;

    @Mock private RootCauseAnalysisRepository repo;
    @Mock private CapaTaskService capaService;
    @InjectMocks private RootCauseAnalysisService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static RootCauseAnalysis rca(String status) {
        RootCauseAnalysis r = new RootCauseAnalysis();
        r.setId(RCA_ID);
        r.setOrganisationId(ORG_ID);
        r.setRelatedEntityType("incident");
        r.setRelatedEntityId(100L);
        r.setMethodology("FIVE_WHYS");
        r.setProblemStatementEn("MFA bypass via cached session");
        r.setStatus(status);
        return r;
    }

    @Test void create_starts_in_DRAFT_and_stamps_external_id() {
        when(repo.save(any(RootCauseAnalysis.class))).thenAnswer(inv -> {
            RootCauseAnalysis r = inv.getArgument(0);
            r.setId(RCA_ID);
            return r;
        });

        CreateRequest body = new CreateRequest(
                "incident", 100L, "INC-2026-007",
                "FIVE_WHYS",
                "MFA bypass via cached session",
                "تجاوز التحقق الثنائي عبر الجلسة المخزنة",
                "[{\"question\":\"Why?\",\"answer\":\"Session token wasn't invalidated\"}]",
                null,
                "Stale session tokens accepted indefinitely",
                "الجلسات قديمة تظل مقبولة",
                "Rotate tokens every 8h; force re-MFA on IP change",
                "تدوير الرموز كل 8 ساعات",
                "Sarah · CISO Office",
                "Sarah, Ahmed, Layla",
                LocalDateTime.now(),
                "CAPA-1024,CAPA-1031");

        RootCauseAnalysis saved = service.create(body, 1L);

        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getExternalId()).startsWith("RCA-");
        assertThat(saved.getMethodology()).isEqualTo("FIVE_WHYS");
        assertThat(saved.getCreatedBy()).isEqualTo(1L);
    }

    @Test void create_defaults_methodology_to_FIVE_WHYS_when_omitted() {
        when(repo.save(any(RootCauseAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateRequest body = new CreateRequest(
                "incident", 100L, null, null,
                "Problem", null, null, null, null, null, null, null,
                null, null, null, null);

        RootCauseAnalysis saved = service.create(body, 1L);
        assertThat(saved.getMethodology()).isEqualTo("FIVE_WHYS");
    }

    @Test void update_allowed_only_when_DRAFT() {
        when(repo.findByIdAndOrganisationId(RCA_ID, ORG_ID)).thenReturn(Optional.of(rca("IN_REVIEW")));

        UpdateRequest body = new UpdateRequest(null, "New problem", null, null, null,
                "Root cause refined", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(RCA_ID, body))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void update_persists_only_non_null_fields() {
        when(repo.findByIdAndOrganisationId(RCA_ID, ORG_ID)).thenReturn(Optional.of(rca("DRAFT")));
        when(repo.save(any(RootCauseAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateRequest body = new UpdateRequest(null, "Updated problem", null, null, null,
                "Updated root cause", null, "Updated recommendations", null,
                "New facilitator", null, null, null);

        RootCauseAnalysis updated = service.update(RCA_ID, body);
        assertThat(updated.getProblemStatementEn()).isEqualTo("Updated problem");
        assertThat(updated.getRootCauseEn()).isEqualTo("Updated root cause");
        assertThat(updated.getRecommendationsEn()).isEqualTo("Updated recommendations");
        assertThat(updated.getFacilitator()).isEqualTo("New facilitator");
        // Methodology was null in the body so should not have been touched.
        assertThat(updated.getMethodology()).isEqualTo("FIVE_WHYS");
    }

    @Test void submit_moves_DRAFT_to_IN_REVIEW_and_stamps_submitter() {
        when(repo.findByIdAndOrganisationId(RCA_ID, ORG_ID)).thenReturn(Optional.of(rca("DRAFT")));
        when(repo.save(any(RootCauseAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        RootCauseAnalysis submitted = service.submit(RCA_ID, 7L);

        assertThat(submitted.getStatus()).isEqualTo("IN_REVIEW");
        assertThat(submitted.getSubmittedBy()).isEqualTo(7L);
        assertThat(submitted.getSubmittedAt()).isNotNull();
    }

    @Test void submit_refuses_when_not_DRAFT() {
        when(repo.findByIdAndOrganisationId(RCA_ID, ORG_ID)).thenReturn(Optional.of(rca("IN_REVIEW")));

        assertThatThrownBy(() -> service.submit(RCA_ID, 7L))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void approve_moves_IN_REVIEW_to_APPROVED_and_stamps_approver() {
        when(repo.findByIdAndOrganisationId(RCA_ID, ORG_ID)).thenReturn(Optional.of(rca("IN_REVIEW")));
        when(repo.save(any(RootCauseAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        RootCauseAnalysis approved = service.approve(RCA_ID, 9L, "CISO Office");

        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(approved.getApprovedBy()).isEqualTo(9L);
        assertThat(approved.getApproverName()).isEqualTo("CISO Office");
        assertThat(approved.getApprovedAt()).isNotNull();
    }

    @Test void approve_refuses_from_DRAFT() {
        when(repo.findByIdAndOrganisationId(RCA_ID, ORG_ID)).thenReturn(Optional.of(rca("DRAFT")));

        assertThatThrownBy(() -> service.approve(RCA_ID, 9L, "CISO Office"))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void reject_requires_reason_and_terminates() {
        when(repo.findByIdAndOrganisationId(RCA_ID, ORG_ID)).thenReturn(Optional.of(rca("IN_REVIEW")));
        when(repo.save(any(RootCauseAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        RootCauseAnalysis rejected = service.reject(RCA_ID, new RejectRequest("Insufficient evidence — re-investigate"));

        assertThat(rejected.getStatus()).isEqualTo("REJECTED");
        assertThat(rejected.getRejectionReason()).startsWith("Insufficient");
    }

    @Test void reject_refuses_from_DRAFT() {
        when(repo.findByIdAndOrganisationId(RCA_ID, ORG_ID)).thenReturn(Optional.of(rca("DRAFT")));

        assertThatThrownBy(() -> service.reject(RCA_ID, new RejectRequest("nope")))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void get_404s_when_row_belongs_to_other_tenant() {
        when(repo.findByIdAndOrganisationId(RCA_ID, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(RCA_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ─── Cross-module: spawn CAPAs from RCA recommendations ───────────

    @Test void generateCapas_refuses_when_not_APPROVED() {
        when(repo.findByIdAndOrganisationId(RCA_ID, ORG_ID)).thenReturn(Optional.of(rca("DRAFT")));
        assertThatThrownBy(() -> service.generateCapasFromRecommendations(RCA_ID, null))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void generateCapas_returns_empty_when_no_recommendations() {
        RootCauseAnalysis r = rca("APPROVED");
        r.setRecommendationsEn(null);
        when(repo.findByIdAndOrganisationId(RCA_ID, ORG_ID)).thenReturn(Optional.of(r));

        List<CapaTaskDto> out = service.generateCapasFromRecommendations(RCA_ID, null);
        assertThat(out).isEmpty();
    }

    @Test void generateCapas_parses_numbered_list_and_creates_one_per_item() {
        RootCauseAnalysis r = rca("APPROVED");
        r.setExternalId("RCA-A1B2C3D4");
        r.setRecommendationsEn(
                "1) Tighten USB exception to a VID/PID allow-list for the biometric thumb-drive only. " +
                "2) Accelerate the YubiKey rollout once SAMA approves. " +
                "3) Add a CrowdStrike custom IOA for the specific ransomware family detected.");
        when(repo.findByIdAndOrganisationId(RCA_ID, ORG_ID)).thenReturn(Optional.of(r));
        when(repo.save(any(RootCauseAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));
        when(capaService.create(any(CapaTaskDto.class))).thenAnswer(inv -> {
            CapaTaskDto dto = inv.getArgument(0);
            dto.setId("CAPA-" + System.nanoTime());
            return dto;
        });

        List<CapaTaskDto> out = service.generateCapasFromRecommendations(RCA_ID, "CISO Office");

        assertThat(out).hasSize(3);
        // Each CAPA carries the RCA's external id as origin.
        assertThat(out).allMatch(d -> "rca".equals(d.getOriginType()));
        assertThat(out).allMatch(d -> "RCA-A1B2C3D4".equals(d.getOriginId()));
        // First item's title starts with the action verb (numeric prefix stripped).
        assertThat(out.get(0).getTitleEn()).startsWith("Tighten USB exception");
        // Owner override applied.
        assertThat(out).allMatch(d -> "CISO Office".equals(d.getOwner()));
        // RCA's capa_refs got the new ids appended.
        assertThat(r.getCapaRefs()).isNotNull();
        assertThat(r.getCapaRefs().split(",")).hasSize(3);
    }

    @Test void generateCapas_falls_back_to_newline_split_when_no_numbered_list() {
        RootCauseAnalysis r = rca("APPROVED");
        r.setExternalId("RCA-XYZ");
        r.setRecommendationsEn(
                "First action item is sufficiently long.\n" +
                "Second action item is also reasonable.\n" +
                "tiny\n" +
                "Third long enough action item here.");
        when(repo.findByIdAndOrganisationId(RCA_ID, ORG_ID)).thenReturn(Optional.of(r));
        when(repo.save(any(RootCauseAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));
        when(capaService.create(any(CapaTaskDto.class))).thenAnswer(inv -> {
            CapaTaskDto dto = inv.getArgument(0);
            dto.setId("CAPA-" + System.nanoTime());
            return dto;
        });

        List<CapaTaskDto> out = service.generateCapasFromRecommendations(RCA_ID, null);
        // 3 long items survive; "tiny" is filtered out.
        assertThat(out).hasSize(3);
    }
}
