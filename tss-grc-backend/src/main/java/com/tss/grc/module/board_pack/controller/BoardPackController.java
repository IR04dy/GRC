package com.tss.grc.module.board_pack.controller;

import com.tss.grc.module.board_pack.dto.BoardPackDto;
import com.tss.grc.module.board_pack.service.BoardPackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController("boardPackBoardPackController")
@RequestMapping("/api/board-pack/packs")
@RequiredArgsConstructor
public class BoardPackController {

    private final BoardPackService service;

    // SEC-EVD-004: board packs aggregate the most sensitive material in the
    // platform (risk posture, appetite breaches, audit engagements, incidents,
    // PDPL/CCM/SLA summaries). Reads must be gated to a board/governance
    // audience, not any authenticated principal — mirrors the write-side guards
    // below and the audit-committee dashboard read guard.
    //
    // F2-05 slice 5 / F2-52 — the guard above did not do what this comment says.
    // It read as a narrow board audience (six role names), but FIVE of those six
    // were absent from iam.roles: CRO, BOARD_MEMBER, AUDIT_COMMITTEE_CHAIR,
    // AUDIT_COMMITTEE_MEMBER and COMPANY_SECRETARY. Only PLATFORM_ADMIN existed,
    // and a role guard gets no PLATFORM_ADMIN bypass, so the whole board-audience
    // half of the expression was inert. What actually admitted callers was the
    // trailing `hasAuthority('REPORTING_READ')` — REPORTING:READ, held by 20 roles
    // INCLUDING VIEWER, the generic read-only persona this comment rules out by
    // name. Live-proven before the fix: `viewer` GET → 200 with all 16 packs.
    // The ghost names were not merely dead; they made a wide guard look narrow.
    //
    // ADMIN is the module that already owns governance administration (committees,
    // the board secretariat). Measured populations:
    //   ADMIN:READ  21 roles — DROPS Viewer, and adds RISK_MANAGER + LEGAL_COUNSEL
    //               (a risk manager was 403 on a RISK_COMMITTEE pack — a real gap).
    //   ADMIN:WRITE  3 roles — BOARD_SECRETARY, GRC_ADMINISTRATOR, ORG_ADMIN.
    // Not a strict narrowing, and said plainly: SUPPLIER_MANAGER and CRISIS_MANAGER
    // gain read where they had none, REGULATOR_VIEWER and EXTERNAL_AUDITOR keep it.
    // No catalogued permission's population IS "the board" — whether a dedicated
    // BOARD module should exist, and whether `audience` should filter reads at all
    // (today it labels but never restricts), are product calls recorded in the plan.
    private static final String READ_GUARD = "hasPermission('ADMIN','READ')";

    // The author of a board pack. ADMIN:WRITE adds BOARD_SECRETARY to the previous
    // population — and the data says that is the point: 4 of the 16 existing packs
    // record drafted_by='board.secretary@tss.com.sa', a persona the old guard could
    // not have admitted. The ghost 'CRO' leg was reaching for the same idea.
    private static final String WRITE_GUARD = "hasPermission('ADMIN','WRITE')";

    @GetMapping
    @PreAuthorize(READ_GUARD)
    public List<BoardPackDto> list() { return service.listForOrg(); }

    @GetMapping("/{id}")
    @PreAuthorize(READ_GUARD)
    public BoardPackDto get(@PathVariable String id) { return service.get(id); }

    @PostMapping
    @PreAuthorize(WRITE_GUARD)
    public ResponseEntity<BoardPackDto> create(@RequestBody @Valid BoardPackDto req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize(WRITE_GUARD)
    public BoardPackDto transition(@PathVariable String id,
                                   @RequestBody @Valid BoardPackDto.TransitionRequest req) {
        return service.transition(id, req);
    }

    // Deliberately PLATFORM_ADMIN and nothing else — NOT an F2-52 attrition site.
    // Soft-deleting a board pack destroys a governance record the board itself reads,
    // and there is no ADMIN:DELETE to convert to: the ADMIN module catalogues only
    // APPROVE, EXPORT, READ and WRITE, so ADMIN:DELETE has zero holders (measured).
    // hasRole('PLATFORM_ADMIN') names a role that exists, so this guard works.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
