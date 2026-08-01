package com.tss.grc.module.platform.auditlog.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuditLogService — F7 builder + tenant auto-fill rule lock.
 *
 * <ul>
 *   <li>recordEvent requires actionType; severity defaults to INFO.</li>
 *   <li>Invalid severity rejected.</li>
 *   <li>Tenant id auto-filled from TenantContext; falls back to 1 (system events).</li>
 *   <li>UUID-based correlation ids generated on demand.</li>
 *   <li>Off-request (scheduler/async/event-listener): no request metadata captured,
 *       the request-scoped provider is never dereferenced.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private AuditLogRepository repo;
    @Mock private ObjectProvider<jakarta.servlet.http.HttpServletRequest> requestProvider;
    @InjectMocks private AuditLogService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    @Test void recordEvent_requiresActionType() {
        assertThatThrownBy(() -> service.recordEvent(new AuditLogService.AuditEventBuilder()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("action_type");
    }

    @Test void recordEvent_invalidSeverityRejected() {
        AuditLogService.AuditEventBuilder b = new AuditLogService.AuditEventBuilder()
            .actionType("CREATE").severity("FATAL");
        assertThatThrownBy(() -> service.recordEvent(b))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("severity");
    }

    @Test void recordEvent_defaultsSeverityToInfo() {
        when(repo.save(any())).thenAnswer(inv -> {
            AuditLogEntry e = inv.getArgument(0); e.setId(1L); return e;
        });
        AuditLogEntry saved = service.recordEvent(new AuditLogService.AuditEventBuilder()
            .actionType("CREATE").entityType("Risk").entityId(99L));
        assertThat(saved.getSeverity()).isEqualTo("INFO");
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getActionType()).isEqualTo("CREATE");
    }

    @Test void recordEvent_fallsBackToTenant1WhenNoTenantBound() {
        TenantContext.clear();
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuditLogEntry saved = service.recordEvent(new AuditLogService.AuditEventBuilder()
            .actionType("CUSTOM").message("system bootstrap"));
        assertThat(saved.getOrganisationId()).isEqualTo(1L);
    }

    @Test void log_oneLinerWorks() {
        when(repo.save(any())).thenAnswer(inv -> {
            AuditLogEntry e = inv.getArgument(0); e.setId(42L); return e;
        });
        AuditLogEntry saved = service.log("DELETE", "Control", 100L, "control retired");
        assertThat(saved.getActionType()).isEqualTo("DELETE");
        assertThat(saved.getEntityType()).isEqualTo("Control");
        assertThat(saved.getEntityId()).isEqualTo(100L);
        assertThat(saved.getMessage()).isEqualTo("control retired");
    }

    @Test void newCorrelationId_returnsUniqueValues() {
        String a = AuditLogService.newCorrelationId();
        String b = AuditLogService.newCorrelationId();
        assertThat(a).isNotBlank().hasSize(36);   // UUID format
        assertThat(b).isNotEqualTo(a);
    }

    /**
     * Regression: from a scheduler / async / event-listener thread there is no bound
     * HTTP request, so {@code RequestContextHolder.getRequestAttributes()} is null and
     * the request-scoped {@code HttpServletRequest} proxy must NOT be dereferenced —
     * touching it off-request threw "No thread-bound request found" and aborted every
     * async/scheduled audit write (the automation rule engine + SocOps / BCM /
     * compliance-deadline sweeps). The audit row is still written without request
     * metadata, and the provider is never touched (the guard short-circuits first).
     */
    @Test void recordEvent_skipsRequestMetadata_whenNoRequestBound() {
        when(repo.save(any())).thenAnswer(inv -> {
            AuditLogEntry e = inv.getArgument(0); e.setId(7L); return e;
        });

        AuditLogEntry saved = service.log("CDL_FIRE:FINDING_OVERDUE", "AuditFinding", 55L,
            "Deadline scheduler fired FINDING_OVERDUE");

        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(saved.getIpAddress()).isNull();
        assertThat(saved.getSourceEndpoint()).isNull();
        verify(requestProvider, never()).getIfAvailable();   // short-circuited off-request
    }

    // ── F2-54: user_role records a ROLE, and the row says how it knows ────────────────────────
    //
    // The old line was `auth.getAuthorities().iterator().next().getAuthority()`. Measured across
    // BOTH databases that produced 16 distinct user_role values and NOT ONE role name — 13 were
    // permissions (BCM_APPROVE alone on 1,665 dev rows) and 3 were ROLE_-prefixed. It was also
    // non-deterministic, because the authority collection is a HashSet: `admin` appeared under six
    // different values, `so_officer` under four. For a product that sells a SAMA/NCA/CMA audit
    // trail, an actor attribution that is differently wrong each time is worse than one that is
    // consistently wrong — two disagreeing rows are indistinguishable from a real role change.

    private static Authentication authWith(String username, String... authorities) {
        return new UsernamePasswordAuthenticationToken(username, "n/a",
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private static void authenticate(Authentication auth) {
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void aPermissionIsNeverRecordedAsARole() {
        // Exactly the shape that produced the defect: one role, many permissions, and the role is
        // NOT first in the collection.
        assertThat(AuditLogService.resolveRoles(authWith("so_officer",
                "EVIDENCE_READ", "EVIDENCE_WRITE", "RISK_READ", "CYBER_READ", "ROLE_CISO")))
            .isEqualTo("CISO");
    }

    @Test void theWholeRoleSetIsRecorded_notAnArbitraryMemberOfIt() {
        // Measured, not assumed: `so_officer` holds THREE roles in dev — CISO, SECURITY_ANALYST and
        // SUSTAINABILITY_OFFICER. (CLAUDE.md describes it as the CISO + SUSTAINABILITY_OFFICER
        // persona; the third grant is real and `iam.user_roles` is the authority.) The four-eyes
        // flows turn on which role acted, so recording one member of a three-element set is a lossy
        // answer to the only question an examiner asks.
        assertThat(AuditLogService.resolveRoles(authWith("so_officer",
                "ROLE_CISO", "ROLE_SUSTAINABILITY_OFFICER", "ROLE_SECURITY_ANALYST",
                "EVIDENCE_WRITE", "RISK_READ")))
            .isEqualTo("CISO,SECURITY_ANALYST,SUSTAINABILITY_OFFICER");
    }

    @Test void theRecordedValueIsDeterministic_whateverOrderTheAuthoritiesArriveIn() {
        // The real source is a HashSet, whose iteration order is stable per JVM but arbitrary per
        // element set — which is why the same principal recorded different values over time. Sorting
        // is what makes reconciliation possible at all, so it is asserted directly rather than
        // assumed from the single-run output above.
        String forward  = AuditLogService.resolveRoles(authWith("admin",
            "ROLE_PLATFORM_ADMIN", "ROLE_GRC_ADMINISTRATOR", "ADMIN_APPROVE", "IAM_DELETE"));
        String reversed = AuditLogService.resolveRoles(authWith("admin",
            "IAM_DELETE", "ADMIN_APPROVE", "ROLE_GRC_ADMINISTRATOR", "ROLE_PLATFORM_ADMIN"));

        assertThat(forward).isEqualTo(reversed)
            .isEqualTo("GRC_ADMINISTRATOR,PLATFORM_ADMIN");
    }

    @Test void anAuthenticatedPrincipalWithNoRoleAuthorityIsLabelledAsSuch_notLeftBlank() {
        // A machine/token identity carrying only permissions. "No role" is a fact worth recording;
        // a NULL beside a NULL provenance is the ambiguity F2-54 is about.
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        authenticate(authWith("splunk-svc", "CYBER_WRITE", "WORKFLOW_READ"));

        AuditLogEntry saved = service.log("IMPORT", "CyberIncident", 5L, "splunk push");

        assertThat(saved.getUserRole()).isNull();
        assertThat(saved.getUserRoleSource()).isEqualTo(AuditLogService.SRC_NO_ROLE_AUTHORITY);
    }

    @Test void aSchedulerRowSaysNoPrincipal_ratherThanSayingNothing() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuditLogEntry saved = service.log("CDL_FIRE:FINDING_OVERDUE", "AuditFinding", 55L, "sweep");

        // 3,317 of dev's 6,065 rows are this case, and before F2-54 they were indistinguishable
        // from a request whose actor the platform simply failed to capture.
        assertThat(saved.getUserRole()).isNull();
        assertThat(saved.getUserRoleSource()).isEqualTo(AuditLogService.SRC_NO_PRINCIPAL);
    }

    @Test void aResolvedRowIsLabelledResolved_andCarriesTheUsername() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        authenticate(authWith("compliance", "ROLE_GRC_ADMINISTRATOR", "COMPLIANCE_WRITE"));

        AuditLogEntry saved = service.log("UPDATE", "Control", 12L, "control updated");

        assertThat(saved.getUserRole()).isEqualTo("GRC_ADMINISTRATOR");
        assertThat(saved.getUserRoleSource()).isEqualTo(AuditLogService.SRC_RESOLVED);
        assertThat(saved.getUserUsername()).isEqualTo("compliance");
    }

    @Test void aRoleSetTooLongForTheColumnIsTruncatedVISIBLY_notSilently() {
        // user_role is NVARCHAR(60) (V95) and the platform has 58 roles, so a heavily-granted
        // principal overflows it. Silent truncation would produce a value that LOOKS like a role
        // set and is not one — so the row is labelled RESOLVED_TRUNCATED and ends in an ellipsis.
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        authenticate(authWith("over-granted",
            "ROLE_AAAAAAAAAAAAAAAAAAAA", "ROLE_BBBBBBBBBBBBBBBBBBBB",
            "ROLE_CCCCCCCCCCCCCCCCCCCC", "ROLE_DDDDDDDDDDDDDDDDDDDD"));

        AuditLogEntry saved = service.log("UPDATE", "Risk", 1L, "wide grant");

        assertThat(saved.getUserRole())
            .hasSizeLessThanOrEqualTo(60)     // the column would otherwise reject the INSERT
            .endsWith("…");
        assertThat(saved.getUserRoleSource()).isEqualTo(AuditLogService.SRC_RESOLVED_TRUNCATED);
    }

    @Test void aCallerMayDeclareSuppression_andItSurvivesTheAutoFill() {
        // The whistleblower portal withholds actor identity BY DESIGN. Before F2-54 that protection
        // was indistinguishable in the ledger from the platform failing to record an actor, so the
        // one place that suppresses on purpose looked exactly like a gap.
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        authenticate(authWith("someone", "ROLE_GRC_ADMINISTRATOR"));

        AuditLogEntry saved = service.recordEvent(new AuditLogService.AuditEventBuilder()
            .actionType("CREATE").entityType("WhistleblowerCase").entityId(3L)
            .userRoleSource(AuditLogService.SRC_SUPPRESSED));

        assertThat(saved.getUserRoleSource()).isEqualTo(AuditLogService.SRC_SUPPRESSED);
        assertThat(saved.getUserRole()).as("a declared suppression must not be overwritten").isNull();
        assertThat(saved.getUserUsername()).isNull();
    }

    @Test void provenanceIsNotAFreeTextField() {
        // The javadoc claims the value is rejected unless it is a known label. A claim a javadoc
        // makes and the code does not enforce is the defect class this whole programme keeps finding.
        AuditLogService.AuditEventBuilder b = new AuditLogService.AuditEventBuilder()
            .actionType("CREATE").userRoleSource("PROBABLY_FINE");
        assertThatThrownBy(() -> service.recordEvent(b))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("user_role_source");
    }

    @Test void theLabelSetIsPinnedHere_becauseThereIsDeliberatelyNoCheckConstraint() {
        // V339 adds NO CHECK constraint: the ck_ca_source landmine is that a CHECK over a value-set
        // the code keeps extending turns each new label into a 500, and here a rejected INSERT is a
        // LOST AUDIT ROW. This test is the substitute gate — adding a label without deciding what it
        // means (and how V339's back-fill relates to it) fails here.
        assertThat(AuditLogService.USER_ROLE_SOURCES).containsExactlyInAnyOrder(
            "RESOLVED", "RESOLVED_TRUNCATED", "NO_ROLE_AUTHORITY", "NO_PRINCIPAL",
            "SUPPRESSED_BY_DESIGN", "LEGACY_AUTHORITY_SAMPLE", "LEGACY_UNRECORDED");
        // The two LEGACY_* labels are written by V339 and by nothing else. If code ever starts
        // emitting one, it is claiming a row predates a fix that it postdates.
        assertThat(AuditLogService.SRC_LEGACY_AUTHORITY_SAMPLE).isEqualTo("LEGACY_AUTHORITY_SAMPLE");
        assertThat(AuditLogService.SRC_LEGACY_UNRECORDED).isEqualTo("LEGACY_UNRECORDED");
    }
}
