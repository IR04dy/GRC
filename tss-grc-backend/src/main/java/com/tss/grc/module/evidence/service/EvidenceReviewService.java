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
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EvidenceReviewService {

    private final EvidenceReviewRepository reviewRepository;
    private final EvidenceItemRepository itemRepository;
    private final EvidenceItemService evidenceItemService;
    private final UserRepository userRepository;

    private static final Set<String> REVIEW_RESULTS = Set.of("APPROVED", "REJECTED", "NEEDS_REVISION");

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
        if (result == null || !REVIEW_RESULTS.contains(result)) {
            throw new IllegalArgumentException(
                "Unknown review result: '" + result + "'. Valid results: " + REVIEW_RESULTS);
        }
        EvidenceReview review = reviewRepository.findById(id)
                .filter(r -> r.getOrganisationId().equals(orgId))
                .orElseThrow(() -> new EntityNotFoundException("Review not found: " + id));
        review.setResult(result);
        review.setNotesEn(notes);
        review.setStatus("COMPLETED");
        review.setReviewDate(LocalDate.now());
        review = reviewRepository.save(review);

        // Drive the evidence item's status through the single item-path
        // pipeline (audit log + DomainEvent + transition guard) rather than
        // mutating item.status directly here. This stops the review and the
        // item-level approve/reject paths from diverging, and preserves
        // NEEDS_REVISION as a distinct state (no longer collapsed to DRAFT).
        EvidenceItem item = itemRepository.findById(review.getEvidenceId()).orElse(null);
        if (item != null && item.getOrganisationId().equals(orgId)) {
            switch (result) {
                case "APPROVED"       -> evidenceItemService.approve(item.getId());
                case "REJECTED"       -> evidenceItemService.reject(item.getId());
                case "NEEDS_REVISION" -> evidenceItemService.requestRevision(item.getId());
                default               -> { /* validated above — unreachable */ }
            }
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
