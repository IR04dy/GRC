package com.tss.grc.module.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** DTOs for the evidence-request comment thread (plan task §12.9). */
public final class EvidenceRequestCommentDtos {

    private EvidenceRequestCommentDtos() {}

    public record CreateCommentDto(
            @NotBlank @Size(max = 4000) String body
    ) {}

    public record CommentResponse(
            Long id,
            Long requestId,
            Long authorId,
            String authorName,
            String body,
            LocalDateTime createdAt
    ) {}
}
