package com.tss.grc.module.knowledge;

import com.tss.grc.module.knowledge.controller.KbArticleController;
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
 * F2-53 — the knowledge base could only be authored by the platform administrator.
 *
 * <h3>A ghost permission, not a ghost role</h3>
 * <p>The three write guards were {@code hasRole('PLATFORM_ADMIN') or hasAuthority('KB_WRITE')}.
 * {@code KB_WRITE} is the raw-authority spelling of the permission {@code KB:WRITE} — the form
 * {@code ModulePermissionEvaluator} emits as a granted authority — but <b>{@code KB} was absent from
 * {@code iam.permissions} entirely</b>, so no role could hold it, so that leg was permanently false
 * and the guard reduced to {@code hasRole('PLATFORM_ADMIN')}. Creating, editing and publishing every
 * article was the platform administrator's job alone, by attrition rather than by decision.
 *
 * <p>This is F2-52's failure mode one bucket over: a name that resolves to nothing, with no compile
 * error, no startup warning and no log line. The role-guard census's own source comment uses
 * {@code hasAuthority('KB_WRITE')} as its worked example of exactly this. {@code V329} catalogues
 * {@code KB:WRITE} and grants it to {@code GRC_ADMINISTRATOR}, {@code ORG_ADMIN} and
 * {@code COMPLIANCE_OFFICER} — the third being the persona actually accountable for the content.
 *
 * <h3>⚠ Why the READS are deliberately NOT converted</h3>
 * <p>Both read endpoints keep {@code isAuthenticated()}, and this test pins that so a uniformity
 * sweep does not "fix" it. Measured by reading the corpus rather than the guards: all 22 articles are
 * PUBLISHED / visibility INTERNAL staff guidance — <i>"Whistleblowing Channel — How to Report"</i>,
 * <i>"Disclosing a Conflict of Interest (Nazaha)"</i>, <i>"Handling Data-Subject Requests under
 * PDPL"</i>, <i>"Business Continuity Activation Steps (NCEMA)"</i>. A whistleblowing procedure
 * readable only by permission-holders is worse than none: every employee must be able to find out how
 * to report. {@code isAuthenticated()} cannot wrongly exclude an employee; a grant list can only ever
 * wrongly exclude one. ADR-017 D-7 originally proposed granting {@code KB:READ} broadly — reading the
 * content showed granting it <em>at all</em> is worse than the guard it would replace, so V329
 * catalogues WRITE only and asserts {@code KB:READ}'s absence.
 *
 * @see com.tss.grc.security.RoleGuardConversionRatchetTest the census and the platform-wide ratchet
 */
class KbArticleGuardTest {

    private static final String KB_WRITE = "hasPermission('KB','WRITE')";
    private static final String OPEN_READ = "isAuthenticated()";

    private static Map<String, String> guards() {
        Map<String, String> found = new TreeMap<>();
        for (Method m : KbArticleController.class.getDeclaredMethods()) {
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
    void authoringAuthorisesOnKbWrite() {
        Map<String, String> g = guards();
        for (String write : List.of("create", "update", "transition")) {
            assertThat(g)
                .as("%s must authorise on KB:WRITE — it was PLATFORM_ADMIN-only because KB_WRITE "
                  + "named a permission that did not exist", write)
                .containsEntry(write, KB_WRITE);
        }
    }

    /**
     * The specific trap: {@code KB_WRITE} as a raw authority looks like a permission check and is not
     * one — the evaluator is never called, so the PLATFORM_ADMIN bypass every other guard relies on is
     * skipped too. If it comes back, authoring silently collapses to the administrator again.
     */
    @Test
    void noGuardReachesForTheRawAuthoritySpelling() {
        guards().forEach((endpoint, guard) -> {
            assertThat(guard)
                .as("%s must not use hasAuthority('KB_WRITE') — that bypasses "
                  + "ModulePermissionEvaluator, so it gets no PLATFORM_ADMIN fallback and it silently "
                  + "matched nothing while KB was uncatalogued", endpoint)
                .doesNotContain("hasAuthority");
            assertThat(guard)
                .as("%s must not authorise by role name", endpoint)
                .doesNotContain("hasAnyRole");
        });
    }

    /**
     * Pinned deliberately. See the class javadoc: this is the one guard in the platform that is more
     * correct as {@code isAuthenticated()} than as a permission, because the corpus is staff guidance
     * including the whistleblowing and conflict-of-interest procedures.
     */
    @Test
    void readsStayOpenToEveryAuthenticatedEmployee() {
        Map<String, String> g = guards();
        assertThat(g)
            .as("converting these to a permission would let a grant-list mistake hide the "
              + "whistleblowing procedure from an employee — see ADR-017 D-7, amended after "
              + "measuring the content")
            .containsEntry("listPublished", OPEN_READ)
            .containsEntry("bySlug", OPEN_READ);
    }

    @Test
    void deleteStaysPlatformAdminOnly() {
        // Removing published guidance is not authoring it. KB catalogues no DELETE action, so there is
        // nothing to convert to, and an author must not be able to destroy a published procedure.
        assertThat(guards().get("delete")).isEqualTo("hasRole('PLATFORM_ADMIN')");
    }

    @Test
    void everyEndpointStillCarriesAGuard() {
        Map<String, String> g = guards();
        assertThat(g.values()).doesNotContain("<NO GUARD>");
        // Anti-vacuity: a reflection scan resolving nothing passes every assertion above.
        // Six mapped endpoints: two reads, three writes, one delete.
        assertThat(g).hasSize(6)
            .containsKeys("listPublished", "bySlug", "create", "update", "transition", "delete");
    }
}
