package com.tss.grc.module.intake.spi;

import com.tss.grc.module.intake.IntakeCatalog;
import com.tss.grc.module.intake.dto.TriageDecisionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F2-172 — promotion creates a real record, or it refuses. There is no third outcome.
 *
 * <p>F2-171 found that pressing <i>Promote</i> wrote a row to {@code localStorage} and nothing
 * else. The server was no better: {@code triage()} copied {@code promotedToRecordId} out of the
 * request body and created nothing, so the intake's permanent note read <i>"Promoted to DPIA
 * #482913"</i> while no DPIA existed. These assertions hold the two halves of the fix that a code
 * review would not notice going away — that the <b>client cannot name the record</b>, and that a
 * target with <b>no way to create one refuses</b> rather than succeeding quietly.
 */
class IntakePromotionContractTest {

    // ── The client cannot name the record ────────────────────────────────────────────────────

    /**
     * The single highest-value assertion in this file, and the cheapest.
     *
     * <p>{@code promotedToEntityType} / {@code promotedToRecordId} were request fields, documented
     * as "echoed back so the frontend can deeplink to the new register row" — a sentence that
     * assumes a row exists. Re-adding either would restore a client-declared identity for a
     * server-side write, which is the defect, not a detail of it.
     */
    @Test
    void theTriageRequestCannotNameTheRecordItClaimsToHaveCreated() {
        List<String> offenders = Stream.of(TriageDecisionRequest.class.getDeclaredFields())
            .map(Field::getName)
            .filter(n -> n.toLowerCase().contains("promoted") || n.toLowerCase().contains("recordid"))
            .toList();

        assertThat(offenders)
            .as("TriageDecisionRequest carries %s. The promotion target comes from the kind's "
              + "catalogue entry and the record id comes from the row the promotion wrote — a "
              + "caller-supplied value for either is how 'Promoted to DPIA #482913' came to name "
              + "nothing (F2-171/F2-172)", offenders)
            .isEmpty();

        // Anti-vacuity: a renamed/emptied DTO would make the filter above trivially true.
        assertThat(Stream.of(TriageDecisionRequest.class.getDeclaredFields()).map(Field::getName))
            .as("TriageDecisionRequest lost its decision field — this test is now checking nothing")
            .contains("decision");
    }

    // ── Every target either has a port, or is a stated gap ───────────────────────────────────

    /**
     * Targets the catalogue routes to that this platform has no register for.
     *
     * <p>Not an allow-list to grow, and deliberately not empty either: writing {@code CONTROL_TEST}
     * down is the honest form of a capability the product does not have. Six kinds route here —
     * {@code CONTROL_ATTESTATION}, {@code SAUDIZATION_REPORT}, {@code GOSI_EMPLOYEE_REGISTER},
     * {@code WPS_PAYROLL_RUN}, {@code TASATTUR_SELF_ASSESSMENT}, {@code CR_ANNUAL_CONFIRMATION} —
     * and every one is a periodic regulatory submission. The nearest server-side thing is
     * {@code ccm.ControlTestRun}, which is an <em>automated</em> continuous-monitoring run executed
     * by a {@code ControlTestRunner}; filing a human's Nitaqat headcount return as one would be a
     * fabrication of a different shape, so it is refused with a message instead. F2-173.
     */
    private static final Map<String, String> TARGETS_WITHOUT_A_REGISTER = Map.of(
        "CONTROL_TEST", "F2-173 — six periodic-regulatory-submission kinds (HRSD/MOC, DD-88/DD-94) "
            + "route here and the platform has no register for one. ccm.ControlTestRun is an "
            + "automated monitoring run, not a filing.");

    private static List<RegisterPromotionPort> ports() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(RegisterPromotionPort.class));
        List<RegisterPromotionPort> out = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc.module")) {
            try {
                Class<?> c = Class.forName(bd.getBeanClassName());
                if (c.isInterface()) continue;
                // ⚠ @Component-only, and the reason is that this file's own FakePort is a
                // RegisterPromotionPort sitting in this very package. Without the filter the scan
                // instantiated it with nulls and reported target()=null as a shipped adapter — a
                // test measuring its own fixture, which is the quietest way to get a red build for
                // something that does not exist and a green one later for something that does.
                if (c.getAnnotation(org.springframework.stereotype.Component.class) == null) continue;
                // Adapters are constructor-injected; instantiate with nulls purely to read the two
                // constant methods. promote() is never called here.
                var ctor = c.getDeclaredConstructors()[0];
                ctor.setAccessible(true);
                out.add((RegisterPromotionPort) ctor.newInstance(new Object[ctor.getParameterCount()]));
            } catch (Throwable ignored) {
                // A port that will not construct is caught by the Spring context, not here.
            }
        }
        return out;
    }

    @Test
    void theScanFindsThePorts() {
        // 13 adapters shipped with F2-172. A collapsed scan makes every assertion below vacuous.
        assertThat(ports())
            .as("no RegisterPromotionPort implementations found — the classpath scan broke, and "
              + "'every target has a port' would then be a statement about an empty set")
            .hasSizeGreaterThan(12);
    }

    @Test
    void everyCataloguedTargetEitherHasAPortOrIsARecordedGap() {
        Set<String> served = new TreeSet<>();
        for (RegisterPromotionPort p : ports()) served.add(p.target());

        Map<String, List<String>> unserved = new LinkedHashMap<>();
        for (var e : IntakeCatalog.all().entrySet()) {
            String target = e.getValue().defaultTarget();
            if (served.contains(target) || TARGETS_WITHOUT_A_REGISTER.containsKey(target)) continue;
            unserved.computeIfAbsent(target, k -> new ArrayList<>()).add(e.getKey());
        }

        assertThat(unserved)
            .as("""
                These intake kinds route to a register that no RegisterPromotionPort serves, so \
                promoting one now fails with a 409. That is the correct behaviour and this is the \
                correct failure: it means a kind was added (or a target changed) without deciding \
                where the record goes. Either add the adapter in the target module, or record the \
                gap in TARGETS_WITHOUT_A_REGISTER with the reason there is no register for it.

                %s""", unserved)
            .isEmpty();
    }

    @Test
    void everyRecordedGapIsStillAGap() {
        // An exemption that outlives its entry stops describing the code and starts hiding it —
        // F2-137's rule, applied to this list.
        Set<String> served = new TreeSet<>();
        for (RegisterPromotionPort p : ports()) served.add(p.target());
        assertThat(served)
            .as("a target on TARGETS_WITHOUT_A_REGISTER now has a port — delete its entry, that is "
              + "the win being recorded")
            .doesNotContainAnyElementsOf(TARGETS_WITHOUT_A_REGISTER.keySet());
    }

    @Test
    void noTwoPortsClaimTheSameTarget() {
        List<String> targets = ports().stream().map(RegisterPromotionPort::target).toList();
        assertThat(targets)
            .as("two ports serving one target means promotions land in whichever bean Spring "
              + "ordered last, and which one that is can change with an unrelated refactor")
            .doesNotHaveDuplicates();
    }

    // ── The declared permission has to be a real one ─────────────────────────────────────────

    /**
     * Every {@code (MODULE, ACTION)} pair any {@code @PreAuthorize} in the platform actually uses.
     *
     * <p>Read from <b>annotation values by reflection</b>, never from source text. That is not
     * fussiness: within this programme a source regex over guards scraped the module argument out
     * of {@code hasPermission('AUDIT','DELETE')} and invented 21 ghost roles (F2-170), and another
     * matched a javadoc phrase as a class declaration. An annotation value cannot contain prose.
     */
    private static Set<String> permissionVocabulary() {
        Pattern p = Pattern.compile("hasPermission\\(\\s*'([A-Z_]+)'\\s*,\\s*'([A-Z_]+)'\\s*\\)");
        Set<String> out = new TreeSet<>();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));
        for (var bd : scanner.findCandidateComponents("com.tss.grc.module")) {
            try {
                Class<?> c = Class.forName(bd.getBeanClassName());
                List<String> exprs = new ArrayList<>();
                PreAuthorize onType = c.getAnnotation(PreAuthorize.class);
                if (onType != null) exprs.add(onType.value());
                for (Method m : c.getDeclaredMethods()) {
                    PreAuthorize a = m.getAnnotation(PreAuthorize.class);
                    if (a != null) exprs.add(a.value());
                }
                for (String e : exprs) {
                    Matcher m = p.matcher(e);
                    while (m.find()) out.add(m.group(1) + ":" + m.group(2));
                }
            } catch (Throwable ignored) {
                // unloadable classes carry no live guard either
            }
        }
        return out;
    }

    @Test
    void everyPortDemandsAPermissionThePlatformActuallyUses() {
        Set<String> vocabulary = permissionVocabulary();
        assertThat(vocabulary)
            .as("no hasPermission pairs found by reflection — the scan broke")
            .hasSizeGreaterThan(50);

        Map<String, String> offenders = new LinkedHashMap<>();
        for (RegisterPromotionPort port : ports()) {
            String required = port.requiredPermission();
            if (required == null || !required.matches("[A-Z_]+:[A-Z_]+")) {
                offenders.put(port.getClass().getSimpleName(), "not MODULE:ACTION — " + required);
            } else if (!vocabulary.contains(required)) {
                offenders.put(port.getClass().getSimpleName(), required + " is not a pair any "
                    + "@PreAuthorize in this platform uses. A permission nobody grants is a guard "
                    + "nobody passes, and it reads as the feature being broken");
            }
        }
        assertThat(offenders)
            .as("""
                A promotion port must demand the same permission the target module's own create \
                endpoint demands. Guessing the module name from the target is the trap: the ethics \
                register is guarded by COMPLIANCE:WRITE and the policy module by COMPLIANCE:WRITE \
                too, even though ETHICS exists in the catalogue.

                %s""", offenders)
            .isEmpty();
    }

    // ── The registry fails loudly, in both of the ways that are otherwise silent ─────────────

    private static IntakePromotionContext ctx() {
        return new IntakePromotionContext(1L, "DI-K7L9-Z4M2", "DPIA_INITIATION", "HIGH",
            "Sara", "sara@example.sa", false, "en", java.time.LocalDateTime.now(),
            null, "triager", Map.of(), "Initiate a DPIA", "بدء تقييم أثر حماية البيانات");
    }

    private record FakePort(String target, String requiredPermission, String result)
        implements RegisterPromotionPort {
        @Override public String promote(IntakePromotionContext c) { return result; }
    }

    @Test
    void anUnknownTargetIsARefusalThatNamesIt() {
        PromotionRegistry reg = new PromotionRegistry(List.of(new FakePort("RISK", "RISK:WRITE", "7")));
        assertThatThrownBy(() -> reg.promote("CONTROL_TEST", ctx()))
            .isInstanceOf(IllegalStateException.class)      // → 409, per GlobalExceptionHandler
            .hasMessageContaining("CONTROL_TEST")
            .hasMessageContaining("DI-K7L9-Z4M2");
    }

    /**
     * The subtle one. {@link RegisterPromotionPort#promote} is <em>documented</em> as returning the
     * id of a row that exists, and an adapter returning null satisfies the compiler while
     * reproducing F2-171 exactly — a promotion that records success and wrote nothing. Contracts
     * that can be broken silently get checked, not trusted.
     */
    @Test
    void aPortThatReturnsNothingIsARefusalToo() {
        for (String bad : new String[] { null, "", "   " }) {
            PromotionRegistry reg = new PromotionRegistry(
                List.of(new FakePort("DPIA", "PRIVACY:WRITE", bad)));
            assertThatThrownBy(() -> reg.promote("DPIA", ctx()))
                .as("a port returning %s must not read as a successful promotion", bad == null ? "null" : "'" + bad + "'")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has NOT been promoted");
        }
    }

    @Test
    void twoPortsForOneTargetFailAtStartupRatherThanAtRandom() {
        assertThatThrownBy(() -> new PromotionRegistry(List.of(
            new FakePort("RISK", "RISK:WRITE", "1"),
            new FakePort("RISK", "RISK:WRITE", "2"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("claim target 'RISK'");
    }

    @Test
    void aGoodPortStillWorks() {
        // Anti-vacuity for the three refusals above: a registry that rejected everything would
        // pass all of them and break every promotion in the product.
        PromotionRegistry reg = new PromotionRegistry(List.of(new FakePort("DPIA", "PRIVACY:WRITE", "482913")));
        assertThat(reg.promote("DPIA", ctx())).isEqualTo("482913");
        assertThat(reg.requiredPermissionFor("DPIA")).isEqualTo("PRIVACY:WRITE");
        assertThat(reg.requiredPermissionFor("RISK"))
            .as("an unserved target has no permission to demand — the caller must let promote() "
              + "raise the real error rather than reporting a permission problem")
            .isNull();
    }

    // ── Base stays Base ──────────────────────────────────────────────────────────────────────

    private static final Path INTAKE_MODULE = Path.of("src/main/java/com/tss/grc/module/intake");
    private static final Pattern MODULE_IMPORT =
        Pattern.compile("^\\s*import\\s+(?:static\\s+)?com\\.tss\\.grc\\.module\\.([a-z_0-9]+)\\.",
                        Pattern.MULTILINE);

    /**
     * ADR-018 homes {@code intake} in Base — mandatory, shipped to every customer, never a SKU.
     * Promotion fans out into fourteen registers and almost all of them are sold separately, so the
     * one thing that must never happen is intake importing them: the mandatory kernel would then
     * depend on commercial modules, which {@code change_adapters}' package-info calls out as the
     * worse of the two boundary mistakes. The seam is {@code intake.spi} and the adapters live in
     * the module that owns the record.
     */
    @Test
    void theIntakeModuleImportsNoPeerModule() {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(INTAKE_MODULE)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(files)
            .as("anti-vacuity: an empty scan passes this trivially. If module/intake moved, repoint "
              + "INTAKE_MODULE rather than letting the boundary go quiet")
            .hasSizeGreaterThan(8);

        List<String> violations = new ArrayList<>();
        for (Path f : files) {
            String src;
            try {
                src = Files.readString(f, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            // Strip comments first — this package's javadoc names peer modules on purpose, and a
            // scan that reads documentation as a dependency is the exact mistake F2-170 made.
            src = src.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//[^\\n]*", " ");
            Matcher m = MODULE_IMPORT.matcher(src);
            while (m.find()) {
                if (!m.group(1).equals("intake") && !m.group(1).equals("platform")) {
                    violations.add(INTAKE_MODULE.relativize(f) + " → module." + m.group(1));
                }
            }
        }
        assertThat(violations)
            .as("""
                module/intake is Base (ADR-018) and these imports reach a sold module. Promotion \
                must go through intake.spi.RegisterPromotionPort, implemented in the module that \
                owns the record — the arrow points inward to the interface, never outward to \
                fourteen SKUs:

                %s""", violations)
            .isEmpty();
    }
}
