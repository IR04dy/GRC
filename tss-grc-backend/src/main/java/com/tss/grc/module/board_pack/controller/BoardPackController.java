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
    private static final String READ_GUARD =
        "hasAnyRole('PLATFORM_ADMIN','CRO','BOARD_MEMBER',"
        + "'AUDIT_COMMITTEE_CHAIR','AUDIT_COMMITTEE_MEMBER','COMPANY_SECRETARY') "
        + "or hasAuthority('REPORTING_READ')";

    @GetMapping
    @PreAuthorize(READ_GUARD)
    public List<BoardPackDto> list() { return service.listForOrg(); }

    @GetMapping("/{id}")
    @PreAuthorize(READ_GUARD)
    public BoardPackDto get(@PathVariable String id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('CRO') or hasAuthority('REPORTING_WRITE')")
    public ResponseEntity<BoardPackDto> create(@RequestBody @Valid BoardPackDto req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('CRO') or hasAuthority('REPORTING_WRITE')")
    public BoardPackDto transition(@PathVariable String id,
                                   @RequestBody @Valid BoardPackDto.TransitionRequest req) {
        return service.transition(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
