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

    /**
     * F2-177 — the same expression was written out three times, and two of the ten names it recited
     * ({@code AUDIT_DIRECTOR}, {@code AUDIT_COMMITTEE_CHAIR}) matched no row in {@code iam.roles}. Both
     * are gone; the audit committee reaches this view through its <b>seat</b> instead, which is the
     * question the two names were trying to ask.
     *
     * <p>⚠ <b>{@code AUDIT_DIRECTOR} needed its intent recovered before it was touched</b> — F2-52's rule,
     * and it changes the answer. It is not a committee office at all; it is the head of internal audit,
     * whom this platform models as {@code INTERNAL_AUDITOR}, <em>already named in this guard</em>. So
     * deleting it removes a duplicate rather than an authority, and in dev that person also holds a
     * {@code Standing Invitee} seat on the audit committee, so the seat leg covers them a second way.
     *
     * <p>Extracted to a constant because three copies of a guard is three chances to fix two of them —
     * the shape F2-156 found on the attachment surface, one file over.
     */
    private static final String READ_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','COMPLIANCE_OFFICER','INTERNAL_AUDITOR',"
          + "'BOARD_SECRETARY','DPO','AUDITOR','EXECUTIVE_REVIEWER')"
          + " or hasPermission('AUDIT_COMMITTEE','COMMITTEE','SEAT')";

    private final ThreeLinesService service;

    /** Per-band counts (risks/findings/CAPA/policies/DSR + overdue/escalations). */
    @GetMapping("/overview")
    @PreAuthorize(READ_GUARD)
    public ThreeLinesOverviewDto overview() {
        return service.overview();
    }

    /** Drill-down: list of artefacts assigned to the band. */
    @GetMapping("/line/{band}")
    @PreAuthorize(READ_GUARD)
    public List<ThreeLinesArtefactDto> drillDown(@PathVariable("band") String band) {
        return service.drillDown(band);
    }

    /** Effective LoD band(s) for a user — drives the frontend "my band" highlighter. */
    @GetMapping("/user/{userId}/role-band")
    @PreAuthorize(READ_GUARD)
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
