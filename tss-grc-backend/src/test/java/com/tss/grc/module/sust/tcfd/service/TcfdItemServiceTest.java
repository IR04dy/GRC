package com.tss.grc.module.sust.tcfd.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.capa.service.CorrectiveActionService;
import com.tss.grc.module.sust.climate.entity.ClimateRiskOpportunity;
import com.tss.grc.module.sust.climate.repository.ClimateRiskOpportunityRepository;
import com.tss.grc.module.sust.tcfd.entity.TcfdItem;
import com.tss.grc.module.sust.tcfd.repository.TcfdItemRepository;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TcfdItemService — S-K: principal-bound approval + separation of duties,
 * the approved-item content lock, and the official TCFD 11-recommendation
 * library seed. (Before S-K TcfdItemService had no {@code approve()} at all.)
 */
@ExtendWith(MockitoExtension.class)
class TcfdItemServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock private TcfdItemRepository repo;
    @Mock private AuditLogService audit;
    @Mock private CorrectiveActionService capaService;
    @Mock private ClimateRiskOpportunityRepository climateRiskRepo;
    @InjectMocks private TcfdItemService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ── S-K — principal-bound approval + SoD ────────────────────────

    @Test
    void approve_refusesSelfApprovalByOwner() {
        TcfdItem t = item();
        t.setDisclosureStatus("DISCLOSED");
        t.setOwnerName("Lina Al-Harbi");
        when(repo.findByIdAndOrganisationId(9L, ORG_ID)).thenReturn(Optional.of(t));

        // The owner cannot approve their own item (separation of duties).
        assertThatThrownBy(() -> service.approve(9L, user("Lina Al-Harbi")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("separation of duties");
    }

    @Test
    void approve_stampsApprovedByFromPrincipal_notBody() {
        TcfdItem t = item();
        t.setDisclosureStatus("DISCLOSED");
        t.setOwnerName("Lina Al-Harbi");
        when(repo.findByIdAndOrganisationId(9L, ORG_ID)).thenReturn(Optional.of(t));
        when(repo.save(any(TcfdItem.class))).thenAnswer(inv -> inv.getArgument(0));

        TcfdItem after = service.approve(9L, user("Khalid Al-Otaibi"));

        assertThat(after.getApprovalStatus()).isEqualTo("APPROVED");
        assertThat(after.getApprovedBy()).isEqualTo("Khalid Al-Otaibi");   // from principal
        assertThat(after.getApprovedAt()).isNotNull();
    }

    @Test
    void approve_isTerminal_cannotReapprove() {
        TcfdItem t = item();
        t.setDisclosureStatus("DISCLOSED");
        t.setApprovalStatus("APPROVED");
        when(repo.findByIdAndOrganisationId(9L, ORG_ID)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.approve(9L, user("Khalid")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already approved");
    }

    // ── S-K — approved item is content-locked ───────────────────────

    @Test
    void update_onApprovedItem_isLocked() {
        TcfdItem t = item();
        t.setApprovalStatus("APPROVED");
        when(repo.findByIdAndOrganisationId(9L, ORG_ID)).thenReturn(Optional.of(t));

        TcfdItem patch = new TcfdItem();
        patch.setTitleEn("tampered");
        assertThatThrownBy(() -> service.update(9L, patch))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("locked");
    }

    @Test
    void transition_onApprovedItem_isLocked() {
        TcfdItem t = item();
        t.setApprovalStatus("APPROVED");
        when(repo.findByIdAndOrganisationId(9L, ORG_ID)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.transition(9L, "PARTIAL"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("locked");
    }

    // ── S-K — official TCFD 11-recommendation library seed ──────────

    @Test
    void seedOfficialLibrary_seeds11_whenYearEmpty() {
        when(repo.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2099)).thenReturn(List.of());
        when(repo.save(any(TcfdItem.class))).thenAnswer(inv -> inv.getArgument(0));

        int n = service.seedOfficialLibrary((short) 2099);

        assertThat(n).isEqualTo(11);                            // the 11 TCFD recommendations
        verify(repo, times(11)).save(any(TcfdItem.class));
    }

    @Test
    void seedOfficialLibrary_isIdempotent_whenAlreadySeeded() {
        when(repo.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2025)).thenReturn(List.of(item()));

        assertThat(service.seedOfficialLibrary((short) 2025)).isEqualTo(0);
        verify(repo, never()).save(any());
    }

    // ── S-F — TCFD ↔ climate-risk link validation ───────────────────

    @Test
    void linkClimateRisk_rejectsUnknownClimateRisk() {
        when(repo.findByIdAndOrganisationId(9L, ORG_ID)).thenReturn(Optional.of(item()));
        when(climateRiskRepo.findByIdAndOrganisationId(555L, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.linkClimateRisk(9L, 555L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not exist");
        verify(repo, never()).save(any());
    }

    @Test
    void linkClimateRisk_validClimateRisk_setsPointer() {
        when(repo.findByIdAndOrganisationId(9L, ORG_ID)).thenReturn(Optional.of(item()));
        when(climateRiskRepo.findByIdAndOrganisationId(5L, ORG_ID))
            .thenReturn(Optional.of(new ClimateRiskOpportunity()));
        when(repo.save(any(TcfdItem.class))).thenAnswer(inv -> inv.getArgument(0));

        TcfdItem after = service.linkClimateRisk(9L, 5L);

        assertThat(after.getRelatedClimateRiskId()).isEqualTo(5L);
    }

    @Test
    void linkClimateRisk_null_clearsLinkWithoutLookup() {
        TcfdItem t = item();
        t.setRelatedClimateRiskId(5L);
        when(repo.findByIdAndOrganisationId(9L, ORG_ID)).thenReturn(Optional.of(t));
        when(repo.save(any(TcfdItem.class))).thenAnswer(inv -> inv.getArgument(0));

        TcfdItem after = service.linkClimateRisk(9L, null);

        assertThat(after.getRelatedClimateRiskId()).isNull();   // link cleared, no validation lookup
    }

    // ── F2-231 — the review clock, and who is allowed to claim one ───

    /**
     * A create must not claim a review date. All three guarded routes stamp {@code lastReviewedAt}
     * from the server's own clock, so accepting it from the caller let a brand-new TCFD item arrive
     * carrying the date an IFRS S2 / TCFD reader takes as "when this recommendation was last looked
     * at". The mirror, {@code CmaDisclosureService#create}, has scrubbed it since SEC-ESG-003.
     */
    @Test
    void create_refusesACallerSuppliedReviewDateAndApprovalTrio() {
        TcfdItem req = item();
        req.setId(null);
        req.setEvidenceRef("EV-1");
        req.setLastReviewedAt(LocalDateTime.of(2001, 1, 1, 0, 0));
        req.setApprovalStatus("APPROVED");
        req.setApprovedBy("Nobody Who Approved This");
        req.setApprovedAt(LocalDateTime.of(2001, 1, 2, 0, 0));
        when(repo.save(any(TcfdItem.class))).thenAnswer(i -> i.getArgument(0));

        TcfdItem saved = service.create(req);

        assertThat(saved.getLastReviewedAt())
            .as("a create files an item nobody has reviewed yet")
            .isNull();
        assertThat(saved.getApprovalStatus()).isNull();
        assertThat(saved.getApprovedBy()).isNull();
        assertThat(saved.getApprovedAt()).isNull();
    }

    /**
     * ⚠ An ECHO is not a review. Both ESG tabs send the current {@code disclosureStatus} back on
     * every save — they have to, because the entity's field initialiser makes an omitted key
     * indistinguishable from a real {@code UNDER_REVIEW} — so an unchanged value must leave the review
     * clock and the audit action alone.
     */
    @Test
    void update_echoingTheSameStatusIsNotAReview() {
        TcfdItem cur = item();
        cur.setEvidenceRef("EV-1");
        cur.setLastReviewedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        when(repo.findByIdAndOrganisationId(9L, ORG_ID)).thenReturn(Optional.of(cur));
        when(repo.save(any(TcfdItem.class))).thenAnswer(i -> i.getArgument(0));

        TcfdItem req = new TcfdItem();
        req.setTitleEn("Resilience under climate scenarios (reworded)");
        req.setDisclosureStatus("UNDER_REVIEW");          // the echo

        TcfdItem saved = service.update(9L, req);

        assertThat(saved.getLastReviewedAt())
            .as("nothing was reviewed, so the clock must not move")
            .isEqualTo(LocalDateTime.of(2026, 1, 1, 9, 0));
        verify(audit).log(org.mockito.ArgumentMatchers.eq("UPDATE"),
                          org.mockito.ArgumentMatchers.eq("TcfdItem"),
                          org.mockito.ArgumentMatchers.any(),
                          org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * F2-297 — and neither is an echo in a DIFFERENT CASE, which is where F2-231's fix carried
     * F2-179's defect. {@code sust.tcfd_item.disclosure_status} collates case-insensitively, so
     * {@code 'under_review'} IS the stored value; a bare {@code equals} read it as a CHANGE, and
     * the consequence is the unsafe direction of this family — {@code markReviewed} would stamp an
     * IFRS S2 review that never happened and the audit row would record a TRANSITION that never
     * occurred. Latent (0 non-canonical values in dev or prod), so what made it harmless was the
     * data and not the code (F2-182); the mirror in {@code CmaDisclosureService} took the same fix
     * in the same commit, because F2-231 already recorded that closing one of this pair is not
     * closing the shape.
     */
    @Test
    void update_echoingTheSameStatusInADifferentCaseIsStillNotAReview() {
        TcfdItem cur = item();
        cur.setEvidenceRef("EV-1");
        cur.setLastReviewedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        when(repo.findByIdAndOrganisationId(9L, ORG_ID)).thenReturn(Optional.of(cur));
        when(repo.save(any(TcfdItem.class))).thenAnswer(i -> i.getArgument(0));

        TcfdItem req = new TcfdItem();
        req.setTitleEn("Resilience under climate scenarios (reworded)");
        req.setDisclosureStatus("under_review");          // the same value, differently spelled

        TcfdItem saved = service.update(9L, req);

        assertThat(saved.getLastReviewedAt())
            .as("a case variant of the stored status is the stored status, so the review clock "
                + "must not move and no review may be manufactured")
            .isEqualTo(LocalDateTime.of(2026, 1, 1, 9, 0));
        verify(audit).log(org.mockito.ArgumentMatchers.eq("UPDATE"),
                          org.mockito.ArgumentMatchers.eq("TcfdItem"),
                          org.mockito.ArgumentMatchers.any(),
                          org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * ⚠⚠ A CHANGE is. {@code update} performing the classification change {@code transition} performs
     * must do what {@code transition} does — stamp the review clock through the one shared writer and
     * record a TRANSITION naming from and to. F2-188/F2-191: a duplicated transition agrees with the
     * original on the row and differs on the parts nothing asserts.
     */
    @Test
    void update_changingTheStatusStampsTheReviewClockAndLogsATransition() {
        TcfdItem cur = item();
        cur.setEvidenceRef("EV-1");
        cur.setLastReviewedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        when(repo.findByIdAndOrganisationId(9L, ORG_ID)).thenReturn(Optional.of(cur));
        when(repo.save(any(TcfdItem.class))).thenAnswer(i -> i.getArgument(0));

        TcfdItem req = new TcfdItem();
        req.setDisclosureStatus("DISCLOSED");

        TcfdItem saved = service.update(9L, req);

        assertThat(saved.getDisclosureStatus()).isEqualTo("DISCLOSED");
        assertThat(saved.getLastReviewedAt())
            .as("the classification changed, so the review clock is the server's")
            .isAfter(LocalDateTime.of(2026, 1, 1, 9, 0));
        verify(audit).log(org.mockito.ArgumentMatchers.eq("TRANSITION"),
                          org.mockito.ArgumentMatchers.eq("TcfdItem"),
                          org.mockito.ArgumentMatchers.any(),
                          org.mockito.ArgumentMatchers.contains("UNDER_REVIEW -> DISCLOSED"));
    }

    // ── Helpers ──────────────────────────────────────────────────────
    private static TcfdItem item() {
        TcfdItem t = new TcfdItem();
        t.setId(9L);
        t.setOrganisationId(ORG_ID);
        t.setReportingYear((short) 2025);
        t.setPillar("STRATEGY");
        t.setRecommendationCode("c");
        t.setTitleEn("Resilience under climate scenarios");
        t.setDisclosureStatus("UNDER_REVIEW");
        return t;
    }

    private static GrcUserDetails user(String username) {
        return new GrcUserDetails(7L, ORG_ID, username, "x", true, true, List.of());
    }
}
