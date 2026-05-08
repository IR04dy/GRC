package com.tss.grc.module.knowledge.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDateTime;

@Entity
@Table(schema = "kb", name = "articles")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class KbArticle {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(nullable = false, length = 120)
    private String slug;

    @Column(name = "category_code", length = 64)
    private String categoryCode;

    @Column(name = "title_en", nullable = false, length = 500) private String titleEn;
    @Column(name = "title_ar", nullable = false, length = 500) private String titleAr;

    @Column(name = "summary_en", columnDefinition = "NVARCHAR(MAX)") private String summaryEn;
    @Column(name = "summary_ar", columnDefinition = "NVARCHAR(MAX)") private String summaryAr;

    @Column(name = "body_en_md", columnDefinition = "NVARCHAR(MAX)") private String bodyEnMd;
    @Column(name = "body_ar_md", columnDefinition = "NVARCHAR(MAX)") private String bodyArMd;

    @Column(length = 500) private String tags;

    /** DRAFT/PUBLISHED/ARCHIVED */
    @Column(nullable = false, length = 20) private String status = "DRAFT";
    /** INTERNAL/PUBLIC/RESTRICTED */
    @Column(nullable = false, length = 20) private String visibility = "INTERNAL";

    @Column(length = 255) private String author;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "published_at") private LocalDateTime publishedAt;
    @Column(name = "view_count", nullable = false) private Integer viewCount = 0;

    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
}
