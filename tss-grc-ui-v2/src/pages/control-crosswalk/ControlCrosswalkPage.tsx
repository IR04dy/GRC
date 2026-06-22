import { useMemo, useState, type CSSProperties } from 'react';
import {
  Link2, Filter, Globe, Landmark, Layers, ChevronRight, BookCopy,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import {
  MASTER_CONTROLS, FRAMEWORK_LABEL, FRAMEWORK_IS_SAUDI, STATUS_LABEL, STATUS_TONE,
  aggregateCrosswalk,
  type Framework, type ControlStatus, type MasterControl,
} from '../../lib/controlCrosswalk';

/**
 * ControlCrosswalkPage — Sprint 6 follow-on (B5).
 *
 * "Maintain 1 control · satisfy N frameworks." Master control catalogue on
 * the left (filterable by domain + status); selecting one shows the
 * frameworks × clauses it satisfies as a crosswalk matrix. KPI strip on
 * top counts: total masters, total framework refs, unique frameworks
 * covered, implementation %.
 */
export function ControlCrosswalkPage() {
  const { t, lang } = useI18n();
  const [selected, setSelected] = useState<string>(MASTER_CONTROLS[0].code);
  const [domainFilter, setDomainFilter] = useState<string>('ALL');
  const [statusFilter, setStatusFilter] = useState<ControlStatus | 'ALL'>('ALL');

  const stats = useMemo(() => aggregateCrosswalk(MASTER_CONTROLS), []);

  const domains = useMemo(() => {
    const set = new Set(MASTER_CONTROLS.map(m => lang === 'ar' ? m.domainAr : m.domainEn));
    return Array.from(set).sort();
  }, [lang]);

  const filtered = useMemo(() => MASTER_CONTROLS
    .filter(m => domainFilter === 'ALL' || (lang === 'ar' ? m.domainAr : m.domainEn) === domainFilter)
    .filter(m => statusFilter === 'ALL' || m.status === statusFilter),
  [domainFilter, statusFilter, lang]);

  const current = MASTER_CONTROLS.find(m => m.code === selected) ?? MASTER_CONTROLS[0];

  return (
    <Shell title={t('cwTitle')} sub={t('cwSub')}>
      <PageHeader
        eyebrow={t('cwEyebrow')}
        title={t('cwTitle')}
        sub={t('cwPageSub')}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Layers}   label={t('cwKpiMasters')}      value={String(stats.totalMasters)}      tone="primary" to="/controls/library" />
        <Kpi Icon={Link2}     label={t('cwKpiRefs')}         value={String(stats.totalRefs)}         tone="info" to="/regulatory/library" />
        <Kpi Icon={Globe}     label={t('cwKpiFrameworks')}   value={String(stats.uniqueFrameworks)}  tone="accent" to="/regulatory/library" />
        <Kpi Icon={Link2}     label={t('cwKpiAvgRefs')}      value={String(stats.avgRefsPerMaster)}  tone="primary" />
        <Kpi Icon={Landmark}  label={t('cwKpiImplemented')}  value={`${stats.implementedPct}%`}      tone={stats.implementedPct >= 80 ? 'success' : 'warning'} to="/controls/library" />
      </div>

      {/* ── Framework reach summary ───────────────────────── */}
      <Card title={t('cwReachTitle')} Icon={Globe}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 10 }}>
          {t('cwReachSub')}
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          {(Object.keys(stats.byFramework) as Framework[]).map(f => (
            <div key={f} style={{
              padding: '8px 12px',
              borderRadius: 'var(--r-md)',
              background: FRAMEWORK_IS_SAUDI[f] ? 'var(--primary-50)' : 'var(--surface-2)',
              border: `1px solid ${FRAMEWORK_IS_SAUDI[f] ? 'var(--primary-100, var(--border))' : 'var(--border)'}`,
              fontSize: 12,
              display: 'inline-flex', alignItems: 'center', gap: 8,
            }}>
              <strong style={{ color: FRAMEWORK_IS_SAUDI[f] ? 'var(--primary-700, var(--primary))' : 'var(--ink-1)' }}>
                {lang === 'ar' ? FRAMEWORK_LABEL[f].ar : FRAMEWORK_LABEL[f].en}
              </strong>
              {FRAMEWORK_IS_SAUDI[f] && <Badge tone="primary">{t('cwSaudiFlag')}</Badge>}
              <span style={{ color: 'var(--ink-3)' }}>{stats.byFramework[f]} {t('cwRefsSuffix')}</span>
            </div>
          ))}
        </div>
      </Card>

      <div style={{ display: 'grid', gridTemplateColumns: '380px 1fr', gap: 16, marginTop: 16 }}>
        {/* ── Master list ───────────────────────────────── */}
        <Card padded={false} title={t('cwListTitle')} Icon={Layers}
          action={<Badge tone="primary">{filtered.length}</Badge>}>
          <div style={{ padding: '8px 14px', borderBottom: '1px solid var(--border)', display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            <Filter size={11} color="var(--ink-3)" />
            <select value={domainFilter} onChange={e => setDomainFilter(e.target.value)}
              style={{ padding: '4px 6px', fontSize: 11, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', background: 'var(--surface-2)' }}>
              <option value="ALL">{t('cwFilterDomainAll')}</option>
              {domains.map(d => <option key={d} value={d}>{d}</option>)}
            </select>
            <select value={statusFilter} onChange={e => setStatusFilter(e.target.value as ControlStatus | 'ALL')}
              style={{ padding: '4px 6px', fontSize: 11, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', background: 'var(--surface-2)' }}>
              <option value="ALL">{t('cwFilterStatusAll')}</option>
              {(Object.keys(STATUS_LABEL) as ControlStatus[]).map(s =>
                <option key={s} value={s}>{lang === 'ar' ? STATUS_LABEL[s].ar : STATUS_LABEL[s].en}</option>)}
            </select>
          </div>
          <div style={{ maxHeight: 660, overflowY: 'auto' }}>
            {filtered.map(m => {
              const isSelected = m.code === selected;
              return (
                <button key={m.code} onClick={() => setSelected(m.code)}
                  style={{
                    width: '100%', textAlign: 'start',
                    padding: '12px 14px', border: 'none', cursor: 'pointer',
                    borderBottom: '1px solid var(--border)',
                    background: isSelected ? 'var(--primary-50)' : 'transparent',
                    display: 'flex', flexDirection: 'column', gap: 6,
                  }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span className="mono" style={{ fontSize: 10, fontWeight: 700, color: 'var(--primary-700, var(--primary))' }}>{m.code}</span>
                    <Badge tone={STATUS_TONE[m.status]}>{lang === 'ar' ? STATUS_LABEL[m.status].ar : STATUS_LABEL[m.status].en}</Badge>
                  </div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
                    {lang === 'ar' ? m.titleAr : m.titleEn}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 10, color: 'var(--ink-3)', flexWrap: 'wrap' }}>
                    <span>{lang === 'ar' ? m.domainAr : m.domainEn}</span>
                    <span>·</span>
                    <span>{m.frameworkRefs.length} {t('cwRefsSuffix')}</span>
                  </div>
                </button>
              );
            })}
          </div>
        </Card>

        {/* ── Selected detail ─────────────────────────── */}
        <CrosswalkDetail control={current} t={t} lang={lang} />
      </div>
    </Shell>
  );
}

function CrosswalkDetail({ control, t, lang }: { control: MasterControl; t: (k: string) => string; lang: 'en' | 'ar' }) {
  const saudiCount = control.frameworkRefs.filter(r => FRAMEWORK_IS_SAUDI[r.framework]).length;
  const intlCount = control.frameworkRefs.length - saudiCount;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Card>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8, flexWrap: 'wrap' }}>
          <span className="mono" style={{ fontSize: 11, fontWeight: 700, color: 'var(--primary-700, var(--primary))' }}>{control.code}</span>
          <Badge tone={STATUS_TONE[control.status]}>{lang === 'ar' ? STATUS_LABEL[control.status].ar : STATUS_LABEL[control.status].en}</Badge>
          <Badge tone="info">{lang === 'ar' ? control.domainAr : control.domainEn}</Badge>
          {control.evidenceRef && (
            <Badge tone="neutral"><BookCopy size={10} style={{ marginInlineEnd: 4, verticalAlign: 'middle' }} />{control.evidenceRef}</Badge>
          )}
        </div>
        <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--ink-1)', marginBottom: 8 }}>
          {lang === 'ar' ? control.titleAr : control.titleEn}
        </div>
        <div style={{ fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.6 }}>
          {lang === 'ar' ? control.descriptionAr : control.descriptionEn}
        </div>
      </Card>

      {/* ── Headline reach ────────────────────────── */}
      <div className="kpi-grid">
        <Kpi Icon={Link2}    label={t('cwReachTotal')}     value={String(control.frameworkRefs.length)} tone="primary" />
        <Kpi Icon={Landmark}  label={t('cwReachSaudi')}     value={String(saudiCount)} tone="success" />
        <Kpi Icon={Globe}      label={t('cwReachIntl')}      value={String(intlCount)} tone="info" />
      </div>

      {/* ── Crosswalk table ───────────────────────── */}
      <Card title={t('cwMatrixTitle')} Icon={Link2}>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 10 }}>
          {t('cwMatrixSub')}
        </div>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
          <thead>
            <tr>
              <th style={th}>{t('cwTblFramework')}</th>
              <th style={th}>{t('cwTblClause')}</th>
              <th style={th}>{t('cwTblTitle')}</th>
              <th style={{ ...th, width: 100 }}>{t('cwTblOrigin')}</th>
            </tr>
          </thead>
          <tbody>
            {control.frameworkRefs.map((r, idx) => {
              const saudi = FRAMEWORK_IS_SAUDI[r.framework];
              return (
                <tr key={`${r.framework}-${r.clause}-${idx}`} style={{ borderBottom: '1px dashed var(--border)' }}>
                  <td style={td}>
                    <strong>{lang === 'ar' ? FRAMEWORK_LABEL[r.framework].ar : FRAMEWORK_LABEL[r.framework].en}</strong>
                  </td>
                  <td style={{ ...td, fontFamily: 'var(--font-mono)' }}>{r.clause}</td>
                  <td style={{ ...td, color: 'var(--ink-2)' }}>
                    {lang === 'ar' ? (r.titleAr ?? r.titleEn ?? '—') : (r.titleEn ?? '—')}
                  </td>
                  <td style={td}>
                    <Badge tone={saudi ? 'success' : 'info'}>{saudi ? t('cwSaudiFlag') : t('cwIntlFlag')}</Badge>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        <div style={{
          marginTop: 12,
          padding: 10,
          background: 'var(--success-bg)',
          color: 'var(--success-fg)',
          borderRadius: 'var(--r-sm)',
          fontSize: 12,
          display: 'inline-flex', alignItems: 'center', gap: 8,
        }}>
          <ChevronRight size={14} />
          {t('cwInheritanceNote').replace('{n}', String(control.frameworkRefs.length))}
        </div>
      </Card>
    </div>
  );
}

const th: CSSProperties = {
  padding: '8px 10px',
  textAlign: 'start',
  fontSize: 10,
  textTransform: 'uppercase',
  letterSpacing: '0.05em',
  color: 'var(--ink-3)',
  background: 'var(--surface-2)',
  borderBottom: '2px solid var(--ink-3)',
};
const td: CSSProperties = {
  padding: '10px',
  verticalAlign: 'top',
  lineHeight: 1.5,
};
