package com.tss.grc.module.workflow.approval_chains.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.tss.grc.module.workflow.approval_chains.service.ApprovalChainInstanceService;
import com.tss.grc.module.workflow.approval_chains.service.ApprovalChainTemplateService;
import com.tss.grc.security.GrcUserDetails;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * F2-450 — the set the controller hands the service must be ROLES, not every authority.
 *
 * <p>The gate {@code ActingRoleSetContractTest} asserts the SHAPE of the expression; this asserts
 * what actually reaches {@code ApprovalChainInstanceService}, which is the thing that decides who
 * may sign an approval step. F2-431's rule: assert what the call site passes, because the two
 * halves can come apart without either looking wrong on its own.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalChainActingRolesTest {

    @Mock private ApprovalChainTemplateService templateService;
    @Mock private ApprovalChainInstanceService instanceService;
    @InjectMocks private ApprovalChainController controller;

    @Captor private ArgumentCaptor<Set<String>> roles;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** A principal holding one real role plus the permission authorities the platform grants. */
    private static void authenticate() {
        GrcUserDetails details = new GrcUserDetails(
            42L, 1L, "someone", "x", true, true, List.of(
                new SimpleGrantedAuthority("ROLE_RISK_MANAGER"),
                // CustomUserDetailsService grants each permission name VERBATIM — 144 of them in
                // dev. These are the strings that used to land in the "roles" set.
                new SimpleGrantedAuthority("WORKFLOW_APPROVE"),
                new SimpleGrantedAuthority("ADMIN_APPROVE"),
                new SimpleGrantedAuthority("BCM_EXPORT")));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(details, "n/a", details.getAuthorities()));
    }

    @Test
    @DisplayName("F2-450 — only ROLE_-derived names reach the approval-step matcher")
    void onlyRoleDerivedNamesReachTheMatcher() {
        authenticate();

        controller.pendingForMe();

        verify(instanceService).pendingForUser(eq(42L), roles.capture());
        assertThat(roles.getValue())
            .as("""
                This set is the input to matchesActor, i.e. to WHO MAY SIGN AN APPROVAL STEP. It
                used to be built as `name.startsWith("ROLE_") ? name.substring(5) : name`, which
                KEPT every non-prefixed authority — so it carried every permission the principal
                held. A step's required_role is free text a tenant admin types, and permission
                names look exactly like the role names this platform already ships (DLP_INGEST,
                SANCTIONS_CHECK).""")
            .containsExactly("RISK_MANAGER");
        assertThat(roles.getValue())
            .as("the permission strings must not be in it, and WORKFLOW_APPROVE is the sharpest — "
                + "it is the very permission that opens the action route")
            .doesNotContain("WORKFLOW_APPROVE", "ADMIN_APPROVE", "BCM_EXPORT");
    }

    @Test
    @DisplayName("F2-450 — the set is upper-cased, so the inbox and the action path agree")
    void theSetIsUpperCasedSoBothReadersAgree() {
        GrcUserDetails details = new GrcUserDetails(
            7L, 1L, "someone", "x", true, true,
            List.of(new SimpleGrantedAuthority("ROLE_risk_manager")));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(details, "n/a", details.getAuthorities()));

        controller.pendingForMe();

        verify(instanceService).pendingForUser(eq(7L), roles.capture());
        assertThat(roles.getValue())
            .as("""
                matchesActor's parameter is named rolesUpper and only ONE of its two callers used
                to supply one — pendingForUser built it, recordAction passed the raw set. Every
                role name is upper-case in both databases, so the divergence was latent, but its
                failure is asymmetric: a lower-case role name would make pendingForUser list a
                step in the user's inbox that recordAction then refuses with 403.""")
            .containsExactly("RISK_MANAGER");
    }

    @Test
    @DisplayName("F2-450 — no authentication yields no roles rather than throwing")
    void noAuthenticationYieldsNoRoles() {
        SecurityContextHolder.clearContext();

        controller.pendingForMe();

        verify(instanceService).pendingForUser(any(), roles.capture());
        assertThat(roles.getValue())
            .as("an unauthenticated call must not blow up building the set; the route's own "
                + "isAuthenticated() guard is what refuses it")
            .isEmpty();
    }
}
