package com.tss.grc.module.bcm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.entity.CrisisCommsAudience;
import com.tss.grc.module.bcm.entity.CrisisCommsDistribution;
import com.tss.grc.module.bcm.entity.CrisisCommsTemplate;
import com.tss.grc.module.bcm.repository.CrisisCommsAudienceRepository;
import com.tss.grc.module.bcm.repository.CrisisCommsDistributionRepository;
import com.tss.grc.module.bcm.repository.CrisisCommsTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * BCM Crisis Communication module — assessment §5.5 #5.
 * Pre-approved templates + audience groups + distribution audit log. The
 * actual outbound channel (email server, SMS gateway, Tawakkalna push) is a
 * follow-up; this commit ships SIMULATED sends so the workflow surface and
 * audit trail can be exercised end-to-end.
 */
@Service
@RequiredArgsConstructor
public class CrisisCommsService {

    private final CrisisCommsTemplateRepository     templateRepo;
    private final CrisisCommsAudienceRepository     audienceRepo;
    private final CrisisCommsDistributionRepository distributionRepo;

    // ── Templates ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CrisisCommsTemplate> listTemplates() {
        return templateRepo.findByOrganisationIdAndIsActiveTrueOrderByAudienceRoleAscCodeAsc(TenantContext.getCurrentTenant());
    }

    @Transactional
    public CrisisCommsTemplate createTemplate(CrisisCommsTemplate t) {
        t.setOrganisationId(TenantContext.getCurrentTenant());
        return templateRepo.save(t);
    }

    @Transactional
    public CrisisCommsTemplate updateTemplate(Long id, CrisisCommsTemplate req) {
        CrisisCommsTemplate t = mustGetTemplate(id);
        t.setCode(req.getCode());
        t.setNameEn(req.getNameEn());
        t.setNameAr(req.getNameAr());
        t.setAudienceRole(req.getAudienceRole());
        t.setChannel(req.getChannel());
        t.setSubjectEn(req.getSubjectEn());
        t.setSubjectAr(req.getSubjectAr());
        t.setBodyEn(req.getBodyEn());
        t.setBodyAr(req.getBodyAr());
        t.setSeverityBand(req.getSeverityBand());
        t.setVersion(req.getVersion());
        t.setUpdatedAt(LocalDateTime.now());
        return templateRepo.save(t);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        CrisisCommsTemplate t = mustGetTemplate(id);
        t.setIsDeleted(true);
        templateRepo.save(t);
    }

    private CrisisCommsTemplate mustGetTemplate(Long id) {
        return templateRepo.findById(id)
            .filter(x -> x.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Template not found: " + id));
    }

    // ── Audiences ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CrisisCommsAudience> listAudiences() {
        return audienceRepo.findByOrganisationIdAndIsActiveTrueOrderByName(TenantContext.getCurrentTenant());
    }

    @Transactional
    public CrisisCommsAudience createAudience(CrisisCommsAudience a) {
        a.setOrganisationId(TenantContext.getCurrentTenant());
        // recalc count from contacts CSV
        a.setRecipientCount(countRecipients(a.getContactsCsv()));
        return audienceRepo.save(a);
    }

    @Transactional
    public CrisisCommsAudience updateAudience(Long id, CrisisCommsAudience req) {
        CrisisCommsAudience a = audienceRepo.findById(id)
            .filter(x -> x.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Audience not found: " + id));
        a.setName(req.getName());
        a.setNameAr(req.getNameAr());
        a.setAudienceRole(req.getAudienceRole());
        a.setContactsCsv(req.getContactsCsv());
        a.setRecipientCount(countRecipients(req.getContactsCsv()));
        return audienceRepo.save(a);
    }

    private int countRecipients(String csv) {
        if (csv == null || csv.isBlank()) return 0;
        return (int) java.util.Arrays.stream(csv.split(","))
            .map(String::trim).filter(s -> !s.isEmpty() && !s.startsWith("{")).count();
    }

    // ── Distributions (the send action) ──────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CrisisCommsDistribution> listDistributions() {
        return distributionRepo.findByOrganisationIdOrderBySentAtDesc(TenantContext.getCurrentTenant());
    }

    /**
     * Send a template to an audience. The MVP simulates the send and writes a
     * distribution row with status SIMULATED. Production integration (email
     * SMTP, STC/Mobily SMS gateway, Tawakkalna Business push) is a follow-up.
     * Optional `placeholders` map is used for {placeholder} substitution in
     * subject + body before snapshotting.
     */
    @Transactional
    public CrisisCommsDistribution send(Long templateId, Long audienceId, Long planId, Long sentById, Map<String, String> placeholders) {
        Long org = TenantContext.getCurrentTenant();
        CrisisCommsTemplate t = mustGetTemplate(templateId);
        CrisisCommsAudience a = audienceRepo.findById(audienceId)
            .filter(x -> x.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Audience not found: " + audienceId));

        String subject = applyPlaceholders(t.getSubjectEn(), placeholders);
        String body    = applyPlaceholders(t.getBodyEn(),    placeholders);

        CrisisCommsDistribution d = new CrisisCommsDistribution();
        d.setOrganisationId(org);
        d.setPlanId(planId);
        d.setTemplateId(templateId);
        d.setAudienceId(audienceId);
        d.setChannel(t.getChannel());
        d.setSubjectSnapshot(subject);
        d.setBodySnapshot(body);
        d.setRecipientCount(a.getRecipientCount());
        d.setSentAt(LocalDateTime.now());
        d.setSentById(sentById);
        d.setDeliveryStatus("SIMULATED");
        d.setDeliverySummary(
            "MVP send: " + a.getRecipientCount() + " recipients via " + t.getChannel()
            + ". Production runtime integrates SMTP / SMS gateway (STC, Mobily) / Tawakkalna Business push as follow-up.");
        return distributionRepo.save(d);
    }

    private String applyPlaceholders(String s, Map<String, String> placeholders) {
        if (s == null || placeholders == null || placeholders.isEmpty()) return s;
        String out = s;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }
}
