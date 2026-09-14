package com.tss.grc.config;

import org.junit.jupiter.api.Test;

import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-149 — {@code external.properties} overrides <b>placeholders</b>, not properties, and the build
 * now knows the difference.
 *
 * <h3>What went wrong, and why no test existed</h3>
 *
 * On 2026-08-02 production could not complete a login. The mitigation was to write
 * {@code grc.security.tenant-write-guard.enabled=false} into {@code external.properties} beside the
 * jar. <b>It had no effect whatsoever.</b> That file arrives through
 * {@code spring.config.import} in {@code application.yml}, and a profile-specific document
 * ({@code application-prod.yml}) outranks {@code application.yml} <em>and everything it imports</em>
 * — so a property path set literally in the profile can never be overridden from here. Production
 * booted the following day still logging {@code [TenantWriteGuard] interceptor WIRED}, a line
 * {@link com.tss.grc.config.TenantWriteGuardConfig} emits <em>only</em> when the flag is true, and
 * the finding recorded the guard as OFF for four days.
 *
 * <p>No error. No warning. Spring resolved the higher-precedence value exactly as documented, and
 * the operator's switch was simply not connected to anything. <b>Both</b> config headers said the
 * opposite in prose: {@code application.yml} claimed "raw spring keys work too" and the template
 * called a raw key a "full override".
 *
 * <h3>Why a placeholder is different</h3>
 *
 * {@code ${SERVER_PORT:8443}} is a <em>reference</em>, resolved against the whole
 * {@code Environment} when the containing document is bound. It therefore picks up a value from any
 * property source at any precedence — which is why the other eighteen keys in the deployed file
 * always worked, and why the one key written as a real property path did not. The tell was visible
 * in the file: one line in a different style from the other eighteen, and it was the broken one.
 *
 * <h3>What this asserts</h3>
 *
 * <ol>
 *   <li>No key in the template names a Spring property <em>path</em> — those are silently inert.</li>
 *   <li>Every {@code ${SCREAMING_SNAKE}} the packaged yml files read is documented in the template,
 *       so the operator surface is complete rather than a third of itself.</li>
 *   <li>The five {@code grc.*} runtime kill-switches stay placeholders. Each was a literal
 *       {@code true}, so none could be thrown in an incident without a rebuild.</li>
 *   <li>Both config headers still state the override rule the operator acted on — asserted
 *       positively, so it cannot go vacuous.</li>
 *   <li>F2-456: no {@code @Value} names a bare env key instead of the property, which would read
 *       the <em>pre-profile</em> key and be invisible to every override the yml expresses.</li>
 *   <li>F2-456: both storage env keys reach the one storage property, in both profiles.</li>
 *   <li>DEP-4: the packaged logback config reads the {@code logging.*} properties Boot publishes,
 *       so {@code LOGGING_FILE_NAME} governs where the log actually lands.</li>
 *   <li>DEP-4: {@code LOGGING_FILE_NAME} reaches {@code logging.file.name}, and the appender's
 *       fallback path has not drifted from the yml's default.</li>
 * </ol>
 *
 * ⚠ That list is the class's own enumeration of what it covers, and it is what stops the next
 * reader asking what else belongs here — so it moves with the legs. It was three items long while
 * the class held six, because F2-456 added two legs and did not touch it (F2-454's headline, on
 * this file).
 *
 * <p>DB-free and infrastructure-free: it reads configuration off disk and walks
 * {@code src/main/java}.
 */
class ExternalConfigOverrideContractTest {

    /**
     * The switches an operator must be able to throw during an incident, with the placeholder each
     * one has to keep. Pinned deliberately rather than derived: the property is not "every boolean
     * is overridable" — 18 other literal booleans in {@code application-prod.yml} are settled
     * platform policy (Swagger off, Flyway clean disabled, secure cookies) and making those
     * operator-tunable would be a regression, not a fix. What matters is the set that stops
     * something happening.
     */
    private static final List<String[]> KILL_SWITCHES = List.of(
        new String[] { "grc.security.tenant-write-guard.enabled", "TENANT_WRITE_GUARD_ENABLED" },
        new String[] { "grc.vuln-sla.scheduler-enabled", "VULN_SLA_SCHEDULER_ENABLED" },
        new String[] { "grc.cyber-capa-sla.scheduler-enabled", "CYBER_CAPA_SLA_SCHEDULER_ENABLED" },
        new String[] { "grc.cyber-evidence-expiry.scheduler-enabled",
                       "CYBER_EVIDENCE_EXPIRY_SCHEDULER_ENABLED" },
        new String[] { "grc.workflow-request-sla.scheduler-enabled",
                       "WORKFLOW_REQUEST_SLA_SCHEDULER_ENABLED" });

    /** 59 placeholders across the two packaged files today. A collapse means the scan broke. */
    private static final int MIN_PLACEHOLDERS_READ = 40;
    /** 60 documented keys in the template today. */
    private static final int MIN_TEMPLATE_KEYS = 40;

    /** {@code ${NAME}} or {@code ${NAME:default}} — the reference form that always resolves. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]{2,})[:}]");

    /** {@code KEY=} or {@code #KEY=} — a commented example still documents the knob. */
    private static final Pattern TEMPLATE_KEY =
        Pattern.compile("(?m)^\\s*#?\\s*([A-Z][A-Z0-9_]{2,})\\s*=");

    /** A lower-case dotted key is a Spring property path, which cannot be overridden from here. */
    private static final Pattern TEMPLATE_PROPERTY_PATH =
        Pattern.compile("(?m)^\\s*([a-z][a-zA-Z0-9]*(?:[.-][a-zA-Z0-9]+)+)\\s*=(.*)$");

    @Test
    void theTemplateNamesNoSpringPropertyPathBecauseThoseAreSilentlyInert() throws IOException {
        List<String> paths = new ArrayList<>();
        Matcher m = TEMPLATE_PROPERTY_PATH.matcher(template());
        while (m.find()) {
            paths.add(m.group(1) + "=" + m.group(2).trim());
        }

        assertThat(paths).as("""
            The external-config template names %d Spring property path(s). Those do NOT override \
            anything a profile-specific file also sets: external.properties is imported from \
            application.yml, and application-prod.yml outranks it. There is no error and no \
            warning — the operator throws a switch that is not connected.

            This is F2-149 exactly. On 2026-08-02 `grc.security.tenant-write-guard.enabled=false` \
            was written into the deployed file to mitigate a P0; production booted the next day \
            still logging "[TenantWriteGuard] interceptor WIRED" and the finding recorded the guard \
            as OFF for four days.

            Fix: give the setting a ${SCREAMING_SNAKE:default} placeholder in the yml and put the \
            UPPER_CASE name here instead.

            %s""".formatted(paths.size(), String.join("\n            ", paths)))
            .isEmpty();
    }

    @Test
    void everyPlaceholderTheYmlReadsIsDocumentedInTheTemplate() throws IOException {
        Set<String> read = placeholdersRead();
        Set<String> documented = new LinkedHashSet<>();
        Matcher m = TEMPLATE_KEY.matcher(template());
        while (m.find()) {
            documented.add(m.group(1));
        }

        assertThat(read.size()).as(
            "Only %d ${PLACEHOLDER} references found across the packaged yml files. Under %d means "
            + "the scan broke, and the assertion below would compare an empty set to a full one — "
            + "passing for the wrong reason.", read.size(), MIN_PLACEHOLDERS_READ)
            .isGreaterThanOrEqualTo(MIN_PLACEHOLDERS_READ);
        assertThat(documented.size()).as(
            "Only %d keys documented in the template. Under %d means the file was truncated or the "
            + "key pattern stopped matching it.", documented.size(), MIN_TEMPLATE_KEYS)
            .isGreaterThanOrEqualTo(MIN_TEMPLATE_KEYS);

        List<String> undocumented = new ArrayList<>(read);
        undocumented.removeAll(documented);
        undocumented.sort(null);

        assertThat(undocumented).as("""
            %d configuration knob(s) are read by the packaged yml files and appear nowhere in \
            external.properties.template, so an operator cannot know they exist. That is how the \
            deployed file came to document a third of the surface while the boot log warned about a \
            key — AUDIT_INTEGRITY_KEY — the template did not name.

            Add each to tss-grc-backend/external.properties.template, commented out with its \
            default and a sentence on what changing it costs. A commented example counts: the point \
            is discoverability, not a value.

            %s""".formatted(undocumented.size(), String.join("\n            ", undocumented)))
            .isEmpty();
    }

    @Test
    void everyRuntimeKillSwitchStaysOperatorOverridable() throws IOException {
        String prod = read("src/main/resources/application-prod.yml");
        String tpl = template();

        List<String> broken = new ArrayList<>();
        for (String[] pair : KILL_SWITCHES) {
            String path = pair[0];
            String name = pair[1];
            String leaf = path.substring(path.lastIndexOf('.') + 1);

            // The leaf must be bound to the placeholder, not to a literal. Matching the leaf plus
            // the placeholder name together is what makes this specific: `enabled: true` under a
            // different parent is somebody else's setting.
            boolean wired = Pattern.compile(Pattern.quote(leaf) + ":\\s*\\$\\{" + name + "[:}]")
                .matcher(prod).find();
            if (!wired) {
                broken.add("%s must be `%s: ${%s:true}` in application-prod.yml".formatted(path, leaf, name));
            }
            if (!Pattern.compile("(?m)^\\s*#?\\s*" + name + "\\s*=").matcher(tpl).find()) {
                broken.add("%s is not documented in external.properties.template".formatted(name));
            }
        }

        assertThat(broken).as("""
            A runtime kill-switch is not overridable from external.properties, so it cannot be \
            thrown during an incident without rebuilding and redeploying the jar.

            All five of these were literal booleans in application-prod.yml until F2-149. The \
            tenant write guard is the one that mattered: an operator tried to turn it off in the \
            middle of a P0 and could not, and nobody found out for four days because nothing \
            reports an outranked property.

            %s""".formatted(String.join("\n            ", broken)))
            .isEmpty();
    }

    @Test
    void bothConfigHeadersStateTheOverrideRuleTheOperatorNeeds() throws IOException {
        // ⚠ Asserted positively, on purpose. The first version of this test looked for the OLD
        // wording and skipped the check whenever the corrective paragraph was present — which the
        // corrected files always are, so it could only fire if a reverter deleted the explanation
        // too. A negative assertion with an escape hatch for the fix is a test that passes because
        // of the thing it is supposed to be checking. Requiring the correction cannot go vacuous.
        //
        // The claim lived in prose, in two files, and prose is what the operator acted on during a
        // P0. A doc fix with no test is a doc fix the next comment-tidy reverts.
        List<String> missing = new ArrayList<>();
        for (String f : List.of("src/main/resources/application.yml", "external.properties.template")) {
            String body = read(f).toLowerCase();
            if (!body.contains("spring.config.import")) {
                missing.add(f + " no longer explains where external.properties is loaded from");
            }
            boolean namesTheRule = body.contains("outranks") || body.contains("silently ignored")
                                   || body.contains("only the upper_case");
            if (!namesTheRule) {
                missing.add(f + " does not state that a raw Spring property path is outranked and "
                            + "silently ignored — only ${PLACEHOLDER} names override");
            }
        }

        assertThat(missing).as("""
            A config header no longer tells the operator the one rule that matters: external \
            .properties overrides ${PLACEHOLDER} names, and a raw Spring property path set in a \
            profile-specific file is silently outranked. An operator acted on the previous, false \
            wording during a P0 (F2-149) and the switch they threw did nothing for four days.

            %s""".formatted(String.join("\n            ", missing)))
            .isEmpty();
    }

    /**
     * F2-456 \u2014 a {@code @Value} on a bare env-var name reads the <em>pre-profile</em> key, so it
     * is invisible to every override the yml expresses.
     *
     * <p>{@code AttachmentStorageService} and {@code EvidenceStorageService} both read
     * {@code @Value("${STORAGE_BASE_PATH:./uploads}")} while the yml exposes the same concept as the
     * property {@code grc.storage.base-path} \u2014 and {@code application-prod.yml} re-points that
     * property at {@code ${FILE_STORAGE_PATH}}. The two spellings therefore <b>agree in dev and
     * diverge in prod</b>: binder PDFs went to the operator's configured root while attachments and
     * evidence went to {@code ./uploads}, relative to the service working directory. No amount of
     * local testing could see it, because dev is the profile where the two agree.
     *
     * <p>Hard zero, no exemption map, and the argument is F2-214's: a property path is always
     * available, and the yml is where profile precedence lives, so reading the raw env key is never
     * correct code. This is the same question the rest of this class asks \u2014 <em>can the operator's
     * key reach the code?</em> \u2014 put to the Java side instead of the template, which is why it is
     * a leg here rather than a second gate (F2-190).
     */
    @Test
    void noValuePlaceholderNamesABareEnvKeyInsteadOfTheProperty() throws IOException {
        List<String> offenders = new ArrayList<>();
        int sites = 0;

        for (Path p : javaSources()) {
            // Strip comments first. This register's convention is that a fix QUOTES the expression
            // it retired (F2-450), and both fixed sites do exactly that \u2014 so a raw scan would read
            // the epitaph as the corpse (F2-193) and fail on a correct tree.
            String body = COMMENT.matcher(Files.readString(p)).replaceAll("");
            Matcher m = VALUE_PLACEHOLDER.matcher(body);
            while (m.find()) {
                sites++;
                String key = m.group(1).trim();
                if (BARE_ENV_KEY.matcher(key).matches()) {
                    offenders.add("%s reads ${%s} \u2014 the raw env key, not a property"
                                  .formatted(p.getFileName(), key));
                }
            }
        }

        // Anti-vacuity, and the floor sits above the largest WRONG answer rather than above zero
        // (F2-215): if the comment stripper ever blanks real code, this collapses to 0 and fires,
        // where the offender list below would simply read as a clean tree.
        assertThat(sites).as(
            "Only %d @Value placeholder sites found across src/main/java. That is far below the %d "
            + "measured, which means the walk or the comment stripper stopped matching and the "
            + "assertion below is passing over an unread tree.", sites, MIN_VALUE_SITES)
            .isGreaterThanOrEqualTo(MIN_VALUE_SITES);

        assertThat(offenders).as("""
            A @Value names a bare SCREAMING_SNAKE env key instead of the Spring property.

            That reads the key BEFORE any profile has a chance to re-point it, so every override \
            application-prod.yml expresses is invisible to it. F2-456: two file-storage backends \
            read ${STORAGE_BASE_PATH} while prod maps grc.storage.base-path onto ${FILE_STORAGE_PATH}, \
            so attachments and evidence went to ./uploads while binder exports went to the \
            operator's configured root. The spellings agree in dev, which is why it survived.

            Read the property (${grc.some.thing}) and let the yml decide which env key feeds it.

            %s""".formatted(String.join("\n            ", offenders)))
            .isEmpty();
    }

    /**
     * F2-456 \u2014 both env keys must reach the one storage property, in both profiles.
     *
     * <p>The fix above is only half of it: the property has to actually follow the key the operator
     * set. This resolves the <b>real expressions from the yml</b> through a real Spring
     * {@code Environment}, so it cannot drift from the files and it tests Spring's nested-default
     * resolution rather than my reading of it.
     *
     * <p>The counter-factual leg is the one that matters: with only {@code FILE_STORAGE_PATH} set
     * \u2014 the shape the template ships \u2014 the retired expression still resolves to {@code ./uploads}.
     * That is the divergence stated as a measurement rather than as a claim.
     */
    @Test
    void bothStorageEnvKeysReachTheOneStoragePropertyInBothProfiles() throws IOException {
        String base = storageExpression("src/main/resources/application.yml");
        String prod = storageExpression("src/main/resources/application-prod.yml");

        String configured = "C:/tss-grc/data/uploads";

        // prod: the operator sets FILE_STORAGE_PATH (what the runbook does) \u2014 property follows it.
        assertThat(resolve(prod, Map.of("FILE_STORAGE_PATH", configured))).as(
            "application-prod.yml must map FILE_STORAGE_PATH onto grc.storage.base-path")
            .isEqualTo(configured);

        // prod: the operator sets the OTHER key instead \u2014 still reaches the same property, which is
        // what makes the template's "either one works" wording true.
        assertThat(resolve(prod, Map.of("STORAGE_BASE_PATH", configured))).as(
            "prod must accept STORAGE_BASE_PATH as a fallback, or the template's wording is false "
            + "and an operator who set the base-profile key silently gets the yml default")
            .isEqualTo(configured);

        // base profile: mirror image, so neither key is profile-specific in effect.
        assertThat(resolve(base, Map.of("STORAGE_BASE_PATH", configured))).isEqualTo(configured);
        assertThat(resolve(base, Map.of("FILE_STORAGE_PATH", configured))).isEqualTo(configured);

        // Neither set: each profile keeps its own documented default.
        assertThat(resolve(prod, Map.of())).isEqualTo("/var/data/tss-grc/uploads");
        assertThat(resolve(base, Map.of())).isEqualTo("./uploads");

        // The counter-factual. This is the defect, measured: the expression the two storage
        // backends used to carry does NOT follow FILE_STORAGE_PATH, so it lands somewhere else
        // entirely while the property beside it is correct.
        assertThat(resolve("${STORAGE_BASE_PATH:./uploads}", Map.of("FILE_STORAGE_PATH", configured)))
            .as("the retired @Value expression must still demonstrate the divergence \u2014 if this ever "
                + "equals the configured root, the counter-factual has stopped proving anything")
            .isEqualTo("./uploads")
            .isNotEqualTo(configured);
    }

    /**
     * DEP-4 - the packaged logback config must read the {@code logging.*} properties Boot
     * publishes, or {@code LOGGING_FILE_NAME} governs nothing.
     *
     * <p>A custom {@code logback-spring.xml} is delegated to <em>instead of</em> Boot's
     * {@code base.xml}, so {@code org/springframework/boot/logging/logback/file-appender.xml} - the
     * only shipped file that reads {@code ${LOG_FILE}} and {@code ${LOGBACK_ROLLINGPOLICY_*}} - is
     * never included. Boot still SETS those system properties before applying this file, precisely
     * so a custom config can use them. The appender carried four literals instead, so
     * {@code application-prod.yml}'s whole {@code logging.file.name} block and the template's
     * {@code LOGGING_FILE_NAME} entry affected nothing. On Windows the hard-coded Linux path then
     * resolved DRIVE-RELATIVE and succeeded silently, because {@code FileAppender} creates missing
     * parent directories - so the file DEPLOYMENT.md's first-boot check greps could not exist.
     *
     * <p>This is the same question the rest of this class asks - <em>can the operator's key reach
     * the thing it names?</em> - put to the logging config, which is why it is a leg here rather
     * than a second gate (F2-190).
     */
    @Test
    void theLogbackAppenderReadsTheLoggingPropertiesBootPublishes() throws IOException {
        String raw = read("src/main/resources/logback-spring.xml");
        String xml = XML_COMMENT.matcher(raw).replaceAll("");

        // Anti-vacuity, and the anchor sits above the largest WRONG answer rather than above zero
        // (F2-215): a stripper that blanked the markup would make the negative assertion below pass
        // over nothing, and an empty file reads exactly like a clean one.
        assertThat(xml).as(
            "The JSON_FILE appender is not in the comment-stripped logback config. Either it was "
            + "renamed or the comment stripper ate real markup - either way the assertions below "
            + "are passing over an unread file.")
            .contains("<appender name=\"JSON_FILE\"");

        assertThat(appenderFileExpression()).as(
            "The rolling file appender must take its path from ${LOG_FILE}, the system property "
            + "Boot sets from logging.file.name. A literal there makes LOGGING_FILE_NAME and the "
            + "whole logging.file.name block decoration (DEP-4).")
            .startsWith("${LOG_FILE");

        for (String key : List.of("LOGBACK_ROLLINGPOLICY_MAX_FILE_SIZE",
                                  "LOGBACK_ROLLINGPOLICY_MAX_HISTORY",
                                  "LOGBACK_ROLLINGPOLICY_TOTAL_SIZE_CAP")) {
            assertThat(xml).as(
                "The appender must read ${%s}. Boot publishes it from "
                + "logging.logback.rollingpolicy.*, and the literal it replaced matched the yml by "
                + "coincidence - which is exactly what made those three values look wired (DEP-4).",
                key)
                .contains("${" + key);
        }

        // The retired literal. The comment above the appender QUOTES it to record what it replaced,
        // so this assertion only means anything over stripped markup - and the pair below is what
        // makes the stripping demonstrably load-bearing rather than decorative (F2-450).
        assertThat(xml).as(
            "The comment-stripped logback config still names `application.json`, the hard-coded "
            + "path DEP-4 retired. On Windows that Linux absolute path resolves DRIVE-RELATIVE and "
            + "succeeds silently, so the file the runbook greps never appears.")
            .doesNotContain("application.json");

        assertThat(raw).as(
            "The DEP-4 comment must keep quoting the path it replaced. Quoting it in full is what "
            + "makes the comment-stripping above demonstrably load-bearing AND the documentation "
            + "faithful at the same time (F2-450); paraphrase it away and the negative assertion "
            + "starts passing for a reason nobody chose.")
            .contains("application.json");
    }

    /**
     * DEP-4 - {@code LOGGING_FILE_NAME} must reach {@code logging.file.name}, and the appender's
     * own fallback must not drift from the yml's default.
     *
     * <p>Mirrors the storage leg above: it resolves the expression <b>the yml really writes</b>
     * through a real Spring {@code Environment}, so it cannot drift from the file. Unlike F2-456's
     * storage path there is exactly ONE declaration to read - {@code application.yml} and
     * {@code application-dev.yml} declare no {@code logging.file.name} at all - so one profile's
     * expression is the whole operator surface.
     *
     * <p>⚠ The two halves of the chain use two placeholder SYNTAXES, and only one of them is
     * Spring's. The yml writes {@code ${VAR:default}} and is resolved by Spring; the appender
     * writes {@code ${VAR:-default}} and is resolved by <b>Logback</b>, whose default operator is
     * {@code :-}. Handing the appender's expression to a Spring {@code Environment} splits on the
     * first colon and returns the default with a leading hyphen - so the appender's fallback is
     * compared as a LITERAL here, never resolved. Nothing in production resolves that string with
     * Spring, so resolving it in a test would be asserting a chain that does not ship.
     */
    @Test
    void theLoggingFileNameEnvKeyReachesTheLoggingFileNameProperty() throws IOException {
        String prod = loggingFileNameExpression();
        String configured = "C:/tss-grc/logs/application.log";

        assertThat(resolve(prod, Map.of("LOGGING_FILE_NAME", configured))).as(
            "application-prod.yml must map LOGGING_FILE_NAME onto logging.file.name, or the "
            + "template entry and DEPLOYMENT.md's grep path both describe a knob nothing reads")
            .isEqualTo(configured);

        assertThat(resolve(prod, Map.of())).as(
            "with nothing set, the packaged Linux default must stand")
            .isEqualTo("/var/log/tss-grc/application.log");

        // The appender's fallback is a SECOND copy of that default, reachable only when no
        // logging.file.name is configured. Asserting they agree is what stops the two drifting
        // into naming different files (F2-177). Literal comparison, per the syntax note above.
        assertThat(appenderFallbackPath()).as(
            "the appender's ${LOG_FILE:-...} fallback must still name the yml's own default, or a "
            + "deployment with no logging.file.name writes somewhere neither artefact describes")
            .isEqualTo(resolve(prod, Map.of()));

        // The counter-factual, and it states the mechanism rather than restating the fix: the
        // chain is LOGGING_FILE_NAME -> (Boot publishes) -> LOG_FILE -> (Logback resolves) -> the
        // path. The appender must therefore name LOG_FILE and NOT the operator's key - which is
        // precisely why the yml line is load-bearing, and why the literal that used to sit here
        // could never have been reached by an operator setting LOGGING_FILE_NAME.
        assertThat(appenderFileExpression()).as(
            "the appender must NOT read LOGGING_FILE_NAME directly - if it ever names the "
            + "operator's key, the chain under test is not the chain that ships")
            .doesNotContain("LOGGING_FILE_NAME");
    }

    /** DEP-4: the {@code logging.file.name} expression as application-prod.yml really writes it. */
    private static String loggingFileNameExpression() throws IOException {
        Matcher m = LOGGING_FILE_NAME_LINE.matcher(read("src/main/resources/application-prod.yml"));
        List<String> found = new ArrayList<>();
        while (m.find()) {
            found.add(m.group(1).trim());
        }
        assertThat(found).as(
            "Expected exactly one placeholder-valued logging.file.name line in "
            + "application-prod.yml. Zero means the logging block moved and every assertion built "
            + "on it is vacuous; more than one means this helper is reading whichever sorted first.")
            .hasSize(1);
        return found.get(0);
    }

    /** DEP-4: the {@code <file>} expression as the packaged appender really writes it. */
    private static String appenderFileExpression() throws IOException {
        String xml = XML_COMMENT.matcher(read("src/main/resources/logback-spring.xml"))
                                .replaceAll("");
        Matcher m = APPENDER_FILE_ELEMENT.matcher(xml);
        List<String> found = new ArrayList<>();
        while (m.find()) {
            found.add(m.group(1).trim());
        }
        assertThat(found).as(
            "Expected exactly one <file> element in the comment-stripped logback config. Zero "
            + "means the appender moved and every assertion built on it is vacuous; more than one "
            + "means this helper is reading whichever sorted first.")
            .hasSize(1);
        return found.get(0);
    }

    /**
     * DEP-4: the appender's own fallback path, parsed out of Logback's {@code ${VAR:-default}}
     * rather than resolved - see the syntax note on the behavioural leg.
     */
    private static String appenderFallbackPath() throws IOException {
        String expression = appenderFileExpression();
        Matcher m = LOGBACK_DEFAULT.matcher(expression);
        assertThat(m.matches()).as(
            "Expected the appender's <file> to be a Logback placeholder with a default, "
            + "${NAME:-path}, but it reads %s. If it has become a bare literal the appender no "
            + "longer honours logging.file.name at all, which is DEP-4's own defect.", expression)
            .isTrue();
        return m.group(1);
    }

    /** F2-456: the {@code grc.storage.base-path} expression as the yml really writes it. */
    private static String storageExpression(String relative) throws IOException {
        Matcher m = STORAGE_BASE_PATH_LINE.matcher(read(relative));
        List<String> found = new ArrayList<>();
        while (m.find()) {
            found.add(m.group(1).trim());
        }
        assertThat(found).as(
            "Expected exactly one placeholder-valued base-path line in %s. The actuator's "
            + "management.endpoints.web.base-path is a literal, so it must not match; zero matches "
            + "means the storage block moved and every assertion built on it is vacuous.", relative)
            .hasSize(1);
        return found.get(0);
    }

    /** Resolve an expression through a real Spring Environment, with {@code env} on top. */
    private static String resolve(String expression, Map<String, Object> env) {
        StandardEnvironment e = new StandardEnvironment();
        e.getPropertySources().addFirst(new MapPropertySource("f2-456-external", env));
        return e.resolveRequiredPlaceholders(expression);
    }

    private static List<Path> javaSources() throws IOException {
        Path root = Paths.get("src/main/java").toAbsolutePath().normalize();
        assertThat(Files.isDirectory(root)).as(
            "Expected %s to be a directory. A wrong root would make the scan vacuous.", root)
            .isTrue();
        try (var walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    /** F2-456: {@code @Value("${...}")}, capturing the key up to a default or the closing brace. */
    private static final Pattern VALUE_PLACEHOLDER =
        Pattern.compile("@Value\\(\\s*\"\\$\\{([^:}\"]+)");

    /** A bare env-var name \u2014 no dots, so no profile can re-point it. */
    private static final Pattern BARE_ENV_KEY = Pattern.compile("[A-Z][A-Z0-9_]*");

    /**
     * Line comments and block comments in ONE left-to-right alternation. Two sequential passes
     * corrupt the source (F2-198), and the {@code (?<!:)} is what stops a {@code http://} inside a
     * string literal opening a comment.
     */
    private static final Pattern COMMENT = Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    /** Only the storage block writes base-path as a placeholder; the actuator one is a literal. */
    private static final Pattern STORAGE_BASE_PATH_LINE =
        Pattern.compile("(?m)^\\s*base-path:\\s*(\\$\\{.*?)\\s*$");

    /** 38 @Value placeholder sites in src/main/java today. */
    private static final int MIN_VALUE_SITES = 30;

    /**
     * DEP-4: an XML comment stripper, and it has to be its OWN pattern — {@link #COMMENT}
     * strips JAVA comments and matches nothing in markup. The DEP-4 comment above the appender
     * quotes the path it replaced, so a scan of that file for the retired literal reads the
     * epitaph as the corpse unless the comments go first (F2-193).
     *
     * <p>Safe as a lazy alternation-free pattern over a 119-line file; the recorded
     * StackOverflowError applies to an alternation inside {@code *} over whole-file input.
     */
    private static final Pattern XML_COMMENT = Pattern.compile("<!--[\\s\\S]*?-->");

    /** DEP-4: the appender's {@code <file>} element — exactly one, in stripped markup. */
    private static final Pattern APPENDER_FILE_ELEMENT =
        Pattern.compile("(?s)<file>\\s*(.*?)\\s*</file>");

    /**
     * DEP-4: LOGBACK's placeholder-with-default, whose operator is {@code :-} where Spring's is
     * {@code :}. Anchored, so it refuses a bare literal rather than matching some substring of one.
     */
    private static final Pattern LOGBACK_DEFAULT =
        Pattern.compile("\\$\\{[A-Z_][A-Z0-9_]*:-(.*)\\}");

    /**
     * DEP-4: {@code logging.file.name}'s placeholder-valued line. Unlike F2-456's storage path
     * this concept is declared in exactly ONE profile — application.yml and application-dev.yml
     * declare no {@code logging.file.name} at all — so one expression is the whole surface.
     */
    private static final Pattern LOGGING_FILE_NAME_LINE =
        Pattern.compile("(?m)^\\s*name:\\s*(\\$\\{LOGGING_FILE_NAME[^}]*\\})\\s*$");

    // ── evidence ─────────────────────────────────────────────────────────────

    private static Set<String> placeholdersRead() throws IOException {
        Set<String> out = new LinkedHashSet<>();
        for (String f : List.of("src/main/resources/application.yml",
                                "src/main/resources/application-prod.yml")) {
            // Strip comments first. A ${PLACEHOLDER} named inside prose — this class's own headers
            // do it — is documentation, not a binding, and counting it would demand a template
            // entry for a word in a sentence. Same rule this repo keeps relearning in the other
            // direction: strip comments before asserting what a file does.
            String body = read(f).replaceAll("(?m)^\\s*#.*$", " ").replaceAll("\\s#.*$", " ");
            Matcher m = PLACEHOLDER.matcher(body);
            while (m.find()) {
                out.add(m.group(1));
            }
        }
        return out;
    }

    private static String template() throws IOException {
        return read("external.properties.template");
    }

    private static String read(String relative) throws IOException {
        Path p = Paths.get(relative).toAbsolutePath().normalize();
        assertThat(Files.exists(p)).as(
            "Expected %s to exist. This class reads configuration off disk, so a wrong path would "
            + "make every assertion vacuous rather than failing.", p)
            .isTrue();
        return Files.readString(p);
    }
}
