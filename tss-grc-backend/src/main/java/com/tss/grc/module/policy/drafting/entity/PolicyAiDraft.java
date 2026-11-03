package com.tss.grc.module.policy.drafting.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tss.grc.common.model.BaseEntity;
import com.tss.grc.common.validation.ClosedVocabulary;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * AI-generated policy draft staging record ({@code policy.ai_drafts}, V401).
 *
 * <p>Lifecycle:
 * {@code REQUESTED -> GENERATING -> GENERATED | FAILED}, followed by human
 * disposition {@code GENERATED -> ACCEPTED | REJECTED} (or {@code FAILED -> REJECTED}).
 *
 * <p>Accepting a draft promotes its bilingual content into {@code policy.documents}
 * via {@code PolicyManagementService#create} at {@code DRAFT} status so C-6
 * segregation-of-duty and document-control publish gates remain strictly enforced.
 */
@Entity
@Table(schema = "policy", name = "ai_drafts")
@Where(clause = "is_deleted = 0")
@Getter
@Setter
@NoArgsConstructor
public class PolicyAiDraft extends BaseEntity {

    public static final ClosedVocabulary STATUS = ClosedVocabulary.of(
        "status",
        "CK_policy_ai_drafts_status",
        "REQUESTED", "GENERATING", "GENERATED", "FAILED", "ACCEPTED", "REJECTED"
    );

    @Column(name = "organisation_id", nullable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "policy_type", nullable = false, length = 60)
    private String policyType;

    @Column(name = "category", nullable = false, length = 60)
    private String category;

    @Column(name = "tone", nullable = false, length = 40)
    private String tone;

    @Column(name = "request_json", nullable = false, length = -1, columnDefinition = "NVARCHAR(MAX)")
    private String requestJson;

    @Column(name = "output_json", length = -1, columnDefinition = "NVARCHAR(MAX)")
    private String outputJson;

    @Column(name = "edited_output_json", length = -1, columnDefinition = "NVARCHAR(MAX)")
    private String editedOutputJson;

    @Column(name = "prompt_template_id", nullable = false, length = 80)
    private String promptTemplateId;

    @Column(name = "prompt_template_version", nullable = false, length = 20)
    private String promptTemplateVersion;

    @Column(name = "provider", length = 40)
    private String provider;

    @Column(name = "model", length = 120)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private Integer inputTokens = 0;

    @Column(name = "output_tokens", nullable = false)
    private Integer outputTokens = 0;

    @Column(name = "latency_ms", nullable = false)
    private Long latencyMs = 0L;

    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

    @Column(name = "hallucinated_citations", length = -1, columnDefinition = "NVARCHAR(MAX)")
    private String hallucinatedCitations;

    @Column(name = "accepted_policy_id")
    private Long acceptedPolicyId;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "requested_by_user_id")
    private Long requestedByUserId;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewer_note", length = 1000)
    private String reviewerNote;
}
