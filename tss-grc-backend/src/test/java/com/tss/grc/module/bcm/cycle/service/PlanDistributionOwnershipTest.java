package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcpPlanAcknowledgement;
import com.tss.grc.module.bcm.cycle.repository.BcpPlanAcknowledgementRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpPlanRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
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
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the plan-acknowledgement OWNERSHIP check.
 *
 * <p>The defect (found while sweeping finding F-1): a BCP plan acknowledgement is the
 * compliance artefact ISO 22301 §7.4 / SAMA BCM §6.4 expect — evidence that a NAMED
 * recipient received and accepted a specific plan version. {@code transition()} scoped
 * by organisation only, so <em>any</em> authenticated user in the tenant could
 * acknowledge or decline someone else's distribution; the row then read as that
 * recipient's acceptance, with only the audit line naming who actually clicked.
 *
 * <p>A permission could not express this: BCM_READ is granted to 17 of 57 roles while
 * plan recipients are ordinary staff, so guarding the endpoint would have locked out
 * the very people meant to use it. The control is therefore an identity check — act on
 * your own row, or hold BCM WRITE and have it recorded as acting on behalf.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanDistributionOwnershipTest {

    private static final Long ORG = 1L;
    private static final Long ACK_ID = 77L;

    @Mock BcpPlanAcknowledgementRepository acks;
    @Mock BcpPlanRepository plans;
    @Mock AuditLogService audit;

    @InjectMocks PlanDistributionService service;

    @BeforeEach
    void bindTenant() {
        TenantContext.setCurrentTenant(ORG);
        when(acks.save(any(BcpPlanAcknowledgement.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private BcpPlanAcknowledgement row(Long recipientUserId, String name, String email) {
        BcpPlanAcknowledgement a = new BcpPlanAcknowledgement();
        a.setId(ACK_ID);
        a.setOrganisationId(ORG);
        a.setPlanId(5L);
        a.setPlanVersion(3);
        a.setRecipientUserId(recipientUserId);
        a.setRecipientName(name);
        a.setRecipientEmail(email);
        a.setStatus("PENDING");
        return a;
    }

    private void given(BcpPlanAcknowledgement a) {
        when(acks.findByIdAndOrganisationId(ACK_ID, ORG)).thenReturn(Optional.of(a));
    }

    // ── the recipient themselves ────────────────────────────────────────────

    @Test
    void the_designated_recipient_may_acknowledge_by_user_id() {
        given(row(42L, "Sara Al-Otaibi", "sara@example.sa"));
        BcpPlanAcknowledgement out =
            service.acknowledge(ACK_ID, "received", "sara", 42L, false);
        assertThat(out.getStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(out.getAcknowledgedAt()).isNotNull();
    }

    @Test
    void the_designated_recipient_may_decline() {
        given(row(42L, "Sara Al-Otaibi", "sara@example.sa"));
        BcpPlanAcknowledgement out =
            service.decline(ACK_ID, "wrong scope", "sara", 42L, false);
        assertThat(out.getStatus()).isEqualTo("DECLINED");
    }

    // ── somebody else — the defect this test exists for ─────────────────────

    @Test
    void another_user_may_not_acknowledge_on_the_recipients_row() {
        given(row(42L, "Sara Al-Otaibi", "sara@example.sa"));
        assertThatThrownBy(() -> service.acknowledge(ACK_ID, "not mine", "mansour", 99L, false))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Only the designated recipient")
            .hasMessageContaining("Sara Al-Otaibi");
        verify(acks, never()).save(any());
    }

    @Test
    void another_user_may_not_decline_on_the_recipients_row() {
        given(row(42L, "Sara Al-Otaibi", "sara@example.sa"));
        assertThatThrownBy(() -> service.decline(ACK_ID, "not mine", "mansour", 99L, false))
            .isInstanceOf(AccessDeniedException.class);
        verify(acks, never()).save(any());
    }

    @Test
    void an_anonymous_or_unidentified_caller_is_refused() {
        given(row(42L, "Sara Al-Otaibi", "sara@example.sa"));
        assertThatThrownBy(() -> service.acknowledge(ACK_ID, null, null, null, false))
            .isInstanceOf(AccessDeniedException.class);
    }

    // ── BCM WRITE may act on behalf, and it must be visible in the audit ────

    @Test
    void a_bcm_write_holder_may_act_on_behalf_and_it_is_audited_as_such() {
        given(row(42L, "Sara Al-Otaibi", "sara@example.sa"));
        BcpPlanAcknowledgement out =
            service.acknowledge(ACK_ID, "phoned it in", "bcm.manager", 7L, true);
        assertThat(out.getStatus()).isEqualTo("ACKNOWLEDGED");

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("ACKNOWLEDGED"), eq("BcpPlanAcknowledgement"), anyLong(), detail.capture());
        assertThat(detail.getValue())
            .contains("bcm.manager")
            .contains("ON BEHALF OF Sara Al-Otaibi");
    }

    @Test
    void the_recipient_acting_for_themselves_is_not_marked_on_behalf() {
        given(row(42L, "Sara Al-Otaibi", "sara@example.sa"));
        service.acknowledge(ACK_ID, "mine", "sara", 42L, true);   // capability present but unused
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).log(anyString(), anyString(), anyLong(), detail.capture());
        assertThat(detail.getValue()).doesNotContain("ON BEHALF OF");
    }

    // ── legacy rows distributed before the user link was captured ───────────

    @Test
    void a_legacy_row_without_a_user_id_matches_on_recipient_name() {
        given(row(null, "Sara Al-Otaibi", "sara@example.sa"));
        assertThatCode(() -> service.acknowledge(ACK_ID, null, "sara al-otaibi", null, false))
            .doesNotThrowAnyException();   // case-insensitive
    }

    @Test
    void a_legacy_row_without_a_user_id_matches_on_recipient_email() {
        given(row(null, "Sara Al-Otaibi", "sara@example.sa"));
        assertThatCode(() -> service.acknowledge(ACK_ID, null, "SARA@example.sa", null, false))
            .doesNotThrowAnyException();
    }

    @Test
    void a_legacy_row_still_refuses_a_stranger() {
        given(row(null, "Sara Al-Otaibi", "sara@example.sa"));
        assertThatThrownBy(() -> service.acknowledge(ACK_ID, null, "mansour", null, false))
            .isInstanceOf(AccessDeniedException.class);
    }

    // ── pre-existing behaviour must survive the new check ───────────────────

    @Test
    void a_stale_distribution_is_still_refused_even_for_the_right_recipient() {
        BcpPlanAcknowledgement stale = row(42L, "Sara Al-Otaibi", "sara@example.sa");
        stale.setStatus("STALE");
        given(stale);
        assertThatThrownBy(() -> service.acknowledge(ACK_ID, null, "sara", 42L, false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("stale");
    }

    @Test
    void a_row_from_another_organisation_is_not_found() {
        when(acks.findByIdAndOrganisationId(ACK_ID, ORG)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.acknowledge(ACK_ID, null, "sara", 42L, false))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }
}
