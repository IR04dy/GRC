package com.tss.grc.module.integrations.llm.entity;

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

/**
 * Append-only metering and audit log for outbound / local LLM inference calls
 * ({@code platform.llm_call_log}, V401).
 *
 * <p>Stores the SHA-256 hex digest of the sanitized prompt rather than raw
 * prompt text so tenant data is not duplicated in telemetry logs, while
 * enabling per-tenant 24h token quota enforcement and cost/latency analysis.
 */
@Entity
@Table(schema = "platform", name = "llm_call_log")
@Where(clause = "is_deleted = 0")
@Getter
@Setter
@NoArgsConstructor
public class LlmCallLog extends BaseEntity {

    public static final ClosedVocabulary STATUS = ClosedVocabulary.of(
        "status",
        "CK_llm_call_log_status",
        "SUCCEEDED", "FAILED", "QUOTA_EXCEEDED", "DISABLED"
    );

    @Column(name = "organisation_id", nullable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long organisationId;

    @Column(name = "feature_code", nullable = false, length = 60)
    private String featureCode;

    @Column(name = "prompt_template_id", nullable = false, length = 80)
    private String promptTemplateId;

    @Column(name = "prompt_template_version", nullable = false, length = 20)
    private String promptTemplateVersion;

    @Column(name = "provider", nullable = false, length = 40)
    private String provider;

    @Column(name = "model", nullable = false, length = 120)
    private String model;

    @Column(name = "prompt_sha256", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String promptSha256;

    @Column(name = "input_tokens", nullable = false)
    private Integer inputTokens = 0;

    @Column(name = "output_tokens", nullable = false)
    private Integer outputTokens = 0;

    @Column(name = "latency_ms", nullable = false)
    private Long latencyMs = 0L;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "finish_reason", length = 60)
    private String finishReason;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;
}
