package com.tss.grc.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-52 — the <b>second</b> authorisation surface that decides by role name, and the one no ratchet
 * has ever measured.
 *
 * <h3>Why this exists</h3>
 * <p>{@link RoleGuardConversionRatchetTest} scans {@code @PreAuthorize} by reflection and counts role
 * names absent from {@code iam.roles}. Its own javadoc flagged, in as many words, that the count is
 * incomplete — {@code TPRM_LEAD} lived in {@code @PreAuthorize} <em>and</em> in a Java role
 * {@code Set} "that no {@code @PreAuthorize} scan sees" — and warned that "the ghost count falling is
 * <b>not</b> evidence the name is gone from the authorisation surface". Nobody built the scan, and the
 * prediction came true exactly: measured 2026-08-07, {@code TPRM_LEAD} is gone from every guard in the
 * platform and still live in {@link com.tss.grc.module.attachment.service.AttachmentAccessPolicy}.
 * A documented gap in a measurement is still an unmeasured gap.
 *
 * <h3>What the second surface is</h3>
 * <p>{@code AttachmentAccessPolicy} decides who may download an attachment by <b>mirroring the parent
 * record's own read guard</b>, so where a parent authorises by role it holds a role list. That makes it
 * a genuine authorisation decision — not a copy of one — and a ghost name in it is the same
 * permanently-false leg it is in a guard: the file is less downloadable than the code reads.
 *
 * <h3>⚠ What this test found on its first run, which is why it is a fixture and not a script</h3>
 * <p>Two entries had <b>drifted from the parents they mirror</b>, in the one direction that hurts:
 * <ul>
 *   <li>{@code IA_ENGAGEMENT} carried an 11-name role list while {@code IaController} had already
 *       become {@code hasPermission('AUDIT','READ')} — pinned by {@code IaControllerGuardTest}, whose
 *       javadoc even lists the names the guard "used to recite". 11 roles hold {@code AUDIT:READ} and
 *       5 were unnamed, so {@code compliance} and {@code viewer} could open an IA engagement and 403
 *       on its files.</li>
 *   <li>{@code VENDOR_BCP_CAMPAIGN} named {@code TPRM_LEAD} while {@code VendorBcpCampaignController}
 *       had become {@code hasPermission('TPRM','READ')} — pinned by {@code TprmPermissionConversionTest},
 *       which records all 39 of that module's role guards converting. 17 roles hold {@code TPRM:READ}
 *       and <b>13</b> were unnamed: {@code auditor}, {@code compliance}, {@code so_officer} and
 *       {@code viewer} all affected.</li>
 * </ul>
 * <p>The class's own {@code FRAUD_CASE} comment states the rule that was broken — <i>"It has to move
 * WITH the parent, not later"</i> — so this is not a missing instruction, it is an instruction with no
 * enforcement. Two converting slices read it and did not act on it. That is what a test is for.
 *
 * <h3>Deliberately narrow</h3>
 * <p>Only role <em>collections</em> in this one class are scanned. An earlier draft anchored on
 * {@code Set.of(} and {@code List.of(} across all of {@code src/main/java} and reported roughly 200
 * "ghosts" — every status vocabulary, committee type and HTTP method in the codebase. A role scan must
 * anchor on constructs that are about roles or it invents defects wholesale, and the number it invents
 * is large enough to look like a finding.
 */
class RoleNameSurfaceRatchetTest {

    private static final Path POLICY = Paths.get(
        "src/main/java/com/tss/grc/module/attachment/service/AttachmentAccessPolicy.java");

    /**
     * Ghost names on this surface, measured 2026-08-07 after F2-52's sweep: <b>4</b>.
     * <b>MUST only ever decrease.</b>
     *
     * <p>The survivors, each with the reason it is not simply renamed away — they are the same four
     * mechanisms {@link RoleGuardConversionRatchetTest} adjudicates, seen from the attachment side:
     * <ul>
     *   <li>{@code MODEL_OWNER} ({@code AI_MODEL}) — entity-bound. {@code ml_model.model_owner_user_id}
     *       is populated 25/25; seating the name would be looser than the column.</li>
     *   <li>{@code AUDIT_COMMITTEE_MEMBER} ({@code SANCTIONS_CHECK}) — a committee seat, not a job
     *       family. F2-26 shipped the roster linkage; no SpEL function asks the question yet.</li>
     *   <li>{@code TPRM}, {@code BCM} — <b>scan artefacts, not ghosts</b>, and recorded so the number
     *       is honest: they are the first argument of {@code permOrRoles(module, roles...)}, which this
     *       extractor cannot separate from the role varargs without parsing Java properly. Counting them
     *       is the conservative direction — an extractor that guessed which argument was a module could
     *       silently stop seeing a real ghost.</li>
     * </ul>
     *
     * <p>⚠ Before F2-52 this was <b>9</b>: the four above plus {@code CIO} and {@code CRO} (seated by
     * V373), {@code PRIVACY_OFFICER} (corrected to {@code PRIVACY_MANAGER}), and
     * {@code CHIEF_AUDIT_EXECUTIVE}, {@code AUDIT_DIRECTOR}, {@code INTERNAL_AUDITOR_LEAD},
     * {@code TPRM_LEAD} (deleted with the two drifted entries). Three of those four
     * — {@code CHIEF_AUDIT_EXECUTIVE}, {@code INTERNAL_AUDITOR_LEAD}, {@code TPRM_LEAD} — appeared in
     * <b>no {@code @PreAuthorize} anywhere</b>, so the guard ratchet never counted them and never could.
     */
    private static final int BASELINE_SURFACE_GHOSTS = 4;

    /**
     * Role collections in the class, so a collapsed extractor cannot report a clean surface.
     *
     * <p>⚠ Measured with <em>this</em> extractor: 26 collections, 23 distinct names. The first draft set
     * the name floor to 25 from a throwaway Python scan that also swept {@code perm(...)} module codes,
     * and it failed on a correct surface. <b>An anti-vacuity floor calibrated against a different
     * extractor is not a floor, it is a guess</b> — and had it been guessed 5 low instead of 2 high the
     * test would have passed while measuring almost nothing, which is the failure that does not announce
     * itself.
     */
    private static final int MIN_ROLE_COLLECTIONS = 15;
    private static final int MIN_DISTINCT_NAMES = 18;

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("(?m)^\\s*//.*$");
    /** {@code ParentGuard.roles(...)} / {@code permOrRoles(...)}, and the shared {@code String[]}. */
    private static final Pattern ROLE_CONSTRUCT = Pattern.compile(
        "ParentGuard\\.(?:roles|permOrRoles)\\s*\\(|String\\[\\]\\s+[A-Z_]+\\s*=\\s*\\{");
    private static final Pattern LITERAL = Pattern.compile("\"([A-Za-z][A-Za-z0-9_]*)\"");

    private record Surface(List<String> names, int collections) { }

    private static Surface scan() throws Exception {
        String raw = Files.readString(POLICY);
        // Comments first, and this is not tidiness: this class documents its role lists heavily, and a
        // javadoc naming TPRM_LEAD while explaining that TPRM_LEAD was removed would read to a regex as
        // a live authorisation decision. The same confusion cost F2-154 two mutation rounds.
        String src = LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(raw).replaceAll("")).replaceAll("");

        List<String> names = new ArrayList<>();
        int collections = 0;
        Matcher m = ROLE_CONSTRUCT.matcher(src);
        while (m.find()) {
            int open = Math.max(src.lastIndexOf('(', m.end() - 1), src.lastIndexOf('{', m.end() - 1));
            if (open < 0) continue;
            collections++;
            Matcher lit = LITERAL.matcher(balanced(src, open));
            while (lit.find()) {
                names.add(lit.group(1));
            }
        }
        return new Surface(names, collections);
    }

    /** From the opening bracket to its match, so a nested call cannot end the extraction early. */
    private static String balanced(String text, int start) {
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(' || c == '{') depth++;
            else if (c == ')' || c == '}') {
                depth--;
                if (depth == 0) return text.substring(start, i + 1);
            }
        }
        return text.substring(start, Math.min(text.length(), start + 800));
    }

    @Test
    void theAttachmentPolicyNamesNoRoleThatDoesNotExist() throws Exception {
        Surface s = scan();
        TreeSet<String> ghosts = new TreeSet<>();
        for (String n : s.names()) {
            if (!RoleGuardConversionRatchetTest.ROLE_CATALOGUE.contains(n)) ghosts.add(n);
        }
        TreeSet<String> distinct = new TreeSet<>(s.names());
        System.out.println("[F2-52] AttachmentAccessPolicy role collections = " + s.collections()
            + " ; distinct names = " + distinct.size()
            + " ; absent from iam.roles = " + ghosts.size() + " -> " + ghosts);

        // Anti-vacuity, both dimensions: an extractor that found no constructs, or found them and
        // pulled no names out, reports a spotless surface. That is the reassuring direction, so it is
        // the one that has to be impossible.
        assertThat(s.collections())
            .as("found %d role collections in AttachmentAccessPolicy; the class holds well over %d. "
                + "A collapsed extractor reports zero ghosts.", s.collections(), MIN_ROLE_COLLECTIONS)
            .isGreaterThanOrEqualTo(MIN_ROLE_COLLECTIONS);
        assertThat(distinct.size())
            .as("extracted %d distinct names from %d collections — too few to be the real surface.",
                distinct.size(), s.collections())
            .isGreaterThanOrEqualTo(MIN_DISTINCT_NAMES);

        assertThat(ghosts.size())
            .as("%d role names in AttachmentAccessPolicy match no row in iam.roles, against a baseline "
                + "of %d. Each is a permanently false leg, so a user who can read the parent record "
                + "cannot download its files. Correct the name, seat the role, or move the entry to "
                + "the parent's permission — do not raise the baseline. Names: %s",
                ghosts.size(), BASELINE_SURFACE_GHOSTS, ghosts)
            .isLessThanOrEqualTo(BASELINE_SURFACE_GHOSTS);
    }

    /**
     * The drift this test was written for: an entry may not name roles when the parent it mirrors has
     * stopped authorising by role.
     *
     * <p>Kept as named pairs rather than a general rule because the general rule needs the parent
     * controller's live guard, which is {@link RoleGuardConversionRatchetTest}'s reflection dump and not
     * available here. These four are the converted modules whose entries were, or could next be, left
     * behind — each verified against its parent controller on 2026-08-07.
     */
    @Test
    void anEntryDoesNotNameRolesWhenItsParentIsPermissionGuarded() throws Exception {
        String src = LINE_COMMENT.matcher(
            BLOCK_COMMENT.matcher(Files.readString(POLICY)).replaceAll("")).replaceAll("");
        String flat = src.replaceAll("\\s+", " ");

        Map<String, String> converted = new LinkedHashMap<>();
        converted.put("IA_ENGAGEMENT",
            "IaController is hasPermission('AUDIT','READ') — IaControllerGuardTest pins it");
        converted.put("VENDOR_BCP_CAMPAIGN",
            "VendorBcpCampaignController is hasPermission('TPRM','READ') — TprmPermissionConversionTest");
        converted.put("FRAUD_CASE",
            "FraudController converted in F2-05; V328 catalogued FRAUD");
        converted.put("WHISTLEBLOWER_CASE",
            "ethics converted in F2-145(e) slice 6; V371 catalogued ETHICS:READ_CASES");

        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, String> e : converted.entrySet()) {
            Matcher m = Pattern.compile("DECLARED\\.put\\(\\s*\"" + e.getKey() + "\"\\s*,\\s*([^;]*?)\\);")
                .matcher(flat);
            assertThat(m.find())
                .as("entry %s is gone from AttachmentAccessPolicy. If the entity type was retired, "
                    + "remove it here too; if it was renamed, this test must follow it, because the "
                    + "check silently passes on a key it cannot find.", e.getKey())
                .isTrue();
            String body = m.group(1);
            if (body.contains("ParentGuard.roles") || body.contains("permOrRoles")) {
                wrong.add(e.getKey() + " -> " + body.trim() + "   (" + e.getValue() + ")");
            }
        }

        assertThat(wrong)
            .as("these entity types mirror a parent that authorises by PERMISSION, and the entry still "
                + "lists role names. Every role holding the permission but absent from the list can "
                + "open the record and 403 on its attachments — the exact failure this class's own "
                + "FRAUD_CASE comment warns about, measured twice in F2-52. Offenders: %s", wrong)
            .isEmpty();
    }
}
