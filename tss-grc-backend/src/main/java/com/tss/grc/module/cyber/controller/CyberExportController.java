package com.tss.grc.module.cyber.controller;

import com.tss.grc.module.cyber.service.CyberExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CY-F — cyber register exports (Excel + Arabic-RTL PDF).
 *
 * <pre>
 *   GET /api/v1/cyber/export?register=controls&format=xlsx
 *   GET /api/v1/cyber/export?register=vulnerabilities&format=pdf&lang=ar
 * </pre>
 *
 * <p>READ-gated (CYBER:READ) so auditors/regulators can pull the pack without
 * write access. An unknown {@code register}/{@code format} returns <b>400</b>
 * (via the service's {@code CyberInputValidation} guard), not a 500.
 */
@RestController
@RequestMapping("/api/v1/cyber/export")
@RequiredArgsConstructor
@Tag(name = "Cyber Security — Exports")
@SecurityRequirement(name = "bearerAuth")
public class CyberExportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CyberExportService exportService;

    @GetMapping
    @PreAuthorize("hasPermission('CYBER', 'EXPORT')")
    @Operation(summary = "Export a cyber register (controls | vulnerabilities | assessments | incidents | "
            + "action-plans | evidence) as an Excel workbook or an Arabic-capable executive PDF. lang=ar renders Arabic titles.")
    public ResponseEntity<byte[]> export(@RequestParam String register,
                                         @RequestParam(defaultValue = "XLSX") String format,
                                         @RequestParam(required = false) String lang) {
        byte[] body = exportService.export(register, format, lang);   // validates register/format → 400
        boolean pdf = "PDF".equalsIgnoreCase(format);
        String slug = register.toLowerCase().replaceAll("[^a-z0-9-]", "");
        String filename = "cyber-" + (slug.isEmpty() ? "register" : slug) + (pdf ? ".pdf" : ".xlsx");
        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
