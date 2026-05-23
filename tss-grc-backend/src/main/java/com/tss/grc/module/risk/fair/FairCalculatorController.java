package com.tss.grc.module.risk.fair;

import com.tss.grc.module.risk.fair.FairCalculatorService.FairInputs;
import com.tss.grc.module.risk.fair.FairCalculatorService.FairResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FAIR calculator — Sprint-8 Slice 8-A.
 *
 * <pre>
 *   POST /api/v1/risk/fair/calculate
 *   Body: FairInputs (scenarioName, threatEventFrequency, vulnerability,
 *                     primaryLoss, secondaryLoss, iterations?)
 *   Returns: FairResult (p10/p50/p90/mean/max ALE + histogram)
 * </pre>
 *
 * <p>Pure computation — no DB, no schema changes. Stateless service;
 * tenant-scoping not needed because no data leaves the request.
 *
 * <p>Currency normalised to SAR; localisation handled at the frontend.
 */
@RestController
@RequestMapping("/api/v1/risk/fair")
@RequiredArgsConstructor
@Tag(name = "Risk — FAIR calculator")
public class FairCalculatorController {

    private final FairCalculatorService service;

    @PostMapping("/calculate")
    @PreAuthorize("isAuthenticated()")
    public FairResult calculate(@Valid @RequestBody FairInputs body) {
        return service.calculate(body);
    }
}
