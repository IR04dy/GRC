package com.tss.grc.module.tprm.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-247 — the TPRM cyber-assessment register may not store a caller's vendor reference.
 *
 * <p><b>What was wrong.</b> {@code tprm.third_party_cyber_assessments} stores {@code vendor_id}
 * beside {@code vendor_name}, and {@code ThirdPartyCyberAssessmentService.create} bound the ENTITY
 * and touched neither — so both halves arrived from the caller's body, with no resolution and no
 * tenant check. Measured identical in dev and prod: <b>8 of 16 live rows carry a vendor label and
 * no link</b>, the labels being annotated prose the register can never join. Probed live as
 * {@code compliance}: a label-only create answered 201, another tenant's vendor id answered 201 and
 * was stored, {@code vendorId: 999999} answered 201 (no FOREIGN KEY exists on the column), and a
 * real id beside a lying label stored the lie.
 *
 * <p><b>Why a second gate rather than widening F2-246's.</b> That one is scoped to
 * {@code module/scm} and its four registers over {@code scm.vendors}. This is a different parent
 * register — {@code tprm.vendors}, resolved from {@code TprmService.getVendor} and not guessed from
 * the schema name — and ADR-012/018 keep the two modules' internals apart, so the primitive cannot
 * be shared. That the shape recurs once per module <i>because</i> the primitive cannot be shared is
 * the structural finding, and it is filed rather than papered over here.
 *
 * <p>Every leg is scoped to the two files this slice owns. A gate that fails on code the slice did
 * not touch is a gate nobody can keep green (F2-209), and one that fails on <i>correct</i> code is
 * worse than no gate at all, because it trains the next reader to edit the assertion (F2-214).
 */
class TprmVendorReferenceContractTest {

    private static final Path MAIN =
            Path.of("src", "main", "java", "com", "tss", "grc", "module", "tprm");
    private static final Path UI = Path.of("..", "tss-grc-ui-v2", "src");

    private static final String SERVICE =
            "cyber/service/ThirdPartyCyberAssessmentService.java";
    private static final String RESOLVER = "service/TprmVendorReferenceResolver.java";
    private static final String TPRM_SERVICE = "service/TprmService.java";
    private static final String PAGE = "pages/third-party-cyber/ThirdPartyCyberPage.tsx";
    private static final String CLIENT = "lib/thirdPartyCyber.ts";

    /**
     * Line comments and block comments in ONE left-to-right alternation.
     *
     * <p>Written as two sequential passes this deletes 45% of a file whose {@code //} prose happens
     * to contain a {@code /*} — and a corrupted source yields a SMALLER population, which reads
     * exactly like "those files are fine" (F2-198). Within one alternation the branch order is a
     * no-op, because {@code //} and a block opener can never match at the same position (F2-211's
     * correction); the defect is the two passes, not the ordering.
     *
     * <p>Stripping matters here specifically because this slice's own comments quote the shapes they
     * retired, and a token-matching detector reads the epitaph as the corpse (F2-193, F2-205).
     */
    private static final Pattern COMMENTS =
            Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    private static String code(Path root, String rel) {
        try {
            return COMMENTS.matcher(Files.readString(root.resolve(rel))).replaceAll(" ");
        } catch (IOException e) {
            throw new AssertionError("cannot read " + root.resolve(rel), e);
        }
    }

    /** {@code .setX(req.…)} — a write of the caller's own value, in any of the house receivers. */
    private static Pattern writtenFromRequest(String setter) {
        return Pattern.compile("\\." + setter + "\\s*\\(\\s*(?:req|body|in|patch|request)\\s*\\.");
    }

    // ------------------------------------------------------------------ the invariant ---------

    /**
     * ⚠⚠ <b>On an entity-bound endpoint the caller's value reaches the column with NO SETTER for a
     * scan to find, so "the service does not write X from the request" is VACUOUS — it was already
     * true of the defect.</b>
     *
     * <p>My first draft of this gate asserted exactly that, and the pre-fix control caught it:
     * both setter detectors were <b>SILENT ON MASTER</b>, where the register's create was
     * {@code create(ThirdPartyCyberAssessment req)} and simply never mentioned {@code vendorId} or
     * {@code vendorName} at all. Jackson had already bound them onto the entity, so there was
     * nothing for a {@code setX(req.…)} pattern to match, and the leg would have passed forever over
     * the defect it was written for — a detector that cannot match reads exactly like a clean tree,
     * and it UNDER-reports (F2-208). Only running it against {@code git show master:} showed it.
     *
     * <p>So the load-bearing assertion has to be the <b>positive</b>: both halves must be
     * <i>overwritten from the resolver</i> before the save. That is discriminating in both
     * directions — absent on master, present on the fix — and it is the only shape that can see an
     * entity-bound create doing nothing at all.
     */
    @Test
    @DisplayName("the create overwrites both halves from the resolver before it saves")
    void theCreateDerivesTheVendorReferenceRatherThanInheritingIt() {
        String svc = code(MAIN, SERVICE);

        assertThat(svc)
                .as("both halves must be assigned from the resolver's returned reference. An "
                  + "entity-bound create that says nothing about them lets Jackson's bound values "
                  + "reach the columns unresolved, which is exactly what stored 8 unjoinable rows.")
                .contains("target.setVendorId(vendor.id())")
                .contains("target.setVendorName(vendor.name())");

        // Kept as a second, narrower guard: if this register ever grows a request DTO, the
        // echo-the-caller shape becomes representable again and must stay refused.
        for (String setter : new String[] {"setVendorId", "setVendorName"}) {
            assertThat(writtenFromRequest(setter).matcher(svc).find())
                    .as("%s must never be written straight from the request — a caller-supplied "
                      + "label beside a resolved id is how the two halves came to describe "
                      + "different rows (F2-245)", setter)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("self-test: the detector rejects both an echoing create AND a silent one")
    void theDetectorIsNotVacuous() {
        // A gate that passes both before and after the fix is measuring nothing (F2-223).
        String echoing = "req.setId(null); target.setVendorName(req.getVendorName());";
        String silent = "req.setInherentCyberRisk(INHERENT.require(req.getInherentCyberRisk()));"
                      + " req.setId(null); return repo.save(req);";
        String fixed = "var vendor = vendorRefs.resolve(vendorId, suppliedName, orgId);"
                     + " target.setVendorId(vendor.id()); target.setVendorName(vendor.name());";

        assertThat(writtenFromRequest("setVendorName").matcher(echoing).find())
                .as("the narrow guard must fire on an echoing write")
                .isTrue();
        assertThat(writtenFromRequest("setVendorName").matcher(fixed).find())
                .as("and must NOT fire on the derived form")
                .isFalse();

        // The half the narrow guard cannot see, and the reason this leg exists: master's create was
        // the `silent` shape below — no setter at all — so only the positive assertion catches it.
        assertThat(silent)
                .as("master's entity-bound create mentioned neither half, which is why a "
                  + "setter-matching invariant was silent on the defect")
                .doesNotContain("setVendorId").doesNotContain("setVendorName");
        assertThat(fixed)
                .as("the positive assertion is the one that discriminates")
                .contains("target.setVendorId(vendor.id())");

        // Comment stripping is load-bearing: the fix's own prose quotes the shapes it retired, and a
        // token-matching detector reads the epitaph as the corpse (F2-193, F2-205).
        assertThat(COMMENTS.matcher("// setVendorName(req.getVendorName()) was the defect")
                        .replaceAll(" ").trim())
                .isEmpty();
    }

    @Test
    @DisplayName("the pair has exactly one writer, and it is the resolver's result")
    void theResolverIsTheOnlyWriter() {
        String svc = code(MAIN, SERVICE);
        assertThat(count(svc, "setVendorId\\s*\\("))
                .as("setVendorId must be written in exactly one place — a scan cannot tell a "
                  + "relocated write from a removed one, and the population shrinks either way")
                .isEqualTo(1);
        assertThat(count(svc, "setVendorName\\s*\\("))
                .as("setVendorName must be written in exactly one place")
                .isEqualTo(1);
        assertThat(svc)
                .as("both halves must come from the resolver's returned reference, so a resolved "
                  + "id and a derived label cannot be written independently")
                .contains("target.setVendorId(vendor.id())")
                .contains("target.setVendorName(vendor.name())");
        // Assert the CALL SHAPE, never the bare token: a name is still present in an import list
        // and in a comment, so `contains("applyVendorReference")` passes over a deleted call
        // (F2-194, F2-205).
        assertThat(count(svc, "applyVendorReference\\s*\\("))
                .as("the create must actually CALL the writer, not merely declare it")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("the resolve takes its tenant from the request, not from the row being built")
    void theResolveDoesNotDependOnStatementOrder() {
        String svc = code(MAIN, SERVICE);
        // F2-246's own fix was wrong in one of its four registers for exactly this reason: the
        // resolver ran eighteen lines above setOrganisationId, so the tenant argument was null and
        // every LINKED create answered 404 — and BOTH probe legs that expect a refusal passed on
        // that broken code, because a refusal caused by a null tenant is indistinguishable from a
        // correct one. Ban the shape rather than trusting statement order.
        assertThat(svc)
                .as("the tenant must be read from TenantContext into a local and passed in, so the "
                  + "resolve cannot depend on whether the entity has been stamped yet")
                .contains("applyVendorReference(req, req.getVendorId(), req.getVendorName(), orgId)");
        assertThat(Pattern.compile("resolve\\([^)]*\\breq\\.getOrganisationId\\(\\)").matcher(svc)
                        .find())
                .as("the resolve must not take the tenant from the half-built entity — that is the "
                  + "defect F2-246 shipped and only the derived-label probe leg caught")
                .isFalse();
    }

    @Test
    @DisplayName("one tenant predicate answers 'is this my vendor', read from both services")
    void theTenantCheckIsSharedAndReal() {
        String resolver = code(MAIN, RESOLVER);
        String tprm = code(MAIN, TPRM_SERVICE);

        assertThat(resolver)
                .as("the resolver must filter on the tenant and refuse rather than return null")
                .contains("orgId != null && orgId.equals(v.getOrganisationId())")
                .contains("Vendor not found: ");

        // TprmService.getVendor was the module's only in-tenant vendor load and tprm/cyber did not
        // use it. What made the duplicate harmless was two copies agreeing and nobody maintaining
        // that (F2-177), so there is one predicate now and this asserts the delegation.
        assertThat(tprm)
                .as("TprmService.getVendor must delegate to the shared resolver")
                .contains("vendorRefs.requireOwn(id, TenantContext.getCurrentTenant())");
        assertThat(Pattern.compile("vendorRepo\\.findById\\([^)]*\\)\\s*\\.\\s*filter").matcher(tprm)
                        .find())
                .as("TprmService must not keep a hand-spelled copy of the tenant filter — F2-246 "
                  + "found three such copies in the sibling module")
                .isFalse();
    }

    // -------------------------------------------------------------- the wire and the form ------

    @Test
    @DisplayName("the response still carries a label, because the column is NOT NULL")
    void theWireStillCarriesTheDerivedLabel() {
        String client = code(UI, CLIENT);
        // Relaxing a RESPONSE type is a different claim from relaxing a REQUEST one: vendor_name is
        // `nullable = false`, so the server always sends a label, and F2-246 learned this from tsc
        // failing at a display site after the wrong four interfaces were edited.
        assertThat(client)
                .as("vendorName is NOT NULL in the column, so the response type must keep it "
                  + "required — the derivation changes where it comes from, not whether it arrives")
                .contains("vendorName: string;");
        assertThat(client)
                .as("vendorId is nullable — 8 of 16 live rows have no link, and the unlinked case "
                  + "is preserved and disclosed rather than refused (F2-182, F2-209)")
                .contains("vendorId?: number;");
    }

    @Test
    @DisplayName("the create form picks the vendor from the register instead of typing a name")
    void theFormCollectsTheLinkAndNotTheLabel() {
        String page = code(UI, PAGE);
        assertThat(page)
                .as("the vendor field must be a picker over the register, keyed on the id")
                .contains("kind: 'select-searchable', key: 'vendorId'");
        assertThat(page)
                .as("and it must be populated from the TPRM register, not SCM's — two tables for "
                  + "one concept, and reaching for the wrong loader is the easy mistake")
                .contains("loadTprmVendorOptions(lang)");
        assertThat(page)
                .as("the payload must send the resolved id")
                .contains("vendorId: values.vendorId ? Number(values.vendorId) : undefined");
        assertThat(Pattern.compile("kind:\\s*'text',\\s*key:\\s*'vendorName'").matcher(page).find())
                .as("no free-text vendor-name box may remain: a form that collects a label instead "
                  + "of a link produces a row nothing can join, and that is what made 8 of the 16 "
                  + "live rows unjoinable")
                .isFalse();
        assertThat(Pattern.compile("vendorName:\\s*String\\(values\\.vendorName").matcher(page)
                        .find())
                .as("and the payload must not send a label the server would overwrite anyway")
                .isFalse();
    }

    @Test
    @DisplayName("the approve client sends no attribution the server discards")
    void theApproveClientSendsNoAttribution() {
        String client = code(UI, CLIENT);
        String page = code(UI, PAGE);
        // ThirdPartyCyberAssessmentController#approve reads principal.getUsername(); the body field
        // has always been ignored, and the page passed the literal 'admin' into it. A required-looking
        // parameter the server discards tells the caller their value matters (F2-184/F2-225).
        // ⚠ Scope this to the FUNCTION, never the file. My first draft asserted
        // `.doesNotContain("approverName")` over the whole client and failed on CORRECT code:
        // `approver_name` is a real column and the RESPONSE type legitimately declares it — telling
        // a reader who approved an assessment is the point of reading one. F2-225 recorded this exact
        // mistake ("it banned originatorUserId file-wide and the RESPONSE records legitimately carry
        // it") and I made it again. A gate that fails on correct code is worse than no gate: it
        // trains the next reader to edit the assertion rather than the code (F2-214).
        assertThat(client)
                .as("approveTpc must take only the id — the approver comes from the principal")
                .contains("export function approveTpc(id: number)");
        Matcher approve = Pattern.compile(
                "export function approveTpc\\(id: number\\)\\s*\\{(?<body>[^}]*)}").matcher(client);
        assertThat(approve.find()).as("the approveTpc declaration must be findable").isTrue();
        assertThat(approve.group("body"))
                .as("and its body must not post an attribution the server discards")
                .doesNotContain("approverName");
        assertThat(client)
                .as("the response type must still declare approverName — it is a real column, and "
                  + "the fix changes where the value comes from, not whether it is readable")
                .contains("approverName?: string;");
        assertThat(page)
                .as("and no call site may still pass a literal actor")
                .doesNotContain("approveTpc(id, 'admin')");
    }

    @Test
    @DisplayName("the population is this one register, and it did not shrink to nothing")
    void thePopulationIsNotEmpty() {
        // Anti-vacuity. Every leg above reads files by path, so a rename or a move makes them all
        // pass over an empty string unless something asserts the corpus is really there — and a
        // detector that cannot match reads exactly like a clean tree (F2-208).
        assertThat(code(MAIN, SERVICE)).hasSizeGreaterThan(2_000);
        assertThat(code(MAIN, RESOLVER)).hasSizeGreaterThan(500);
        assertThat(code(UI, PAGE)).hasSizeGreaterThan(5_000);
        assertThat(code(UI, CLIENT)).hasSizeGreaterThan(500);
    }

    private static int count(String haystack, String regex) {
        Matcher m = Pattern.compile(regex).matcher(haystack);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
