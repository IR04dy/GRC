package com.tss.grc.module.ethics.coi.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-144(b) — every employee can now file their own conflict-of-interest declaration, and this pins the
 * two properties that make that safe.
 *
 * <h3>What was wrong</h3>
 * <p>{@code SUBMIT_GUARD} was {@code hasAnyRole('PLATFORM_ADMIN','ETHICS_OFFICER','BOARD_SECRETARY',
 * 'COMPLIANCE_OFFICER','USER')} and <b>{@code ETHICS_OFFICER} and {@code USER} exist in no row of
 * {@code iam.roles}</b>. Both legs were permanently false, so the class javadoc's stated intent — <i>"any
 * authenticated employee can submit their own attestation"</i> — had never once been true. A Nazaha COI
 * campaign ran with the declarants locked out and three administrators able to file for everyone.
 *
 * <h3>Why this is not simply "open the guard"</h3>
 * <p>Widening it was <b>unsafe</b> until F2-144(a) landed, because {@code submitDeclaration} read
 * {@code userId} out of the request body. An open guard over a body-chosen subject is an impersonation
 * path: any authenticated user could have filed an attestation in a colleague's name, which is precisely
 * what a conflict-of-interest register must not permit. {@code CoiDeclarantBindingTest} is the test that
 * holds that half and it fails unconditionally if the service ever trusts the body again — so the two
 * files together, not either alone, are what makes {@code isAuthenticated()} defensible here.
 *
 * <h3>Why a permission would be the wrong instrument</h3>
 * <p>ADR-010 makes {@code hasPermission(MODULE, ACTION)} the rule for writes. Its §Sanctioned exceptions
 * covers endpoints that discharge an obligation every employee is under, following ADR-017 D-7's ruling
 * on knowledge-base reads: <b>a grant list can only ever wrongly exclude someone</b>, and an employee who
 * cannot file their declaration is a compliance failure that reads as the employee's fault. Note the
 * platform already agreed with this for the *instructions* — D-7 keeps <i>"Disclosing a Conflict of
 * Interest (Nazaha)"</i> readable by every employee — so gating the act behind a grant would have left
 * the product explaining a duty to people it then refused.
 *
 * @see com.tss.grc.module.ethics.coi.service.CoiDeclarantBindingTest the server-side declarant binding
 * @see com.tss.grc.security.ControllerAuthorizationRatchetTest {@code SELF_SCOPED} — the sanctioned floor
 */
class CoiSubmitGuardTest {

    private static final String OPEN_SUBMIT = "isAuthenticated()";
    /**
     * The authority acts, as they read after F2-145(e) slice 6. This was one
     * {@code hasAnyRole('PLATFORM_ADMIN','ETHICS_OFFICER','BOARD_SECRETARY','COMPLIANCE_OFFICER')} over all
     * eleven; V371 splits it into the three ETHICS actions those same three roles hold, so the population is
     * unchanged and this test's property — <b>the submit exception must not leak to acts over other
     * people's declarations</b> — is unaffected by how the restriction is spelled. Any of the three counts
     * as restricted; being {@code isAuthenticated()} or absent does not.
     */
    private static final List<String> RESTRICTED = List.of(
            "hasPermission('ETHICS','READ')",
            "hasPermission('ETHICS','WRITE')",
            "hasPermission('ETHICS','APPROVE')");

    private static Map<String, String> guards() {
        Map<String, String> found = new TreeMap<>();
        for (Method m : CoiController.class.getDeclaredMethods()) {
            boolean mapped = m.isAnnotationPresent(GetMapping.class)
                || m.isAnnotationPresent(PostMapping.class)
                || m.isAnnotationPresent(PutMapping.class)
                || m.isAnnotationPresent(PatchMapping.class)
                || m.isAnnotationPresent(DeleteMapping.class);
            if (!mapped) continue;
            PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
            found.put(m.getName(), pa == null ? "<NO GUARD>" : pa.value());
        }
        return found;
    }

    @Test
    void anyAuthenticatedEmployeeMaySubmitTheirOwnDeclaration() {
        assertThat(guards())
            .as("submitDeclaration must stay isAuthenticated(). A permission or role list here can only "
              + "wrongly exclude a declarant, and an employee unable to file their COI attestation is a "
              + "compliance failure that looks like theirs — see ADR-010 §Sanctioned exceptions.")
            .containsEntry("submitDeclaration", OPEN_SUBMIT);
    }

    /**
     * The specific regression this guards against: reciting a role name that does not exist produces no
     * compile error, no startup warning and no log line — the guard just silently admits nobody. That is
     * how this endpoint spent its whole life locked.
     */
    @Test
    void theSubmitGuardNamesNoRoleAtAll() {
        String submit = guards().get("submitDeclaration");
        assertThat(submit)
            .as("a role or authority name here is how this endpoint broke: ETHICS_OFFICER and USER were "
              + "not roles, so both legs were dead and submission collapsed to three administrators")
            .doesNotContain("hasAnyRole")
            .doesNotContain("hasRole")
            .doesNotContain("hasAuthority");
    }

    /**
     * Opening submission must not leak to the authority acts. Reviewing, opening/closing a campaign and
     * recording a recusal are decisions about <em>other people's</em> declarations; only the act of
     * declaring for oneself is universal. If a later sweep applies the exception one endpoint too far,
     * this fails.
     */
    @Test
    void authorityActsOverOtherPeoplesDeclarationsStayRestricted() {
        Map<String, String> g = guards();
        for (String managed : List.of("createCampaign", "openCampaign", "closeCampaign",
                                      "archiveCampaign", "reviewDeclaration", "recordRecusal",
                                      "listCampaigns", "getCampaign", "getDeclaration",
                                      "listDeclarationsByCampaign", "recusalsByUser")) {
            assertThat(g.get(managed))
                .as("%s decides or discloses somebody else's declaration — it must NOT inherit the "
                  + "submit exception. Any of the three ETHICS actions is restricted enough; "
                  + "isAuthenticated() is not.", managed)
                .isIn(RESTRICTED);
        }
    }

    @Test
    void everyEndpointStillCarriesAGuard() {
        Map<String, String> g = guards();
        assertThat(g.values()).doesNotContain("<NO GUARD>");
        // Anti-vacuity: a reflection scan that resolves nothing passes every assertion above, and the
        // reassuring direction is always the one a broken scan fails in.
        assertThat(g).hasSize(12).containsKey("submitDeclaration");
    }
}
