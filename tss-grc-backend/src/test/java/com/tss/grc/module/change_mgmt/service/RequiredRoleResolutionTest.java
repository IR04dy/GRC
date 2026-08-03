package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.entity.ChangeApproval;
import com.tss.grc.module.change_mgmt.entity.ChangeAuthorisationMatrix;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.repository.ChangeApprovalRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeAuthorisationMatrixRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.platform.service.RoleDirectoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * F2-95 — {@code required_roles} was configured on 33 of 36 matrix rows and applied nowhere,
 * while the API echoed it back beside a {@code satisfied} flag computed from vote counts alone.
 *
 * <p>The interesting half is that "not satisfied" is <b>three</b> outcomes, not one. Measured
 * 2026-08-03, the same in dev and production: {@code CISO} has one active holder and is
 * checkable; {@code CHANGE_MANAGER} — required on all 33 rows — is a real role that nobody in
 * the tenant holds; {@code CTO} is not a role at all. Collapsing those would send an
 * administrator to grant a role that cannot be granted, and is why a literal gate would have
 * refused <b>9 of 11</b> already-decided changes in dev and 8 of 10 in prod.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequiredRoleResolutionTest {

    private static final Long ORG = 1L;
    private static final Long CHANGE_ID = 500L;

    @Mock private ChangeRequestRepository changeRepo;
    @Mock private ChangeApprovalRepository approvalRepo;
    @Mock private ChangeAuthorisationMatrixRepository matrixRepo;
    @Mock private RoleDirectoryService roleDirectory;

    private ChangeManagementService service;

    @BeforeEach
    void wire() {
        service = new ChangeManagementService(changeRepo, approvalRepo, null, null, null,
            null, null);
        ReflectionTestUtils.setField(service, "authMatrixRepo", matrixRepo);
        ReflectionTestUtils.setField(service, "roleDirectory", roleDirectory);

        ChangeRequest c = new ChangeRequest();
        c.setId(CHANGE_ID);
        c.setOrganisationId(ORG);
        c.setChangeType("NORMAL");
        c.setRiskLevel("CRITICAL");
        c.setStatus("CAB_PENDING");
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(c));
        // require() filters on the thread-local tenant, so it must be set or every lookup 404s.
        TenantContext.setCurrentTenant(ORG);

        // The tenant's real picture, as measured: CISO checkable, CHANGE_MANAGER unheld,
        // CTO absent from the catalogue entirely.
        when(roleDirectory.enforceabilityByRole(ORG)).thenReturn(Map.of(
            "CISO", RoleDirectoryService.ENFORCEABLE,
            "CHANGE_MANAGER", RoleDirectoryService.NO_ACTIVE_HOLDER));
        when(roleDirectory.enforceability(any(), any())).thenCallRealMethod();
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    /** Casting the payload value to {@code List<?>} makes AssertJ's varargs matchers reject a
     *  String; the payload is genuinely {@code List<String>}, so name that once here. */
    @SuppressWarnings("unchecked")
    private static List<String> roles(Map<String, Object> payload, String key) {
        return (List<String>) payload.get(key);
    }

    private void matrixRequires(String csv) {
        ChangeAuthorisationMatrix m = new ChangeAuthorisationMatrix();
        m.setOrganisationId(ORG);
        m.setChangeType("NORMAL");
        m.setRiskLevel("CRITICAL");
        m.setGate("CAB");
        m.setMinApprovals(1);
        m.setRequiredRoles(csv);
        when(matrixRepo.findByOrganisationIdAndChangeTypeAndRiskLevel(
            ORG, "NORMAL", "CRITICAL")).thenReturn(Optional.of(m));
    }

    private void approvedBy(Long userId, String name, String... platformRoles) {
        ChangeApproval a = new ChangeApproval();
        a.setChangeRequestId(CHANGE_ID);
        a.setApproverId(userId);
        a.setApproverName(name);
        a.setApproverRole("Member — Cybersecurity");   // free text; deliberately not a role name
        a.setDecision("APPROVE");
        when(approvalRepo.findByChangeRequestIdOrderByDecidedAtAsc(CHANGE_ID)).thenReturn(List.of(a));
        when(roleDirectory.rolesOf(userId)).thenReturn(Set.of(platformRoles));
    }

    @Test
    void aRequiredRoleIsSatisfiedOnlyByAnApproverWhoActuallyHoldsIt() {
        matrixRequires("CISO");
        approvedBy(7L, "so_officer", "CISO", "SECURITY_ANALYST");

        List<ChangeManagementService.RequiredRoleState> states = service.requiredRoleStates(CHANGE_ID);

        assertThat(states).hasSize(1);
        assertThat(states.get(0).status()).isEqualTo(ChangeManagementService.ROLE_SATISFIED);
        assertThat(states.get(0).satisfiedBy()).isEqualTo("so_officer");
    }

    /**
     * The seed's own data makes this the realistic case: the approver <em>named</em> "Chief
     * Information Security Officer", whose free-text capacity reads "Member — Cybersecurity",
     * holds the platform role {@code RISK_MANAGER}. Title, capacity and role are three
     * different things and only the third can be checked.
     */
    @Test
    void aTitleThatLooksLikeTheRoleDoesNotSatisfyIt() {
        matrixRequires("CISO");
        approvedBy(9L, "Chief Information Security Officer", "RISK_MANAGER");

        List<ChangeManagementService.RequiredRoleState> states = service.requiredRoleStates(CHANGE_ID);

        assertThat(states.get(0).status())
            .as("CISO is checkable in this tenant and no approver holds it — a real gap")
            .isEqualTo(ChangeManagementService.ROLE_MISSING);
        assertThat(states.get(0).satisfiedBy()).isNull();
    }

    @Test
    void theThreeWaysOfNotBeingSatisfiedStayDistinctBecauseTheRemediesDiffer() {
        matrixRequires("CHANGE_MANAGER,CISO,CTO");
        approvedBy(9L, "Chief Information Security Officer", "RISK_MANAGER");

        Map<String, String> byRole = service.requiredRoleStates(CHANGE_ID).stream()
            .collect(java.util.stream.Collectors.toMap(
                ChangeManagementService.RequiredRoleState::role,
                ChangeManagementService.RequiredRoleState::status));

        assertThat(byRole).containsEntry("CISO", ChangeManagementService.ROLE_MISSING);
        assertThat(byRole)
            .as("a real role nobody holds cannot be satisfied by any approval — telling the "
              + "administrator to 'get a CHANGE_MANAGER to approve' is a dead end")
            .containsEntry("CHANGE_MANAGER", RoleDirectoryService.NO_ACTIVE_HOLDER);
        assertThat(byRole)
            .as("CTO is not a role at all (F2-52's ghost shape) — granting is impossible")
            .containsEntry("CTO", RoleDirectoryService.NOT_A_ROLE);
    }

    @Test
    void satisfiedMustNeverReadTrueWhileAConfiguredRoleRequirementIsUnmet() {
        matrixRequires("CISO");
        approvedBy(9L, "Chief Information Security Officer", "RISK_MANAGER");

        Map<String, Object> out = service.resolveAuthorisation(CHANGE_ID);

        assertThat(out.get("approvalsSatisfied"))
            .as("the vote count IS met — which is exactly why the old single flag read true")
            .isEqualTo(true);
        assertThat(out.get("rolesSatisfied")).isEqualTo(false);
        assertThat(out.get("satisfied"))
            .as("the whole of F2-95: a reviewer reading requiredRoles:CISO beside satisfied:true "
              + "concludes a CISO signed off")
            .isEqualTo(false);
        assertThat(roles(out, "roleGaps")).containsExactly("CISO");
        assertThat(String.valueOf(out.get("roleRemedy"))).contains("APPROVE from a holder of CISO");
    }

    @Test
    void theRemedyNamesTheRightActionAndRulesOutTheImpossibleOnes() {
        matrixRequires("CHANGE_MANAGER,CTO");
        approvedBy(9L, "someone", "RISK_MANAGER");

        Map<String, Object> out = service.resolveAuthorisation(CHANGE_ID);
        String remedy = String.valueOf(out.get("roleRemedy"));

        assertThat(out.get("rolesSatisfied")).isEqualTo(false);
        assertThat(roles(out, "roleGaps")).isEmpty();
        assertThat(roles(out, "unverifiableRoles"))
            .containsExactlyInAnyOrder("CHANGE_MANAGER", "CTO");
        assertThat(remedy)
            .contains("no active user in this tenant holds it")
            .contains("is not a role in this tenant at all");
    }

    @Test
    void aMatrixRowWithNoRequiredRolesIsUnchanged() {
        matrixRequires("   ");
        approvedBy(9L, "someone", "RISK_MANAGER");

        Map<String, Object> out = service.resolveAuthorisation(CHANGE_ID);

        assertThat(service.requiredRoleStates(CHANGE_ID)).isEmpty();
        assertThat(out.get("rolesSatisfied")).isEqualTo(true);
        assertThat(out.get("satisfied"))
            .as("no configured requirement means the vote count is the whole answer, as before")
            .isEqualTo(true);
        assertThat(out.get("roleRemedy")).isNull();
    }

    // ── S6b-ii: the gate. Only MISSING blocks, and that is the decision. ──────────────

    @Test
    void theGateRefusesWhenAnEnforceableRoleWasNotHeldByAnyApprover() {
        matrixRequires("CISO");
        approvedBy(9L, "Chief Information Security Officer", "RISK_MANAGER");

        assertThatThrownBy(() -> service.requireRequiredRolesPresent(CHANGE_ID))
            .isInstanceOf(IllegalStateException.class)
            .as("409, same shape as the vote-count and REJECT-on-record gates beside it")
            .hasMessageContaining("requires an APPROVE from a holder of CISO")
            .hasMessageContaining("amend the authorisation matrix");
    }

    /**
     * The measurement that shaped the whole design. A literal gate — one refusing whenever any
     * required role was unsatisfied — would have refused <b>9 of 11</b> already-decided changes in
     * dev and <b>8 of 10</b> in production, because {@code CHANGE_MANAGER} is required on all 33
     * configured rows and no active user in either database holds it. Refusing on that leaves the
     * approver no action that could ever succeed, which is F2-92's shape.
     */
    @Test
    void theGateDoesNotRefuseForARoleNobodyCouldEverSatisfy() {
        matrixRequires("CHANGE_MANAGER");        // real role, zero active holders
        approvedBy(9L, "someone", "RISK_MANAGER");

        assertThatCode(() -> service.requireRequiredRolesPresent(CHANGE_ID))
            .as("a control whose first act refuses every approval, with no action available to the "
              + "approver, is the defect and not the feature")
            .doesNotThrowAnyException();
    }

    @Test
    void theGateDoesNotRefuseForANameThatIsNotARoleAtAll() {
        // V347 removed the only seeded NOT_A_ROLE (`CTO`), but a tenant could still configure one,
        // and the gate must report rather than block — granting is impossible either way.
        matrixRequires("CTO");
        approvedBy(9L, "someone", "RISK_MANAGER");

        assertThatCode(() -> service.requireRequiredRolesPresent(CHANGE_ID))
            .doesNotThrowAnyException();
    }

    @Test
    void theGatePassesOnceAHolderHasApproved() {
        matrixRequires("CHANGE_MANAGER,CISO");   // one unenforceable, one enforceable and satisfied
        approvedBy(7L, "so_officer", "CISO");

        assertThatCode(() -> service.requireRequiredRolesPresent(CHANGE_ID))
            .as("the enforceable half is met; the unenforceable half must not hold the decision up")
            .doesNotThrowAnyException();
    }

    /**
     * Fail-safe direction. With no directory wired the platform cannot resolve anything, and the
     * one thing it must not do is claim the requirement is met — that is the defect.
     */
    @Test
    void withNoRoleDirectoryTheRequirementReadsUnverifiableRatherThanSatisfied() {
        matrixRequires("CISO");
        approvedBy(7L, "so_officer", "CISO");
        ReflectionTestUtils.setField(service, "roleDirectory", null);

        Map<String, Object> out = service.resolveAuthorisation(CHANGE_ID);

        assertThat(out.get("satisfied")).isEqualTo(false);
        assertThat(roles(out, "unverifiableRoles")).containsExactly("CISO");
    }
}
