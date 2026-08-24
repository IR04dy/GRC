package com.tss.grc.security;

import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.common.tenant.TenantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/**
 * ⚠⚠ F2-324 — {@code platform.audit_log.source_module} has two readers and, until this slice, almost
 * no writers.
 *
 * <p>It is the {@code sourceModule} filter on {@code GET /api/v1/audit-log} — a free-text control on
 * a sidebar-linked page whose own subtitle promises <em>"Every state-changing action across modules —
 * filterable and traceable"</em> — and it is how {@code BcmEvidencePackageService} selects the BCM
 * evidence package's {@code audit_log.csv}. Measured over both live databases it was <b>NULL on 87.1%
 * of dev rows and 93.6% of prod rows</b>, because {@link AuditLogService#log} builds its own builder
 * and never set it: <b>686 of the 722 audit-writing sites</b> could not attribute themselves however
 * carefully they were written.
 *
 * <p>🔑 The consequence was in the emitted artefact, not the filter. The evidence package's audit CSV
 * carried <b>15.0% of the BCM trail in dev and 6.8% in prod</b>, omitting 136 and 119
 * {@code BcmAssessmentCycle} rows — the audit trail of the very cycle the package documents — with
 * {@code README.pdf} printing the truncated count as the total.
 *
 * <p>Of the 14 values the column did hold, <b>11 were registry modules and 3 were not</b>:
 * {@code automation} and {@code licensing} are {@code platform} SUB-packages and
 * {@code ethics.whistleblower} is a dotted {@code module.subpackage} token — three vocabularies in one
 * column, with a fourth in {@code ExportAuditInterceptor.moduleFromPath}'s {@code "EXPORT"} arm, which
 * named an action rather than a module.
 *
 * <p>⚠ The vocabulary here is <b>not a list</b>. A top-level package under {@code com.tss.grc.module}
 * <i>is</i> a module, enumerated exactly once by {@code module-registry.yaml} and gated by
 * {@code ModuleRegistryContractTest} — so the derivation is correct by construction and this class is
 * what ties every hand-written literal to the same authority ([[F2-217]]: a check keyed on a list
 * measures the list).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditSourceModuleContractTest {

    private static final Path BACKEND = Paths.get("").toAbsolutePath();
    private static final Path MAIN    = BACKEND.resolve("src/main/java/com/tss/grc");
    private static final Path REGISTRY =
        BACKEND.resolve("src/main/resources/module-registry.yaml");

    private static final Pattern COMMENT = Pattern.compile("(?<!:)//[^\n]*|/\\*[\\s\\S]*?\\*/");

    /** Comment-blank that preserves newlines, so reported line numbers refer to the real file. */
    private static String blank(String s) {
        Matcher m = COMMENT.matcher(s);
        StringBuilder out = new StringBuilder(s);
        while (m.find()) {
            for (int i = m.start(); i < m.end(); i++) {
                if (out.charAt(i) != '\n' && out.charAt(i) != '\r') out.setCharAt(i, ' ');
            }
        }
        return out.toString();
    }

    /**
     * The 59 top-level packages under {@code com.tss.grc.module}, read from the registry the build
     * already gates. Parsed permissively: an entry is any two-space-indented key inside the
     * {@code modules:} map, because three of the 59 are not formatted on one line and a parser that
     * assumes they are reports 56 — a smaller population, which reads exactly like a clean tree.
     */
    private static Set<String> registryModules() throws IOException {
        String yaml = Files.readString(REGISTRY, StandardCharsets.UTF_8);
        int at = yaml.indexOf("\nmodules:");
        assertThat(at).as("module-registry.yaml has no `modules:` map any more").isGreaterThan(0);
        Set<String> out = new LinkedHashSet<>();
        for (String line : yaml.substring(at + 1).split("\n")) {
            String l = line.replace("\r", "");
            if (l.isBlank() || l.stripLeading().startsWith("#")) continue;
            Matcher m = Pattern.compile("^ {2}(\\w+):").matcher(l);
            if (m.find()) {
                out.add(m.group(1));
            } else if (!l.startsWith(" ") && !l.startsWith("modules:")) {
                break;                                  // left the modules map
            }
        }
        return out;
    }

    private static List<Path> mainSources() throws IOException {
        try (Stream<Path> w = Files.walk(MAIN)) {
            return w.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    // ── the literals ─────────────────────────────────────────────────────────────────────────────

    /**
     * ⚠⚠ Every hand-written {@code sourceModule} literal on the audit ledger must name a registry
     * module.
     *
     * <p>A hard zero with <b>no exemption map</b>: naming the module is always available, so there is
     * no correct code for this to fail on ([[F2-214]]).
     *
     * <p>⚠ Scoped to files that reference {@code AuditEventBuilder} or {@code AuditLogEntry}, because
     * {@code sourceModule} is also a column on committee referrals, committee standing items, evidence
     * items, Splunk outbound events and workflow requests — five other tables whose vocabulary is
     * their own. Those writers pass variables rather than literals, so they could not match anyway;
     * the file scoping says the narrowing is deliberate rather than lucky.
     */
    @Test
    @DisplayName("F2-324: every audit sourceModule literal names a module the registry declares")
    void everyAuditSourceModuleLiteralIsARegistryModule() throws IOException {
        Set<String> modules = registryModules();
        Pattern lit = Pattern.compile("\\.?[sS]etSourceModule\\s*\\(\\s*\"([^\"]*)\"|"
                                      + "\\.sourceModule\\s*\\(\\s*\"([^\"]*)\"");
        List<String> bad = new ArrayList<>();
        int sites = 0;
        for (Path p : mainSources()) {
            String raw = Files.readString(p, StandardCharsets.UTF_8);
            if (!raw.contains("AuditEventBuilder") && !raw.contains("AuditLogEntry")) continue;
            String src = blank(raw);
            Matcher m = lit.matcher(src);
            while (m.find()) {
                String v = m.group(1) != null ? m.group(1) : m.group(2);
                sites++;
                if (!modules.contains(v.toLowerCase())) {
                    bad.add(p.getFileName() + ":" + (src.substring(0, m.start()).split("\n", -1).length)
                            + " -> '" + v + "'");
                }
            }
        }

        assertThat(modules).as("""
            The registry parse collapsed — fewer than 50 modules found, so every check below is \
            comparing against a vocabulary that is mostly missing.""")
            .hasSizeGreaterThanOrEqualTo(50);
        assertThat(sites).as("""
            No audit sourceModule literal was found at all, so this leg is vacuous — there were 24 \
            when F2-324 measured it.""")
            .isGreaterThanOrEqualTo(10);

        assertThat(bad).as("""
            An audit row is being tagged with something that is not a module.

            source_module has two readers that both match it with a single `=`: the filter on \
            GET /api/v1/audit-log and BcmEvidencePackageService's audit_log.csv. A token that is not \
            a top-level package under com.tss.grc.module cannot be found by either — that is how \
            `automation` (535 rows), `licensing` (26) and `ethics.whistleblower` (17) became \
            unreachable from a `platform`/`ethics` filter. Name the module; the action type already \
            carries the finer detail.""")
            .isEmpty();
    }

    /**
     * ⚠⚠ The export interceptor maps a URL segment to a module, and every arm it names must be one.
     *
     * <p>Its {@code "EXPORT"} arm named an <b>action</b>, so the highest-volume writer in the ledger
     * (887 EXPORT rows in dev) could tag a row with a token no module filter can match.
     *
     * <p>⚠ Its {@code default} arm is <b>deliberately not closed</b> and this leg does not pretend
     * otherwise: {@code seg.toUpperCase()} has only ever produced registry modules, because the URL
     * segment happens to be the package name ({@code /api/v1/privacy/…} → {@code PRIVACY}), and
     * narrowing it to a known set would drop those real attributions. The path→module mapping is a
     * mapping job and is filed as one.
     */
    @Test
    @DisplayName("F2-324: moduleFromPath's arms all name registry modules")
    void moduleFromPathMapsOnlyToRegistryModules() throws IOException {
        Set<String> modules = registryModules();
        String src = blank(Files.readString(MAIN.resolve("config/ExportAuditInterceptor.java"),
                                           StandardCharsets.UTF_8));
        int from = src.indexOf("static String moduleFromPath");
        assertThat(from).as("ExportAuditInterceptor.moduleFromPath is gone or renamed")
            .isGreaterThan(0);
        String body = src.substring(from, src.indexOf("private static String firstSegmentAfterApi",
                                                      from));

        // ⚠⚠ F2-324 — extract every UPPERCASE-only string literal in the switch, not only the ones
        // sitting immediately after `->`. The `default` arm returns through a ternary
        // (`seg.isEmpty() ? "EXPORT" : …`), so a literal it returns is not adjacent to the arrow at
        // all — which is exactly how the mutation restoring "EXPORT" survived the first draft of this
        // leg. Case labels are lowercase URL segments, so uppercase-only reliably distinguishes a
        // RETURN value from a MATCH value in this method.
        List<String> arms = new ArrayList<>();
        Matcher m = Pattern.compile("\"([A-Z][A-Z_]*)\"").matcher(body);
        while (m.find()) arms.add(m.group(1));

        assertThat(arms).as("""
            No mapped arm was parsed out of moduleFromPath, so this leg is vacuous — there were 11 \
            when F2-324 measured it.""")
            .hasSizeGreaterThanOrEqualTo(8);
        assertThat(arms.stream().filter(a -> !modules.contains(a.toLowerCase())).toList()).as("""
            moduleFromPath maps a URL segment to something that is not a module. Every value this \
            method returns lands in source_module, which two readers match with a single `=`.""")
            .isEmpty();
    }

    // ── the derivation ───────────────────────────────────────────────────────────────────────────

    /**
     * ⚠⚠ The frame walk must be narrowed to the <b>platform root</b>, not to this class by name.
     *
     * <p>{@code recordEvent} is {@code @Transactional}, so the bean is CGLIB-proxied and the frames
     * between a module caller and here include the generated subclass — whose class name is
     * {@code …AuditLogService$$SpringCGLIB$$0}, which is <b>not equal to</b>
     * {@code AuditLogService.class.getName()} — plus Spring's AOP interceptors. F2-321's filter was an
     * exact-equality test, so it could return Spring's proxy machinery: a diagnostic naming nothing
     * locatable, which is the defect F2-321 existed to remove.
     *
     * <p>🔑 Asserted structurally because it cannot be asserted behaviourally: the hermetic suite has
     * no Spring context, so no unit test can put a proxy frame on the stack. Without this leg the next
     * reader simplifies the walk back to an equality test and nothing fails.
     */
    @Test
    @DisplayName("F2-324: the caller walk is narrowed by package root, not by this class's name")
    void theFrameWalkIsNarrowedToThePlatformRootNotToThisClassByName() throws IOException {
        String src = blank(Files.readString(
            MAIN.resolve("module/platform/auditlog/service/AuditLogService.java"),
            StandardCharsets.UTF_8));
        int at = src.indexOf("private static String callerFrame()");
        assertThat(at).as("AuditLogService.callerFrame is gone — the shared walk F2-324 added")
            .isGreaterThan(0);
        String body = src.substring(at, Math.min(src.length(), at + 800));

        assertThat(body).as("""
            callerFrame no longer narrows on the platform package root. recordEvent is @Transactional, \
            so the stack between a module caller and here carries the CGLIB subclass and Spring's AOP \
            interceptors; a walk that only excludes this class BY NAME returns one of those. That is \
            what made F2-321's warning name Spring rather than the caller.""")
            .contains("PLATFORM_ROOT");
        assertThat(body).as("""
            callerFrame excludes this class by equality again. The CGLIB subclass is named \
            AuditLogService$$SpringCGLIB$$0, which is not EQUAL to AuditLogService's name but does \
            start with it — startsWith is the test that survives the proxy.""")
            .contains("startsWith(self)");

        int rec = src.indexOf("e.setSourceModule(");
        assertThat(rec).as("recordEvent no longer writes source_module").isGreaterThan(0);
        assertThat(src.substring(rec, rec + 160)).as("""
            recordEvent no longer derives source_module when the builder is silent. That derivation is \
            the whole fix: 686 of the 722 audit-writing sites use log(...), which constructs its own \
            builder, so nothing else can attribute them.""")
            .contains("callerModule()");
    }

    /**
     * ⚠⚠ A caller outside {@code com.tss.grc.module} derives <b>no</b> module — and the fixture for
     * that is this test's own package.
     *
     * <p>🔑 This class lives in {@code com.tss.grc.security}, which is not a module, so driving the
     * public {@code log(...)} path from here must leave {@code source_module} null. {@code config},
     * {@code security} and {@code common} are not modules, and inventing a token for them would put a
     * fifth spelling into a column that already had four.
     *
     * <p>⚠ <b>Moving this test changes what it asserts</b>, because the derivation reads the caller's
     * package. Its positive twin is deliberately in {@code module/bcm} — see
     * {@code AuditModuleDerivationTest}, and note why it cannot live in the ledger's own package.
     */
    @Test
    @DisplayName("F2-324: a caller outside com.tss.grc.module derives no module")
    void aCallerOutsideTheModuleRootDerivesNoModule(@Mock AuditLogRepository repo,
                                                    @Mock ObjectProvider<jakarta.servlet.http.HttpServletRequest> reqs) {
        AuditLogService svc = new AuditLogService(repo, reqs);
        // ⚠ recordEvent dereferences the SAVED row (it reads back the persisted id for the
        // hash chain), so a mock returning null NPEs before any assertion is reached.
        when(repo.save(any(AuditLogEntry.class))).thenAnswer(i -> i.getArgument(0));
        TenantContext.setCurrentTenant(1L);
        try {
            svc.log("CUSTOM", "Thing", 1L, "from a non-module package");
        } finally {
            TenantContext.clear();
        }
        ArgumentCaptor<AuditLogEntry> cap = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getSourceModule()).as("""
            com.tss.grc.security is not a module, so the derivation must leave source_module null \
            rather than invent a token for it.""")
            .isNull();
    }

    // ── the emitted artefact ─────────────────────────────────────────────────────────────────────

    /**
     * ⚠⚠ The BCM evidence package must not select its audit trail on {@code source_module} alone.
     *
     * <p>Historical rows carry no module and never will: the ledger is append-only and F2-31
     * hash-chains it, so a back-fill would rewrite history <b>and</b> invalidate every seal. Selecting
     * on the module alone therefore leaves the package permanently short — 15.0% of the trail in dev
     * and 6.8% in prod when F2-324 measured it.
     */
    @Test
    @DisplayName("F2-324: the BCM evidence package's audit selection covers unattributed rows")
    void theEvidencePackageWidensItsAuditSelection() throws IOException {
        String src = blank(Files.readString(
            MAIN.resolve("module/bcm/cycle/service/BcmEvidencePackageService.java"),
            StandardCharsets.UTF_8));

        assertThat(src).as("""
            The evidence package no longer uses the widened finder. Selecting on source_module alone \
            carried 15% of the BCM trail, because rows written before F2-324 have no module and cannot \
            be given one.""")
            .contains("findForModuleOrEntityTypes");
        assertThat(src).as("""
            bcmEntityTypes is gone. The widened finder needs this module's entity vocabulary, and it \
            must be DERIVED from the JPA metamodel — module/bcm declares 52 entity classes and a \
            constant naming them is the artefact F2-217 found measuring its own contents.""")
            .contains("getMetamodel()");
        assertThat(src).as("""
            The metamodel filter no longer narrows to this module's package, so the package would \
            select audit rows for every entity in the platform.""")
            .contains("com.tss.grc.module.bcm.");
    }

    /**
     * ⚠ A positive control for the registry parser, because a parser that under-reports narrows the
     * vocabulary every leg above compares against — and a narrower vocabulary means FEWER literals
     * flagged, which reads exactly like a clean tree.
     *
     * <p>Three of the 59 entries are not formatted on one line, so the obvious
     * {@code ^ {2}(\w+):\s*\{} parse returns 56 and silently drops {@code ndi},
     * {@code change_adapters} and {@code datagov}. This leg fails on that parser rather than leaving
     * the omission to be noticed by whichever literal happens to name one of them.
     */
    @Test
    @DisplayName("F2-324: the registry parse survives a multi-line entry")
    void theRegistryParseDoesNotAssumeOneLinePerModule() throws IOException {
        Set<String> modules = registryModules();
        assertThat(modules).as("""
            The registry parse dropped a module whose entry spans lines. A parser keyed on `{` being             on the same line returns 56 of 59 — and every literal check above then compares against             a vocabulary that is missing three modules.""")
            .contains("ndi", "change_adapters", "datagov");
        assertThat(modules).as("the registry is gated at 59 entries by ModuleRegistryContractTest")
            .hasSize(59);
    }

    /**
     * ⚠ Anti-vacuity for the pair of readers: both must still exist, or the legs above are protecting
     * a column nothing consults.
     */
    @Test
    @DisplayName("F2-324: source_module still has two readers")
    void theColumnStillHasTwoReaders() throws IOException {
        String repo = Files.readString(
            MAIN.resolve("module/platform/auditlog/repository/AuditLogRepository.java"),
            StandardCharsets.UTF_8);
        assertThat(repo).as("the platform-wide module filter's finder is gone")
            .contains("findByOrganisationIdAndSourceModuleOrderByCreatedAtDesc");
        assertThat(repo).as("the widened finder F2-324 added is gone")
            .contains("findForModuleOrEntityTypes");
        String ctl = Files.readString(
            MAIN.resolve("module/platform/auditlog/controller/AuditLogController.java"),
            StandardCharsets.UTF_8);
        assertThat(ctl).as("the sourceModule request parameter is gone from the audit-log endpoint")
            .contains("sourceModule");
    }
}
