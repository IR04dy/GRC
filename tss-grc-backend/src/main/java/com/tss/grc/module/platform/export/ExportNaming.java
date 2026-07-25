package com.tss.grc.module.platform.export;

import java.time.LocalDate;

/**
 * Export remediation P1 (§15) — one shared, filename-safe naming convention so downloaded files
 * carry module, type, <b>language</b> and <b>version</b> (server names were dated but omitted both).
 *
 * <p>Pattern: {@code [Module]_[ExportType]_[LANG]_[YYYYMMDD]_v[N].[ext]}, e.g.
 * {@code ERM_RiskRegister_AR_20260708_v1.pdf}. Organisation prefix is intentionally omitted here
 * (the reporting controller has no tenant-name context); callers that do can prepend it.
 */
public final class ExportNaming {

    private ExportNaming() {}

    /** Build a filename with an explicit version. */
    public static String build(String module, String exportType, String lang, int version, String ext) {
        String date = LocalDate.now().toString().replace("-", "");
        String langCode = (lang != null && lang.trim().toLowerCase().startsWith("ar")) ? "AR" : "EN";
        return sanitize(module) + "_" + sanitize(exportType) + "_" + langCode
             + "_" + date + "_v" + Math.max(1, version) + "." + safeExt(ext);
    }

    /** Build a filename at version 1. */
    public static String build(String module, String exportType, String lang, String ext) {
        return build(module, exportType, lang, 1, ext);
    }

    /** Collapse anything non-alphanumeric to a single hyphen; trim leading/trailing hyphens. */
    private static String sanitize(String s) {
        if (s == null || s.isBlank()) return "Export";
        String out = s.trim().replaceAll("[^A-Za-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return out.isEmpty() ? "Export" : out;
    }

    private static String safeExt(String ext) {
        if (ext == null || ext.isBlank()) return "bin";
        return ext.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
