package com.tss.grc.module.tprm.automation;

import com.tss.grc.module.capa.dto.CapaTaskDto;
import com.tss.grc.module.capa.service.CapaTaskService;
import com.tss.grc.module.privacy.processor.entity.ProcessorAssessment;
import com.tss.grc.module.scm.fourthparty.entity.FourthPartyExposure;
import com.tss.grc.module.scm.resilience.entity.SupplierResilienceTest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Cross-module automation hooks for the five new TPRM modules (gap-analysis
 * enhancement E).
 *
 * <p>Each method here is invoked by the originating service after a
 * meaningful state change:
 *
 * <ul>
 *   <li>{@link #onFourthPartyHighConcentration} — auto-opens a CAPA when a
 *       fourth-party row's derived concentration risk reaches HIGH (≥3
 *       primaries behind the same fourth party).</li>
 *   <li>{@link #onProcessorAssessmentFailed} — auto-opens a CAPA when an
 *       Art. 28 assessment lands at overall outcome FAIL.</li>
 *   <li>{@link #onResilienceTestFailed} — auto-opens a CAPA when a supplier
 *       resilience test is completed with outcome FAIL.</li>
 * </ul>
 *
 * <p>All hooks are best-effort: if the CAPA service fails (e.g. validation),
 * we log and continue rather than rolling back the original transition.
 *
 * <p><b>F2-22b(iv) — and until this fix that sentence was not true.</b> Each hook was plain
 * {@code @Transactional}, i.e. {@code REQUIRED}, so it <em>joined</em> the caller's transaction.
 * When {@code capaService.create} threw, Spring had already marked that shared transaction
 * rollback-only; catching the exception and logging a warning does not clear the flag, so the
 * <em>caller's</em> commit then failed with {@code UnexpectedRollbackException} — surfaced to the
 * client as a 503 "Transaction rolled back". The best-effort hook was silently fatal to the thing
 * it was decorating.
 *
 * <p>Live-proven on the PDPL Art. 28 path: {@code POST /processor-assessments/{id}/score} with all
 * controls PASS returned 200, and the same call with one FAIL returned <b>503 and saved nothing</b>
 * — so a processor assessment could never be recorded as failing, which is the only outcome that
 * matters for compliance. {@code REQUIRES_NEW} gives each hook its own transaction, so a CAPA
 * failure rolls back only the CAPA and the assessment still commits.
 *
 * <p>This is the engine-wiring rule the platform already documents (per-call {@code REQUIRES_NEW};
 * joined-transaction failures poison commits). ⚠ <b>The underlying CAPA break is a separate,
 * wider finding:</b> {@code POST /api/capa/tasks} returns 500 for <em>every</em> payload
 * (reproduced with {@code audit_finding}, {@code processor_assessment} and {@code incident}), while
 * a raw INSERT into {@code capa.tasks} with the same values succeeds — so every auto-CAPA hook in
 * the platform is currently a no-op that only logs. Recorded as F2-48; not fixed here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TprmAutomationService {

    private final CapaTaskService capaService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFourthPartyHighConcentration(FourthPartyExposure ex) {
        if (ex == null || !"HIGH".equals(ex.getConcentrationRisk())) return;
        try {
            CapaTaskDto dto = baseCapa(
                    "fourth_party_exposure",
                    String.valueOf(ex.getId()),
                    "HIGH concentration risk: " + ex.getFourthPartyName(),
                    "تركّز عالي: " + (ex.getFourthPartyName() != null ? ex.getFourthPartyName() : ""),
                    "PREVENTIVE",
                    "HIGH",
                    "Fourth-party " + ex.getFourthPartyName() + " now sits behind 3+ of our primary vendors, "
                        + "creating cascade exposure. Review whether to (a) require flow-down contractual "
                        + "controls, (b) diversify the affected primaries, or (c) accept and track.",
                    LocalDate.now().plusDays(30)
            );
            capaService.create(dto);
            log.info("Auto-created CAPA for fourth-party {} (HIGH concentration)", ex.getFourthPartyName());
        } catch (Exception e) {
            log.warn("Auto-CAPA for 4P {} failed", ex.getFourthPartyName(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onProcessorAssessmentFailed(ProcessorAssessment a) {
        if (a == null || !"FAIL".equals(a.getOverallOutcome())) return;
        try {
            CapaTaskDto dto = baseCapa(
                    "processor_assessment",
                    a.getAssessmentCode() != null ? a.getAssessmentCode() : String.valueOf(a.getId()),
                    "PDPL Art. 28 FAIL: " + a.getProcessorName(),
                    "إخفاق في فحص المعالج: " + (a.getProcessorName() != null ? a.getProcessorName() : ""),
                    "CORRECTIVE",
                    "CRITICAL",
                    "Processor " + a.getProcessorName() + " failed one or more PDPL Art. 28 controls. "
                        + "Either remediate the failing control(s), renegotiate the processor agreement, "
                        + "or migrate the processing to a compliant provider before continuing to use.",
                    LocalDate.now().plusDays(45)
            );
            capaService.create(dto);
            log.info("Auto-created CAPA for FAIL processor assessment {}", a.getAssessmentCode());
        } catch (Exception e) {
            log.warn("Auto-CAPA for processor {} failed", a.getAssessmentCode(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onResilienceTestFailed(SupplierResilienceTest t) {
        if (t == null || !"FAIL".equals(t.getOutcome())) return;
        try {
            CapaTaskDto dto = baseCapa(
                    "supplier_resilience_test",
                    t.getTestCode() != null ? t.getTestCode() : String.valueOf(t.getId()),
                    "Resilience test FAIL: " + t.getTitleEn(),
                    "إخفاق تمرين صمود: " + (t.getTitleAr() != null ? t.getTitleAr() : t.getTitleEn()),
                    "CORRECTIVE",
                    "HIGH",
                    "Resilience test for vendor " + t.getVendorName() + " (" + t.getTitleEn() + ") "
                        + "failed against its success criteria. Capture the root cause from the test's "
                        + "observations and action items, drive each item to closure, then re-test within "
                        + "the next cycle.",
                    LocalDate.now().plusDays(60)
            );
            capaService.create(dto);
            log.info("Auto-created CAPA for failed resilience test {}", t.getTestCode());
        } catch (Exception e) {
            log.warn("Auto-CAPA for resilience test {} failed", t.getTestCode(), e);
        }
    }

    private CapaTaskDto baseCapa(String originType, String originId,
                                  String titleEn, String titleAr,
                                  String capaType, String severity,
                                  String descriptionEn, LocalDate targetDate) {
        CapaTaskDto dto = new CapaTaskDto();
        dto.setOriginType(originType);
        dto.setOriginId(originId);
        dto.setTitleEn(titleEn);
        dto.setTitleAr(titleAr);
        dto.setCapaType(capaType);
        dto.setSeverity(severity);
        dto.setDescriptionEn(descriptionEn);
        dto.setOwner("tprm-auto");
        dto.setTargetDate(targetDate);
        return dto;
    }
}
