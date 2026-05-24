package com.tss.grc.module.bcm.binder.dto;

import com.tss.grc.module.bcm.binder.entity.BcpBinderExport;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTOs for the Slice 10-F BCP binder export surface.
 *
 * <p>Wire response only — the request side is just a path/query parameter
 * (language), so no body type is needed.
 */
public final class BcpBinderDtos {

    private BcpBinderDtos() {}

    /** Wire response shape — never expose {@code file_path} or
     *  {@code row_version} to the client. The file is fetched via the
     *  separate {@code /download} endpoint. */
    public record BinderResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        Long bcpPlanId,
        String exportCode,
        String format,
        String language,
        String versionLabel,
        String watermarkText,
        String status,
        String errorMessage,
        LocalDateTime generatedAt,
        Long generatedByUserId,
        String generatedByName,
        Integer generationDurationMs,
        Integer pageCount,
        Integer byteSize,
        Integer downloadCount,
        LocalDateTime lastDownloadedAt
    ) {
        public static BinderResponse from(BcpBinderExport e) {
            return new BinderResponse(
                e.getId(),
                e.getExternalId(),
                e.getOrganisationId(),
                e.getBcpPlanId(),
                e.getExportCode(),
                e.getFormat(),
                e.getLanguage(),
                e.getVersionLabel(),
                e.getWatermarkText(),
                e.getStatus(),
                e.getErrorMessage(),
                e.getGeneratedAt(),
                e.getGeneratedByUserId(),
                e.getGeneratedByName(),
                e.getGenerationDurationMs(),
                e.getPageCount(),
                e.getByteSize(),
                e.getDownloadCount(),
                e.getLastDownloadedAt()
            );
        }
    }
}
