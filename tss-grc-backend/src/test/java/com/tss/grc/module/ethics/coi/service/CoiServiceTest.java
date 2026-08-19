package com.tss.grc.module.ethics.coi.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.CampaignResponse;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.CreateCampaignRequest;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.DeclarationResponse;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.DisclosedItemDto;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.RecordRecusalRequest;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.RecusalEventResponse;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.ReviewDeclarationRequest;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.SubmitDeclarationRequest;
import com.tss.grc.module.ethics.coi.entity.CoiDeclaration;
import com.tss.grc.module.ethics.coi.entity.CoiDeclarationCampaign;
import com.tss.grc.module.ethics.coi.entity.CoiDisclosedItem;
import com.tss.grc.module.ethics.coi.entity.CoiRecusalEvent;
import com.tss.grc.module.ethics.coi.repository.CoiDeclarationCampaignRepository;
import com.tss.grc.module.ethics.coi.repository.CoiDeclarationRepository;
import com.tss.grc.module.ethics.coi.repository.CoiDisclosedItemRepository;
import com.tss.grc.module.ethics.coi.repository.CoiRecusalEventRepository;
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
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
 * Unit tests for {@link CoiService} — Sprint 13.
 *
 * <p>Coverage:
 * <ul>
 *   <li>Campaign lifecycle DRAFT → OPEN → CLOSED → ARCHIVED + invalid transitions</li>
 *   <li>Auto-flag rule: RELATED_PARTY / FAMILY_RELATIONSHIP / POLITICAL_OFFICE → RECUSAL_REQUIRED</li>
 *   <li>Auto-flag rule: DIRECTORSHIP / OUTSIDE_EMPLOYMENT → MITIGATION_REQUIRED</li>
 *   <li>Auto-flag threshold: value &gt;= SAR 100,000 → RECUSAL_REQUIRED</li>
 *   <li>Auto-flag threshold: value &gt;= SAR 50,000 → MITIGATION_REQUIRED</li>
 *   <li>Auto-flag: clean items → PENDING</li>
 *   <li>Auto-flag: highest severity wins when multiple items present</li>
 *   <li>reviewDeclaration sets reviewer + outcome + emits event</li>
 *   <li>recordRecusal persists + emits COI_RECUSAL_RECORDED</li>
 *   <li>Tenant isolation on declaration load</li>
 *   <li>F8 events for declaration submission + auto-flag</li>
 *   <li>Cannot submit declaration against a non-OPEN campaign</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CoiServiceTest {

    private static final Long ORG_ID = 7L;
    private static final Long OTHER_ORG = 9L;
    private static final Long CAMPAIGN_ID = 50L;

    @Mock private CoiDeclarationCampaignRepository campaignRepo;
    @Mock private CoiDeclarationRepository declarationRepo;
    @Mock private CoiDisclosedItemRepository itemRepo;
    @Mock private CoiRecusalEventRepository recusalRepo;
    /** F2-244 — the recusal's subject is resolved in-tenant now, so the service needs a directory. */
    @Mock private com.tss.grc.module.iam.repository.UserRepository users;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    @InjectMocks private CoiService service;

    /**
     * F2-144 — a principal is now required to submit a declaration.
     *
     * <p>The five {@code submitDeclaration_*} tests below exercise the auto-flag thresholds and never
     * authenticated anyone, which worked only because the service stored the request body's
     * {@code userId} verbatim. That is the defect F2-144 records: nothing bound a conflict-of-interest
     * attestation to the person filing it. The service now refuses a declaration it cannot attribute,
     * so these tests supply a declarant — the identity rules themselves are covered by
     * {@link CoiDeclarantBindingTest}, and nothing here asserts on who the declarant is.
     */
    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        // F2-249 — a REAL TenantUserDirectory over the mocked repository, never a mock of it.
        // F2-237: a mocked resolver returns empty, so every refusal this service performs
        // would go green asserting nothing. The primitive is pure logic over the repository.
        org.springframework.test.util.ReflectionTestUtils.setField(service, "directory",
                new com.tss.grc.module.iam.service.TenantUserDirectory(users));
        var principal = new com.tss.grc.security.GrcUserDetails(
                7L, ORG_ID, "declarant", "pw", true, true,
                java.util.List.of(new org.springframework.security.core.authority
                        .SimpleGrantedAuthority("ROLE_VIEWER")));
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(new org.springframework.security.authentication
                        .UsernamePasswordAuthenticationToken(
                                principal, "pw", principal.getAuthorities()));
    }

    @AfterEach  void tearDown() {
        TenantContext.clear();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // ─── Fixtures ──────────────────────────────────────────────────

    private static CoiDeclarationCampaign campaign(String status) {
        CoiDeclarationCampaign c = new CoiDeclarationCampaign();
        c.setId(CAMPAIGN_ID);
        c.setOrganisationId(ORG_ID);
        c.setCode("COI-2026-ANNUAL");
        c.setTitleEn("FY2026 cycle");
        c.setTitleAr("دورة 2026");
        c.setFiscalYear(2026);
        c.setOpensOn(LocalDate.of(2026, 1, 15));
        c.setClosesOn(LocalDate.of(2026, 2, 28));
        c.setStatus(status);
        c.setScope("BOARD");
        c.setTargetUserCount(25);
        c.setDeclaredUserCount(0);
        return c;
    }

    private static DisclosedItemDto item(String type, BigDecimal sar) {
        return new DisclosedItemDto(
                type,
                "Counterparty EN", "الطرف المقابل",
                "Description EN", "وصف",
                sar, null, null, true,
                "Mitigate EN", "تخفيف");
    }

    private static SubmitDeclarationRequest submitReq(List<DisclosedItemDto> items) {
        return new SubmitDeclarationRequest(
                CAMPAIGN_ID, 1001L, "BOD-001",
                "Ahmad Al-Otaibi", "أحمد العتيبي",
                "Board Member", "عضو مجلس الإدارة",
                "Board of Directors", "مجلس الإدارة",
                false, false,
                "I attest the above is accurate.",
                items);
    }

    // ─── Campaign lifecycle ────────────────────────────────────────

    @Test
    void createCampaign_persistsAndSetsDraftStatus() {
        when(campaignRepo.existsByOrganisationIdAndCodeAndIsDeletedFalse(ORG_ID, "COI-2026-ANNUAL"))
                .thenReturn(false);
        when(campaignRepo.save(any(CoiDeclarationCampaign.class))).thenAnswer(inv -> {
            CoiDeclarationCampaign c = inv.getArgument(0);
            c.setId(CAMPAIGN_ID);
            return c;
        });
        CreateCampaignRequest req = new CreateCampaignRequest(
                "COI-2026-ANNUAL", "FY2026 cycle", "دورة 2026",
                2026, LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 28),
                "BOARD", 25);

        CampaignResponse out = service.createCampaign(req);

        assertThat(out.id()).isEqualTo(CAMPAIGN_ID);
        assertThat(out.status()).isEqualTo("DRAFT");
        assertThat(out.scope()).isEqualTo("BOARD");
        assertThat(out.targetUserCount()).isEqualTo(25);

        ArgumentCaptor<CoiDeclarationCampaign> captor = ArgumentCaptor.forClass(CoiDeclarationCampaign.class);
        verify(campaignRepo).save(captor.capture());
        assertThat(captor.getValue().getOrganisationId()).isEqualTo(ORG_ID);
        verify(audit).log(eq("COI_CAMPAIGN_CREATED"), eq("CoiDeclarationCampaign"),
                eq(CAMPAIGN_ID), any());
    }

    @Test
    void openCampaign_draftToOpen_emitsEvent() {
        CoiDeclarationCampaign c = campaign("DRAFT");
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(c));
        when(campaignRepo.save(any(CoiDeclarationCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignResponse out = service.openCampaign(CAMPAIGN_ID);

        assertThat(out.status()).isEqualTo("OPEN");
        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evt.capture());
        assertThat(evt.getValue().triggerEvent()).isEqualTo("COI_CAMPAIGN_OPENED");
        assertThat(evt.getValue().payload()).containsEntry("status", "OPEN");
    }

    @Test
    void closeCampaign_openToClosed_emitsEvent() {
        CoiDeclarationCampaign c = campaign("OPEN");
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(c));
        when(campaignRepo.save(any(CoiDeclarationCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignResponse out = service.closeCampaign(CAMPAIGN_ID);

        assertThat(out.status()).isEqualTo("CLOSED");
        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evt.capture());
        assertThat(evt.getValue().triggerEvent()).isEqualTo("COI_CAMPAIGN_CLOSED");
    }

    @Test
    void openCampaign_fromArchived_throwsIllegalTransition() {
        CoiDeclarationCampaign c = campaign("ARCHIVED");
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.openCampaign(CAMPAIGN_ID))
                .isInstanceOf(IllegalTransitionException.class);
    }

    // ─── submitDeclaration: auto-flag rules ────────────────────────

    @Test
    void submitDeclaration_relatedPartyItem_autoFlagsRecusalRequired() {
        CoiDeclarationCampaign c = campaign("OPEN");
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(c));
        when(declarationRepo.save(any(CoiDeclaration.class))).thenAnswer(inv -> {
            CoiDeclaration d = inv.getArgument(0);
            if (d.getId() == null) d.setId(100L);
            return d;
        });
        when(itemRepo.save(any(CoiDisclosedItem.class))).thenAnswer(inv -> {
            CoiDisclosedItem i = inv.getArgument(0);
            i.setId(200L);
            return i;
        });
        when(declarationRepo.countByOrganisationIdAndCampaignIdAndIsDeletedFalse(ORG_ID, CAMPAIGN_ID))
                .thenReturn(1L);

        DeclarationResponse out = service.submitDeclaration(
                submitReq(List.of(item("RELATED_PARTY", new BigDecimal("5000")))));

        assertThat(out.reviewOutcome()).isEqualTo("RECUSAL_REQUIRED");
        // Two events: SUBMITTED and AUTO_FLAGGED_RECUSAL.
        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, times(2)).publishEvent(evt.capture());
        assertThat(evt.getAllValues()).extracting(DomainEvent::triggerEvent)
                .containsExactly("COI_DECLARATION_SUBMITTED", "COI_AUTO_FLAGGED_RECUSAL");
    }

    /**
     * F2-228 — the declaration row must be written EXACTLY ONCE.
     *
     * <p>It used to be saved, then re-saved to carry the auto-flagged outcome once the items had
     * been inserted. Because the first save is an IDENTITY INSERT and {@code BaseEntity} maps
     * {@code row_version} {@code @Version insertable=false updatable=false} over a SQL Server
     * ROWVERSION, the in-memory version was null afterwards and the second UPDATE matched zero
     * rows. Probed live: <b>any declaration disclosing an item answered HTTP 409 and rolled
     * back</b>, so the conflict-of-interest register could record everything except a conflict.
     *
     * <p>⚠ Structural, because no mock can reproduce the failure — a mocked repository has no
     * {@code row_version}, which is exactly why this family went unmeasured (CLAUDE.md). The
     * assertion is therefore the property the fix guarantees, as F2-227 did for the exercise
     * inject: one save, and the outcome decided before it.
     */
    @Test
    void submitDeclaration_withADisclosedItem_savesTheDeclarationExactlyOnce() {
        CoiDeclarationCampaign c = campaign("OPEN");
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(c));
        when(declarationRepo.save(any(CoiDeclaration.class))).thenAnswer(inv -> {
            CoiDeclaration d = inv.getArgument(0);
            if (d.getId() == null) d.setId(100L);
            return d;
        });
        when(itemRepo.save(any(CoiDisclosedItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<CoiDeclaration> saved = ArgumentCaptor.forClass(CoiDeclaration.class);
        DeclarationResponse out = service.submitDeclaration(
                submitReq(List.of(item("RELATED_PARTY", new BigDecimal("5000")))));

        verify(declarationRepo, times(1)).save(saved.capture());
        assertThat(saved.getValue().getReviewOutcome())
                .as("the auto-flagged outcome must be decided BEFORE the single save, not "
                    + "written by a second one")
                .isEqualTo("RECUSAL_REQUIRED");
        assertThat(out.reviewOutcome()).isEqualTo("RECUSAL_REQUIRED");
    }

    @Test
    void submitDeclaration_directorshipItem_autoFlagsMitigationRequired() {
        CoiDeclarationCampaign c = campaign("OPEN");
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(c));
        when(declarationRepo.save(any(CoiDeclaration.class))).thenAnswer(inv -> {
            CoiDeclaration d = inv.getArgument(0);
            if (d.getId() == null) d.setId(101L);
            return d;
        });
        when(itemRepo.save(any(CoiDisclosedItem.class))).thenAnswer(inv -> inv.getArgument(0));

        DeclarationResponse out = service.submitDeclaration(
                submitReq(List.of(item("DIRECTORSHIP", null))));

        assertThat(out.reviewOutcome()).isEqualTo("MITIGATION_REQUIRED");
        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(evt.capture());
        assertThat(evt.getAllValues()).extracting(DomainEvent::triggerEvent)
                .contains("COI_AUTO_FLAGGED_MITIGATION");
    }

    @Test
    void submitDeclaration_valueAt100000_autoFlagsRecusalRequired() {
        CoiDeclarationCampaign c = campaign("OPEN");
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(c));
        when(declarationRepo.save(any(CoiDeclaration.class))).thenAnswer(inv -> {
            CoiDeclaration d = inv.getArgument(0);
            if (d.getId() == null) d.setId(102L);
            return d;
        });
        when(itemRepo.save(any(CoiDisclosedItem.class))).thenAnswer(inv -> inv.getArgument(0));

        // Type alone wouldn't flag (SHAREHOLDING), but value >= 100k pushes to RECUSAL.
        DeclarationResponse out = service.submitDeclaration(
                submitReq(List.of(item("SHAREHOLDING", new BigDecimal("100000")))));

        assertThat(out.reviewOutcome()).isEqualTo("RECUSAL_REQUIRED");
    }

    @Test
    void submitDeclaration_valueAt50000_autoFlagsMitigationRequired() {
        CoiDeclarationCampaign c = campaign("OPEN");
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(c));
        when(declarationRepo.save(any(CoiDeclaration.class))).thenAnswer(inv -> {
            CoiDeclaration d = inv.getArgument(0);
            if (d.getId() == null) d.setId(103L);
            return d;
        });
        when(itemRepo.save(any(CoiDisclosedItem.class))).thenAnswer(inv -> inv.getArgument(0));

        DeclarationResponse out = service.submitDeclaration(
                submitReq(List.of(item("SHAREHOLDING", new BigDecimal("60000")))));

        assertThat(out.reviewOutcome()).isEqualTo("MITIGATION_REQUIRED");
    }

    @Test
    void submitDeclaration_cleanItems_remainsPending() {
        CoiDeclarationCampaign c = campaign("OPEN");
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(c));
        when(declarationRepo.save(any(CoiDeclaration.class))).thenAnswer(inv -> {
            CoiDeclaration d = inv.getArgument(0);
            if (d.getId() == null) d.setId(104L);
            return d;
        });
        when(itemRepo.save(any(CoiDisclosedItem.class))).thenAnswer(inv -> inv.getArgument(0));

        DeclarationResponse out = service.submitDeclaration(
                submitReq(List.of(item("GIFT_HOSPITALITY", new BigDecimal("1800")))));

        assertThat(out.reviewOutcome()).isEqualTo("PENDING");
    }

    @Test
    void computeAutoOutcome_recusalDominatesOverMitigation() {
        CoiDisclosedItem mitigation = new CoiDisclosedItem();
        mitigation.setItemType("DIRECTORSHIP");
        mitigation.setIsActive(true);
        CoiDisclosedItem recusal = new CoiDisclosedItem();
        recusal.setItemType("FAMILY_RELATIONSHIP");
        recusal.setIsActive(true);

        assertThat(CoiService.computeAutoOutcome(List.of(mitigation, recusal)))
                .isEqualTo("RECUSAL_REQUIRED");
    }

    @Test
    void computeAutoOutcome_inactiveItemsAreIgnored() {
        CoiDisclosedItem inactive = new CoiDisclosedItem();
        inactive.setItemType("RELATED_PARTY");
        inactive.setIsActive(false);

        assertThat(CoiService.computeAutoOutcome(List.of(inactive))).isEqualTo("PENDING");
    }

    @Test
    void submitDeclaration_campaignNotOpen_throwsIllegalState() {
        CoiDeclarationCampaign c = campaign("DRAFT");
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.submitDeclaration(submitReq(List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    // ─── reviewDeclaration ─────────────────────────────────────────

    @Test
    void reviewDeclaration_setsOutcomeAndEmitsEvent() {
        CoiDeclaration d = new CoiDeclaration();
        d.setId(300L);
        d.setOrganisationId(ORG_ID);
        d.setCampaignId(CAMPAIGN_ID);
        d.setFullNameEn("Noura Al-Qahtani");
        d.setFullNameAr("نورة القحطاني");
        d.setReviewOutcome("PENDING");
        when(declarationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 300L))
                .thenReturn(Optional.of(d));
        when(declarationRepo.save(any(CoiDeclaration.class))).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepo.findByDeclarationIdAndIsDeletedFalse(300L)).thenReturn(List.of());

        DeclarationResponse out = service.reviewDeclaration(300L,
                new ReviewDeclarationRequest("MITIGATION_REQUIRED",
                        "Mitigation note EN", "ملاحظة تخفيف"),
                42L);

        assertThat(out.reviewOutcome()).isEqualTo("MITIGATION_REQUIRED");
        assertThat(out.reviewedBy()).isEqualTo(42L);
        verify(audit).log(eq("COI_DECLARATION_REVIEWED"), eq("CoiDeclaration"), eq(300L), any());

        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evt.capture());
        assertThat(evt.getValue().triggerEvent()).isEqualTo("COI_DECLARATION_REVIEWED");
        assertThat(evt.getValue().payload()).containsEntry("reviewOutcome", "MITIGATION_REQUIRED");
    }

    @Test
    void reviewDeclaration_invalidOutcome_throws() {
        // Note: no repo stubbing necessary — outcome is validated before load.
        assertThatThrownBy(() -> service.reviewDeclaration(300L,
                new ReviewDeclarationRequest("BANANA", null, null), 42L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── recordRecusal ─────────────────────────────────────────────

    /**
     * F2-244 — repurposed, not deleted.
     *
     * <p>This test used to pass {@code userId = 1007L} and assert {@code out.userId()} came back as
     * {@code 1007L} — i.e. <b>that a user id resolving to nobody is stored and returned</b>, which
     * is exactly the defect. The number was not arbitrary either: 1007 is one of the seeded ids
     * that names no user in dev or prod ({@code MAX(iam.users.id)} is 8), so the fixture was copied
     * from the broken rows. The Nth instance of a test asserting the defect as correct (F2-159,
     * F2-175, F2-227, F2-236, F2-243).
     *
     * <p>The audit and event assertions are kept — they protect the record — and the subject is now
     * a real in-tenant user, with the refusal covered by the two tests below.
     */
    @Test
    void recordRecusal_persistsAndEmitsEvent() {
        var subject = new com.tss.grc.module.iam.entity.User();
        subject.setId(4L);
        subject.setOrganisationId(ORG_ID);
        subject.setIsActive(true);
        subject.setFullNameEn("Faisal Al-Harbi");
        subject.setFullNameAr("فيصل الحربي");
        when(users.findById(4L)).thenReturn(java.util.Optional.of(subject));
        when(recusalRepo.save(any(CoiRecusalEvent.class))).thenAnswer(inv -> {
            CoiRecusalEvent r = inv.getArgument(0);
            r.setId(500L);
            return r;
        });

        RecusalEventResponse out = service.recordRecusal(new RecordRecusalRequest(
                null, 4L, "BOD-2026-Q1-03", "BOD", null,
                "Credit facility — related family member",
                "تسهيل ائتماني — قريب عائلي",
                "4.2", true));

        assertThat(out.id()).isEqualTo(500L);
        assertThat(out.meetingReference()).isEqualTo("BOD-2026-Q1-03");
        assertThat(out.userId()).isEqualTo(4L);
        // F2-244 — the register can name the person now. Before this, the response carried no name
        // field at all while the frontend read one, so the recusal log named nobody anywhere.
        assertThat(out.userResolved()).isTrue();
        assertThat(out.userNameEn()).isEqualTo("Faisal Al-Harbi");
        assertThat(out.userNameAr()).isEqualTo("فيصل الحربي");
        verify(audit).log(eq("COI_RECUSAL_RECORDED"), eq("CoiRecusalEvent"), eq(500L), any());

        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evt.capture());
        assertThat(evt.getValue().triggerEvent()).isEqualTo("COI_RECUSAL_RECORDED");
        assertThat(evt.getValue().payload())
                .containsEntry("meetingReference", "BOD-2026-Q1-03")
                .containsEntry("userId", "4");
    }

    /**
     * F2-244 — the shape the old fixture used is now refused, and nothing is written.
     *
     * <p>{@code coi_recusal_event} declares {@code user_id} NOT NULL and has <b>no name column</b>,
     * so an unresolvable id leaves a SAMA Article 30 record whose subject cannot be recovered by
     * any means. The {@code verify(never())} matters as much as the throw: refusing after saving
     * would leave the row and lose only the response.
     */
    @Test
    void recordRecusal_refusesASubjectThatResolvesToNobody() {
        when(users.findById(1007L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.recordRecusal(new RecordRecusalRequest(
                null, 1007L, "BOD-2026-Q1-03", "BOD", null, "reason", "سبب", "4.2", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not an active user in this organisation");

        verify(recusalRepo, never()).save(any(CoiRecusalEvent.class));
        verify(audit, never()).log(eq("COI_RECUSAL_RECORDED"), any(), any(), any());
    }

    /**
     * F2-244 — and a user of another tenant is refused for the same reason and with the same
     * message. This is the leg that makes the check a TENANT check rather than an existence check:
     * {@code findById} is a bare primary-key lookup, so without the org filter a recusal could name
     * another organisation's director.
     */
    @Test
    void recordRecusal_refusesAForeignTenantsUser() {
        var foreign = new com.tss.grc.module.iam.entity.User();
        foreign.setId(88L);
        foreign.setOrganisationId(OTHER_ORG);
        foreign.setIsActive(true);
        when(users.findById(88L)).thenReturn(java.util.Optional.of(foreign));

        assertThatThrownBy(() -> service.recordRecusal(new RecordRecusalRequest(
                null, 88L, "BOD-2026-Q1-03", "BOD", null, "reason", "سبب", "4.2", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not an active user in this organisation");

        verify(recusalRepo, never()).save(any(CoiRecusalEvent.class));
    }

    // ─── Tenant isolation ──────────────────────────────────────────

    @Test
    void getDeclaration_otherOrgsDeclaration_throwsNotFound() {
        // Repository call uses the current tenant id, not the entity's — so a
        // declaration belonging to OTHER_ORG simply won't be returned, even
        // by id.
        when(declarationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDeclaration(999L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listCampaignsByOrg_mismatchedOrg_throws() {
        assertThatThrownBy(() -> service.listCampaignsByOrg(OTHER_ORG))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant mismatch");
    }
}
