package com.tss.grc.module.platform.automation.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.automation.entity.AutomationRule;
import com.tss.grc.module.platform.automation.entity.AutomationRuleRun;
import com.tss.grc.module.platform.automation.repository.AutomationRuleRepository;
import com.tss.grc.module.platform.automation.repository.AutomationRuleRunRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST surface for the Automation Rules engine — F8.
 *
 * <pre>
 *   GET    /api/v1/automation/rules              list all
 *   GET    /api/v1/automation/rules/:id          one rule + recent fires
 *   PATCH  /api/v1/automation/rules/:id          enable / disable / edit
 *   GET    /api/v1/automation/runs               page recent fires
 *   GET    /api/v1/automation/stats              outcome counts
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/automation")
@RequiredArgsConstructor
@Tag(name = "Automation Rules (F8)")
public class AutomationRuleController {

    private final AutomationRuleRepository rules;
    private final AutomationRuleRunRepository runs;

    private Long org() { return TenantContext.getCurrentTenant(); }

    @GetMapping("/rules")
    @PreAuthorize("isAuthenticated()")
    public List<AutomationRule> list() {
        return rules.findByOrganisationIdOrderByPriorityAscRuleCodeAsc(org());
    }

    public record RuleDetail(AutomationRule rule, List<AutomationRuleRun> recentRuns) {}

    @GetMapping("/rules/{id}")
    @PreAuthorize("isAuthenticated()")
    public RuleDetail get(@PathVariable Long id) {
        AutomationRule r = rules.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Rule not found: " + id));
        return new RuleDetail(r,
            runs.findTop50ByOrganisationIdAndRuleIdOrderByFiredAtDesc(org(), id));
    }

    public record PatchBody(Boolean enabled, Integer priority, String nameEn, String nameAr,
                            String descriptionEn, String descriptionAr,
                            String conditionJson, String actionJson) {}

    @PatchMapping("/rules/{id}")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public AutomationRule patch(@PathVariable Long id, @RequestBody PatchBody body) {
        AutomationRule r = rules.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Rule not found: " + id));
        if (body.enabled()        != null) r.setEnabled(body.enabled());
        if (body.priority()       != null) r.setPriority(body.priority());
        if (body.nameEn()         != null) r.setNameEn(body.nameEn());
        if (body.nameAr()         != null) r.setNameAr(body.nameAr());
        if (body.descriptionEn()  != null) r.setDescriptionEn(body.descriptionEn());
        if (body.descriptionAr()  != null) r.setDescriptionAr(body.descriptionAr());
        if (body.conditionJson()  != null) r.setConditionJson(body.conditionJson());
        if (body.actionJson()     != null) r.setActionJson(body.actionJson());
        r.setUpdatedAt(LocalDateTime.now());
        return rules.save(r);
    }

    @GetMapping("/runs")
    @PreAuthorize("isAuthenticated()")
    public Page<AutomationRuleRun> listRuns(@RequestParam(defaultValue = "0")   int page,
                                            @RequestParam(defaultValue = "100") int size) {
        return runs.findByOrganisationIdOrderByFiredAtDesc(org(), PageRequest.of(page, Math.min(size, 200)));
    }

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> stats() {
        Long o = org();
        return Map.of(
            "rules",    (long) rules.findByOrganisationIdOrderByPriorityAscRuleCodeAsc(o).size(),
            "success",  runs.countByOrganisationIdAndOutcome(o, "SUCCESS"),
            "skipped",  runs.countByOrganisationIdAndOutcome(o, "SKIPPED"),
            "failed",   runs.countByOrganisationIdAndOutcome(o, "FAILED")
        );
    }

    @SuppressWarnings("unused")
    @DeleteMapping("/rules/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> softDelete(@PathVariable Long id) {
        AutomationRule r = rules.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Rule not found: " + id));
        r.setIsDeleted(true);
        rules.save(r);
        return ResponseEntity.noContent().build();
    }
}
