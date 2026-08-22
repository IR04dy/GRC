package com.tss.grc.module.privacy.extensions.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.NoticeArchiveRequest;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.NoticeCreate;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.NoticeUpdate;
import com.tss.grc.module.privacy.extensions.entity.PrivacyNotice;
import com.tss.grc.module.privacy.extensions.repository.PrivacyNoticeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Privacy notice lifecycle (PDPL Art. 12).
 *
 * <pre>
 *   DRAFT     ──publish()──▶ PUBLISHED   (effective_from auto-stamped)
 *   PUBLISHED ──archive()──▶ ARCHIVED    (terminal)
 *   PUBLISHED ──supersede() ▶ creates new version in DRAFT, links via supersedes_id
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class PrivacyNoticeService {

    private final PrivacyNoticeRepository repo;

    @Transactional(readOnly = true)
    public Page<PrivacyNotice> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByCreatedAtDesc(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public PrivacyNotice get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new EntityNotFoundException("PrivacyNotice " + id));
    }

    @Transactional(readOnly = true)
    public List<PrivacyNotice> historyForCode(String code) {
        return repo.findByOrganisationIdAndNoticeCodeOrderByVersionDesc(
                TenantContext.getCurrentTenant(), code);
    }

    @Transactional
    public PrivacyNotice create(NoticeCreate body, Long createdBy) {
        // If a notice with this code already exists, new version = max+1.
        Long orgId = TenantContext.getCurrentTenant();
        int nextVersion = repo.findByOrganisationIdAndNoticeCodeOrderByVersionDesc(orgId, body.noticeCode())
                .stream().findFirst().map(n -> n.getVersion() + 1).orElse(1);

        PrivacyNotice n = new PrivacyNotice();
        n.setOrganisationId(orgId);
        n.setNoticeCode(body.noticeCode());
        n.setVersion(nextVersion);
        n.setTitleEn(body.titleEn());
        n.setTitleAr(body.titleAr());
        n.setPurposeEn(body.purposeEn());
        n.setPurposeAr(body.purposeAr());
        n.setDataCategories(body.dataCategories());
        n.setRecipients(body.recipients());
        n.setRetentionPeriodEn(body.retentionPeriodEn());
        n.setRetentionPeriodAr(body.retentionPeriodAr());
        n.setDpoContact(body.dpoContact());
        n.setLawfulBasis(body.lawfulBasis());
        n.setBodyEn(body.bodyEn());
        n.setBodyAr(body.bodyAr());
        n.setLinkedRopaCodes(body.linkedRopaCodes());
        n.setEffectiveFrom(body.effectiveFrom());
        n.setEffectiveTo(body.effectiveTo());
        n.setCreatedBy(createdBy);
        n.setStatus("DRAFT");
        return repo.save(n);
    }

    @Transactional
    public PrivacyNotice update(Long id, NoticeUpdate body) {
        PrivacyNotice n = get(id);
        if (!"DRAFT".equals(n.getStatus())) {
            throw new IllegalTransitionException(n.getStatus(), "DRAFT(edit)", Set.of("DRAFT"));
        }
        if (body.titleEn() != null) n.setTitleEn(body.titleEn());
        if (body.titleAr() != null) n.setTitleAr(body.titleAr());
        if (body.purposeEn() != null) n.setPurposeEn(body.purposeEn());
        if (body.purposeAr() != null) n.setPurposeAr(body.purposeAr());
        if (body.dataCategories() != null) n.setDataCategories(body.dataCategories());
        if (body.recipients() != null) n.setRecipients(body.recipients());
        if (body.retentionPeriodEn() != null) n.setRetentionPeriodEn(body.retentionPeriodEn());
        if (body.retentionPeriodAr() != null) n.setRetentionPeriodAr(body.retentionPeriodAr());
        if (body.dpoContact() != null) n.setDpoContact(body.dpoContact());
        if (body.lawfulBasis() != null) n.setLawfulBasis(body.lawfulBasis());
        if (body.bodyEn() != null) n.setBodyEn(body.bodyEn());
        if (body.bodyAr() != null) n.setBodyAr(body.bodyAr());
        if (body.linkedRopaCodes() != null) n.setLinkedRopaCodes(body.linkedRopaCodes());
        if (body.effectiveFrom() != null) n.setEffectiveFrom(body.effectiveFrom());
        if (body.effectiveTo() != null) n.setEffectiveTo(body.effectiveTo());
        return repo.save(n);
    }

    @Transactional
    public PrivacyNotice publish(Long id, Long publisherId) {
        PrivacyNotice n = get(id);
        if (!"DRAFT".equals(n.getStatus())) {
            throw new IllegalTransitionException(n.getStatus(), "PUBLISHED", Set.of("DRAFT"));
        }
        n.setStatus("PUBLISHED");
        n.setPublishedAt(LocalDateTime.now());
        n.setPublishedBy(publisherId);
        if (n.getEffectiveFrom() == null) n.setEffectiveFrom(LocalDateTime.now());
        return repo.save(n);
    }

    @Transactional
    public PrivacyNotice archive(Long id, NoticeArchiveRequest body, Long archiverId) {
        PrivacyNotice n = get(id);
        if (!"PUBLISHED".equals(n.getStatus())) {
            throw new IllegalTransitionException(n.getStatus(), "ARCHIVED", Set.of("PUBLISHED"));
        }
        n.setStatus("ARCHIVED");
        n.setArchivedAt(LocalDateTime.now());
        n.setArchivedBy(archiverId);
        n.setArchiveReasonEn(body == null ? null : body.archiveReasonEn());
        n.setArchiveReasonAr(body == null ? null : body.archiveReasonAr());
        return repo.save(n);
    }

    /**
     * Open a new DRAFT version of an existing notice, copying its content
     * forward. Marks {@code supersedes_id} so the audit trail can prove
     * lineage between versions.
     */
    @Transactional
    public PrivacyNotice supersede(Long id, Long createdBy) {
        PrivacyNotice prev = get(id);
        Long orgId = TenantContext.getCurrentTenant();
        int nextVersion = prev.getVersion() + 1;

        PrivacyNotice next = new PrivacyNotice();
        next.setOrganisationId(orgId);
        next.setNoticeCode(prev.getNoticeCode());
        next.setVersion(nextVersion);
        next.setTitleEn(prev.getTitleEn());
        next.setTitleAr(prev.getTitleAr());
        next.setPurposeEn(prev.getPurposeEn());
        next.setPurposeAr(prev.getPurposeAr());
        next.setDataCategories(prev.getDataCategories());
        next.setRecipients(prev.getRecipients());
        next.setRetentionPeriodEn(prev.getRetentionPeriodEn());
        next.setRetentionPeriodAr(prev.getRetentionPeriodAr());
        next.setDpoContact(prev.getDpoContact());
        next.setLawfulBasis(prev.getLawfulBasis());
        next.setBodyEn(prev.getBodyEn());
        next.setBodyAr(prev.getBodyAr());
        next.setLinkedRopaCodes(prev.getLinkedRopaCodes());
        next.setSupersedesId(prev.getId());
        next.setStatus("DRAFT");
        next.setCreatedBy(createdBy);
        return repo.save(next);
    }
}
