package com.tss.grc.module.ethics.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-250 — the structural half: the AML alert register's subject reference is DERIVED, and its
 * entity-bound PUT cannot perform another route's control.
 *
 * <h2>⚠ Why the load-bearing legs are the POSITIVE ones</h2>
 *
 * <p>F2-247 measured this: on a path that never wrote a field, <i>"the service does not write X
 * from the request"</i> is <b>vacuous</b> — there is no setter for a negative scan to find, so the
 * leg passes forever over the defect it was written for. Here the risk is the mirror: an
 * {@code applyEditable} that quietly regains {@code setStatus} is caught by a ban, but an
 * {@code applySubject} that stops deriving the label is not — the setter is still there, just fed
 * from the wrong side. So the derivation is asserted as an assignment from the resolver, and each
 * detector carries a synthetic positive control proving it fires on the literal pre-fix shape
 * (F2-195): a leg that passes on a clean tree and on a rotted one is not a gate.
 */
class AmlReferenceAndControlContractTest {

    private static final Path SERVICE = Path.of("src", "main", "java", "com", "tss", "grc",
            "module", "ethics", "service", "AmlAlertService.java");
    private static final Path ENTITY = Path.of("src", "main", "java", "com", "tss", "grc",
            "module", "ethics", "entity", "AmlAlert.java");
    private static final Path PAGE = Path.of("..", "tss-grc-ui-v2", "src", "pages", "ethics",
            "EthicsPage.tsx");

    /**
     * ⚠ ONE left-to-right alternation, never two sequential passes — written the obvious way it
     * deletes from a {@code //} comment containing a literal {@code /*} to the next close
     * delimiter, and a corrupted source yields a SMALLER population, which reads exactly like a
     * clean tree (F2-198/F2-211). The branch ORDER within the alternation is a no-op (F2-211's
     * correction); the defect is two passes.
     */
    private static final Pattern COMMENTS =
            Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    private static String code(Path p) {
        try {
            return COMMENTS.matcher(Files.readString(p, StandardCharsets.UTF_8)).replaceAll("");
        } catch (IOException e) {
            throw new AssertionError("cannot read " + p.toAbsolutePath() + " — a gate that cannot "
                    + "read its subject reads exactly like a clean tree", e);
        }
    }

    /** The brace-balanced body of a private method, so a ban is scoped to its own claim. */
    private static String body(String src, String signature) {
        int at = src.indexOf(signature);
        assertThat(at)
                .as("%s must exist — a gate keyed on a method that has been renamed passes while "
                  + "asserting nothing", signature)
                .isGreaterThanOrEqualTo(0);
        int open = src.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return src.substring(open, i + 1);
        }
        throw new AssertionError("unbalanced braces after " + signature);
    }

    private static boolean assigns(String src, String setter, String source) {
        return Pattern.compile("\\." + Pattern.quote(setter) + "\\s*\\(\\s*"
                + Pattern.quote(source)).matcher(src).find();
    }

    // ── the derivation: POSITIVE, and load-bearing ─────────────────────────────────────────────

    @Test
    @DisplayName("the subject's label is written from the RESOLVER, never from the request")
    void theSubjectLabelIsDerived() {
        String subject = body(code(SERVICE), "private void applySubject(");

        assertThat(assigns(subject, "setSubjectName", "derived"))
                .as("the label must be assigned from what the id resolved to. Storing the caller's "
                  + "string beside a resolved id is F2-245's travelling-label defect: the halves "
                  + "then describe different parties and the one on screen has no vote")
                .isTrue();
        // F2-277 — asserted by SHAPE rather than by the old `subjects.requireLabel(` spelling.
        // That convenience was `require` then `labelFor`, and this method now needs BOTH halves of
        // the label, which a resolve-and-label helper cannot give without resolving twice. A gate
        // keyed on an identifier breaks on your own refactor (F2-197), and the claim was never
        // about the method's name.
        assertThat(subject)
                .as("the resolution must be tenant-narrowed through the directory — "
                  + "FK_aml_subject refuses a nonexistent id and knows nothing about tenancy")
                .containsPattern("subjects\\.require(?:Label)?\\s*\\(");
        assertThat(subject)
                .as("F2-277 — and BOTH halves of the label must come from ONE resolve, so the "
                  + "two halves of a single label can never come from two reads")
                .contains("setSubjectNameAr(");
        assertThat(subject.split("subjects\\.require", -1).length - 1)
                .as("exactly one resolve: asking the directory twice is how the English half and "
                  + "the Arabic half come to describe different rows")
                .isEqualTo(1);
        assertThat(assigns(subject, "setSubjectName", "s.getSubjectName()"))
                .as("the label may never be taken straight from the request; the only label that "
                  + "reaches storage unresolved is the label-only branch's, which the schema "
                  + "permits (F2-246)")
                .isFalse();

        // The positive control: the literal pre-fix shape must be rejected by the same detector.
        String preFix = "{ if (s.getSubjectId() != null) t.setSubjectId(s.getSubjectId());"
                      + " if (s.getSubjectName() != null) t.setSubjectName(s.getSubjectName()); }";
        assertThat(assigns(preFix, "setSubjectName", "derived"))
                .as("the detector must FAIL on the shape this slice removed, or it is measuring "
                  + "nothing")
                .isFalse();
        assertThat(assigns(preFix, "setSubjectName", "s.getSubjectName()")).isTrue();
    }

    @Test
    @DisplayName("the editable set names the alert's own facts and none of another route's")
    void theEditableSetExcludesEveryRouteOwnedField() {
        String editable = body(code(SERVICE), "private void applyEditable(");

        for (String setter : new String[]{"setStatus", "setAnalystName", "setAnalystId",
                                         "setSubjectId", "setSubjectName", "setDisposition",
                                         "setDecidedByName", "setStrFiled"}) {
            assertThat(editable)
                    .as("applyEditable must not write %s — the whole point of naming the editable "
                      + "set is that everything omitted is closed by construction (F2-213). A "
                      + "scrub is a list of fields somebody remembered; an allow-list is the set "
                      + "of fields that exist.", setter)
                    .doesNotContain("." + setter + "(");
        }
        assertThat(editable)
                .as("and it must still write the alert's own facts, or the route has stopped "
                  + "working and this gate would not notice")
                .contains(".setTitleEn(").contains(".setSeverity(").contains(".setDescription(");
    }

    @Test
    @DisplayName("both write paths hold the route-owned fields to an echo")
    void bothWritePathsRefuseAChange() {
        String src = code(SERVICE);
        String create = body(src, "public AmlAlert create(");
        String update = body(src, "public AmlAlert update(");

        assertThat(create)
                .as("a create must be held to the machine's initial state, derived from the machine "
                  + "rather than typed a second time (F2-217)")
                .contains("ServerOwnedState.requireInitial(").contains("INITIAL_STATUS");
        assertThat(create)
                .as("and a create may not name an analyst — nothing has assigned one yet")
                .contains("requireEcho(\"analystName\"");
        assertThat(update)
                .as("the PUT must hold BOTH route-owned fields to an echo. Missing the status leg "
                  + "lets it file an STR; missing the analyst leg re-opens F2-249's fix, and that "
                  + "is worse than pre-F2-249 because there is now an id for the label to "
                  + "contradict")
                .contains("requireEcho(\"status\"").contains("requireEcho(\"analystName\"");

        assertThat(body(src, "private static void requireEcho("))
                .as("an echo must be case-INSENSITIVE, because the columns collate that way "
                  + "(F2-179) — and nothing may be written on an echo, so there is no question of "
                  + "whose spelling wins")
                .contains("equalsIgnoreCase");
    }

    @Test
    @DisplayName("the initial state is derived from the machine and declared exactly once")
    void theInitialStateIsNotASecondLiteral() {
        String src = code(SERVICE);

        // ⚠ Scoped to the shape of its claim, not to a count of the literal. A first draft banned
        // every "OPEN" in the file and reported 4; reading them showed one real second literal
        // (assign's `"OPEN".equals(...)`, now INITIAL_STATUS) and two legitimate ones — the
        // analytics bucket list and its getOrDefault key, which enumerate the whole vocabulary for
        // a KPI strip and ask a different question ("awaiting disposition", which is deliberately
        // narrower than the machine's non-terminal complement). A red leg is a finding only if the
        // code is actually wrong (F2-206), so the assertion is the one that was meant: nothing
        // COMPARES a status against a literal root.
        assertThat(src)
                .as("a comparison against a literal \"OPEN\" is a second spelling of the machine's "
                  + "root, which is how the value the server picks and the value it accepts drift "
                  + "apart (F2-196/F2-217) — compare against INITIAL_STATUS")
                .doesNotContain("\"OPEN\".equals")
                .doesNotContain("equals(\"OPEN\")");
        // ⚠ NOT body(): that walks to the first brace after the signature, and a field initialiser
        // has none — it silently returned the NEXT method's body, which is a leg passing or failing
        // for a reason unrelated to its claim (F2-244). Sliced to the initialiser instead.
        int at = src.indexOf("TRANSITIONS = Map.of(");
        assertThat(at).as("the machine must be declared as a Map.of initialiser").isPositive();
        String machine = src.substring(at, src.indexOf(");", at));
        assertThat(machine)
                .as("the root must still be declared by the machine, or the derivation has nothing "
                  + "to resolve — and INITIAL_STATUS would then fail its own self-check at "
                  + "class-init rather than here")
                .contains("\"OPEN\"");

        assertThat(code(ENTITY))
                .as("the entity must NOT initialise status: on an entity binding that makes an "
                  + "omitted key indistinguishable from a supplied \"OPEN\" (F2-237), so the echo "
                  + "rule would refuse every partial update — a fix failing on correct code, the "
                  + "one failure a fix must never have (F2-214)")
                .doesNotContain("private String status =");
    }

    // ── the collector: the form that produced 8 of the 10 unlinked rows ────────────────────────

    @Test
    @DisplayName("the create form offers a KYC subject PICKER, not only a free-text name")
    void theFormCollectsTheLinkNotOnlyTheLabel() {
        String page = code(PAGE);

        assertThat(page)
                .as("the free-text box that used to be the only subject field sent subjectName and "
                  + "never subjectId, which is why subject_id is NULL on 8 of the 10 live alerts in "
                  + "dev and prod — and all 8 of those labels match a registered subject exactly, "
                  + "so the register knew which party each alert was about and could not join to it")
                .contains("kind: 'select-searchable', key: 'subjectId'");
        assertThat(page)
                .as("and the picker must be fed from the real KYC register, measured reachable: "
                  + "GET /api/v1/ethics/kyc is COMPLIANCE:READ and all 13 roles holding "
                  + "COMPLIANCE:WRITE also hold it, so this cannot 403 on open (F2-246)")
                .containsPattern("kycApi\\.list\\s*\\(");
        assertThat(page)
                .as("an unlinked party must be DISCLOSED rather than rendered as if it were a "
                  + "register entry — three states, and the last two must not look alike (F2-209)")
                .contains("amlSubjectUnlinked");
    }

    /**
     * The bilingual half, asserted because F2-208 found this class of drift in the Arabic only and
     * Arabic is the platform's primary language (house rule 7).
     */
    @Test
    @DisplayName("every key this slice added is declared in BOTH dictionaries")
    void bothDictionariesCarryTheNewKeys() {
        String en = code(Path.of("..", "tss-grc-ui-v2", "src", "translations", "en.ts"));
        String ar = code(Path.of("..", "tss-grc-ui-v2", "src", "translations", "ar.ts"));
        for (String key : new String[]{"amlFldSubjectLink", "amlFldSubjectPlaceholder",
                                      "amlFldSubjectUnregistered", "amlFldSubjectUnregPlaceholder",
                                      "amlSubjectHint", "amlSubjectUnlinked",
                                      "amlSubjectUnlinkedHint"}) {
            assertThat(en).as("en.ts must declare %s", key).contains(key + ":");
            assertThat(ar).as("ar.ts must declare %s", key).contains(key + ":");
        }
    }

    /**
     * Anti-vacuity for the comment stripper, and the direction that matters: a stripper that eats
     * too much reports a SMALLER population, which reads exactly like a clean tree (F2-198). Counted
     * by declarations anchored to line starts, which a comment can never satisfy (F2-199).
     */
    @Test
    @DisplayName("self-test: the comment stripper keeps every anchored declaration")
    void theStripperDoesNotEatTheSubject() {
        String raw;
        try {
            raw = Files.readString(SERVICE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Pattern decl = Pattern.compile("(?m)^\\s{4}(?:public|private|static|@)");
        long before = decl.matcher(raw).results().count();
        long after = decl.matcher(code(SERVICE)).results().count();
        assertThat(after)
                .as("the stripper must not remove anchored declarations — %d before, %d after. The "
                  + "two-sequential-pass form deletes 45%% of a file whose comment happens to "
                  + "contain a block-open delimiter.", before, after)
                .isEqualTo(before);
        assertThat(before).as("and the file must actually have declarations to lose")
                .isGreaterThan(10);
    }
}
