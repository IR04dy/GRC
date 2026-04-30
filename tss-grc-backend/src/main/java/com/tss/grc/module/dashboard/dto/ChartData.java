package com.tss.grc.module.dashboard.dto;

import java.util.List;

public record ChartData(List<String> labels, List<Long> values) {}
