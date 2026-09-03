package com.tss.grc.module.policy.service;

import com.tss.grc.common.exception.DomainAccessDeniedException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.policy.entity.PolicyAttestation;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.repository.PolicyAttestationRepository;
import com.tss.grc.module.policy.repository.PolicyDocumentControlLinkRepository;
import com.tss.grc.module.policy.repository.PolicyDocumentRepository;
import com.tss.grc.module.policy.repository.PolicyExceptionRepository;
import com.tss.grc.module.attachment.repository.AttachmentRepository;
import com.tss.grc.module.compliance.repository.ControlRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * F2-219 — an attestation may be ASSIGNED by anyone entitled to the register, and COMPLETED only
 * by the person it names.
 *
 * <p>{@code attest()} carries SEC-CMP-004: object-level authorization, in its own words <i>"only
 * the assignee (or an admin override) may complete an attestation-of-record"</i>. It also gates on
 * PENDING, stamps {@code attestedAt} from the clock, records the acting principal in
 * {@code updatedBy} <i>"for non-repudiation"</i>, writes an ATTEST audit row and emits
 * POLICY_ATTESTED.
 *
 * <p>{@code createAttestation} defaulted the status instead of forcing it, so all of that was
 * optional. Probed live 2026-08-16 as {@code ranalyst} — who holds {@code COMPLIANCE:READ} and
 * nothing else, the permission both endpoints are guarded by, held by 32 roles:
 *
 * <pre>
 *   POST /api/policy/attestations
 *        {policyId: 1, userUsername: "compliance", status: "ATTESTED",
 *         attestedAt: "2001-01-01"}                        -> 200, all three stored, no audit row
 *   POST /api/policy/attestations/{id}/attest  (not the assignee)  -> 403
 * </pre>
 *
 * <p>A completed attestation-of-record in another person's name is the artefact an auditor is shown
 * as proof that individual read the policy.
 */
@ExtendWith(MockitoExtension.class)
class PolicyAttestationCreateAuthorityTest {

    private static final Long ORG_ID = 1L;

    @Mock private PolicyDocumentRepository docs;
    @Mock private PolicyExceptionRepository exceptions;
    @Mock private PolicyAttestationRepository attestations;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private ControlRepository controls;
    @Mock private PolicyDocumentControlLinkRepository controlLinks;
    @Mock private AttachmentRepository attachments;

    private PolicyManagementService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        service = new PolicyManagementService(docs, exceptions, attestations, audit, publisher,
                                              controls, controlLinks, attachments);
        lenient().when(attestations.save(any())).thenAnswer(i -> i.getArgument(0));
        // F2-453 — createAttestation now RESOLVES the policy in-tenant before saving, so a fixture
        // naming policyId 7 has to supply document 7. Before that fix the reference was stored
        // unresolved: `assigned()` named a policy that does not exist and the create returned 200.
        //
        // Lenient because the four legs asserting a refused status throw at the status guard,
        // before the resolution is reached — so those must not be charged an unused stub.
        //
        // ⚠ Reading the FIXTURE first is the rule here: when this family's fix breaks a test, the
        // fixture is usually describing a row that could not exist (F2-240). Nothing about F2-219's
        // assertions changed — they are all about what a caller may not forge, and they still hold.
        lenient().when(docs.findByIdAndOrganisationId(7L, ORG_ID))
            .thenReturn(Optional.of(fixturePolicy()));
    }

    /** The policy `assigned()` names. Version "1.0" is what createAttestation now derives. */
    private static PolicyDocument fixturePolicy() {
        PolicyDocument d = new PolicyDocument();
        d.setId(7L);
        d.setOrganisationId(ORG_ID);
        d.setPolicyCode("POL-FIXTURE-007");
        d.setVersion("1.0");
        return d;
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    private static PolicyAttestation assigned() {
        PolicyAttestation a = new PolicyAttestation();
        a.setPolicyId(7L);
        a.setUserUsername("compliance");
        return a;
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void anAttestationCannotBeFiledAlreadyAttested() {
        PolicyAttestation a = assigned();
        a.setStatus("ATTESTED");

        assertThatThrownBy(() -> service.createAttestation(a))
            .as("ATTESTED is attest()'s to write, behind SEC-CMP-004's assignee check. Creating "
                + "one straight into it produces the same record with none of the control.")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("status")
            .hasMessageContaining("ATTESTED")
            .hasMessageContaining("attest");
    }

    @Test
    void anAttestationCannotBeFiledAlreadyDeclinedEither() {
        PolicyAttestation a = assigned();
        a.setStatus("DECLINED");

        assertThatThrownBy(() -> service.createAttestation(a))
            .as("decline() is the other guarded outcome — closing only the one that was probed "
                + "would leave the sibling open, which is the shape this slice is about")
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aCreateKeepsNoCompletionStampTheCallerSupplied() {
        PolicyAttestation a = assigned();
        a.setAttestedAt(LocalDateTime.of(2001, 1, 1, 0, 0));
        a.setDeclinedAt(LocalDateTime.of(2001, 1, 1, 0, 0));
        a.setDeclinedReason("did not happen");

        PolicyAttestation saved = service.createAttestation(a);

        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getAttestedAt())
            .as("an assignment has been attested by nobody, so a date here describes an act that "
                + "did not happen")
            .isNull();
        assertThat(saved.getDeclinedAt()).isNull();
        assertThat(saved.getDeclinedReason()).isNull();
        assertThat(saved.getUserUsername())
            .as("who the attestation is FOR is the assigner's to state — the fix must not eat it")
            .isEqualTo("compliance");
    }

    @Test
    void assigningIsUntouchedWhetherTheStatusIsOmittedOrExplicitlyPending() {
        assertThat(service.createAttestation(assigned()).getStatus()).isEqualTo("PENDING");

        PolicyAttestation explicit = assigned();
        explicit.setStatus("PENDING");
        assertThat(service.createAttestation(explicit).getStatus())
            .as("assigning is this route's whole purpose and must keep working")
            .isEqualTo("PENDING");
    }

    // ── the guarded route still does its job ─────────────────────────────────

    @Test
    void attestStillRefusesAnyoneButTheAssignee() {
        PolicyAttestation pending = assigned();
        pending.setId(3L);
        pending.setStatus("PENDING");
        when(attestations.findByIdAndOrganisationId(3L, ORG_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.attest(3L, null, "ranalyst", false))
            .as("SEC-CMP-004 — the control the create was reaching around")
            .isInstanceOf(DomainAccessDeniedException.class);
    }

    @Test
    void theAssigneeCanStillAttestAndTheStampsComeFromTheServer() {
        PolicyAttestation pending = assigned();
        pending.setId(4L);
        pending.setStatus("PENDING");
        when(attestations.findByIdAndOrganisationId(4L, ORG_ID)).thenReturn(Optional.of(pending));

        PolicyAttestation done = service.attest(4L, "EV-1", "compliance", false);

        assertThat(done.getStatus()).isEqualTo("ATTESTED");
        assertThat(done.getAttestedAt())
            .as("the route allowed to attest must still record when")
            .isNotNull();
        assertThat(done.getUpdatedBy())
            .as("and who actually acted, which is what non-repudiation means here")
            .isEqualTo("compliance");
    }

    @Test
    void attestStillRefusesAnAttestationThatIsNotPending() {
        PolicyAttestation already = assigned();
        already.setId(5L);
        already.setStatus("ATTESTED");
        when(attestations.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(already));

        assertThatThrownBy(() -> service.attest(5L, null, "compliance", false))
            .as("PENDING is the only state attest() acts on, and the create's guard is read from "
                + "the same constant so the two cannot drift")
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PENDING");
    }
}
