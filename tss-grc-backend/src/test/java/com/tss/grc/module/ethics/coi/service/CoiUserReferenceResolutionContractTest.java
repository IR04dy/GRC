package com.tss.grc.module.ethics.coi.service;

import com.tss.grc.module.ethics.coi.dto.CoiDtos;
import com.tss.grc.module.ethics.coi.entity.CoiRecusalEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-244 — a caller-supplied {@code user_id} on the COI registers must be RESOLVED, and the
 * register must be able to name the person it resolves to.
 *
 * <h3>What was wrong</h3>
 * {@code CoiService#recordRecusal} stored {@code req.userId()} verbatim, three lines below a
 * comment announcing that it validates the <em>other</em> foreign key
 * (<em>"If a declaration id is provided, validate it belongs to this tenant."</em>). F2-215's
 * two-of-N at its shortest range yet — and the split ran the same way in the database:
 * {@code declaration_id} carries {@code FK_coi_recusal_decl}, {@code user_id} carries no
 * constraint at all. The second writer, {@code declarantFor}, was rigorous about the
 * <em>authority</em> to file on somebody else's behalf (F2-145(e) converted three role names to
 * {@code ETHICS:WRITE} there) and never asked whether the person it named existed.
 *
 * <p>🔑 <b>An authority check on the caller is not a check on the referent.</b> That is this
 * slice's addition to F2-243's rule that a presence test on an id is not a check on the thing it
 * names.
 *
 * <h3>Why it mattered</h3>
 * {@code CoiRecusalEvent} declares {@code user_id} NOT NULL and <b>no name column of any kind</b>,
 * so that id is the only thing identifying who recused — and the entity's own javadoc says the
 * subject is a <em>"director, committee member, or senior executive"</em> whose recusal the
 * Compliance Officer reports under SAMA Article 30. Measured identical in dev and prod:
 * <b>3 of 8 recusals and 8 of 8 non-null declarations name ids in the 1001–1007 range while
 * {@code MAX(iam.users.id)} is 8.</b>
 *
 * <p>The display half was the same defect seen from the client: {@code lib/coi.ts}'s
 * {@code RecusalResponse} has always declared {@code userNameEn} / {@code userNameAr}, the server
 * record declared neither, and {@code CoiRegisterPage} rendered the name behind
 * {@code selected.userNameEn && …} — F2-211's shape, with the consequence that the register named
 * the person <em>nowhere</em>: the table had no person column either.
 *
 * <h3>Shape of the assertions</h3>
 * Every leg is scoped to the shape of its claim, never to the spelling that was removed — the
 * mistake F2-205 and F2-209 each made in a gate written after quoting the rule. The pre-fix and
 * post-fix shapes are both carried as synthetic controls, because a leg that passes on a correct
 * tree is indistinguishable from a rotted regex (F2-195).
 */
class CoiUserReferenceResolutionContractTest {

    private static final Path SERVICE = Path.of(
        "src/main/java/com/tss/grc/module/ethics/coi/service/CoiService.java");
    /** F2-249 — where the resolve-in-tenant predicate lives now that it is shared. */
    private static final Path DIRECTORY = Path.of(
        "src/main/java/com/tss/grc/module/iam/service/TenantUserDirectory.java");
    private static final Path FE_CLIENT = Path.of("..", "tss-grc-ui-v2", "src", "lib", "coi.ts");
    private static final Path FE_PAGE = Path.of(
        "..", "tss-grc-ui-v2", "src", "pages", "ethics", "coi", "CoiRegisterPage.tsx");

    /**
     * Strip comments but KEEP newlines, so a reported position still maps to its real line.
     * F2-243 blanked matches with spaces, which preserves byte offsets and destroys line numbers —
     * and a wrong line number sends the reader to unrelated code, reading exactly like a false
     * finding. Written as ONE left-to-right alternation: at a {@code //} an inner {@code /*} can
     * never open a block, and at a {@code /*} a URL cannot truncate it (F2-198; and per F2-211 the
     * defect there is two passes, not the branch order).
     */
    private static final Pattern COMMENT = Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    static String strip(String src) {
        Matcher m = COMMENT.matcher(src);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(
                m.group().replaceAll("[^\\n]", " ")));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String read(Path p) throws IOException {
        assertThat(Files.exists(p)).as("%s must exist for this gate to mean anything", p).isTrue();
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    // ── the defect's own shape, and the fix's ────────────────────────────────────────────────

    /**
     * The user reference reaching a setter or a return straight out of the request. Scoped to the
     * SHAPE (any {@code req.userId()} flowing into the stored reference) rather than to the exact
     * line that was deleted, so re-introducing it under a different spelling still fails.
     */
    private static final Pattern RAW_USER_REFERENCE = Pattern.compile(
        "setUserId\\s*\\(\\s*req\\s*\\.\\s*userId\\s*\\(\\s*\\)\\s*\\)"
      + "|return\\s+req\\s*\\.\\s*userId\\s*\\(\\s*\\)\\s*;");

    private static final Pattern RESOLVER_DECLARED = Pattern.compile(
        "private\\s+User\\s+requireOwnUser\\s*\\(");
    private static final Pattern RESOLVER_CALLED = Pattern.compile("requireOwnUser\\s*\\(");

    @Test
    @DisplayName("no COI writer stores a user reference the service has not resolved")
    void noWriterStoresAnUnresolvedUserReference() throws IOException {
        String src = strip(read(SERVICE));
        assertThat(RAW_USER_REFERENCE.matcher(src).find())
            .as("""
                CoiService must not take the recusal/declaration subject straight from the request.
                A user id that resolves to nobody leaves a SAMA Article 30 governance record
                attributable to nobody, and this register has no name column to fall back on.""")
            .isFalse();
    }

    @Test
    @DisplayName("BOTH writers of the reference call the one resolver — not just the one noticed")
    void bothWritersCallTheResolver() throws IOException {
        String src = strip(read(SERVICE));

        assertThat(RESOLVER_DECLARED.matcher(src).results().count())
            .as("""
                requireOwnUser must be declared exactly once. Two resolvers is how the platform
                grew four DSR registers and five CAPA vocabularies (F2-190/F2-196) — and the
                exemplar this one copies, CommitteeMemberRegistryService#requireUserInTenant,
                is itself a single method for the same reason.""")
            .isEqualTo(1L);

        // Declaration + call sites: one declaration, and one call in EACH writer.
        long calls = RESOLVER_CALLED.matcher(src).results().count() - 1; // minus the declaration
        assertThat(calls)
            .as("""
                Both writers must resolve: recordRecusal (the recusal's subject) and declarantFor's
                may-file-for-others branch (the declaration's declarant). Closing only the one that
                was noticed is F2-215's two-of-N, which is the defect this slice fixed — with the
                twist that declarantFor was rigorous about WHO MAY NAME another person and silent
                about whether the person named exists.""")
            .isGreaterThanOrEqualTo(2L);

        assertThat(methodBody(src, "recordRecusal"))
            .as("recordRecusal must resolve the recusal's subject")
            .contains("requireOwnUser(");
        assertThat(methodBody(src, "declarantFor"))
            .as("declarantFor must resolve a declarant it is asked to file on behalf of")
            .contains("requireOwnUser(");
    }

    /**
     * ⚠ F2-249 — this leg FOLLOWS THE DELEGATION rather than asserting the predicate's location.
     *
     * <p>The predicate moved into {@code TenantUserDirectory} when four hand-written copies of it
     * were consolidated. Scoped to {@code CoiService}'s own body, this leg then went red on
     * <b>correct code</b> — the one failure a gate must never have, because it trains the next
     * reader to edit the assertion rather than the code (F2-214). The claim it makes is unchanged
     * and still true; only the file holding the answer changed, so the gate resolves that file.
     *
     * <p>🔑 The alternative — deleting or relaxing the assertion — always passes and proves
     * nothing (F2-206/F2-235). Following the delegation keeps the claim enforced.
     */
    @Test
    @DisplayName("the refusal does not distinguish 'no such user' from 'another tenant's user'")
    void theRefusalCannotBeUsedToEnumerateForeignUserIds() throws IOException {
        String src = strip(read(SERVICE));
        String body = methodBody(src, "requireOwnUser");
        if (body.contains("directory.require(")) {
            // The predicate is split across the primitive's two methods: `resolve` narrows by
            // tenant and `require` turns an empty result into the refusal. Assert each where it
            // actually lives — pointing both claims at one method name is how F2-244's own
            // name-keyed lookup read a CALL for a DECLARATION.
            String directory = strip(read(DIRECTORY));
            assertThat(methodBody(directory, "resolve"))
                .as("the delegation target must narrow by tenant, or the shared primitive is a "
                  + "cross-tenant read that every caller now trusts")
                .contains("getOrganisationId()");
            body = methodBody(directory, "require");
            assertThat(body)
                .as("and it must declare require(), or CoiService delegates to nothing")
                .isNotBlank();
        } else {
            assertThat(body)
                .as("the resolver must filter on the tenant, or it is not a tenant check at all")
                .contains("getOrganisationId()");
        }

        assertThat(body)
            .as("""
                One orElseThrow for both cases. CommitteeMemberRegistryService#requireUserInTenant
                records why: distinguishing "does not exist" from "belongs to another tenant" would
                let a caller enumerate other tenants' user ids by reading the error text.""")
            .containsOnlyOnce("orElseThrow");
        assertThat(body.toLowerCase())
            .as("the message must not name the other tenant or say the user exists elsewhere")
            .doesNotContain("another organisation")
            .doesNotContain("another tenant")
            .doesNotContain("different organisation");
    }

    @Test
    @DisplayName("the plural load filters every row on the tenant (findAllById does not)")
    void thePluralLoadFiltersOnTheTenant() throws IOException {
        String src = strip(read(SERVICE));
        String body = methodBody(src, "resolveSubjects");

        assertThat(body)
            .as("resolveSubjects exists to batch the lookup for a page of rows")
            .contains("findAllById");
        assertThat(body)
            .as("""
                findAllById is a bare primary-key lookup with NO tenant predicate — F2-243's filed
                plural-load shape. Every returned row must be checked against the tenant, exactly
                as CommitteeMemberRegistryService's seat-linkage report does.""")
            .contains("getOrganisationId()");
    }

    // ── the display half: the wire must carry what its client reads ──────────────────────────

    @Test
    @DisplayName("the response record declares the identity fields its client has always read")
    void theResponseDeclaresTheIdentityItsClientReads() throws IOException {
        Set<String> declared = Arrays.stream(CoiDtos.RecusalEventResponse.class.getRecordComponents())
            .map(RecordComponent::getName).collect(Collectors.toSet());

        assertThat(declared)
            .as("""
                lib/coi.ts's RecusalResponse declared userNameEn/userNameAr long before the server
                sent either, so CoiRegisterPage's `selected.userNameEn && …` guard could never fire
                and the recusal register named the person NOWHERE. F2-211's shape: the client
                nominates, the entity adjudicates. userResolved is separate because a resolvable
                user with a blank name and an id that resolves to nobody are different facts, and
                11 of 28 live rows are the second kind.""")
            .contains("userNameEn", "userNameAr", "userResolved");

        // Resolution is the SERVICE's job. A DTO has no repository and no tenant, and giving it
        // either is how a cross-tenant read gets built by accident.
        assertThat(Arrays.stream(CoiDtos.RecusalEventResponse.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("from") && m.getParameterCount() == 2))
            .as("from() must be handed the resolved user rather than resolving one itself")
            .isTrue();
    }

    @Test
    @DisplayName("the frontend and the wire agree on the identity fields, in both directions")
    void theFrontendAndTheWireAgree() throws IOException {
        String ts = strip(read(FE_CLIENT));
        int start = ts.indexOf("interface RecusalResponse");
        assertThat(start).as("RecusalResponse must still be declared in lib/coi.ts").isGreaterThan(-1);
        String block = ts.substring(start, ts.indexOf('}', start));

        for (String f : List.of("userResolved", "userNameEn", "userNameAr")) {
            assertThat(block).as("lib/coi.ts's RecusalResponse must declare %s", f).contains(f);
        }

        String page = strip(read(FE_PAGE));
        // Assert the CALL shape, never the bare token: F2-194/F2-205 both had a leg satisfied by
        // the name surviving in an import statement after every call site was deleted.
        assertThat(Pattern.compile("recusalSubjectLabel\\s*\\(").matcher(page).results().count())
            .as("""
                Both surfaces must name the subject through the shared predicate: the register
                table (which had NO person column at all) and the detail panel (whose guard the
                wire never satisfied). One predicate, because F2-209 measured what happens when a
                console and the table below it each compute their own answer.""")
            .isGreaterThanOrEqualTo(2L);
        assertThat(page)
            .as("an unresolvable id must be announced, not merely coloured (F2-197/F2-209)")
            .contains("recusalSubjectIsUnresolved")
            .contains("role={recusalSubjectIsUnresolved(r) ? 'status' : undefined}");
    }

    // ── the filed schema gap, pinned so it cannot rot into silence ───────────────────────────

    @Test
    @DisplayName("the recusal entity still cannot name a director who has no platform login")
    void theExternalDirectorGapIsStillOpenAndStillRecorded() {
        Set<String> fields = Arrays.stream(CoiRecusalEvent.class.getDeclaredFields())
            .map(java.lang.reflect.Field::getName).collect(Collectors.toSet());

        assertThat(fields)
            .as("""
                FILED, OWNER DECISION — not a defect this slice invented a fix for.

                user_id is NOT NULL and there is no name column, so a recusal can only name a
                platform user. The population a COI register exists for — non-executive directors,
                external committee members — largely has no login, which is why the seed invented
                ids 1001–1007. F2-26's committee roster already models this properly
                (LINKED | EXTERNAL | UNLINKED via noPlatformUser), so the shape to copy exists;
                adopting it means a migration plus a form field, i.e. a product decision.

                This leg exists so the filing cannot rot into silence: if somebody adds the name
                columns, this test fails and the register entry must be closed in the same commit.""")
            .doesNotContain("subjectNameEn", "subjectNameAr", "employeeNo", "noPlatformUser");
    }

    // ── synthetic self-tests: the detectors must fire on the real pre-fix shapes ─────────────

    @Test
    @DisplayName("the detectors fire on the literal pre-fix shapes and not on the fixed ones")
    void theDetectorsFireOnTheLiteralPreFixShapes() {
        // Verbatim from master before this slice.
        String preFixRecusal = "        r.setDeclarationId(req.declarationId());\n"
                             + "        r.setUserId(req.userId());\n";
        String preFixDeclarant = "        if (mayFileForOthers && req.userId() != null) {\n"
                               + "            return req.userId();\n"
                               + "        }\n";
        String fixedRecusal = "        User subject = requireOwnUser(req.userId(), \"a recusal\");\n"
                            + "        r.setUserId(subject.getId());\n";
        String fixedDeclarant = "            return requireOwnUser(req.userId(), \"x\").getId();\n";

        assertThat(RAW_USER_REFERENCE.matcher(preFixRecusal).find())
            .as("must fire on the recusal writer as it stood").isTrue();
        assertThat(RAW_USER_REFERENCE.matcher(preFixDeclarant).find())
            .as("must fire on declarantFor's bare return as it stood").isTrue();
        assertThat(RAW_USER_REFERENCE.matcher(fixedRecusal).find())
            .as("must NOT fire on the fixed recusal writer").isFalse();
        assertThat(RAW_USER_REFERENCE.matcher(fixedDeclarant).find())
            .as("must NOT fire on the fixed declarant branch — the id still comes from the "
              + "request, and that is fine; what matters is that it went through the resolver")
            .isFalse();
    }

    @Test
    @DisplayName("the comment stripper keeps line numbers and cannot be fooled by a URL")
    void theStripperKeepsLineNumbers() {
        String src = "a\n// a comment mentioning /* a block opener\nb\n/* real\n block */\nc\n"
                   + "String u = \"https://sama.gov.sa\";\n";
        String out = strip(src);
        assertThat(out.chars().filter(c -> c == '\n').count())
            .as("""
                Every newline must survive. F2-243 blanked comment matches with spaces, which keeps
                byte offsets and deletes newlines, so every site after the first block comment was
                attributed to the wrong line — and a wrong line number reads exactly like a false
                finding.""")
            .isEqualTo(src.chars().filter(c -> c == '\n').count());
        assertThat(out).as("code outside comments must survive").contains("a").contains("b").contains("c");
        assertThat(out).as("the block comment's body must be gone").doesNotContain("real");
        assertThat(out)
            .as("the `//` prose containing a `/*` must not open a block and eat the code after it")
            .contains("b");
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────

    /**
     * Brace-balanced body of the named method, skipping string literals.
     *
     * <p>⚠ Anchored on the DECLARATION, not on the bare name. The first draft matched
     * {@code \brequireOwnUser\s*\(} and found {@code recordRecusal}'s <em>call site</em>, which
     * appears earlier in the file than the declaration — so the leg asserting that the resolver
     * filters on the tenant read a completely different method's body and failed on a correct
     * tree. 🔑 <b>A name-keyed lookup finds the first thing with that name, and a call is not a
     * declaration</b> — F2-234's "resolve per owner type" one level down, and the failure mode is
     * the dangerous one for the OTHER legs: had the wrong body happened to contain the token, the
     * assertion would have passed for the wrong reason.
     */
    private static String methodBody(String src, String method) {
        // ⚠ Written WITHOUT a nested quantifier. The first attempt at this anchor was
        // `(?:public|private|protected|static|final|\s)+[\w<>,.\[\]?]+\s+` and it blew the stack
        // with a StackOverflowError on three legs — an alternation of single tokens under `+`
        // backtracks catastrophically over ordinary whitespace. A modifier at a line start plus a
        // bounded run of not-a-delimiter is linear and says the same thing.
        Matcher m = Pattern.compile(
            "\\n[ \\t]*(?:public|private|protected)[^;{}()\\n]{0,80}?\\b"
            + Pattern.quote(method) + "\\s*\\(").matcher(src);
        assertThat(m.find()).as("method %s must be DECLARED in CoiService", method).isTrue();
        int open = src.indexOf('{', m.end());
        assertThat(open).as("method %s must have a body", method).isGreaterThan(-1);
        int depth = 0, i = open;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == '"') {
                i++;
                while (i < src.length() && src.charAt(i) != '"') i += src.charAt(i) == '\\' ? 2 : 1;
            } else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) break;
            i++;
        }
        return src.substring(open, Math.min(i + 1, src.length()));
    }
}
