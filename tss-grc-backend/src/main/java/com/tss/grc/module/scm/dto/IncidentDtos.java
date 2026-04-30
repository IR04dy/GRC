package com.tss.grc.module.scm.dto;

import lombok.Data;
import java.time.LocalDateTime;

public class IncidentDtos {

    @Data
    public static class Request {
        private Long vendorId;
        private String incidentType;
        private String impact;
        private String description;
        private Boolean slaBreached;
        private Long assignedToId;
    }

    @Data
    public static class Response {
        private Long id;
        private String incidentCode;
        private Long vendorId;
        private String vendorNameEn;
        private String vendorNameAr;
        private String incidentType;
        private String impact;
        private String description;
        private Boolean slaBreached;
        private String status;
        private Boolean backupPlanActivated;
        private Boolean capaCreated;
        private Boolean escalatedToManagement;
        private LocalDateTime reportedAt;
        private LocalDateTime resolvedAt;
        private String resolutionNotes;
    }
}
