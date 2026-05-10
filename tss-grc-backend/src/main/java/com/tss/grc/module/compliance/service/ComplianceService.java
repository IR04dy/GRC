package com.tss.grc.module.compliance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.dto.*;
import com.tss.grc.module.compliance.entity.*;
import com.tss.grc.module.compliance.repository.*;
import com.tss.grc.platform.realtime.BroadcastHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ComplianceService {

    private final ControlRepository controlRepo;
    private final ComplianceAssessmentRepository assessmentRepo;
    private final AuditFindingRepository findingRepo;

    @Autowired(required = false)
    private BroadcastHelper bcast;

    // ── Controls ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ControlResponse> listControls(String framework, String status, String q, Pageable pageable) {
        Long orgId = TenantContext.getCurrentTenant();
        String fwNull    = (framework != null && !framework.isBlank()) ? framework : null;
        String statusNull = (status   != null && !status.isBlank())   ? status    : null;
        String qNull      = (q        != null && !q.isBlank())        ? q         : null;
        return controlRepo.findWithFilters(orgId, fwNull, statusNull, qNull, pageable)
                          .map(this::toControlResponse);
    }

    @Transactional(readOnly = true)
    public ControlResponse getControl(Long id) {
        return toControlResponse(requireControl(id));
    }

    @Transactional
    public ControlResponse createControl(ControlRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        Control c = new Control();
        c.setOrganisationId(orgId);
        applyControl(c, req);
        ControlResponse out = toControlResponse(controlRepo.save(c));
        if (bcast != null) bcast.entity(orgId, "compliance_control", out.id(), out);
        return out;
    }

    @Transactional
    public ControlResponse updateControl(Long id, ControlRequest req) {
        Control c = requireControl(id);
        applyControl(c, req);
        ControlResponse out = toControlResponse(controlRepo.save(c));
        if (bcast != null) bcast.entity(c.getOrganisationId(), "compliance_control", id, out);
        return out;
    }

    @Transactional
    public void deleteControl(Long id) {
        Control c = requireControl(id);
        c.setIsDeleted(true);
        controlRepo.save(c);
        if (bcast != null) bcast.entity(c.getOrganisationId(), "compliance_control", id, java.util.Map.of("id", id, "deleted", true));
    }

    private void applyControl(Control c, ControlRequest req) {
        c.setCode(req.code());
        c.setNameEn(req.nameEn());
        c.setNameAr(req.nameAr());
        c.setFramework(req.framework());
        c.setDomain(req.domain());
        c.setMaturityLevel(req.maturityLevel() != null ? req.maturityLevel() : 1);
        c.setStatus(req.status() != null ? req.status() : "PLANNED");
        c.setOwner(req.owner());
        c.setDescription(req.description());
    }

    private Control requireControl(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        return controlRepo.findById(id)
            .filter(c -> c.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("Control not found: " + id));
    }

    private ControlResponse toControlResponse(Control c) {
        return new ControlResponse(
            c.getId(), c.getCode(), c.getNameEn(), c.getNameAr(),
            c.getFramework(), c.getDomain(), c.getMaturityLevel(),
            c.getStatus(), c.getOwner(), c.getDescription(),
            c.getCreatedAt(), c.getUpdatedAt()
        );
    }

    // ── Assessments ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AssessmentResponse> listAssessments(Pageable pageable) {
        Long orgId = TenantContext.getCurrentTenant();
        return assessmentRepo.findByOrganisationId(orgId, pageable).map(this::toAssessmentResponse);
    }

    @Transactional
    public AssessmentResponse createAssessment(AssessmentRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        ComplianceAssessment a = new ComplianceAssessment();
        a.setOrganisationId(orgId);
        applyAssessment(a, req);
        AssessmentResponse out = toAssessmentResponse(assessmentRepo.save(a));
        if (bcast != null) bcast.entity(orgId, "compliance_assessment", a.getId(), out);
        return out;
    }

    @Transactional
    public AssessmentResponse updateAssessment(Long id, AssessmentRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        ComplianceAssessment a = assessmentRepo.findById(id)
            .filter(x -> x.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("Assessment not found: " + id));
        applyAssessment(a, req);
        AssessmentResponse out = toAssessmentResponse(assessmentRepo.save(a));
        if (bcast != null) bcast.entity(orgId, "compliance_assessment", id, out);
        return out;
    }

    private void applyAssessment(ComplianceAssessment a, AssessmentRequest req) {
        a.setFramework(req.framework());
        a.setPeriod(req.period());
        a.setAuditor(req.auditor());
        a.setScore(req.score());
        a.setStatus(req.status() != null ? req.status() : "PLANNED");
        a.setAssessmentDate(req.assessmentDate());
        a.setNotes(req.notes());
    }

    private AssessmentResponse toAssessmentResponse(ComplianceAssessment a) {
        return new AssessmentResponse(
            a.getId(), a.getFramework(), a.getPeriod(), a.getAuditor(),
            a.getScore(), a.getStatus(), a.getAssessmentDate(),
            a.getNotes(), a.getCreatedAt()
        );
    }

    // ── Findings ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<FindingResponse> listFindings(String severity, String status, Pageable pageable) {
        Long orgId = TenantContext.getCurrentTenant();
        String sevNull    = (severity != null && !severity.isBlank()) ? severity : null;
        String statusNull = (status   != null && !status.isBlank())   ? status   : null;
        return findingRepo.findWithFilters(orgId, sevNull, statusNull, pageable)
                          .map(this::toFindingResponse);
    }

    @Transactional
    public FindingResponse createFinding(FindingRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        AuditFinding f = new AuditFinding();
        f.setOrganisationId(orgId);
        applyFinding(f, req);
        FindingResponse out = toFindingResponse(findingRepo.save(f));
        if (bcast != null) bcast.entity(orgId, "audit_finding", f.getId(), out);
        return out;
    }

    @Transactional
    public FindingResponse updateFinding(Long id, FindingRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        AuditFinding f = findingRepo.findById(id)
            .filter(x -> x.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("Finding not found: " + id));
        applyFinding(f, req);
        FindingResponse out = toFindingResponse(findingRepo.save(f));
        if (bcast != null) bcast.entity(orgId, "audit_finding", id, out);
        return out;
    }

    @Transactional
    public void deleteFinding(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        AuditFinding f = findingRepo.findById(id)
            .filter(x -> x.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("Finding not found: " + id));
        f.setIsDeleted(true);
        findingRepo.save(f);
        if (bcast != null) bcast.entity(orgId, "audit_finding", id, java.util.Map.of("id", id, "deleted", true));
    }

    private void applyFinding(AuditFinding f, FindingRequest req) {
        f.setTitle(req.title());
        f.setAssessmentId(req.assessmentId());
        f.setSeverity(req.severity());
        f.setOwner(req.owner());
        f.setCapa(req.capa());
        f.setDueDate(req.dueDate());
        f.setStatus(req.status() != null ? req.status() : "OPEN");
        if (req.frameworkId() != null) f.setFrameworkId(req.frameworkId());
        if (req.clauseRef()   != null) f.setClauseRef(req.clauseRef());
    }

    private FindingResponse toFindingResponse(AuditFinding f) {
        return new FindingResponse(
            f.getId(), f.getTitle(), f.getAssessmentId(),
            f.getSeverity(), f.getOwner(), f.getCapa(),
            f.getDueDate(), f.getStatus(), f.getCreatedAt(),
            f.getCapaStage(),
            f.getOwnerTestAt(), f.getOwnerTestResult(), f.getOwnerTestNotes(),
            f.getIndependentTestAt(), f.getIndependentTestResult(), f.getIndependentTestNotes(),
            f.getAuditSignoffAt(), f.getAuditSignoffNotes(),
            f.getFrameworkId(), f.getClauseRef()
        );
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ComplianceStats getStats() {
        Long orgId = TenantContext.getCurrentTenant();
        long total       = controlRepo.countByOrganisationId(orgId);
        long implemented = controlRepo.countByOrganisationIdAndStatus(orgId, "IMPLEMENTED");
        long partial     = controlRepo.countByOrganisationIdAndStatus(orgId, "PARTIAL");
        long planned     = controlRepo.countByOrganisationIdAndStatus(orgId, "PLANNED");
        long openFindings     = findingRepo.countByOrganisationIdAndStatus(orgId, "OPEN");
        long criticalFindings = findingRepo.countByOrganisationIdAndStatus(orgId, "CRITICAL");

        Double avgScore = assessmentRepo.avgScoreByOrg(orgId);
        double rate = avgScore != null ? avgScore : (total > 0 ? (implemented * 100.0 / total) : 0.0);

        List<Object[]> fwScores = assessmentRepo.avgScoreByFramework(orgId);
        List<Map<String, Object>> frameworkScores = new ArrayList<>();
        for (Object[] row : fwScores) {
            frameworkScores.add(Map.of(
                "framework", row[0].toString(),
                "score", row[1] != null ? ((Number) row[1]).doubleValue() : 0.0
            ));
        }

        return new ComplianceStats(total, implemented, partial, planned, rate,
                                   openFindings, criticalFindings, frameworkScores);
    }
}
