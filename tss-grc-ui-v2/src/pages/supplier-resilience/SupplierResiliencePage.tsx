import { useEffect, useMemo, useState, Fragment } from 'react';
import {
  ShieldCheck, Plus, CheckCircle2, XCircle, Archive, Play, FlaskConical,
  ChevronRight, ChevronDown, AlertTriangle, Layers, Wrench,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import {
  listAlternates, createAlternate, approveAlternate, archiveAlternate,
  listExitPlans, createExitPlan, approvePlan, rejectPlan, activatePlan, archivePlan, recordPlanTest,
  parseExitPlanSteps,
  ALT_STATUS_TONE, ALT_OUTCOME_TONE, PLAN_STATUS_TONE, PLAN_TEST_TONE,
  fmtSar,
  type AlternateSupplier, type ExitPlan, type AltRole, type PlanSeverity,
} from '../../lib/supplierResilience';

/**
 * SupplierResiliencePage — gap-analysis item 4 (Saudi GRC §3.3).
 *
 * Two tabs: alternate suppliers + exit plans. Each tab is its own
 * register with KPIs, an expandable row table, and inline action
 * buttons. Feeds the "supplier-resilience report" dashboard.
 */
type TabId = 'alternates' | 'exit-plans';

interface AltFormValues {
  primaryVendorName?:     string;     alternateVendorName?:     string;
  alternateVendorCountry?: string;     alternateVendorRole?:    string;
  serviceScopeEn?:         string;     serviceScopeAr?:         string;
  estimatedOnboardingDays?: number;    estimatedCostSar?:       number;
  contractualStatus?:       string;    notesEn?:                string;
}
const ALT_INITIAL: AltFormValues = {
  alternateVendorRole: 'BACKUP_PRIMARY',
  contractualStatus:   'NONE',
};

interface PlanFormValues {
  vendorName?:          string;        contractCode?:           string;
  titleEn?:             string;        titleAr?:                string;
  triggerConditions?:   string;        severityThreshold?:      string;
  targetDisengagementDays?: number;
  stepsJson?:           string;
  dataMigrationPlanEn?: string;
}
const PLAN_INITIAL: PlanFormValues = {
  severityThreshold:       'MEDIUM',
  targetDisengagementDays: 90,
  stepsJson:               '[{"seq":1,"actionEn":"Notify vendor of disengagement","owner":"Vendor Risk Manager","days":3,"evidenceRequired":true}]',
};

export function SupplierResiliencePage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [tab, setTab]         = useState<TabId>('alternates');
  const [alts, setAlts]       = useState<AlternateSupplier[]>([]);
  const [plans, setPlans]     = useState<ExitPlan[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId]   = useState<number | null>(null);
  const [createAltOpen, setCreateAltOpen]   = useState(false);
  const [createPlanOpen, setCreatePlanOpen] = useState(false);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  useEffect(() => { void reload(); }, []);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — alternates and exit plans live in separate
    // backends; one outage shouldn't blank the entire SCM workspace.
    const [a, p] = await Promise.all([
      listAlternates(undefined, 0, 200).catch(() => ({ content: [] as AlternateSupplier[], totalElements: 0 })),
      listExitPlans(undefined, 0, 200).catch(() => ({ content: [] as ExitPlan[], totalElements: 0 })),
    ]);
    setAlts(a.content);
    setPlans(p.content);
    setLoading(false);
  }

  // ─── KPIs ────────────────────────────────────────────────────────
  const stats = useMemo(() => {
    const s = {
      altApproved: alts.filter(a => a.status === 'APPROVED').length,
      altReady:    alts.filter(a => a.lastAssessedOutcome === 'READY').length,
      altGap:      alts.filter(a => a.lastAssessedOutcome === 'GAP' || a.lastAssessedOutcome === 'NOT_VIABLE').length,
      plansApproved: plans.filter(p => p.status === 'APPROVED' || p.status === 'ACTIVATED').length,
      plansActivated: plans.filter(p => p.status === 'ACTIVATED').length,
      plansOverdueTest: plans.filter(p => {
        if (!p.nextTestDue) return false;
        if (p.status === 'ARCHIVED' || p.status === 'REJECTED') return false;
        return new Date(p.nextTestDue) < new Date();
      }).length,
    };
    return s;
  }, [alts, plans]);

  // ─── Tabs ────────────────────────────────────────────────────────
  const tabs: TabItem<TabId>[] = [
    { id: 'alternates', label: `${t('srpTabAlts')} (${alts.length})` },
    { id: 'exit-plans', label: `${t('srpTabPlans')} (${plans.length})` },
  ];

  function toggleExpanded(key: string) {
    const n = new Set(expanded);
    if (n.has(key)) n.delete(key); else n.add(key);
    setExpanded(n);
  }

  // ─── Alternate-supplier actions ──────────────────────────────────
  async function onApproveAlt(id: number) {
    setBusyId(id);
    try { await approveAlternate(id); toast.success(t('srpAltApproved')); await reload(); }
    catch { toast.danger(t('srpActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onArchiveAlt(id: number) {
    const reason = await dialog.prompt({
      title: t('srpArchivePrompt'),
      label: isAr ? 'سبب الأرشفة' : 'Archive reason',
      type: 'textarea', required: true,
    });
    if (!reason) return;
    setBusyId(id);
    try { await archiveAlternate(id, reason); toast.success(t('srpAltArchived')); await reload(); }
    catch { toast.danger(t('srpActionFailed')); }
    finally { setBusyId(null); }
  }

  // ─── Exit-plan actions ──────────────────────────────────────────
  async function onApprovePlan(id: number) {
    setBusyId(id);
    try { await approvePlan(id); toast.success(t('srpPlanApproved')); await reload(); }
    catch { toast.danger(t('srpActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onRejectPlan(id: number) {
    const reason = await dialog.prompt({
      title: t('srpRejectPrompt'),
      label: isAr ? 'سبب الرفض' : 'Rejection reason',
      type: 'textarea', required: true,
    });
    if (!reason) return;
    setBusyId(id);
    try { await rejectPlan(id, reason); toast.success(t('srpPlanRejected')); await reload(); }
    catch { toast.danger(t('srpActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onActivatePlan(id: number) {
    const reason = await dialog.prompt({
      title: t('srpActivatePrompt'),
      label: isAr ? 'سبب التفعيل' : 'Activation reason',
      type: 'textarea', required: true,
    });
    if (!reason) return;
    setBusyId(id);
    try { await activatePlan(id, reason); toast.success(t('srpPlanActivated')); await reload(); }
    catch { toast.danger(t('srpActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onArchivePlan(id: number) {
    const reason = await dialog.prompt({
      title: t('srpArchivePrompt'),
      label: isAr ? 'سبب الأرشفة' : 'Archive reason',
      type: 'textarea', required: true,
    });
    if (!reason) return;
    setBusyId(id);
    try { await archivePlan(id, reason); toast.success(t('srpPlanArchived')); await reload(); }
    catch { toast.danger(t('srpActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onRecordTest(id: number) {
    // Three-way outcome — kept as a text prompt for now since the legacy
    // input was a plain text field. A select-style dialog upgrade can
    // come when we promote dialog.prompt() with `kind: 'select'` support.
    const outcome = await dialog.prompt({
      title: t('srpTestPrompt'),
      label: isAr ? 'النتيجة (PASSED · GAP_IDENTIFIED · FAILED)'
                  : 'Outcome (PASSED · GAP_IDENTIFIED · FAILED)',
      initial: 'PASSED',
      required: true,
      validate: (v) => {
        const up = v.trim().toUpperCase();
        if (up !== 'PASSED' && up !== 'GAP_IDENTIFIED' && up !== 'FAILED') {
          return t('srpTestInvalid');
        }
        return null;
      },
    });
    if (!outcome) return;
    setBusyId(id);
    try { await recordPlanTest(id, outcome.toUpperCase() as 'PASSED'); toast.success(t('srpTestRecorded')); await reload(); }
    catch { toast.danger(t('srpActionFailed')); }
    finally { setBusyId(null); }
  }

  // ─── Create modals ──────────────────────────────────────────────
  const ALT_FIELDS: FieldDef[] = [
    { kind: 'text', key: 'primaryVendorName',     label: t('srpAltPrimaryVendor') + ' *', required: true, span: 6 },
    { kind: 'text', key: 'alternateVendorName',   label: t('srpAltAltVendor') + ' *',     required: true, span: 6 },
    { kind: 'text', key: 'alternateVendorCountry', label: t('srpAltCountry'), placeholder: 'SAU', span: 3 },
    { kind: 'select', key: 'alternateVendorRole', label: t('srpAltRole') + ' *', required: true, span: 4,
      options: [
        { value: 'BACKUP_PRIMARY',  label: t('srpRolePrimary')  },
        { value: 'BACKUP_SECONDARY', label: t('srpRoleSecondary') },
        { value: 'TERTIARY',        label: t('srpRoleTertiary') },
      ] },
    { kind: 'select', key: 'contractualStatus', label: t('srpAltContractual'), span: 4,
      options: [
        { value: 'NONE',             label: t('srpContractNone')  },
        { value: 'MOU_SIGNED',       label: t('srpContractMou')   },
        { value: 'FRAMEWORK_ACTIVE', label: t('srpContractFwk')   },
      ] },
    { kind: 'textarea', key: 'serviceScopeEn',     label: t('srpAltScopeEn') + ' *', rows: 2, span: 12 },
    { kind: 'textarea', key: 'serviceScopeAr',     label: t('srpAltScopeAr'),        rows: 2, span: 12 },
    { kind: 'number', key: 'estimatedOnboardingDays', label: t('srpAltDays'), min: 1, max: 365, span: 6 },
    { kind: 'number', key: 'estimatedCostSar',     label: t('srpAltCostSar'), min: 0, span: 6 },
    { kind: 'textarea', key: 'notesEn',            label: t('srpAltNotesEn'), rows: 2, span: 12 },
  ];

  const PLAN_FIELDS: FieldDef[] = [
    { kind: 'text', key: 'vendorName', label: t('srpPlanVendor') + ' *', required: true, span: 8 },
    { kind: 'text', key: 'contractCode', label: t('srpPlanContract'),     placeholder: 'CON-2026-...', span: 4 },
    { kind: 'text', key: 'titleEn',    label: t('srpPlanTitleEn') + ' *', required: true, span: 6 },
    { kind: 'text', key: 'titleAr',    label: t('srpPlanTitleAr'),                       span: 6 },
    { kind: 'text', key: 'triggerConditions', label: t('srpPlanTriggers'),
      placeholder: 'BREACH_SLA_30D,COST_OVERRUN', span: 8 },
    { kind: 'select', key: 'severityThreshold', label: t('srpPlanSeverity'), span: 4,
      options: [
        { value: 'LOW', label: 'LOW' }, { value: 'MEDIUM', label: 'MEDIUM' },
        { value: 'HIGH', label: 'HIGH' }, { value: 'CRITICAL', label: 'CRITICAL' },
      ] },
    { kind: 'number', key: 'targetDisengagementDays', label: t('srpPlanTargetDays'), min: 1, max: 365, span: 6 },
    { kind: 'textarea', key: 'stepsJson', label: t('srpPlanStepsJson') + ' *', rows: 5, span: 12 },
    { kind: 'textarea', key: 'dataMigrationPlanEn', label: t('srpPlanMigration'), rows: 2, span: 12 },
  ];

  async function onCreateAlt(v: AltFormValues) {
    try {
      await createAlternate({
        primaryVendorName:      (v.primaryVendorName ?? '').trim(),
        alternateVendorName:    (v.alternateVendorName ?? '').trim(),
        alternateVendorCountry: (v.alternateVendorCountry ?? '').trim() || undefined,
        alternateVendorRole:    (v.alternateVendorRole as AltRole) ?? 'BACKUP_PRIMARY',
        serviceScopeEn:         (v.serviceScopeEn ?? '').trim(),
        serviceScopeAr:         (v.serviceScopeAr ?? '').trim() || undefined,
        estimatedOnboardingDays: v.estimatedOnboardingDays != null ? Number(v.estimatedOnboardingDays) : undefined,
        estimatedCostSar:        v.estimatedCostSar != null ? Number(v.estimatedCostSar) : undefined,
        contractualStatus:       v.contractualStatus as 'NONE' | 'MOU_SIGNED' | 'FRAMEWORK_ACTIVE' | undefined,
        notesEn:                 (v.notesEn ?? '').trim() || undefined,
      });
      toast.success(t('srpAltCreated'));
      setCreateAltOpen(false);
      await reload();
    } catch { toast.danger(t('srpActionFailed')); }
  }

  async function onCreatePlan(v: PlanFormValues) {
    try {
      await createExitPlan({
        vendorName:    (v.vendorName ?? '').trim(),
        contractCode:  (v.contractCode ?? '').trim() || undefined,
        titleEn:       (v.titleEn ?? '').trim(),
        titleAr:       (v.titleAr ?? '').trim() || undefined,
        triggerConditions: (v.triggerConditions ?? '').trim() || undefined,
        severityThreshold: v.severityThreshold as PlanSeverity | undefined,
        targetDisengagementDays: v.targetDisengagementDays != null ? Number(v.targetDisengagementDays) : undefined,
        stepsJson:     (v.stepsJson ?? '').trim(),
        dataMigrationPlanEn: (v.dataMigrationPlanEn ?? '').trim() || undefined,
      });
      toast.success(t('srpPlanCreated'));
      setCreatePlanOpen(false);
      await reload();
    } catch { toast.danger(t('srpActionFailed')); }
  }

  // ─── Render ─────────────────────────────────────────────────────
  return (
    <Shell title={t('srpPageTitle')} sub={t('srpPageSub')}>
      <PageHeader
        eyebrow={t('navSupply')}
        title={t('srpPageTitle')}
        sub={t('srpPageSub')}
        actions={
          tab === 'alternates'
            ? <Button variant="primary" Icon={Plus} onClick={() => setCreateAltOpen(true)}>{t('srpNewAlt')}</Button>
            : <Button variant="primary" Icon={Plus} onClick={() => setCreatePlanOpen(true)}>{t('srpNewPlan')}</Button>
        }
      />

      <div className="kpi-grid">
        <Kpi Icon={Layers}        label={t('srpKpiAltApproved')}    value={stats.altApproved}    tone="success" />
        <Kpi Icon={ShieldCheck}   label={t('srpKpiAltReady')}       value={stats.altReady}       tone="primary" />
        <Kpi Icon={AlertTriangle} label={t('srpKpiAltGap')}         value={stats.altGap}         tone="warning" />
        <Kpi Icon={Wrench}        label={t('srpKpiPlansApproved')}  value={stats.plansApproved} tone="success" />
        <Kpi Icon={Play}          label={t('srpKpiPlansActivated')} value={stats.plansActivated} tone="primary" />
        <Kpi Icon={FlaskConical}  label={t('srpKpiTestOverdue')}    value={stats.plansOverdueTest} tone="danger" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'alternates' ? (
        <Card title={t('srpTabAlts')} Icon={Layers}>
          {loading
            ? <EmptyState Icon={Layers} title={t('loading')} />
            : alts.length === 0
              ? <EmptyState Icon={Layers} title={t('srpAltEmpty')} sub={t('srpAltEmptyHint')} />
              : (
                <table className="grc-table">
                  <thead>
                    <tr>
                      <th style={{ width: 28 }} />
                      <th>{t('srpAltPrimaryVendor')}</th>
                      <th>{t('srpAltAltVendor')}</th>
                      <th>{t('srpAltRole')}</th>
                      <th>{t('srpAltOutcome')}</th>
                      <th>{t('srpAltContractual')}</th>
                      <th style={{ textAlign: 'right' }}>{t('srpAltDaysShort')}</th>
                      <th style={{ textAlign: 'right' }}>{t('srpAltCostShort')}</th>
                      <th>{t('scnColStatus')}</th>
                      <th style={{ textAlign: 'right' }}>{t('scnColActions')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {alts.map(a => {
                      const key = `alt-${a.id}`;
                      const open = expanded.has(key);
                      return (
                        <Fragment key={a.id}>
                          <tr>
                            <td>
                              <button onClick={() => toggleExpanded(key)}
                                style={{ background: 'none', border: 0, cursor: 'pointer', padding: 0 }}>
                                {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                              </button>
                            </td>
                            <td>{a.primaryVendorName}</td>
                            <td>{a.alternateVendorName}</td>
                            <td>{t(`srpRole_${a.alternateVendorRole}`)}</td>
                            <td>
                              {a.lastAssessedOutcome
                                ? <Badge tone={ALT_OUTCOME_TONE[a.lastAssessedOutcome]}>
                                    {t(`srpOutcome_${a.lastAssessedOutcome}`)}
                                  </Badge>
                                : <span style={{ color: 'var(--muted-foreground)' }}>—</span>}
                            </td>
                            <td>{t(`srpContract_${a.contractualStatus}`)}</td>
                            <td style={{ textAlign: 'right' }} className="mono">
                              {a.estimatedOnboardingDays != null ? `${a.estimatedOnboardingDays}d` : '—'}
                            </td>
                            <td style={{ textAlign: 'right' }} className="mono">
                              {fmtSar(a.estimatedCostSar)}
                            </td>
                            <td><Badge tone={ALT_STATUS_TONE[a.status]}>{t(`scnStatus_${a.status}`)}</Badge></td>
                            <td style={{ textAlign: 'right' }}>
                              {a.status === 'DRAFT' && (
                                <Button size="sm" variant="primary" Icon={CheckCircle2}
                                  disabled={busyId === a.id} onClick={() => onApproveAlt(a.id)}>
                                  {t('scnApprove')}
                                </Button>
                              )}
                              {(a.status === 'DRAFT' || a.status === 'APPROVED') && (
                                <>{' '}
                                  <Button size="sm" variant="ghost" Icon={Archive}
                                    disabled={busyId === a.id} onClick={() => onArchiveAlt(a.id)}>
                                    {t('scnArchive')}
                                  </Button>
                                </>
                              )}
                            </td>
                          </tr>
                          {open && (
                            <tr>
                              <td colSpan={10} style={{ background: 'var(--muted)' }}>
                                <div style={{ padding: 12, fontSize: 13 }}>
                                  <p style={{ marginBottom: 6 }}>
                                    <strong>{t('srpAltScopeEn')}:</strong>{' '}
                                    {lang === 'ar' && a.serviceScopeAr ? a.serviceScopeAr : a.serviceScopeEn}
                                  </p>
                                  {a.notesEn && (
                                    <p style={{ marginBottom: 6 }}>
                                      <strong>{t('srpAltNotesEn')}:</strong>{' '}
                                      {lang === 'ar' && a.notesAr ? a.notesAr : a.notesEn}
                                    </p>
                                  )}
                                  {a.archiveReason && (
                                    <p style={{ color: 'var(--muted-foreground)' }}>
                                      <strong>{t('scnArchiveReason')}:</strong> {a.archiveReason}
                                    </p>
                                  )}
                                </div>
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      );
                    })}
                  </tbody>
                </table>
              )}
        </Card>
      ) : (
        <Card title={t('srpTabPlans')} Icon={Wrench}>
          {loading
            ? <EmptyState Icon={Wrench} title={t('loading')} />
            : plans.length === 0
              ? <EmptyState Icon={Wrench} title={t('srpPlanEmpty')} sub={t('srpPlanEmptyHint')} />
              : (
                <table className="grc-table">
                  <thead>
                    <tr>
                      <th style={{ width: 28 }} />
                      <th>{t('srpPlanCode')}</th>
                      <th>{t('srpPlanVendor')}</th>
                      <th>{t('srpPlanTitle')}</th>
                      <th>{t('srpPlanSeverity')}</th>
                      <th style={{ textAlign: 'right' }}>{t('srpPlanDaysShort')}</th>
                      <th>{t('srpPlanLastTest')}</th>
                      <th>{t('scnColStatus')}</th>
                      <th style={{ textAlign: 'right' }}>{t('scnColActions')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {plans.map(p => {
                      const key = `plan-${p.id}`;
                      const open = expanded.has(key);
                      const steps = parseExitPlanSteps(p.stepsJson);
                      return (
                        <Fragment key={p.id}>
                          <tr>
                            <td>
                              <button onClick={() => toggleExpanded(key)}
                                style={{ background: 'none', border: 0, cursor: 'pointer', padding: 0 }}>
                                {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                              </button>
                            </td>
                            <td className="mono">{p.planCode}</td>
                            <td>{p.vendorName}</td>
                            <td>{lang === 'ar' && p.titleAr ? p.titleAr : p.titleEn}</td>
                            <td>
                              {p.severityThreshold
                                ? <Badge tone={p.severityThreshold === 'CRITICAL' ? 'danger'
                                              : p.severityThreshold === 'HIGH' ? 'warning'
                                              : 'info'}>{p.severityThreshold}</Badge>
                                : '—'}
                            </td>
                            <td style={{ textAlign: 'right' }} className="mono">{p.targetDisengagementDays}d</td>
                            <td>
                              {p.lastTestedOutcome
                                ? <Badge tone={PLAN_TEST_TONE[p.lastTestedOutcome]}>
                                    {t(`srpTest_${p.lastTestedOutcome}`)}
                                  </Badge>
                                : <Badge tone="info">{t('srpTest_NOT_TESTED')}</Badge>}
                            </td>
                            <td><Badge tone={PLAN_STATUS_TONE[p.status]}>{t(`scnStatus_${p.status}`)}</Badge></td>
                            <td style={{ textAlign: 'right' }}>
                              {p.status === 'DRAFT' && (
                                <>
                                  <Button size="sm" variant="primary" Icon={CheckCircle2}
                                    disabled={busyId === p.id} onClick={() => onApprovePlan(p.id)}>
                                    {t('scnApprove')}
                                  </Button>
                                  {' '}
                                  <Button size="sm" variant="danger" Icon={XCircle}
                                    disabled={busyId === p.id} onClick={() => onRejectPlan(p.id)}>
                                    {t('scnReject')}
                                  </Button>
                                </>
                              )}
                              {p.status === 'APPROVED' && (
                                <Button size="sm" variant="primary" Icon={Play}
                                  disabled={busyId === p.id} onClick={() => onActivatePlan(p.id)}>
                                  {t('srpPlanActivate')}
                                </Button>
                              )}
                              {(p.status === 'APPROVED' || p.status === 'ACTIVATED' || p.status === 'DRAFT') && (
                                <>{' '}
                                  <Button size="sm" variant="ghost" Icon={FlaskConical}
                                    disabled={busyId === p.id} onClick={() => onRecordTest(p.id)}>
                                    {t('srpPlanRecordTest')}
                                  </Button>
                                </>
                              )}
                              {p.status !== 'ARCHIVED' && p.status !== 'REJECTED' && (
                                <>{' '}
                                  <Button size="sm" variant="ghost" Icon={Archive}
                                    disabled={busyId === p.id} onClick={() => onArchivePlan(p.id)}>
                                    {t('scnArchive')}
                                  </Button>
                                </>
                              )}
                            </td>
                          </tr>
                          {open && (
                            <tr>
                              <td colSpan={9} style={{ background: 'var(--muted)' }}>
                                <div style={{ padding: 12, fontSize: 13 }}>
                                  {p.triggerConditions && (
                                    <p style={{ marginBottom: 6 }}>
                                      <strong>{t('srpPlanTriggers')}:</strong>{' '}
                                      <span className="mono">{p.triggerConditions}</span>
                                    </p>
                                  )}
                                  {steps.length > 0 && (
                                    <div style={{ marginBottom: 6 }}>
                                      <strong>{t('srpPlanSteps')}</strong>
                                      <ol style={{ paddingInlineStart: 18, marginTop: 4 }}>
                                        {steps.map(s => (
                                          <li key={s.seq} style={{ marginBottom: 3 }}>
                                            {lang === 'ar' && s.actionAr ? s.actionAr : s.actionEn}
                                            <span style={{ color: 'var(--muted-foreground)' }}>
                                              {' · '}{s.owner}{' · '}{s.days}d
                                              {s.doneAt ? ` · ✓ ${new Date(s.doneAt).toLocaleDateString()}` : ''}
                                            </span>
                                          </li>
                                        ))}
                                      </ol>
                                    </div>
                                  )}
                                  {p.dataMigrationPlanEn && (
                                    <p style={{ marginBottom: 6 }}>
                                      <strong>{t('srpPlanMigration')}:</strong>{' '}
                                      {lang === 'ar' && p.dataMigrationPlanAr ? p.dataMigrationPlanAr : p.dataMigrationPlanEn}
                                    </p>
                                  )}
                                  {p.nextTestDue && (
                                    <p style={{ color: 'var(--muted-foreground)' }}>
                                      <strong>{t('srpPlanNextTest')}:</strong>{' '}
                                      <span className="mono">{p.nextTestDue}</span>
                                    </p>
                                  )}
                                  {p.activationReason && (
                                    <p style={{ color: 'var(--primary)' }}>
                                      <strong>{t('srpPlanActivationReason')}:</strong> {p.activationReason}
                                    </p>
                                  )}
                                  {p.rejectionReason && (
                                    <p style={{ color: 'var(--danger)' }}>
                                      <strong>{t('scnRejectionReason')}:</strong> {p.rejectionReason}
                                    </p>
                                  )}
                                </div>
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      );
                    })}
                  </tbody>
                </table>
              )}
        </Card>
      )}

      <FormModal<AltFormValues>
        open={createAltOpen}
        title={t('srpNewAlt')}
        fields={ALT_FIELDS}
        initial={ALT_INITIAL}
        onClose={() => setCreateAltOpen(false)}
        onSubmit={onCreateAlt}
      />
      <FormModal<PlanFormValues>
        open={createPlanOpen}
        title={t('srpNewPlan')}
        fields={PLAN_FIELDS}
        initial={PLAN_INITIAL}
        onClose={() => setCreatePlanOpen(false)}
        onSubmit={onCreatePlan}
      />
    </Shell>
  );
}
