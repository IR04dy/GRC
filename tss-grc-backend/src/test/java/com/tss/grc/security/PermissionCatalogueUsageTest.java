package com.tss.grc.security;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-13a — emits the authoritative list of {@code MODULE:ACTION} pairs the code actually
 * guards on, so grant tightness can be measured against the catalogue in both directions.
 *
 * <p><b>Why this exists as a test rather than a grep.</b> The two directions of grant
 * tightness are:
 * <ol>
 *   <li><b>Code guards on a permission nobody holds</b> — fail-closed dead code. This is
 *       <b>F2-23</b> ({@code ADMIN:APPROVE} guarded 59 committee methods while not existing in
 *       {@code iam.permissions} at all) and <b>F2-27</b>'s {@code AI:EXPORT}. Both were found
 *       by hand, months apart.</li>
 *   <li><b>A permission is granted so widely that guarding on it is cosmetic</b> —
 *       {@code WORKFLOW:WRITE} is held by 55 of 58 roles <em>including VIEWER</em>, so
 *       {@code ApprovalRequestController#create} counts as "guarded" in the authorization
 *       ratchet while a read-only viewer still passes it (live-verified 200). The
 *       {@link ControllerAuthorizationRatchetTest} javadoc records this caveat explicitly:
 *       "guarded" is not "restricted".</li>
 * </ol>
 *
 * <p>Measuring either direction needs the guard set from the <em>code</em> joined to the grant
 * counts from the <em>database</em>. The suite has no database, so this half runs here — by
 * reflection, because a source regex has twice under-counted a security surface in this
 * codebase (F2-27 missed 8 constant-valued guards and a 40th endpoint; F2-30 missed 54 field
 * pairs). It writes {@code target/permission-usage.txt} for
 * {@code Appendix/scripts/grant_tightness.py} to join against {@code iam.role_permissions}.
 *
 * <p>The assertions here are the ones that need no database: the extraction still works, and
 * every guard is well-formed. The tightness verdict itself is the script's job.
 */
class PermissionCatalogueUsageTest {

    /** {@code hasPermission('MODULE','ACTION')} in any whitespace variant used in the codebase. */
    private static final Pattern HAS_PERMISSION =
        Pattern.compile("hasPermission\\(\\s*'([A-Z_]+)'\\s*,\\s*'([A-Z_]+)'\\s*\\)");

    /** Where the report script picks up the code-side half: the distinct pairs. */
    private static final Path OUT = Path.of("target", "permission-usage.txt");

    /**
     * And the guard sites behind them, {@code CLASS#member MODULE:ACTION} per line.
     *
     * <p>The pair list alone answers "is this permission guarded anywhere", which is enough to
     * find a dead permission but not enough to act on one: a report that says
     * {@code REPORTING:WRITE} is held by no role is a curiosity until it also says which three
     * endpoints that closes. Emitted from the same reflection pass so the two files cannot
     * disagree.
     */
    private static final Path SITES = Path.of("target", "permission-usage-sites.txt");

    /**
     * The subset of sites whose guard ANDs a {@code hasAnyRole} leg onto the permission.
     *
     * <p>This is the report's honesty margin. The tightness join answers "who holds this
     * permission", which equals "who gets through this endpoint" only when the permission is the
     * whole guard. Where a second leg is ANDed on, holding the permission is necessary but not
     * sufficient, and the report must not claim otherwise.
     *
     * <p>Emitted rather than remembered, and the reason is a third repeat of the same lesson:
     * grepping for the two calls on one line returns <b>3</b> files, because a guard that spans
     * two source lines is invisible to a line-oriented match. Reflection over the assembled
     * annotation value says <b>73 sites</b> — 5% of the guard set, not 0.2%.
     */
    private static final Path COMPOSED = Path.of("target", "permission-usage-composed.txt");

    /**
     * Distinct MODULE:ACTION pairs referenced by any {@code @PreAuthorize} in the platform.
     *
     * <p><b>91 distinct pairs across 1,432 guard sites</b>, measured 2026-07-26. The floor is
     * set slightly below that (85) rather than at it: F2-22b will legitimately remove a guard
     * here and there as endpoints move to DTOs, and a floor that tracks the exact number would
     * fail on honest work. It is still tight enough to catch what it exists to catch — a broken
     * extraction returning near-nothing, which would make the tightness report cheerfully
     * declare every catalogued permission unused.
     */
    private static final int BASELINE_DISTINCT_PAIRS = 85;

    @Test
    void everyGuardedPermissionIsRecordedForTheTightnessReport() throws Exception {
        Set<String> pairs = new TreeSet<>();
        List<String> sites = new ArrayList<>();
        List<String> composed = new ArrayList<>();

        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                continue;
            }
            collect(c.getAnnotation(PreAuthorize.class), c.getSimpleName(), "<class>",
                    pairs, sites, composed);
            for (Method m : c.getDeclaredMethods()) {
                collect(m.getAnnotation(PreAuthorize.class), c.getSimpleName(), m.getName(),
                        pairs, sites, composed);
            }
        }

        Files.createDirectories(OUT.getParent());
        Files.write(OUT, pairs);
        sites.sort(String::compareTo);
        Files.write(SITES, sites);
        composed.sort(String::compareTo);
        Files.write(COMPOSED, composed);

        System.out.println("[F2-13a] distinct MODULE:ACTION pairs guarded in code = " + pairs.size());
        System.out.println("[F2-13a] guard sites = " + sites.size());
        System.out.println("[F2-13a] sites ANDing a hasAnyRole leg = " + composed.size());
        System.out.println("[F2-13a] wrote " + OUT.toAbsolutePath());
        System.out.println("[F2-13a] wrote " + SITES.toAbsolutePath());
        System.out.println("[F2-13a] wrote " + COMPOSED.toAbsolutePath());
        pairs.forEach(p -> System.out.println("  " + p));

        assertThat(pairs)
            .as("The permission-usage extraction found %d distinct MODULE:ACTION pairs; %d were "
                + "measured when F2-13a shipped. A sharp drop means the reflection scan broke, "
                + "not that the guards left — and the grant-tightness report would then wrongly "
                + "report catalogued permissions as unused.", pairs.size(), BASELINE_DISTINCT_PAIRS)
            .hasSizeGreaterThanOrEqualTo(BASELINE_DISTINCT_PAIRS);
    }

    /**
     * A guard that names a module or action the catalogue cannot express is dead on arrival —
     * {@code ModulePermissionEvaluator} matches the granted authority {@code MODULE + "_" +
     * ACTION} exactly, so a lower-case or hyphenated name can never match anything. Cheap to
     * check here and impossible to see by reading one annotation at a time.
     */
    @Test
    void noGuardNamesAPermissionTheEvaluatorCanNeverMatch() {
        List<String> malformed = new ArrayList<>();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Pattern loose = Pattern.compile("hasPermission\\(\\s*'([^']*)'\\s*,\\s*'([^']*)'\\s*\\)");
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                if (pa == null) {
                    continue;
                }
                Matcher mt = loose.matcher(pa.value());
                while (mt.find()) {
                    String mod = mt.group(1);
                    String act = mt.group(2);
                    if (!mod.matches("[A-Z][A-Z_]*") || !act.matches("[A-Z][A-Z_]*")) {
                        malformed.add(c.getSimpleName() + "#" + m.getName()
                            + " -> hasPermission('" + mod + "','" + act + "')");
                    }
                }
            }
        }
        System.out.println("[F2-13a] malformed guards = " + malformed);
        assertThat(malformed)
            .as("ModulePermissionEvaluator matches the granted authority MODULE_ACTION exactly, "
                + "so a module or action that is not UPPER_SNAKE can never be granted — the "
                + "guard is permanently closed and PLATFORM_ADMIN's code bypass hides it.")
            .isEmpty();
    }

    private static void collect(PreAuthorize pa, String cls, String member,
                                Set<String> pairs, List<String> sites, List<String> composed) {
        if (pa == null) {
            return;
        }
        boolean andsARole = pa.value().contains("hasAnyRole") || pa.value().contains("hasRole");
        Matcher m = HAS_PERMISSION.matcher(pa.value());
        while (m.find()) {
            pairs.add(m.group(1) + ":" + m.group(2));
            sites.add(cls + "#" + member + " " + m.group(1) + ":" + m.group(2));
            if (andsARole) {
                composed.add(cls + "#" + member + " " + m.group(1) + ":" + m.group(2));
            }
        }
    }
}
