package com.tss.grc.module.change_mgmt.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(schema = "change_mgmt", name = "change_comments")
@Getter @Setter @NoArgsConstructor
public class ChangeComment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "change_request_id", nullable = false)
    private Long changeRequestId;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "author_name", nullable = false, length = 255)
    private String authorName;

    @Column(name = "body", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String body;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
