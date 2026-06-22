import { useMemo, useState } from 'react';
import { Shield, Printer, Download, ChevronDown, ChevronRight, Landmark, FileText } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { HijriDate } from '../../components/ui/HijriDate';
import {
  ECC_DOMAINS, computeCoverage, buildCsv, STATUS_TONE, STATUS_LABEL,
} from '../../lib/ncaEcc';
import './ncaEcc.css';

/**
 * NcaEccExportPage — Sprint 6.
 *
 * Print-styled NCA ECC-1 self-assessment template ready for regulator
 * submission. Domain → Subdomain → Control hierarchy with status,
 * maturity, evidence reference, and remarks. Two export buttons:
 *   • Print → PDF (browser print dialog with @media print rules)
 *   • Download CSV (NCA Excel template-friendly format)
 *
 * Cover bears organisation identity, CR number, reporting period (Hijri
 * + Gregorian), and the assessor's signature line.
 */
export function NcaEccExportPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const coverage = useMemo(() => computeCoverage(ECC_DOMAINS), []);
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});

  const toggle = (code: string) => setCollapsed(prev => ({ ...prev, [code]: !prev[code] }));

  function exportCsv() {
    const csv = buildCsv();
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `NCA-ECC-self-assessment-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    toast.success(t('ncaCsvDone'));
  }

  return (
    <Shell title={t('ncaTitle')} sub={t('ncaSub')}>
      <div className="nca-actions" style={{
        display: 'flex', justifyContent: 'flex-end', gap: 8, marginBottom: 16,
      }}>
        <Button variant="secondary" Icon={Download} onClick={exportCsv}>{t('ncaDownloadCsv')}</Button>
        <Button variant="primary" Icon={Printer} onClick={() => window.print()}>{t('ncaPrint')}</Button>
      </div>

      <div className="nca-document" dir={lang === 'ar' ? 'rtl' : 'ltr'}>
        {/* ── Cover ─────────────────────────────────────────── */}
        <section className="nca-cover">
          <div className="nca-cover-mark">
            <Shield size={42} />
          </div>
          <div className="nca-cover-eyebrow">NCA ECC-1 · {t('ncaSubmissionTemplate')}</div>
          <h1 className="nca-cover-title">
            {lang === 'ar' ? 'النموذج المُقدَّم للجهة الوطنية للأمن السيبراني' : 'NCA ECC-1 Self-Assessment Submission'}
          </h1>
          <div className="nca-cover-grid">
            <div>
              <span className="nca-meta-label">{t('ncaCoverEntity')}</span>
              <span className="nca-meta-value">{t('brandName')}</span>
            </div>
            <div>
              <span className="nca-meta-label">{t('ncaCoverCr')}</span>
              <span className="nca-meta-value mono">1010299292</span>
            </div>
            <div>
              <span className="nca-meta-label">{t('ncaCoverPeriod')}</span>
              <span className="nca-meta-value">{t('ncaCoverPeriodValue')}</span>
            </div>
            <div>
              <span className="nca-meta-label">{t('ncaCoverGenerated')}</span>
              <span className="nca-meta-value mono">
                <HijriDate iso={new Date().toISOString().slice(0, 10)} layout="inline" mono />
              </span>
            </div>
          </div>
        </section>

        {/* ── Coverage summary ──────────────────────────────── */}
        <section className="nca-section">
          <h2 className="nca-section-title"><Landmark size={18} /> {t('ncaCoverageTitle')}</h2>
          <div className="kpi-grid" style={{ marginBottom: 16 }}>
            <Kpi Icon={FileText} label={t('ncaKpiTotal')}        value={String(coverage.total)}        tone="primary" to="/nca-ecc/filings" />
            <Kpi Icon={FileText} label={t('ncaKpiImplemented')}   value={String(coverage.implemented)}  tone="success" to="/nca-ecc/filings" />
            <Kpi Icon={FileText} label={t('ncaKpiPartial')}       value={String(coverage.partial)}      tone="warning" to="/nca-ecc/filings" />
            <Kpi Icon={FileText} label={t('ncaKpiNotImpl')}       value={String(coverage.notImplemented)} tone={coverage.notImplemented > 0 ? 'danger' : 'success'} to="/nca-ecc/filings" />
            <Kpi Icon={FileText} label={t('ncaKpiScore')}         value={`${coverage.scorePct}%`}        tone={coverage.scorePct >= 80 ? 'success' : 'warning'} to="/nca-ecc/filings" />
            <Kpi Icon={FileText} label={t('ncaKpiMaturity')}      value={String(coverage.avgMaturity)}   tone="info" to="/nca-ecc/filings" />
          </div>
        </section>

        {/* ── Per-domain control matrix ─────────────────────── */}
        {ECC_DOMAINS.map(d => {
          const open = !collapsed[d.code];
          const allControls = d.subdomains.flatMap(s => s.controls);
          const dImpl = allControls.filter(c => c.status === 'IMPLEMENTED').length;
          return (
            <section key={d.code} className="nca-domain">
              <button className="nca-domain-header" onClick={() => toggle(d.code)}>
                {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                <span className="nca-domain-code mono">{d.code}</span>
                <span className="nca-domain-title">{lang === 'ar' ? d.titleAr : d.titleEn}</span>
                <span className="nca-domain-meta">
                  {dImpl} / {allControls.length} {t('ncaImplementedSuffix')}
                </span>
              </button>
              {open && d.subdomains.map(s => (
                <div key={s.code} className="nca-subdomain">
                  <div className="nca-subdomain-header">
                    <span className="nca-subdomain-code mono">{s.code}</span>
                    <span className="nca-subdomain-title">{lang === 'ar' ? s.titleAr : s.titleEn}</span>
                  </div>
                  <table className="nca-table">
                    <thead>
                      <tr>
                        <th style={{ width: '70px' }}>{t('ncaTblCode')}</th>
                        <th>{t('ncaTblTitle')}</th>
                        <th style={{ width: '160px' }}>{t('ncaTblStatus')}</th>
                        <th style={{ width: '90px' }}>{t('ncaTblMaturity')}</th>
                        <th style={{ width: '180px' }}>{t('ncaTblEvidence')}</th>
                        <th>{t('ncaTblRemarks')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {s.controls.map(c => (
                        <tr key={c.code}>
                          <td className="mono">{c.code}</td>
                          <td>
                            <strong>{lang === 'ar' ? c.titleAr : c.titleEn}</strong>
                            <div className="nca-control-desc">
                              {lang === 'ar' ? c.descriptionAr : c.descriptionEn}
                            </div>
                          </td>
                          <td>
                            <Badge tone={STATUS_TONE[c.status]}>
                              {lang === 'ar' ? STATUS_LABEL[c.status].ar : STATUS_LABEL[c.status].en}
                            </Badge>
                          </td>
                          <td className="mono" style={{ textAlign: 'center' }}>
                            {c.maturity != null ? `L${c.maturity}` : '—'}
                          </td>
                          <td className="mono" style={{ fontSize: 11 }}>{c.evidenceRef ?? '—'}</td>
                          <td className="nca-remarks">{c.remarksEn ?? '—'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ))}
            </section>
          );
        })}

        {/* ── Sign-off ─────────────────────────────────────── */}
        <section className="nca-signoff">
          <h2 className="nca-section-title">{t('ncaSignoffTitle')}</h2>
          <p className="nca-signoff-narrative">
            {t('ncaSignoffNarrative')}
          </p>
          <div className="nca-signoff-grid">
            <div className="nca-signoff-cell">
              <div className="nca-signoff-line" />
              <div className="nca-signoff-label">{t('ncaSignoffCiso')}</div>
            </div>
            <div className="nca-signoff-cell">
              <div className="nca-signoff-line" />
              <div className="nca-signoff-label">{t('ncaSignoffCeo')}</div>
            </div>
          </div>
        </section>
      </div>
    </Shell>
  );
}
