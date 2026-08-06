package com.tss.grc.module.governance.threelines.controller;

import com.tss.grc.module.governance.threelines.dto.RoleMappingUpdateRequest;
import com.tss.grc.module.governance.threelines.dto.ThreeLinesArtefactDto;
import com.tss.grc.module.governance.threelines.dto.ThreeLinesOverviewDto;
import com.tss.grc.module.governance.threelines.dto.UserBandDto;
import com.tss.grc.module.governance.threelines.entity.ThreeLinesRoleMapping;
import com.tss.grc.module.governance.threelines.service.ThreeLinesService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Three Lines of Defense (3LoD) — Sprint 11 endpoints.
 *
 * <pre>
 *   GET  /api/v1/governance/three-lines/overview
 *   GET  /api/v1/governance/three-lines/line/{band}
 *   GET  /api/v1/governance/three-lines/user/{userId}/role-band
 *   POST /api/v1/governance/three-lines/role-mapping   (admin only)
 * </pre>
 *
 * <p>Auth: every endpoint requires a role that is permitted to see the
 * platform's governance picture. The mutation endpoint is admin-only.
 * Note we use {@code hasAnyRole(...)} (not {@code hasAnyAuthority}) per the
 * project convention — {@code CustomUserDetailsService} adds the
 * {@code ROLE_} prefix to persisted role names.
 */
@RestController
@RequestMapping("/api/v1/governance/three-lines")
@RequiredArgsConstructor
@Tag(name = "Three Lines of Defense")
public class ThreeLinesController {

    private final ThreeLinesService service;

    /** Per-band counts (risks/findings/CAPA/policies/DSR + overdue/escalations). */
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','COMPLIANCE_OFFICER','INTERNAL_AUDITOR',"
                + "'AUDIT_DIRECTOR','BOARD_MEMBER','AUDIT_COMMITTEE_CHAIR','BOARD_SECRETARY',"
                + "'DPO','AUDITOR','EXECUTIVE_REVIEWER')")
    public ThreeLinesOverviewDto overview() {
        return service.overview();
    }

    /** Drill-down: list of artefacts assigned to the band. */
    @GetMapping("/line/{band}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','COMPLIANCE_OFFICER','INTERNAL_AUDITOR',"
                + "'AUDIT_DIRECTOR','BOARD_MEMBER','AUDIT_COMMITTEE_CHAIR','BOARD_SECRETARY',"
                + "'DPO','AUDITOR','EXECUTIVE_REVIEWER')")
    public List<ThreeLinesArtefactDto> drillDown(@PathVariable("band") String band) {
        return service.drillDown(band);
    }

    /** Effective LoD band(s) for a user — drives the frontend "my band" highlighter. */
    @GetMapping("/user/{userId}/role-band")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','COMPLIANCE_OFFICER','INTERNAL_AUDITOR',"
                + "'AUDIT_DIRECTOR','BOARD_MEMBER','AUDIT_COMMITTEE_CHAIR','BOARD_SECRETARY',"
                + "'DPO','AUDITOR','EXECUTIVE_REVIEWER')")
    public UserBandDto userBand(@PathVariable("userId") Long userId) {
        return service.userBandFor(userId);
    }

    /** Admin mutation — re-map a role to a different band. Writes F7 audit
     *  row + emits F8 {@code LOD_ROLE_MAPPING_CHANGED} domain event. */
    @PostMapping("/role-mapping")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','GRC_ADMINISTRATOR')")
    public ResponseEntity<ThreeLinesRoleMapping> upsertMapping(
            @Valid @RequestBody RoleMappingUpdateRequest req) {
        return ResponseEntity.ok(service.mapRoleToBand(req));
    }
}
