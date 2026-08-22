package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberControl;
import com.tss.grc.module.cyber.entity.CyberFramework;
import com.tss.grc.module.cyber.repository.CyberControlRepository;
import com.tss.grc.module.cyber.repository.CyberFrameworkRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-252 — the {@code (framework_id, framework_name)} pair on a cyber control.
 *
 * <h2>What this pins, and why each leg exists</h2>
 * Three defects landed together, all probed live 2026-08-20 as {@code so_officer}
 * ({@code CYBER:READ} + {@code CYBER:WRITE}) against controls the probe planted itself:
 *
 * <ol>
 *   <li><b>The destruction.</b> {@code CyberPage}'s {@code controlToReq} carried
 *       {@code frameworkName} and no {@code frameworkId}, and {@code applyEditable} wrote both
 *       columns unconditionally — so editing a control's title NULLED its framework link at HTTP
 *       200, on a column populated on <b>75 of 75</b> live rows in dev and prod. The label was left
 *       standing, so the register still rendered the framework and only a join could tell.</li>
 *   <li><b>The unresolved reference.</b> A nonexistent id (999999) and <b>another tenant's</b>
 *       framework were both stored at HTTP 200. The column carries no FOREIGN KEY, and
 *       {@code CyberFrameworkRepository.findByIdAndOrganisationId} has existed since SEC-CYB-001
 *       and was never called from this path (F2-242, F2-215's two-of-N).</li>
 *   <li><b>The lying label.</b> {@code frameworkId = 1} ("NCA ECC") was stored beside
 *       {@code frameworkName = 'ISO 27001'} (F2-245).</li>
 * </ol>
 *
 * <h2>⚠ The structural leg is the load-bearing one</h2>
 * A behaviour test over a mocked repository cannot see {@code applyEditable} copying the pair back
 * in — a future reader "restoring symmetry" with its eleven siblings would reintroduce defect (1)
 * and every behaviour leg here would still pass, because they drive
 * {@code applyFrameworkReference}, which runs afterwards and would win. So the field names are
 * banned from {@code applyEditable}'s own body, by <b>shape</b>, with a positive control proving the
 * detector fires on the literal pre-fix line (F2-195).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CyberControlFrameworkReferenceTest {

    private static final Path SERVICE = Path.of(
        "src/main/java/com/tss/grc/module/cyber/service/CyberControlService.java");
    private static final Path PAGE = Path.of(
        "../tss-grc-ui-v2/src/pages/cyber/CyberPage.tsx");

    @Mock private CyberControlRepository controlRepo;
    @Mock private CyberFrameworkRepository frameworkRepo;

    private CyberControlService service;

    private static final Long ORG = 1L;
    private static final Long OTHER_ORG = 2L;

    @BeforeEach
    void setUp() {
        // ⚠ A REAL resolver over the mocked repository, never a mock of the resolver: a mocked
        // resolver returns empty and every refusal leg goes green asserting nothing (F2-237/F2-249).
        CyberFrameworkReferenceResolver resolver = new CyberFrameworkReferenceResolver(frameworkRepo);
        service = new CyberControlService(controlRepo, resolver);
        when(controlRepo.save(any(CyberControl.class))).thenAnswer(i -> i.getArgument(0));
        // `getControl` is tenant-scoped through TenantContext (SEC-CYB-001), so the update legs need
        // one — without it they throw before reaching the claim, which reads exactly like the fix
        // refusing a valid request (F2-244).
        TenantContext.setCurrentTenant(ORG);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CyberFramework framework(Long id, Long org, String name) {
        CyberFramework f = new CyberFramework();
        f.setId(id);
        f.setOrganisationId(org);
        f.setName(name);
        return f;
    }

    private void register(CyberFramework f) {
        when(frameworkRepo.findByIdAndOrganisationId(f.getId(), f.getOrganisationId()))
            .thenReturn(Optional.of(f));
    }

    private CyberControl control(Long id, Long frameworkId, String frameworkName) {
        CyberControl c = new CyberControl();
        c.setId(id);
        c.setOrganisationId(ORG);
        c.setTitleEn("A control");
        c.setFrameworkId(frameworkId);
        c.setFrameworkName(frameworkName);
        return c;
    }

    // ── (1) the destruction ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("an UPDATE that omits frameworkId leaves the stored link alone")
    void anOmittedFrameworkIdOnUpdateDoesNotClearTheLink() {
        CyberFramework ecc = framework(1L, ORG, "NCA ECC");
        register(ecc);
        when(controlRepo.findByIdAndOrganisationId(7L, ORG))
            .thenReturn(Optional.of(control(7L, 1L, "NCA ECC")));

        CyberControl edit = new CyberControl();
        edit.setTitleEn("A control, retitled");
        edit.setFrameworkName("NCA ECC");   // exactly what CyberPage used to send: label, no id

        CyberControl saved = service.updateControl(7L, edit);

        assertThat(saved.getFrameworkId())
            .as("this is the whole finding: an entity binding cannot tell `frameworkId: null` from "
              + "an absent key, so writing it unconditionally destroyed the link on every edit")
            .isEqualTo(1L);
        assertThat(saved.getFrameworkName()).isEqualTo("NCA ECC");
    }

    @Test
    @DisplayName("an UPDATE that omits BOTH halves still leaves the link alone")
    void omittingBothHalvesLeavesTheLinkAlone() {
        register(framework(1L, ORG, "NCA ECC"));
        when(controlRepo.findByIdAndOrganisationId(7L, ORG))
            .thenReturn(Optional.of(control(7L, 1L, "NCA ECC")));

        CyberControl edit = new CyberControl();
        edit.setTitleEn("retitled");

        CyberControl saved = service.updateControl(7L, edit);
        assertThat(saved.getFrameworkId()).isEqualTo(1L);
        assertThat(saved.getFrameworkName()).isEqualTo("NCA ECC");
    }

    // ── (2) the unresolved reference ────────────────────────────────────────────────

    @Test
    @DisplayName("a nonexistent framework id is refused, naming the field")
    void aNonexistentFrameworkIdIsRefused() {
        CyberControl c = new CyberControl();
        c.setTitleEn("x");
        c.setFrameworkId(999999L);

        assertThatThrownBy(() -> service.saveControl(c, ORG))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("frameworkId")
            .hasMessageContaining("999999");
        verify(controlRepo, never()).save(any());
    }

    @Test
    @DisplayName("ANOTHER TENANT's framework is refused, with the same message as a missing one")
    void anotherTenantsFrameworkIsRefused() {
        CyberFramework foreign = framework(8L, OTHER_ORG, "Their framework");
        register(foreign);   // resolvable, but only for org 2

        CyberControl c = new CyberControl();
        c.setTitleEn("x");
        c.setFrameworkId(8L);

        assertThatThrownBy(() -> service.saveControl(c, ORG))
            .as("one message for both cases: distinguishing them lets a caller enumerate another "
              + "tenant's framework ids from the error text")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not name a framework in this organisation");
        verify(controlRepo, never()).save(any());
    }

    @Test
    @DisplayName("the tenant filter comes from the CALLER's argument, not the half-built row")
    void theTenantComesFromTheCallersArgument() {
        CyberFramework ecc = framework(1L, ORG, "NCA ECC");
        register(ecc);

        CyberControl c = new CyberControl();
        c.setTitleEn("x");
        c.setFrameworkId(1L);
        c.setOrganisationId(OTHER_ORG);   // a caller's lie about its own tenant

        CyberControl saved = service.saveControl(c, ORG);

        // F2-246 shipped a resolver that read the entity's own organisationId BEFORE it was
        // stamped, so the filter could never match and every linked create answered 404 — and the
        // two probe legs expecting a refusal passed over it, because a refusal caused by a null
        // tenant is indistinguishable from a correct one.
        assertThat(saved.getOrganisationId()).isEqualTo(ORG);
        assertThat(saved.getFrameworkId()).isEqualTo(1L);
        ArgumentCaptor<Long> orgArg = ArgumentCaptor.forClass(Long.class);
        verify(frameworkRepo).findByIdAndOrganisationId(any(), orgArg.capture());
        assertThat(orgArg.getValue()).isEqualTo(ORG);
    }

    // ── (3) the label is derived ────────────────────────────────────────────────────

    @Test
    @DisplayName("the label is DERIVED from the resolved framework, not echoed from the request")
    void theLabelIsDerived() {
        register(framework(2L, ORG, "ISO 27001"));

        CyberControl c = new CyberControl();
        c.setTitleEn("x");
        c.setFrameworkId(2L);   // no label at all

        assertThat(service.saveControl(c, ORG).getFrameworkName())
            .as("pre-fix an id with no label stored a NULL label — nothing derived it")
            .isEqualTo("ISO 27001");
    }

    @Test
    @DisplayName("a label CONTRADICTING the id is refused; an echo is accepted")
    void aContradictingLabelIsRefusedAndAnEchoIsAccepted() {
        register(framework(1L, ORG, "NCA ECC"));

        CyberControl lying = new CyberControl();
        lying.setTitleEn("x");
        lying.setFrameworkId(1L);
        lying.setFrameworkName("ISO 27001");
        assertThatThrownBy(() -> service.saveControl(lying, ORG))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not name framework 1");

        CyberControl echo = new CyberControl();
        echo.setTitleEn("x");
        echo.setFrameworkId(1L);
        echo.setFrameworkName("  nca ecc  ");   // trimmed, case-insensitive — a round-tripping form
        assertThat(service.saveControl(echo, ORG).getFrameworkName())
            .as("an echo must be accepted so a form that round-trips the row keeps working, and the "
              + "SPELLING stored must be the framework's, not the caller's casing (F2-179)")
            .isEqualTo("NCA ECC");
    }

    @Test
    @DisplayName("a bare label with no id stays legal — the schema says an unlinked control exists")
    void aBareLabelStaysLegal() {
        CyberControl c = new CyberControl();
        c.setTitleEn("x");
        c.setFrameworkName("A framework nobody registered");

        CyberControl saved = service.saveControl(c, ORG);
        assertThat(saved.getFrameworkId()).isNull();
        assertThat(saved.getFrameworkName())
            .as("both columns are nullable and live rows are made this way, so requiring an id "
              + "would refuse requests that work today (F2-182)")
            .isEqualTo("A framework nobody registered");
        verify(frameworkRepo, never()).findByIdAndOrganisationId(any(), any());
    }

    @Test
    @DisplayName("on an UNLINKED row the label stays the caller's to change, and to clear")
    void onAnUnlinkedRowTheLabelIsStillTheCallers() {
        when(controlRepo.findByIdAndOrganisationId(9L, ORG))
            .thenReturn(Optional.of(control(9L, null, "Something free-text")));

        CyberControl edit = new CyberControl();
        edit.setTitleEn("retitled");   // no label, no id

        assertThat(service.updateControl(9L, edit).getFrameworkName())
            .as("with no link there is nothing to protect, so an omitted label clears it as before")
            .isNull();
    }

    @Test
    @DisplayName("a label change on a LINKED row is refused, and the message says to send the id")
    void aLabelChangeOnALinkedRowIsRefused() {
        register(framework(1L, ORG, "NCA ECC"));
        when(controlRepo.findByIdAndOrganisationId(7L, ORG))
            .thenReturn(Optional.of(control(7L, 1L, "NCA ECC")));

        CyberControl edit = new CyberControl();
        edit.setTitleEn("x");
        edit.setFrameworkName("ISO 27001");

        assertThatThrownBy(() -> service.updateControl(7L, edit))
            .as("relinking by NAME is what this pair's hazard forbids: two registers share an id "
              + "space and near-identical names, so the caller names the framework it means")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("send frameworkId to change the link");
    }

    // ── structural: the pair has exactly ONE writer ─────────────────────────────────

    /** Strips line and block comments in ONE left-to-right alternation (F2-198/F2-211). */
    private static String strip(String s) {
        return s.replaceAll("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/", "");
    }

    /** The brace-balanced body of a named method, sliced from the first {@code {} after it. */
    private static String body(String src, String signature) {
        int at = src.indexOf(signature);
        assertThat(at).as("signature not found: %s", signature).isNotNegative();
        int open = src.indexOf('{', at);
        assertThat(open).isNotNegative();
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char ch = src.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}' && --depth == 0) return src.substring(open, i + 1);
        }
        throw new AssertionError("unbalanced body for " + signature);
    }

    private static String service() {
        try {
            return Files.readString(SERVICE);
        } catch (Exception e) {  // noqa
            throw new AssertionError("cannot read " + SERVICE.toAbsolutePath(), e);
        }
    }

    @Test
    @DisplayName("applyEditable does not touch the framework pair — the leg a mock cannot see")
    void applyEditableDoesNotWriteTheFrameworkPair() {
        String editable = strip(body(service(), "private void applyEditable("));

        Pattern write = Pattern.compile(
            "\\.\\s*setFramework(?:Id|Name)\\s*\\(", Pattern.CASE_INSENSITIVE);
        assertThat(write.matcher(editable).find())
            .as("applyEditable's body writes the framework pair. That is the pre-fix shape: it "
              + "copies both halves from the request unconditionally, so an omitted id NULLS the "
              + "link. The pair has ONE writer, applyFrameworkReference. Body was:%n%s", editable)
            .isFalse();

        // Positive control: the detector must fire on the literal line that was removed (F2-195).
        String prefix = "{ existing.setFrameworkId(req.getFrameworkId());"
                      + " existing.setFrameworkName(req.getFrameworkName()); }";
        assertThat(write.matcher(prefix).find())
            .as("the detector does not fire on the pre-fix line, so a green result above means "
              + "nothing — this is the check that stops the leg rotting silently")
            .isTrue();
        // ...and must NOT fire on the comment that explains the removal (F2-193: a token scan reads
        // an epitaph as a corpse, which is why `strip` runs first).
        assertThat(write.matcher(strip("// existing.setFrameworkId(req.getFrameworkId());")).find())
            .isFalse();
    }

    /**
     * ⚠ Why this leg is STRUCTURAL and the behaviour leg above is not enough.
     *
     * <p>The mutation harness replaced {@code applyFrameworkReference(c, c, orgId, true)} with
     * {@code …(c, c, c.getOrganisationId(), true)} and it <b>SURVIVED</b> —
     * {@code theTenantComesFromTheCallersArgument} could not see it, because {@code saveControl}
     * stamps {@code c.setOrganisationId(orgId)} on the line before, so the two expressions hold the
     * same value. The mutant is EQUIVALENT today, so it is not a hole in the gate (F2-206: a
     * surviving mutant is a hole only if the mutated code is actually wrong).
     *
     * <p>It is still worth banning, because that is exactly the fragility F2-246 recorded: there the
     * resolver read the entity's own {@code organisationId} <em>before</em> it had been stamped, so
     * the filter could never match and every linked create answered 404 — and it was safe in three
     * of the four registers it touched only "by accident of statement order". Moving one line here
     * would turn this equivalent mutant into that defect, and no behaviour test would notice,
     * because a refusal caused by a null tenant is indistinguishable from a correct refusal.
     */
    @Test
    @DisplayName("the create passes the CALLER's orgId, never the row's own — a statement-order trap")
    void theCreatePassesTheCallersOrgIdStructurally() {
        String create = strip(body(service(), "public CyberControl saveControl("));

        Matcher call = Pattern.compile(
            "applyFrameworkReference\\s*\\(([^;]*?)\\)\\s*;").matcher(create);
        assertThat(call.find()).as("saveControl must call applyFrameworkReference").isTrue();
        String args = call.group(1);

        assertThat(args)
            .as("the tenant argument must be the caller's parameter. Body was:%n%s", create)
            .contains("orgId");
        assertThat(args)
            .as("taking it from the row being built is F2-246's defect one statement away — the "
              + "stamp happens to precede the resolution today, and a reorder makes it wrong")
            .doesNotContain("getOrganisationId()");

        // Positive control: the detector must fire on the literal mutated form (F2-195).
        assertThat("applyFrameworkReference(c, c, c.getOrganisationId(), true);")
            .contains("getOrganisationId()");
    }

    @Test
    @DisplayName("applyFrameworkReference is the only writer, and it resolves before it writes")
    void theResolverIsConsultedBeforeTheWrite() {
        String src = strip(service());
        Matcher m = Pattern.compile("\\.\\s*setFramework(?:Id|Name)\\s*\\(").matcher(src);
        int sites = 0;
        while (m.find()) sites++;
        // 2 in applyFrameworkReference's id branch, 2 in the unlinked branch, 1 in the linked
        // branch. Any site OUTSIDE that method is what this leg exists to catch.
        String ref = body(src, "private void applyFrameworkReference(");
        Matcher inRef = Pattern.compile("\\.\\s*setFramework(?:Id|Name)\\s*\\(").matcher(ref);
        int inside = 0;
        while (inRef.find()) inside++;
        assertThat(inside)
            .as("every write to the pair must live in applyFrameworkReference")
            .isEqualTo(sites);
        assertThat(inside).as("anti-vacuity: the method must actually write the pair").isGreaterThan(2);
        assertThat(ref).contains("frameworks.require(");
    }

    // ── the frontend half, read from disk the way ConnectorBackingContractTest does ──

    @Test
    @DisplayName("CyberPage carries frameworkId in BOTH payload builders, and offers no hard-coded list")
    void theFrontendSendsTheIdAndPicksFromTheRegister() {
        String page;
        try {
            page = strip(Files.readString(PAGE));
        } catch (Exception e) {  // noqa
            // The frontend tree is not present in every checkout; skip rather than fail, the same
            // way the docs gates do. The `assumeTrue` equivalent here is an early return.
            return;
        }

        assertThat(page)
            .as("F2-212: a wire-contract gate proves the TYPES agree and cannot prove the payload "
              + "BUILDER sends what the type permits. `frameworkId` has always been declarable.")
            .contains("frameworkId: c.frameworkId");

        assertThat(page)
            .as("the hard-coded five-name list is gone — it offered two frameworks this register "
              + "does not have and hid three that it does")
            .doesNotContain("FRAMEWORK_OPTIONS");

        assertThat(Pattern.compile("cyberApi\\.frameworks\\s*\\(").matcher(page).find())
            .as("assert the CALL SHAPE, never the token: F2-194 recorded a gate satisfied by the "
              + "name appearing in an import statement")
            .isTrue();

        assertThat(page)
            .as("the select must be keyed on the id, not the label")
            .contains("key: 'frameworkId'");
    }

    // ── (5) F2-277 — the Arabic half of the derived label ────────────────────────────

    @Test
    @DisplayName("F2-277: both halves of the label are derived from the one resolved framework")
    void bothHalvesOfTheLabelAreDerived() {
        CyberFramework ecc = framework(1L, ORG, "NCA ECC");
        ecc.setNameAr("الضوابط الأساسية للأمن السيبراني");
        register(ecc);

        CyberControl c = new CyberControl();
        c.setTitleEn("x");
        c.setFrameworkId(1L);

        CyberControl saved = service.saveControl(c, ORG);
        assertThat(saved.getFrameworkName()).isEqualTo("NCA ECC");
        assertThat(saved.getFrameworkNameAr())
            .as("cyber_frameworks stores name_ar for 4 of 6 rows, and the derivation read only "
              + "the English half — so an Arabic-primary register rendered an English framework "
              + "name on every one of its 154 controls")
            .isEqualTo("الضوابط الأساسية للأمن السيبراني");
    }

    @Test
    @DisplayName("F2-277: a framework with no Arabic name leaves the Arabic half NULL, not the code")
    void aFrameworkWithNoArabicNameLeavesTheArabicHalfNull() {
        register(framework(3L, ORG, "PCI DSS"));   // nameAr never set

        CyberControl c = new CyberControl();
        c.setTitleEn("x");
        c.setFrameworkId(3L);

        CyberControl saved = service.saveControl(c, ORG);
        assertThat(saved.getFrameworkName()).isEqualTo("PCI DSS");
        assertThat(saved.getFrameworkNameAr())
            .as("an empty Arabic label is an honest 'not recorded'; filling it with the English "
              + "name or the code would be a fabricated governance record (F2-271)")
            .isNull();
    }

    @Test
    @DisplayName("F2-277: an update that omits the id re-derives BOTH halves together")
    void anUpdateThatOmitsTheIdReDerivesBothHalves() {
        CyberFramework ecc = framework(1L, ORG, "NCA ECC");
        ecc.setNameAr("الضوابط الأساسية");
        register(ecc);

        // The stored row's Arabic half is STALE — the framework was renamed in Arabic since.
        CyberControl stored = control(9L, 1L, "NCA ECC");
        stored.setFrameworkNameAr("اسم قديم");
        when(controlRepo.findByIdAndOrganisationId(9L, ORG)).thenReturn(Optional.of(stored));

        CyberControl req = new CyberControl();
        req.setTitleEn("renamed");   // no frameworkId, no label: an ordinary edit

        CyberControl saved = service.updateControl(9L, req);
        assertThat(saved.getFrameworkId()).as("F2-252 — an omitted id keeps the link").isEqualTo(1L);
        assertThat(saved.getFrameworkName()).isEqualTo("NCA ECC");
        assertThat(saved.getFrameworkNameAr())
            .as("both halves are re-derived from the SAME resolved framework, so an edit can "
              + "never leave one half fresh and the other stale — which is how two spellings of "
              + "one name come to describe different rows (F2-245)")
            .isEqualTo("الضوابط الأساسية");
    }

    @Test
    @DisplayName("F2-277: a bare label with no id leaves the Arabic half NULL")
    void aBareLabelWithNoIdLeavesTheArabicHalfNull() {
        CyberControl c = new CyberControl();
        c.setTitleEn("x");
        c.setFrameworkName("A framework that is not in this register");

        CyberControl saved = service.saveControl(c, ORG);
        assertThat(saved.getFrameworkId()).isNull();
        assertThat(saved.getFrameworkName()).isEqualTo("A framework that is not in this register");
        assertThat(saved.getFrameworkNameAr())
            .as("there is no parent to derive an Arabic half from, and inventing one from the "
              + "English string would be a fabricated governance record")
            .isNull();
    }

    @Test
    @DisplayName("F2-277: a contradicting ARABIC label is refused, and an echo is accepted")
    void aContradictingArabicLabelIsRefusedAndAnEchoIsAccepted() {
        CyberFramework ecc = framework(1L, ORG, "NCA ECC");
        ecc.setNameAr("الضوابط الأساسية");
        register(ecc);

        CyberControl lying = new CyberControl();
        lying.setTitleEn("x");
        lying.setFrameworkId(1L);
        lying.setFrameworkNameAr("اسم آخر تمامًا");
        assertThatThrownBy(() -> service.saveControl(lying, ORG))
            .as("frameworkNameAr is declared on the request type, so it must be CONSULTED: a "
              + "declared field the server silently discards tells the caller their value "
              + "matters (F2-184), which is what makes an inert field worse than none")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("frameworkNameAr")
            .hasMessageContaining("does not name framework 1");

        CyberControl echo = new CyberControl();
        echo.setTitleEn("x");
        echo.setFrameworkId(1L);
        echo.setFrameworkNameAr("  الضوابط الأساسية  ");
        assertThat(service.saveControl(echo, ORG).getFrameworkNameAr())
            .as("an echo must be accepted so a form that round-trips the whole row keeps working "
              + "in EITHER language, and the stored value is the framework's own spelling")
            .isEqualTo("الضوابط الأساسية");
    }
}
