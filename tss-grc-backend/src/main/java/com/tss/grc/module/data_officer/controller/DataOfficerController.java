package com.tss.grc.module.data_officer.controller;

import com.tss.grc.module.data_officer.dto.DataOfficerDtos.*;
import com.tss.grc.module.data_officer.service.DataGovernanceService;
import com.tss.grc.module.data_officer.service.DataOfficerQueryService;
import com.tss.grc.module.data_officer.service.DataSubjectRightsService;
import com.tss.grc.module.data_officer.service.PrivacyAssuranceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Sprint 33 — Saudi Data Officer Toolkit REST surface. Mounts everything
 *  under {@code /api/v1/data-officer/...} so the 10 sub-modules share one
 *  namespace + one Swagger tag — matches how a real DPO opens the platform
 *  (one URL, branches into many surfaces).
 *
 *  <p>QUAL-ESG-001: the former 1069-LOC {@code DataOfficerService} god-class
 *  was split into cohesive per-domain services (subject-rights, assurance,
 *  governance) plus a read-only query aggregator; this controller fans each
 *  endpoint out to the relevant service. Routes, payloads and authorization
 *  are unchanged. */
@RestController
@RequestMapping("/api/v1/data-officer")
@RequiredArgsConstructor
@Tag(name = "Data Officer Toolkit", description = "DSR · Consent · DPIA · Breach · Notice · Retention · NDR · Catalog · Quality · Ethics")
public class DataOfficerController {

    private final DataSubjectRightsService subjectRights;
    private final PrivacyAssuranceService assurance;
    private final DataGovernanceService governance;
    private final DataOfficerQueryService query;

    // ─── DSR ────────────────────────────────────────────────────────────
    @PostMapping("/dsr")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<DsrResponse> createDsr(@Valid @RequestBody CreateDsrRequest req) {
        return ResponseEntity.ok(subjectRights.createDsr(req));
    }
    @PostMapping("/dsr/{id}/transition")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<DsrResponse> transitionDsr(@PathVariable Long id, @Valid @RequestBody TransitionDsrRequest req) {
        return ResponseEntity.ok(subjectRights.transitionDsr(id, req));
    }
    @GetMapping("/dsr")
    @PreAuthorize("hasPermission('PRIVACY','READ') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<DsrResponse>> listDsr() { return ResponseEntity.ok(subjectRights.listDsr()); }

    @GetMapping("/dsr/{id}")
    @PreAuthorize("hasPermission('PRIVACY','READ') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<DsrResponse> getDsr(@PathVariable Long id) { return ResponseEntity.ok(subjectRights.getDsr(id)); }

    // ─── Consent ────────────────────────────────────────────────────────
    @PostMapping("/consents")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ConsentResponse> recordConsent(@Valid @RequestBody CreateConsentRequest req) {
        return ResponseEntity.ok(subjectRights.recordConsent(req));
    }
    @PostMapping("/consents/{id}/withdraw")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ConsentResponse> withdrawConsent(@PathVariable Long id, @Valid @RequestBody WithdrawConsentRequest req) {
        return ResponseEntity.ok(subjectRights.withdrawConsent(id, req));
    }
    @GetMapping("/consents")
    @PreAuthorize("hasPermission('PRIVACY','READ') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<ConsentResponse>> listConsents(@RequestParam(required = false) String subject) {
        return ResponseEntity.ok(subject == null || subject.isBlank()
            ? subjectRights.listConsents() : subjectRights.listConsentsForSubject(subject));
    }

    // ─── DPIA ───────────────────────────────────────────────────────────
    @PostMapping("/dpia")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<DpiaResponse> createDpia(@Valid @RequestBody CreateDpiaRequest req) {
        return ResponseEntity.ok(assurance.createDpia(req));
    }
    @PostMapping("/dpia/{id}/transition")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<DpiaResponse> transitionDpia(@PathVariable Long id, @Valid @RequestBody TransitionDpiaRequest req) {
        return ResponseEntity.ok(assurance.transitionDpia(id, req));
    }
    @GetMapping("/dpia")
    @PreAuthorize("hasPermission('PRIVACY','READ') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<DpiaResponse>> listDpia() { return ResponseEntity.ok(assurance.listDpia()); }

    // ─── Breach ─────────────────────────────────────────────────────────
    @PostMapping("/breaches")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<BreachResponse> createBreach(@Valid @RequestBody CreateBreachRequest req) {
        return ResponseEntity.ok(assurance.createBreach(req));
    }
    @PostMapping("/breaches/{id}/transition")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<BreachResponse> transitionBreach(@PathVariable Long id, @Valid @RequestBody TransitionBreachRequest req) {
        return ResponseEntity.ok(assurance.transitionBreach(id, req));
    }
    @GetMapping("/breaches")
    @PreAuthorize("hasPermission('PRIVACY','READ') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<BreachResponse>> listBreaches() { return ResponseEntity.ok(assurance.listBreaches()); }

    // ─── Notice ─────────────────────────────────────────────────────────
    @PostMapping("/notices")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<NoticeResponse> createNotice(@Valid @RequestBody CreateNoticeRequest req) {
        return ResponseEntity.ok(subjectRights.createNotice(req));
    }
    @PostMapping("/notices/{id}/transition")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<NoticeResponse> transitionNotice(@PathVariable Long id, @Valid @RequestBody TransitionNoticeRequest req) {
        return ResponseEntity.ok(subjectRights.transitionNotice(id, req));
    }
    @GetMapping("/notices")
    @PreAuthorize("hasPermission('PRIVACY','READ') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<NoticeResponse>> listNotices() { return ResponseEntity.ok(subjectRights.listNotices()); }

    // ─── Retention + Disposal ───────────────────────────────────────────
    @PostMapping("/retention-rules")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<RetentionRuleResponse> createRule(@Valid @RequestBody CreateRetentionRuleRequest req) {
        return ResponseEntity.ok(governance.createRetentionRule(req));
    }
    @GetMapping("/retention-rules")
    @PreAuthorize("hasPermission('PRIVACY','READ') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<RetentionRuleResponse>> listRules() { return ResponseEntity.ok(governance.listRetentionRules()); }

    @PostMapping("/disposals")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<DisposalJobResponse> scheduleDisposal(@Valid @RequestBody CreateDisposalJobRequest req) {
        return ResponseEntity.ok(governance.scheduleDisposal(req));
    }
    @PostMapping("/disposals/{id}/transition")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<DisposalJobResponse> transitionDisposal(@PathVariable Long id, @Valid @RequestBody TransitionDisposalRequest req) {
        return ResponseEntity.ok(governance.transitionDisposal(id, req));
    }
    @GetMapping("/disposals")
    @PreAuthorize("hasPermission('PRIVACY','READ') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<DisposalJobResponse>> listDisposals() { return ResponseEntity.ok(governance.listDisposals()); }

    // ─── NDR ────────────────────────────────────────────────────────────
    @PostMapping("/ndr")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<NdrResponse> createNdr(@Valid @RequestBody CreateNdrRequest req) {
        return ResponseEntity.ok(governance.createNdr(req));
    }
    @PostMapping("/ndr/{id}/transition")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<NdrResponse> transitionNdr(@PathVariable Long id, @Valid @RequestBody TransitionNdrRequest req) {
        return ResponseEntity.ok(governance.transitionNdr(id, req));
    }
    @GetMapping("/ndr")
    @PreAuthorize("hasPermission('PRIVACY','READ') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<NdrResponse>> listNdr() { return ResponseEntity.ok(governance.listNdr()); }

    // ─── Catalog + Lineage ──────────────────────────────────────────────
    @PostMapping("/catalog")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<CatalogEntryResponse> createCatalogEntry(@Valid @RequestBody CreateCatalogEntryRequest req) {
        return ResponseEntity.ok(governance.createCatalogEntry(req));
    }
    @GetMapping("/catalog")
    @PreAuthorize("hasPermission('PRIVACY','READ') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<CatalogEntryResponse>> listCatalog() { return ResponseEntity.ok(governance.listCatalog()); }

    @PostMapping("/lineage")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<LineageResponse> createLineage(@Valid @RequestBody CreateLineageRequest req) {
        return ResponseEntity.ok(governance.createLineage(req));
    }
    @GetMapping("/lineage")
    @PreAuthorize("hasPermission('PRIVACY','READ') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<LineageResponse>> listLineage(@RequestParam Long sourceId) {
        return ResponseEntity.ok(governance.listLineage(sourceId));
    }

    // ─── Quality ────────────────────────────────────────────────────────
    @PostMapping("/quality/rules")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<QualityRuleResponse> createQualityRule(@Valid @RequestBody CreateQualityRuleRequest req) {
        return ResponseEntity.ok(governance.createQualityRule(req));
    }
    @GetMapping("/quality/rules")
    @PreAuthorize("hasPermission('PRIVACY','READ') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<QualityRuleResponse>> listQualityRules() { return ResponseEntity.ok(governance.listQualityRules()); }

    @PostMapping("/quality/runs")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<QualityRunResponse> recordQualityRun(@Valid @RequestBody RecordQualityRunRequest req) {
        return ResponseEntity.ok(governance.recordQualityRun(req));
    }
    @GetMapping("/quality/runs")
    @PreAuthorize("hasPermission('PRIVACY','READ') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<QualityRunResponse>> listQualityRuns(@RequestParam(required = false) Long ruleId) {
        return ResponseEntity.ok(governance.listQualityRuns(ruleId));
    }

    // ─── Ethics ─────────────────────────────────────────────────────────
    @PostMapping("/ethics")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<EthicsResponse> createEthics(@Valid @RequestBody CreateEthicsRequest req) {
        return ResponseEntity.ok(assurance.createEthics(req));
    }
    @PostMapping("/ethics/{id}/transition")
    @PreAuthorize("hasPermission('PRIVACY','WRITE') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<EthicsResponse> transitionEthics(@PathVariable Long id, @Valid @RequestBody TransitionEthicsRequest req) {
        return ResponseEntity.ok(assurance.transitionEthics(id, req));
    }
    @GetMapping("/ethics")
    @PreAuthorize("hasPermission('PRIVACY','READ') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<EthicsResponse>> listEthics() { return ResponseEntity.ok(assurance.listEthics()); }

    // ─── Timeline + Dashboard ───────────────────────────────────────────
    @GetMapping("/timeline")
    @PreAuthorize("hasPermission('PRIVACY','READ') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<EventResponse>> timeline(@RequestParam String entityType, @RequestParam Long entityId) {
        return ResponseEntity.ok(query.timeline(entityType, entityId));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasPermission('PRIVACY','READ') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<DashboardResponse> dashboard() { return ResponseEntity.ok(query.dashboard()); }
}
