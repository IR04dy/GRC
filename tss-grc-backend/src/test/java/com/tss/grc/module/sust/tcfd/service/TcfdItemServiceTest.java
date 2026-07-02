package com.tss.grc.module.sust.tcfd.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.capa.service.CorrectiveActionService;
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
