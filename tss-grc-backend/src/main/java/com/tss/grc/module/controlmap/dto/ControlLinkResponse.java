package com.tss.grc.module.controlmap.dto;

public record ControlLinkResponse(
    Long subControlId,
    String subControlCode,
    String subControlNameEn,
    String subControlNameAr,
    String controlCode,
    String frameworkCode,
    String satisfaction
) {}
