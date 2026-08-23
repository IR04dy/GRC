package com.tss.grc.module.bcm.service;

import com.tss.grc.common.validation.ServerOwnedState;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.entity.CrisisCommsAudience;
import com.tss.grc.module.bcm.entity.CrisisCommsDistribution;
import com.tss.grc.module.bcm.entity.CrisisCommsTemplate;
import com.tss.grc.module.bcm.notify.NotificationChannel;
import com.tss.grc.module.bcm.repository.CrisisCommsAudienceRepository;
import com.tss.grc.module.bcm.repository.CrisisCommsDistributionRepository;
import com.tss.grc.module.bcm.repository.CrisisCommsTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * BCM Crisis Communication module — assessment §5.5 #5.
 * Pre-approved templates + audience groups + distribution audit log. The
 * actual outbound channel (email server, SMS gateway, Tawakkalna push) is a
 * follow-up; this commit ships SIMULATED sends so the workflow surface and
 * audit trail can be exercised end-to-end.
 */
@Service
public class CrisisCommsService {

    /**
     * F2-294 — {@code CrisisCommsTemplate.version} used to carry this as a field
     * initialiser, which made {@code updateTemplate}'s null guard fire on every
     * entity-bound request. The column is NOT NULL and {@link #createTemplate} saves the
     * bound entity, so the default has to be written on the create path.
     */
    static final String DEFAULT_TEMPLATE_VERSION = "1.0";

    private final CrisisCommsTemplateRepository     templateRepo;
    private final CrisisCommsAudienceRepository     audienceRepo;
    private final CrisisCommsDistributionRepository distributionRepo;

    /**
     * Registered notification channels, keyed by {@link NotificationChannel#channelType()}.
     * Wired by Spring from any {@link NotificationChannel} bean on the classpath.
     * Empty (legacy SIMULATED behaviour) when no channel implementation is enabled
     * — e.g. mail starter present but {@code bcm.notify.email.enabled=false} keeps
     * {@link com.tss.grc.module.bcm.notify.EmailNotificationChannel} dormant.
     */
    private final Map<String, NotificationChannel> channelsByType;

    @Autowired
    public CrisisCommsService(CrisisCommsTemplateRepository templateRepo,
                              CrisisCommsAudienceRepository audienceRepo,
                              CrisisCommsDistributionRepository distributionRepo,
                              List<NotificationChannel> channels) {
        this.templateRepo = templateRepo;
        this.audienceRepo = audienceRepo;
        this.distributionRepo = distributionRepo;
        this.channelsByType = channels.stream()
            .collect(Collectors.toMap(NotificationChannel::channelType, c -> c, (a, b) -> a));
    }

    // ── Templates ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CrisisCommsTemplate> listTemplates() {
        return templateRepo.findByOrganisationIdAndIsActiveTrueOrderByAudienceRoleAscCodeAsc(TenantContext.getCurrentTenant());
    }

    @Transactional
    public CrisisCommsTemplate createTemplate(CrisisCommsTemplate t) {
        // Mass-assignment defense — controller accepts the entity via
        // @RequestBody. Null id forces save() to INSERT instead of
        // merge() into a potentially cross-tenant row.
        t.setId(null);
        t.setOrganisationId(TenantContext.getCurrentTenant());
        // F2-294 — `version` is NOT NULL and this create saves the BOUND entity, so the
        // default the entity used to carry has to live here or an omitted version reaches
        // the INSERT as null.
        if (t.getVersion() == null) t.setVersion(DEFAULT_TEMPLATE_VERSION);
        return templateRepo.save(t);
    }

    @Transactional
    /**
     * F2-214 — absent means "leave alone"; {@code crisisCommsApi.updateTemplate} sends a
     * {@code Partial<CrisisCommsTemplateDto>}. Writing all eleven unconditionally meant an edit
     * that omitted any of them erased it: the code, both language names, the audience, the
     * channel, both subjects, both bodies, the severity band and the version — every one
     * populated on 5/5 live rows in dev and prod. A crisis communication template with a null
     * body is a template that produces nothing at the moment it is needed.
     */
    public CrisisCommsTemplate updateTemplate(Long id, CrisisCommsTemplate req) {
        CrisisCommsTemplate t = mustGetTemplate(id);
        // F2-308 — settable at birth, fixed thereafter. Nothing mints this code (one
        // write in the whole service, the caller's), so a rename was expressible.
        ServerOwnedState.requireImmutableReference(
            req.getCode(), t.getCode(), "code",
            "Crisis comms template " + (t.getId() == null ? "(new)" : t.getId()),
            "Omit code, or send the value already recorded.");
        if (req.getCode() != null && t.getCode() == null) {
            t.setCode(req.getCode());
        }
        if (req.getNameEn() != null)       t.setNameEn(req.getNameEn());
        if (req.getNameAr() != null)       t.setNameAr(req.getNameAr());
        if (req.getAudienceRole() != null) t.setAudienceRole(req.getAudienceRole());
        if (req.getChannel() != null)      t.setChannel(req.getChannel());
        if (req.getSubjectEn() != null)    t.setSubjectEn(req.getSubjectEn());
        if (req.getSubjectAr() != null)    t.setSubjectAr(req.getSubjectAr());
        if (req.getBodyEn() != null)       t.setBodyEn(req.getBodyEn());
        if (req.getBodyAr() != null)       t.setBodyAr(req.getBodyAr());
        if (req.getSeverityBand() != null) t.setSeverityBand(req.getSeverityBand());
        if (req.getVersion() != null)      t.setVersion(req.getVersion());
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
        // Mass-assignment defense — see createTemplate().
        a.setId(null);
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
        // F2-214 — absent means "leave alone"; the client sends a Partial<CrisisCommsAudienceDto>.
        // `recipientCount` is DERIVED from contactsCsv, so it has to move with it: recomputing
        // from an absent list is not a null but a **0**, and a zero recipient count reads as
        // "this audience has nobody in it" rather than as missing data. All four fields are
        // populated on 4/4 live rows in dev and prod.
        if (req.getName() != null)         a.setName(req.getName());
        if (req.getNameAr() != null)       a.setNameAr(req.getNameAr());
        if (req.getAudienceRole() != null) a.setAudienceRole(req.getAudienceRole());
        if (req.getContactsCsv() != null) {
            a.setContactsCsv(req.getContactsCsv());
            a.setRecipientCount(countRecipients(req.getContactsCsv()));
        }
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

        // Dispatch via a registered NotificationChannel if one matches the
        // template's channel column. Otherwise fall back to SIMULATED so
        // workflows still record a row (audit trail integrity).
        NotificationChannel channel = channelsByType.get(t.getChannel());
        if (channel == null || !channel.isHealthy()) {
            d.setDeliveryStatus("SIMULATED");
            d.setDeliverySummary("No live " + t.getChannel() + " channel configured — recorded "
                + a.getRecipientCount() + " recipients in simulation. Configure "
                + "bcm.notify.<channel>.enabled to wire a real channel.");
            return distributionRepo.save(d);
        }

        // Recipients: parse the audience CSV. Spaces and stray empties are tolerated.
        List<String> recipients = (a.getContactsCsv() == null ? "" : a.getContactsCsv())
            .lines()
            .flatMap(line -> Arrays.stream(line.split(",")))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();

        String correlationId = "crisis-comms-" + d.getId() + "-" + System.currentTimeMillis();
        List<NotificationChannel.DeliveryResult> results =
            channel.send(recipients, subject, body, correlationId);
        long ok = results.stream().filter(NotificationChannel.DeliveryResult::delivered).count();
        long fail = results.size() - ok;

        d.setDeliveryStatus(fail == 0 ? "DELIVERED" : (ok == 0 ? "FAILED" : "PARTIAL"));
        d.setDeliverySummary(String.format("%s channel: %d delivered, %d failed (of %d).%s",
            t.getChannel(), ok, fail, results.size(),
            fail > 0 ? " First error: " + results.stream()
                .filter(r -> !r.delivered()).findFirst().map(NotificationChannel.DeliveryResult::error).orElse("?")
              : ""));
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
