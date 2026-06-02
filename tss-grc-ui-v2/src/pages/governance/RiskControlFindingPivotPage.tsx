import { useEffect, useMemo, useState } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import {
  AlertTriangle, ShieldCheck, FileWarning, ClipboardCheck, Activity,
  ExternalLink, RefreshCw, GitBranch, CheckCircle2, XCircle, MinusCircle,
  type LucideIcon,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Kpi } from '../../components/ui/Kpi';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { isAuthenticated } from '../../lib/auth';
import { ApiError } from '../../lib/api';
import {
  pivotApi,
  pivotTitle,
  EFFECTIVENESS_LABEL,
  SEVERITY_TONE,
  STATUS_TONE,
  FOCAL_ENTITY_LABEL,
  type PivotResponse,
  type FocalEntity,
} from '../../lib/pivot';

/**
 * Risk-Control-Finding Pivot — Sprint 11.
 *
 * Saudi-native title:
 *   ar: محور المخاطر والضوابط والنتائج  (NOT literal — "pivot" carries
 *       the "centred analytical view" sense of an Excel pivot table,
 *       which محور captures naturally in operator-Arabic.)
 *   en: Risk-Control-Finding Pivot
 *
 * Layout:
 *   - Central focal card carries the risk / control / finding at the centre.
 *   - Four surrounding cards: linked controls, linked findings, linked
 *     CAPA, CCM coverage rollup. A fifth ("linked risks") appears when
 *     lensed by control or finding.
 *   - Auditor's checklist sidebar on the right with four smart
 *     checkboxes (has controls, tested, has open findings, has overdue
 *     CAPA) — turns the data-completeness number into actionable
 *     "what's missing" for engagement scoping.
 *   - Mini-graph at the top: Risk → Control → Finding → CAPA in SVG.
 *
 * Cross-lens navigation: each related-card row is a Link that
 * switches the lens to that entity. The header carries three "Switch
 * lens" pills (Risk · Control · Finding) — only the non-focal ones
 * are enabled, and only when there's a related entity to pivot to.
 */
export function RiskControlFindingPivotPage() {
  const { lang } = useI18n();
  const t = (en: string, ar: string) => (lang === 'ar' ? ar : en);
  const demoMode = !isAuthenticated();
  const navigate = useNavigate();
  const { riskId, controlId, findingId } = useParams<{
    riskId?: string; controlId?: string; findingId?: string;
  }>();

  const focal: FocalEntity = riskId ? 'RISK' : controlId ? 'CONTROL' : 'FINDING';
  const focalId = riskId ?? controlId ?? findingId;

  const [pivot, setPivot] = useState<PivotResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    if (demoMode || !focalId) { setPivot(null); return; }
    setLoading(true);
    setError(null);
    try {
      const data =
        focal === 'RISK'    ? await pivotApi.byRisk(focalId)
      : focal === 'CONTROL' ? await pivotApi.byControl(focalId)
      :                       await pivotApi.byFinding(focalId);
      setPivot(data);
    } catch (err) {
      setError(err instanceof ApiError
        ? err.message
        : t('Failed to load pivot view', 'تعذّر تحميل المحور'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); /* eslint-disable-next-line */ }, [focal, focalId]);

  function switchLens(target: FocalEntity, id: number | string) {
    if (target === 'RISK')    navigate(`/governance/pivot/risk/${id}`);
    if (target === 'CONTROL') navigate(`/governance/pivot/control/${id}`);
    if (target === 'FINDING') navigate(`/governance/pivot/finding/${id}`);
  }

  // ─── Auditor's checklist ───────────────────────────────────────
  const checklist = useMemo(() => {
    if (!pivot) return null;
    const hasControls = pivot.linkedControls.length > 0;
    const hasTestedControls = pivot.linkedControls.some(c =>
      c.effectiveness && c.effectiveness !== 'NOT_TESTED');
    const hasOpenFindings = pivot.summary.openFindingsCount > 0;
    const hasOverdueCapa = pivot.summary.overdueCapaCount > 0;
    return { hasControls, hasTestedControls, hasOpenFindings, hasOverdueCapa };
  }, [pivot]);

  // ─── Render ────────────────────────────────────────────────────
  return (
    <Shell title={pivotTitle(lang)}
           sub={t('NCA ECC-2 · SAMA CSF 1.2.3 · IIA 2210', 'NCA ECC-2 · SAMA CSF 1.2.3 · IIA 2210')}>
      <PageHeader
        eyebrow={t('Governance · Cross-module', 'الحوكمة · متعدد الوحدات')}
        title={pivotTitle(lang)}
        sub={t(
          'One screen with every linked control, audit finding, corrective action and continuous-monitoring result for the selected risk, control, or finding — the workspace the auditor would otherwise build in Excel during engagement scoping.',
          'شاشة واحدة تجمع جميع الضوابط المرتبطة ونتائج التدقيق والإجراءات التصحيحية ونتائج المراقبة المستمرة للمخاطرة أو الضابط أو النتيجة المحددة — مساحة العمل التي يبنيها المدقق عادة في Excel عند تحديد نطاق الارتباط.'
        )}
        actions={
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <LensSwitcher focal={focal} pivot={pivot} onSwitch={switchLens} t={t} lang={lang} />
            <button onClick={load} disabled={loading || demoMode}
                    style={btnStyle('outline')}>
              <RefreshCw size={14} style={{ verticalAlign: '-2px', marginInlineEnd: 6 }} />
              {t('Refresh', 'تحديث')}
            </button>
            {demoMode && <Badge tone="warning">{t('Demo mode', 'وضع العرض')}</Badge>}
          </div>
        }
      />

      {error && (
        <Card padded>
          <div style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</div>
        </Card>
      )}
      {loading && !pivot && (
        <Card padded>
          <InlineEmpty>{t('Loading pivot view…', 'جارٍ تحميل المحور…')}</InlineEmpty>
        </Card>
      )}
      {!pivot && !loading && (
        <EmptyState
          Icon={GitBranch}
          title={t('Select an entity to pivot around',
                   'اختر كياناً لبناء المحور حوله')}
          sub={t(
            'Open a risk, control, or finding from its register and click "Open pivot view". The route accepts /governance/pivot/risk/:id, /control/:id, or /finding/:id.',
            'افتح مخاطرة أو ضابطاً أو نتيجة من سجلها واضغط «فتح المحور». يقبل المسار /governance/pivot/risk/:id أو /control/:id أو /finding/:id.'
          )} />
      )}

      {pivot && (
        <>
          {/* Mini-graph */}
          <Card padded>
            <PivotMiniGraph pivot={pivot} t={t} />
          </Card>

          {/* Summary KPIs */}
          <div className="kpi-grid" style={{ display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12 }}>
            <Kpi Icon={ShieldCheck}
                 label={t('Linked controls', 'الضوابط المرتبطة')}
                 value={pivot.summary.controlsCount}
                 tone={pivot.summary.controlsCount > 0 ? 'primary' : 'neutral'} />
            <Kpi Icon={CheckCircle2}
                 label={t('Effective', 'فعّالة')}
                 value={`${Math.round(pivot.summary.effectiveControlsPct)}%`}
                 tone={pivot.summary.effectiveControlsPct >= 70 ? 'success'
                     : pivot.summary.effectiveControlsPct >= 40 ? 'warning' : 'danger'} />
            <Kpi Icon={FileWarning}
                 label={t('Open findings', 'النتائج المفتوحة')}
                 value={pivot.summary.openFindingsCount}
                 tone={pivot.summary.openFindingsCount === 0 ? 'success'
                     : pivot.summary.openFindingsCount > 3 ? 'danger' : 'warning'} />
            <Kpi Icon={ClipboardCheck}
                 label={t('Overdue CAPA', 'إجراءات متأخرة')}
                 value={pivot.summary.overdueCapaCount}
                 tone={pivot.summary.overdueCapaCount === 0 ? 'success' : 'danger'} />
            <Kpi Icon={Activity}
                 label={t('Data completeness', 'اكتمال البيانات')}
                 value={`${Math.round(pivot.summary.dataCompletenessPct)}%`}
                 tone={pivot.summary.dataCompletenessPct >= 80 ? 'success'
                     : pivot.summary.dataCompletenessPct >= 50 ? 'warning' : 'danger'} />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 280px',
                        gap: 16, marginTop: 16 }}>
            {/* Main column — focal card + surrounding cards */}
            <div style={{ display: 'grid', gap: 16 }}>
              <FocalCard pivot={pivot} t={t} lang={lang} />

              {/* Linked Risks — only present when lensed by control/finding */}
              {pivot.linkedRisks.length > 0 && (
                <Card padded>
                  <SectionTitle Icon={AlertTriangle}
                                title={t('Linked risks', 'المخاطر المرتبطة')}
                                count={pivot.linkedRisks.length} />
                  <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
                    {pivot.linkedRisks.map(r => (
                      <li key={r.id} style={rowStyle}>
                        <button onClick={() => switchLens('RISK', r.id)}
                                style={linkBtnStyle}>
                          <strong>{(lang === 'ar' && r.titleAr) || r.titleEn}</strong>
                        </button>
                        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                          {r.riskLevel && (
                            <Badge tone={r.riskLevel === 'CRITICAL' || r.riskLevel === 'HIGH'
                              ? 'danger' : r.riskLevel === 'MEDIUM' ? 'warning' : 'neutral'}>
                              {r.riskLevel}
                            </Badge>
                          )}
                          {r.residualScore != null && (
                            <span style={metaStyle}>{r.residualScore}</span>
                          )}
                          <Link to={`/risk/${r.id}`} target="_blank" style={extStyle}
                                title={t('Open in Risk Register', 'فتح في سجل المخاطر')}>
                            <ExternalLink size={14} />
                          </Link>
                        </div>
                      </li>
                    ))}
                  </ul>
                </Card>
              )}

              {/* Linked Controls */}
              <Card padded>
                <SectionTitle Icon={ShieldCheck}
                              title={t('Linked controls', 'الضوابط المرتبطة')}
                              count={pivot.linkedControls.length} />
                {pivot.linkedControls.length === 0 ? (
                  <InlineEmpty>{t('No linked controls — this is an uncontrolled entity.',
                                   'لا توجد ضوابط مرتبطة — كيان غير مضبوط.')}</InlineEmpty>
                ) : (
                  <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
                    {pivot.linkedControls.map(c => {
                      const effLabel = c.effectiveness ? EFFECTIVENESS_LABEL[c.effectiveness] : undefined;
                      return (
                        <li key={c.id} style={rowStyle}>
                          <button onClick={() => switchLens('CONTROL', c.id)}
                                  style={linkBtnStyle}>
                            <strong>{c.code}</strong>
                            <span style={{ color: 'var(--ink-3)', marginInlineStart: 8 }}>
                              {(lang === 'ar' && c.titleAr) || c.titleEn}
                            </span>
                          </button>
                          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                            {effLabel && (
                              <Badge tone={effLabel.tone === 'good' ? 'success'
                                : effLabel.tone === 'warn' ? 'warning'
                                : effLabel.tone === 'bad' ? 'danger' : 'neutral'}>
                                {effLabel[lang]}
                              </Badge>
                            )}
                            {c.lastTestStatus && (
                              <Badge tone={STATUS_TONE[c.lastTestStatus] === 'good' ? 'success'
                                  : STATUS_TONE[c.lastTestStatus] === 'bad' ? 'danger' : 'neutral'}>
                                {c.lastTestStatus}
                              </Badge>
                            )}
                            <Link to={`/controls/library`} target="_blank" style={extStyle}
                                  title={t('Open in Control Library', 'فتح في مكتبة الضوابط')}>
                              <ExternalLink size={14} />
                            </Link>
                          </div>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </Card>

              {/* Linked Findings */}
              <Card padded>
                <SectionTitle Icon={FileWarning}
                              title={t('Linked findings', 'النتائج المرتبطة')}
                              count={pivot.linkedFindings.length} />
                {pivot.linkedFindings.length === 0 ? (
                  <InlineEmpty>{t('No linked findings.', 'لا توجد نتائج مرتبطة.')}</InlineEmpty>
                ) : (
                  <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
                    {pivot.linkedFindings.map(f => (
                      <li key={f.id} style={rowStyle}>
                        <button onClick={() => switchLens('FINDING', f.id)}
                                style={linkBtnStyle}>
                          <strong>{f.refNumber}</strong>
                          <span style={{ color: 'var(--ink-3)', marginInlineStart: 8 }}>
                            {(lang === 'ar' && f.titleAr) || f.titleEn}
                          </span>
                        </button>
                        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                          {f.severity && (
                            <Badge tone={SEVERITY_TONE[f.severity] === 'bad' ? 'danger'
                                : SEVERITY_TONE[f.severity] === 'warn' ? 'warning' : 'neutral'}>
                              {f.severity}
                            </Badge>
                          )}
                          {f.status && (
                            <Badge tone={STATUS_TONE[f.status] === 'good' ? 'success'
                                : STATUS_TONE[f.status] === 'bad' ? 'danger' : 'warning'}>
                              {f.status}
                            </Badge>
                          )}
                          <Link to={`/audit-lifecycle`} target="_blank" style={extStyle}
                                title={t('Open in Audit Findings', 'فتح في نتائج التدقيق')}>
                            <ExternalLink size={14} />
                          </Link>
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </Card>

              {/* Linked CAPA */}
              <Card padded>
                <SectionTitle Icon={ClipboardCheck}
                              title={t('Open corrective actions', 'إجراءات تصحيحية مفتوحة')}
                              count={pivot.linkedCapa.length} />
                {pivot.linkedCapa.length === 0 ? (
                  <InlineEmpty>{t('No open CAPA.', 'لا توجد إجراءات تصحيحية مفتوحة.')}</InlineEmpty>
                ) : (
                  <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
                    {pivot.linkedCapa.map(c => {
                      const isOverdue = c.daysOverdue != null && c.daysOverdue > 0;
                      return (
                        <li key={`${c.sourceModule}-${c.id}`} style={rowStyle}>
                          <div>
                            <strong>{c.externalId}</strong>
                            <span style={{ color: 'var(--ink-3)', marginInlineStart: 8 }}>
                              {(lang === 'ar' && c.titleAr) || c.titleEn}
                            </span>
                          </div>
                          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                            {c.targetDate && (
                              <span style={metaStyle}>{c.targetDate}</span>
                            )}
                            <Badge tone={isOverdue ? 'danger'
                                : c.status === 'CLOSED' ? 'success' : 'warning'}>
                              {isOverdue ? t('Overdue', 'متأخر') : c.status}
                            </Badge>
                            <Link to={`/capa`} target="_blank" style={extStyle}
                                  title={t('Open in CAPA', 'فتح في الإجراءات التصحيحية')}>
                              <ExternalLink size={14} />
                            </Link>
                          </div>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </Card>

              {/* CCM Coverage */}
              <Card padded>
                <SectionTitle Icon={Activity}
                              title={t('Continuous monitoring coverage',
                                       'تغطية المراقبة المستمرة')}
                              count={pivot.ccmCoverage.totalTests} />
                {pivot.ccmCoverage.totalTests === 0 ? (
                  <InlineEmpty>{t('No CCM tests linked to these controls.',
                                   'لا توجد اختبارات مراقبة مستمرة مرتبطة بهذه الضوابط.')}</InlineEmpty>
                ) : (
                  <div style={{ display: 'grid',
                                gridTemplateColumns: 'repeat(auto-fit, minmax(140px,1fr))',
                                gap: 12 }}>
                    <Kpi Icon={CheckCircle2} label={t('Passing 24h', 'ناجح 24س')}
                         value={pivot.ccmCoverage.passingLast24h} tone="success" />
                    <Kpi Icon={XCircle} label={t('Failing 24h', 'فاشل 24س')}
                         value={pivot.ccmCoverage.failingLast24h}
                         tone={pivot.ccmCoverage.failingLast24h > 0 ? 'danger' : 'neutral'} />
                    <Kpi Icon={MinusCircle} label={t('Errors 24h', 'أخطاء 24س')}
                         value={pivot.ccmCoverage.errorLast24h}
                         tone={pivot.ccmCoverage.errorLast24h > 0 ? 'warning' : 'neutral'} />
                  </div>
                )}
              </Card>
            </div>

            {/* Right rail — Auditor's checklist */}
            {checklist && (
              <div>
                <Card padded>
                  <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 12 }}>
                    {t("Auditor's checklist", 'قائمة المدقق')}
                  </div>
                  <ChecklistRow ok={checklist.hasControls}
                    label={t('Has linked controls', 'يحتوي على ضوابط مرتبطة')} />
                  <ChecklistRow ok={checklist.hasTestedControls}
                    label={t('Controls have been tested', 'الضوابط مُختبرة')} />
                  <ChecklistRow ok={!checklist.hasOpenFindings}
                    label={t('No open findings', 'لا توجد نتائج مفتوحة')}
                    inverted={checklist.hasOpenFindings} />
                  <ChecklistRow ok={!checklist.hasOverdueCapa}
                    label={t('No overdue CAPA', 'لا توجد إجراءات متأخرة')}
                    inverted={checklist.hasOverdueCapa} />
                  <div style={{ marginTop: 12, fontSize: 12, color: 'var(--ink-3)' }}>
                    {t(
                      'These four checks gate the engagement scoping conversation. Any failure becomes a discussion point with the risk owner.',
                      'هذه الفحوصات الأربعة تُؤطّر نقاش تحديد نطاق الارتباط. أيّ فشل يُصبح نقطة نقاش مع مالك المخاطرة.'
                    )}
                  </div>
                </Card>
              </div>
            )}
          </div>
        </>
      )}
    </Shell>
  );
}

// ─── Sub-components ────────────────────────────────────────────────

function LensSwitcher({
  focal, pivot, onSwitch, t, lang,
}: {
  focal: FocalEntity;
  pivot: PivotResponse | null;
  onSwitch: (target: FocalEntity, id: number | string) => void;
  t: (en: string, ar: string) => string;
  lang: 'en' | 'ar';
}) {
  if (!pivot) return null;
  const riskOpt =
    focal === 'RISK' ? pivot.risk?.id
    : pivot.linkedRisks[0]?.id;
  const controlOpt =
    focal === 'CONTROL' ? pivot.control?.id
    : pivot.linkedControls[0]?.id;
  const findingOpt =
    focal === 'FINDING' ? pivot.finding?.id
    : pivot.linkedFindings[0]?.id;

  return (
    <div style={{ display: 'flex', gap: 4, fontSize: 12 }}
         title={t('Switch lens', 'تبديل المنظور')}>
      <PillBtn active={focal === 'RISK'} disabled={!riskOpt}
        onClick={() => riskOpt && onSwitch('RISK', riskOpt)}>
        {FOCAL_ENTITY_LABEL.RISK[lang]}
      </PillBtn>
      <PillBtn active={focal === 'CONTROL'} disabled={!controlOpt}
        onClick={() => controlOpt && onSwitch('CONTROL', controlOpt)}>
        {FOCAL_ENTITY_LABEL.CONTROL[lang]}
      </PillBtn>
      <PillBtn active={focal === 'FINDING'} disabled={!findingOpt}
        onClick={() => findingOpt && onSwitch('FINDING', findingOpt)}>
        {FOCAL_ENTITY_LABEL.FINDING[lang]}
      </PillBtn>
    </div>
  );
}

function PillBtn({
  children, active, disabled, onClick,
}: {
  children: React.ReactNode;
  active?: boolean;
  disabled?: boolean;
  onClick?: () => void;
}) {
  return (
    <button onClick={onClick} disabled={disabled || active}
      style={{
        padding: '4px 10px',
        border: `1px solid ${active ? 'var(--primary)' : 'var(--border)'}`,
        borderRadius: 999,
        background: active ? 'var(--primary)' : 'var(--surface)',
        color: active ? 'white' : disabled ? 'var(--ink-3)' : 'var(--ink-1)',
        cursor: disabled || active ? 'default' : 'pointer',
        fontSize: 12,
        opacity: disabled ? 0.5 : 1,
      }}>
      {children}
    </button>
  );
}

function FocalCard({
  pivot, t, lang,
}: { pivot: PivotResponse; t: (en: string, ar: string) => string; lang: 'en' | 'ar' }) {
  if (pivot.focalEntity === 'RISK' && pivot.risk) {
    const r = pivot.risk;
    return (
      <Card padded>
        <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>
          {t('Focal risk', 'المخاطرة المحورية')} · {r.code}
        </div>
        <div style={{ fontSize: 20, fontWeight: 600, margin: '6px 0' }}>
          {(lang === 'ar' && r.titleAr) || r.titleEn}
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {r.riskLevel && (
            <Badge tone={r.riskLevel === 'CRITICAL' || r.riskLevel === 'HIGH' ? 'danger'
              : r.riskLevel === 'MEDIUM' ? 'warning' : 'neutral'}>
              {r.riskLevel}
            </Badge>
          )}
          {r.residualScore != null && (
            <span style={metaStyle}>
              {t('Residual', 'متبقّي')}: <strong>{r.residualScore}</strong>
            </span>
          )}
          {r.status && (
            <Badge tone={STATUS_TONE[r.status] === 'good' ? 'success' : 'warning'}>
              {r.status}
            </Badge>
          )}
        </div>
      </Card>
    );
  }
  if (pivot.focalEntity === 'CONTROL' && pivot.control) {
    const c = pivot.control;
    return (
      <Card padded>
        <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>
          {t('Focal control', 'الضابط المحوري')} · {c.code}
        </div>
        <div style={{ fontSize: 20, fontWeight: 600, margin: '6px 0' }}>
          {(lang === 'ar' && c.titleAr) || c.titleEn}
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {c.domain && <Badge tone="neutral">{c.domain}</Badge>}
          {c.controlType && <Badge tone="neutral">{c.controlType}</Badge>}
          {c.automationLevel && <Badge tone="neutral">{c.automationLevel}</Badge>}
          {c.status && <Badge tone={c.status === 'ACTIVE' ? 'success' : 'neutral'}>{c.status}</Badge>}
        </div>
      </Card>
    );
  }
  if (pivot.focalEntity === 'FINDING' && pivot.finding) {
    const f = pivot.finding;
    return (
      <Card padded>
        <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>
          {t('Focal finding', 'النتيجة المحورية')} · {f.refNumber}
        </div>
        <div style={{ fontSize: 20, fontWeight: 600, margin: '6px 0' }}>
          {(lang === 'ar' && f.titleAr) || f.titleEn}
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {f.severity && (
            <Badge tone={SEVERITY_TONE[f.severity] === 'bad' ? 'danger'
              : SEVERITY_TONE[f.severity] === 'warn' ? 'warning' : 'neutral'}>
              {f.severity}
            </Badge>
          )}
          {f.status && (
            <Badge tone={STATUS_TONE[f.status] === 'good' ? 'success' : 'warning'}>
              {f.status}
            </Badge>
          )}
          {f.engagementCode && (
            <span style={metaStyle}>{t('Engagement', 'الارتباط')}: {f.engagementCode}</span>
          )}
        </div>
      </Card>
    );
  }
  return null;
}

function PivotMiniGraph({
  pivot, t,
}: { pivot: PivotResponse; t: (en: string, ar: string) => string }) {
  // Simple SVG: 4 nodes in a row + arrows. Focal node is highlighted.
  const focal = pivot.focalEntity;
  const colors: Record<string, string> = {
    RISK:    '#ef4444',
    CONTROL: '#3b82f6',
    FINDING: '#f59e0b',
    CAPA:    '#10b981',
  };
  const labelEn: Record<string, string> = {
    RISK: 'Risk', CONTROL: 'Control', FINDING: 'Finding', CAPA: 'CAPA',
  };
  const labelAr: Record<string, string> = {
    RISK: 'مخاطرة', CONTROL: 'ضابط', FINDING: 'نتيجة', CAPA: 'إجراء',
  };
  const counts: Record<string, number> = {
    RISK: pivot.linkedRisks.length + (pivot.risk ? 1 : 0),
    CONTROL: pivot.linkedControls.length,
    FINDING: pivot.linkedFindings.length,
    CAPA: pivot.linkedCapa.length,
  };
  const lang = t('en', 'ar') === 'ar' ? 'ar' : 'en';
  const nodes: ('RISK' | 'CONTROL' | 'FINDING' | 'CAPA')[] = ['RISK', 'CONTROL', 'FINDING', 'CAPA'];

  return (
    <div>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 4 }}>
        {t('Risk → Control → Finding → CAPA flow',
           'تدفّق المخاطرة ← الضابط ← النتيجة ← الإجراء')}
      </div>
      <svg viewBox="0 0 520 90" width="100%" height="90" role="img"
           aria-label={t('Pivot flow diagram', 'مخطط تدفّق المحور')}>
        {nodes.map((node, idx) => {
          const x = 40 + idx * 140;
          const isFocal = node === focal;
          return (
            <g key={node}>
              {idx < 3 && (
                <line x1={x + 55} y1={45} x2={x + 80} y2={45}
                      stroke="var(--ink-3)" strokeWidth={1.5}
                      markerEnd="url(#arrow)" />
              )}
              <circle cx={x + 25} cy={45} r={isFocal ? 24 : 18}
                      fill={colors[node]} stroke={isFocal ? 'var(--primary)' : 'transparent'}
                      strokeWidth={3} opacity={isFocal ? 1 : 0.85} />
              <text x={x + 25} y={49} textAnchor="middle"
                    fontSize={isFocal ? 14 : 12} fontWeight={isFocal ? 700 : 500}
                    fill="white">
                {counts[node]}
              </text>
              <text x={x + 25} y={80} textAnchor="middle" fontSize={11} fill="var(--ink-1)">
                {lang === 'ar' ? labelAr[node] : labelEn[node]}
              </text>
            </g>
          );
        })}
        <defs>
          <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6"
                  markerHeight="6" orient="auto">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--ink-3)" />
          </marker>
        </defs>
      </svg>
    </div>
  );
}

function SectionTitle({
  Icon, title, count,
}: {
  Icon: LucideIcon;
  title: string; count?: number;
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8,
                  fontWeight: 600, fontSize: 14, marginBottom: 10 }}>
      <Icon size={16} />
      <span>{title}</span>
      {count != null && (
        <span style={{ marginInlineStart: 6, fontSize: 12, color: 'var(--ink-3)' }}>
          ({count})
        </span>
      )}
    </div>
  );
}

function ChecklistRow({
  ok, label, inverted,
}: {
  ok: boolean;
  label: string;
  inverted?: boolean;   // true when "ok" actually represents a bad state being absent
}) {
  const tone = ok ? 'success' : inverted ? 'danger' : 'warning';
  const Icon = ok ? CheckCircle2 : XCircle;
  return (
    <div style={{ display: 'flex', gap: 8, alignItems: 'center', padding: '6px 0',
                  borderBottom: '1px solid var(--border)' }}>
      <Icon size={16} style={{ color:
          tone === 'success' ? 'var(--success)'
        : tone === 'danger' ? 'var(--danger)'
        : 'var(--warning)' }} />
      <span style={{ fontSize: 13 }}>{label}</span>
    </div>
  );
}

// ─── Inline styles ────────────────────────────────────────────────

const rowStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  padding: '8px 0',
  borderBottom: '1px solid var(--border)',
  fontSize: 13,
  gap: 12,
};

const linkBtnStyle: React.CSSProperties = {
  background: 'transparent',
  border: 'none',
  padding: 0,
  textAlign: 'inherit',
  color: 'var(--ink-1)',
  cursor: 'pointer',
  fontSize: 13,
  flex: 1,
  minWidth: 0,
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  whiteSpace: 'nowrap',
};

const metaStyle: React.CSSProperties = {
  fontSize: 12,
  color: 'var(--ink-3)',
};

const extStyle: React.CSSProperties = {
  color: 'var(--ink-3)',
  display: 'inline-flex',
  alignItems: 'center',
};

function btnStyle(variant: 'outline' | 'primary'): React.CSSProperties {
  return {
    padding: '6px 12px',
    border: `1px solid ${variant === 'primary' ? 'var(--primary)' : 'var(--border)'}`,
    borderRadius: 6,
    background: variant === 'primary' ? 'var(--primary)' : 'var(--surface)',
    color: variant === 'primary' ? 'white' : 'var(--ink-1)',
    fontSize: 13,
    cursor: 'pointer',
  };
}
