package com.tss.grc.config;

import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService.AuditEventBuilder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Export remediation P2 — central export audit trail. Any successful response that streams a file
 * (Content-Disposition: attachment) is recorded as an {@code EXPORT} event in the existing platform
 * audit log — capturing WHO exported WHAT, WHEN, in which format/language. One cross-cutting hook,
 * so every current and future export endpoint is covered with zero per-controller changes and no
 * new table (the audit log already carries org, user, IP, endpoint and is off-request-safe).
 *
 * <p>Queryable via {@code GET /api/v1/audit-log?actionType=EXPORT}. Audit failure never breaks the
 * download (best-effort, fully guarded).
 */
@Component
@RequiredArgsConstructor
public class ExportAuditInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ExportAuditInterceptor.class);

    private final AuditLogService audit;

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        try {
            if (res.getStatus() >= 400) return;                     // only successful downloads
            String cd = res.getHeader(HttpHeaders.CONTENT_DISPOSITION);
            if (cd == null || !cd.toLowerCase().contains("attachment")) return;   // only file streams

            String fileName = parseFilename(cd);
            String format   = ext(fileName);
            String lang     = req.getParameter("lang");
            String path     = req.getRequestURI();
            String module   = moduleFromPath(path);
            String type     = typeFromPath(path, fileName);

            String detail = "{"
                + "\"format\":\"" + esc(format) + "\","
                + "\"language\":\"" + esc(lang == null ? "" : lang) + "\","
                + "\"fileName\":\"" + esc(fileName) + "\","
                + "\"path\":\"" + esc(path) + "\","
                + "\"status\":" + res.getStatus()
                + "}";

            audit.recordEvent(new AuditEventBuilder()
                .actionType("EXPORT")
                .sourceModule(module)
                .entityType("Export")
                .entityCode(type)
                .newValueJson(detail)
                .message("Exported " + type + " (" + format
                    + (lang != null && !lang.isBlank() ? ", " + lang : "") + ")"));
        } catch (RuntimeException e) {
            // An audit-write failure must never break a user's download.
            log.debug("export audit skipped for {}: {}", req.getRequestURI(), e.getMessage());
        }
    }

    // ── metadata extraction ──────────────────────────────────────────────

    /** Pull the filename from a Content-Disposition header (quoted or bare). */
    static String parseFilename(String cd) {
        int i = cd.toLowerCase().indexOf("filename=");
        if (i < 0) return "";
        String v = cd.substring(i + "filename=".length()).trim();
        if (v.startsWith("\"")) {
            int end = v.indexOf('"', 1);
            return end > 0 ? v.substring(1, end) : v.substring(1);
        }
        int semi = v.indexOf(';');
        return semi > 0 ? v.substring(0, semi).trim() : v;
    }

    /** Lower-case extension of a filename (pdf/xlsx/docx/pptx/csv/zip/json/…), or "file". */
    static String ext(String fileName) {
        if (fileName == null) return "file";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "file";
        return fileName.substring(dot + 1).toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /** Best-effort module from the request path (first segment after /api[/v1]). */
    static String moduleFromPath(String path) {
        String p = path == null ? "" : path.toLowerCase();
        String seg = firstSegmentAfterApi(p);
        return switch (seg) {
            case "reporting"      -> "REPORTING";
            case "cyber"          -> "CYBER";
            case "socops"         -> "SOCOPS";
            case "bcm"            -> "BCM";
            case "ndi"            -> "NDI";
            case "sustainability", "climate" -> "SUST";
            case "ai"             -> "AI";
            case "committees"     -> "ADMIN";
            case "attachments"    -> "EVIDENCE";
            case "risk"           -> "RISK";
            case "calendar"       -> "PLATFORM";
            default               -> seg.isEmpty() ? "EXPORT" : seg.toUpperCase();
        };
    }

    private static String firstSegmentAfterApi(String p) {
        String[] parts = p.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("api")) {
                int j = i + 1;
                if (j < parts.length && parts[j].matches("v\\d+")) j++;   // skip version segment
                return j < parts.length ? parts[j] : "";
            }
        }
        return "";
    }

    /** Export identifier — the last path segment sans extension; falls back to the file's base name
     *  for generic segments like "export"/"download". */
    static String typeFromPath(String path, String fileName) {
        String[] parts = (path == null ? "" : path).split("/");
        String last = parts.length > 0 ? parts[parts.length - 1] : "";
        int dot = last.indexOf('.');
        if (dot > 0) last = last.substring(0, dot);
        if (last.isEmpty() || last.matches("\\d+") || last.equals("export") || last.equals("download")
                || last.equals("version") || last.equals("export-adhoc")) {
            String base = fileName == null ? "" : fileName;
            int fd = base.lastIndexOf('.');
            if (fd > 0) base = base.substring(0, fd);
            return base.isEmpty() ? "export" : base;
        }
        return last;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
