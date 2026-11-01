// PDF export and print formatting for AI-generated bilingual policy drafts.
//
// Follows the packetPrint design: renders semantic A4 HTML with print CSS
// and calls window.print() so the browser generates a high-quality PDF via
// "Save as PDF", supporting Arabic RTL, English LTR, and bilingual formats.

import { loadWhiteLabel } from './whiteLabel';
import type { ControlCatalogItem } from './policy';
import type { GeneratedPolicyContent, PolicyDraftResponse } from './policyDrafts';

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function markdownToHtml(md: string): string {
  if (!md) return '';
  const lines = md.split(/\r?\n/);
  const out: string[] = [];
  let inList: 'ul' | 'ol' | null = null;

  function closeList() {
    if (inList === 'ul') out.push('</ul>');
    if (inList === 'ol') out.push('</ol>');
    inList = null;
  }

  function inlineFormat(text: string): string {
    let t = escapeHtml(text);
    // **bold**
    t = t.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    // *italic*
    t = t.replace(/\*(.+?)\*/g, '<em>$1</em>');
    // `code`
    t = t.replace(/`([^`]+)`/g, '<code>$1</code>');
    return t;
  }

  for (const rawLine of lines) {
    const line = rawLine.trim();

    if (!line) {
      closeList();
      continue;
    }

    // Headings
    if (line.startsWith('### ')) {
      closeList();
      out.push(`<h3>${inlineFormat(line.slice(4))}</h3>`);
      continue;
    }
    if (line.startsWith('## ')) {
      closeList();
      out.push(`<h2>${inlineFormat(line.slice(3))}</h2>`);
      continue;
    }
    if (line.startsWith('# ')) {
      closeList();
      out.push(`<h1>${inlineFormat(line.slice(2))}</h1>`);
      continue;
    }

    // Horizontal Rule
    if (line === '---' || line === '***') {
      closeList();
      out.push('<hr class="divider" />');
      continue;
    }

    // Unordered list
    if (line.startsWith('- ') || line.startsWith('* ')) {
      if (inList !== 'ul') {
        closeList();
        out.push('<ul>');
        inList = 'ul';
      }
      out.push(`<li>${inlineFormat(line.slice(2))}</li>`);
      continue;
    }

    // Ordered list
    const olMatch = line.match(/^(\d+)\.\s+(.*)$/);
    if (olMatch) {
      if (inList !== 'ol') {
        closeList();
        out.push('<ol>');
        inList = 'ol';
      }
      out.push(`<li>${inlineFormat(olMatch[2])}</li>`);
      continue;
    }

    // Regular paragraph
    closeList();
    out.push(`<p>${inlineFormat(line)}</p>`);
  }

  closeList();
  return out.join('\n');
}

export function exportPolicyDraftPdf(
  draft: PolicyDraftResponse,
  lang: 'en' | 'ar' | 'bilingual' = 'bilingual',
  controls: ControlCatalogItem[] = []
): void {
  const brand = loadWhiteLabel();
  const content: GeneratedPolicyContent | undefined = draft.edited ?? draft.generated;
  const orgName = lang === 'ar' ? brand.organisationNameAr : brand.organisationNameEn;
  const primary = brand.primaryHex || '#1e40af';
  const isAr = lang === 'ar';
  const docTitle = content
    ? (isAr ? content.titleAr : content.titleEn) || draft.policyType
    : draft.policyType;

  const controlMap = new Map<number, ControlCatalogItem>();
  for (const c of controls) {
    controlMap.set(c.id, c);
  }

  const citedControls = (content?.citedControlIds ?? []).map(id => controlMap.get(id) || {
    id,
    code: `#${id}`,
    nameEn: `Control ${id}`,
    nameAr: `ضابط ${id}`,
  });

  const html = `<!doctype html>
<html lang="${isAr ? 'ar' : 'en'}" dir="${isAr ? 'rtl' : 'ltr'}">
<head>
  <meta charset="utf-8" />
  <title>${escapeHtml(draft.externalId)} - ${escapeHtml(docTitle)}</title>
  <style>
    @page {
      size: A4;
      margin: 16mm 18mm 20mm 18mm;
    }
    * { box-sizing: border-box; }
    html, body {
      margin: 0;
      padding: 0;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Noto Sans Arabic', 'Cairo', Arial, sans-serif;
      color: #1f2937;
      line-height: 1.6;
      background: #ffffff;
      -webkit-print-color-adjust: exact;
      print-color-adjust: exact;
    }
    body { font-size: 10.5pt; }

    /* Screen-only top action bar */
    .no-print-toolbar {
      position: sticky;
      top: 0;
      background: #111827;
      color: #ffffff;
      padding: 10px 20px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      z-index: 1000;
      box-shadow: 0 2px 8px rgba(0,0,0,0.2);
    }
    .no-print-toolbar button {
      cursor: pointer;
      padding: 8px 16px;
      font-size: 13px;
      font-weight: 600;
      border-radius: 6px;
      border: none;
      transition: opacity 0.2s;
    }
    .btn-print {
      background: #3b82f6;
      color: white;
    }
    .btn-close {
      background: #374151;
      color: white;
      margin-left: 8px;
    }

    .container {
      max-width: 820px;
      margin: 0 auto;
      padding: 24px 20px;
    }

    /* Document Header */
    .header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      border-bottom: 3px solid ${primary};
      padding-bottom: 14px;
      margin-bottom: 20px;
    }
    .brand-block {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .brand-logo {
      width: 48px;
      height: 48px;
      border-radius: 8px;
      background: ${primary};
      display: flex;
      align-items: center;
      justify-content: center;
      color: #ffffff;
      font-weight: 800;
      font-size: 20px;
      overflow: hidden;
    }
    .brand-logo img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }
    .org-title {
      font-size: 14pt;
      font-weight: 700;
      color: #111827;
      margin: 0;
    }
    .org-sub {
      font-size: 9pt;
      color: #6b7280;
      margin: 2px 0 0 0;
    }
    .meta-box {
      text-align: ${isAr ? 'left' : 'right'};
      font-size: 8.5pt;
      color: #4b5563;
    }
    .ref-badge {
      font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
      font-weight: 700;
      font-size: 10pt;
      color: ${primary};
    }
    .classification-tag {
      display: inline-block;
      margin-top: 4px;
      padding: 2px 8px;
      background: #fef3c7;
      color: #92400e;
      border-radius: 4px;
      font-weight: 700;
      font-size: 8pt;
      letter-spacing: 0.05em;
    }

    /* Meta Table */
    .spec-table {
      width: 100%;
      border-collapse: collapse;
      margin-bottom: 22px;
      background: #f9fafb;
      border: 1px solid #e5e7eb;
      border-radius: 6px;
      overflow: hidden;
    }
    .spec-table td {
      padding: 7px 12px;
      font-size: 9pt;
      border-bottom: 1px solid #e5e7eb;
      vertical-align: top;
    }
    .spec-table tr:last-child td { border-bottom: none; }
    .spec-table .k {
      width: 25%;
      font-weight: 600;
      color: #374151;
      background: #f3f4f6;
    }
    .spec-table .v {
      color: #111827;
    }

    /* Document Titles */
    .doc-main-title {
      font-size: 18pt;
      font-weight: 800;
      color: #111827;
      margin: 16px 0 6px 0;
      line-height: 1.3;
    }
    .doc-sub-title {
      font-size: 14pt;
      font-weight: 700;
      color: #4b5563;
      margin: 0 0 16px 0;
    }

    /* Callout Summary Box */
    .summary-card {
      background: #eff6ff;
      border-left: 4px solid #3b82f6;
      padding: 12px 16px;
      border-radius: 4px;
      margin-bottom: 22px;
    }
    [dir="rtl"] .summary-card {
      border-left: none;
      border-right: 4px solid #3b82f6;
    }
    .summary-card h4 {
      margin: 0 0 6px 0;
      font-size: 10pt;
      color: #1e40af;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .summary-card p {
      margin: 0;
      font-size: 9.5pt;
      line-height: 1.65;
      color: #1e3a8a;
    }

    /* Section Styling */
    .policy-section {
      margin-bottom: 26px;
    }
    .policy-section h2 {
      font-size: 12.5pt;
      color: ${primary};
      border-bottom: 1.5px solid #e5e7eb;
      padding-bottom: 4px;
      margin: 20px 0 10px 0;
      font-weight: 700;
    }
    .policy-section h3 {
      font-size: 11pt;
      color: #111827;
      margin: 14px 0 6px 0;
      font-weight: 600;
    }
    .policy-section p {
      margin: 0 0 10px 0;
      line-height: 1.65;
    }
    .policy-section ul, .policy-section ol {
      margin: 6px 0 12px 0;
      padding-inline-start: 22px;
    }
    .policy-section li {
      margin-bottom: 6px;
      line-height: 1.6;
    }
    .divider {
      border: none;
      border-top: 1px dashed #d1d5db;
      margin: 28px 0;
    }

    /* Controls & Citations */
    .citations-grid {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      margin-top: 6px;
    }
    .citation-badge {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      background: #e0e7ff;
      color: #3730a3;
      padding: 3px 8px;
      border-radius: 4px;
      font-size: 8.5pt;
    }
    .citation-badge strong { font-family: ui-monospace, monospace; }

    /* Sign-off Table */
    .signoff-table {
      width: 100%;
      border-collapse: collapse;
      margin-top: 30px;
      page-break-inside: avoid;
    }
    .signoff-table th, .signoff-table td {
      border: 1px solid #d1d5db;
      padding: 10px 14px;
      font-size: 9pt;
      text-align: ${isAr ? 'right' : 'left'};
    }
    .signoff-table th {
      background: #f3f4f6;
      font-weight: 700;
      color: #374151;
    }
    .sig-line {
      height: 28px;
    }

    .footer {
      margin-top: 36px;
      padding-top: 12px;
      border-top: 1px solid #e5e7eb;
      font-size: 8pt;
      color: #9ca3af;
      text-align: center;
    }

    @media print {
      .no-print { display: none !important; }
      .container { padding: 0; max-width: 100%; }
      .page-break { page-break-before: always; }
    }
  </style>
</head>
<body>
  <div class="no-print no-print-toolbar">
    <div style="font-weight: 600; font-size: 14px;">
      ${escapeHtml(draft.externalId)} · ${escapeHtml(docTitle)}
    </div>
    <div>
      <button class="btn-print" onclick="window.print()">
        ${isAr ? 'طباعة / حفظ بتنسيق PDF' : 'Print / Save as PDF'}
      </button>
      <button class="btn-close" onclick="window.close()">
        ${isAr ? 'إغلاق' : 'Close'}
      </button>
    </div>
  </div>

  <div class="container">
    <!-- Header -->
    <div class="header">
      <div class="brand-block">
        <div class="brand-logo">
          ${brand.logoDataUrl
            ? `<img src="${brand.logoDataUrl}" alt="${escapeHtml(orgName)}" />`
            : `<span>TSS</span>`}
        </div>
        <div>
          <h1 class="org-title">${escapeHtml(orgName || 'Trusted Security Solutions')}</h1>
          <p class="org-sub">
            ${isAr
              ? 'نظام حوكمة وإدارة المخاطر والالتزام (GRC) — استوديو صياغة السياسات الذكي'
              : 'Enterprise Governance, Risk & Compliance Platform — AI Policy Studio'}
          </p>
        </div>
      </div>
      <div class="meta-box">
        <div class="ref-badge">${escapeHtml(draft.externalId)}</div>
        <div class="classification-tag">
          ${isAr ? 'داخلي ومقيد — مسودة نظامية' : 'INTERNAL & RESTRICTED · DRAFT'}
        </div>
        <div style="margin-top: 4px;">
          ${escapeHtml(draft.createdAt ? new Date(draft.createdAt).toLocaleDateString() : new Date().toLocaleDateString())}
        </div>
      </div>
    </div>

    <!-- Metadata Grid -->
    <table class="spec-table">
      <tr>
        <td class="k">${isAr ? 'موضوع / نوع السياسة' : 'Policy Topic / Type'}</td>
        <td class="v" style="font-weight: 600;">${escapeHtml(draft.policyType)}</td>
        <td class="k">${isAr ? 'الفئة' : 'Category'}</td>
        <td class="v">${escapeHtml(draft.category)}</td>
      </tr>
      <tr>
        <td class="k">${isAr ? 'الأسلوب الصياغي' : 'Drafting Tone'}</td>
        <td class="v">${escapeHtml(draft.tone)}</td>
        <td class="k">${isAr ? 'دورية المراجعة المقترحة' : 'Suggested Review'}</td>
        <td class="v">${escapeHtml(content?.suggestedReviewCadence || 'ANNUAL')}</td>
      </tr>
      <tr>
        <td class="k">${isAr ? 'نطاق السريان' : 'Applicability Scope'}</td>
        <td class="v" colspan="3">${escapeHtml(draft.request?.applicabilityScope || (isAr ? 'جميع العاملين والأنظمة' : 'All employees, contractors, and cloud workloads'))}</td>
      </tr>
      <tr>
        <td class="k">${isAr ? 'طالب الصياغة' : 'Requested By'}</td>
        <td class="v">${escapeHtml(draft.requestedBy)}</td>
        <td class="k">${isAr ? 'حالة المسودة' : 'Draft Status'}</td>
        <td class="v">${escapeHtml(draft.status)}</td>
      </tr>
      ${draft.provider ? `
      <tr>
        <td class="k">${isAr ? 'النموذج ومحرك الذكاء' : 'AI Provider & Model'}</td>
        <td class="v" colspan="3" style="font-family: ui-monospace, monospace; font-size: 8.5pt;">
          ${escapeHtml(draft.provider)} / ${escapeHtml(draft.model || '')} (${draft.inputTokens} in / ${draft.outputTokens} out · ${Math.round(draft.latencyMs / 1000)}s)
        </td>
      </tr>
      ` : ''}
    </table>

    ${content ? `
      <!-- English Section (if bilingual or en) -->
      ${(lang === 'en' || lang === 'bilingual') ? `
        <div class="policy-section" dir="ltr" lang="en">
          <div class="doc-main-title">${escapeHtml(content.titleEn)}</div>
          ${content.summaryEn ? `
            <div class="summary-card">
              <h4>Executive Summary</h4>
              <p>${escapeHtml(content.summaryEn)}</p>
            </div>
          ` : ''}
          <div class="policy-body">
            ${markdownToHtml(content.contentMdEn)}
          </div>
        </div>
      ` : ''}

      ${(lang === 'bilingual') ? '<div class="divider page-break"></div>' : ''}

      <!-- Arabic Section (if bilingual or ar) -->
      ${(lang === 'ar' || lang === 'bilingual') ? `
        <div class="policy-section" dir="rtl" lang="ar">
          <div class="doc-main-title">${escapeHtml(content.titleAr)}</div>
          ${content.summaryAr ? `
            <div class="summary-card">
              <h4>الملخص التنفيذي</h4>
              <p>${escapeHtml(content.summaryAr)}</p>
            </div>
          ` : ''}
          <div class="policy-body">
            ${markdownToHtml(content.contentMdAr)}
          </div>
        </div>
      ` : ''}

      <!-- Grounded Citations & Controls -->
      ${citedControls.length > 0 ? `
        <div class="policy-section" style="margin-top: 24px;">
          <h2>${isAr ? 'الضوابط التنظيمية المستند إليها (Grounded Controls)' : 'Grounded Regulatory Controls & Citations'}</h2>
          <div class="citations-grid">
            ${citedControls.map(c => `
              <div class="citation-badge">
                <strong>${escapeHtml(c.code)}</strong>
                <span>· ${escapeHtml(isAr && c.nameAr ? c.nameAr : c.nameEn)}</span>
              </div>
            `).join('')}
          </div>
        </div>
      ` : ''}

      <!-- Regulatory Assumptions -->
      ${content.assumptions && content.assumptions.length > 0 ? `
        <div class="policy-section">
          <h2>${isAr ? 'الافتراضات التنظيمية والأسس المرجعية' : 'Regulatory Assumptions & Baseline Scope'}</h2>
          <ul>
            ${content.assumptions.map(a => `<li>${escapeHtml(a)}</li>`).join('')}
          </ul>
        </div>
      ` : ''}
    ` : `
      <div style="padding: 40px; text-align: center; color: #6b7280;">
        ${isAr ? 'لا يوجد محتوى سياسة مولّد متاح في هذه المسودة.' : 'No generated policy content available for this draft.'}
      </div>
    `}

    <!-- Human Governance Sign-off Box -->
    <table class="signoff-table">
      <thead>
        <tr>
          <th>${isAr ? 'الدور / الصفة' : 'Governance Role'}</th>
          <th>${isAr ? 'الاسم والمنصب' : 'Name & Title'}</th>
          <th>${isAr ? 'التاريخ' : 'Date'}</th>
          <th>${isAr ? 'التوقيع / الاعتماد' : 'Signature / Decision'}</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td>${isAr ? 'طالب الصياغة' : 'Requester'}</td>
          <td>${escapeHtml(draft.requestedBy)}</td>
          <td>${escapeHtml(draft.createdAt ? new Date(draft.createdAt).toLocaleDateString() : '—')}</td>
          <td class="sig-line">${isAr ? 'مكتمل عبر الذكاء الاصطناعي' : 'Generated via AI Studio'}</td>
        </tr>
        <tr>
          <td>${isAr ? 'مراجع الامتثال' : 'Compliance Reviewer'}</td>
          <td>${escapeHtml(draft.reviewedBy || (isAr ? 'قيد المراجعة' : 'Pending Review'))}</td>
          <td>${escapeHtml(draft.reviewedAt ? new Date(draft.reviewedAt).toLocaleDateString() : '—')}</td>
          <td class="sig-line">${draft.status === 'ACCEPTED' ? (isAr ? 'معتمد ومقبول' : 'ACCEPTED') : (draft.status === 'REJECTED' ? (isAr ? 'مرفوض' : 'REJECTED') : '')}</td>
        </tr>
        <tr>
          <td>${isAr ? 'مسؤول أمن المعلومات / مالك السياسة' : 'CISO / Policy Owner'}</td>
          <td></td>
          <td></td>
          <td class="sig-line"></td>
        </tr>
      </tbody>
    </table>

    <!-- Footer -->
    <div class="footer">
      ${isAr
        ? 'تم استخراج هذا التقرير تلقائياً من منصة TSS GRC — الخدمات الموثوقة للأمن السيبراني. جميع الحقوق محفوظة.'
        : 'Confidential & Proprietary — Trusted Security Solutions GRC Platform. Generated automatically.'}
    </div>
  </div>
</body>
</html>`;

  const w = window.open('', '_blank', 'width=960,height=1100');
  if (!w) {
    // If pop-up is blocked, alert fallback
    alert(isAr
      ? 'يرجى السماح بالنوافذ المنبثقة لتحميل وطباعة ملف PDF.'
      : 'Please allow pop-ups to download and print the PDF.');
    return;
  }
  w.document.write(html);
  w.document.close();
  w.onload = () => {
    setTimeout(() => {
      w.print();
    }, 250);
  };
}
