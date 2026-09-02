package com.tss.grc.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ⚠⚠ F2-318 — {@code AuditLogService.recordEvent} does {@code org != null ? org : 1L}, so <b>any</b>
 * off-request writer that does not anchor {@code TenantContext} files its audit rows under
 * organisation 1.
 *
 * <p>[[F2-317]] found one instance by reading. Measured over every {@code @Scheduled} method,
 * following one hop of delegation because that defect was exactly one hop down: <b>8 of 33 reach
 * {@code audit.log} and 3 anchored no tenant</b>. F2-317 closed
 * {@code RiskReviewService#flag}; F2-318 closed {@code AccessRequestService#scheduledExpirySweep}
 * (⚠ {@code @Scheduled(PT1H)} and <b>ungated</b>, so it runs hourly in every environment) and
 * {@code VulnerabilitySlaComputeService#runEscalationCheck}.
 *
 * <p>🔑 All three shared one tell: <b>the notification or domain event beside the audit row was
 * already org-explicit and the audit row was not.</b> One of them put the organisation in the audit
 * <em>message text</em> — {@code "(org " + org + ")"} — which is what an author writes when the row's
 * own tenant column is not carrying it.
 *
 * <p>⚠ This is a <b>source</b> gate, deliberately. The defect is not observable from a mocked
 * repository (the organisation is never an argument — it is read from the thread) and not observable
 * in either database, because every row happens to be organisation 1: what made all three harmless
 * is the data, not the code ([[F2-182]]).
 */
class OffRequestAuditTenancyTest {

    private static final String EOL = System.lineSeparator();

    private static final Path MODULE =
        Paths.get("src", "main", "java", "com", "tss", "grc", "module");

    private static final Pattern COMMENTS = Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    /**
     * A method signature, shared by every leg that has to resolve one. F2-424 hoisted it out of the
     * F2-323 leg: {@link #transactionalAt} needs the same notion of "a signature" the population
     * does, and a second copy is how the two come apart ([[F2-217]]).
     */
    private static final Pattern METHOD_SIGNATURE = Pattern.compile(
        "^[ \\t]+(?:public|private|protected)[^;=(){}\\n]{0,140}?\\b(\\w+)"
        + "\\([^;{}\\n]{0,400}\\)[^;{\\n]{0,90}\\{", Pattern.MULTILINE);

    private static final Pattern AUDIT = Pattern.compile(
        "\\b(?:audit|auditLog|auditService|auditLogService)\\.(?:log|logSystem|recordEvent)\\s*\\(");

    /**
     * ⚠⚠ F2-320 — declaring the event org-less is the OTHER correct answer, so the invariant accepts
     * it. {@code logSystem(...)} / {@code systemEvent()} say "this belongs to no tenant", which is
     * true of exactly one writer today.
     *
     * <p>🔑 This turned an exemption into a property: {@code DbSizeMonitorJob} used to need a named
     * entry in {@link #NOT_THIS_DEFECT} and now passes on its own merits. <b>An exemption is a weaker
     * artefact than a property</b> ([[F2-205]]) — it asserts that somebody read the file once, where
     * this asserts what the file does.
     */
    private static final Pattern DECLARES_ORG_LESS =
        Pattern.compile("\\.logSystem\\s*\\(|\\.systemEvent\\s*\\(");
    /**
     * ⚠⚠ A <b>real</b> anchor, not a restore.
     *
     * <p>The first draft of this gate matched any {@code setCurrentTenant(...)} — and every fix in
     * this family adds <b>two</b>: the anchor taken from the loop variable, and a
     * {@code finally { setCurrentTenant(ambient) }} that puts the caller's tenant back. So deleting
     * the anchor left the restore behind, the pattern still matched, and all three pre-fix mutations
     * <b>survived</b>. 🔑 <b>A substring check satisfied by something other than its claim — and the
     * thing satisfying it existed only because of the fix, so the gate got weaker the moment it was
     * applied.</b>
     *
     * <p>So the argument may not be the saved ambient value. {@code allowCrossTenantWrite()} counts
     * because a writer that deliberately opens the cross-tenant scope has answered the question.
     */
    private static final Pattern ANCHOR = Pattern.compile(
        "TenantContext\\.setCurrentTenant\\(\\s*(?!ambient|null)\\w"
        + "|TenantContext\\.allowCrossTenantWrite\\s*\\(");

    /**
     * The three closed by F2-317 and F2-318, held by name so a revert names itself. A file that
     * writes an audit row off-request and anchors nothing is the defect; these are the files that
     * used to.
     */
    private static final Map<String, String> CLOSED = new LinkedHashMap<>() {{
        put("RiskReviewService.java",
            "F2-317. Shared by RiskReviewController (tenant set) and RiskReviewScheduler (not), so "
            + "the all-tenants sweep audited every row against tenant 1. Anchored per row from "
            + "r.getOrganisationId(), ambient restored because of the controller caller.");
        put("AccessRequestService.java",
            "F2-318. @Scheduled(PT1H) and UNGATED, so it runs hourly everywhere. Looped "
            + "distinctOrganisationIds() and interpolated the org into the audit MESSAGE — the tell "
            + "that the row's tenant column was not carrying it. Anchored per organisation.");
        put("VulnerabilitySlaComputeService.java",
            "F2-318. orgId arrives as a PARAMETER; the DomainEvent used it and the audit row did "
            + "not. Two callers, a controller and the scheduler, so the ambient tenant is restored "
            + "rather than cleared.");
    }};

    /**
     * ⚠⚠ The three the gate surfaced on its first run that are <b>not</b> this defect — each for a
     * different reason, which is exactly why this is a named map and not a blanket rule.
     *
     * <p>A named map proves a reason exists and never that it is true ([[F2-188]]). These three were
     * read; the two pollers were the interesting ones, because they <em>consult</em> the tenant
     * without setting it, which a {@code setCurrentTenant} scan cannot tell from ignoring it.
     */
    private static final Map<String, String> NOT_THIS_DEFECT = new LinkedHashMap<>() {{
        put("CrowdStrikeInboundService.java",
            "REFUSES rather than mis-attributes. The poll path reads TenantContext.getCurrentTenant() "
            + "and bails when it is null, so off-request it does nothing at all instead of writing "
            + "under tenant 1. Its audit row is on the ingest path, which is reachable on-request "
            + "with a tenant set. (F2-193 measured that its polling is disabled in every profile "
            + "anyway, which is a separate question from this one.)");
        put("TenableInboundService.java",
            "Same shape as the CrowdStrike poller: reads the tenant, refuses when absent. A scan for "
            + "setCurrentTenant cannot distinguish consulting the tenant from ignoring it, which is "
            + "why both needed reading rather than a pattern.");
        // ⚠ DbSizeMonitorJob USED to be here, exempted as "genuinely org-less". F2-320 gave it a
        // way to say so — audit.logSystem(...) — so it now satisfies the invariant on its own merits
        // and needs no entry. 🔑 An exemption asserts that somebody read the file once; a property
        // asserts what the file does. When a fix lets an exemption become a property, delete the
        // exemption rather than keeping both (F2-205).
    }};

    private static String strip(String s) {
        return COMMENTS.matcher(s).replaceAll(m -> " ".repeat(m.group().length()));
    }

    /**
     * ⚠⚠ The invariant. A file reached by a {@code @Scheduled} method that writes an audit row must
     * anchor a tenant somewhere.
     *
     * <p>⚠ <b>File-scoped, and that limitation is stated rather than implied.</b> [[F2-317]]'s first
     * draft looked forward a fixed number of characters from the call site and failed on its own fix,
     * because the writer sat in a helper the window did not reach. A character window is a fragile way
     * to ask a per-method question, so this asks whether the class anchors a tenant at all: it catches
     * a class that never does, and would miss one anchoring in a different method.
     *
     * <p>⚠ It is only as strong as {@link #ANCHOR}, whose first draft was satisfied by the
     * <em>restore</em> line each fix introduces — see that field. The mutation harness is what found
     * it; on a fixed tree the two patterns are indistinguishable.
     */
    @Test
    @DisplayName("every scheduled class that writes an audit row anchors a tenant")
    void noScheduledAuditWriterRelisOnTheTenantOneFallback() throws IOException {
        List<String> unanchored = new ArrayList<>();
        int writers = 0;
        try (Stream<Path> walk = Files.walk(MODULE)) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = strip(Files.readString(p, StandardCharsets.UTF_8));
                if (!src.contains("@Scheduled")) {
                    continue;
                }
                // the scheduled class itself, plus the collaborators it delegates to, are covered by
                // requiring the SCHEDULED class to anchor OR to delegate to a class that does. Here
                // the narrower, honest claim: if the scheduled class writes an audit row itself, it
                // must anchor.
                if (!AUDIT.matcher(src).find()) {
                    continue;
                }
                writers++;
                String file = p.getFileName().toString();
                if (NOT_THIS_DEFECT.containsKey(file)) {
                    continue;
                }
                if (!ANCHOR.matcher(src).find() && !DECLARES_ORG_LESS.matcher(src).find()) {
                    unanchored.add(file);
                }
            }
        }

        assertThat(writers).as("""
            No @Scheduled class writing an audit row was found, so this leg is vacuous — either the \
            walk is reading the wrong tree or the audit call shape changed.""")
            .isGreaterThanOrEqualTo(2);

        assertThat(unanchored).as("""
            A @Scheduled class writes an audit row and never anchors a tenant.

            AuditLogService.recordEvent does `org != null ? org : 1L`, so off-request every one of \
            those rows is filed under organisation 1 whatever tenant's record it describes: tenant 1 \
            gains entries about other tenants, and those tenants get none. Anchor the tenant on the \
            row or the loop variable, and RESTORE the ambient one — restoring is correct whether or \
            not a caller supplied it, clearing only when none did.""")
            .isEmpty();
    }

    /**
     * The reverse direction: each closed file must still anchor, and must still carry a substantial
     * reason. A named map proves a reason exists and never that it is true ([[F2-188]]) — what it
     * buys here is that a revert fails with the file's own name.
     */
    @Test
    @DisplayName("each file F2-317 and F2-318 closed still anchors its tenant")
    void everyClosedWriterStillAnchors() throws IOException {
        List<String> regressed = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(MODULE)) {
            Map<String, Path> found = new LinkedHashMap<>();
            for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (CLOSED.containsKey(p.getFileName().toString())) {
                    found.put(p.getFileName().toString(), p);
                }
            }
            assertThat(found.keySet()).as("""
                A file this map names no longer exists, so its entry is an exemption for something \
                that is not in the population — the direction nobody writes first (F2-199).""")
                .containsExactlyInAnyOrderElementsOf(CLOSED.keySet());

            for (Map.Entry<String, Path> e : found.entrySet()) {
                String src = strip(Files.readString(e.getValue(), StandardCharsets.UTF_8));
                if (!ANCHOR.matcher(src).find()) {
                    regressed.add(e.getKey() + " — " + CLOSED.get(e.getKey()));
                }
            }
        }
        assertThat(regressed).as("A file closed by F2-317 or F2-318 no longer anchors its tenant.")
            .isEmpty();

        List<String> thin = CLOSED.entrySet().stream()
            .filter(e -> e.getValue().length() < 120)
            .map(Map.Entry::getKey)
            .toList();
        assertThat(thin).as("""
            A reason is a bare label. It must say which callers exist and why restore-versus-clear \
            was chosen, because that is the part the next reader gets wrong.""").isEmpty();
    }

    /**
     * The reverse direction for the exclusions: each must still be a {@code @Scheduled} class that
     * writes an audit row and anchors no tenant — otherwise its entry is an exemption for something
     * that is not in the population, which reads exactly like a real one ([[F2-199]]).
     */
    @Test
    @DisplayName("each excluded file is still a scheduled audit writer that anchors nothing")
    void everyExclusionIsStillInThePopulation() throws IOException {
        List<String> stale = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(MODULE)) {
            Map<String, String> found = new LinkedHashMap<>();
            for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                String file = p.getFileName().toString();
                if (NOT_THIS_DEFECT.containsKey(file)) {
                    found.put(file, strip(Files.readString(p, StandardCharsets.UTF_8)));
                }
            }
            assertThat(found.keySet()).as("an excluded file no longer exists")
                .containsExactlyInAnyOrderElementsOf(NOT_THIS_DEFECT.keySet());
            for (Map.Entry<String, String> e : found.entrySet()) {
                String src = e.getValue();
                if (!src.contains("@Scheduled") || !AUDIT.matcher(src).find()) {
                    stale.add(e.getKey() + " is no longer a scheduled audit writer, so excluding it "
                              + "says nothing");
                } else if (ANCHOR.matcher(src).find()) {
                    stale.add(e.getKey() + " now anchors a tenant, so the exclusion is obsolete and "
                              + "hides whether it stays anchored");
                }
            }
        }
        assertThat(stale).as("An exclusion no longer describes the file it names.").isEmpty();

        List<String> thin = NOT_THIS_DEFECT.entrySet().stream()
            .filter(e -> e.getValue().length() < 140)
            .map(Map.Entry::getKey)
            .toList();
        assertThat(thin).as("""
            An exclusion reason is a bare label. Both remaining entries REFUSE rather than mis-attribute, which the invariant cannot express structurally — that is the whole content, and it is why they are exemptions where DbSizeMonitorJob is now a property.""").isEmpty();
    }

    /**
     * ⚠ The fallback itself is asserted, because every leg above is only interesting while it exists.
     * If {@code AuditLogService} ever starts refusing an unset tenant instead of defaulting to 1,
     * this whole family becomes a loud failure rather than a silent mis-attribution — and these gates
     * should then be re-read rather than left asserting a hazard that is gone.
     */
    @Test
    void theTenantOneFallbackThisFamilyDependsOnStillExists() throws IOException {
        Path audit = Paths.get("src", "main", "java", "com", "tss", "grc", "module", "platform",
                               "auditlog", "service", "AuditLogService.java");
        String src = strip(Files.readString(audit, StandardCharsets.UTF_8));
        Matcher m = Pattern.compile("setOrganisationId\\(\\s*org\\s*!=\\s*null\\s*\\?\\s*org\\s*:\\s*1L")
            .matcher(src);
        assertThat(m.find()).as("""
            AuditLogService no longer silently falls back to tenant 1. That is an improvement, and it \
            means F2-317's and F2-318's fixes now guard a hazard that fails loudly instead — re-read \
            the legs above rather than leaving them asserting the old mechanism.""")
            .isTrue();
    }

    // ── F2-320: the fallback is opt-in, and silence is what changed ─────────────────────────────

    /**
     * ⚠⚠ F2-320 — the tenant-1 fallback is right for exactly one caller and was silently wrong for
     * four, so it now announces itself unless the caller declares a system event.
     *
     * <p>🔑 <b>The row is written either way, so a leg asserting the row proves nothing.</b> The
     * warning is the whole deliverable — so this constructs the real service over mocks, invokes
     * {@code recordEvent}, and reads a Logback appender.
     *
     * <p>⚠ My first version of this leg attached an appender it never read and asserted through a
     * helper that <b>reimplemented the condition</b>, so it proved the helper agreed with itself —
     * the defect this register keeps recording, inside the leg meant to observe a warning. Replaced
     * rather than patched.
     *
     * <p>⚠ A WARN rather than a throw, deliberately: off-request audit paths cannot be enumerated
     * statically (listener registration, reflection, SpEL), so refusing would risk breaking a path
     * nobody has seen ([[F2-214]]).
     */
    @Test
    @DisplayName("F2-320: an audit write with no tenant warns, and a declared system event does not")
    void anUnanchoredAuditWriteAnnouncesItselfAndADeclaredOneDoesNot() {
        var repo = org.mockito.Mockito.mock(
            com.tss.grc.module.platform.auditlog.repository.AuditLogRepository.class);
        org.mockito.Mockito.when(repo.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(i -> i.getArgument(0));
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<jakarta.servlet.http.HttpServletRequest>
            noRequest = org.mockito.Mockito.mock(
                org.springframework.beans.factory.ObjectProvider.class);
        var service = new com.tss.grc.module.platform.auditlog.service.AuditLogService(
            repo, noRequest);

        ch.qos.logback.classic.Logger auditLogger = (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(
                com.tss.grc.module.platform.auditlog.service.AuditLogService.class);
        var appender = new ch.qos.logback.core.read.ListAppender
            <ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        auditLogger.addAppender(appender);
        try {
            com.tss.grc.common.tenant.TenantContext.clear();

            service.recordEvent(new com.tss.grc.module.platform.auditlog.service.AuditLogService
                .AuditEventBuilder().actionType("PROBE_UNANCHORED").sourceModule("test")
                .message("probe"));
            assertThat(appender.list.stream()
                    .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN)
                    .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .toList()).as("""
                An audit write with no tenant produced no warning, so a row filed under organisation                 1 by mistake stays indistinguishable from one filed there on purpose — which is how                 F2-317, F2-318 and F2-319 each stayed invisible.""")
                .anyMatch(m -> m.contains("PROBE_UNANCHORED") && m.contains("systemEvent"));

            // F2-321 — the warning must name the CALLING CLASS. Its first version printed
            // b.sourceModule, which is NULL on 3,524 of the 3,809 off-request rows in dev because
            // the log(...) convenience method never sets it — so it would have read "module null"
            // for 92% of the population it exists to surface. A warning naming nothing locatable is
            // worse than none: it looks informative, so the reader stops.
            assertThat(appender.list.stream()
                    .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .toList())
                .as("the warning must name a class the reader can go and fix, never a null module")
                .anyMatch(m -> m.contains(getClass().getName()))
                .noneMatch(m -> m.contains("null"));

            appender.list.clear();
            service.recordEvent(new com.tss.grc.module.platform.auditlog.service.AuditLogService
                .AuditEventBuilder().actionType("PROBE_SYSTEM").sourceModule("test")
                .systemEvent().message("probe"));
            assertThat(appender.list).as("""
                A caller that declared systemEvent() still warned. The one legitimate caller                 (DbSizeMonitorJob, auditing the database itself) would then be noise — and noise is                 how a warning stops being read.""")
                .isEmpty();

            // and with a tenant set, neither warns
            appender.list.clear();
            com.tss.grc.common.tenant.TenantContext.setCurrentTenant(4L);
            var saved = service.recordEvent(new com.tss.grc.module.platform.auditlog.service
                .AuditLogService.AuditEventBuilder().actionType("PROBE_ANCHORED")
                .sourceModule("test").message("probe"));
            assertThat(appender.list).as("an anchored write must be silent").isEmpty();
            assertThat(saved.getOrganisationId()).as("and must carry the anchored tenant")
                .isEqualTo(4L);
        } finally {
            auditLogger.detachAppender(appender);
            com.tss.grc.common.tenant.TenantContext.clear();
        }
    }

    /**
     * ⚠ The structural half, because {@link #warnsFor} reimplements the condition and would keep
     * passing if {@code recordEvent} stopped consulting it — [[F2-274]]'s rule that a leg exercising a
     * primitive says nothing about whether the code under review calls it.
     */
    @Test
    @DisplayName("F2-320: recordEvent consults the opt-in, and the one declared caller declares it")
    void theOptInIsConsultedAndItsOneLegitimateCallerUsesIt() throws IOException {
        Path service = Paths.get("src", "main", "java", "com", "tss", "grc", "module", "platform",
                                 "auditlog", "service", "AuditLogService.java");
        String src = strip(Files.readString(service, StandardCharsets.UTF_8));
        assertThat(src).as("""
            recordEvent no longer guards the fallback on the opt-in, so either every write warns or             none does. The condition is `org == null && !b.systemEvent` and both halves matter.""")
            .contains("org == null && !b.systemEvent")
            .contains("log.warn");

        Path job = Paths.get("src", "main", "java", "com", "tss", "grc", "module", "platform",
                             "health", "DbSizeMonitorJob.java");
        assertThat(strip(Files.readString(job, StandardCharsets.UTF_8))).as("""
            The one genuinely org-less writer stopped declaring itself, so it is now indistinguishable             from the four that were defects — which is the distinction F2-320 exists to make.""")
            .contains("audit.logSystem(");
    }

    // ── F2-322: the per-org catch means something now ────────────────────────────────────────────

    /**
     * ⚠⚠ F2-322 — the hourly IAM expiry sweep gets a transaction <b>per organisation</b>, which is what
     * makes its per-org catch mean anything.
     *
     * <p>It used to be {@code @Transactional}, so {@code audit} joined one batch-wide transaction: a
     * failure on one tenant marked it rollback-only and <b>every</b> organisation's work was discarded
     * at commit, while the catch made the method read as resilient. [[F2-317]] recorded that and
     * declined to fix it because the change is structural rather than a wrapper.
     *
     * <p>⚠⚠ <b>This leg is structural because the behavioural half is not observable in a unit test.</b>
     * {@code inNewTransaction} runs <em>inline</em> when {@code txManager} is null, which is the case
     * for a directly-constructed service — so the boundary does not exist and a failure propagates
     * exactly as before. {@code EmergencyRatificationSlaScheduler}'s wrapper records the same trap in
     * the same words: <em>"the inline path is why the six unit tests could not catch the missing
     * transaction."</em> 🔑 <b>A leg that cannot see the thing it is named for is worse than one that
     * says so.</b>
     */
    @Test
    @DisplayName("F2-322: the expiry sweep is not batch-transactional and isolates each organisation")
    void theExpirySweepIsolatesEachOrganisation() throws IOException {
        Path svc = Paths.get("src", "main", "java", "com", "tss", "grc", "module", "iam",
                             "governance", "service", "AccessRequestService.java");
        String all = strip(Files.readString(svc, StandardCharsets.UTF_8));

        int at = all.indexOf("public void scheduledExpirySweep()");
        assertThat(at).as("scheduledExpirySweep not found — the gate is reading the wrong file")
            .isGreaterThanOrEqualTo(0);

        // the annotations immediately above the method
        String annotations = all.substring(Math.max(0, at - 260), at);
        assertThat(annotations).as("""
            The sweep is @Transactional again, so every organisation shares one transaction: a             failure on any of them discards all of their work at commit, and the per-org catch below             becomes decoration.""")
            .contains("@Scheduled")
            .doesNotContain("@Transactional");

        int end = all.indexOf(EOL + "    }", at);
        String body = all.substring(at, end < 0 ? all.length() : end);
        assertThat(body).as("""
            The sweep no longer opens a transaction per organisation, so nothing isolates one             tenant's failure from the rest.""")
            .contains("inNewTransaction(")
            .contains("catch (RuntimeException");

        // and the wrapper must tolerate an absent manager, or every unit test of this class breaks
        String wrapper = all.substring(all.indexOf("private <T> T inNewTransaction"));
        assertThat(wrapper.substring(0, Math.min(400, wrapper.length()))).as("""
            inNewTransaction no longer tolerates a null transaction manager. That is what lets a             directly-constructed unit test run at all — and the reason the isolation itself cannot             be asserted here.""")
            .contains("txManager == null");
    }

    // ── F2-323: a per-item catch inside a batch transaction, measured as a boundary ──────────────

    /**
     * ⚠⚠ F2-323 — a {@code @Scheduled} method that loops with a per-item catch must not be
     * {@code @Transactional} unless the loop body opens its own transaction.
     *
     * <p>Otherwise one item's failure marks the batch transaction rollback-only and <b>every</b>
     * item's work is discarded at commit, while the catch makes the method read as resilient
     * ([[F2-322]]). 🔑 [[F2-317]] measured this population and counted the <b>catch</b> — "0 of 33
     * sweeps lack a per-item catch", which is true and says nothing about whether the catch can mean
     * anything. Asked of the boundary instead, the answer was <b>1 of 21</b>:
     * {@code ReportSchedulerService#tick}, closed by F2-323.
     *
     * <p>A hard zero with <b>no exemption map</b>: dropping the annotation or wrapping the body is
     * always available, so there is no correct code for this to fail on ([[F2-214]]).
     *
     * <p>⚠ It asks whether a boundary is <b>opened</b>, not whether the isolation works — the latter
     * is not observable in a unit test, because the wrappers run inline when no transaction manager
     * is present. Stated rather than implied.
     */
    @Test
    @DisplayName("F2-323: no scheduled sweep catches per item inside a batch-wide transaction")
    void noPerItemCatchSitsInsideABatchWideTransaction() throws IOException {
        Pattern ownTx = Pattern.compile("inNewTransaction\\s*\\(|TransactionTemplate"
                                        + "|PROPAGATION_REQUIRES_NEW");

        List<ScheduledLoop> population = scheduledLoopsWithAPerItemCatch();
        List<String> decoys = new ArrayList<>();
        for (ScheduledLoop s : population) {
            if (transactionalAt(s.src(), s.sigStart()) && !ownTx.matcher(s.body()).find()) {
                decoys.add(s.file() + "#" + s.name());
            }
        }
        int examined = population.size();

        assertThat(examined).as("""
            No looping sweep with a per-item catch was found, so this leg is vacuous — the population             was 21 when F2-323 measured it.""")
            .isGreaterThanOrEqualTo(15);

        assertThat(decoys).as("""
            A scheduled sweep catches per item inside a batch-wide transaction.

            Any joined write that fails marks that transaction rollback-only, so every item's work is             discarded at commit while the catch makes the method read as resilient. Either drop             @Transactional from the sweep, or wrap each item's work in its own transaction — the             platform has that wrapper in six files now.""")
            .isEmpty();
        // ⚠ F2-323 — every wrapper this family added must tolerate an ABSENT transaction manager, or
        // a directly-constructed unit test cannot call the sweep at all. The mutation removing that
        // tolerance SURVIVED, because no test currently calls the reporting tick — so the property is
        // asserted structurally rather than left to a test that does not exist.
        for (String f : List.of("reporting/service/ReportSchedulerService.java",
                                "iam/governance/service/AccessRequestService.java")) {
            String w = strip(Files.readString(MODULE.resolve(f), StandardCharsets.UTF_8));
            int at = w.indexOf("private void inNewTransaction");
            if (at < 0) {
                at = w.indexOf("private <T> T inNewTransaction");
            }
            assertThat(at).as("%s has no inNewTransaction wrapper any more", f)
                .isGreaterThanOrEqualTo(0);
            assertThat(w.substring(at, Math.min(w.length(), at + 500)))
                .as(f + "'s inNewTransaction no longer tolerates a null transaction manager, which is "
                    + "what lets a directly-constructed unit test run the sweep — and is why the "
                    + "isolation itself cannot be asserted here.")
                .contains("txManager == null");
        }
    }

    // ── F2-424: an annotation a javadoc separates from its method ────────────────────────────

    /**
     * ⚠⚠ F2-424 — Java treats a comment as whitespace, so {@code @Transactional /** … *}{@code /
     * public void x()} binds the annotation to {@code x} and works. A scanner that resolves the
     * annotation with a fixed <b>lookback window</b> from the signature cannot see it once the
     * javadoc is longer than the window — and the method then reads as <b>non-transactional</b>,
     * which is the quiet direction: F2-322/F2-323's decoy would go unreported.
     *
     * <p>The leg above used {@code src.substring(sig.start() - 300, sig.start())} over a stripper
     * that <b>blanks comments with spaces of the same length</b>, so the offsets survive and a
     * javadoc of more than 300 characters pushes the annotation clean out of the window. Measured:
     * the shape exists at <b>10 sites across 8 files</b> in {@code src/main} — and
     * {@code ChangeManagementService#recordApproval} is one of them, which is how this was found.
     * A scratch atomicity sweep reported it as a non-transactional multi-write method: a complete,
     * plausible, <b>false</b> finding that only reading the file refuted.
     *
     * <p>⚠ <b>Latent, and say which you have</b> ([[F2-283]]): none of the gate's 21 members is
     * hidden today. What makes it harmless is <em>where the ten sites happen to be</em> — none is a
     * {@code @Scheduled} looping sweep — rather than anything the gate checks ([[F2-182]]).
     *
     * <p>This leg is the demonstration, and it is what discriminates: with the window resolver the
     * two verdicts agree everywhere, so the blind set is empty and the leg fires.
     */
    @Test
    @DisplayName("F2-424: a javadoc between the annotation and its method defeats a fixed window")
    void aJavadocBetweenTheAnnotationAndItsMethodDefeatsAFixedWindow() throws IOException {
        Pattern shape = Pattern.compile("(?m)^[ \\t]*@Transactional[ \\t]*\\r?\\n[ \\t]*/\\*\\*");
        List<String> blind = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(MODULE)) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                String raw = Files.readString(p, StandardCharsets.UTF_8);
                Matcher shaped = shape.matcher(raw);
                if (!shaped.find()) {
                    continue;
                }
                // strip() blanks a comment with spaces of the SAME length, so an offset taken from
                // the raw source is valid in the stripped one — which is the whole mechanism here.
                String src = strip(raw);
                shaped.reset();
                while (shaped.find()) {
                    Matcher sig = METHOD_SIGNATURE.matcher(src);
                    if (!sig.find(shaped.start())) {
                        continue;
                    }
                    // ⚠ A multi-line signature matches METHOD_SIGNATURE not at all, so the regex
                    // resolves to a LATER method and reports THAT method's verdict — the shaped
                    // site is mis-attributed, never reported false. Measured: 1 of the 10, and it
                    // is ChangeManagementService#recordApproval, the very site that fooled the
                    // scratch sweep — so that one is invisible to BOTH resolvers, for a second and
                    // independent reason. Nothing is asserted per site, so this cannot fail on
                    // correct code ([[F2-214]]) and the floor below clears it 7 wide.
                    if (transactionalAt(src, sig.start())
                            && !windowSaysTransactional(src, sig.start())) {
                        blind.add(p.getFileName() + "#" + sig.group(1));
                    }
                }
            }
        }

        assertThat(blind).as("""
            No site in src/main carries @Transactional above a javadoc long enough to fall outside a
            300-character lookback — so this leg is measuring nothing and the boundary resolver it
            protects is no longer load-bearing. It was 10 sites across 8 files when F2-424 measured
            it. Re-derive before deleting the resolver: a hard zero over a population the instrument
            cannot see is the most reassuring possible artefact ([[F2-205]]).""")
            .isNotEmpty();
    }

    /**
     * ⚠ F2-424 — a tripwire, not a finding: the gate's own conclusion must not depend on how long
     * a member's javadoc is. Today the two resolvers agree at 21 of 21, so this leg does <b>not</b>
     * discriminate under the pre-fix control — its subject is in the future, and saying so beats
     * counting its green as a pass ([[F2-225]]).
     */
    @Test
    @DisplayName("F2-424: no sweep's transactionality depends on how long its javadoc is")
    void noSweepsTransactionalityDependsOnItsJavadocLength() throws IOException {
        List<ScheduledLoop> population = scheduledLoopsWithAPerItemCatch();
        List<String> divergent = new ArrayList<>();
        for (ScheduledLoop s : population) {
            if (transactionalAt(s.src(), s.sigStart())
                    != windowSaysTransactional(s.src(), s.sigStart())) {
                divergent.add(s.file() + "#" + s.name());
            }
        }

        assertThat(population.size()).as("""
            No looping sweep with a per-item catch was found, so this leg is vacuous — the population
            was 21 when F2-323 measured it.""")
            .isGreaterThanOrEqualTo(15);

        assertThat(divergent).as("""
            A scheduled sweep's transactionality now depends on how far its annotation sits from its
            signature — a javadoc has pushed @Transactional outside the 300-character window the leg
            above used to read. The boundary resolver sees it; a window would not. Read the member:
            if it loops with a per-item catch inside a batch-wide transaction, it is F2-322's decoy
            and the sibling leg should already have failed.""")
            .isEmpty();
    }

    /**
     * ⚠ F2-424 — the synthetic self-test. A hard zero over a detector that has stopped matching is
     * indistinguishable from a clean tree ([[F2-235]]), and both directions matter: the resolver
     * must read <b>across</b> a javadoc of any length, and must <b>not</b> attribute the previous
     * method's annotation to this one (the previous method's own signature sits in between, which is
     * exactly what a fixed window has no way to notice).
     */
    @Test
    @DisplayName("F2-424: the resolver reads across a javadoc and stops at the previous method")
    void theResolverReadsAcrossAJavadocAndStopsAtThePreviousMethod() {
        String pad = "/** " + "x".repeat(600) + " */";
        String separated = strip(
            "class Fixture {\n"
            + "    @Transactional\n"
            + "    " + pad + "\n"
            + "    public void sweep() { for (Long o : orgs) { try { w(o); } catch (Exception e) { } } }\n"
            + "}\n");
        int sweep = signatureOf(separated, "sweep");

        assertThat(transactionalAt(separated, sweep)).as("""
            The resolver stopped reading before the annotation. A javadoc is whitespace to the
            compiler, so the method IS transactional — resolve the annotation by walking back to the
            previous member boundary, never by a fixed character count.""")
            .isTrue();
        assertThat(windowSaysTransactional(separated, sweep)).as("""
            The 300-character window can suddenly see across a 600-character javadoc, so this test is
            no longer contrasting the two resolvers and the leg above proves nothing.""")
            .isFalse();

        String previousOnly = strip(
            "class Fixture {\n"
            + "    @Transactional\n"
            + "    public void other() { }\n"
            + "\n"
            + "    public void sweep() { for (Long o : orgs) { try { w(o); } catch (Exception e) { } } }\n"
            + "}\n");
        assertThat(transactionalAt(previousOnly, signatureOf(previousOnly, "sweep"))).as("""
            The resolver attributed the PREVIOUS method's @Transactional to this one. Walking back
            must stop at the intervening signature, or every method after a transactional one reads
            as transactional and the gate reports decoys that are not there ([[F2-214]]).""")
            .isFalse();
    }

    /** Offset of the signature of {@code name} in a stripped source — for the fixtures above. */
    private static int signatureOf(String src, String name) {
        Matcher m = METHOD_SIGNATURE.matcher(src);
        while (m.find()) {
            if (name.equals(m.group(1))) {
                return m.start();
            }
        }
        throw new AssertionError("no signature for " + name + " — the fixture is not being parsed");
    }

    /**
     * Does the method whose signature starts at {@code sigStart} carry {@code @Transactional}?
     *
     * <p>F2-424: the last {@code @Transactional} before the signature with <b>no other signature in
     * between</b>. That is the definition of "this annotation belongs to this method", and unlike a
     * lookback window it is immune to a javadoc of any length and cannot capture the previous
     * method's annotation.
     */
    private static boolean transactionalAt(String src, int sigStart) {
        int at = src.lastIndexOf("@Transactional", sigStart);
        if (at < 0) {
            return false;
        }
        Matcher between = METHOD_SIGNATURE.matcher(src);
        return !(between.find(at + 1) && between.start() < sigStart);
    }

    /**
     * F2-323's original resolver, kept so F2-424's legs can <b>measure</b> the gap rather than
     * merely avoid it. Never call this to decide anything.
     */
    private static boolean windowSaysTransactional(String src, int sigStart) {
        return src.substring(Math.max(0, sigStart - 300), sigStart).contains("@Transactional");
    }

    /** One {@code @Scheduled} method that loops with a per-item catch — F2-323's population. */
    private record ScheduledLoop(String file, String src, int sigStart, String name, String body) { }

    /**
     * F2-323's population, derived once so both legs provably measure the same set. Re-typing the
     * walk is how two readers of one population come apart ([[F2-217]]).
     */
    private static List<ScheduledLoop> scheduledLoopsWithAPerItemCatch() throws IOException {
        Pattern loop = Pattern.compile("\\bfor\\s*\\(|\\.forEach\\s*\\(");
        Pattern perItemCatch = Pattern.compile("\\bcatch\\s*\\(");
        Pattern scheduled = Pattern.compile("@Scheduled\\s*\\(");
        List<ScheduledLoop> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(MODULE)) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = strip(Files.readString(p, StandardCharsets.UTF_8));
                if (!src.contains("@Scheduled")) {
                    continue;
                }
                Matcher sched = scheduled.matcher(src);
                while (sched.find()) {
                    Matcher sig = METHOD_SIGNATURE.matcher(src);
                    if (!sig.find(sched.start()) || sig.start() - sched.start() > 1500) {
                        continue;
                    }
                    String body = braceBalanced(src, sig.end() - 1);
                    if (!loop.matcher(body).find() || !perItemCatch.matcher(body).find()) {
                        continue;
                    }
                    found.add(new ScheduledLoop(p.getFileName().toString(), src, sig.start(),
                                                sig.group(1), body));
                }
            }
        }
        return found;
    }

    /** Brace-balanced body starting at an opening brace. */
    private static String braceBalanced(String src, int open) {
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return src.substring(open, i + 1);
                }
            }
        }
        return src.substring(open);
    }
}
