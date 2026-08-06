package com.tss.grc.module.ethics.coi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tss.grc.module.ethics.coi.dto.CoiDtos.SubmitDeclarationRequest;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * <p>The private resolver is invoked directly. It is the whole unit under test, it touches none of the
 * service's six collaborators, and going through {@code submitDeclaration} would mean stubbing a
 * campaign, four repositories and an audit sink to observe one field.
 */
class CoiDeclarantBindingTest {

    private static final long CALLER = 7L;
    private static final long SOMEONE_ELSE = 99L;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
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

    private static Long resolve(SubmitDeclarationRequest req) throws Exception {
        CoiService service = new CoiService(null, null, null, null, null, null);
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

    @Test
    @DisplayName("a COI management authority may still file on another employee's behalf")
    void anAdministratorMayNameAnotherDeclarant() throws Exception {
        for (String authority : new String[] {
                "ROLE_PLATFORM_ADMIN", "ROLE_BOARD_SECRETARY",
                "ROLE_COMPLIANCE_OFFICER", "ROLE_ETHICS_OFFICER" }) {
            SecurityContextHolder.clearContext();
            authenticate(CALLER, authority);

            assertThat(resolve(requestNaming(SOMEONE_ELSE)))
                    .as("%s is a COI management authority; filing on behalf of an employee is the only "
                        + "behaviour that has ever worked at this endpoint and must survive the fix.",
                        authority)
                    .isEqualTo(SOMEONE_ELSE);
        }
    }

    @Test
    @DisplayName("an administrator who names nobody declares for themselves")
    void anAdministratorWithoutAUserIdDeclaresForThemselves() throws Exception {
        authenticate(CALLER, "ROLE_COMPLIANCE_OFFICER");

        assertThat(resolve(requestNaming(null)))
                .as("naming no declarant is a self-declaration, not an unattributed one")
                .isEqualTo(CALLER);
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
