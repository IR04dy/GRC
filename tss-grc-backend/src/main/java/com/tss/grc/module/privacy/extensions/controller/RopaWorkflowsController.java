package com.tss.grc.module.privacy.extensions.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.CrossBorderResponse;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.DisposalResponse;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.NoticeResponse;
import com.tss.grc.module.privacy.extensions.repository.CrossBorderTransferRepository;
import com.tss.grc.module.privacy.extensions.repository.DisposalCertificateRepository;
import com.tss.grc.module.privacy.extensions.repository.PrivacyNoticeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ROPA → PDPL-workflows facade.
 *
 * <p>Single round-trip view used by the ROPA detail panel: for a given
 * ROPA code, return the counts + full payloads of all three workflows
 * (privacy notices, cross-border transfers, disposal certificates) so
 * the DPO can see and act on them without leaving ROPA.
 *
 * <p>This is the "entry point" for the three modules — every meaningful
 * PDPL workflow hangs off a processing activity, so this is where the
 * action buttons live.
 */
@RestController
@RequestMapping("/api/privacy/ropa-workflows")
@RequiredArgsConstructor
@RequireModule("PDPL")
@Tag(name = "PDPL — ROPA workflows")
@SecurityRequirement(name = "bearerAuth")
public class RopaWorkflowsController {

    private final PrivacyNoticeRepository           noticeRepo;
    private final CrossBorderTransferRepository     cbtRepo;
    private final DisposalCertificateRepository     disposalRepo;

    public record Counts(int notices, int crossBorder, int disposal) { }
    public record Bundle(String ropaCode, Counts counts,
                         List<NoticeResponse> notices,
                         List<CrossBorderResponse> crossBorder,
                         List<DisposalResponse> disposal) { }

    @GetMapping("/{ropaCode}")
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    @Operation(summary = "Counts + full lists of all three PDPL workflows for one ROPA code.")
    public Bundle bundleFor(@PathVariable String ropaCode) {
        Long orgId = TenantContext.getCurrentTenant();

        List<NoticeResponse> notices = noticeRepo
                .findByOrgAndLinkedRopa(orgId, ropaCode).stream()
                .map(NoticeResponse::from).toList();
        List<CrossBorderResponse> cbts = cbtRepo
                .findByOrganisationIdAndRopaCodeOrderByCreatedAtDesc(orgId, ropaCode).stream()
                .map(CrossBorderResponse::from).toList();
        List<DisposalResponse> disposals = disposalRepo
                .findByOrganisationIdAndRelatedEntityTypeAndRelatedEntityIdOrderByCreatedAtDesc(orgId, "ropa", ropaCode).stream()
                .map(DisposalResponse::from).toList();

        return new Bundle(ropaCode,
                new Counts(notices.size(), cbts.size(), disposals.size()),
                notices, cbts, disposals);
    }

    @GetMapping("/{ropaCode}/counts")
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    @Operation(summary = "Counts only — lightweight call for chip rendering on ROPA list/detail.")
    public Counts counts(@PathVariable String ropaCode) {
        Long orgId = TenantContext.getCurrentTenant();
        return new Counts(
                noticeRepo.countByOrgAndLinkedRopa(orgId, ropaCode),
                cbtRepo.countByOrganisationIdAndRopaCode(orgId, ropaCode),
                disposalRepo.countByOrganisationIdAndRelatedEntityTypeAndRelatedEntityId(orgId, "ropa", ropaCode));
    }
}
