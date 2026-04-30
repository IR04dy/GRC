package com.tss.grc.module.scm.dto;

import lombok.Data;
import java.time.LocalDateTime;

public class DependencyDtos {

    @Data
    public static class Request {
        private String serviceNameEn;
        private String serviceNameAr;
        private String assetName;
        private Long vendorId;
        private String dependencyType;
        private String dependencyLevel;
        private Boolean hasAlternative;
        private Long alternativeVendorId;
        private String status;
        private String impactAnalysis;
    }

    @Data
    public static class Response {
        private Long id;
        private String serviceNameEn;
        private String serviceNameAr;
        private String assetName;
        private Long vendorId;
        private String vendorNameEn;
        private String dependencyType;
        private String dependencyLevel;
        private Boolean hasAlternative;
        private Long alternativeVendorId;
        private String alternativeVendorNameEn;
        private String status;
        private String impactAnalysis;
        private LocalDateTime createdAt;
    }
}
