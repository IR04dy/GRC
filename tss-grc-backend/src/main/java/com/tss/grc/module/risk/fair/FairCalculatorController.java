package com.tss.grc.module.risk.fair;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
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
@RequireModule("RISK")
@Tag(name = "Risk — FAIR calculator")
public class FairCalculatorController {

    private final FairCalculatorService service;

    @PostMapping("/calculate")
    @PreAuthorize("hasPermission('RISK','READ')")
    public FairResult calculate(@Valid @RequestBody FairInputs body) {
        // SEC-RISK-004: pure read-only what-if computation (no persistence),
        // so RISK:READ is the correct gate — but it must be a real module
        // permission, not isAuthenticated(), to keep low-privilege roles out
        // of the risk surface and preserve segregation of duties.
        return service.calculate(body);
    }
}
