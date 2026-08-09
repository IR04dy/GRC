package com.tss.grc.module.privacy.intake;

import com.tss.grc.module.intake.spi.IntakePromotionContext;
import com.tss.grc.module.intake.spi.RegisterPromotionPort;
import com.tss.grc.module.privacy.entity.DataSubjectRequest;
import com.tss.grc.module.privacy.service.PrivacyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code DSR_REQUEST} → {@code privacy.data_subject_request} — F2-172.
 *
 * <h3>The Article 10 clock is the reason this adapter is not a two-line mapping</h3>
 * PDPL Article 10 gives the controller 30 days <b>from receipt</b>. {@code PrivacyService.createDsr}
 * does not set {@code due_date} at all — the platform's own convention (see {@code startDsr}) is
 * {@code received_at + 30 days}, applied later, on the first status change. That is fine for a DSR
 * typed straight into the register, where received-at is now; it is wrong for a promoted one,
 * because the request was received when the data subject submitted it and has been sitting in the
 * triage queue ever since.
 *
 * <p>So both {@code receivedAt} and {@code dueDate} are stamped from the intake's
 * {@code acknowledgedAt}. Deriving them from the promotion instead would hand the organisation
 * however many days the triage took, silently, on a statutory deadline — and it would look
 * <em>more</em> compliant on the dashboard, which is the direction of error nobody catches.
 */
@Component
@RequiredArgsConstructor
public class DsrPromotionAdapter implements RegisterPromotionPort {

    /** PDPL Article 10 — 30 days from receipt. */
    private static final int PDPL_FULFILMENT_DAYS = 30;

    private final PrivacyService privacy;

    @Override public String target() { return "DSR"; }

    @Override public String requiredPermission() { return "PRIVACY:WRITE"; }

    @Override
    public String promote(IntakePromotionContext ctx) {
        DataSubjectRequest d = new DataSubjectRequest();
        d.setRequestType(ctx.firstText("requestType").orElse("ACCESS"));
        // An anonymous DSR is a contradiction — PDPL requires the subject to be identified before
        // the request can be fulfilled — but the portal allows the name to be absent, so record
        // what we have rather than inventing a requester.
        d.setRequesterName(ctx.anonymous() ? null : ctx.submitterName());
        d.setRequesterEmail(ctx.anonymous() ? null : ctx.submitterEmail());
        d.setReceivedAt(ctx.acknowledgedAt());
        d.setDueDate(ctx.statutoryDeadline(PDPL_FULFILMENT_DAYS));
        d.setStatus("OPEN");
        return String.valueOf(privacy.createDsr(d).getId());
    }
}
