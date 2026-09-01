package com.tss.grc.module.policy.intake;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.intake.spi.IntakePromotionContext;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.entity.PolicyException;
import com.tss.grc.module.policy.repository.PolicyDocumentRepository;
import com.tss.grc.module.policy.service.PolicyManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-419 — the adapter's first test. It had none, which is not the same as a path somebody decided
 * was safe (F2-248), and the thing it got wrong is the one thing it does that needs a decision:
 * <b>which version of the cited policy the exception hangs off.</b>
 *
 * <p>It resolved that with {@code Comparator.comparing(p -> nullSafe(p.getVersion()))} — a lexical
 * sort key — so a policy at {@code "1.10"} lost to the same policy at {@code "1.9"} and the
 * exception was filed against a superseded version, on a path whose own error message says
 * <em>"An exception must hang off the policy it excepts."</em>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("F2-419 — a promoted exception hangs off the NEWEST version of its policy")
class PolicyExceptionPromotionAdapterTest {

    private static final Long ORG = 1L;

    @Mock private PolicyManagementService policies;
    @Mock private PolicyDocumentRepository documents;
    @InjectMocks private PolicyExceptionPromotionAdapter adapter;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    /**
     * The discriminating leg. {@code "1.10"} is the newest version and sorts <em>below</em>
     * {@code "1.9"} as text, so a lexical pick returns the superseded document — and it needs no
     * exotic version string at all, just a policy on its tenth minor revision.
     */
    @Test
    @DisplayName("1.10 beats 1.9 — a policy on its tenth minor revision")
    void theExceptionHangsOffTheNumericallyNewestVersion() {
        when(documents.findByOrganisationIdOrderByPolicyCodeAscVersionDesc(ORG))
            .thenReturn(List.of(doc(11L, "POL-INFO-001", "1.9"),
                                doc(12L, "POL-INFO-001", "1.10"),
                                doc(13L, "POL-HR-002",   "3.0")));
        when(policies.createException(any(PolicyException.class)))
            .thenAnswer(inv -> { PolicyException e = inv.getArgument(0); e.setId(900L); return e; });

        adapter.promote(ctx("POL-INFO-001"));

        ArgumentCaptor<PolicyException> captor = ArgumentCaptor.forClass(PolicyException.class);
        verify(policies).createException(captor.capture());
        assertThat(captor.getValue().getPolicyId())
            .as("the newest version of POL-INFO-001 is 1.10 (id 12). A lexical sort key returns "
              + "1.9 (id 11), because \"1.10\" < \"1.9\" as text — so the exception would be "
              + "filed against a superseded policy")
            .isEqualTo(12L);
    }

    /** PM-H2's own worked example, on the path it never reached. */
    @Test
    @DisplayName("10.0 beats 9.0 — PM-H2's worked example")
    void theTwoDigitMajorVersionCaseAlsoResolvesNumerically() {
        when(documents.findByOrganisationIdOrderByPolicyCodeAscVersionDesc(ORG))
            .thenReturn(List.of(doc(21L, "POL-SEC-009", "9.0"),
                                doc(22L, "POL-SEC-009", "10.0")));
        when(policies.createException(any(PolicyException.class)))
            .thenAnswer(inv -> { PolicyException e = inv.getArgument(0); e.setId(901L); return e; });

        adapter.promote(ctx("POL-SEC-009"));

        ArgumentCaptor<PolicyException> captor = ArgumentCaptor.forClass(PolicyException.class);
        verify(policies).createException(captor.capture());
        assertThat(captor.getValue().getPolicyId()).isEqualTo(22L);
    }

    /**
     * The matching rule is deliberately case- and whitespace-insensitive because the code comes
     * from a human-typed intake submission — which is exactly why the <em>operation</em> was not
     * shared with {@code newVersion} (whose match is {@code equals}) and only the comparator was
     * (F2-190).
     */
    @Test
    @DisplayName("the cited code is matched case- and whitespace-insensitively")
    void theCitedCodeIsMatchedTolerantlyBecauseAHumanTypedIt() {
        when(documents.findByOrganisationIdOrderByPolicyCodeAscVersionDesc(ORG))
            .thenReturn(List.of(doc(31L, "POL-INFO-001", "2.0")));
        when(policies.createException(any(PolicyException.class)))
            .thenAnswer(inv -> { PolicyException e = inv.getArgument(0); e.setId(902L); return e; });

        adapter.promote(ctx("  pol-info-001 "));

        ArgumentCaptor<PolicyException> captor = ArgumentCaptor.forClass(PolicyException.class);
        verify(policies).createException(captor.capture());
        assertThat(captor.getValue().getPolicyId()).isEqualTo(31L);
    }

    /**
     * The refusal is the adapter's whole reason for resolving a policy at all, so it is asserted
     * rather than assumed — a fix to the version pick must not quietly make an unknown code
     * promote to nothing.
     */
    @Test
    @DisplayName("an unknown policy code is refused, naming the code")
    void anUnknownPolicyCodeIsRefusedNamingIt() {
        when(documents.findByOrganisationIdOrderByPolicyCodeAscVersionDesc(ORG))
            .thenReturn(List.of(doc(41L, "POL-HR-002", "1.0")));

        assertThatThrownBy(() -> adapter.promote(ctx("POL-NOPE-999")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("POL-NOPE-999")
            .hasMessageContaining("must hang off the policy it excepts");

        verify(policies, never()).createException(any());
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────

    private static PolicyDocument doc(Long id, String code, String version) {
        PolicyDocument p = new PolicyDocument();
        p.setId(id);
        p.setOrganisationId(ORG);
        p.setPolicyCode(code);
        p.setVersion(version);
        p.setTitleEn("Information Security Policy");
        return p;
    }

    private static IntakePromotionContext ctx(String policyCode) {
        Map<String, Object> payload = Map.of(
            "policyCode", policyCode,
            "exceptionScope", "Legacy payment gateway cannot enforce TLS 1.3",
            "businessJustification", "Vendor upgrade scheduled for Q2");
        return new IntakePromotionContext(
            41L, "PX-A1B2-C3D4", "POLICY_EXCEPTION", "MEDIUM", "Rana", "rana@example.sa",
            false, "en", LocalDateTime.of(2026, 9, 1, 9, 30), null, "compliance",
            payload, "Request a policy exception", "طلب استثناء من سياسة");
    }
}
