package com.tss.grc.module.evidence.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly auto-expiry sweep for evidence freshness.
 *
 * <p>Closes the "no EXPIRED state / no auto-expiry job" gap: an APPROVED
 * evidence item whose {@code validTo} has passed is no longer silently
 * counted as COVERED in the GRC Expert rollup. {@link EvidenceItemService#expireStaleEvidence()}
 * flips it to EXPIRED, emits {@code EVIDENCE_EXPIRED}, and re-opens the
 * originating request so the gap re-surfaces for collection.
 *
 * <p>Scheduling is enabled globally on {@code GrcApplication}.
 */
@Component
@RequiredArgsConstructor
public class EvidenceExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(EvidenceExpiryScheduler.class);

    private final EvidenceItemService evidenceItemService;

    /** Runs at 01:35 every day (server local time). */
    @Scheduled(cron = "0 35 1 * * *")
    public void sweepExpiredEvidence() {
        try {
            int expired = evidenceItemService.expireStaleEvidence();
            if (expired > 0) {
                log.info("[evidence-expiry] auto-expired {} stale APPROVED evidence item(s)", expired);
            }
        } catch (Exception e) {
            log.error("[evidence-expiry] sweep failed: {}", e.getMessage(), e);
        }
    }
}
