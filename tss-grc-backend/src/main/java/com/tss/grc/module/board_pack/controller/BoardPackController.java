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

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<BoardPackDto> list() { return service.listForOrg(); }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
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
