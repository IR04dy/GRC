package com.tss.grc.module.governance.threelines.dto;

import java.util.List;

/**
 * Returns the LoD band(s) for a single user — used by the frontend "my
 * band" highlighter and by the JWT claim builder.
 *
 * <p>{@code effectiveBand} is the single highest band used for assurance
 * independence (LINE_3 &gt; LINE_2 &gt; LINE_1; BOARD treated separately).
 * {@code allBands} retains every band the user touches so the UI can show
 * "you are on the Risk Committee (BOARD) and also a Risk Owner (LINE_1)".
 */
public record UserBandDto(
        Long userId,
        String username,
        List<String> roleNames,
        List<String> allBands,
        String effectiveBand
) {}
