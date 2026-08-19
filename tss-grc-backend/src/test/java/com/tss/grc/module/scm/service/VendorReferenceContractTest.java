package com.tss.grc.module.scm.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-246 — a soft foreign key and the copy of its parent's identity have <b>one</b> writer.
 *
 * <h2>What this gate exists for</h2>
 *
 * Four SCM registers store a vendor id beside a copy of that vendor's name. Every create took
 * both from the request, so one reference had two authors and nothing compared them. Measured
 * identical in dev and prod: <b>32 of 53 live rows carry a label and no link</b>, because all
 * four create forms offered a required free-text vendor box and no picker and sent no id — and
 * two repositories run coverage queries over exactly that id.
 *
 * <p>Probed live as {@code ranalyst} before the fix, 17/17: a label-only create at 201, another
 * tenant's vendor id stored at 201, {@code 999999} stored at 201, and a deliberately wrong label
 * stored beside a real id at 201.
 *
 * <h2>Why the legs are shaped the way they are</h2>
 *
 * <ul>
 *   <li>The invariant is a <b>hard zero with no exemption map</b>, which is safe rather than
 *       strict (F2-214): a derived label is always available, so there is no correct code for it
 *       to fail on.</li>
 *   <li>Every assertion is scoped to <b>the shape of its claim</b> and asserts a <b>call
 *       shape</b> ({@code name\s*\(}) rather than a bare token — a token is satisfied by an
 *       import or by a comment naming the thing it retired (F2-194, F2-205).</li>
 *   <li>Comments are stripped before scanning, in one left-to-right alternation. Prose about
 *       code, stored as data, invents matches (F2-193, F2-198, F2-211).</li>
 *   <li>The detector carries a <b>synthetic self-test</b> over the literal pre-fix shape, because
 *       a leg that passes is indistinguishable from a leg whose regex has rotted (F2-195).</li>
 *   <li>The direction nobody writes first is here too: the id-less labels must <b>keep</b> their
 *       {@code @NotBlank}, so "relax them as well" fails (F2-199).</li>
 * </ul>
 */
class VendorReferenceContractTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "tss", "grc", "module", "scm");
    private static final Path UI = Path.of("..", "tss-grc-ui-v2", "src");

    /** The four registers, and the vendor reference each one stores. */
    private static final Map<String, String[]> REGISTERS = new LinkedHashMap<>();
    static {
        // service file -> { id setter, label setter }
        REGISTERS.put("resilience/service/ExitPlanService.java",
                new String[]{"setVendorId", "setVendorName"});
        REGISTERS.put("resilience/service/AlternateSupplierService.java",
                new String[]{"setPrimaryVendorId", "setPrimaryVendorName"});
        REGISTERS.put("resilience/service/SupplierResilienceTestService.java",
                new String[]{"setVendorId", "setVendorName"});
        REGISTERS.put("fourthparty/service/FourthPartyExposureService.java",
                new String[]{"setPrimaryVendorId", "setPrimaryVendorName"});
    }

    /**
     * The four create forms and the key each must now pick.
     *
     * <p>Two of them live in one file — {@code SupplierResiliencePage} owns both the alternates
     * tab and the exit-plans tab — which is why the population is keyed by (file, key) and not
     * by file.
     */
    private static final Map<String, String> FORMS = new LinkedHashMap<>();
    static {
        FORMS.put("pages/supplier-resilience/SupplierResiliencePage.tsx|primaryVendorId", "srpAltPrimaryVendor");
        FORMS.put("pages/supplier-resilience/SupplierResiliencePage.tsx|vendorId", "srpPlanVendor");
        FORMS.put("pages/resilience-tests/ResilienceTestsPage.tsx|vendorId", "srtColVendor");
        FORMS.put("pages/fourth-party/FourthPartyPage.tsx|primaryVendorId", "f4pColPrimary");
    }

    // ── the detector ─────────────────────────────────────────────────────────────────────

    /**
     * Line and block comments in ONE left-to-right alternation.
     *
     * <p>Two sequential passes deleted 45% of a file in F2-198, because a {@code //} prose
     * comment containing a literal {@code /*} let the block pattern run to the next closing
     * delimiter. Within one alternation the branch order is a no-op (F2-211) — the defect is
     * the two passes.
     */
    private static final Pattern COMMENTS =
            Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    private static String stripComments(String src) {
        return COMMENTS.matcher(src).replaceAll("");
    }

    /**
     * A setter fed straight from the request: the defect's signature.
     *
     * <p>Scoped to the receivers this codebase's DTOs arrive under. It is a <em>signature</em>
     * rather than a spelling, so renaming the parameter does not evade it.
     */
    private static Pattern writtenFromRequest(String setter) {
        return Pattern.compile("\\." + Pattern.quote(setter)
                + "\\s*\\(\\s*(?:req|body|in|patch|request)\\s*\\.");
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new AssertionError("cannot read " + p.toAbsolutePath(), e);
        }
    }

    // ── leg 1: the invariant ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("no SCM service writes a subject-vendor id or label straight from the request")
    void noServiceStoresTheCallersVendorReference() {
        List<String> offenders = new ArrayList<>();
        for (var e : REGISTERS.entrySet()) {
            String src = stripComments(read(MAIN.resolve(e.getKey())));
            for (String setter : e.getValue()) {
                Matcher m = writtenFromRequest(setter).matcher(src);
                if (m.find()) {
                    offenders.add(e.getKey() + " :: " + setter + "  <- " + m.group());
                }
            }
        }
        assertThat(offenders)
                .as("A vendor id and a copy of that vendor's name are two halves of ONE "
                  + "reference. Taking both from the request gives them two authors, and "
                  + "nothing then compares them: 32 of 53 live rows carry a label with no "
                  + "link. Resolve the id in-tenant and DERIVE the label from the row it "
                  + "resolved — VendorReferenceResolver#resolve.")
                .isEmpty();
    }

    @Test
    @DisplayName("self-test: the detector fires on the literal pre-fix shape")
    void theDetectorIsNotVacuous() {
        // A leg that passes over a clean tree and a leg whose regex has rotted look identical,
        // so the detector is driven against the exact code this slice deleted.
        String preFix = """
                    ExitPlan p = new ExitPlan();
                    p.setVendorId(req.vendorId());
                    p.setVendorName(req.vendorName());
                """;
        assertThat(writtenFromRequest("setVendorId").matcher(preFix).find()).isTrue();
        assertThat(writtenFromRequest("setVendorName").matcher(preFix).find()).isTrue();

        // ...and NOT on the fixed shape, nor on a comment that quotes the retired one.
        String fixed = """
                    var vendor = vendorRefs.resolve(req.vendorId(), req.vendorName(), orgId);
                    p.setVendorId(vendor.id());
                    p.setVendorName(vendor.name());
                """;
        assertThat(writtenFromRequest("setVendorId").matcher(fixed).find()).isFalse();
        assertThat(writtenFromRequest("setVendorName").matcher(fixed).find()).isFalse();

        String epitaph = "// this used to read p.setVendorName(req.vendorName());\n";
        assertThat(writtenFromRequest("setVendorName").matcher(stripComments(epitaph)).find())
                .as("a comment explaining the removal must not read as the removal's subject")
                .isFalse();
    }

    // ── leg 2: the resolver is the only writer ───────────────────────────────────────────

    @Test
    @DisplayName("every register writes both halves from the resolver, in one place")
    void theResolverIsTheOnlyWriter() {
        for (var e : REGISTERS.entrySet()) {
            String src = stripComments(read(MAIN.resolve(e.getKey())));
            assertThat(Pattern.compile("vendorRefs\\s*\\.\\s*resolve\\s*\\(").matcher(src).find())
                    .as("%s must resolve its vendor reference rather than storing the "
                      + "caller's claim about it", e.getKey())
                    .isTrue();
            for (String setter : e.getValue()) {
                String half = setter.endsWith("Id") ? "id" : "name";
                assertThat(Pattern.compile(
                                "\\." + Pattern.quote(setter) + "\\s*\\(\\s*vendor\\s*\\.\\s*"
                                + half + "\\s*\\(")
                        .matcher(src).find())
                        .as("%s :: %s must be written from the resolved reference's %s(), so "
                          + "the two halves cannot disagree", e.getKey(), setter, half)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("no register takes the resolve's tenant from the row it is still building")
    void theResolveDoesNotDependOnStatementOrder() {
        // ⚠ Written after the live probe caught this in my own fix, in 1 of the 4 registers:
        // FourthPartyExposureService resolved the vendor 18 lines ABOVE
        // `e.setOrganisationId(...)`, so the tenant argument was null, the filter could never
        // match, and every LINKED create answered 404. The other three were safe by accident of
        // statement order.
        //
        // 🔑 The two probe legs that expect a REFUSAL passed on the broken code, because a
        // refusal caused by a null tenant is indistinguishable from a correct one. Only the leg
        // asserting the DERIVED label could see it — which is why that leg exists.
        List<String> offenders = new ArrayList<>();
        for (String rel : REGISTERS.keySet()) {
            String src = stripComments(read(MAIN.resolve(rel)));
            Matcher m = Pattern.compile(
                    "vendorRefs\\s*\\.\\s*resolve\\s*\\([^;]{0,240}?\\b[a-z]\\s*\\.\\s*"
                    + "getOrganisationId\\s*\\(\\s*\\)").matcher(src);
            if (m.find()) {
                offenders.add(rel + "  <- " + m.group().replaceAll("\\s+", " "));
            }
        }
        assertThat(offenders)
                .as("the tenant must be read from the request, not from the half-built entity: "
                  + "a resolve placed above the organisation stamp passes null and refuses "
                  + "every linked create")
                .isEmpty();

        // Anti-vacuity: the detector must fire on the shape it is written for.
        assertThat(Pattern.compile(
                        "vendorRefs\\s*\\.\\s*resolve\\s*\\([^;]{0,240}?\\b[a-z]\\s*\\.\\s*"
                        + "getOrganisationId\\s*\\(\\s*\\)")
                .matcher("var v = vendorRefs.resolve(req.primaryVendorId(), req.name(),\n"
                       + "        e.getOrganisationId());").find())
                .isTrue();
    }

    @Test
    @DisplayName("the resolver resolves inside the tenant, and one place answers that question")
    void theTenantCheckIsSharedAndReal() {
        String resolver = stripComments(read(MAIN.resolve("service/VendorReferenceResolver.java")));
        // ⚠ Asserted as the tenant COMPARISON, not as the word `organisationId`: the code
        // spells it `getOrganisationId` and the lowercase form appears only in the prose this
        // scan strips, so the first draft of this leg failed on a correct resolver. F2-206's
        // rule applied to an assertion — a red leg is a finding only if the code is wrong.
        assertThat(Pattern.compile(
                        "findById\\s*\\([^;]{0,120}?\\.\\s*filter\\s*\\([^;]{0,160}?getOrganisationId")
                .matcher(resolver).find())
                .as("a resolver that does not filter on the tenant turns a cross-tenant "
                  + "reference into a resolved one (F2-242's discriminator: verify the "
                  + "reference where it is STORED)")
                .isTrue();

        // ScmService was already correct; it must now delegate rather than keep a second
        // implementation that happens to agree (F2-177).
        String scm = stripComments(read(MAIN.resolve("service/ScmService.java")));
        assertThat(Pattern.compile("vendorRefs\\s*\\.\\s*requireOwn\\s*\\(").matcher(scm).find())
                .as("ScmService#requireOwnVendor must delegate to the shared resolver")
                .isTrue();
        // Scoped to the shape of the claim. A first draft banned every
        // `vendorRepo.findById(...)…getOrganisationId`, which also matches
        // `stampVendorOnboarding` — F2-242's deliberately tenant-scoped WRITE helper, using
        // `ifPresent` rather than resolving a vendor for a caller. Banning that would be a
        // gate failing on correct code (F2-214). The duplicate-implementation shape is the
        // refusal, so that is what is banned; the three copies this found in `getVendor`,
        // `updateVendor` and `deleteVendor` were converted rather than exempted.
        assertThat(scm)
                .as("'Vendor not found' must be raised in exactly one place. Four copies of "
                  + "one predicate agree today with nobody maintaining that (F2-177).")
                .doesNotContain("EntityNotFoundException(\"Vendor not found");
    }

    // ── leg 3: the wire ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the subject vendor's label is optional on the wire, and the id-less ones are not")
    void theWireDistinguishesTheSubjectVendorFromAnUnregisteredParty() {
        String alt = read(MAIN.resolve("resilience/dto/SupplierResilienceDtos.java"));
        String fp = read(MAIN.resolve("fourthparty/dto/FourthPartyExposureDtos.java"));

        // The subject vendor may be named by id instead, so its label is not mandatory.
        assertThat(alt).doesNotContain("@NotBlank @Size(max = 255) String vendorName");
        assertThat(alt).doesNotContain("@NotBlank @Size(max = 255) String primaryVendorName");
        assertThat(fp).doesNotContain("@NotBlank @Size(max = 255) String primaryVendorName");

        // The direction nobody writes first (F2-199). `alternate_vendor_id` and a fourth-party
        // id do not exist as columns ANYWHERE, so for those parties the label is the only
        // identifier and blanking it would leave an unnamed row. Relaxing them too must fail.
        assertThat(alt)
                .as("alternateVendorName must stay mandatory: the schema has no "
                  + "alternate_vendor_id column, so a substitute supplier is identified by "
                  + "its name or not at all")
                .contains("@NotBlank @Size(max = 255) String alternateVendorName");
        assertThat(fp)
                .as("fourthPartyName must stay mandatory: a fourth party is not one of our "
                  + "vendors and has no id column anywhere in the schema")
                .contains("@NotBlank @Size(max = 255) String fourthPartyName");
    }

    // ── leg 4: the form, which is where the 32 unlinked rows came from ───────────────────

    @Test
    @DisplayName("every create form picks the vendor from the register instead of typing a name")
    void theFormsCollectTheLinkAndNotTheLabel() {
        assertThat(Files.exists(UI.resolve("lib/vendorOptions.ts")))
                .as("frontend not checked out at " + UI.toAbsolutePath())
                .isTrue();

        for (var e : FORMS.entrySet()) {
            String[] parts = e.getKey().split("\\|", 2);
            String src = read(UI.resolve(parts[0]));
            String key = parts[1];

            assertThat(Pattern.compile(
                            "kind:\\s*'select-searchable',\\s*key:\\s*'" + Pattern.quote(key) + "'")
                    .matcher(src.replace("\n", " ").replaceAll("\\s+", " ")).find())
                    .as("%s must offer a picker on '%s'. A form that collects a LABEL instead "
                      + "of a LINK produces a row nothing can join — that is where 32 of 53 "
                      + "unlinked rows came from.", parts[0], key)
                    .isTrue();

            // And the payload must actually send it. Asserting the field's presence on the
            // form is not asserting that the create carries it (F2-212).
            assertThat(Pattern.compile(Pattern.quote(key) + "\\s*:\\s*[^,;]*Number\\s*\\(")
                    .matcher(src).find())
                    .as("%s must SEND '%s' — a picker whose value never reaches the payload "
                      + "changes nothing", parts[0], key)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("no create form still types the subject vendor's name")
    void noFormStillCollectsTheVendorLabel() {
        List<String> offenders = new ArrayList<>();
        for (String fileAndKey : FORMS.keySet()) {
            String file = fileAndKey.split("\\|", 2)[0];
            String src = read(UI.resolve(file)).replace("\n", " ").replaceAll("\\s+", " ");
            for (String label : List.of("vendorName", "primaryVendorName")) {
                Matcher m = Pattern.compile(
                        "kind:\\s*'text',\\s*key:\\s*'" + label + "'").matcher(src);
                if (m.find()) {
                    offenders.add(file + " :: free-text " + label);
                }
            }
        }
        assertThat(offenders)
                .as("the server derives the label from the vendor the picker names, so a "
                  + "free-text vendor box is a second author for a value that already has one")
                .isEmpty();
    }

    // ── leg 5: anti-vacuity ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the population is the four registers, and it did not shrink to nothing")
    void thePopulationIsNotEmpty() {
        // A floor must sit above the largest WRONG answer, not above zero (F2-215). Here the
        // wrong answer IS zero: a path that stops resolving reads exactly like a swept tree.
        assertThat(REGISTERS).hasSize(4);
        assertThat(FORMS).hasSize(4);
        for (String rel : REGISTERS.keySet()) {
            assertThat(Files.exists(MAIN.resolve(rel)))
                    .as("%s — if a register is renamed this population must move with it "
                      + "rather than quietly shrinking", rel)
                    .isTrue();
        }
        for (String fileAndKey : FORMS.keySet()) {
            assertThat(Files.exists(UI.resolve(fileAndKey.split("\\|", 2)[0]))).isTrue();
        }
    }
}
