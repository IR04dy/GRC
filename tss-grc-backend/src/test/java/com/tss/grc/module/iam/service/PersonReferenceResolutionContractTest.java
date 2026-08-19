package com.tss.grc.module.iam.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-249 — <b>a register that declares a person-id column beside a person-label column must
 * write both halves, and the label must be DERIVED from the id.</b>
 *
 * <h2>The finding</h2>
 *
 * <p>Executing F2-248's work list, the eight actor-attribution members whose parent is a PERSON
 * split cleanly, and the same absence produced two opposite symptoms:
 *
 * <pre>
 *   register                                 id writer   label writer        symptom
 *   platform.signatures      :: signer        principal   DERIVED from id     ✅ the exemplar
 *   privacy.dpia_review      :: reviewer      CurrentActor CurrentActor       ✅ already closed
 *   bcm.plan_review_attest.  :: attested_by   NONE        principal           honest, unlinkable
 *   bcm.bcp_plans            :: last_attest.  NONE        derived from attest honest, unlinkable
 *   bcm.plan_acknowledgements:: distributed_by NONE       principal           honest, unlinkable
 *   ethics.aml_alerts        :: analyst       NONE        CALLER FREE TEXT    a claim
 *   ethics.cases             :: investigator  NONE        CALLER FREE TEXT    a claim
 *   evidence.evidence_items  :: collector     NONE        NONE                no writer at all
 * </pre>
 *
 * <p>Measured identical in dev and prod: the id half was NULL on 10 of 10 AML alerts, 5 of 5 plan
 * attestations, 8 of 8 plan distributions and 8 of 8 plans, and <b>0 of the 3 analyst labels and 0
 * of the 14 investigator labels resolved to any real user</b>.
 *
 * <h2>🔑 Why the assertions below are POSITIVE</h2>
 *
 * <p>F2-247's lesson: on a path that never wrote the field, <i>"the service does not write X from
 * the request"</i> is <b>vacuous</b> — there is no setter for a scan to find, so the leg passes
 * forever over the defect it was written for. The load-bearing assertion has to be that the id
 * half <b>is</b> written, and written from the resolver or the principal.
 *
 * <h2>⚠ What is deliberately NOT asserted</h2>
 *
 * <p>That a bare label is refused. The schema settles that per column (F2-246): a nullable id
 * <em>beside</em> a nullable name states that an unresolvable party is representable, and the live
 * data proves the register means it — {@code 'Financial Crime Unit'}, {@code 'Internal Audit'},
 * {@code 'External Investigator'}. A gate demanding refusal there would drive a fix that fails on
 * correct usage, which is the one thing a gate must never do (F2-214).
 *
 * <p>And a hard zero over <i>"nobody may hand-spell a tenant-narrowed user load"</i> was
 * <b>measured and rejected</b>: 25 such sites exist across 9 files, and most are correct
 * ({@code UserService} resolving its own aggregate, {@code WorkflowRequestService}'s recipient
 * reach). Measuring before writing the assertion is what stopped that shipping.
 */
class PersonReferenceResolutionContractTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "tss", "grc");

    /** Strip line and block comments in ONE left-to-right alternation (the F2-198/F2-211 trap). */
    private static final Pattern COMMENTS =
            Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    private static String body(String relative) throws IOException {
        String src = Files.readString(MAIN.resolve(relative), StandardCharsets.UTF_8);
        return COMMENTS.matcher(src).replaceAll(m -> " ".repeat(m.group().length()));
    }

    /** Assert a CALL, never a bare token — an import or a javadoc satisfies a substring (F2-194). */
    private static boolean calls(String src, String receiverAndMethod) {
        return Pattern.compile(Pattern.quote(receiverAndMethod) + "\\s*\\(").matcher(src).find();
    }

    private static int count(String src, String regex) {
        Matcher m = Pattern.compile(regex).matcher(src);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. The primitive exists, and is the only spelling of the label
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the directory is blank-safe, and is the one place the display label is spelled")
    void theDirectoryOwnsTheLabelSpelling() throws IOException {
        String src = body("module/iam/service/TenantUserDirectory.java");

        assertThat(src)
                .as("labelFor must fall back on a BLANK full name, not merely a null one. Of the "
                  + "two live copies this replaced, one was null-only — so a user whose "
                  + "full_name_en was the empty string rendered as an empty register cell in one "
                  + "place and as their username in the other. That divergence is the whole "
                  + "argument for sharing it (F2-177).")
                .contains("isBlank()");

        assertThat(src)
                .as("resolve() must narrow by tenant, or the primitive is a cross-tenant read "
                  + "dressed up as a safe one")
                .contains("org.equals(u.getOrganisationId())");
        assertThat(src)
                .as("and by active status — an id resolving to a deactivated user names nobody who "
                  + "can act")
                .contains("u.getIsActive()");
    }

    /**
     * Every register that resolves a person now goes through the primitive, so a future reader
     * cannot reasonably write a fifth spelling. Asserted by CALL, not by import (F2-205).
     */
    @Test
    @DisplayName("every converted register resolves through the one primitive")
    void everyConvertedRegisterUsesThePrimitive() throws IOException {
        Map<String, String> sites = new LinkedHashMap<>();
        sites.put("module/ethics/service/AmlAlertService.java", "directory.requireLabel");
        sites.put("module/ethics/service/EthicsCaseService.java", "directory.requireLabel");
        sites.put("module/ethics/coi/service/CoiService.java", "directory.require");
        sites.put("module/signature/service/SignatureService.java", "directory.resolve");
        sites.put("module/admin/committee/service/CommitteeMemberRegistryService.java",
                  "directory.require");

        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> e : sites.entrySet()) {
            if (!calls(body(e.getKey()), e.getValue())) missing.add(e.getKey() + " -> " + e.getValue());
        }
        assertThat(missing)
                .as("F2-190 — a second mechanism beside an existing one is how two copies drift "
                  + "with nobody maintaining the agreement. If a site legitimately stops needing "
                  + "the primitive, delete its entry here in the same commit.")
                .isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2. The POSITIVE invariant — the id half is written
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * 🔑 The load-bearing leg. Each entry is (file, the setter for the id half, the expression it
     * must be written from). A path that writes the label and not the id is exactly the defect,
     * and it has no setter for a negative scan to find (F2-247).
     */
    private static final Map<String, String[]> ID_HALF_WRITERS = new TreeMap<>(Map.of(
            "module/ethics/service/AmlAlertService.java",
            new String[]{"setAnalystId", "req.analystUserId()"},

            // `userId` is the local the resolver validated, not the raw body value: the body's
            // `investigatorUserId` is parsed into it and `directory.requireLabel` must succeed
            // before either half is written.
            "module/ethics/service/EthicsCaseService.java",
            new String[]{"setInvestigatorId", "userId"},

            "module/bcm/cycle/service/PlanReviewService.java",
            new String[]{"setAttestedById", "CurrentActor.requireUserId"},

            "module/bcm/cycle/service/PlanDistributionService.java",
            new String[]{"setDistributedById", "CurrentActor.requireUserId"}
    ));

    @Test
    @DisplayName("every closed register WRITES the id half, from the resolver or the principal")
    void theIdHalfIsWrittenEverywhere() throws IOException {
        List<String> broken = new ArrayList<>();
        for (Map.Entry<String, String[]> e : ID_HALF_WRITERS.entrySet()) {
            String src = body(e.getKey());
            String setter = e.getValue()[0];
            String source = e.getValue()[1];
            if (!calls(src, "." + setter)) {
                broken.add(e.getKey() + ": nothing calls " + setter + "(...)");
                continue;
            }
            // The setter's argument must name the source.
            //
            // ⚠ This used to carry a blanket fallback — "if the file calls directory.requireLabel
            // anywhere, accept it" — because EthicsCaseService assigns the resolved id to a local
            // first. The mutation harness killed that idea: deleting `c.setInvestigatorId(userId)`
            // SURVIVED, because the file still contains `c.setInvestigatorId(null)` in the
            // no-assignee branch AND still calls requireLabel, so both the presence check and the
            // fallback were satisfied by something that was not the claim. F2-217's failure mode,
            // in a leg written to be the load-bearing one.
            Matcher m = Pattern.compile("\\." + setter + "\\s*\\(([^;]{0,200})").matcher(src);
            boolean ok = false;
            boolean anyNonNull = false;
            while (m.find()) {
                String arg = m.group(1).trim();
                if (!arg.startsWith("null")) anyNonNull = true;
                if (arg.contains(source)) { ok = true; }
            }
            if (!anyNonNull)
                broken.add(e.getKey() + ": " + setter + " is only ever called with null — the id "
                         + "half is cleared but never written");
            else if (!ok)
                broken.add(e.getKey() + ": " + setter + " is not written from " + source);
        }
        assertThat(broken)
                .as("This is the POSITIVE assertion and it is the point of the whole gate. A "
                  + "register that writes only the label is the F2-249 defect, and it leaves "
                  + "nothing for a 'does not write X' leg to match — so that leg would pass "
                  + "forever over it (F2-247).")
                .isEmpty();
    }

    /**
     * The parent plan's pair must be derived from the attestation row just written, together —
     * re-resolving either half independently is how a snapshot drifts from its source (F2-245).
     */
    @Test
    @DisplayName("the plan's attestation pair is derived from the saved row, both halves together")
    void theParentPairIsDerivedFromTheSavedRow() throws IOException {
        String src = body("module/bcm/cycle/service/PlanReviewService.java");
        assertThat(src).contains("setLastAttestedByName(saved.getAttestedByName())");
        assertThat(src)
                .as("the id half must come from the SAME saved row as the label half — a second "
                  + "CurrentActor call here would work today and diverge the moment anything "
                  + "writes an attestation on someone else's behalf")
                .contains("setLastAttestedById(saved.getAttestedById())");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3. The label may not travel beside the id unchecked
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("where an id is supplied, a contradicting label is refused rather than stored")
    void aLabelThatContradictsTheIdIsRefused() throws IOException {
        for (String f : List.of("module/ethics/service/AmlAlertService.java",
                                "module/ethics/service/EthicsCaseService.java")) {
            String src = body(f);
            assertThat(src)
                    .as("%s must compare the supplied label against the DERIVED one and throw. "
                      + "Storing the caller's label beside a resolved id is the F2-245 shape: the "
                      + "row then describes two different people and the half on screen is the "
                      + "one with no vote.", f)
                    .containsPattern("equalsIgnoreCase\\s*\\(\\s*derived\\s*\\)");
            assertThat(src)
                    .as("%s must accept an ECHO — a form that round-trips the row has to keep "
                      + "working (F2-231/F2-234)", f)
                    .contains("!");
        }
    }

    /** The {@code "system"} placeholder is gone. F2-236: a placeholder attribution is worse than a
     *  refusal, because it reads as an automated act when a human performed it — or when nobody did. */
    @Test
    @DisplayName("the 'system' attestation placeholder is gone")
    void theSystemPlaceholderIsGone() throws IOException {
        String src = body("module/bcm/cycle/service/PlanReviewService.java");
        assertThat(src)
                .as("`byName == null ? \"system\" : byName` recorded an ISO 22301 plan review "
                  + "attestation as performed by an automation. Scoped to the shape rather than "
                  + "to the word, because 'system' is an ordinary word (F2-205).")
                .doesNotContainPattern("\\?\\s*\"system\"");
        assertThat(src)
                .as("and the fallback must REFUSE instead")
                .contains("CurrentActor.requireUsername");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 4. Self-test: the detector fires on the literal pre-fix shapes
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * F2-211's lesson: a detector that cannot match reads exactly like a clean tree, and it
     * UNDER-reports. These are the shapes as they were on master.
     */
    @Test
    @DisplayName("self-test: the detectors fire on the pre-fix shapes and not on the fixed ones")
    void theDetectorsDiscriminate() {
        String prefixAml = "a.setAnalystName(analystName); if (\"OPEN\".equals(a.getStatus()))";
        assertThat(count(prefixAml, "\\.setAnalystId\\s*\\("))
                .as("the pre-fix AML assign wrote only the label — the id-half detector must "
                  + "report ZERO on it, which is what makes a non-zero reading meaningful")
                .isZero();

        String fixedAml = "a.setAnalystId(req.analystUserId()); a.setAnalystName(derived);";
        assertThat(count(fixedAml, "\\.setAnalystId\\s*\\(")).isOne();

        String prefixBcm = "row.setAttestedByName(byName == null ? \"system\" : byName);";
        assertThat(Pattern.compile("\\?\\s*\"system\"").matcher(prefixBcm).find())
                .as("the placeholder detector must fire on the literal pre-fix line")
                .isTrue();
        assertThat(Pattern.compile("\\?\\s*\"system\"")
                        .matcher("row.setAttestedByName(CurrentActor.requireUsername(\"x\"));").find())
                .as("and not on the fixed one")
                .isFalse();

        assertThat(calls("import com.tss.grc.module.iam.service.TenantUserDirectory;",
                         "directory.require"))
                .as("an IMPORT must not satisfy the call check — F2-194's substring trap, which "
                  + "cost two surviving mutants in that slice")
                .isFalse();
        assertThat(calls("directory.require(id, \"x\")", "directory.require")).isTrue();
    }
}
