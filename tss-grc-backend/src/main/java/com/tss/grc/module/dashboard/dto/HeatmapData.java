package com.tss.grc.module.dashboard.dto;

import java.util.List;

public record HeatmapData(List<HeatmapCell> cells) {
    public record HeatmapCell(int likelihood, int impact, long count) {}
}
