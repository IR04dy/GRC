package com.tss.grc.module.platform.savedviews.dto;

import com.tss.grc.module.platform.savedviews.entity.SavedView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Wire-shape for {@link SavedView}. Field names match the
 * {@code lib/savedViews.ts} TypeScript interface 1-for-1 so the
 * frontend adapter doesn't need a translation layer.
 */
public record SavedViewDto(
    Long id,
    @NotBlank @Size(max = 120) String pageKey,
    @NotBlank @Size(max = 160) String name,
    Boolean isDefault,
    Boolean isShared,
    String filterJson,
    String sortJson,
    String visibleColumnsJson,
    @Size(max = 2000) String urlQuery,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    public static SavedViewDto from(SavedView v) {
        return new SavedViewDto(
            v.getId(),
            v.getPageKey(),
            v.getName(),
            v.getIsDefault(),
            v.getIsShared(),
            v.getFilterJson(),
            v.getSortJson(),
            v.getVisibleColumnsJson(),
            v.getUrlQuery(),
            v.getCreatedAt(),
            v.getUpdatedAt()
        );
    }
}
