package com.tss.grc.module.ethics.coi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.SubmitDeclarationRequest;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.iam.service.TenantUserDirectory;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * F2-144 part (a) — a conflict-of-interest declaration is attributed by the server, not by the body.
 *
 * <p><b>The defect.</b> {@code submitDeclaration} read {@code userId} from the request body and stored
 * it verbatim, so nothing tied a declaration to the person filing it. It was latent only because
 * {@code CoiController.SUBMIT_GUARD} recites two role names that exist in no role
 * ({@code ETHICS_OFFICER}, {@code USER}), leaving three administrators as the only possible callers.
 * <b>The lockout was the sole thing preventing an unbound write</b> — which is why opening the guard,
 * the fix F2-52's ghost-name gate invites, had to wait for this.
 *
 * <p><b>What the rule preserves.</b> Filing on someone's behalf is the only behaviour that has ever
 * worked here and is legitimate, so a holder of a COI management authority may still name any
 * {@code userId}. Everyone else declares for themselves and the body's {@code userId} is <b>ignored,
 * not rejected</b>: a rejection would disclose whether another id exists, and a self-declaration has no
 * reason to carry one.
 *
 * <p>The private resolver is invoked directly. It is the whole unit under test, and going through
 * {@code submitDeclaration} would mean stubbing a campaign, four repositories and an audit sink to
 * observe one field.
 *
 * <p><b>F2-244 — this class's own premise was the next defect, and it was written down.</b> The
 * sentence above used to read <em>"it touches none of the service's six collaborators"</em>, offered
 * as evidence that the unit was cleanly isolated. It was true, and the reason it was true is that
 * {@code declarantFor} decided <em>who may name another person</em> and then returned that person's
 * id <b>without ever asking whether they exist</b>. 🔑 <b>A resolver that touches nothing cannot
 * resolve anything</b> — so the isolation the javadoc presented as a virtue was the shape of the
 * hole. Measured: 8 of the 8 non-null declaration user ids in dev and prod name no user at all.
 *
 * <p>These tests were right and are kept: every one of them is about <em>authority</em>, which is a
 * different question from <em>referent</em>, and both are needed. What is added is the leg nobody had
 * — that a named declarant must resolve inside the caller's tenant.
 */
@ExtendWith(MockitoExtension.class)
class CoiDeclarantBindingTest {

    private static final long CALLER = 7L;
    private static final long SOMEONE_ELSE = 99L;
    private static final long ORG = 1L;

    @Mock private UserRepository users;

    @BeforeEach
    void setTenant() {
        TenantContext.setCurrentTenant(ORG);
    }

    /** A live user of the caller's own tenant, for the paths that must keep working. */
    private static User userIn(long id, long org) {
        User u = new User();
        u.setId(id);
        u.setOrganisationId(org);
        u.setIsActive(true);
        u.setFullNameEn("Named Declarant");
        return u;
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private static void authenticate(long userId, String... authorities) {
        var principal = new com.tss.grc.security.GrcUserDetails(
                userId, 1L, "u" + userId, "pw", true, true,
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "pw", principal.getAuthorities()));
    }

    private static SubmitDeclarationRequest requestNaming(Long userId) {
        return new SubmitDeclarationRequest(
                1L, userId, "E-1", "Declarant", "مُقرّ", null, null, null, null,
                false, false, "I attest.", List.of());
    }

    private Long resolve(SubmitDeclarationRequest req) throws Exception {
        // F2-244 — `users` is the fifth argument now. It is the collaborator this class's javadoc
        // once boasted the resolver did not need.
        //
        // F2-249 — the sixth is a REAL TenantUserDirectory over that same mocked repository, not a
        // mock of it. F2-237's rule: a mocked collaborator returns null, and every refusal leg
        // below would then go green asserting nothing at all. The primitive is pure logic over the
        // repository, so using the real one keeps the mock's stubbing load-bearing.
        CoiService service = new CoiService(null, null, null, null, users,
                new TenantUserDirectory(users), null, null);
        Method m = CoiService.class.getDeclaredMethod("declarantFor", SubmitDeclarationRequest.class);
        m.setAccessible(true);
        return (Long) m.invoke(service, req);
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("an ordinary employee's declaration binds to the caller, whatever the body says")
    void aSelfDeclarationIgnoresTheBodysUserId() throws Exception {
        authenticate(CALLER, "ROLE_VIEWER");

        assertThat(resolve(requestNaming(SOMEONE_ELSE)))
                .as("the body named user %d and the caller is %d. Trusting the body is how an "
                    + "authenticated user attests in another person's name — the one thing a "
                    + "conflict-of-interest process must prevent.", SOMEONE_ELSE, CALLER)
                .isEqualTo(CALLER);

        assertThat(resolve(requestNaming(null)))
                .as("a self-declaration with no userId must still be attributed to the caller")
                .isEqualTo(CALLER);
    }

    /**
     * F2-145(e) slice 6 changed <em>how</em> a COI management authority is recognised, not who has one.
     * The check recited {@code ROLE_BOARD_SECRETARY │ ROLE_COMPLIANCE_OFFICER │ ROLE_ETHICS_OFFICER} —
     * {@code CoiController}'s old role list minus the admin — and now reads {@code ETHICS_WRITE}, the
     * authority those exact three roles hold after V371. Leaving the role list behind would have split one
     * authority in two: a tenant granting {@code ETHICS:WRITE} to a fourth role would get campaign
     * management and silently not the right to file on an employee's behalf.
     */
    @Test
    @DisplayName("a COI management authority may still file on another employee's behalf")
    void anAdministratorMayNameAnotherDeclarant() throws Exception {
        // F2-244 — the named declarant now has to be a real, active user of this tenant, so the
        // path that must keep working needs one.
        org.mockito.Mockito.when(users.findById(SOMEONE_ELSE))
                .thenReturn(java.util.Optional.of(userIn(SOMEONE_ELSE, ORG)));

        for (String authority : new String[] { "ROLE_PLATFORM_ADMIN", "ETHICS_WRITE" }) {
            SecurityContextHolder.clearContext();
            authenticate(CALLER, authority);

            assertThat(resolve(requestNaming(SOMEONE_ELSE)))
                    .as("%s is a COI management authority; filing on behalf of an employee is the only "
                        + "behaviour that has ever worked at this endpoint and must survive the fix.",
                        authority)
                    .isEqualTo(SOMEONE_ELSE);
        }
    }

    /**
     * The converse, and it is the half that would have gone unnoticed. Holding the ROLE is no longer
     * enough — the permission is. If V371 were reverted, or a tenant revoked {@code ETHICS:WRITE} from
     * {@code COMPLIANCE_OFFICER}, filing on behalf would stop working while the role name still looked
     * right, and this test says so at the point of failure.
     */
    @Test
    @DisplayName("the role name alone no longer files on somebody else's behalf — the permission does")
    void aRoleNameWithoutThePermissionDeclaresForItself() throws Exception {
        for (String roleOnly : new String[] {
                "ROLE_COMPLIANCE_OFFICER", "ROLE_BOARD_SECRETARY", "ROLE_ETHICS_OFFICER" }) {
            SecurityContextHolder.clearContext();
            authenticate(CALLER, roleOnly);

            assertThat(resolve(requestNaming(SOMEONE_ELSE)))
                    .as("%s without ETHICS_WRITE must fall back to a self-declaration. The three roles do "
                        + "hold that permission in the seeded matrix (V371), so this is about the "
                        + "MECHANISM: authority comes from the grant table, not from the name.", roleOnly)
                    .isEqualTo(CALLER);
        }
    }

    @Test
    @DisplayName("an administrator who names nobody declares for themselves")
    void anAdministratorWithoutAUserIdDeclaresForThemselves() throws Exception {
        authenticate(CALLER, "ETHICS_WRITE");

        assertThat(resolve(requestNaming(null)))
                .as("naming no declarant is a self-declaration, not an unattributed one")
                .isEqualTo(CALLER);
    }

    /**
     * F2-244 — the leg this class never had. Every test above asks <em>who may name another
     * person</em>; none asked whether the person named is real.
     */
    @Test
    @DisplayName("a named declarant who resolves to nobody is refused, not stored")
    void aNamedDeclarantMustResolveInsideTheTenant() throws Exception {
        authenticate(CALLER, "ETHICS_WRITE");
        org.mockito.Mockito.when(users.findById(SOMEONE_ELSE)).thenReturn(java.util.Optional.empty());

        assertThat(catchThrowableOf(() -> resolve(requestNaming(SOMEONE_ELSE))))
                .as("""
                    A declaration filed against a user id that resolves to nobody is attributable
                    to nobody, and this register cannot fall back on a name — coi_declaration has
                    full_name_en, but the recusal table sharing this resolver has no name column at
                    all. Storing it is what left 8 of 8 non-null declaration user ids in dev and
                    prod naming ids 1001-1007 against MAX(iam.users.id) = 8.""")
                .isNotNull()
                .hasMessageContaining("not an active user in this organisation");
    }

    /**
     * F2-244 — and the tenant is the point, not merely existence. A user of another organisation is
     * refused with the SAME message, deliberately: distinguishing "no such user" from "another
     * tenant's user" would let a caller enumerate foreign user ids by reading the error text (the
     * rule {@code CommitteeMemberRegistryService#requireUserInTenant} records).
     */
    @Test
    @DisplayName("a declarant belonging to another tenant is refused, with the same message")
    void aForeignDeclarantIsRefusedIndistinguishably() throws Exception {
        authenticate(CALLER, "ETHICS_WRITE");
        org.mockito.Mockito.when(users.findById(SOMEONE_ELSE))
                .thenReturn(java.util.Optional.of(userIn(SOMEONE_ELSE, ORG + 1)));

        assertThat(catchThrowableOf(() -> resolve(requestNaming(SOMEONE_ELSE))))
                .isNotNull()
                .hasMessageContaining("not an active user in this organisation");
    }

    /**
     * F2-244 — an inactive user is refused too. A leaver's conflict declaration filed after they
     * left would be a record nobody can be asked about, and {@code linkableUsers} already treats
     * {@code isActive} as part of "is this a person we can name".
     */
    @Test
    @DisplayName("an inactive user cannot be named as a declarant")
    void anInactiveDeclarantIsRefused() throws Exception {
        authenticate(CALLER, "ETHICS_WRITE");
        User leaver = userIn(SOMEONE_ELSE, ORG);
        leaver.setIsActive(false);
        org.mockito.Mockito.when(users.findById(SOMEONE_ELSE)).thenReturn(java.util.Optional.of(leaver));

        assertThat(catchThrowableOf(() -> resolve(requestNaming(SOMEONE_ELSE))))
                .isNotNull()
                .hasMessageContaining("not an active user in this organisation");
    }

    @Test
    @DisplayName("an unauthenticated submission is refused rather than attributed to nobody")
    void noAuthenticationMeansNoDeclaration() {
        SecurityContextHolder.clearContext();

        assertThat(catchThrowableOf(() -> resolve(requestNaming(SOMEONE_ELSE))))
                .as("with no principal there is nobody to attribute the attestation to, and storing "
                    + "the body's userId would be the original defect. A COI declaration attributable "
                    + "to nobody is worse than a refused one.")
                .isNotNull()
                .hasMessageContaining("authenticated declarant");
    }

    /** Unwraps the reflective wrapper so the assertion sees the service's own exception. */
    private static Throwable catchThrowableOf(ThrowingCall call) {
        try {
            call.run();
            return null;
        } catch (Exception e) {
            return e.getCause() != null ? e.getCause() : e;
        }
    }

    private interface ThrowingCall {
        void run() throws Exception;
    }
}
