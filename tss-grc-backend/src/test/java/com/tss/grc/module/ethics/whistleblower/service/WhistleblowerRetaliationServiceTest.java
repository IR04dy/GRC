package com.tss.grc.module.ethics.whistleblower.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.RetaliationComplaintRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.RetaliationComplaintResponse;
import com.tss.grc.module.ethics.whistleblower.entity.WhistleblowerCase;
import com.tss.grc.module.ethics.whistleblower.entity.WhistleblowerRetaliationComplaint;
import com.tss.grc.module.ethics.whistleblower.repository.WhistleblowerCaseRepository;
import com.tss.grc.module.ethics.whistleblower.repository.WhistleblowerRetaliationComplaintRepository;
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
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link WhistleblowerRetaliationService} — Sprint 13.
 *
 * <p>State machine: RECEIVED → VALIDATED → UPHELD | DISMISSED.
 */
@ExtendWith(MockitoExtension.class)
class WhistleblowerRetaliationServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private WhistleblowerRetaliationComplaintRepository repo;
    @Mock private WhistleblowerCaseRepository caseRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    @InjectMocks private WhistleblowerRetaliationService service;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static WhistleblowerRetaliationComplaint existing(Long id, String status) {
        WhistleblowerRetaliationComplaint r = new WhistleblowerRetaliationComplaint();
        r.setId(id);
        r.setOrganisationId(ORG_ID);
        r.setRelatedCaseId(101L);
        r.setClaimantName("Sara Al-Otaibi");
        r.setClaimedActor("Operations Department supervisor");
        r.setClaimedAction("Punitive reassignment after raising the original report.");
        r.setStatus(status);
        return r;
    }

    private static WhistleblowerCase parentCase() {
        WhistleblowerCase c = new WhistleblowerCase();
        c.setId(101L);
        c.setOrganisationId(ORG_ID);
        c.setStatus("UNDER_INVESTIGATION");
        return c;
    }

    @Test
    void claim_persistsAndEmitsClaimedEvent() {
        when(caseRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L))
                .thenReturn(Optional.of(parentCase()));
        when(repo.save(any(WhistleblowerRetaliationComplaint.class))).thenAnswer(inv -> {
            WhistleblowerRetaliationComplaint r = inv.getArgument(0);
            r.setId(701L);
            return r;
        });

        RetaliationComplaintResponse out = service.claim(new RetaliationComplaintRequest(
                101L, 42L, null,
                "Operations Department supervisor",
                "Punitive reassignment after raising the original report.",
                LocalDate.now().minusDays(7)
        ));

        assertThat(out.id()).isEqualTo(701L);
        assertThat(out.status()).isEqualTo("RECEIVED");
        assertThat(out.relatedCaseId()).isEqualTo(101L);

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("WHISTLEBLOWER_RETALIATION_CLAIMED");
    }

    @Test
    void claim_relatedCaseInWrongTenant_throws() {
        when(caseRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 999L))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.claim(new RetaliationComplaintRequest(
                999L, null, "Anon claimant",
                "Manager",
                "Action description here.", null)))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
                .hasMessageContaining("Related whistleblower case not found");
        verify(repo, never()).save(any());
    }

    @Test
    void validate_movesReceivedToValidated() {
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 702L))
                .thenReturn(Optional.of(existing(702L, "RECEIVED")));
        when(repo.save(any(WhistleblowerRetaliationComplaint.class))).thenAnswer(inv -> inv.getArgument(0));

        RetaliationComplaintResponse out = service.validate(702L, 55L);

        assertThat(out.status()).isEqualTo("VALIDATED");
        assertThat(out.assignedProtectorUserId()).isEqualTo(55L);

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("WHISTLEBLOWER_RETALIATION_VALIDATED");
    }

    @Test
    void uphold_movesValidatedToUpheld() {
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 703L))
                .thenReturn(Optional.of(existing(703L, "VALIDATED")));
        when(repo.save(any(WhistleblowerRetaliationComplaint.class))).thenAnswer(inv -> inv.getArgument(0));

        RetaliationComplaintResponse out = service.uphold(703L, 55L);

        assertThat(out.status()).isEqualTo("UPHELD");

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("WHISTLEBLOWER_RETALIATION_UPHELD");
    }

    @Test
    void dismiss_movesReceivedDirectlyToDismissed() {
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 704L))
                .thenReturn(Optional.of(existing(704L, "RECEIVED")));
        when(repo.save(any(WhistleblowerRetaliationComplaint.class))).thenAnswer(inv -> inv.getArgument(0));

        RetaliationComplaintResponse out = service.dismiss(704L, 55L);

        assertThat(out.status()).isEqualTo("DISMISSED");
    }

    @Test
    void uphold_blockedFromReceivedDirectly() {
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 705L))
                .thenReturn(Optional.of(existing(705L, "RECEIVED")));
        // RECEIVED → UPHELD is not allowed; must validate first.
        assertThatThrownBy(() -> service.uphold(705L, 55L))
                .isInstanceOf(IllegalTransitionException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void uphold_blockedFromTerminalDismissed() {
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 706L))
                .thenReturn(Optional.of(existing(706L, "DISMISSED")));
        assertThatThrownBy(() -> service.uphold(706L, 55L))
                .isInstanceOf(IllegalTransitionException.class);
    }
}
