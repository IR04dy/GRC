package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.IrPlaybook;
import com.tss.grc.module.socops.entity.IrPlaybookStep;
import com.tss.grc.module.socops.repository.IrPlaybookRepository;
import com.tss.grc.module.socops.repository.IrPlaybookStepRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for IR playbook headers and their ordered steps.
 *
 * <p>The 6 template playbooks live under {@code organisationId = 0} (seeded
 * in V150). The {@link #cloneFromTemplate(String)} method copies a template
 * into the current tenant on first use — keeps the seed data immutable and
 * lets each tenant edit their own copy without polluting the shared
 * baseline.
 */
@Service
@RequiredArgsConstructor
public class IrPlaybookService {

    private static final Long TEMPLATE_ORG = 0L;

    private final IrPlaybookRepository      playbooks;
    private final IrPlaybookStepRepository  steps;
    private final AuditLogService           audit;
    private final ApplicationEventPublisher publisher;

    @Transactional(readOnly = true)
    public List<IrPlaybook> list() {
        return playbooks.findByOrganisationIdOrderByCategoryAscPlaybookCodeAsc(
            TenantContext.getCurrentTenant());
    }

    @Transactional(readOnly = true)
    public List<IrPlaybook> templates() {
        return playbooks.findByOrganisationIdOrderByCategoryAscPlaybookCodeAsc(TEMPLATE_ORG);
    }

    @Transactional(readOnly = true)
    public IrPlaybook get(Long id) {
        return playbooks.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("IrPlaybook " + id));
    }

    @Transactional(readOnly = true)
    public List<IrPlaybookStep> stepsOf(Long playbookId) {
        return steps.findByOrganisationIdAndPlaybookIdOrderByStepOrderAsc(
            TenantContext.getCurrentTenant(), playbookId);
    }

    /** Copy a template (org=0) into the current tenant. Idempotent — if the
     *  tenant already has a playbook with the same code we return the
     *  existing one rather than creating a duplicate. */
    @Transactional
    public IrPlaybook cloneFromTemplate(String templateCode) {
        Long org = TenantContext.getCurrentTenant();
        IrPlaybook tpl = playbooks.findByOrganisationIdAndPlaybookCode(TEMPLATE_ORG, templateCode)
            .orElseThrow(() -> new EntityNotFoundException("Template " + templateCode));
        return playbooks.findByOrganisationIdAndPlaybookCode(org, templateCode).orElseGet(() -> {
            IrPlaybook copy = new IrPlaybook();
            copy.setOrganisationId(org);
            copy.setPlaybookCode(tpl.getPlaybookCode());
            copy.setTitleEn(tpl.getTitleEn());
            copy.setTitleAr(tpl.getTitleAr());
            copy.setDescriptionEn(tpl.getDescriptionEn());
            copy.setDescriptionAr(tpl.getDescriptionAr());
            copy.setCategory(tpl.getCategory());
            copy.setSeverityTrigger(tpl.getSeverityTrigger());
            copy.setTargetMttdMinutes(tpl.getTargetMttdMinutes());
            copy.setTargetMttrMinutes(tpl.getTargetMttrMinutes());
            copy.setAlignedFrameworks(tpl.getAlignedFrameworks());
            copy.setStatus("ACTIVE");
            copy.setVersion(1);
            IrPlaybook saved = playbooks.save(copy);

            // Copy steps
            for (IrPlaybookStep tplStep : steps.findByPlaybookIdOrderByStepOrderAsc(tpl.getId())) {
                IrPlaybookStep s = new IrPlaybookStep();
                s.setOrganisationId(org);
                s.setPlaybookId(saved.getId());
                s.setStepOrder(tplStep.getStepOrder());
                s.setPhase(tplStep.getPhase());
                s.setTitleEn(tplStep.getTitleEn());
                s.setTitleAr(tplStep.getTitleAr());
                s.setInstructionsEn(tplStep.getInstructionsEn());
                s.setInstructionsAr(tplStep.getInstructionsAr());
                s.setResponsibleRole(tplStep.getResponsibleRole());
                s.setEstimatedMinutes(tplStep.getEstimatedMinutes());
                s.setIsCritical(tplStep.getIsCritical());
                steps.save(s);
            }
            audit.log("IR_PLAYBOOK_CLONE", "IrPlaybook", saved.getId(),
                "Cloned " + templateCode + " from template into tenant");
            return saved;
        });
    }

    @Transactional
    public IrPlaybook recordInvocation(Long id) {
        IrPlaybook p = get(id);
        p.setInvocationCount((p.getInvocationCount() == null ? 0 : p.getInvocationCount()) + 1);
        p.setLastReviewedAt(LocalDateTime.now());
        IrPlaybook saved = playbooks.save(p);
        audit.log("IR_PLAYBOOK_INVOKE", "IrPlaybook", id, "Invocation count incremented");
        // F8 — AR-119 picks this up to log the invocation and start the MTTD/MTTR clock.
        publisher.publishEvent(DomainEvent.of(
            saved.getOrganisationId(), "IR_PLAYBOOK_INVOKED",
            "IrPlaybook", id,
            Map.of("playbookCode",     saved.getPlaybookCode()    == null ? "" : saved.getPlaybookCode(),
                   "category",         saved.getCategory()        == null ? "" : saved.getCategory(),
                   "severityTrigger",  saved.getSeverityTrigger() == null ? "" : saved.getSeverityTrigger(),
                   "invocationCount",  saved.getInvocationCount() == null ? 0  : saved.getInvocationCount())));
        return saved;
    }

    @Transactional
    public IrPlaybook save(IrPlaybook in) {
        in.setOrganisationId(TenantContext.getCurrentTenant());
        boolean isNew = in.getId() == null;
        IrPlaybook saved = playbooks.save(in);
        audit.log(isNew ? "IR_PLAYBOOK_CREATE" : "IR_PLAYBOOK_UPDATE",
            "IrPlaybook", saved.getId(), saved.getPlaybookCode());
        return saved;
    }
}
