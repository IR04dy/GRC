package com.tss.grc.module.reporting.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.reporting.entity.ReportRun;
import com.tss.grc.module.reporting.entity.ReportSchedule;
import com.tss.grc.module.reporting.repository.ReportRunRepository;
import com.tss.grc.module.reporting.repository.ReportScheduleRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;

/**
 * R4 — cron-driven scheduler that runs every minute, picks up due
 * {@link ReportSchedule} rows, regenerates the PDF/XLSX via
 * {@link ExportService}, attempts email delivery via
 * {@link JavaMailSender} (graceful no-op when SMTP is not configured),
 * persists a {@link ReportRun}, and recomputes {@code nextRunAt}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportSchedulerService {

    private final ReportScheduleRepository scheduleRepo;
    private final ReportRunRepository runRepo;
    private final ExportService exportService;
    private final BoardPackService boardPackService;
    private final BoardPackPptxService boardPackPptxService;
    private final com.tss.grc.module.ai.governance.service.AiRegistryExportService aiRegistryExportService;
    /** Optional — JavaMailSender bean exists only when spring.mail.* is configured. */
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    /**
     * ⚠⚠ F2-323 — injected outside the constructor, and optional, so
     * {@code @RequiredArgsConstructor} is unchanged and the two direct constructions in
     * {@code ReportCadenceContractTest} keep compiling. Same declaration as
     * {@code EmergencyRatificationSlaScheduler}, whose javadoc states this fix's rationale: <em>"one
     * unwritable change cannot roll back the escalations already recorded for the others. A batch
     * control that is all-or-nothing fails closed on the whole tenant the first time one row
     * misbehaves."</em>
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.transaction.PlatformTransactionManager txManager;

    @Value("${grc.reporting.from:noreply@tss-grc.local}")
    private String fromAddress;

    /**
     * F2-293 — every cadence {@link #advanceNextRun} can compute a next run for.
     *
     * <p>One declaration, two readers: this switch, and
     * {@code ReportScheduleService#requireSupportedCadence}, which refuses anything else at the
     * write boundary. Before this the vocabulary existed only as the switch's own {@code case}
     * labels and, separately, as {@code CADENCE_OPTIONS} in {@code ReportSchedulesPage} — two
     * copies that happened to agree while the DATABASE held a third token neither knew
     * ({@code SEMIANNUAL}, written by the seed into a table with no CHECK constraint), which the
     * {@code default} arm then rescheduled <b>daily</b>.
     *
     * <p>🔑 The screen and the scheduler agreeing is what made this invisible: F2-205's question
     * ("how many things read this, and which is the server?") assumes the disagreement is between
     * readers. Here every reader agreed and the stored DATA was the outlier.
     */
    /**
     * ⚠⚠ F2-315 — the formats {@link #renderBody} can actually produce, per report type.
     *
     * <p>Three switches decide what a caller gets and none of them agrees with the others by
     * construction: {@code renderBody} branches on the <b>report type</b>, while
     * {@link #filenameFor} and {@link #mimeFor} branch on the <b>format</b>. So before this,
     * {@code DOCX} + {@code RISK_REGISTER} produced <b>XLSX bytes</b> named {@code .pdf} with an
     * {@code application/pdf} MIME type, and {@code XLSX} + {@code BOARD_PACK} produced <b>PDF
     * bytes</b> named {@code .xlsx} with an Excel MIME type — a file that opens as nothing.
     * 🔑 <b>The form offered {@code DOCX} and no report type can emit it at all</b>, which is
     * [[F2-193]]'s rule: a catalogue must not claim a capability that does not exist.
     *
     * <p>Latent — every live schedule in both databases is {@code PDF}, which every declared type
     * handles, except one {@code XLSX} row whose type has no generator at all. So this is a
     * latent-hazard closure, not a defect discovery ([[F2-283]]).
     *
     * <p>⚠ Deliberately not a {@code ClosedVocabulary}: that primitive mirrors a {@code CHECK} and
     * there is none on this table. The claim is about <em>what this service can render</em>, so it
     * is checkable in Java — {@code ReportFormatContractTest} asserts these keys are exactly
     * {@code renderBody}'s {@code case} labels, so a new report type cannot ship without declaring
     * its formats.
     *
     * <p>⚠ The <b>seven</b> report types with no generator ([[F2-293]]'s filing) are deliberately
     * absent, and {@code requireRenderableFormat} therefore leaves them alone: a validation added to
     * a table holding rows this platform cannot render must not make those rows uneditable
     * ([[F2-183]]).
     */
    public static final Map<String, Set<String>> SUPPORTED_FORMATS = Map.of(
        "RISK_REGISTER",     Set.of("PDF", "XLSX"),
        "FINDINGS_AGING",    Set.of("PDF", "XLSX"),
        "KRI_DASHBOARD",     Set.of("PDF", "XLSX"),
        "BOARD_PACK",        Set.of("PDF", "PPTX"),
        "AI_MODEL_REGISTRY", Set.of("PDF", "XLSX"));

    public static final List<String> SUPPORTED_CADENCES =
        List.of("DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "SEMIANNUAL");

    /**
     * ⚠⚠ F2-323 — each due schedule runs in its <b>own</b> transaction, and this method is no
     * longer {@code @Transactional}.
     *
     * <p>It used to hold one transaction across every due schedule. Two consequences, and the second
     * is the one that bites: a database-level failure persisting the fifth schedule's run row would
     * discard the first four's rows and their {@code next_run_at} advances, and the transaction stayed
     * open across up to eleven report generations — each rendering a PDF or workbook — on a
     * 60-second scheduler.
     *
     * <p>🔑 <b>The catch below was UNREACHABLE before this.</b> {@link #runOnce} has its own
     * try/catch that persists {@code FAILED} and returns normally, so nothing ever reached here —
     * which is precisely why F2-322's census flagged it as a decoy. It becomes reachable <em>and</em>
     * useful now: a commit failure in one schedule's own transaction throws into this loop and is
     * handled without touching the others. The catch was written for a world where each schedule had
     * its own boundary; it simply did not have one.
     *
     * <p>⚠ Found by the census [[F2-322]] filed — [[F2-317]] had measured "0 of 33 sweeps lack a
     * per-item catch", which counted the <b>catch</b> rather than whether the catch could mean
     * anything. Asked of the transaction boundary instead, the answer is <b>1 of 21</b>, and this is it.
     */
    @Scheduled(fixedDelayString = "${grc.reporting.scheduler-period-ms:60000}")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        List<ReportSchedule> due = scheduleRepo.findDue(now);
        if (due.isEmpty()) return;
        log.info("Reporting scheduler tick — {} due schedules", due.size());
        for (ReportSchedule s : due) {
            try { inNewTransaction(() -> runOnce(s, "CRON")); }
            catch (Exception e) {
                log.warn("Scheduled report '{}' failed: {}", s.getName(), e.getMessage());
                inNewTransaction(() -> recordRun(s, "FAILED", 0, 0, e.getMessage()));
                inNewTransaction(() -> advanceNextRun(s));
            }
        }
    }

    /**
     * ⚠⚠ F2-323 — runs {@code body} in its own transaction, or <b>inline</b> when no transaction
     * manager is present (directly-constructed unit tests).
     *
     * <p>The inline path is why <b>no unit test can verify the isolation this provides</b>: with a null
     * {@code txManager} there is no boundary and a failure propagates exactly as before.
     * {@code EmergencyRatificationSlaScheduler}'s wrapper records the same trap in the same words —
     * <em>"the inline path is why the six unit tests could not catch the missing transaction"</em> —
     * which is why F2-323's leg is structural and says so.
     */
    private void inNewTransaction(Runnable body) {
        if (txManager == null) {
            body.run();
            return;
        }
        var tpl = new org.springframework.transaction.support.TransactionTemplate(txManager);
        tpl.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tpl.executeWithoutResult(status -> body.run());
    }

    @Transactional
    public ReportRun runNow(ReportSchedule s) {
        return runOnce(s, "MANUAL");
    }

    /** Pure-side-effect run: generate, send, persist, advance. */
    private ReportRun runOnce(ReportSchedule s, String triggeredBy) {
        long start = System.currentTimeMillis();
        Long previousTenant = TenantContext.getCurrentTenant();
        try {
            TenantContext.setCurrentTenant(s.getOrganisationId());
            // F2-316 — one call, so the bytes and their two labels cannot disagree.
            Rendered r = render(s);
            byte[] body = r.body();
            String filename = r.filename();
            String mime = r.mime();
            String message = sendEmail(s, filename, mime, body);

            long duration = System.currentTimeMillis() - start;
            ReportRun run = recordRun(s, "SUCCESS", duration, body.length, message);
            advanceNextRun(s);
            s.setLastRunStatus("SUCCESS");
            s.setLastRunMessage(message);
            s.setLastRunAt(LocalDateTime.now());
            scheduleRepo.save(s);
            return run;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            ReportRun run = recordRun(s, "FAILED", duration, 0, e.getMessage());
            advanceNextRun(s);
            s.setLastRunStatus("FAILED");
            s.setLastRunMessage(e.getMessage());
            s.setLastRunAt(LocalDateTime.now());
            scheduleRepo.save(s);
            run.setTriggeredBy(triggeredBy);
            return run;
        } finally {
            if (previousTenant != null) TenantContext.setCurrentTenant(previousTenant);
            else TenantContext.clear();
        }
    }

    /**
     * What a rendered report is: bytes and the two labels that describe them, produced together.
     *
     * <p>⚠⚠ F2-316 — the point of this record is that the three cannot be assigned separately. Before
     * it, {@code renderBody} branched on the report <b>type</b> while {@code filenameFor} and
     * {@code mimeFor} branched on the <b>format</b>, so {@code XLSX} + {@code BOARD_PACK} emitted PDF
     * bytes named {@code .xlsx} with an Excel MIME type. [[F2-315]] closed that at the write
     * boundary; this closes it at the renderer, because a row can reach the scheduler without
     * passing a boundary at all — {@code seed_real_data.py} is how [[F2-293]]'s unhandled
     * {@code SEMIANNUAL} cadence got in.
     */
    record Rendered(byte[] body, String filename, String mime) { }

    /**
     * ⚠⚠ F2-316 — one switch, three outputs per arm, {@code default -> throw}.
     *
     * <p>This is not a refactor for tidiness: it is the idiom this platform already uses everywhere
     * else it streams a download. {@code NdiReportController#streamBytes} assigns
     * {@code body}, {@code ct} and {@code ext} in the same {@code case} arm;
     * {@code MlModelController#export} derives all three from one {@code boolean pdf}; and the other
     * twenty download handlers put the format in the <b>route</b> ({@code /export.pdf},
     * {@code /export.xlsx}) so the question cannot arise. 🔑 <b>The report scheduler was the only
     * export surface that put the format in a COLUMN and then consulted it from three separate
     * switches</b> — [[F2-195]]'s shape, the codebase holding the correct rule beside the one place
     * it was not applied.
     *
     * <p>{@code PDF} is the fallback arm for every type because every reader treated a null or
     * unrecognised format as PDF, and {@code ReportFormatContractTest} asserts that PDF is
     * renderable for each declared type rather than assuming it.
     */
    private Rendered render(ReportSchedule s) {
        String type = s.getReportType();
        String fmt  = s.getFormat() == null ? "PDF" : s.getFormat().toUpperCase(Locale.ROOT);
        String lang = s.getLanguage() == null ? "en" : s.getLanguage();
        String stem = type.toLowerCase().replace('_', '-') + "-" + LocalDate.now();
        return switch (type) {
            case "RISK_REGISTER"  -> "XLSX".equals(fmt)
                ? xlsx(exportService.riskRegisterXlsx(), stem)
                : pdf(exportService.riskRegisterPdf(lang), stem);
            case "FINDINGS_AGING" -> "XLSX".equals(fmt)
                ? xlsx(exportService.findingsXlsx(), stem)
                : pdf(exportService.findingsAgingPdf(lang), stem);
            case "KRI_DASHBOARD"  -> "XLSX".equals(fmt)
                ? xlsx(exportService.kriDashboardXlsx(), stem)
                : pdf(exportService.kriDashboardPdf(lang), stem);
            case "BOARD_PACK"     -> "PPTX".equals(fmt)
                ? pptx(boardPackPptxService.generatePptx(lang), stem)
                : pdf(boardPackService.generatePdf(lang), stem);
            case "AI_MODEL_REGISTRY" -> "XLSX".equals(fmt)
                ? xlsx(aiRegistryExportService.export("XLSX"), stem)
                : pdf(aiRegistryExportService.export("PDF"), stem);
            default -> throw new IllegalArgumentException("Unknown report type: " + type);
        };
    }

    private static Rendered pdf(byte[] b, String stem) {
        return new Rendered(b, stem + ".pdf", "application/pdf");
    }

    private static Rendered xlsx(byte[] b, String stem) {
        return new Rendered(b, stem + ".xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    private static Rendered pptx(byte[] b, String stem) {
        return new Rendered(b, stem + ".pptx",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    }

    // F2-316 — `filenameFor` and `mimeFor` are gone. They branched on the FORMAT while the
    // renderer branched on the TYPE, and their shared `default -> pdf` is what labelled every
    // unrecognised format a PDF whatever bytes it held. The three now come from `render`'s one
    // switch, so a mismatch is not a bug to catch but a state that cannot be expressed.

    /** Returns a status message describing what happened (sent, dry-run, etc.). */
    private String sendEmail(ReportSchedule s, String filename, String mime, byte[] body)
            throws MessagingException {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.info("SMTP not configured — '{}' rendered ({} bytes) but not emailed (would go to: {})",
                s.getName(), body.length, s.getRecipients());
            return "Rendered " + body.length + " bytes (SMTP not configured — dry run)";
        }
        MimeMessage mm = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mm, true, "UTF-8");
        helper.setFrom(fromAddress);
        String[] to = splitAddresses(s.getRecipients());
        if (to.length == 0) throw new IllegalArgumentException("No recipients");
        helper.setTo(to);
        if (s.getCcRecipients() != null && !s.getCcRecipients().isBlank()) {
            String[] cc = splitAddresses(s.getCcRecipients());
            if (cc.length > 0) helper.setCc(cc);
        }
        helper.setSubject("[TSS GRC] " + s.getName());
        helper.setText("Automated " + s.getReportType().replace('_', ' ').toLowerCase() +
            " report attached. Cadence: " + s.getCadence() + ". Language: " + s.getLanguage(), false);
        helper.addAttachment(filename, new ByteArrayResource(body), mime);
        mailSender.send(mm);
        return "Sent to " + to.length + " recipient(s)";
    }

    private String[] splitAddresses(String csv) {
        if (csv == null || csv.isBlank()) return new String[0];
        return Arrays.stream(csv.split("[,;\\s]+"))
                     .map(String::trim)
                     .filter(s -> !s.isBlank())
                     .toArray(String[]::new);
    }

    private ReportRun recordRun(ReportSchedule s, String status, long duration, long bytes, String message) {
        ReportRun r = new ReportRun();
        r.setScheduleId(s.getId());
        r.setOrganisationId(s.getOrganisationId());
        r.setStatus(status);
        r.setDurationMs(duration);
        r.setBytes(bytes);
        r.setRecipients(s.getRecipients());
        r.setMessage(message);
        return runRepo.save(r);
    }

    /**
     * Compute the next run time based on cadence. UTC times. Anchor day-of-week
     * for WEEKLY, day-of-month for MONTHLY, day-of-month within Jan/Apr/Jul/Oct
     * for QUARTERLY.
     */
    private void advanceNextRun(ReportSchedule s) {
        LocalDateTime now = LocalDateTime.now();
        LocalTime tod = parseTod(s.getTimeOfDay());
        String cadence = s.getCadence() == null
            ? null : s.getCadence().trim().toUpperCase(java.util.Locale.ROOT);
        LocalDateTime next = switch (cadence == null ? "" : cadence) {
            case "DAILY"      -> nextDaily(now, tod);
            case "WEEKLY"     -> nextWeekly(now, tod, s.getCadenceAnchor());
            case "MONTHLY"    -> nextMonthly(now, tod, s.getCadenceAnchor());
            case "QUARTERLY"  -> nextQuarterly(now, tod, s.getCadenceAnchor());
            // F2-293 — SEMIANNUAL was stored by the seed and named by no `case`, so this fell to
            // the `default` below and the semi-annual ISO 22301 / NCEMA BCM readiness report was
            // rescheduled every day: 13 consecutive FAILED runs in dev and prod, 2026-08-11..23.
            case "SEMIANNUAL" -> nextSemiannual(now, tod, s.getCadenceAnchor());
            default -> {
                // ⚠ The fallback stays a day, because throwing here would break the run loop —
                // `advanceNextRun` is called from the catch path too. What changes is that it now
                // SAYS SO: an unrecognised cadence used to become a silent daily retry, which is
                // indistinguishable from a schedule somebody chose (F2-196 — an unrecognised member
                // should be chased, not excused). The write boundary refuses these now, so this arm
                // is reachable only by a row that predates that guard.
                log.warn("Schedule {} ('{}') has cadence '{}', which is not one of {} — "
                         + "rescheduling one day out. This row was written before the cadence guard "
                         + "and its real cadence cannot be computed.",
                         s.getId(), s.getName(), s.getCadence(), SUPPORTED_CADENCES);
                yield now.plusDays(1).withHour(tod.getHour()).withMinute(tod.getMinute())
                         .withSecond(0).withNano(0);
            }
        };
        s.setNextRunAt(next);
    }

    private LocalDateTime nextDaily(LocalDateTime now, LocalTime tod) {
        LocalDateTime today = now.toLocalDate().atTime(tod);
        return today.isAfter(now) ? today : today.plusDays(1);
    }

    private LocalDateTime nextWeekly(LocalDateTime now, LocalTime tod, String anchor) {
        DayOfWeek target = parseDayOfWeek(anchor, DayOfWeek.MONDAY);
        LocalDateTime cand = now.toLocalDate().atTime(tod);
        while (cand.getDayOfWeek() != target || !cand.isAfter(now)) cand = cand.plusDays(1);
        return cand;
    }

    private LocalDateTime nextMonthly(LocalDateTime now, LocalTime tod, String anchor) {
        int day = parseInt(anchor, 1);
        if (day < 1) day = 1; if (day > 28) day = 28;
        LocalDate baseDay = now.toLocalDate().withDayOfMonth(day);
        LocalDateTime cand = baseDay.atTime(tod);
        if (!cand.isAfter(now)) cand = baseDay.plusMonths(1).atTime(tod);
        return cand;
    }

    private LocalDateTime nextQuarterly(LocalDateTime now, LocalTime tod, String anchor) {
        int day = parseInt(anchor, 1);
        if (day < 1) day = 1; if (day > 28) day = 28;
        // Quarter starts: Jan, Apr, Jul, Oct
        int curMonth = now.getMonthValue();
        int qStart = ((curMonth - 1) / 3) * 3 + 1;          // 1, 4, 7, or 10
        LocalDate cand = LocalDate.of(now.getYear(), qStart, Math.min(day, 28));
        LocalDateTime t = cand.atTime(tod);
        if (!t.isAfter(now)) t = cand.plusMonths(3).atTime(tod);
        return t;
    }

    /**
     * F2-293 — twice a year, on the anchor day of the half-year (January / July), mirroring
     * {@link #nextQuarterly}. The anchor is a day-of-month, clamped to 28 for the same reason.
     */
    private LocalDateTime nextSemiannual(LocalDateTime now, LocalTime tod, String anchor) {
        int day = parseInt(anchor, 1);
        if (day < 1) day = 1; if (day > 28) day = 28;
        int hStart = now.getMonthValue() <= 6 ? 1 : 7;   // half starts: Jan, Jul
        LocalDate cand = LocalDate.of(now.getYear(), hStart, Math.min(day, 28));
        LocalDateTime t = cand.atTime(tod);
        if (!t.isAfter(now)) t = cand.plusMonths(6).atTime(tod);
        return t;
    }

    private DayOfWeek parseDayOfWeek(String name, DayOfWeek fallback) {
        if (name == null) return fallback;
        try { return DayOfWeek.valueOf(name.toUpperCase()); }
        catch (Exception e) { return fallback; }
    }

    private int parseInt(String s, int fallback) {
        if (s == null) return fallback;
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return fallback; }
    }

    private LocalTime parseTod(String hhmm) {
        try { return LocalTime.parse(hhmm == null ? "06:00" : hhmm, DateTimeFormatter.ofPattern("HH:mm")); }
        catch (Exception e) { return LocalTime.of(6, 0); }
    }
}
