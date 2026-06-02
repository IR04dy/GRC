import { useEffect, useMemo, useState } from 'react';
import { Shield, Wrench, Layers, GitBranch } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import {
  listControls, listControlMappings, listControlAssessments, listControlRiskLinks,
  controlStatusTone, controlTypeTone, coverageTone, effectivenessTone, domainLabel,
  type Control, type ControlDomain, type ControlMapping,
  type ControlAssessment, type RiskControlLink,
} from '../../lib/controlLibrary';
import {
  listAuthorityDocuments,
  type AuthorityDocument,
} from '../../lib/regulatoryAuthority';

/**
 * Unified Control Library — Framework Intersection F2.
 *
 * Browser for platform.controls + their citation mappings + their
 * risk-control links + their assessment history. This is the screen
 * that proves the F1+F2 spine — pick any control, see every
 * regulation it satisfies, every risk it mitigates, every assessment
 * that has been run against it.
 */

const DOMAINS: ControlDomain[] = ['RISK', 'CYBER', 'BCM', 'PRIVACY', 'TPRM', 'COMPLIANCE', 'GENERAL'];

export function ControlLibraryPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';

  const [controls, setControls] = useState<Control[]>([]);
  const [docs, setDocs] = useState<AuthorityDocument[]>([]);
  const [domain, setDomain] = useState<ControlDomain | ''>('');
  const [loading, setLoading] = useState(true);

  const [selected, setSelected] = useState<Control | null>(null);
  const [mappings, setMappings] = useState<ControlMapping[]>([]);
  const [riskLinks, setRiskLinks] = useState<RiskControlLink[]>([]);
  const [assessments, setAssessments] = useState<ControlAssessment[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — controls and authority docs come from independent
    // tables; either failing shouldn't blank the whole library.
    const [cs, ds] = await Promise.all([
      listControls(domain || undefined).catch(() => [] as Control[]),
      listAuthorityDocuments().catch(() => [] as AuthorityDocument[]),
    ]);
    setControls(cs);
    setDocs(ds);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, [domain]); // eslint-disable-line react-hooks/exhaustive-deps

  async function selectControl(c: Control) {
    setSelected(c);
    setDetailLoading(true);
    // Per-call .catch() — show whatever sub-tab loaded; a missing
    // assessments endpoint shouldn't hide mappings.
    const [ms, rl, as] = await Promise.all([
      listControlMappings(c.id).catch(() => [] as ControlMapping[]),
      listControlRiskLinks(c.id).catch(() => [] as RiskControlLink[]),
      listControlAssessments(c.id).catch(() => [] as ControlAssessment[]),
    ]);
    setMappings(ms); setRiskLinks(rl); setAssessments(as);
    setDetailLoading(false);
  }

  // Future F5 slice will resolve citationId → document via a dedicated
  // endpoint. For now mappings render the raw citationId — honest about
  // the gap. `docs` is loaded for the KPI strip + future hydration.

  const placeholderDocs = useMemo(() =>
    docs.filter(d => d.isPlaceholder).length, [docs]);

  const byDomain = useMemo(() => {
    const m = new Map<string, number>();
    for (const c of controls) m.set(c.domain, (m.get(c.domain) ?? 0) + 1);
    return m;
  }, [controls]);

  return (
    <Shell
      title={isAr ? 'مكتبة الضوابط الموحّدة' : 'Unified control library'}
      sub={isAr ? 'الضوابط مع تغطيتها للنصوص التنظيمية وارتباطها بالمخاطر'
                : 'Controls with their regulatory coverage and risk linkages'}>
      <PageHeader
        eyebrow={isAr ? 'العمود الفقري للحوكمة' : 'GRC spine'}
        title={isAr ? 'مكتبة الضوابط' : 'Control library'} />

      {/* KPIs */}
      <div className="kpi-strip-4">
        <Kpi Icon={Shield} label={isAr ? 'الضوابط' : 'Controls'} value={controls.length} />
        <Kpi Icon={Layers} label={isAr ? 'مرجعيات تنظيمية' : 'Authority documents'} value={docs.length} />
        <Kpi Icon={Wrench} label={isAr ? 'مرجعيات مؤقتة' : 'Placeholder docs'} value={placeholderDocs}
             tone={placeholderDocs > 0 ? 'warning' : 'success'} />
        <Kpi Icon={Shield} label={isAr ? 'العائلات النشطة' : 'Active domains'} value={byDomain.size} />
      </div>

      {/* Domain chips */}
      <div className="chip-row">
        <button
          type="button"
          onClick={() => setDomain('')}
          aria-label={isAr ? 'تصفية: جميع المجالات' : 'Filter: all domains'}
          aria-pressed={domain === ''}
          style={chip(domain === '')}>
          {isAr ? 'الكل' : 'All'}
          <span className="mono text-2xs chip-row__count">{controls.length}</span>
        </button>
        {DOMAINS.map(d => (
          <button
            key={d}
            type="button"
            onClick={() => setDomain(d)}
            aria-label={(isAr ? 'تصفية بمجال: ' : 'Filter by domain: ') + domainLabel(d, isAr ? 'ar' : 'en')}
            aria-pressed={domain === d}
            style={chip(domain === d)}>
            {domainLabel(d, isAr ? 'ar' : 'en')}
            <span className="mono text-2xs chip-row__count">{byDomain.get(d) ?? 0}</span>
          </button>
        ))}
      </div>

      <div className="list-detail-grid">
        <Card title={isAr ? 'الضوابط' : 'Controls'}
          action={<Badge tone="primary">{controls.length}</Badge>}>
          {loading
            ? <div className="loading-block">{isAr ? 'جاري التحميل…' : 'Loading…'}</div>
            : controls.length === 0
              ? <InlineEmpty>{isAr ? 'لا توجد ضوابط' : 'No controls'}</InlineEmpty>
              : <table className="grc-table">
                  <thead><tr>
                    <th>{isAr ? 'الكود' : 'Code'}</th>
                    <th>{isAr ? 'العنوان' : 'Title'}</th>
                    <th>{isAr ? 'المجال' : 'Domain'}</th>
                    <th>{isAr ? 'النوع' : 'Type'}</th>
                    <th>{isAr ? 'الأتمتة' : 'Automation'}</th>
                    <th>{isAr ? 'الحالة' : 'Status'}</th>
                  </tr></thead>
                  <tbody>
                    {controls.map(c => (
                      <tr
                        key={c.id}
                        className={`table-row-clickable${selected?.id === c.id ? ' table-row--selected' : ''}`}
                        onClick={() => selectControl(c)}>
                        <td className="mono text-2xs">{c.controlCode}</td>
                        <td>
                          <div style={{ fontWeight: 600 }}>
                            {isAr && c.titleAr ? c.titleAr : c.titleEn}
                          </div>
                          <div className="text-2xs text-muted">v{c.version}</div>
                        </td>
                        <td className="text-xs">{domainLabel(c.domain, isAr ? 'ar' : 'en')}</td>
                        <td><Badge tone={controlTypeTone(c.controlType)}>{c.controlType}</Badge></td>
                        <td className="text-2xs">{c.automationLevel}</td>
                        <td><Badge tone={controlStatusTone(c.status)}>{c.status}</Badge></td>
                      </tr>
                    ))}
                  </tbody>
                </table>}
        </Card>

        {selected ? (
          <Card title={`${selected.controlCode} — ${isAr && selected.titleAr ? selected.titleAr : selected.titleEn}`}
            action={
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                <Badge tone={controlStatusTone(selected.status)}>{selected.status}</Badge>
                <Badge tone={controlTypeTone(selected.controlType)}>{selected.controlType}</Badge>
                {/* Sprint 11 — open Risk-Control-Finding pivot for this control. */}
                <button
                  onClick={() => window.open(`/governance/pivot/control/${selected.id}`, '_blank')}
                  title={isAr ? 'محور المخاطر والضوابط والنتائج' : 'Open pivot view'}
                  style={{
                    padding: '4px 8px', fontSize: 12,
                    border: '1px solid var(--border)', borderRadius: 6,
                    background: 'var(--surface)', cursor: 'pointer',
                    color: 'var(--text)', display: 'inline-flex',
                    alignItems: 'center', gap: 4,
                  }}>
                  <GitBranch size={12} />
                  {isAr ? 'فتح المحور' : 'Pivot'}
                </button>
              </div>
            }>
            {detailLoading
              ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>
                  {isAr ? 'جاري التحميل…' : 'Loading…'}
                </div>
              : <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  {selected.descriptionEn && (
                    <div className="text-xs text-muted">
                      {isAr && selected.descriptionAr ? selected.descriptionAr : selected.descriptionEn}
                    </div>
                  )}

                  {/* Mappings */}
                  <Section title={isAr ? 'تغطية النصوص التنظيمية' : 'Regulatory coverage'} count={mappings.length}>
                    {mappings.length === 0
                      ? <InlineEmpty>
                          {isAr ? 'لم يُربط هذا الضابط بأي نص تنظيمي بعد'
                                : 'This control has no citation mappings yet'}
                        </InlineEmpty>
                      : <table className="grc-table">
                          <thead><tr>
                            <th>{isAr ? 'النص' : 'Citation'}</th>
                            <th>{isAr ? 'التغطية' : 'Coverage'}</th>
                            <th>{isAr ? 'المبرر' : 'Rationale'}</th>
                            <th>{isAr ? 'آخر مراجعة' : 'Reviewed'}</th>
                          </tr></thead>
                          <tbody>
                            {mappings.map(m => (
                              <tr key={m.id}>
                                <td className="mono text-2xs">#{m.citationId}</td>
                                <td><Badge size="sm" tone={coverageTone(m.coverage)}>{m.coverage}</Badge></td>
                                <td className="text-xs" style={{ maxWidth: 360 }}>
                                  {(isAr && m.rationaleAr ? m.rationaleAr : m.rationaleEn) ?? '—'}
                                </td>
                                <td className="mono text-2xs">{m.lastReviewedAt?.substring(0, 10) ?? '—'}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>}
                  </Section>

                  {/* Risk-control matrix */}
                  <Section title={isAr ? 'المخاطر المرتبطة' : 'Risk-control matrix'} count={riskLinks.length}>
                    {riskLinks.length === 0
                      ? <InlineEmpty>
                          {isAr ? 'لم يُربط هذا الضابط بأي خطر' : 'No risks linked to this control yet'}
                        </InlineEmpty>
                      : <table className="grc-table">
                          <thead><tr>
                            <th>{isAr ? 'الخطر' : 'Risk'}</th>
                            <th>{isAr ? 'النوع' : 'Link type'}</th>
                            <th>{isAr ? 'الفاعلية' : 'Effectiveness'}</th>
                            <th>{isAr ? 'آخر مراجعة' : 'Reviewed'}</th>
                          </tr></thead>
                          <tbody>
                            {riskLinks.map(l => (
                              <tr key={l.id}>
                                <td className="mono text-2xs">#{l.riskId}</td>
                                <td><Badge size="sm" tone="info">{l.linkType}</Badge></td>
                                <td><Badge size="sm" tone={effectivenessTone(l.effectiveness)}>{l.effectiveness ?? '—'}</Badge></td>
                                <td className="mono text-2xs">{l.lastReviewedAt?.substring(0, 10) ?? '—'}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>}
                  </Section>

                  {/* Assessments */}
                  <Section title={isAr ? 'التقييمات' : 'Assessments'} count={assessments.length}>
                    {assessments.length === 0
                      ? <InlineEmpty>
                          {isAr ? 'لا توجد تقييمات' : 'No assessments yet'}
                        </InlineEmpty>
                      : <table className="grc-table">
                          <thead><tr>
                            <th>{isAr ? 'التاريخ' : 'Date'}</th>
                            <th>{isAr ? 'الفاعلية' : 'Effectiveness'}</th>
                            <th>{isAr ? 'المُقيِّم' : 'Assessor'}</th>
                            <th>{isAr ? 'الحالة' : 'Workflow'}</th>
                          </tr></thead>
                          <tbody>
                            {assessments.map(a => (
                              <tr key={a.id}>
                                <td className="mono text-2xs">{a.assessmentDate}</td>
                                <td><Badge size="sm" tone={effectivenessTone(a.effectiveness)}>{a.effectiveness}</Badge></td>
                                <td className="text-xs">{a.assessorName ?? '—'}</td>
                                <td><Badge size="sm" tone="neutral">{a.workflowStatus}</Badge></td>
                              </tr>
                            ))}
                          </tbody>
                        </table>}
                  </Section>
                </div>}
          </Card>
        ) : (
          <Card title={isAr ? 'تفاصيل الضابط' : 'Control detail'}>
            <InlineEmpty>
              <div style={{ fontWeight: 600, marginBottom: 4 }}>
                {isAr ? 'اختر ضابطاً لعرض تفاصيله' : 'Pick a control to inspect it'}
              </div>
              <div className="text-2xs text-muted">
                {isAr ? 'سترى التغطية التنظيمية والمخاطر المرتبطة والتقييمات'
                      : "You'll see regulatory coverage, linked risks, and assessments"}
              </div>
            </InlineEmpty>
          </Card>
        )}
      </div>
    </Shell>
  );
}

function Section({ title, count, children }: { title: string; count: number; children: React.ReactNode }) {
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
        <h4 style={{ margin: 0, fontSize: 13, fontWeight: 600 }}>{title}</h4>
        <Badge tone="primary">{count}</Badge>
      </div>
      {children}
    </div>
  );
}

function chip(active: boolean): React.CSSProperties {
  return {
    padding: '6px 12px', fontSize: 12,
    border: '1px solid var(--border)',
    borderRadius: 999,
    background: active ? 'var(--primary)' : 'var(--surface-1)',
    color: active ? '#fff' : 'var(--ink-1)',
    cursor: 'pointer',
    display: 'inline-flex', alignItems: 'center', gap: 4,
  };
}
