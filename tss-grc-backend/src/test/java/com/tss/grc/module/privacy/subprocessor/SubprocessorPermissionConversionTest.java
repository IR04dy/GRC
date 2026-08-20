package com.tss.grc.module.privacy.subprocessor;

import com.tss.grc.module.privacy.subprocessor.controller.SubprocessorController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-253 — the PDPL Article 30 sub-processor write guard is a permission, and its read guard is
 * deliberately still a role list.
 *
 * <h2>What this pins and why</h2>
 * {@code WRITE_GUARD} was {@code hasAnyRole('DPO','PLATFORM_ADMIN')}, and {@code DPO} is held by
 * <b>no user</b> in dev or in prod. A role guard gets no {@code PLATFORM_ADMIN} bypass — the
 * evaluator is never consulted — so that expression admitted exactly one account, and the 403
 * everyone else received is indistinguishable from a deliberate refusal. Probed live as
 * {@code compliance} (who holds {@code PRIVACY:WRITE}): all seven write routes answered 403 while
 * {@code admin} created at 201 and the read guard admitted the same caller at 200.
 *
 * <h2>⚠ Measured by REFLECTION over the compiled annotations, never a source regex</h2>
 * This repository has under-counted a security surface by source regex three times (1,763 endpoints
 * from a javadoc sentence; 0 of 389; 75 types). An annotation carries the <em>resolved</em> constant,
 * so reflection also proves the guard string a route really gets rather than the one a reader
 * assembles from a field declaration several screens away — which is precisely how F2-52 audited
 * {@code READ_GUARD} at length and left {@code WRITE_GUARD} one line below it untouched.
 *
 * <h2>⚠⚠ The read half is a FILED OWNER DECISION, and this test is what stops it being converted
 * by accident</h2>
 * {@code READ_GUARD} names {@code CISO}, {@code COMPLIANCE_OFFICER} and {@code RISK_MANAGER}. All
 * three are HELD, and none of them holds {@code PRIVACY:READ}, so converting the read guard drops
 * three live roles; granting them {@code PRIVACY:READ} instead widens them from this register to the
 * whole privacy read surface — the DSR queue and the breach register, which carry data subjects'
 * identities. That is a decision about who may read personal data. So the read guard stays, and
 * {@link #theReadGuardStillNamesRolesAndTheAttachmentSurfaceStillMirrorsIt()} asserts BOTH halves:
 * the guard and {@code AttachmentAccessPolicy}'s {@code SUBPROCESSOR} entry, which mirrors it.
 *
 * <p>That is the direction nobody writes first (F2-199). {@code RoleNameSurfaceRatchetTest} pins the
 * forward case — an entry may not name roles once its parent is permission-guarded — and had no way
 * to say "this entry MUST still name roles, and the day its parent's read guard changes, this pair
 * must move together." The class it guards says in its own comment that the entry <i>"has to move
 * WITH the parent, not later"</i>, and F2-52 found two entries that had not. When a later slice takes
 * the filed read conversion, this test fails until the attachment entry moves in the same commit,
 * which is the whole point.
 */
class SubprocessorPermissionConversionTest {

    private static final String WRITE_PERMISSION = "hasPermission('PRIVACY','WRITE')";

    /** The literal expression this slice retired. Its absence is the positive control. */
    private static final String RETIRED_WRITE_GUARD = "hasAnyRole('DPO','PLATFORM_ADMIN')";

    /**
     * The three roles the filed read decision turns on: named by {@code READ_GUARD}, HELD in both
     * databases, and holding no {@code PRIVACY:READ} grant. Measured 2026-08-20.
     */
    private static final java.util.List<String> READ_ROLES_WITHOUT_THE_PERMISSION =
        java.util.List.of("CISO", "COMPLIANCE_OFFICER", "RISK_MANAGER");

    private static final Path POLICY = Paths.get(
        "src/main/java/com/tss/grc/module/attachment/service/AttachmentAccessPolicy.java");

    // One left-to-right alternation. Two sequential passes corrupt a file whose `//` comment text
    // contains a `/*` — measured at 45% of App.tsx in F2-198, and the corrupted source yields a
    // SMALLER population, which reads exactly like a clean tree.
    private static final Pattern COMMENTS =
        Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    private static Map<String, String> guardsByMethod() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Method m : SubprocessorController.class.getDeclaredMethods()) {
            if (m.isSynthetic() || !java.lang.reflect.Modifier.isPublic(m.getModifiers())) continue;
            boolean mapped = false;
            for (var a : m.getAnnotations()) {
                if (a.annotationType().getName().startsWith("org.springframework.web.bind.annotation")
                    && (a.annotationType().getSimpleName().endsWith("Mapping"))) {
                    mapped = true;
                    break;
                }
            }
            if (!mapped) continue;
            PreAuthorize pre = m.getAnnotation(PreAuthorize.class);
            out.put(m.getName(), pre == null ? null : pre.value());
        }
        return out;
    }

    @Test
    void everyWriteRouteIsGuardedByThePrivacyWritePermission() {
        Map<String, String> guards = guardsByMethod();

        // Anti-vacuity: a reflection walk that resolves nothing passes every assertion below.
        // The controller has 12 guarded routes plus one permitAll public route.
        assertThat(guards)
            .as("reflection resolved no mapped handlers on SubprocessorController — the walk is "
                + "broken, and a broken walk passes every assertion in this class")
            .hasSizeGreaterThanOrEqualTo(12);

        TreeSet<String> writeRoutes = new TreeSet<>();
        for (var e : guards.entrySet()) {
            if (WRITE_PERMISSION.equals(e.getValue())) writeRoutes.add(e.getKey());
        }

        assertThat(writeRoutes)
            .as("the seven PDPL Article 30 mutations must each be guarded by %s. A new write route "
                + "that reaches for a role list instead re-creates F2-253: DPO is held by nobody, "
                + "and a role guard has no administrator bypass, so the route would silently admit "
                + "one account. Resolved: %s", WRITE_PERMISSION, guards)
            .containsExactly("approve", "create", "materialChange", "recordEvent", "review",
                             "transition", "update");
    }

    @Test
    void noRouteOnThisControllerStillCarriesTheRetiredWriteGuard() {
        // The positive control: the literal pre-fix expression. Asserted against the RESOLVED
        // annotation values, so respelling the constant cannot satisfy it.
        assertThat(guardsByMethod().values())
            .as("the retired write guard %s is back on a route. DPO has no holder in either "
                + "database, so this expression admits PLATFORM_ADMIN alone.", RETIRED_WRITE_GUARD)
            .doesNotContain(RETIRED_WRITE_GUARD);
    }

    /**
     * ⚠ Scoped to the seven write routes BY NAME, not to "routes whose guard already equals the
     * permission". The first draft did the latter, so on the pre-fix tree it iterated an empty set
     * and passed — a leg that can only pass, which is worse than no leg because it occupies the
     * place where a reader expects this to be checked. The pre-fix control is what exposed it: 3 of
     * 5 legs fired on master and this was one of the two that did not.
     */
    @Test
    void noWriteRouteRecitesARoleName() {
        Map<String, String> guards = guardsByMethod();
        for (String route : java.util.List.of("approve", "create", "materialChange", "recordEvent",
                                              "review", "transition", "update")) {
            assertThat(guards)
                .as("write route %s is missing from the reflection walk — renamed, or the walk is "
                    + "broken. Either way this assertion would otherwise skip it silently.", route)
                .containsKey(route);
            assertThat(guards.get(route))
                .as("%s is a write route and its guard recites a role name. A role leg ORed onto a "
                    + "permission is WIDER than the permission, and this platform's own ratchet "
                    + "classifies any guard merely MENTIONING hasPermission as 'composed — already "
                    + "tight' (RoleGuardConversionRatchetTest line 929 tests presence, not "
                    + "conjunction), so such a leg would read as a tightening while being the "
                    + "opposite. 96 of the 99 guards in that bucket are ORs — filed.", route)
                .doesNotContain("hasAnyRole").doesNotContain("hasRole");
        }
    }

    /**
     * ⚠ This leg passes on the pre-fix tree TOO, and that is correct rather than rot: it is a
     * <b>pin</b> on a deliberate non-change, not a detector for this slice's edit. Its tripwire fires
     * in the future — the day somebody converts the read guard or repoints the attachment entry
     * without the other. Said out loud because "green on master" is otherwise indistinguishable from
     * a regex that stopped matching, and the other four legs here are change-detectors that do fire.
     */
    @Test
    void theReadGuardStillNamesRolesAndTheAttachmentSurfaceStillMirrorsIt() throws Exception {
        Map<String, String> guards = guardsByMethod();

        String readGuard = guards.get("list");
        assertThat(readGuard)
            .as("SubprocessorController#list has no @PreAuthorize — this test resolves the read "
                + "guard through it, so it cannot silently pass on a renamed method")
            .isNotNull();

        assertThat(readGuard)
            .as("the read guard is no longer a role list. That is the FILED owner decision, not a "
                + "tidy-up: %s are named here, are HELD in both databases, and hold no PRIVACY:READ "
                + "grant — so converting drops three live roles, and granting them the permission "
                + "widens them to the DSR queue and the breach register. If that decision has now "
                + "been taken, move AttachmentAccessPolicy's SUBPROCESSOR entry in the SAME commit "
                + "and update this test.", READ_ROLES_WITHOUT_THE_PERMISSION)
            .contains("hasAnyRole")
            .contains(READ_ROLES_WITHOUT_THE_PERMISSION.toArray(new String[0]));

        // The paired surface. AttachmentAccessPolicy makes an attachment exactly as confidential as
        // its parent's READ guard, so while the read guard is a role list this entry must be one too.
        String policy = COMMENTS.matcher(Files.readString(POLICY)).replaceAll("")
            .replaceAll("\\s+", " ");
        Matcher m = Pattern.compile("DECLARED\\.put\\(\\s*\"SUBPROCESSOR\"\\s*,\\s*([^;]*?)\\);")
            .matcher(policy);
        assertThat(m.find())
            .as("the SUBPROCESSOR entry is gone from AttachmentAccessPolicy. A check that cannot "
                + "find its key passes silently, which is why this is asserted before its body.")
            .isTrue();
        String body = m.group(1);

        assertThat(body)
            .as("SUBPROCESSOR must mirror the read guard, which is still a role list. If it became "
                + "ParentGuard.perm(\"PRIVACY\") while the parent still authorises reads by role, "
                + "then CISO, COMPLIANCE_OFFICER and RISK_MANAGER open the register and 403 on its "
                + "files — the F2-52 failure, in the other direction. Body: %s", body)
            .contains("ParentGuard.roles");

        for (String role : READ_ROLES_WITHOUT_THE_PERMISSION) {
            assertThat(body)
                .as("the SUBPROCESSOR attachment entry no longer names %s, which the parent's read "
                    + "guard still admits — so that role can open a sub-processor record and 403 on "
                    + "its DPA. The two lists have to move together.", role)
                .contains("\"" + role + "\"");
        }
    }

    /**
     * The class javadoc used to state an audience the code did not have — <i>"Write guard is narrow
     * (DPO + Platform Admin)"</i> over an effective audience of one. A sentence that answers the
     * question a reader was about to ask is how a wrong belief propagates (F2-184), so the
     * correction is asserted rather than trusted to survive the next edit.
     */
    @Test
    void theClassCommentNoLongerClaimsTheRetiredWriteAudience() throws Exception {
        Path controller = Paths.get("src/main/java/com/tss/grc/module/privacy/subprocessor/"
            + "controller/SubprocessorController.java");
        String raw = Files.readString(controller);

        // ⚠ Three traps here, and the first two cost a red run on correct code.
        //
        // (1) A prose claim WRAPS, so it must be asserted against FLOWED javadoc — a raw contains()
        //     only ever anchors on whichever fragment the author happened to pick (F2-216).
        // (2) The quote-stripper must therefore not be line-scoped either: `"[^"\n]*"` cannot cross
        //     the newline a wrapped quotation contains, so the quotation stays and reads as a claim.
        // (3) But `unquoted()` over a WHOLE Java file pairs a javadoc citation's opening quote with
        //     some unrelated string literal's quote further down (F2-216). So isolate the comments
        //     FIRST, flow them, and only then strip quoted spans.
        StringBuilder flowed = new StringBuilder();
        Matcher block = Pattern.compile("/\\*\\*[\\s\\S]*?\\*/").matcher(raw);
        while (block.find()) {
            flowed.append(block.group().replaceAll("(?m)^\\s*\\*", " ")
                .replaceAll("\\s+", " ")).append('\n');
        }
        String comments = flowed.toString();
        assertThat(comments)
            .as("no javadoc found in SubprocessorController — the extractor is broken, and a broken "
                + "extractor passes the 'claim is absent' assertion below trivially")
            .hasSizeGreaterThan(500);

        // This register's convention is that a correction QUOTES the sentence it replaces; a
        // detector that cannot tell a quotation from a claim forces every correction to paraphrase
        // away its own evidence (F2-193).
        String unquoted = comments.replaceAll("\"[^\"]*\"", "\"\"");

        assertThat(unquoted)
            .as("the retired claim about the write audience is being asserted again outside a "
                + "quotation. The measured audience of that guard was one account, not two.")
            .doesNotContain("Write guard is narrow");

        assertThat(comments)
            .as("the correction's own evidence has been deleted. Keep the retired sentence as a "
                + "quotation so the next reader can see what changed and why.")
            .contains("Write guard is narrow");
    }
}
