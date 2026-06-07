import { useEffect, useMemo, useRef, useState } from 'react';
import { Shield, Wrench, Layers, GitBranch, Plus, Link2, BookText } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { PillTabs } from '../../components/ui/PillTabs';
import { ApiError, riskApi, type RiskResponse } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  listControls, listControlMappings, listControlAssessments, listControlRiskLinks,
  createControlAssessment, linkRiskToControl, createMapping,
  controlStatusTone, controlTypeTone, coverageTone, effectivenessTone, domainLabel,
  type Control, type ControlDomain, type ControlMapping,
  type ControlAssessment, type RiskControlLink, type Effectiveness, type RiskLinkType,
  type Coverage,
} from '../../lib/controlLibrary';
import {
  listAuthorityDocuments, listAuthorityDocumentCitations,
  type AuthorityDocument, type AuthorityDocumentCitation,
} from '../../lib/regulatoryAuthority';

/** A flattened citation option — carries its parent document's code for display. */
interface CitOpt {
  value: string;        // citation id as string
  docCode: string;
  citationCode: string;
  titleEn: string;
  titleAr?: string;
}

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
  const toast = useToast();
  const demoMode = !isAuthenticated();

  const [openAssess, setOpenAssess] = useState(false);
  const [openLink, setOpenLink] = useState(false);
  const [openMap, setOpenMap] = useState(false);

  const [controls, setControls] = useState<Control[]>([]);
  const [docs, setDocs] = useState<AuthorityDocument[]>([]);
  const [risks, setRisks] = useState<RiskResponse[]>([]);
  const [citations, setCitations] = useState<CitOpt[]>([]);
  const citationsLoadedRef = useRef(false);
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
    const [cs, ds, rs] = await Promise.all([
      listControls(domain || undefined).catch(() => [] as Control[]),
      listAuthorityDocuments().catch(() => [] as AuthorityDocument[]),
      riskApi.list(undefined, 0, 200).then(p => p.content).catch(() => [] as RiskResponse[]),
    ]);
    setControls(cs);
    setDocs(ds);
    setRisks(rs);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, [domain]); // eslint-disable-line react-hooks/exhaustive-deps

  // Citations live one-per-document; the mapping picker needs a flat pool.
  // Load it once (across all authority docs) the first time docs arrive —
  // powers both the searchable citation picker and the coverage table's
  // doc·code hydration. ~19 GETs once per mount; cached thereafter.
  useEffect(() => {
    if (citationsLoadedRef.current || docs.length === 0) return;
    citationsLoadedRef.current = true;
    void (async () => {
      const lists = await Promise.all(docs.map(d =>
        listAuthorityDocumentCitations(d.id)
          .then(cs => ({ d, cs }))
          .catch(() => ({ d, cs: [] as AuthorityDocumentCitation[] })),
      ));
      const opts: CitOpt[] = [];
      for (const { d, cs } of lists) {
        for (const c of cs) {
          if (c.id == null) continue;
          opts.push({
            value: String(c.id), docCode: d.code, citationCode: c.citationCode,
            titleEn: c.titleEn, titleAr: c.titleAr,
          });
        }
      }
      setCitations(opts);
    })();
  }, [docs]);

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

  const today = new Date().toISOString().slice(0, 10);
  const EFFECTIVENESS: Effectiveness[] = ['EFFECTIVE', 'NEEDS_IMPROVEMENT', 'INEFFECTIVE', 'NOT_TESTED'];
  const effLabel = (e: Effectiveness) => {
    const en: Record<Effectiveness, string> = {
      EFFECTIVE: 'Effective', NEEDS_IMPROVEMENT: 'Needs improvement',
      INEFFECTIVE: 'Ineffective', NOT_TESTED: 'Not tested',
    };
    const ar: Record<Effectiveness, string> = {
      EFFECTIVE: 'فعّال', NEEDS_IMPROVEMENT: 'يحتاج تحسيناً',
      INEFFECTIVE: 'غير فعّال', NOT_TESTED: 'لم يُختبر',
    };
    return (isAr ? ar : en)[e];
  };

  const assessFields: FieldDef[] = [
    { key: 'effectiveness', label: isAr ? 'الفاعلية' : 'Effectiveness', kind: 'select', required: true, span: 6,
      options: EFFECTIVENESS.map(e => ({ value: e, label: effLabel(e) })) },
    { key: 'assessmentDate', label: isAr ? 'تاريخ التقييم' : 'Assessment date', kind: 'date', span: 6 },
    { key: 'assessorName', label: isAr ? 'اسم المُقيِّم' : 'Assessor name', kind: 'text', span: 12,
      placeholder: isAr ? 'مثال: فريق التدقيق الداخلي' : 'e.g. Internal Audit team' },
    { key: 'notesEn', label: isAr ? 'ملاحظات (إنجليزي)' : 'Notes (English)', kind: 'textarea', rows: 3, span: 12 },
    { key: 'notesAr', label: isAr ? 'ملاحظات (عربي)' : 'Notes (Arabic)', kind: 'textarea', rows: 3, span: 12 },
  ];
  const assessInitial = {
    effectiveness: 'EFFECTIVE', assessmentDate: today,
    assessorName: '', notesEn: '', notesAr: '',
  };

  async function submitAssessment(v: Record<string, string>) {
    if (!selected) return;
    const assessmentDate = (v.assessmentDate || '').trim();
    if (!v.effectiveness || !assessmentDate) {
      toast.danger(isAr ? 'الفاعلية وتاريخ التقييم حقول مطلوبة'
                        : 'Effectiveness and assessment date are required');
      return;
    }
    try {
      await createControlAssessment({
        controlId: selected.id,
        effectiveness: v.effectiveness as Effectiveness,
        assessmentDate,
        assessorName: (v.assessorName || '').trim() || undefined,
        notesEn: (v.notesEn || '').trim() || undefined,
        notesAr: (v.notesAr || '').trim() || undefined,
        workflowStatus: 'DRAFT',
      });
      toast.success(isAr ? `تم تسجيل تقييم للضابط ${selected.controlCode}`
                        : `Assessment recorded for ${selected.controlCode}`);
      setOpenAssess(false);
      await selectControl(selected);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تسجيل التقييم' : 'Failed to record assessment'));
    }
  }

  // ─── Link a risk to this control ────────────────────────────────
  const LINK_TYPES: RiskLinkType[] = ['MITIGATES', 'DETECTS', 'TRANSFERS', 'AVOIDS', 'COMPENSATES'];
  const linkTypeLabel = (l: RiskLinkType) => {
    const en: Record<RiskLinkType, string> = {
      MITIGATES: 'Mitigates', DETECTS: 'Detects', TRANSFERS: 'Transfers',
      AVOIDS: 'Avoids', COMPENSATES: 'Compensates',
    };
    const ar: Record<RiskLinkType, string> = {
      MITIGATES: 'يخفّف', DETECTS: 'يكشف', TRANSFERS: 'يحوّل',
      AVOIDS: 'يتجنّب', COMPENSATES: 'يعوّض',
    };
    return (isAr ? ar : en)[l];
  };

  const riskById = useMemo(() => {
    const m = new Map<number, RiskResponse>();
    for (const r of risks) m.set(r.id, r);
    return m;
  }, [risks]);

  const linkFields: FieldDef[] = [
    { key: 'riskId', label: isAr ? 'الخطر' : 'Risk', kind: 'select-searchable', required: true, span: 12,
      placeholder: isAr ? 'ابحث عن خطر بالكود أو العنوان…' : 'Search a risk by code or title…',
      options: risks.map(r => ({ value: String(r.id), label: `${r.riskCode} — ${isAr && r.titleAr ? r.titleAr : r.titleEn}` })) },
    { key: 'linkType', label: isAr ? 'نوع الارتباط' : 'Link type', kind: 'select', required: true, span: 6,
      options: LINK_TYPES.map(l => ({ value: l, label: linkTypeLabel(l) })) },
    { key: 'effectiveness', label: isAr ? 'الفاعلية' : 'Effectiveness', kind: 'select', span: 6,
      options: [{ value: '', label: isAr ? '— (اختياري)' : '— (optional)' },
                ...EFFECTIVENESS.map(e => ({ value: e, label: effLabel(e) }))] },
    { key: 'rationaleEn', label: isAr ? 'المبرر (إنجليزي)' : 'Rationale (English)', kind: 'textarea', rows: 2, span: 12 },
    { key: 'rationaleAr', label: isAr ? 'المبرر (عربي)' : 'Rationale (Arabic)', kind: 'textarea', rows: 2, span: 12 },
  ];
  const linkInitial = {
    riskId: '', linkType: 'MITIGATES', effectiveness: '', rationaleEn: '', rationaleAr: '',
  };

  async function submitLink(v: Record<string, string>) {
    if (!selected) return;
    const riskId = Number(v.riskId);
    if (!riskId || Number.isNaN(riskId)) {
      toast.danger(isAr ? 'يجب اختيار خطر لربطه' : 'Pick a risk to link');
      return;
    }
    try {
      await linkRiskToControl({
        riskId,
        controlId: selected.id,
        linkType: (v.linkType || 'MITIGATES') as RiskLinkType,
        effectiveness: v.effectiveness ? (v.effectiveness as Effectiveness) : undefined,
        rationaleEn: (v.rationaleEn || '').trim() || undefined,
        rationaleAr: (v.rationaleAr || '').trim() || undefined,
      });
      const r = riskById.get(riskId);
      toast.success(isAr ? `تم ربط ${r ? r.riskCode : 'الخطر'} بالضابط ${selected.controlCode}`
                        : `Linked ${r ? r.riskCode : 'risk'} to ${selected.controlCode}`);
      setOpenLink(false);
      await selectControl(selected);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر ربط الخطر' : 'Failed to link risk'));
    }
  }

  // ─── Map this control to a regulatory citation ──────────────────
  const COVERAGES: Coverage[] = ['FULL', 'PARTIAL', 'COMPENSATING'];
  const coverageLabel = (c: Coverage) => {
    const en: Record<Coverage, string> = { FULL: 'Full', PARTIAL: 'Partial', COMPENSATING: 'Compensating' };
    const ar: Record<Coverage, string> = { FULL: 'كاملة', PARTIAL: 'جزئية', COMPENSATING: 'تعويضية' };
    return (isAr ? ar : en)[c];
  };

  const citationById = useMemo(() => {
    const m = new Map<number, CitOpt>();
    for (const c of citations) m.set(Number(c.value), c);
    return m;
  }, [citations]);

  const mapFields: FieldDef[] = [
    { key: 'citationId', label: isAr ? 'النص التنظيمي' : 'Citation', kind: 'select-searchable', required: true, span: 12,
      placeholder: isAr ? 'ابحث عن نص بالكود أو العنوان…' : 'Search a citation by code or title…',
      options: citations.map(c => ({ value: c.value, label: `${c.docCode} · ${c.citationCode} — ${isAr && c.titleAr ? c.titleAr : c.titleEn}` })) },
    { key: 'coverage', label: isAr ? 'التغطية' : 'Coverage', kind: 'select', required: true, span: 6,
      options: COVERAGES.map(c => ({ value: c, label: coverageLabel(c) })) },
    { key: 'rationaleEn', label: isAr ? 'المبرر (إنجليزي) — مطلوب' : 'Rationale (English) — required', kind: 'textarea', rows: 2, span: 12 },
    { key: 'rationaleAr', label: isAr ? 'المبرر (عربي)' : 'Rationale (Arabic)', kind: 'textarea', rows: 2, span: 12 },
  ];
  const mapInitial = { citationId: '', coverage: 'FULL', rationaleEn: '', rationaleAr: '' };

  async function submitMapping(v: Record<string, string>) {
    if (!selected) return;
    const citationId = Number(v.citationId);
    if (!citationId || Number.isNaN(citationId)) {
      toast.danger(isAr ? 'يجب اختيار نص تنظيمي' : 'Pick a citation to map');
      return;
    }
    const rationaleEn = (v.rationaleEn || '').trim();
    if (!rationaleEn) {
      toast.danger(isAr ? 'المبرر (الإنجليزي) مطلوب' : 'Rationale (English) is required');
      return;
    }
    try {
      await createMapping({
        controlId: selected.id,
        citationId,
        coverage: (v.coverage || 'FULL') as Coverage,
        rationaleEn,
        rationaleAr: (v.rationaleAr || '').trim() || undefined,
      });
      const c = citationById.get(citationId);
      toast.success(isAr ? `تم ربط ${selected.controlCode} بـ ${c ? c.citationCode : 'النص'}`
                        : `Mapped ${selected.controlCode} to ${c ? c.citationCode : 'citation'}`);
      setOpenMap(false);
      await selectControl(selected);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر إنشاء الربط' : 'Failed to create mapping'));
    }
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

      {/* Domain filter — one shared pill control */}
      <div style={{ marginBottom: 14 }}>
        <PillTabs
          aria-label={isAr ? 'تصفية بالمجال' : 'Filter by domain'}
          active={domain}
          onChange={setDomain}
          options={[
            { id: '', label: isAr ? 'الكل' : 'All', count: controls.length },
            ...DOMAINS.map(d => ({ id: d, label: domainLabel(d, isAr ? 'ar' : 'en'), count: byDomain.get(d) ?? 0 })),
          ]}
        />
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
                    color: 'var(--ink-1)', display: 'inline-flex',
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
                  <Section title={isAr ? 'تغطية النصوص التنظيمية' : 'Regulatory coverage'} count={mappings.length}
                    action={!demoMode && citations.length > 0
                      ? <Button variant="secondary" size="sm" Icon={BookText} onClick={() => setOpenMap(true)}>
                          {isAr ? 'ربط نص تنظيمي' : 'Map citation'}
                        </Button>
                      : undefined}>
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
                            {mappings.map(m => {
                              const c = citationById.get(m.citationId);
                              return (
                                <tr key={m.id}>
                                  <td>
                                    {c
                                      ? <>
                                          <div className="mono text-2xs">{c.docCode} · {c.citationCode}</div>
                                          <div className="text-2xs text-muted">{isAr && c.titleAr ? c.titleAr : c.titleEn}</div>
                                        </>
                                      : <span className="mono text-2xs">#{m.citationId}</span>}
                                  </td>
                                  <td><Badge size="sm" tone={coverageTone(m.coverage)}>{coverageLabel(m.coverage)}</Badge></td>
                                  <td className="text-xs" style={{ maxWidth: 360 }}>
                                    {(isAr && m.rationaleAr ? m.rationaleAr : m.rationaleEn) ?? '—'}
                                  </td>
                                  <td className="mono text-2xs">{m.lastReviewedAt?.substring(0, 10) ?? '—'}</td>
                                </tr>
                              );
                            })}
                          </tbody>
                        </table>}
                  </Section>

                  {/* Risk-control matrix */}
                  <Section title={isAr ? 'المخاطر المرتبطة' : 'Risk-control matrix'} count={riskLinks.length}
                    action={!demoMode && risks.length > 0
                      ? <Button variant="secondary" size="sm" Icon={Link2} onClick={() => setOpenLink(true)}>
                          {isAr ? 'ربط خطر' : 'Link risk'}
                        </Button>
                      : undefined}>
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
                            {riskLinks.map(l => {
                              const r = riskById.get(l.riskId);
                              return (
                                <tr key={l.id}>
                                  <td>
                                    {r
                                      ? <>
                                          <div className="mono text-2xs">{r.riskCode}</div>
                                          <div className="text-2xs text-muted">{isAr && r.titleAr ? r.titleAr : r.titleEn}</div>
                                        </>
                                      : <span className="mono text-2xs">#{l.riskId}</span>}
                                  </td>
                                  <td><Badge size="sm" tone="info">{linkTypeLabel(l.linkType)}</Badge></td>
                                  <td><Badge size="sm" tone={effectivenessTone(l.effectiveness)}>{l.effectiveness ?? '—'}</Badge></td>
                                  <td className="mono text-2xs">{l.lastReviewedAt?.substring(0, 10) ?? '—'}</td>
                                </tr>
                              );
                            })}
                          </tbody>
                        </table>}
                  </Section>

                  {/* Assessments */}
                  <Section title={isAr ? 'التقييمات' : 'Assessments'} count={assessments.length}
                    action={!demoMode && selected.status === 'ACTIVE'
                      ? <Button variant="secondary" size="sm" Icon={Plus} onClick={() => setOpenAssess(true)}>
                          {isAr ? 'تسجيل تقييم' : 'Record assessment'}
                        </Button>
                      : undefined}>
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

      {selected && (
        <FormModal
          open={openAssess}
          title={isAr ? `تسجيل تقييم — ${selected.controlCode}` : `Record assessment — ${selected.controlCode}`}
          fields={assessFields}
          initial={assessInitial}
          submitLabel={isAr ? 'تسجيل' : 'Record'}
          size="md"
          onClose={() => setOpenAssess(false)}
          onSubmit={submitAssessment}
        />
      )}

      {selected && (
        <FormModal
          open={openLink}
          title={isAr ? `ربط خطر — ${selected.controlCode}` : `Link risk — ${selected.controlCode}`}
          fields={linkFields}
          initial={linkInitial}
          submitLabel={isAr ? 'ربط' : 'Link'}
          size="md"
          onClose={() => setOpenLink(false)}
          onSubmit={submitLink}
        />
      )}

      {selected && (
        <FormModal
          open={openMap}
          title={isAr ? `ربط نص تنظيمي — ${selected.controlCode}` : `Map citation — ${selected.controlCode}`}
          fields={mapFields}
          initial={mapInitial}
          submitLabel={isAr ? 'ربط' : 'Map'}
          size="md"
          onClose={() => setOpenMap(false)}
          onSubmit={submitMapping}
        />
      )}
    </Shell>
  );
}

function Section({ title, count, action, children }:
  { title: string; count: number; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
        <h4 style={{ margin: 0, fontSize: 13, fontWeight: 600 }}>{title}</h4>
        <Badge tone="primary">{count}</Badge>
        {action && <div style={{ marginInlineStart: 'auto' }}>{action}</div>}
      </div>
      {children}
    </div>
  );
}

