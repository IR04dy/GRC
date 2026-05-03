package com.tss.grc.module.compliance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FindingResponse(
    Long id,
    String title,
    Long assessmentId,
    String severity,
    String owner,
    String capa,
    LocalDate dueDate,
    String status,
    LocalDateTime createdAt,
    // CAPA closure verification (assessment §5.3 #6)
    String capaStage,
    LocalDateTime ownerTestAt,
    String ownerTestResult,
    String ownerTestNotes,
    LocalDateTime independentTestAt,
    String independentTestResult,
    String independentTestNotes,
    LocalDateTime auditSignoffAt,
    String auditSignoffNotes,
    // Framework anchor (IA-2)
    Long frameworkId,
    String clauseRef
) {}
