package com.tss.grc.module.controlmap.dto;

import java.util.List;
import java.util.Map;

/**
 * Replace-set: the full list of sub-control ids the entity should map to, plus
 * an optional per-id satisfaction level (FULL / PARTIAL / PLANNED). Ids absent
 * from {@code satisfaction} default to FULL — so older callers that send only
 * {@code subControlIds} keep working.
 */
public record ControlLinkSetRequest(List<Long> subControlIds, Map<Long, String> satisfaction) {}
