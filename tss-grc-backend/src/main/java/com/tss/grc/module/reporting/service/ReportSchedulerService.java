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
    public static final List<String> SUPPORTED_CADENCES =
        List.of("DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "SEMIANNUAL");

    @Scheduled(fixedDelayString = "${grc.reporting.scheduler-period-ms:60000}")
    @Transactional
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        List<ReportSchedule> due = scheduleRepo.findDue(now);
        if (due.isEmpty()) return;
        log.info("Reporting scheduler tick — {} due schedules", due.size());
        for (ReportSchedule s : due) {
            try { runOnce(s, "CRON"); }
            catch (Exception e) {
                log.warn("Scheduled report '{}' failed: {}", s.getName(), e.getMessage());
                recordRun(s, "FAILED", 0, 0, e.getMessage());
                advanceNextRun(s);
            }
        }
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
            byte[] body = renderBody(s);
            String filename = filenameFor(s);
            String mime = mimeFor(s.getFormat());
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

    private byte[] renderBody(ReportSchedule s) {
        String type = s.getReportType();
        String fmt  = s.getFormat();
        String lang = s.getLanguage() == null ? "en" : s.getLanguage();
        return switch (type) {
            case "RISK_REGISTER"  -> "PDF".equalsIgnoreCase(fmt)
                ? exportService.riskRegisterPdf(lang)
                : exportService.riskRegisterXlsx();
            case "FINDINGS_AGING" -> "PDF".equalsIgnoreCase(fmt)
                ? exportService.findingsAgingPdf(lang)
                : exportService.findingsXlsx();
            case "KRI_DASHBOARD"  -> "PDF".equalsIgnoreCase(fmt)
                ? exportService.kriDashboardPdf(lang)
                : exportService.kriDashboardXlsx();
            case "BOARD_PACK"     -> "PPTX".equalsIgnoreCase(fmt)
                ? boardPackPptxService.generatePptx(lang)
                : boardPackService.generatePdf(lang);
            case "AI_MODEL_REGISTRY" -> aiRegistryExportService.export(fmt);
            default -> throw new IllegalArgumentException("Unknown report type: " + type);
        };
    }

    private String filenameFor(ReportSchedule s) {
        String date = LocalDate.now().toString();
        String ext = switch (s.getFormat() == null ? "PDF" : s.getFormat().toUpperCase()) {
            case "PPTX" -> "pptx";
            case "XLSX" -> "xlsx";
            default     -> "pdf";
        };
        return s.getReportType().toLowerCase().replace('_', '-') + "-" + date + "." + ext;
    }
    private String mimeFor(String format) {
        return switch (format == null ? "PDF" : format.toUpperCase()) {
            case "PPTX" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "XLSX" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default     -> "application/pdf";
        };
    }

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
