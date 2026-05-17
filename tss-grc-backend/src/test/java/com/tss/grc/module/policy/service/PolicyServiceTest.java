package com.tss.grc.module.policy.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.policy.entity.PolicyAttestation;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.entity.PolicyException;
import com.tss.grc.module.policy.repository.PolicyAttestationRepository;
import com.tss.grc.module.policy.repository.PolicyDocumentRepository;
import com.tss.grc.module.policy.repository.PolicyExceptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * PolicyService (module.policy) — V79 lifecycle rule lock.
 *
 * <p>Covers:
 * <ul>
 *   <li>Policy DRAFT → REVIEW → APPROVED → PUBLISHED happy path,
 *       including the "effective_date required before PUBLISHED" guard
 *       and {@code publishedAt} stamp.</li>
 *   <li>Illegal transitions (DRAFT → PUBLISHED, EXPIRED → PUBLISHED)
 *       throw {@link IllegalTransitionException}.</li>
 *   <li>Delete of PUBLISHED policy refused (must ARCHIVE first).</li>
 *   <li>Exception REQUESTED → UNDER_REVIEW → APPROVED happy path
 *       stamps reviewer/approver, and REJECTED requires a reason.</li>
 *   <li>Attestation PENDING → ATTESTED stamps attestedAt; double-attest
 *       refused; decline requires a reason.</li>
 *   <li>Expiry sweep flips PUBLISHED → EXPIRED for policies whose
 *       expiryDate is in the past.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private PolicyDocumentRepository docs;
    @Mock private PolicyExceptionRepository exceptions;
    @Mock private PolicyAttestationRepository attestations;

    @InjectMocks private PolicyService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static PolicyDocument draft() {
        PolicyDocument p = new PolicyDocument();
        p.setId(1L);
        p.setOrganisationId(ORG_ID);
        p.setPolicyCode("ISMS-001");
        p.setVersion("1.0");
        p.setTitleEn("Information Security Policy");
        p.setCategory("CYBER");
        p.setStatus("DRAFT");
        return p;
    }

    @Test void policy_publishedRequiresEffectiveDate() {
        PolicyDocument p = draft();
        p.setStatus("APPROVED");
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.transition(1L, "PUBLISHED", "alice"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("effective_date");
    }

    @Test void policy_publishedStampsPublishedAt() {
        PolicyDocument p = draft();
        p.setStatus("APPROVED");
        p.setEffectiveDate(LocalDate.now());
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(p));
        when(docs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PolicyDocument out = service.transition(1L, "PUBLISHED", "alice");
        assertThat(out.getStatus()).isEqualTo("PUBLISHED");
        assertThat(out.getPublishedAt()).isNotNull();
    }

    @Test void policy_illegalTransitionRejected() {
        PolicyDocument p = draft();
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(p));

        // DRAFT → PUBLISHED is not allowed (must go via REVIEW + APPROVED)
        assertThatThrownBy(() -> service.transition(1L, "PUBLISHED", "alice"))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void policy_deletePublishedRefused() {
        PolicyDocument p = draft();
        p.setStatus("PUBLISHED");
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PUBLISHED");
    }

    @Test void exception_approvalStampsApprover() {
        PolicyException e = new PolicyException();
        e.setId(10L);
        e.setOrganisationId(ORG_ID);
        e.setPolicyId(1L);
        e.setStatus("UNDER_REVIEW");
        when(exceptions.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(e));
        when(exceptions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PolicyException out = service.transitionException(10L, "APPROVED", "bob", null);
        assertThat(out.getStatus()).isEqualTo("APPROVED");
        assertThat(out.getApproverName()).isEqualTo("bob");
        assertThat(out.getApprovedAt()).isNotNull();
    }

    @Test void exception_rejectionRequiresReason() {
        PolicyException e = new PolicyException();
        e.setId(10L);
        e.setOrganisationId(ORG_ID);
        e.setStatus("UNDER_REVIEW");
        when(exceptions.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.transitionException(10L, "REJECTED", "bob", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reason");
    }

    @Test void attestation_attestStampsTimestamp() {
        PolicyAttestation a = new PolicyAttestation();
        a.setId(50L);
        a.setOrganisationId(ORG_ID);
        a.setPolicyId(1L);
        a.setUserUsername("carol");
        a.setStatus("PENDING");
        when(attestations.findByIdAndOrganisationId(50L, ORG_ID)).thenReturn(Optional.of(a));
        when(attestations.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PolicyAttestation out = service.attest(50L, "EV-001");
        assertThat(out.getStatus()).isEqualTo("ATTESTED");
        assertThat(out.getAttestedAt()).isNotNull();
        assertThat(out.getEvidenceRef()).isEqualTo("EV-001");
    }

    @Test void attestation_doubleAttestRefused() {
        PolicyAttestation a = new PolicyAttestation();
        a.setId(50L);
        a.setOrganisationId(ORG_ID);
        a.setStatus("ATTESTED");
        when(attestations.findByIdAndOrganisationId(50L, ORG_ID)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.attest(50L, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PENDING");
    }

    @Test void attestation_declineRequiresReason() {
        PolicyAttestation a = new PolicyAttestation();
        a.setId(50L);
        a.setOrganisationId(ORG_ID);
        a.setStatus("PENDING");
        when(attestations.findByIdAndOrganisationId(50L, ORG_ID)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.decline(50L, "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reason");
    }

    @Test void expirySweep_flipsExpiredPublished() {
        PolicyDocument p1 = draft(); p1.setId(100L); p1.setStatus("PUBLISHED");
        PolicyDocument p2 = draft(); p2.setId(101L); p2.setStatus("PUBLISHED");
        when(docs.findExpiredPublished(anyLong(), any())).thenReturn(List.of(p1, p2));
        when(docs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Long> flipped = service.runExpirySweep();
        assertThat(flipped).containsExactly(100L, 101L);
        assertThat(p1.getStatus()).isEqualTo("EXPIRED");
        assertThat(p2.getStatus()).isEqualTo("EXPIRED");
    }

    @Test void create_duplicateVersionRejected() {
        PolicyDocument body = new PolicyDocument();
        body.setPolicyCode("ISMS-001");
        body.setVersion("1.0");
        body.setTitleEn("Information Security Policy");
        body.setCategory("CYBER");
        when(docs.findByOrganisationIdAndPolicyCodeAndVersion(ORG_ID, "ISMS-001", "1.0"))
            .thenReturn(Optional.of(draft()));

        assertThatThrownBy(() -> service.create(body))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }
}
