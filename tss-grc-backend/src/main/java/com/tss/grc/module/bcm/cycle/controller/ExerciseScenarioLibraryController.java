package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.dto.ScenarioLibraryDtos.InstantiateRequest;
import com.tss.grc.module.bcm.cycle.dto.ScenarioLibraryDtos.InstantiatedExercise;
import com.tss.grc.module.bcm.cycle.dto.ScenarioLibraryDtos.ScenarioDetail;
import com.tss.grc.module.bcm.cycle.dto.ScenarioLibraryDtos.ScenarioSummary;
import com.tss.grc.module.bcm.cycle.service.ExerciseScenarioLibraryService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Slice 10-A — REST surface for the Exercise Scenario Library.
 *
 * <pre>
 *   GET    /api/v1/bcm/scenario-library
 *      ?type=&complexity=&framework=&q=&page=&size=
 *      — paginated, filterable list (cards on the library page).
 *
 *   GET    /api/v1/bcm/scenario-library/{id}
 *      — full detail + ordered injects for the detail page.
 *
 *   POST   /api/v1/bcm/scenario-library/{id}/instantiate
 *      — wizard "Run as exercise" — creates a new BcmExercise row,
 *        copies the scenario''s objectives + success criteria, links
 *        the new row via scenario_id, increments usage_count, audits.
 *
 *   GET    /api/v1/bcm/scenario-library/popular?limit=5
 *      — top-N by usage_count (pinned row).
 *
 *   GET    /api/v1/bcm/scenario-library/recommended?framework=SAMA_CSF
 *      — scenarios whose framework_alignment includes the code.
 * </pre>
 *
 * <p>Role guard uses {@code hasAnyRole} (not {@code hasAnyAuthority}) —
 * {@code CustomUserDetailsService} prefixes authorities with {@code ROLE_}.
 * Same shape as PR #174 + the Slice 9-D StrategyRoiController.
 *
 * <p>Read-side is open wide (BCM_MANAGER, CISO, RISK_MANAGER, AUDITOR,
 * PLATFORM_ADMIN) because the library is reference content. Write-side
 * stays tight to BCM_MANAGER + PLATFORM_ADMIN since instantiation
 * creates an org-scoped exercise row.
 */
@RestController
@RequestMapping("/api/v1/bcm/scenario-library")
@RequiredArgsConstructor
@RequireModule("BCM")
@Tag(name = "BCM — Exercise Scenario Library (Slice 10-A)")
@SecurityRequirement(name = "bearerAuth")
public class ExerciseScenarioLibraryController {

    private final ExerciseScenarioLibraryService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','RISK_MANAGER','AUDITOR','PLATFORM_ADMIN')")
    public Page<ScenarioSummary> list(
            @RequestParam(value = "type", required = false) String scenarioType,
            @RequestParam(value = "complexity", required = false) String complexity,
            @RequestParam(value = "framework", required = false) String framework,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return service.search(scenarioType, complexity, framework, query, page, Math.min(size, 200));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','RISK_MANAGER','AUDITOR','PLATFORM_ADMIN')")
    public ScenarioDetail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    // F2-254: was hasAnyRole('BCM_MANAGER','PLATFORM_ADMIN') and BCM_MANAGER is held by NO user
    // in dev or prod - a role guard never reaches ModulePermissionEvaluator, so it never reaches
    // the PLATFORM_ADMIN bypass either; this passed only because it happened to NAME the
    // administrator. Audience diff measured first: both names hold BCM:WRITE, so nobody is dropped
    // and GRC_ADMINISTRATOR + RISK_MANAGER are admitted. AUDITOR and VIEWER hold READ+EXPORT only.
    // The READ guards here keep their role lists: CISO is named on them and holds only BCM:EXPORT.
    @PostMapping("/{id}/instantiate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('BCM','WRITE')")
    public InstantiatedExercise instantiate(
            @PathVariable Long id,
            @RequestBody @Valid InstantiateRequest req) {
        return service.instantiate(id, req);
    }

    @GetMapping("/popular")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','RISK_MANAGER','AUDITOR','PLATFORM_ADMIN')")
    public List<ScenarioSummary> popular(
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        return service.popular(Math.min(limit, 200));
    }

    @GetMapping("/recommended")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','RISK_MANAGER','AUDITOR','PLATFORM_ADMIN')")
    public List<ScenarioSummary> recommended(
            @RequestParam("framework") String framework) {
        return service.recommended(framework);
    }
}
