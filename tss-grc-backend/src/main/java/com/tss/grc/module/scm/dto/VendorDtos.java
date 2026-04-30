package com.tss.grc.module.scm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

public class VendorDtos {

    @Data
    public static class Request {
        @NotBlank
        private String nameEn;
        private String nameAr;
        private String category;
        private String criticality;
        private String country;
        private String serviceType;
        private String riskLevel;
        private String status;
        private String website;
        private String contactName;
        private String contactEmail;
        private String contactPhone;
        private String registrationNumber;
        private String description;
    }

    @Data
    public static class Response {
        private Long id;
        private String externalId;
        private String nameEn;
        private String nameAr;
        private String category;
        private String criticality;
        private String country;
        private String serviceType;
        private String riskLevel;
        private String status;
        private String website;
        private String contactName;
        private String contactEmail;
        private String contactPhone;
        private String registrationNumber;
        private String onboardingStatus;
        private String description;
        private LocalDateTime createdAt;
    }
}
