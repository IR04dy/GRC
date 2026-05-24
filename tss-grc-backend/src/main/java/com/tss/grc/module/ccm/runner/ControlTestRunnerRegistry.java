package com.tss.grc.module.ccm.runner;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Indexes every {@link ControlTestRunner} Spring bean by {@code test_type}
 * so the service layer can dispatch by string.
 *
 * <p>If two beans claim the same {@code testType()} the constructor
 * throws — that's a programming error worth failing fast on.
 */
@Component
public class ControlTestRunnerRegistry {

    private final Map<String, ControlTestRunner> byType;

    public ControlTestRunnerRegistry(List<ControlTestRunner> runners) {
        this.byType = runners.stream().collect(Collectors.toMap(
                ControlTestRunner::testType,
                r -> r,
                (a, b) -> { throw new IllegalStateException(
                        "Duplicate ControlTestRunner for type " + a.testType() +
                        ": " + a.getClass() + " vs " + b.getClass()); }
        ));
    }

    public Optional<ControlTestRunner> find(String testType) {
        return Optional.ofNullable(byType.get(testType));
    }
}
