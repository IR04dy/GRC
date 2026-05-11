package com.tss.grc.module.tprm.automation;

import com.tss.grc.module.capa.dto.CapaTaskDto;
import com.tss.grc.module.capa.service.CapaTaskService;
import com.tss.grc.module.privacy.processor.entity.ProcessorAssessment;
import com.tss.grc.module.scm.fourthparty.entity.FourthPartyExposure;
import com.tss.grc.module.scm.resilience.entity.SupplierResilienceTest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TprmAutomationService {

    private final CapaTaskService capaService;

    @Transactional
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
            log.warn("Auto-CAPA for 4P {} failed: {}", ex.getFourthPartyName(), e.getMessage());
        }
    }

    @Transactional
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
            log.warn("Auto-CAPA for processor {} failed: {}", a.getAssessmentCode(), e.getMessage());
        }
    }

    @Transactional
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
            log.warn("Auto-CAPA for resilience test {} failed: {}", t.getTestCode(), e.getMessage());
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
