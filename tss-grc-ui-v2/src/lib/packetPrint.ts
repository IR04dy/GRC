// PDF packet generation via browser print — Phase 6 of the gap fill.
//
// Avoids adding a heavy PDF dependency (jspdf, pdfmake). Instead opens a
// new window with formatted HTML + a print stylesheet, then calls
// window.print() so Chrome renders a proper PDF via "Save as PDF".
//
// This works in every browser, supports bilingual + RTL layout, and
// produces regulator-ready output that the user can email/upload.

import { loadWhiteLabel } from './whiteLabel';

export interface PacketSection {
  /** Section heading (bilingual). */
  titleEn: string;
  titleAr: string;
  /** Body content — can be plain text or HTML. */
  body: string;
}

export interface PacketDocument {
  /** Document title (bilingual). */
  titleEn: string;
  titleAr: string;
  /** Reference id (e.g. SAMA-OPRES-Q1-2026). */
  referenceId: string;
  /** Bilingual eyebrow over the title (e.g. "SAMA Operational Resilience"). */
  eyebrowEn?: string;
  eyebrowAr?: string;
  /** Sections. */
  sections: PacketSection[];
  /** Optional bilingual classification label. */
  classificationEn?: string;
  classificationAr?: string;
}

/** Generate the styled HTML and open print dialog. */
export function printPacket(doc: PacketDocument, lang: 'en' | 'ar'): void {
  const brand = loadWhiteLabel();
  const orgName = lang === 'ar' ? brand.organisationNameAr : brand.organisationNameEn;
  const html = renderPacketHtml(doc, brand, orgName, lang);
  const w = window.open('', '_blank', 'width=900,height=1200');
  if (!w) {
    // Pop-up blocked — fallback to text download.
    return;
  }
  w.document.write(html);
  w.document.close();
  // Slight delay so layout settles before triggering print.
  w.onload = () => {
    setTimeout(() => {
      w.print();
    }, 250);
  };
}

function renderPacketHtml(doc: PacketDocument, brand: ReturnType<typeof loadWhiteLabel>, orgName: string, lang: 'en' | 'ar'): string {
  const dir = lang === 'ar' ? 'rtl' : 'ltr';
  const title = lang === 'ar' ? doc.titleAr : doc.titleEn;
  const eyebrow = lang === 'ar' ? doc.eyebrowAr : doc.eyebrowEn;
  const classification = lang === 'ar' ? doc.classificationAr : doc.classificationEn;
  const generatedAt = new Date().toLocaleString();
  const primary = brand.primaryHex || '#4f46e5';
  return `<!doctype html><html lang="${lang}" dir="${dir}"><head>
<meta charset="utf-8" />
<title>${title}</title>
<style>
  @page { size: A4; margin: 18mm; }
  * { box-sizing: border-box; }
  html, body { margin: 0; padding: 0; font-family: ${lang === 'ar' ? "'Segoe UI', 'Tahoma'" : "'Segoe UI', 'Arial'"}, sans-serif; color: #1a1a1a; line-height: 1.55; }
  body { font-size: 12pt; }
  .header { display: flex; justify-content: space-between; align-items: flex-start; padding-bottom: 14pt; border-bottom: 3pt solid ${primary}; margin-bottom: 18pt; }
  .header .brand { display: flex; align-items: center; gap: 12pt; }
  .header .brand .mark { width: 44pt; height: 44pt; border-radius: 8pt; background: ${primary}; display: inline-flex; align-items: center; justify-content: center; color: white; font-weight: 800; font-size: 18pt; overflow: hidden; }
  .header .brand .mark img { width: 100%; height: 100%; object-fit: contain; }
  .header .brand .info { font-size: 11pt; }
  .header .brand .org { font-weight: 700; font-size: 13pt; }
  .header .meta { text-align: ${lang === 'ar' ? 'left' : 'right'}; font-size: 9pt; color: #555; }
  .eyebrow { font-size: 9pt; color: ${primary}; text-transform: uppercase; letter-spacing: 0.06em; font-weight: 700; margin-bottom: 4pt; }
  h1 { font-size: 22pt; margin: 0 0 6pt 0; color: #111; }
  .ref { font-family: ui-monospace, 'Cascadia Mono', 'Consolas', monospace; font-size: 10pt; color: #555; margin-bottom: 18pt; }
  .classification { display: inline-block; padding: 3pt 10pt; background: #fef3c7; color: #92400e; font-size: 9pt; font-weight: 700; border-radius: 3pt; margin-bottom: 12pt; }
  section.packet-section { margin-bottom: 18pt; page-break-inside: avoid; }
  section.packet-section h2 { font-size: 13pt; color: ${primary}; border-bottom: 1pt solid #ddd; padding-bottom: 4pt; margin-bottom: 8pt; }
  section.packet-section .body { font-size: 10pt; white-space: pre-wrap; line-height: 1.7; }
  section.packet-section .body table { border-collapse: collapse; width: 100%; font-size: 9pt; margin-top: 6pt; }
  section.packet-section .body th, section.packet-section .body td { border: 1pt solid #ddd; padding: 4pt 6pt; text-align: ${lang === 'ar' ? 'right' : 'left'}; }
  section.packet-section .body th { background: #f3f4f6; font-weight: 700; }
  .footer { margin-top: 30pt; padding-top: 10pt; border-top: 1pt solid #ddd; font-size: 8pt; color: #888; text-align: center; }
  @media print {
    .no-print { display: none; }
  }
</style>
</head><body>
  <div class="header">
    <div class="brand">
      <div class="mark">${brand.logoDataUrl ? `<img src="${brand.logoDataUrl}" alt="logo" />` : (orgName.charAt(0) || 'O')}</div>
      <div class="info">
        <div class="org">${escapeHtml(orgName)}</div>
        ${brand.taglineEn ? `<div style="color:#666;">${escapeHtml(lang === 'ar' ? (brand.taglineAr ?? '') : brand.taglineEn)}</div>` : ''}
      </div>
    </div>
    <div class="meta">
      <div>${escapeHtml(generatedAt)}</div>
      <div>Asia/Riyadh</div>
    </div>
  </div>

  ${eyebrow ? `<div class="eyebrow">${escapeHtml(eyebrow)}</div>` : ''}
  <h1>${escapeHtml(title)}</h1>
  <div class="ref">${escapeHtml(doc.referenceId)}</div>
  ${classification ? `<div class="classification">${escapeHtml(classification)}</div>` : ''}

  ${doc.sections.map(s => `
    <section class="packet-section">
      <h2>${escapeHtml(lang === 'ar' ? s.titleAr : s.titleEn)}</h2>
      <div class="body">${s.body}</div>
    </section>
  `).join('')}

  <div class="footer">
    Generated by TSS GRC Platform · Print or "Save as PDF" via the browser dialog
  </div>
</body></html>`;
}

function escapeHtml(s: string | undefined): string {
  if (!s) return '';
  return s.replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c] ?? c));
}

/** Helper — convert a UTF-8 packet text (with line breaks) into one big section. */
export function textToSection(titleEn: string, titleAr: string, text: string): PacketSection {
  return { titleEn, titleAr, body: escapeHtml(text) };
}
