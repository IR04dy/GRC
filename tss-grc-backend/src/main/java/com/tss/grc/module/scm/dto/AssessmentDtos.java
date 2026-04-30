package com.tss.grc.module.scm.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AssessmentDtos {

    @Data
    public static class Request {
        private Long vendorId;
        private String assessmentType;
        private LocalDate assessmentDate;
        private LocalDate nextAssessmentDate;
        private BigDecimal overallScore;
        private String riskRating;
        private String status;
        private Long assessorId;
        private String findings;
        private String recommendations;
    }

    @Data
    public static class Response {
        private Long id;
        private Long vendorId;
        private String vendorNameEn;
        private String vendorNameAr;
        private String assessmentType;
        private LocalDate assessmentDate;
        private LocalDate nextAssessmentDate;
        private BigDecimal overallScore;
        private String riskRating;
        private String status;
        private String findings;
        private String recommendations;
        private Boolean escalatedToRisk;
        private LocalDateTime createdAt;
    }
}
