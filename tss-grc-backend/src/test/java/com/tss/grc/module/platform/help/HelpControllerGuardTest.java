package com.tss.grc.module.platform.help;

import com.tss.grc.module.platform.help.controller.HelpController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-52 — the help authoring guard recited {@code CONTENT_EDITOR}, which exists in no database.
 *
 * <p>{@code hasAnyRole('PLATFORM_ADMIN','CONTENT_EDITOR')} is a real leg beside a permanently false
 * one, and a role guard never reaches {@code ModulePermissionEvaluator} so it never reaches the
 * PLATFORM_ADMIN bypass either: authoring every help topic was the platform administrator's job
 * alone, by attrition. Measured live before the fix — {@code GRC_ADMINISTRATOR} 403/403, and
 * {@code COMPLIANCE_OFFICER} <b>200 on the inventory, 403 on every write</b>, while the controller's
 * own javadoc named them an author.
 *
 * <h3>Why this pins tiers and not shapes</h3>
 * The obvious test — "no guard in this class calls {@code hasAnyRole}" — passes the moment the names
 * are swapped for a permission, and keeps passing if {@code delete} is later re-pointed at the read
 * constant or {@code lookup} at the authoring one. {@code FraudControllerGuardTest} established the
 * fix for that: pin <b>each endpoint to its tier</b>, so a mis-tiered method fails by name.
 *
 * <p>⚠ The read tier is the assertion most likely to be "fixed" by a later uniformity sweep. It is
 * {@code isAuthenticated()} on purpose (ADR-017 D-7 as amended, and the same reasoning as
 * {@code KbArticleController}): a grant list can only ever wrongly exclude someone from the guidance
 * they are looking for, and there is no {@code KB:READ} to grant. That intent is asserted here so
 * the next sweep has to argue with a failing test rather than with a comment.
 */
class HelpControllerGuardTest {

    private static final String READ   = "isAuthenticated()";
    private static final String AUTHOR = "hasPermission('KB','WRITE')";
    private static final String DELETE = "hasRole('PLATFORM_ADMIN')";

    /**
     * Every endpoint on the controller and the tier it belongs to. Listed exhaustively: a new method
     * that is not here fails {@link #everyEndpointIsPinnedToATier()}, which is the point — an
     * unlisted endpoint is an unreviewed guard.
     */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>() {{
        // The drawer — every authenticated user.
        put("lookup",        READ);
        put("search",        READ);
        put("listForModule", READ);
        put("trackView",     READ);
        // Authoring, including the inventory of drafts.
        put("listAll",       AUTHOR);
        put("get",           AUTHOR);
        put("create",        AUTHOR);
        put("update",        AUTHOR);
        // Destroying guidance someone may be mid-task with.
        put("delete",        DELETE);
    }};

    private static Map<String, String> actualGuards() {
        Map<String, String> found = new TreeMap<>();
        for (Method m : HelpController.class.getDeclaredMethods()) {
            PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
            if (pa != null) {
                found.put(m.getName(), pa.value());
            }
        }
        assertThat(found).as(
            "reflection found %d guarded methods on HelpController; the class has %d endpoints. A "
            + "collapsed scan asserts nothing.", found.size(), EXPECTED.size())
            .hasSize(EXPECTED.size());
        return found;
    }

    @Test
    @DisplayName("every endpoint sits in its reviewed tier, by name")
    void everyEndpointIsPinnedToATier() {
        assertThat(actualGuards()).as("""
            A help endpoint's guard is not the one this slice reviewed. Mis-tiering is silent: \
            pointing a write at the read constant opens it, and pointing a read at the authoring \
            constant hides guidance from the users it exists for.""")
            .containsExactlyInAnyOrderEntriesOf(EXPECTED);
    }

    @Test
    @DisplayName("no guard here names a role, so none can name a role that does not exist")
    void noGuardAuthorisesByRoleNameExceptTheDeleteTier() {
        Map<String, String> roleNamed = new TreeMap<>();
        for (var e : actualGuards().entrySet()) {
            String g = e.getValue();
            if ((g.contains("hasRole") || g.contains("hasAnyRole")) && !DELETE.equals(g)) {
                roleNamed.put(e.getKey(), g);
            }
        }
        assertThat(roleNamed).as("""
            These guards authorise by role name. F2-52: a name absent from iam.roles is a \
            permanently false OR leg with no compile error, no startup warning and no log line — \
            CONTENT_EDITOR was one, on this very controller. Use a catalogued permission, or if the \
            tier really is the platform administrator, say so through DELETE_GUARD so the exception \
            is visible here.""").isEmpty();

        // Anti-vacuity: the one deliberate role guard must still be present, or this test would
        // pass over a class that had stopped using roles for a different reason than the fix.
        assertThat(actualGuards()).containsEntry("delete", DELETE);
    }

    @Test
    @DisplayName("the drawer stays open to every authenticated user")
    void theReadTierIsNotAPermission() {
        for (var e : actualGuards().entrySet()) {
            if (!READ.equals(EXPECTED.get(e.getKey()))) {
                continue;
            }
            assertThat(e.getValue()).as("""
                %s is a help-drawer read and must stay isAuthenticated(). There is no KB:READ to \
                grant — V329 catalogued WRITE only, deliberately — so replacing this with a \
                permission does not narrow the audience, it empties it. ADR-017 D-7.""", e.getKey())
                .isEqualTo(READ);
        }
    }
}
