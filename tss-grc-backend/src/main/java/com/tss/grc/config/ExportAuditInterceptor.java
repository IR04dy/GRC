package com.tss.grc.config;

import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService.AuditEventBuilder;
import com.tss.grc.module.platform.export.ExportAuditContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;

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

            // Two ways to be an export. Either the response streamed a file, or the handler declared
            // itself one (D-5): GET /reporting/board-pack.json returns every figure in the board pack
            // as data, with no attachment header, and the Content-Disposition test alone would have
            // let the platform's most sensitive aggregate leave with no audit row.
            String cd = res.getHeader(HttpHeaders.CONTENT_DISPOSITION);
            boolean streamed = cd != null && cd.toLowerCase(Locale.ROOT).contains("attachment");
            String declaredFormat = ExportAuditContext.declaredFormat();
            if (!streamed && declaredFormat == null) return;

            String fileName = streamed ? parseFilename(cd) : "";
            String format   = streamed ? ext(fileName) : declaredFormat;
            String lang     = req.getParameter("lang");
            String path     = req.getRequestURI();
            String module   = moduleFromPath(path);
            String type     = typeFromPath(path, fileName);

            // F2-41 — the volume half of the audit trail. A DLP or PDPL enquiry asks two things:
            // how much left, and how sensitive was it. Until now this row answered neither.
            //
            // `bytes` comes from Content-Length and therefore costs nothing and covers EVERY
            // file-returning endpoint. `rowCount` can only come from the generator that built the
            // file (ExportAuditContext), so it is present for exports routed through the shared
            // generators and absent otherwise — and absent is reported as null with
            // rowCountSource=UNINSTRUMENTED, never as 0. "Exported nothing" and "we did not measure"
            // are opposite answers to a DLP question, so a lying zero would be worse than a gap.
            ExportAuditContext.Counted counted = ExportAuditContext.rowCount();
            long bytes = contentLength(res);

            String detail = "{"
                + "\"format\":\"" + esc(format) + "\","
                + "\"language\":\"" + esc(lang == null ? "" : lang) + "\","
                + "\"fileName\":\"" + esc(fileName) + "\","
                + "\"path\":\"" + esc(path) + "\","
                + "\"bytes\":" + (bytes >= 0 ? bytes : "null") + ","
                + "\"rowCount\":" + (counted != null ? String.valueOf(counted.rows()) : "null") + ","
                + "\"sheetCount\":" + (counted != null ? String.valueOf(counted.sheets()) : "null") + ","
                + "\"rowCountSource\":\"" + (counted != null ? esc(counted.generator()) : "UNINSTRUMENTED") + "\","
                + "\"status\":" + res.getStatus()
                + "}";

            audit.recordEvent(new AuditEventBuilder()
                .actionType("EXPORT")
                .sourceModule(module)
                .entityType("Export")
                .entityCode(type)
                .newValueJson(detail)
                .message("Exported " + type + " (" + format
                    + (lang != null && !lang.isBlank() ? ", " + lang : "")
                    + (counted != null ? ", " + counted.rows() + " rows" : "")
                    + (bytes >= 0 ? ", " + bytes + " bytes" : "") + ")"));
        } catch (RuntimeException e) {
            // An audit-write failure must never break a user's download.
            log.debug("export audit skipped for {}: {}", req.getRequestURI(), e.getMessage());
        } finally {
            // MUST run on every request, including the early returns and the error path above.
            // Servlet threads are pooled, so a leaked count would be attributed to whatever request
            // ran next on this thread — an audit trail that is confidently wrong about volume is
            // worse than one that is silent.
            ExportAuditContext.clear();
        }
    }

    /** @return the response's {@code Content-Length}, or {@code -1} when absent or unparseable. */
    private static long contentLength(HttpServletResponse res) {
        String v = res.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (v == null || v.isBlank()) return -1;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ── metadata extraction ──────────────────────────────────────────────

    /** Pull the filename from a Content-Disposition header (quoted or bare). */
    static String parseFilename(String cd) {
        int i = cd.toLowerCase(Locale.ROOT).indexOf("filename=");
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
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Best-effort module from the request path (first segment after /api[/v1]). */
    static String moduleFromPath(String path) {
        String p = path == null ? "" : path.toLowerCase(Locale.ROOT);
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
            // ⚠⚠ F2-324 — null, not "EXPORT". Every other arm names a module
            // (module-registry.yaml, gated by AuditSourceModuleContractTest); "EXPORT" named an
            // action, so it put a fifth spelling into a column that already had four. An export
            // whose path has no segment has no module, and null says that.
            //
            // ⚠ The `seg.toUpperCase()` fallback is deliberately kept and is NOT closed: it has
            // only ever produced registry modules (PRIVACY, TPRM — the URL segment happens to be
            // the package name), and narrowing it to a known set would DROP those real
            // attributions. Filed: the path→module mapping is a mapping job, not a scan.
            default               -> seg.isEmpty() ? null : seg.toUpperCase(Locale.ROOT);
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
