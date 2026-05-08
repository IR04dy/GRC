package com.tss.grc.module.knowledge.dto;

import com.tss.grc.module.knowledge.entity.KbArticle;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class KbArticleDto {
    private String id;
    @NotBlank @Pattern(regexp = "[a-z0-9-]+", message = "slug must be lowercase letters/digits/hyphens")
    private String slug;
    private String categoryCode;
    @NotBlank private String titleEn;
    @NotBlank private String titleAr;
    private String summaryEn;
    private String summaryAr;
    private String bodyEnMd;
    private String bodyArMd;
    private String tags;
    private String status;
    @Pattern(regexp = "INTERNAL|PUBLIC|RESTRICTED")
    private String visibility;
    private String author;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
    private Integer viewCount;

    public static KbArticleDto from(KbArticle e) {
        return KbArticleDto.builder()
            .id(e.getExternalId()).slug(e.getSlug()).categoryCode(e.getCategoryCode())
            .titleEn(e.getTitleEn()).titleAr(e.getTitleAr())
            .summaryEn(e.getSummaryEn()).summaryAr(e.getSummaryAr())
            .bodyEnMd(e.getBodyEnMd()).bodyArMd(e.getBodyArMd())
            .tags(e.getTags())
            .status(e.getStatus()).visibility(e.getVisibility())
            .author(e.getAuthor())
            .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt()).publishedAt(e.getPublishedAt())
            .viewCount(e.getViewCount())
            .build();
    }

    @Getter @Setter @NoArgsConstructor
    public static class TransitionRequest {
        @NotBlank @Pattern(regexp = "DRAFT|PUBLISHED|ARCHIVED")
        private String status;
    }
}
