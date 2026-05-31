import { useEffect, useMemo, useState, Fragment } from 'react';
import {
  ShieldCheck, BookOpen, ChevronRight, ChevronDown, CheckCircle2, AlertTriangle, Globe,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import {
  listFrameworks, listAllControls, listSubControlsByControl,
  CTRL_IMPL_TONE, CTRL_MATURITY_TONE,
  type CyberFramework, type CyberControl, type CyberSubControl,
} from '../../lib/frameworkControls';

type TabId = 'ALL' | 'ECC' | 'CSCC' | 'CCC' | 'OTCC' | 'SAMA_CYBER' | 'ISO27001' | 'ISO22301' | 'PCIDSS';

const TAB_TO_FRAMEWORK_NAME: Record<TabId, string | null> = {
  ALL:        null,
  ECC:        'NCA ECC',
  CSCC:       'NCA CSCC',
  CCC:        'NCA CCC',
  OTCC:       'NCA OTCC',
  SAMA_CYBER: 'SAMA Cyber Security Framework',
  ISO27001:   'ISO 27001',
  ISO22301:   'ISO 22301',
  PCIDSS:     'PCI DSS',
};

export function FrameworkControlsPage() {
  const { t, lang } = useI18n();
  const [tab, setTab] = useState<TabId>('ALL');
  const [frameworks, setFrameworks] = useState<CyberFramework[]>([]);
  const [controls, setControls] = useState<CyberControl[]>([]);
  const [loading, setLoading] = useState(true);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());

  useEffect(() => { void reload(); }, []);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — if one of the two endpoints is missing in a dev
    // DB the user can still see the other (e.g. browse controls even when
    // the frameworks list is empty).
    const [fw, ct] = await Promise.all([
      listFrameworks().catch(() => [] as CyberFramework[]),
      listAllControls().catch(() => [] as CyberControl[]),
    ]);
    setFrameworks(fw);
    setControls(ct);
    setLoading(false);
  }

  const fwByName = useMemo(() => {
    const m = new Map<string, CyberFramework>();
    frameworks.forEach(f => m.set(f.name, f));
    return m;
  }, [frameworks]);

  const filtered = useMemo(() => {
    const target = TAB_TO_FRAMEWORK_NAME[tab];
    if (!target) return controls;
    return controls.filter(c => c.frameworkName === target);
  }, [controls, tab]);

  const stats = useMemo(() => {
    const target = TAB_TO_FRAMEWORK_NAME[tab];
    const subset = target ? controls.filter(c => c.frameworkName === target) : controls;
    const fwMeta = target ? fwByName.get(target) : null;
    return {
      total:       subset.length,
      implemented: subset.filter(c => c.implementationStatus === 'IMPLEMENTED').length,
      partial:     subset.filter(c => c.implementationStatus === 'PARTIAL').length,
      expected:    fwMeta?.controlCount ?? null,
    };
  }, [controls, tab, fwByName]);

  const tabs: TabItem<TabId>[] = [
    { id: 'ALL',        label: `${t('fwcTabAll')} (${controls.length})` },
    { id: 'ECC',        label: `ECC (${controls.filter(c => c.frameworkName === 'NCA ECC').length})` },
    { id: 'CSCC',       label: `CSCC (${controls.filter(c => c.frameworkName === 'NCA CSCC').length})` },
    { id: 'CCC',        label: `CCC (${controls.filter(c => c.frameworkName === 'NCA CCC').length})` },
    { id: 'OTCC',       label: `OTCC (${controls.filter(c => c.frameworkName === 'NCA OTCC').length})` },
    { id: 'SAMA_CYBER', label: `SAMA (${controls.filter(c => c.frameworkName === 'SAMA Cyber Security Framework').length})` },
    { id: 'ISO27001',   label: `ISO 27001 (${controls.filter(c => c.frameworkName === 'ISO 27001').length})` },
    { id: 'ISO22301',   label: `ISO 22301 (${controls.filter(c => c.frameworkName === 'ISO 22301').length})` },
    { id: 'PCIDSS',     label: `PCI DSS (${controls.filter(c => c.frameworkName === 'PCI DSS').length})` },
  ];

  function toggle(id: number) {
    const n = new Set(expanded);
    if (n.has(id)) n.delete(id); else n.add(id);
    setExpanded(n);
  }

  const currentFw = TAB_TO_FRAMEWORK_NAME[tab] ? fwByName.get(TAB_TO_FRAMEWORK_NAME[tab]!) : null;

  return (
    <Shell title={t('fwcPageTitle')} sub={t('fwcPageSub')}>
      <PageHeader eyebrow={t('navCyber')} title={t('fwcPageTitle')} sub={t('fwcPageSub')} />

      <div className="kpi-grid">
        <Kpi Icon={BookOpen}      label={t('fwcKpiTotal')}       value={stats.total} tone="primary" />
        <Kpi Icon={CheckCircle2}  label={t('fwcKpiImplemented')} value={stats.implemented} tone="success" />
        <Kpi Icon={AlertTriangle} label={t('fwcKpiPartial')}     value={stats.partial} tone="warning" />
        <Kpi Icon={Globe}         label={t('fwcKpiExpected')}    value={stats.expected ?? '—'} tone="info" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {currentFw && (
        <Card title={`${currentFw.name} (${currentFw.frameworkCode ?? '—'})`} Icon={ShieldCheck}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, fontSize: 13 }}>
            <div>
              <strong>{t('fwcRegulator')}:</strong> {currentFw.regulator ?? '—'}
              <br />
              <strong>{t('fwcVersion')}:</strong> {currentFw.version ?? '—'}
              <br />
              <strong>{t('fwcEffectiveFrom')}:</strong>{' '}
              <span className="mono">{currentFw.effectiveFrom ?? '—'}</span>
              <br />
              <strong>{t('fwcDomainCount')}:</strong> {currentFw.domainCount ?? '—'}
              {' · '}
              <strong>{t('fwcControlCount')}:</strong> {currentFw.controlCount ?? '—'}
            </div>
            <div>
              {currentFw.descriptionEn && (
                <p>{lang === 'ar' && currentFw.descriptionAr ? currentFw.descriptionAr : currentFw.descriptionEn}</p>
              )}
              {currentFw.mandatoryFor && (
                <p style={{ marginTop: 6 }}>
                  <strong>{t('fwcMandatoryFor')}:</strong>{' '}
                  <span className="mono">{currentFw.mandatoryFor}</span>
                </p>
              )}
              {currentFw.referenceUrl && (
                <p style={{ marginTop: 6 }}>
                  <a href={currentFw.referenceUrl} target="_blank" rel="noopener noreferrer">
                    {currentFw.referenceUrl}
                  </a>
                </p>
              )}
            </div>
          </div>
        </Card>
      )}

      <Card title={t('fwcControlsTitle')} Icon={BookOpen}>
        {loading
          ? <EmptyState Icon={BookOpen} title={t('loading')} />
          : filtered.length === 0
            ? <EmptyState Icon={BookOpen} title={t('fwcEmpty')} sub={t('fwcEmptyHint')} />
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th style={{ width: 28 }} />
                    <th>{t('fwcColNumber')}</th>
                    <th>{t('fwcColFramework')}</th>
                    <th>{t('fwcColDomain')}</th>
                    <th>{t('fwcColTitle')}</th>
                    <th>{t('fwcColType')}</th>
                    <th>{t('fwcColMaturity')}</th>
                    <th>{t('fwcColImplementation')}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(c => {
                    const open = expanded.has(c.id);
                    return (
                      <Fragment key={c.id}>
                        <tr>
                          <td>
                            <button onClick={() => toggle(c.id)}
                              style={{ background: 'none', border: 0, cursor: 'pointer', padding: 0 }}>
                              {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                            </button>
                          </td>
                          <td className="mono">{c.controlNumber ?? '—'}</td>
                          <td>{c.frameworkName}</td>
                          <td>{c.domain}</td>
                          <td>{lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn}</td>
                          <td>{c.controlType ?? '—'}</td>
                          <td>
                            {c.maturityLevel
                              ? <Badge tone={CTRL_MATURITY_TONE[c.maturityLevel] ?? 'info'}>{c.maturityLevel}</Badge>
                              : '—'}
                          </td>
                          <td>
                            {c.implementationStatus
                              ? <Badge tone={CTRL_IMPL_TONE[c.implementationStatus] ?? 'info'}>{c.implementationStatus.replace('_', ' ')}</Badge>
                              : '—'}
                          </td>
                        </tr>
                        {open && (
                          <tr>
                            <td colSpan={8} style={{ background: 'var(--muted)' }}>
                              <div style={{ padding: 12, fontSize: 13 }}>
                                {c.procedureEn && <p>{c.procedureEn}</p>}
                                {c.controlOwner && (
                                  <p style={{ marginTop: 6 }}>
                                    <strong>{t('fwcOwner')}:</strong> {c.controlOwner}
                                    {' · '}
                                    <strong>{t('fwcReviewFrequency')}:</strong> {c.reviewFrequency ?? '—'}
                                  </p>
                                )}
                                <SubControlList controlId={c.id} lang={lang} />
                              </div>
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    );
                  })}
                </tbody>
              </table>
            )
        }
      </Card>
    </Shell>
  );
}

// ─── Lazy-loaded sub-controls for an expanded control ─────────────────────────

function SubControlList({ controlId, lang }: { controlId: number; lang: string }) {
  const ar = lang === 'ar';
  const [subs, setSubs] = useState<CyberSubControl[] | null>(null);
  const [err, setErr] = useState(false);

  useEffect(() => {
    let alive = true;
    setSubs(null); setErr(false);
    listSubControlsByControl(controlId)
      .then(d => { if (alive) setSubs(d); })
      .catch(() => { if (alive) setErr(true); });
    return () => { alive = false; };
  }, [controlId]);

  if (err) return <p style={{ opacity: 0.6, marginTop: 8 }}>{ar ? 'تعذّر تحميل الضوابط الفرعية' : 'Could not load sub-controls'}</p>;
  if (subs === null) return <p style={{ opacity: 0.6, marginTop: 8 }}>{ar ? 'جارٍ التحميل…' : 'Loading…'}</p>;
  if (subs.length === 0) return <p style={{ opacity: 0.6, marginTop: 8 }}>{ar ? 'لا توجد ضوابط فرعية' : 'No sub-controls'}</p>;

  return (
    <div style={{ marginTop: 10 }}>
      <div style={{ fontWeight: 700, marginBottom: 6 }}>
        {ar ? 'الضوابط الفرعية' : 'Sub-controls'} ({subs.length})
      </div>
      <table className="grc-table" style={{ width: '100%' }}>
        <thead>
          <tr>
            <th style={{ width: 90 }}>{ar ? 'الرقم' : 'No.'}</th>
            <th>{ar ? 'العنوان' : 'Title'}</th>
            <th>{ar ? 'الدليل الموصى به' : 'Recommended evidence'}</th>
          </tr>
        </thead>
        <tbody>
          {subs.map(s => (
            <tr key={s.id}>
              <td className="mono">{s.subControlNumber ?? '—'}</td>
              <td>{ar && s.titleAr ? s.titleAr : s.titleEn}</td>
              <td style={{ fontSize: 12, opacity: 0.85 }}>{(ar && s.descriptionAr ? s.descriptionAr : s.descriptionEn) ?? '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
