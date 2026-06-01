package com.tss.grc.module.controlmap.dto;

import java.util.List;

/** Replace-set: the full list of sub-control ids the entity should map to. */
public record ControlLinkSetRequest(List<Long> subControlIds) {}
