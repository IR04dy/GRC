package com.tss.grc.module.privacy.intake;

import com.tss.grc.module.intake.spi.IntakePromotionContext;
import com.tss.grc.module.intake.spi.RegisterPromotionPort;
import com.tss.grc.module.privacy.entity.DataBreach;
import com.tss.grc.module.privacy.service.PrivacyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * {@code BREACH_REPORT} / {@code PRIVACY_BREACH} → {@code privacy.data_breach} — F2-172.
 *
 * <p>{@code discovered_at} is {@code NOT NULL} and is what PDPL Article 27's 72-hour notification
 * window counts from, so it is taken from the reporter's {@code discoveredAt} field when the form
 * supplied one and from the submission time only as a fallback — never from the promotion, which
 * would restart a regulatory clock that has been running since somebody noticed.
 */
@Component
@RequiredArgsConstructor
public class BreachPromotionAdapter implements RegisterPromotionPort {

    private final PrivacyService privacy;

    @Override public String target() { return "BREACH"; }

    @Override public String requiredPermission() { return "PRIVACY:WRITE"; }

    @Override
    public String promote(IntakePromotionContext ctx) {
        DataBreach b = new DataBreach();
        b.setTitleEn(ctx.titleEn(255, "summary", "description"));
        b.setDescriptionEn(ctx.description());
        b.setBreachType(ctx.firstText("category", "breachType").orElse("CONFIDENTIALITY"));
        b.setDiscoveredAt(discoveredAt(ctx));
        b.setDataSubjectsAffectedCount(intOrNull(ctx));
        b.setStatus("OPEN");
        return String.valueOf(privacy.createBreach(b).getId());
    }

    /** The reporter's timestamp when parseable, otherwise when we received the report. */
    private LocalDateTime discoveredAt(IntakePromotionContext ctx) {
        String raw = ctx.firstText("discoveredAt", "observedAt").orElse(null);
        if (raw != null) {
            try {
                // The form emits datetime-local ("2026-08-09T14:30"); a date-only value is also
                // accepted rather than rejected, because losing the whole timestamp to a format
                // mismatch would silently reset the 72-hour clock to now.
                return raw.length() == 10 ? LocalDateTime.parse(raw + "T00:00") : LocalDateTime.parse(raw);
            } catch (DateTimeParseException ignored) {
                // fall through — an unparseable reporter timestamp is not worth failing a promotion
            }
        }
        return ctx.acknowledgedAt();
    }

    private Integer intOrNull(IntakePromotionContext ctx) {
        return ctx.firstText("recordsCount", "dataSubjectsAffectedCount")
            .map(s -> s.replaceAll("[^0-9]", ""))
            .filter(s -> !s.isEmpty())
            .map(Integer::parseInt)
            .orElse(null);
    }
}
