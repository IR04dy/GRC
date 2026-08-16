package com.tss.grc.module.bcm_lc.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm_lc.dto.InvocationDto;
import com.tss.grc.module.bcm_lc.entity.Invocation;
import com.tss.grc.module.bcm_lc.repository.InvocationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Locks the BCM invocation state machine (V18 / NCEMA + ISO 22301):
 *   STANDBY              → DECLARED
 *   DECLARED             → INVOKED | STAND_DOWN
 *   INVOKED              → STABILISED | STAND_DOWN
 *   STABILISED           → STAND_DOWN
 *   STAND_DOWN           → POST_INCIDENT_REVIEW
 *   POST_INCIDENT_REVIEW → CLOSED
 */
@ExtendWith(MockitoExtension.class)
class InvocationServiceTest {

    private static final Long ORG_ID = 1L;
    private static final String EXT_ID = "INV-2026-001";

    @Mock private InvocationRepository repo;
    @InjectMocks private InvocationService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static Invocation invocation(String status) {
        Invocation v = new Invocation();
        v.setExternalId(EXT_ID);
        v.setOrganisationId(ORG_ID);
        v.setStatus(status);
        v.setSeverity("SEV2");
        return v;
    }

    private static InvocationDto.TransitionRequest req(String status) {
        InvocationDto.TransitionRequest r = new InvocationDto.TransitionRequest();
        r.setStatus(status);
        return r;
    }

    @Test
    void transition_STANDBY_to_DECLARED_records_declaredBy_and_timestamp() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(invocation("STANDBY")));
        when(repo.save(any(Invocation.class))).thenAnswer(inv -> inv.getArgument(0));

        // F2-224 — declaredBy is no longer a request field. declaredAt is stamped from
        // the server's clock, so the declaration IS this call and its declarer is the
        // caller. Was: r.setDeclaredBy("CISO") and an assertion that "CISO" came back.
        authAs("bcm.coordinator", 61L);
        InvocationDto.TransitionRequest r = req("DECLARED");
        InvocationDto result = service.transition(EXT_ID, r);

        assertThat(result.getStatus()).isEqualTo("DECLARED");
        assertThat(result.getDeclaredBy()).isEqualTo("bcm.coordinator");
        assertThat(result.getDeclaredAt()).isNotNull();
    }

    /** Stand-down can be reached early from DECLARED for short outages. */
    @Test
    void transition_DECLARED_directly_to_STAND_DOWN_for_short_outage_succeeds() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(invocation("DECLARED")));
        when(repo.save(any(Invocation.class))).thenAnswer(inv -> inv.getArgument(0));

        InvocationDto.TransitionRequest r = req("STAND_DOWN");
        r.setEstimatedLossSar(new BigDecimal("125000"));
        InvocationDto result = service.transition(EXT_ID, r);

        assertThat(result.getStatus()).isEqualTo("STAND_DOWN");
        assertThat(result.getStandDownAt()).isNotNull();
        assertThat(result.getEstimatedLossSar()).isEqualByComparingTo("125000");
    }

    @Test
    void transition_INVOKED_to_STABILISED_records_actual_RTO() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(invocation("INVOKED")));
        when(repo.save(any(Invocation.class))).thenAnswer(inv -> inv.getArgument(0));

        InvocationDto.TransitionRequest r = req("STABILISED");
        r.setActualRtoHours(new BigDecimal("4.5"));
        InvocationDto result = service.transition(EXT_ID, r);

        assertThat(result.getStatus()).isEqualTo("STABILISED");
        assertThat(result.getActualRtoHours()).isEqualByComparingTo("4.5");
    }

    @Test
    void transition_STANDBY_to_INVOKED_skips_DECLARED_and_rejects() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(invocation("STANDBY")));

        assertThatThrownBy(() -> service.transition(EXT_ID, req("INVOKED")))
            .isInstanceOf(IllegalTransitionException.class)
            .extracting("from", "to")
            .containsExactly("STANDBY", "INVOKED");
    }

    @Test
    void transition_CLOSED_is_terminal() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(invocation("CLOSED")));

        assertThatThrownBy(() -> service.transition(EXT_ID, req("STANDBY")))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void transition_unknownExternalId_throwsEntityNotFoundException() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, "INV-X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition("INV-X", req("DECLARED")))
            .isInstanceOf(EntityNotFoundException.class);
    }

    /**
     * F2-224 — a real principal in the SecurityContext, because the service now takes the actor
     * from {@code CurrentActor} rather than from the request. {@code CurrentActor} requires the
     * principal to be a {@link com.tss.grc.security.GrcUserDetails}, so a bare username token
     * would resolve to null and the assertion would be about nothing.
     */
    private static void authAs(String username, Long userId) {
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .setAuthentication(new org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken(
                    new com.tss.grc.security.GrcUserDetails(
                        userId, 1L, username, "x", true, true, java.util.List.of()),
                    "x", java.util.List.of()));
    }

}
