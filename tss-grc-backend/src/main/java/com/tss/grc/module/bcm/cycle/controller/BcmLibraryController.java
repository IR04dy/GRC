package com.tss.grc.module.bcm.cycle.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.*;
import com.tss.grc.module.bcm.cycle.repository.*;
import com.tss.grc.module.bcm.cycle.service.BcpPlanService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only BCM library catalog + "clone template" action — audit Tier-3 #15
 * & #16 + Tier-4 #28.
 *
 * <p>Three catalogs, all global (system-defined, no per-tenant rows):
 * <ul>
 *   <li>{@code GET /api/v1/bcm/library/plan-templates}      — Tier-3 #15</li>
 *   <li>{@code GET /api/v1/bcm/library/exercise-scenarios}  — Tier-3 #16</li>
 *   <li>{@code GET /api/v1/bcm/library/peak-seasons}        — Tier-4 #28</li>
 * </ul>
 *
 * <p>One write action — {@code POST /plan-templates/{code}/clone} — creates a
 * new draft plan in the current tenant by copying the template's sections
 * into actual {@link BcpSection} rows. Other tenant artefacts (exercises,
 * BIA peak-period tagging) are wired by the consuming page.
 */
@RestController
@RequestMapping("/api/v1/bcm/library")
@RequiredArgsConstructor
@Tag(name = "BCM Library (templates · scenarios · peak seasons)")
public class BcmLibraryController {

    private static final ObjectMapper M = new ObjectMapper();

    private final BcpPlanTemplateRepository      templates;
    private final BcmExerciseScenarioRepository  scenarios;
    private final BcmPeakSeasonRepository        peaks;
    private final BcpPlanRepository              plans;
    private final BcpSectionRepository           sections;
    private final BcpPlanService                 planService;
    private final AuditLogService                audit;

    public record CloneTemplateBody(String titleEn, String titleAr, Long sourceBiaId) {}

    // ─── Plan templates (Tier-3 #15) ───────────────────────────────

    @GetMapping("/plan-templates")
    @PreAuthorize("isAuthenticated()")
    public List<BcpPlanTemplate> listPlanTemplates(
            @RequestParam(required = false) String planType,
            @RequestParam(required = false) String category) {
        if (planType != null && !planType.isBlank())
            return templates.findByPlanTypeOrderByTemplateNameEnAsc(planType.toUpperCase(Locale.ROOT));
        if (category != null && !category.isBlank())
            return templates.findByScenarioCategoryOrderByTemplateNameEnAsc(category.toUpperCase(Locale.ROOT));
        return templates.findAll();
    }

    @GetMapping("/plan-templates/{code}")
    @PreAuthorize("isAuthenticated()")
    public BcpPlanTemplate getPlanTemplate(@PathVariable String code) {
        return templates.findByTemplateCode(code)
            .orElseThrow(() -> new EntityNotFoundException("Plan template not found: " + code));
    }

    /**
     * Tier-3 #15 — clone a template into a fresh DRAFT plan for the current
     * tenant. Each entry in the template's sectionsJson becomes a BcpSection
     * row. Caller can optionally pass a {@code sourceBiaId} to wire the plan
     * back to a BIA (so attestation cadence resolves to critical-tier).
     */
    @PostMapping("/plan-templates/{code}/clone")
    @PreAuthorize("hasPermission('BCM','WRITE')")
    public BcpPlan clonePlanTemplate(@PathVariable String code,
                                     @RequestBody(required = false) CloneTemplateBody body) {
        BcpPlanTemplate t = templates.findByTemplateCode(code)
            .orElseThrow(() -> new EntityNotFoundException("Plan template not found: " + code));

        BcpPlan draft = new BcpPlan();
        draft.setPlanType(t.getPlanType());
        draft.setTitleEn(body != null && body.titleEn() != null && !body.titleEn().isBlank()
            ? body.titleEn() : t.getTemplateNameEn());
        draft.setTitleAr(body != null && body.titleAr() != null && !body.titleAr().isBlank()
            ? body.titleAr() : t.getTemplateNameAr());
        draft.setRtoHours(t.getDefaultRtoHours());
        draft.setRpoHours(t.getDefaultRpoHours());
        draft.setMtpdHours(t.getDefaultMtpdHours());
        if (body != null && body.sourceBiaId() != null) draft.setSourceBiaId(body.sourceBiaId());

        BcpPlan saved = planService.create(draft);

        // Walk the JSON sections payload and persist a BcpSection per entry,
        // pre-filled with the EN + AR body from the template. The BcpPlanService
        // already created the default section catalog on save; here we OVERWRITE
        // the bodies so the user sees template content instead of placeholders.
        try {
            JsonNode root = M.readTree(t.getSectionsJson());
            List<BcpSection> existing = sections
                .findByOrganisationIdAndPlanIdOrderBySectionOrderAsc(saved.getOrganisationId(), saved.getId());
            Map<String, BcpSection> byKey = new java.util.HashMap<>();
            for (BcpSection s : existing) byKey.put(s.getSectionKey(), s);

            Iterator<Map.Entry<String, JsonNode>> it = root.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                BcpSection target = byKey.get(entry.getKey());
                if (target == null) continue; // skip unknown section keys
                JsonNode v = entry.getValue();
                target.setBodyEn(v.path("en").asText(null));
                target.setBodyAr(v.path("ar").asText(null));
                target.setLastEditedBy("template:" + t.getTemplateCode());
                target.setLastEditedAt(LocalDateTime.now());
                sections.save(target);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Template " + code + " has malformed sections JSON", e);
        }

        audit.log("CLONE_TEMPLATE", "BcpPlan", saved.getId(),
            "Plan cloned from template " + t.getTemplateCode());

        return saved;
    }

    // ─── Exercise scenarios (Tier-3 #16) ───────────────────────────

    @GetMapping("/exercise-scenarios")
    @PreAuthorize("isAuthenticated()")
    public List<BcmExerciseScenario> listExerciseScenarios(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String type) {
        if (category != null && !category.isBlank())
            return scenarios.findByScenarioCategoryOrderByTitleEnAsc(category.toUpperCase(Locale.ROOT));
        if (type != null && !type.isBlank())
            return scenarios.findByExerciseTypeOrderByTitleEnAsc(type.toUpperCase(Locale.ROOT));
        return scenarios.findAllByOrderByTitleEnAsc();
    }

    @GetMapping("/exercise-scenarios/{code}")
    @PreAuthorize("isAuthenticated()")
    public BcmExerciseScenario getExerciseScenario(@PathVariable String code) {
        return scenarios.findByScenarioCode(code)
            .orElseThrow(() -> new EntityNotFoundException("Exercise scenario not found: " + code));
    }

    // ─── Peak seasons (Tier-4 #28) ────────────────────────────────

    @GetMapping("/peak-seasons")
    @PreAuthorize("isAuthenticated()")
    public List<BcmPeakSeason> listPeakSeasons(@RequestParam(required = false) String type) {
        if (type != null && !type.isBlank())
            return peaks.findBySeasonTypeOrderByGregorianStartDateAsc(type.toUpperCase(Locale.ROOT));
        return peaks.findAllByOrderByGregorianStartDateAsc();
    }

    /**
     * Tier-4 #28 — upcoming peaks lens. Returns any season currently active
     * plus any starting in the next {@code daysAhead} days (default 120).
     */
    @GetMapping("/peak-seasons/upcoming")
    @PreAuthorize("isAuthenticated()")
    public List<BcmPeakSeason> upcomingPeakSeasons(@RequestParam(defaultValue = "120") int daysAhead) {
        // Suppress unused-warning on tenant — we don't filter, this is a global catalog,
        // but reading TenantContext here lets the audit interceptor wrap the call.
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) { /* no-op; calls without tenant context still see the global catalog */ }
        LocalDate today = LocalDate.now();
        return peaks.findUpcoming(today, today.plusDays(Math.max(0, daysAhead)));
    }
}
