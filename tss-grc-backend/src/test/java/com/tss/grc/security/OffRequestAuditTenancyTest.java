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

    private static final Path MODULE =
        Paths.get("src", "main", "java", "com", "tss", "grc", "module");

    private static final Pattern COMMENTS = Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");
    private static final Pattern AUDIT =
        Pattern.compile("\\b(?:audit|auditLog|auditService|auditLogService)\\.(?:log|recordEvent)\\s*\\(");
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
        put("DbSizeMonitorJob.java",
            "GENUINELY org-less. It audits the size of the database itself — entityType Database, "
            + "null entity id — so there is no tenant it could belong to, and the tenant-1 fallback is "
            + "the intended behaviour for a system event rather than an accident. Anchoring a tenant "
            + "here would invent one.");
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
                if (!ANCHOR.matcher(src).find()) {
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
            An exclusion reason is a bare label. Two of these three refuse rather than mis-attribute             and one is genuinely org-less — the distinction is the whole content.""").isEmpty();
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
}
