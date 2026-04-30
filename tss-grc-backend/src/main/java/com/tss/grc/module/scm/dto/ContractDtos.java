package com.tss.grc.module.scm.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ContractDtos {

    @Data
    public static class Request {
        private Long vendorId;
        private String contractNumber;
        private String titleEn;
        private String titleAr;
        private String contractType;
        private LocalDate startDate;
        private LocalDate endDate;
        private Boolean autoRenewal;
        private BigDecimal contractValue;
        private String currency;
        private String status;
        private String notes;
    }

    @Data
    public static class Response {
        private Long id;
        private Long vendorId;
        private String vendorNameEn;
        private String contractNumber;
        private String titleEn;
        private String titleAr;
        private String contractType;
        private LocalDate startDate;
        private LocalDate endDate;
        private Boolean autoRenewal;
        private BigDecimal contractValue;
        private String currency;
        private String status;
    }
}
