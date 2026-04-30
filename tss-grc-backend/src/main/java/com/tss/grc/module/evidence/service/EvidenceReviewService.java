package com.tss.grc.module.evidence.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.evidence.dto.EvidenceReviewRequest;
import com.tss.grc.module.evidence.dto.EvidenceReviewResponse;
import com.tss.grc.module.evidence.entity.EvidenceItem;
import com.tss.grc.module.evidence.entity.EvidenceReview;
import com.tss.grc.module.evidence.repository.EvidenceItemRepository;
import com.tss.grc.module.evidence.repository.EvidenceReviewRepository;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class EvidenceReviewService {

    private final EvidenceReviewRepository reviewRepository;
    private final EvidenceItemRepository itemRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<EvidenceReviewResponse> list(String status, Pageable pageable) {
        Long orgId = TenantContext.getCurrentTenant();
        Page<EvidenceReview> page = (status != null && !status.isBlank())
                ? reviewRepository.findByOrgAndStatus(orgId, status, pageable)
                : reviewRepository.findByOrganisationId(orgId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional
    public EvidenceReviewResponse create(EvidenceReviewRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        EvidenceReview review = new EvidenceReview();
        review.setOrganisationId(orgId);
        review.setEvidenceId(req.evidenceId());
        review.setReviewerId(req.reviewerId());
        review.setReviewDate(req.reviewDate() != null ? req.reviewDate() : LocalDate.now());
        review.setNotesEn(req.notesEn());
        if (req.status() != null) review.setStatus(req.status());
        review = reviewRepository.save(review);
        return toResponse(review);
    }

    @Transactional
    public EvidenceReviewResponse complete(Long id, String result, String notes) {
        Long orgId = TenantContext.getCurrentTenant();
        EvidenceReview review = reviewRepository.findById(id)
                .filter(r -> r.getOrganisationId().equals(orgId))
                .orElseThrow(() -> new EntityNotFoundException("Review not found: " + id));
        review.setResult(result);
        review.setNotesEn(notes);
        review.setStatus("COMPLETED");
        review.setReviewDate(LocalDate.now());
        review = reviewRepository.save(review);

        // Sync evidence item status
        EvidenceItem item = itemRepository.findById(review.getEvidenceId()).orElse(null);
        if (item != null && item.getOrganisationId().equals(orgId)) {
            if ("APPROVED".equals(result)) item.setStatus("APPROVED");
            else if ("REJECTED".equals(result)) item.setStatus("REJECTED");
            else item.setStatus("DRAFT");
            itemRepository.save(item);
        }
        return toResponse(review);
    }

    private EvidenceReviewResponse toResponse(EvidenceReview r) {
        String evidenceTitle = itemRepository.findById(r.getEvidenceId())
                .map(EvidenceItem::getTitleEn).orElse(null);
        String reviewerName = r.getReviewerId() != null
                ? userRepository.findById(r.getReviewerId()).map(User::getFullNameEn).orElse(null)
                : null;
        return new EvidenceReviewResponse(
                r.getId(), r.getExternalId(),
                r.getEvidenceId(), evidenceTitle,
                r.getReviewerId(), reviewerName,
                r.getReviewDate(), r.getResult(), r.getNotesEn(), r.getStatus(),
                r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}
