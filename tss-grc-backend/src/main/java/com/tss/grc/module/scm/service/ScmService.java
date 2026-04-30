package com.tss.grc.module.scm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.scm.dto.*;
import com.tss.grc.module.scm.entity.*;
import com.tss.grc.module.scm.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class ScmService {

    private final VendorRepository vendorRepo;
    private final VendorOnboardingRepository onboardingRepo;
    private final VendorAssessmentRepository assessmentRepo;
    private final DependencyRepository dependencyRepo;
    private final SupplierIncidentRepository incidentRepo;
    private final ContractRepository contractRepo;
    private final SlaRepository slaRepo;

    private Long orgId() {
        Long id = TenantContext.getCurrentTenant();
        return id != null ? id : 1L;
    }

    // ───────────── Vendors ─────────────
    @Transactional(readOnly = true)
    public Page<VendorDtos.Response> listVendors(Pageable pageable) {
        return vendorRepo.findByOrganisationId(orgId(), pageable).map(this::toVendorResponse);
    }

    @Transactional(readOnly = true)
    public List<VendorDtos.Response> allVendors() {
        return vendorRepo.findByOrganisationId(orgId()).stream().map(this::toVendorResponse).toList();
    }

    @Transactional(readOnly = true)
    public VendorDtos.Response getVendor(Long id) {
        Vendor v = vendorRepo.findById(id).filter(x -> x.getOrganisationId().equals(orgId()))
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found: " + id));
        return toVendorResponse(v);
    }

    public VendorDtos.Response createVendor(VendorDtos.Request req) {
        Vendor v = new Vendor();
        v.setOrganisationId(orgId());
        applyVendor(v, req);
        return toVendorResponse(vendorRepo.save(v));
    }

    public VendorDtos.Response updateVendor(Long id, VendorDtos.Request req) {
        Vendor v = vendorRepo.findById(id).filter(x -> x.getOrganisationId().equals(orgId()))
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found: " + id));
        applyVendor(v, req);
        v.setUpdatedAt(LocalDateTime.now());
        return toVendorResponse(vendorRepo.save(v));
    }

    public void deleteVendor(Long id) {
        Vendor v = vendorRepo.findById(id).filter(x -> x.getOrganisationId().equals(orgId()))
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found: " + id));
        v.setIsDeleted(true);
        v.setUpdatedAt(LocalDateTime.now());
        vendorRepo.save(v);
    }

    private void applyVendor(Vendor v, VendorDtos.Request req) {
        if (req.getNameEn() != null) v.setNameEn(req.getNameEn());
        if (req.getNameAr() != null) v.setNameAr(req.getNameAr());
        if (req.getCategory() != null) v.setCategory(req.getCategory());
        if (req.getCriticality() != null) v.setCriticality(req.getCriticality());
        if (req.getCountry() != null) v.setCountry(req.getCountry());
        if (req.getServiceType() != null) v.setServiceType(req.getServiceType());
        if (req.getRiskLevel() != null) v.setRiskLevel(req.getRiskLevel());
        if (req.getStatus() != null) v.setStatus(req.getStatus());
        if (req.getWebsite() != null) v.setWebsite(req.getWebsite());
        if (req.getContactName() != null) v.setContactName(req.getContactName());
        if (req.getContactEmail() != null) v.setContactEmail(req.getContactEmail());
        if (req.getContactPhone() != null) v.setContactPhone(req.getContactPhone());
        if (req.getRegistrationNumber() != null) v.setRegistrationNumber(req.getRegistrationNumber());
        if (req.getDescription() != null) v.setDescription(req.getDescription());
    }

    private VendorDtos.Response toVendorResponse(Vendor v) {
        VendorDtos.Response r = new VendorDtos.Response();
        BeanUtils.copyProperties(v, r);
        r.setExternalId(v.getExternalId() != null ? v.getExternalId().toString() : null);
        return r;
    }

    // ───────────── Onboarding workflow ─────────────
    @Transactional(readOnly = true)
    public Page<OnboardingDtos.Response> listOnboarding(Pageable pageable) {
        Map<Long, Vendor> vMap = vendorIndex();
        return onboardingRepo.findByOrganisationId(orgId(), pageable)
                .map(o -> toOnboardingResponse(o, vMap));
    }

    public OnboardingDtos.Response startOnboarding(OnboardingDtos.Request req) {
        VendorOnboarding o = new VendorOnboarding();
        o.setOrganisationId(orgId());
        o.setVendorId(req.getVendorId());
        o.setCurrentStage("REGISTRATION");
        o.setStatus("IN_PROGRESS");
        o.setAssignedToId(req.getAssignedToId());
        o.setDueDate(req.getDueDate());
        o.setNotes(req.getNotes());
        o.setCompletedStages("");
        o = onboardingRepo.save(o);
        // Update parent vendor onboarding_status
        vendorRepo.findById(req.getVendorId()).ifPresent(v -> { v.setOnboardingStatus("IN_PROGRESS"); vendorRepo.save(v); });
        return toOnboardingResponse(o, vendorIndex());
    }

    public OnboardingDtos.Response advanceOnboarding(Long id) {
        VendorOnboarding o = onboardingRepo.findById(id).filter(x -> x.getOrganisationId().equals(orgId()))
                .orElseThrow(() -> new EntityNotFoundException("Onboarding not found: " + id));
        String[] stages = {"REGISTRATION","CRITICALITY_CLASSIFICATION","SECURITY_REVIEW","OPERATIONAL_REVIEW","LEGAL_REVIEW","APPROVAL"};
        int idx = -1;
        for (int i=0; i<stages.length; i++) if (stages[i].equals(o.getCurrentStage())) { idx = i; break; }
        if (idx < 0) idx = 0;
        String done = (o.getCompletedStages()==null?"":o.getCompletedStages());
        if (!done.contains(stages[idx])) done = (done.isEmpty()?"":done+",") + stages[idx];
        o.setCompletedStages(done);
        if (idx == stages.length - 1) {
            o.setStatus("COMPLETED");
            vendorRepo.findById(o.getVendorId()).ifPresent(v -> { v.setOnboardingStatus("COMPLETED"); v.setStatus("ACTIVE"); vendorRepo.save(v); });
        } else {
            o.setCurrentStage(stages[idx + 1]);
        }
        o.setUpdatedAt(LocalDateTime.now());
        return toOnboardingResponse(onboardingRepo.save(o), vendorIndex());
    }

    public OnboardingDtos.Response approveOnboarding(Long id) {
        VendorOnboarding o = onboardingRepo.findById(id).filter(x -> x.getOrganisationId().equals(orgId()))
                .orElseThrow(() -> new EntityNotFoundException("Onboarding not found: " + id));
        o.setStatus("COMPLETED");
        o.setCurrentStage("APPROVAL");
        o.setUpdatedAt(LocalDateTime.now());
        vendorRepo.findById(o.getVendorId()).ifPresent(v -> { v.setOnboardingStatus("COMPLETED"); v.setStatus("ACTIVE"); vendorRepo.save(v); });
        return toOnboardingResponse(onboardingRepo.save(o), vendorIndex());
    }

    public OnboardingDtos.Response rejectOnboarding(Long id, String reason) {
        VendorOnboarding o = onboardingRepo.findById(id).filter(x -> x.getOrganisationId().equals(orgId()))
                .orElseThrow(() -> new EntityNotFoundException("Onboarding not found: " + id));
        o.setStatus("REJECTED");
        o.setRejectionReason(reason);
        o.setUpdatedAt(LocalDateTime.now());
        vendorRepo.findById(o.getVendorId()).ifPresent(v -> { v.setOnboardingStatus("REJECTED"); vendorRepo.save(v); });
        return toOnboardingResponse(onboardingRepo.save(o), vendorIndex());
    }

    private OnboardingDtos.Response toOnboardingResponse(VendorOnboarding o, Map<Long, Vendor> vMap) {
        OnboardingDtos.Response r = new OnboardingDtos.Response();
        BeanUtils.copyProperties(o, r);
        Vendor v = vMap.get(o.getVendorId());
        if (v != null) { r.setVendorNameEn(v.getNameEn()); r.setVendorNameAr(v.getNameAr()); }
        return r;
    }

    // ───────────── Assessments ─────────────
    @Transactional(readOnly = true)
    public Page<AssessmentDtos.Response> listAssessments(Pageable pageable) {
        Map<Long, Vendor> vMap = vendorIndex();
        return assessmentRepo.findByOrganisationId(orgId(), pageable).map(a -> toAssessmentResponse(a, vMap));
    }

    public AssessmentDtos.Response createAssessment(AssessmentDtos.Request req) {
        VendorAssessment a = new VendorAssessment();
        a.setOrganisationId(orgId());
        applyAssessment(a, req);
        if (a.getAssessmentDate() == null) a.setAssessmentDate(LocalDate.now());
        return toAssessmentResponse(assessmentRepo.save(a), vendorIndex());
    }

    public AssessmentDtos.Response updateAssessment(Long id, AssessmentDtos.Request req) {
        VendorAssessment a = assessmentRepo.findById(id).filter(x -> x.getOrganisationId().equals(orgId()))
                .orElseThrow(() -> new EntityNotFoundException("Assessment not found: " + id));
        applyAssessment(a, req);
        a.setUpdatedAt(LocalDateTime.now());
        return toAssessmentResponse(assessmentRepo.save(a), vendorIndex());
    }

    public AssessmentDtos.Response escalateAssessment(Long id) {
        VendorAssessment a = assessmentRepo.findById(id).filter(x -> x.getOrganisationId().equals(orgId()))
                .orElseThrow(() -> new EntityNotFoundException("Assessment not found: " + id));
        a.setEscalatedToRisk(true);
        a.setStatus("ESCALATED");
        a.setUpdatedAt(LocalDateTime.now());
        return toAssessmentResponse(assessmentRepo.save(a), vendorIndex());
    }

    private void applyAssessment(VendorAssessment a, AssessmentDtos.Request req) {
        if (req.getVendorId() != null) a.setVendorId(req.getVendorId());
        if (req.getAssessmentType() != null) a.setAssessmentType(req.getAssessmentType());
        if (req.getAssessmentDate() != null) a.setAssessmentDate(req.getAssessmentDate());
        if (req.getNextAssessmentDate() != null) a.setNextAssessmentDate(req.getNextAssessmentDate());
        if (req.getOverallScore() != null) a.setOverallScore(req.getOverallScore());
        if (req.getRiskRating() != null) a.setRiskRating(req.getRiskRating());
        if (req.getStatus() != null) a.setStatus(req.getStatus());
        if (req.getAssessorId() != null) a.setAssessorId(req.getAssessorId());
        if (req.getFindings() != null) a.setFindings(req.getFindings());
        if (req.getRecommendations() != null) a.setRecommendations(req.getRecommendations());
    }

    private AssessmentDtos.Response toAssessmentResponse(VendorAssessment a, Map<Long, Vendor> vMap) {
        AssessmentDtos.Response r = new AssessmentDtos.Response();
        BeanUtils.copyProperties(a, r);
        Vendor v = vMap.get(a.getVendorId());
        if (v != null) { r.setVendorNameEn(v.getNameEn()); r.setVendorNameAr(v.getNameAr()); }
        return r;
    }

    // ───────────── Dependencies ─────────────
    @Transactional(readOnly = true)
    public Page<DependencyDtos.Response> listDependencies(Pageable pageable) {
        Map<Long, Vendor> vMap = vendorIndex();
        return dependencyRepo.findByOrganisationId(orgId(), pageable).map(d -> toDependencyResponse(d, vMap));
    }

    public DependencyDtos.Response createDependency(DependencyDtos.Request req) {
        Dependency d = new Dependency();
        d.setOrganisationId(orgId());
        applyDependency(d, req);
        return toDependencyResponse(dependencyRepo.save(d), vendorIndex());
    }

    public DependencyDtos.Response updateDependency(Long id, DependencyDtos.Request req) {
        Dependency d = dependencyRepo.findById(id).filter(x -> x.getOrganisationId().equals(orgId()))
                .orElseThrow(() -> new EntityNotFoundException("Dependency not found: " + id));
        applyDependency(d, req);
        d.setUpdatedAt(LocalDateTime.now());
        return toDependencyResponse(dependencyRepo.save(d), vendorIndex());
    }

    public void deleteDependency(Long id) {
        Dependency d = dependencyRepo.findById(id).filter(x -> x.getOrganisationId().equals(orgId()))
                .orElseThrow(() -> new EntityNotFoundException("Dependency not found: " + id));
        d.setIsDeleted(true);
        dependencyRepo.save(d);
    }

    private void applyDependency(Dependency d, DependencyDtos.Request req) {
        if (req.getServiceNameEn() != null) d.setServiceNameEn(req.getServiceNameEn());
        if (req.getServiceNameAr() != null) d.setServiceNameAr(req.getServiceNameAr());
        if (req.getAssetName() != null) d.setAssetName(req.getAssetName());
        if (req.getVendorId() != null) d.setVendorId(req.getVendorId());
        if (req.getDependencyType() != null) d.setDependencyType(req.getDependencyType());
        if (req.getDependencyLevel() != null) d.setDependencyLevel(req.getDependencyLevel());
        if (req.getHasAlternative() != null) d.setHasAlternative(req.getHasAlternative());
        if (req.getAlternativeVendorId() != null) d.setAlternativeVendorId(req.getAlternativeVendorId());
        if (req.getStatus() != null) d.setStatus(req.getStatus());
        if (req.getImpactAnalysis() != null) d.setImpactAnalysis(req.getImpactAnalysis());
    }

    private DependencyDtos.Response toDependencyResponse(Dependency d, Map<Long, Vendor> vMap) {
        DependencyDtos.Response r = new DependencyDtos.Response();
        BeanUtils.copyProperties(d, r);
        if (d.getVendorId() != null) {
            Vendor v = vMap.get(d.getVendorId());
            if (v != null) r.setVendorNameEn(v.getNameEn());
        }
        if (d.getAlternativeVendorId() != null) {
            Vendor v = vMap.get(d.getAlternativeVendorId());
            if (v != null) r.setAlternativeVendorNameEn(v.getNameEn());
        }
        return r;
    }

    // ───────────── Incidents ─────────────
    @Transactional(readOnly = true)
    public Page<IncidentDtos.Response> listIncidents(Pageable pageable) {
        Map<Long, Vendor> vMap = vendorIndex();
        return incidentRepo.findByOrganisationId(orgId(), pageable).map(i -> toIncidentResponse(i, vMap));
    }

    public IncidentDtos.Response createIncident(IncidentDtos.Request req) {
        SupplierIncident i = new SupplierIncident();
        i.setOrganisationId(orgId());
        i.setVendorId(req.getVendorId());
        i.setIncidentType(req.getIncidentType());
        i.setImpact(req.getImpact() != null ? req.getImpact() : "MEDIUM");
        i.setDescription(req.getDescription());
        i.setSlaBreached(Boolean.TRUE.equals(req.getSlaBreached()));
        i.setAssignedToId(req.getAssignedToId());
        // Column is NVARCHAR(20). Use last 7 digits of epoch ms — short, unique enough for demo.
        i.setIncidentCode("SCM-INC-" + (System.currentTimeMillis() % 10_000_000));
        i.setReportedAt(LocalDateTime.now());
        return toIncidentResponse(incidentRepo.save(i), vendorIndex());
    }

    public IncidentDtos.Response activateBackup(Long id) {
        SupplierIncident i = incidentRepo.findById(id).filter(x -> x.getOrganisationId().equals(orgId()))
                .orElseThrow(() -> new EntityNotFoundException("Incident not found: " + id));
        i.setBackupPlanActivated(true);
        i.setUpdatedAt(LocalDateTime.now());
        return toIncidentResponse(incidentRepo.save(i), vendorIndex());
    }

    public IncidentDtos.Response createCapa(Long id) {
        SupplierIncident i = incidentRepo.findById(id).filter(x -> x.getOrganisationId().equals(orgId()))
                .orElseThrow(() -> new EntityNotFoundException("Incident not found: " + id));
        i.setCapaCreated(true);
        i.setUpdatedAt(LocalDateTime.now());
        return toIncidentResponse(incidentRepo.save(i), vendorIndex());
    }

    public IncidentDtos.Response escalateIncident(Long id) {
        SupplierIncident i = incidentRepo.findById(id).filter(x -> x.getOrganisationId().equals(orgId()))
                .orElseThrow(() -> new EntityNotFoundException("Incident not found: " + id));
        i.setEscalatedToManagement(true);
        i.setUpdatedAt(LocalDateTime.now());
        return toIncidentResponse(incidentRepo.save(i), vendorIndex());
    }

    public IncidentDtos.Response resolveIncident(Long id, String notes) {
        SupplierIncident i = incidentRepo.findById(id).filter(x -> x.getOrganisationId().equals(orgId()))
                .orElseThrow(() -> new EntityNotFoundException("Incident not found: " + id));
        i.setStatus("RESOLVED");
        i.setResolvedAt(LocalDateTime.now());
        i.setResolutionNotes(notes);
        i.setUpdatedAt(LocalDateTime.now());
        return toIncidentResponse(incidentRepo.save(i), vendorIndex());
    }

    private IncidentDtos.Response toIncidentResponse(SupplierIncident i, Map<Long, Vendor> vMap) {
        IncidentDtos.Response r = new IncidentDtos.Response();
        BeanUtils.copyProperties(i, r);
        Vendor v = vMap.get(i.getVendorId());
        if (v != null) { r.setVendorNameEn(v.getNameEn()); r.setVendorNameAr(v.getNameAr()); }
        return r;
    }

    // ───────────── Contracts ─────────────
    @Transactional(readOnly = true)
    public Page<ContractDtos.Response> listContracts(Pageable pageable) {
        Map<Long, Vendor> vMap = vendorIndex();
        return contractRepo.findByOrganisationId(orgId(), pageable).map(c -> toContractResponse(c, vMap));
    }

    public ContractDtos.Response createContract(ContractDtos.Request req) {
        Contract c = new Contract();
        c.setOrganisationId(orgId());
        applyContract(c, req);
        return toContractResponse(contractRepo.save(c), vendorIndex());
    }

    public ContractDtos.Response updateContract(Long id, ContractDtos.Request req) {
        Contract c = contractRepo.findById(id).filter(x -> x.getOrganisationId().equals(orgId()))
                .orElseThrow(() -> new EntityNotFoundException("Contract not found: " + id));
        applyContract(c, req);
        c.setUpdatedAt(LocalDateTime.now());
        return toContractResponse(contractRepo.save(c), vendorIndex());
    }

    private void applyContract(Contract c, ContractDtos.Request req) {
        if (req.getVendorId() != null) c.setVendorId(req.getVendorId());
        if (req.getContractNumber() != null) c.setContractNumber(req.getContractNumber());
        if (req.getTitleEn() != null) c.setTitleEn(req.getTitleEn());
        if (req.getTitleAr() != null) c.setTitleAr(req.getTitleAr());
        if (req.getContractType() != null) c.setContractType(req.getContractType());
        if (req.getStartDate() != null) c.setStartDate(req.getStartDate());
        if (req.getEndDate() != null) c.setEndDate(req.getEndDate());
        if (req.getAutoRenewal() != null) c.setAutoRenewal(req.getAutoRenewal());
        if (req.getContractValue() != null) c.setContractValue(req.getContractValue());
        if (req.getCurrency() != null) c.setCurrency(req.getCurrency());
        if (req.getStatus() != null) c.setStatus(req.getStatus());
        if (req.getNotes() != null) c.setNotes(req.getNotes());
    }

    private ContractDtos.Response toContractResponse(Contract c, Map<Long, Vendor> vMap) {
        ContractDtos.Response r = new ContractDtos.Response();
        BeanUtils.copyProperties(c, r);
        Vendor v = vMap.get(c.getVendorId());
        if (v != null) r.setVendorNameEn(v.getNameEn());
        return r;
    }

    // ───────────── Dashboard ─────────────
    @Transactional(readOnly = true)
    public ScmDashboardStats stats() {
        Long org = orgId();
        ScmDashboardStats s = new ScmDashboardStats();
        s.setTotalVendors(vendorRepo.countByOrganisationId(org));
        s.setCriticalVendors(vendorRepo.countByOrganisationIdAndCriticality(org, "CRITICAL"));
        s.setActiveVendors(vendorRepo.countByOrganisationIdAndStatus(org, "ACTIVE"));
        s.setPendingOnboarding(onboardingRepo.countByOrganisationIdAndStatus(org, "IN_PROGRESS"));
        s.setTotalDependencies(dependencyRepo.countByOrganisationId(org));
        s.setHighDependencies(dependencyRepo.countByOrganisationIdAndDependencyLevel(org, "HIGH"));
        s.setOpenIncidents(incidentRepo.countByOrganisationIdAndStatus(org, "OPEN"));
        s.setSlaBreaches(incidentRepo.countByOrganisationIdAndSlaBreached(org, true));
        s.setActiveContracts(contractRepo.countByOrganisationIdAndStatus(org, "ACTIVE"));
        LocalDate ninetyDays = LocalDate.now().plusDays(90);
        s.setExpiringContracts((long) contractRepo.findByOrganisationIdAndEndDateBefore(org, ninetyDays).size());
        return s;
    }

    // ───────────── Helpers ─────────────
    private Map<Long, Vendor> vendorIndex() {
        Map<Long, Vendor> map = new HashMap<>();
        for (Vendor v : vendorRepo.findByOrganisationId(orgId())) map.put(v.getId(), v);
        return map;
    }
}
