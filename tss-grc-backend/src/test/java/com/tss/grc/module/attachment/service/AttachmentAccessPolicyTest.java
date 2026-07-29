package com.tss.grc.module.attachment.service;

import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCR-004 — an attachment inherits the parent record's read guard.
 *
 * <p>The live reproduction this pins: {@code ranalyst} holds {@code EVIDENCE_READ} and no BCM
 * authority of any kind — every BCM record route answers 403 for it ({@code /api/v1/bcm/plans},
 * {@code /bia-assessments}, {@code /crisis-events}, {@code /scenario-library}) — yet
 * {@code GET /api/v1/attachments/{id}/download} handed over the bytes of a file attached to a BCM
 * plan, and {@code GET /api/v1/attachments/BCM_GOVERNANCE/1} listed them.
 *
 * <p><b>An earlier draft of this test used the wrong pair</b> and it is worth saying why. It picked
 * {@code so_officer} + {@code PolicyDocument} because that persona is 403 on
 * {@code /api/policy/documents/{id}} — but the cyber workspace serves the <em>same</em> policy rows
 * under {@code CYBER:READ}, which that persona holds. So it was never a disclosure for them; the
 * registry treating it as one would have 403'd a CISO on their own screen. Choosing a demonstration
 * pair is itself a measurement.
 */
class AttachmentAccessPolicyTest {

    private final AttachmentAccessPolicy policy = new AttachmentAccessPolicy();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWith(String... authorities) {
        GrcUserDetails principal = new GrcUserDetails(7L, 1L, "someone", "x", true, true,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    // ── the reproduced case ──────────────────────────────────────────────────

    @Test
    void evidenceReadAloneNoLongerReachesABcmRecordsAttachments() {
        // ranalyst's real grant set: no BCM permission, and ROLE_RISK_ANALYST appears on none of the
        // BCM controllers' role lists. Every BCM read route 403s it — measured, not assumed.
        authenticateWith("EVIDENCE_READ", "EVIDENCE_WRITE", "RISK_READ", "RISK_WRITE",
            "COMPLIANCE_READ", "ROLE_RISK_ANALYST");

        for (String bcmType : List.of("BCP_PLAN", "BIA_ASSESSMENT", "BCM_GOVERNANCE",
                                      "CRISIS_EVENT", "BCM_CAPA", "DR_PLAN")) {
            assertThatThrownBy(() -> policy.requireAccess(bcmType, "download"))
                .as("the platform 403s this persona on the %s record itself; the file cannot be "
                    + "softer than the record", bcmType)
                .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void theSamePersonaKeepsTheAttachmentsItCanAlreadyRead() {
        authenticateWith("EVIDENCE_READ", "RISK_READ", "COMPLIANCE_READ", "ROLE_RISK_ANALYST");

        assertThatCode(() -> policy.requireAccess("RISK", "download")).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireAccess("PolicyDocument", "list"))
            .doesNotThrowAnyException();
    }

    // ── the two guard styles the platform actually uses ──────────────────────

    @Test
    void permissionGuardedParentsRequireThatModulesPermission() {
        authenticateWith("EVIDENCE_READ", "COMPLIANCE_READ");
        assertThatCode(() -> policy.requireAccess("PolicyDocument", "list"))
            .doesNotThrowAnyException();

        authenticateWith("EVIDENCE_READ", "RISK_READ");
        assertThatThrownBy(() -> policy.requireAccess("PolicyDocument", "list"))
            .as("a module that does not serve this record grants no access to its files")
            .isInstanceOf(AccessDeniedException.class);

        // Holding only WRITE also proves visibility: nobody can edit a record they cannot see, and
        // a grant that gave WRITE without READ should not produce an absurd denial here.
        authenticateWith("EVIDENCE_READ", "COMPLIANCE_WRITE");
        assertThatCode(() -> policy.requireAccess("PolicyDocument", "list"))
            .doesNotThrowAnyException();
    }

    @Test
    void attachingEvidenceNeedsToSeeTheRecord_notToOwnIt() {
        // The measured decision, and it reverses my first cut. Requiring the parent's WRITE for
        // uploads was live-falsified: `auditor` (EVIDENCE_WRITE + BCM_READ, no BCM_WRITE) and
        // `so_officer` (EVIDENCE_WRITE + RISK_READ, no RISK_WRITE) are the evidence-custodian
        // personas the platform ships, and both lost the ability to file evidence against records
        // they review. EVIDENCE:WRITE exists to authorise exactly that.
        authenticateWith("EVIDENCE_WRITE", "BCM_READ");
        assertThatCode(() -> policy.requireAccess("BCP_PLAN", "upload"))
            .as("a reviewer may attach evidence to a plan they can read but not edit")
            .doesNotThrowAnyException();

        // What it must still stop: writing into a record the caller cannot see at all.
        assertThatThrownBy(() -> policy.requireAccess("WHISTLEBLOWER_CASE", "upload"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aRecordServedByTwoModulesIsVisibleToEither() {
        // Nearly shipped as a regression. The cyber workspace's policy panel is wired to the shared
        // policy service, so `/api/v1/cyber/policies` (CYBER:READ) returns the same PolicyDocument
        // rows as `/api/policy/documents` (COMPLIANCE:READ) — verified live, 200 for a CISO with no
        // COMPLIANCE_READ. Mapping the type to COMPLIANCE alone would have 403'd that CISO on the
        // attachment panel of a screen they are entitled to use.
        authenticateWith("EVIDENCE_READ", "CYBER_READ");
        assertThatCode(() -> policy.requireAccess("PolicyDocument", "list"))
            .as("a CISO reaches policy records through the cyber route; the files must follow")
            .doesNotThrowAnyException();

        authenticateWith("EVIDENCE_READ", "COMPLIANCE_READ");
        assertThatCode(() -> policy.requireAccess("PolicyDocument", "list"))
            .doesNotThrowAnyException();

        authenticateWith("EVIDENCE_READ", "BCM_READ");
        assertThatThrownBy(() -> policy.requireAccess("PolicyDocument", "list"))
            .as("but a module that does not serve those records grants nothing")
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void roleGuardedParentsRequireOneOfTheParentsReadRoles() {
        // The Saudi financial-sector registers are guarded by role lists, not by the permission
        // catalogue; a registry that only spoke hasPermission would have locked their owners out.
        authenticateWith("EVIDENCE_READ", "ROLE_CREDIT_OFFICER");
        assertThatCode(() -> policy.requireAccess("COUNTERPARTY", "list"))
            .doesNotThrowAnyException();

        authenticateWith("EVIDENCE_READ", "ROLE_FRAUD_INVESTIGATOR");
        assertThatThrownBy(() -> policy.requireAccess("COUNTERPARTY", "list"))
            .as("a fraud investigator is not on CptyController's read list")
            .isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> policy.requireAccess("FRAUD_CASE", "list"))
            .doesNotThrowAnyException();
    }

    @Test
    void aParentGuardedByEitherStyleAcceptsEither() {
        // EXERCISE_SCENARIO's register is role-guarded while the surrounding BCM controllers use the
        // permission; both mean "may read this record", so both pass.
        authenticateWith("EVIDENCE_READ", "BCM_READ");
        assertThatCode(() -> policy.requireAccess("EXERCISE_SCENARIO", "list"))
            .doesNotThrowAnyException();

        authenticateWith("EVIDENCE_READ", "ROLE_BCM_MANAGER");
        assertThatCode(() -> policy.requireAccess("EXERCISE_SCENARIO", "list"))
            .doesNotThrowAnyException();
    }

    // ── the surface itself ───────────────────────────────────────────────────

    @Test
    void anUnknownEntityTypeIsRefusedRatherThanAnsweredEmpty() {
        authenticateWith("EVIDENCE_READ", "COMPLIANCE_READ", "RISK_READ");

        // Before this fix a made-up type returned 200 with an empty list, which turns the endpoint
        // into an oracle: probe types and ids until something comes back.
        assertThatThrownBy(() -> policy.requireAccess("NOT_A_REAL_ENTITY_TYPE", "list"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void namingConventionDoesNotDecideAccess() {
        authenticateWith("EVIDENCE_READ", "COMPLIANCE_READ");

        // The codebase spells the same parent both ways; a case-sensitive registry would fail closed
        // on a spelling difference and open on the other one.
        for (String spelling : List.of("PolicyDocument", "POLICY_DOCUMENT", "policydocument",
                                       "policy-document")) {
            assertThatCode(() -> policy.requireAccess(spelling, "list"))
                .as("spelling '%s'", spelling)
                .doesNotThrowAnyException();
        }
    }

    @Test
    void anUnauthenticatedContextIsRefused() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> policy.requireAccess("RISK", "download"))
            .as("off-request there is nothing to check against, so this must not fail open")
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void platformAdminBypassesExactlyAsModulePermissionEvaluatorDoes() {
        authenticateWith("ROLE_PLATFORM_ADMIN");

        assertThatCode(() -> policy.requireAccess("PolicyDocument", "list")).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireAccess("WHISTLEBLOWER_CASE", "upload"))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireAccess("STILL_NOT_A_TYPE", "list"))
            .as("even an admin gets no answer for a type that does not exist — it is a typo, not access")
            .isInstanceOf(AccessDeniedException.class);
    }

    // ── registry hygiene ─────────────────────────────────────────────────────

    @Test
    void noTwoDeclaredTypesCollideOnNormalisationWithDifferentGuards() {
        Map<String, String> byNormalised = new HashMap<>();
        AttachmentAccessPolicy.declared().forEach((raw, guard) -> {
            String key = raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
            String previous = byNormalised.put(key, raw + "=" + guard);
            assertThat(previous == null || previous.endsWith("=" + guard))
                .as("'%s' normalises onto '%s' but declares a different guard — one of them would "
                    + "silently win", raw, previous)
                .isTrue();
        });
    }

    @Test
    void theResidueIsSmallAndNamed() {
        // Types whose parent read endpoint is isAuthenticated(), so there is no authority to
        // inherit. Not a bug in this class: the gap belongs to the parent endpoint. Pinned so it
        // can only shrink, and so nobody adds a new one casually.
        assertThat(AttachmentAccessPolicy.authenticatedOnlyTypes())
            .as("each entry must be justified in the registry's note; add one only with a measured "
                + "reason, and prefer fixing the parent endpoint instead")
            .containsExactly("CRISIS_AAR");
        assertThat(AttachmentAccessPolicy.declared().get("CRISIS_AAR").note())
            .as("and it must say why")
            .contains("isAuthenticated()");
    }
}
