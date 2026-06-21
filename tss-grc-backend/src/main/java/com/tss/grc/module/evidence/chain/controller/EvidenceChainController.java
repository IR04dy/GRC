package com.tss.grc.module.evidence.chain.controller;

import com.tss.grc.module.evidence.chain.dto.ChainDtos.*;
import com.tss.grc.module.evidence.chain.service.EvidenceChainService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/evidence/chain")
@RequiredArgsConstructor
@Tag(name = "Evidence Chain-of-Custody")
@SecurityRequirement(name = "bearerAuth")
public class EvidenceChainController {

    private final EvidenceChainService service;

    @GetMapping
    @PreAuthorize("hasPermission('EVIDENCE', 'READ')")
    public List<ItemSummary> list(@RequestParam(required = false) String status) {
        return service.listItems(status);
    }

    @GetMapping("/{evidenceId}")
    @PreAuthorize("hasPermission('EVIDENCE', 'READ')")
    public ChainReport report(@PathVariable Long evidenceId) {
        return service.readReport(evidenceId);
    }

    // SEC-EVD-003: chain verification mutates the WORM integrity ledger
    // (chain_status / chain_verified_by). It must require the module write
    // permission, not bare authentication, so a principal with no evidence
    // rights cannot stamp itself as verifier on tamper-evidence records.
    @PostMapping("/{evidenceId}/verify")
    @PreAuthorize("hasPermission('EVIDENCE', 'WRITE')")
    public ChainReport verify(@PathVariable Long evidenceId, Authentication auth) {
        return service.verifyChain(evidenceId, userIdOf(auth));
    }

    private static Long userIdOf(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUserId();
        return null;
    }
}
