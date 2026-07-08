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
  /** When true the body is pre-rendered structured HTML (kv rows / lines), not pre-wrap text. */
  structured?: boolean;
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
  section.packet-section .body.structured { white-space: normal; line-height: 1.5; }
  .body.structured .kv { display: flex; gap: 10pt; margin: 2.5pt 0; align-items: baseline; }
  .body.structured .kv .k { flex: 0 0 34%; max-width: 34%; font-weight: 600; color: #374151; }
  .body.structured .kv .v { flex: 1 1 auto; }
  .body.structured .ln { margin: 2.5pt 0; }
  .body.structured .ln.head { font-weight: 700; color: #111; margin-top: 6pt; }
  .body.structured .sp { height: 6pt; }
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
      <div class="body${s.structured ? ' structured' : ''}">${s.body}</div>
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

// ── Structured packet parsing ────────────────────────────────────────────────
// The lifecycle builders emit a regular grammar: a `─`×64 divider band around the
// title, a `Key : value` header block, numbered sections `N. ENGLISH · العربية`,
// and a `Generated by …` footer. This parses that into real PacketSection[] so the
// PDF renders proper section headers + key/value rows instead of one monospace blob.

const DIVIDER_RE = /^[─━=_-]{6,}$/;
const HEADING_RE = /^\s*(\d+)\.\s+(.+?)\s+·\s+(.+?)\s*$/;

/** Parse a packet's plain text into structured, branded-PDF-ready sections.
 *  The title band + footer credit are dropped (the PDF template renders its own). */
export function packetTextToSections(text: string): PacketSection[] {
  const lines = text.replace(/\r\n/g, '\n').split('\n');
  const sections: PacketSection[] = [];

  let firstHeading = lines.findIndex(l => HEADING_RE.test(l));
  if (firstHeading < 0) firstHeading = lines.length;

  // Preamble (before the first numbered section): keep only Key:value rows as an Overview.
  const preamble = lines.slice(0, firstHeading)
    .filter(l => !DIVIDER_RE.test(l.trim()) && l.includes(':'));
  if (preamble.length) {
    sections.push({ titleEn: 'Overview', titleAr: 'نظرة عامة', body: renderBody(preamble), structured: true });
  }

  // Numbered sections.
  let cur: { en: string; ar: string; body: string[] } | null = null;
  const flush = () => { if (cur) sections.push({ titleEn: cur.en, titleAr: cur.ar, body: renderBody(cur.body), structured: true }); };
  for (let i = firstHeading; i < lines.length; i++) {
    const m = lines[i].match(HEADING_RE);
    if (m) { flush(); cur = { en: m[2].trim(), ar: m[3].trim(), body: [] }; continue; }
    if (!cur) continue;
    const l = lines[i];
    if (DIVIDER_RE.test(l.trim())) continue;                 // drop footer dividers
    if (/generated by tss grc/i.test(l)) continue;           // drop footer credit
    cur.body.push(l);
  }
  flush();

  // Fallback — unrecognised format: one faithful pre-wrap section.
  return sections.length ? sections : [textToSection('Document', 'المستند', text)];
}

/** Render packet body lines as light structured HTML: `Key : value` → a two-column
 *  row; nested/list lines → indented text; blank lines → spacing. */
function renderBody(rawLines: string[]): string {
  const lines = [...rawLines];
  while (lines.length && !lines[0].trim()) lines.shift();
  while (lines.length && !lines[lines.length - 1].trim()) lines.pop();

  return lines.map(raw => {
    if (!raw.trim()) return '<div class="sp"></div>';
    const indent = (raw.match(/^\s*/)?.[0].length ?? 0);
    const line = raw.trim();
    const pad = indent >= 5 ? ' style="padding-inline-start:14pt"' : '';
    const kv = line.match(/^([^:]{1,40}?)\s*:\s*(.*)$/);
    if (kv && kv[2] !== '') {
      return `<div class="kv"${pad}><span class="k">${escapeHtml(kv[1])}</span><span class="v">${escapeHtml(kv[2])}</span></div>`;
    }
    // A "[CODE] title / عنوان" line acts as a mini sub-heading within the section.
    const isHead = /^\[.+\]/.test(line);
    return `<div class="ln${isHead ? ' head' : ''}"${pad}>${escapeHtml(line)}</div>`;
  }).join('');
}
