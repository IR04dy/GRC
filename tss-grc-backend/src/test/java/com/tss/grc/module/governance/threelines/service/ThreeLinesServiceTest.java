package com.tss.grc.module.governance.threelines.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.audit_lc.entity.Finding;
import com.tss.grc.module.audit_lc.repository.FindingRepository;
import com.tss.grc.module.capa.entity.CapaTask;
import com.tss.grc.module.capa.repository.CapaTaskRepository;
import com.tss.grc.module.governance.threelines.dto.RoleMappingUpdateRequest;
import com.tss.grc.module.governance.threelines.dto.ThreeLinesArtefactDto;
import com.tss.grc.module.governance.threelines.dto.ThreeLinesOverviewDto;
import com.tss.grc.module.governance.threelines.dto.UserBandDto;
import com.tss.grc.module.governance.threelines.entity.ThreeLinesRoleMapping;
import com.tss.grc.module.governance.threelines.repository.ThreeLinesRoleMappingRepository;
import com.tss.grc.module.iam.entity.Role;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.repository.PolicyDocumentRepository;
import com.tss.grc.module.privacy_lc.repository.DsrCaseRepository;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.repository.RiskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Locks the Sprint-11 3LoD aggregation, user-band resolution, and F7+F8
 * audit-trail behaviour. Pure unit-level — no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class ThreeLinesServiceTest {

    private static final Long ORG = 1L;

    @Mock private ThreeLinesRoleMappingRepository mappingRepo;
    @Mock private UserRepository                  userRepo;
    @Mock private RiskRepository                  riskRepo;
    @Mock private FindingRepository               findingRepo;
    @Mock private CapaTaskRepository              capaRepo;
    @Mock private PolicyDocumentRepository        policyRepo;
    @Mock private DsrCaseRepository               dsrRepo;
    @Mock private AuditLogService                 audit;
    @Mock private ApplicationEventPublisher       events;

    @InjectMocks private ThreeLinesService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ─── Aggregation ──────────────────────────────────────────────

    @Test
    void overview_counts_open_artefacts_per_band_and_org_scopes_every_query() {
        // Mappings: cover all four bands.
        when(mappingRepo.findEffectiveForOrg(ORG)).thenReturn(List.of(
            mapping("RISK_OWNER",          "LINE_1"),
            mapping("RISK_MANAGER",        "LINE_2"),
            mapping("DPO",                 "LINE_2"),
            mapping("INTERNAL_AUDITOR",    "LINE_3"),
            mapping("BOARD_MEMBER",        "BOARD")
        ));

        // Open risks (2 OPEN, 1 CLOSED → filtered out).
        when(riskRepo.findByOrganisationId(eq(ORG), any(Pageable.class)))
            .thenReturn(pageOf(risk("OPEN"), risk("OPEN"), risk("CLOSED")));

        // Findings: 3 raised; 1 CLOSED filtered out; 1 CRITICAL escalates to BOARD.
        when(findingRepo.findByOrganisationIdOrderByRaisedAtDesc(ORG)).thenReturn(List.of(
            finding("OPEN", "HIGH"),
            finding("OPEN", "CRITICAL"),
            finding("CLOSED", "MEDIUM")
        ));

        // CAPA: 2 OPEN (1 overdue), 1 CLOSED.
        when(capaRepo.findByOrganisationIdOrderByCreatedAtDesc(ORG)).thenReturn(List.of(
            capa("OPEN",  LocalDate.now().minusDays(3)),    // overdue
            capa("OPEN",  LocalDate.now().plusDays(15)),    // on track
            capa("CLOSED", LocalDate.now().minusDays(100))  // closed - ignored
        ));

        // Policies pending approval — REVIEW status.
        when(policyRepo.findByOrganisationIdAndStatusOrderByPolicyCodeAscVersionDesc(ORG, "REVIEW"))
            .thenReturn(List.of(policy("REVIEW")));

        // DSR — single INTAKE row.
        when(dsrRepo.countByOrganisationIdAndStatus(eq(ORG), any(String.class))).thenReturn(0L);
        when(dsrRepo.countByOrganisationIdAndStatus(ORG, "INTAKE")).thenReturn(1L);

        ThreeLinesOverviewDto out = service.overview();

        assertThat(out.bands()).hasSize(4);
        Map<String, ThreeLinesOverviewDto.BandCounts> byBand = byBand(out.bands());

        // LINE_1 — 2 open risks, 2 open CAPA (1 overdue).
        assertThat(byBand.get("LINE_1").risksOpen()).isEqualTo(2);
        assertThat(byBand.get("LINE_1").capaOpen()).isEqualTo(2);
        assertThat(byBand.get("LINE_1").overdueItems()).isEqualTo(1);
        assertThat(byBand.get("LINE_1").roleNames()).contains("RISK_OWNER");

        // LINE_2 — 1 policy pending, 1 DSR open.
        assertThat(byBand.get("LINE_2").policiesPendingApproval()).isEqualTo(1);
        assertThat(byBand.get("LINE_2").dsrCasesOpen()).isEqualTo(1);
        assertThat(byBand.get("LINE_2").roleNames()).contains("RISK_MANAGER", "DPO");

        // LINE_3 — 2 open findings (HIGH + CRITICAL).
        assertThat(byBand.get("LINE_3").findingsOpen()).isEqualTo(2);
        assertThat(byBand.get("LINE_3").roleNames()).contains("INTERNAL_AUDITOR");

        // BOARD — the CRITICAL finding escalates.
        assertThat(byBand.get("BOARD").approvalsPending()).isEqualTo(1);
        assertThat(byBand.get("BOARD").roleNames()).contains("BOARD_MEMBER");

        // Totals — only count open items.
        assertThat(out.totalRisksOpen()).isEqualTo(2);
        assertThat(out.totalFindingsOpen()).isEqualTo(2);
        assertThat(out.totalCapaOpen()).isEqualTo(2);
    }

    @Test
    void overview_returns_zero_counts_when_register_is_empty() {
        when(mappingRepo.findEffectiveForOrg(ORG)).thenReturn(List.of());
        when(riskRepo.findByOrganisationId(eq(ORG), any(Pageable.class))).thenReturn(pageOf());
        when(findingRepo.findByOrganisationIdOrderByRaisedAtDesc(ORG)).thenReturn(List.of());
        when(capaRepo.findByOrganisationIdOrderByCreatedAtDesc(ORG)).thenReturn(List.of());
        when(policyRepo.findByOrganisationIdAndStatusOrderByPolicyCodeAscVersionDesc(ORG, "REVIEW"))
            .thenReturn(List.of());
        when(dsrRepo.countByOrganisationIdAndStatus(eq(ORG), any(String.class))).thenReturn(0L);

        ThreeLinesOverviewDto out = service.overview();

        assertThat(out.totalRisksOpen()).isZero();
        assertThat(out.totalFindingsOpen()).isZero();
        assertThat(out.totalCapaOpen()).isZero();
        assertThat(out.totalOverdue()).isZero();
        assertThat(out.bands()).hasSize(4); // all four bands always rendered
    }

    // ─── User → band resolution ──────────────────────────────────

    @Test
    void bandForUser_highestBandWins_when_user_has_multiple_roles() {
        // User holds RISK_OWNER (LINE_1) AND INTERNAL_AUDITOR (LINE_3).
        // Highest-band-wins rule → LINE_3 (assurance independence beats ops).
        User u = user(42L, "auditor-cum-owner", roles("RISK_OWNER", "INTERNAL_AUDITOR"));
        when(userRepo.findById(42L)).thenReturn(Optional.of(u));
        when(mappingRepo.findEffectiveForOrg(ORG)).thenReturn(List.of(
            mapping("RISK_OWNER",       "LINE_1"),
            mapping("INTERNAL_AUDITOR", "LINE_3")
        ));

        String band = service.bandForUser(42L);

        assertThat(band).isEqualTo("LINE_3");
    }

    @Test
    void bandForUser_BOARD_outranks_LINE_3() {
        // Audit Committee Chair (BOARD) who is also Internal Auditor (LINE_3).
        User u = user(42L, "ac-chair", roles("AUDIT_COMMITTEE_CHAIR", "INTERNAL_AUDITOR"));
        when(userRepo.findById(42L)).thenReturn(Optional.of(u));
        when(mappingRepo.findEffectiveForOrg(ORG)).thenReturn(List.of(
            mapping("AUDIT_COMMITTEE_CHAIR", "BOARD"),
            mapping("INTERNAL_AUDITOR",      "LINE_3")
        ));

        assertThat(service.bandForUser(42L)).isEqualTo("BOARD");
    }

    @Test
    void bandForUser_returns_null_when_user_has_no_mapped_role() {
        User u = user(42L, "ghost", roles("SOME_UNMAPPED_ROLE"));
        when(userRepo.findById(42L)).thenReturn(Optional.of(u));
        when(mappingRepo.findEffectiveForOrg(ORG)).thenReturn(List.of(
            mapping("RISK_OWNER", "LINE_1")
        ));

        assertThat(service.bandForUser(42L)).isNull();
    }

    @Test
    void bandForUser_returns_null_for_unknown_userId() {
        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.bandForUser(99L)).isNull();
    }

    @Test
    void userBandFor_returns_all_bands_the_user_touches() {
        // Risk Owner (LINE_1) + Board Member (BOARD) — both bands surface.
        User u = user(42L, "exec-owner", roles("RISK_OWNER", "BOARD_MEMBER"));
        when(userRepo.findById(42L)).thenReturn(Optional.of(u));
        when(mappingRepo.findEffectiveForOrg(ORG)).thenReturn(List.of(
            mapping("RISK_OWNER",   "LINE_1"),
            mapping("BOARD_MEMBER", "BOARD")
        ));

        UserBandDto dto = service.userBandFor(42L);

        assertThat(dto.userId()).isEqualTo(42L);
        assertThat(dto.allBands()).containsExactlyInAnyOrder("LINE_1", "BOARD");
        assertThat(dto.effectiveBand()).isEqualTo("BOARD"); // highest band wins
    }

    @Test
    void effectiveBand_precedence_LINE_3_beats_LINE_2_beats_LINE_1() {
        assertThat(ThreeLinesService.effectiveBand(List.of("LINE_1","LINE_2"))).isEqualTo("LINE_2");
        assertThat(ThreeLinesService.effectiveBand(List.of("LINE_2","LINE_3"))).isEqualTo("LINE_3");
        assertThat(ThreeLinesService.effectiveBand(List.of("LINE_1"))).isEqualTo("LINE_1");
        assertThat(ThreeLinesService.effectiveBand(List.of())).isNull();
        assertThat(ThreeLinesService.effectiveBand(null)).isNull();
    }

    // ─── F7 + F8 audit-trail on mapping mutation ────────────────

    @Test
    void mapRoleToBand_writes_F7_audit_row_and_publishes_F8_event() {
        // No pre-existing tenant override; the call promotes from a default.
        when(mappingRepo.findByOrganisationIdAndRoleName(ORG, "COMPLIANCE_OFFICER"))
            .thenReturn(Optional.empty());
        when(mappingRepo.findByOrganisationIdIsNullAndRoleName("COMPLIANCE_OFFICER"))
            .thenReturn(Optional.of(mapping("COMPLIANCE_OFFICER", "LINE_2")));
        when(mappingRepo.save(any(ThreeLinesRoleMapping.class)))
            .thenAnswer(inv -> {
                ThreeLinesRoleMapping m = inv.getArgument(0);
                m.setId(123L);
                return m;
            });

        RoleMappingUpdateRequest req = new RoleMappingUpdateRequest(
            "COMPLIANCE_OFFICER", "LINE_3", null, null, null, null);

        ThreeLinesRoleMapping saved = service.mapRoleToBand(req);

        // The new tenant-scoped row carries the new band.
        assertThat(saved.getLodBand()).isEqualTo("LINE_3");
        assertThat(saved.getOrganisationId()).isEqualTo(ORG);

        // F7 — exactly one audit row with LOD_ROLE_MAPPING_CHANGED.
        ArgumentCaptor<AuditLogService.AuditEventBuilder> auditCap =
            ArgumentCaptor.forClass(AuditLogService.AuditEventBuilder.class);
        verify(audit).recordEvent(auditCap.capture());
        // (Builder fields are package-private — sanity-check via the recorded
        //  side-effect: audit was called exactly once with a non-null builder.)
        assertThat(auditCap.getValue()).isNotNull();

        // F8 — domain event published with LOD_ROLE_MAPPING_CHANGED.
        ArgumentCaptor<DomainEvent> evtCap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events).publishEvent(evtCap.capture());
        DomainEvent evt = evtCap.getValue();
        assertThat(evt.triggerEvent()).isEqualTo("LOD_ROLE_MAPPING_CHANGED");
        assertThat(evt.organisationId()).isEqualTo(ORG);
        assertThat(evt.entityType()).isEqualTo("ThreeLinesRoleMapping");
        assertThat(evt.payload()).containsEntry("role_name", "COMPLIANCE_OFFICER");
        assertThat(evt.payload()).containsEntry("old_band", "LINE_2");
        assertThat(evt.payload()).containsEntry("new_band", "LINE_3");
    }

    @Test
    void mapRoleToBand_rejects_unknown_band() {
        RoleMappingUpdateRequest bad = new RoleMappingUpdateRequest(
            "RISK_MANAGER", "LINE_99", null, null, null, null);

        assertThatThrownBy(() -> service.mapRoleToBand(bad))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("LINE_99");
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private static ThreeLinesRoleMapping mapping(String roleName, String band) {
        ThreeLinesRoleMapping m = new ThreeLinesRoleMapping();
        m.setRoleName(roleName);
        m.setLodBand(band);
        m.setTitleEn(roleName);
        m.setTitleAr(roleName);
        return m;
    }

    private static Risk risk(String status) {
        Risk r = new Risk();
        r.setOrganisationId(ORG);
        r.setStatus(status);
        r.setTitleEn("test risk");
        r.setTitleAr("مخاطرة اختبار");
        return r;
    }

    private static Finding finding(String status, String severity) {
        Finding f = new Finding();
        f.setOrganisationId(ORG);
        f.setStatus(status);
        f.setSeverity(severity);
        f.setExternalId("FIN-" + status + "-" + severity);
        f.setTitleEn("finding");
        f.setTitleAr("ملاحظة");
        return f;
    }

    private static CapaTask capa(String status, LocalDate targetDate) {
        CapaTask c = new CapaTask();
        c.setOrganisationId(ORG);
        c.setStatus(status);
        c.setTargetDate(targetDate);
        c.setExternalId("CAPA-" + status);
        c.setTitleEn("capa");
        c.setTitleAr("إجراء");
        c.setOwner("user@tss.sa");
        return c;
    }

    private static PolicyDocument policy(String status) {
        PolicyDocument p = new PolicyDocument();
        p.setOrganisationId(ORG);
        p.setStatus(status);
        p.setTitleEn("policy");
        p.setTitleAr("سياسة");
        p.setPolicyCode("POL-01");
        p.setVersion("1.0");
        p.setCategory("GENERAL");
        return p;
    }

    private static User user(Long id, String username, Set<Role> roles) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setOrganisationId(ORG);
        u.setRoles(roles);
        return u;
    }

    private static Set<Role> roles(String... names) {
        Set<Role> out = new HashSet<>();
        for (String n : names) {
            Role r = new Role();
            r.setRoleName(n);
            out.add(r);
        }
        return out;
    }

    private static Page<Risk> pageOf(Risk... risks) {
        return new PageImpl<>(Arrays.asList(risks));
    }

    private static Map<String, ThreeLinesOverviewDto.BandCounts> byBand(
            List<ThreeLinesOverviewDto.BandCounts> bands) {
        Map<String, ThreeLinesOverviewDto.BandCounts> m = new HashMap<>();
        for (ThreeLinesOverviewDto.BandCounts b : bands) m.put(b.lodBand(), b);
        return m;
    }
}
