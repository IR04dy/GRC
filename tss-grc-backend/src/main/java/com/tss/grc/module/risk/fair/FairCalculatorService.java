package com.tss.grc.module.risk.fair;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * FAIR (Factor Analysis of Information Risk) calculator — Sprint-8 Slice 8-A.
 *
 * <p>Open Group FAIR taxonomy in one Saudi-native service. Given a min /
 * likely / max triangular distribution for each factor, runs a Monte Carlo
 * simulation and returns the annualised loss exposure (ALE) distribution
 * (p10 / p50 / p90 / mean / max).
 *
 * <pre>
 *   ALE = LossEventFrequency × LossMagnitude
 *
 *   LossEventFrequency  = ThreatEventFrequency × Vulnerability
 *   LossMagnitude       = PrimaryLoss + SecondaryLoss
 *   PrimaryLoss        = productivity + response + replacement
 *                       (per-event primary cost)
 *   SecondaryLoss      = fines + reputation + competitive
 *                       (per-event secondary cost)
 * </pre>
 *
 * <p>Pure computation — no DB, no schema changes. Future slices can layer
 * persistence ({@code risk.fair_calculations}) and history without breaking
 * this service. Tenant-scoping happens at the controller boundary; the
 * service itself is stateless.
 *
 * <p>Saudi-native bilingual labelling left to the controller / frontend —
 * this service emits numbers only.
 */
@Service
public class FairCalculatorService {

    /** Default iteration count — balances precision vs response time. */
    public static final int DEFAULT_ITERATIONS = 10_000;
    /** Hard cap to prevent abuse of the endpoint. */
    public static final int MAX_ITERATIONS = 100_000;

    /**
     * Triangular distribution input — min / most-likely / max.
     * Numbers may be 0 but not negative; max must be ≥ min.
     */
    public record Triangular(double min, double mostLikely, double max) {
        public Triangular {
            if (min < 0)             throw new IllegalArgumentException("min must be >= 0");
            if (mostLikely < min)    throw new IllegalArgumentException("mostLikely must be >= min");
            if (max < mostLikely)    throw new IllegalArgumentException("max must be >= mostLikely");
        }

        /** Inverse-transform sample given a uniform u in [0,1). */
        double sample(double u) {
            double f = (mostLikely - min) / (max - min);
            if (Double.isNaN(f) || max == min) return min;        // degenerate
            if (u < f) return min + Math.sqrt(u * (max - min) * (mostLikely - min));
            return max - Math.sqrt((1 - u) * (max - min) * (max - mostLikely));
        }
    }

    /**
     * One FAIR scenario's inputs.
     *
     * <p>{@code vulnerability} is a probability in [0, 1] — the share of
     * Threat Events that result in a Loss Event. {@code threatEventFrequency}
     * is in events / year. The two loss buckets are SAR amounts per event.
     */
    public record FairInputs(
        String scenarioName,
        Triangular threatEventFrequency,   // events / year
        Triangular vulnerability,          // [0, 1]
        Triangular primaryLoss,            // SAR / event
        Triangular secondaryLoss,          // SAR / event
        Integer iterations                 // null → DEFAULT_ITERATIONS
    ) {
        public FairInputs {
            if (scenarioName == null || scenarioName.isBlank())
                throw new IllegalArgumentException("scenarioName required");
            if (threatEventFrequency == null) throw new IllegalArgumentException("threatEventFrequency required");
            if (vulnerability == null)        throw new IllegalArgumentException("vulnerability required");
            if (primaryLoss == null)          throw new IllegalArgumentException("primaryLoss required");
            if (secondaryLoss == null)        throw new IllegalArgumentException("secondaryLoss required");
            if (vulnerability.max() > 1.0)
                throw new IllegalArgumentException("vulnerability.max must be <= 1.0");
        }
    }

    /** Quantile result: p10 / p50 / p90 + mean + max + iteration count. */
    public record FairResult(
        String scenarioName,
        int iterations,
        double aleMean,
        double aleP10,
        double aleP50,
        double aleP90,
        double aleMax,
        double lefMean,            // mean Loss Event Frequency (events/yr)
        double lmMean,             // mean Loss Magnitude (SAR/event)
        Map<String, Long> aleHistogram,  // 10-bucket histogram, key = "lo..hi"
        String currency
    ) {}

    public FairResult calculate(FairInputs in) {
        int iterations = (in.iterations() == null || in.iterations() <= 0)
                ? DEFAULT_ITERATIONS
                : Math.min(in.iterations(), MAX_ITERATIONS);

        double[] ales = new double[iterations];
        SplittableRandom rng = new SplittableRandom();

        double lefSum = 0, lmSum = 0;
        for (int i = 0; i < iterations; i++) {
            double tef = in.threatEventFrequency().sample(rng.nextDouble());
            double v   = clamp01(in.vulnerability().sample(rng.nextDouble()));
            double pl  = in.primaryLoss().sample(rng.nextDouble());
            double sl  = in.secondaryLoss().sample(rng.nextDouble());

            double lef = tef * v;
            double lm  = pl + sl;
            double ale = lef * lm;

            ales[i] = ale;
            lefSum += lef;
            lmSum  += lm;
        }

        Arrays.sort(ales);
        double p10 = percentile(ales, 0.10);
        double p50 = percentile(ales, 0.50);
        double p90 = percentile(ales, 0.90);
        double max = ales[iterations - 1];
        double mean = mean(ales);

        return new FairResult(
            in.scenarioName(), iterations,
            mean, p10, p50, p90, max,
            lefSum / iterations,
            lmSum  / iterations,
            histogram(ales, 10),
            "SAR"
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private static double clamp01(double v) { return v < 0 ? 0 : v > 1 ? 1 : v; }

    private static double percentile(double[] sorted, double q) {
        int idx = (int) Math.min(sorted.length - 1, Math.max(0, Math.round(q * (sorted.length - 1))));
        return sorted[idx];
    }

    private static double mean(double[] xs) {
        double s = 0;
        for (double x : xs) s += x;
        return s / xs.length;
    }

    /** 10-bucket linear histogram between p1 and p99 to avoid outlier squashing. */
    private static Map<String, Long> histogram(double[] sortedAles, int buckets) {
        if (sortedAles.length == 0) return Map.of();
        double lo = percentile(sortedAles, 0.01);
        double hi = percentile(sortedAles, 0.99);
        if (hi == lo) hi = lo + 1.0;  // avoid /0 on degenerate scenarios
        double step = (hi - lo) / buckets;
        long[] counts = new long[buckets];
        for (double a : sortedAles) {
            if (a < lo)       { counts[0]++; continue; }
            if (a >= hi)      { counts[buckets - 1]++; continue; }
            int b = (int) Math.min(buckets - 1, Math.floor((a - lo) / step));
            counts[b]++;
        }
        Map<String, Long> out = new LinkedHashMap<>();
        for (int i = 0; i < buckets; i++) {
            long lo0 = Math.round(lo + i * step);
            long hi0 = Math.round(lo + (i + 1) * step);
            out.put(lo0 + ".." + hi0, counts[i]);
        }
        return out;
    }
}
