package com.tss.grc.module.scm.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class OnboardingDtos {

    @Data
    public static class Request {
        private Long vendorId;
        private String currentStage;
        private String status;
        private Long assignedToId;
        private LocalDate dueDate;
        private String notes;
    }

    @Data
    public static class Response {
        private Long id;
        private Long vendorId;
        private String vendorNameEn;
        private String vendorNameAr;
        private String currentStage;
        private String status;
        private Long assignedToId;
        private LocalDate dueDate;
        private String completedStages;
        private String notes;
        private LocalDateTime createdAt;
    }
}
