package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.reporting.service.PdfTheme;
import com.tss.grc.module.reporting.service.RegisterExport;
import com.tss.grc.module.reporting.service.RegisterExport.Col;
import com.tss.grc.module.socops.entity.SecurityEvent;
import com.tss.grc.module.socops.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CY-F — SOC Operations register export. The security-event queue (the SOC
 * spine) renders to Excel or an Arabic-capable executive PDF, reusing the
 * shared {@link RegisterExport} engine so socops output matches the cyber and
 * board-pack house style.
 */
@Service
@RequiredArgsConstructor
public class SocOpsExportService {

    private static final DateTimeFormatter DTFMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SecurityEventRepository eventRepo;
    private final OrganisationRepository orgRepo;

    @Transactional(readOnly = true)
    public byte[] exportSecurityEvents(String format) {
        Long orgId = TenantContext.getCurrentTenant();
        Organisation org = orgId == null ? null : orgRepo.findById(orgId).orElse(null);
        List<SecurityEvent> rows = eventRepo
                .findByOrganisationIdOrderByEventTimeDesc(orgId, PageRequest.of(0, 5000)).getContent();

        List<Col<SecurityEvent>> cols = List.of(
                Col.of("Event ID", SecurityEvent::getExternalId),
                Col.of("Source", SecurityEvent::getSourceSystem),
                Col.of("Time", e -> dt(e.getEventTime())),
                Col.of("Severity", SecurityEvent::getSeverity),
                Col.of("Category", SecurityEvent::getCategory),
                Col.of("Rule", SecurityEvent::getRuleName),
                Col.of("Source host", SecurityEvent::getSourceHost),
                Col.of("Source IP", SecurityEvent::getSourceIp),
                Col.of("User", SecurityEvent::getUsername),
                Col.of("Status", SecurityEvent::getStatus),
                Col.of("Incident", e -> e.getIncidentId() == null ? "" : String.valueOf(e.getIncidentId())),
                Col.of("SLA due", e -> dt(e.getSlaDueAt())));

        long crit = rows.stream().filter(e -> "CRITICAL".equalsIgnoreCase(e.getSeverity())).count();
        long open = rows.stream().filter(e -> !"RESOLVED".equalsIgnoreCase(e.getStatus())
                && !"CLOSED".equalsIgnoreCase(e.getStatus())).count();
        long promoted = rows.stream().filter(e -> e.getIncidentId() != null).count();
        List<PdfTheme.Kpi> kpis = List.of(
                new PdfTheme.Kpi("Total events", String.valueOf(rows.size()), ""),
                new PdfTheme.Kpi("Critical", String.valueOf(crit), ""),
                new PdfTheme.Kpi("Open", String.valueOf(open), "un-resolved"),
                new PdfTheme.Kpi("Promoted", String.valueOf(promoted), "to incident"));

        String orgEn = org == null ? null : org.getNameEn();
        String orgAr = org == null ? null : org.getNameAr();
        return "PDF".equalsIgnoreCase(format)
                ? RegisterExport.pdf(orgEn, orgAr, "SOC Operations", "عمليات الأمن السيبراني",
                        "Security Event Register", "سجل الأحداث الأمنية", kpis, cols, rows)
                : RegisterExport.xlsx("Security Events", cols, rows);
    }

    private static String dt(LocalDateTime v) { return v == null ? "" : v.format(DTFMT); }
}
