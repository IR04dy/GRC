import { useEffect, useMemo, useState, Fragment } from 'react';
import {
  Server, CheckCircle2, XCircle, Archive, Play, FlaskConical, Network,
  ChevronRight, ChevronDown, Clock, Plus,
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
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { useI18n } from '../../lib/i18n';
import { statusLabel } from '../../lib/statusLabels';
import { useToast } from '../../lib/toast';
import {
  listDrPlans, approveDrPlan, rejectDrPlan, activateDrPlan, archiveDrPlan, recordDrTest, createDrPlan,
  parseDrSteps, parseDrDependencies,
  DR_STATUS_TONE, DR_TEST_TONE, DR_SITE_TONE,
  type DrPlan, type DrSiteType,
} from '../../lib/drPlans';

const DR_SITE_TYPES: DrSiteType[] = ['HOT', 'WARM', 'COLD', 'ACTIVE_ACTIVE', 'CLOUD_DR'];
const DR_SITE_LABEL_AR: Record<DrSiteType, string> = {
  HOT: 'موقع ساخن', WARM: 'موقع دافئ', COLD: 'موقع بارد',
  ACTIVE_ACTIVE: 'نشط-نشط', CLOUD_DR: 'تعافٍ سحابي',
};

type TabId = 'all' | 'draft' | 'approved' | 'activated' | 'archived' | 'rejected';

export function DrPlansPage() {
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('all');
  const [rows, setRows] = useState<DrPlan[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [openCreate, setOpenCreate] = useState(false);

  useEffect(() => { void reload(); }, []);

  async function reload() {
    setLoading(true);
    try {
      const page = await listDrPlans(undefined, 0, 200);
      setRows(page.content);
    } catch { toast.danger(t('drpLoadFailed')); }
    finally { setLoading(false); }
  }

  const stats = useMemo(() => {
    const s = { DRAFT:0, APPROVED:0, ACTIVATED:0, ARCHIVED:0, REJECTED:0,
                testedPassed:0, testedGap:0, overdue:0 };
    const today = new Date();
    rows.forEach(r => {
      s[r.status]++;
      if (r.lastTestedOutcome === 'PASSED') s.testedPassed++;
      if (r.lastTestedOutcome === 'GAP' || r.lastTestedOutcome === 'FAILED') s.testedGap++;
      if (r.nextTestDue && r.status !== 'ARCHIVED' && r.status !== 'REJECTED'
          && new Date(r.nextTestDue) < today) s.overdue++;
    });
    return s;
  }, [rows]);

  const filtered = useMemo(() => {
    if (tab === 'all') return rows;
    return rows.filter(r => r.status === tab.toUpperCase());
  }, [rows, tab]);

  const tabs: TabItem<TabId>[] = [
    { id: 'all',       label: `${t('drpTabAll')} (${rows.length})` },
    { id: 'draft',     label: `${t('drpTabDraft')} (${stats.DRAFT})` },
    { id: 'approved',  label: `${t('drpTabApproved')} (${stats.APPROVED})` },
    { id: 'activated', label: `${t('drpTabActivated')} (${stats.ACTIVATED})` },
    { id: 'archived',  label: `${t('drpTabArchived')} (${stats.ARCHIVED})` },
    { id: 'rejected',  label: `${t('drpTabRejected')} (${stats.REJECTED})` },
  ];

  function toggle(id: number) {
    const n = new Set(expanded);
    if (n.has(id)) n.delete(id); else n.add(id);
    setExpanded(n);
  }

  async function onApprove(id: number) {
    setBusyId(id);
    try { await approveDrPlan(id); toast.success(t('drpApproved')); await reload(); }
    catch { toast.danger(t('drpActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onReject(id: number) {
    const reason = window.prompt(t('drpRejectPrompt'), '');
    if (!reason || !reason.trim()) return;
    setBusyId(id);
    try { await rejectDrPlan(id, reason.trim()); toast.success(t('drpRejected')); await reload(); }
    catch { toast.danger(t('drpActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onActivate(id: number) {
    const reason = window.prompt(t('drpActivatePrompt'), '');
    if (!reason || !reason.trim()) return;
    setBusyId(id);
    try { await activateDrPlan(id, reason.trim()); toast.success(t('drpActivated')); await reload(); }
    catch { toast.danger(t('drpActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onArchive(id: number) {
    const reason = window.prompt(t('drpArchivePrompt'), '');
    if (!reason || !reason.trim()) return;
    setBusyId(id);
    try { await archiveDrPlan(id, reason.trim()); toast.success(t('drpArchived')); await reload(); }
    catch { toast.danger(t('drpActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onRecordTest(id: number) {
    const outcome = window.prompt(t('drpTestPrompt'), 'PASSED');
    if (!outcome) return;
    const up = outcome.toUpperCase();
    if (up !== 'PASSED' && up !== 'GAP' && up !== 'FAILED') {
      toast.danger(t('drpTestInvalid')); return;
    }
    setBusyId(id);
    try { await recordDrTest(id, up as 'PASSED'); toast.success(t('drpTestRecorded')); await reload(); }
    catch { toast.danger(t('drpActionFailed')); }
    finally { setBusyId(null); }
  }

  // ---- New DR plan ---------------------------------------------------------
  const createFields: FieldDef[] = [
    { key: 'titleEn', kind: 'text', label: isAr ? 'العنوان (إنجليزي)' : 'Title (English)', required: true, span: 6, maxLength: 255 },
    { key: 'titleAr', kind: 'text', label: isAr ? 'العنوان (عربي)' : 'Title (Arabic)', span: 6, maxLength: 255 },
    { key: 'criticalSystemName', kind: 'text', label: isAr ? 'النظام الحرج' : 'Critical system', span: 6,
      placeholder: isAr ? 'مثال: نظام المدفوعات الفورية' : 'e.g. Instant Payments (IPS)' },
    { key: 'drSiteType', kind: 'select', label: isAr ? 'نوع موقع التعافي' : 'DR site type', required: true, span: 6,
      options: DR_SITE_TYPES.map(s => ({ value: s, label: isAr ? DR_SITE_LABEL_AR[s] : s.replace('_', ' ') })) },
    { key: 'rtoHours', kind: 'number', label: isAr ? 'زمن التعافي المستهدف RTO (ساعات)' : 'RTO (hours)', required: true, span: 4, min: 0 },
    { key: 'rpoHours', kind: 'number', label: isAr ? 'نقطة التعافي المستهدفة RPO (ساعات)' : 'RPO (hours)', required: true, span: 4, min: 0 },
    { key: 'mtpdHours', kind: 'number', label: isAr ? 'أقصى مدة تعطل MTPD (ساعات)' : 'MTPD (hours)', span: 4, min: 0 },
    { key: 'owner', kind: 'text', label: isAr ? 'المالك' : 'Owner', span: 6 },
    { key: 'linkedBcpCode', kind: 'text', label: isAr ? 'رمز خطة الاستمرارية المرتبطة' : 'Linked BCP code', span: 6,
      placeholder: isAr ? 'مثال: BCP-IPS-01' : 'e.g. BCP-IPS-01' },
    { key: 'drSiteLocation', kind: 'text', label: isAr ? 'موقع التعافي' : 'DR site location', span: 6,
      placeholder: isAr ? 'مثال: الرياض - مركز البيانات الثانوي' : 'e.g. Riyadh secondary DC' },
    { key: 'drSiteProvider', kind: 'text', label: isAr ? 'مزود موقع التعافي' : 'DR site provider', span: 6,
      placeholder: isAr ? 'مثال: STC Cloud' : 'e.g. STC Cloud' },
    { key: 'descriptionEn', kind: 'textarea', label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)', span: 12, rows: 2 },
    { key: 'descriptionAr', kind: 'textarea', label: isAr ? 'الوصف (عربي)' : 'Description (Arabic)', span: 12, rows: 2 },
    { key: 'recoveryStrategyEn', kind: 'textarea', label: isAr ? 'استراتيجية التعافي (إنجليزي)' : 'Recovery strategy (English)', span: 12, rows: 2 },
    { key: 'dataBackupStrategyEn', kind: 'textarea', label: isAr ? 'استراتيجية النسخ الاحتياطي (إنجليزي)' : 'Data backup strategy (English)', span: 12, rows: 2 },
  ];

  // Memoised so its identity is stable across re-renders, keeping FormModal's reset effect from wiping the form.
  const createInitial: Record<string, string> = useMemo(() => ({
    titleEn: '', titleAr: '', criticalSystemName: '', drSiteType: 'WARM',
    rtoHours: '4', rpoHours: '1', mtpdHours: '', owner: '', linkedBcpCode: '',
    drSiteLocation: '', drSiteProvider: '',
    descriptionEn: '', descriptionAr: '', recoveryStrategyEn: '', dataBackupStrategyEn: '',
  }), []);

  async function submitCreate(v: Record<string, string>) {
    const titleEn = String(v.titleEn ?? '').trim();
    const drSiteType = v.drSiteType as DrSiteType;
    const rto = Number(v.rtoHours);
    const rpo = Number(v.rpoHours);
    if (!titleEn)                       { toast.danger(isAr ? 'العنوان (إنجليزي) مطلوب' : 'Title (English) is required'); return; }
    if (!drSiteType)                    { toast.danger(isAr ? 'نوع موقع التعافي مطلوب' : 'DR site type is required'); return; }
    if (!Number.isFinite(rto) || rto < 0) { toast.danger(isAr ? 'قيمة RTO غير صالحة' : 'Valid RTO (hours) is required'); return; }
    if (!Number.isFinite(rpo) || rpo < 0) { toast.danger(isAr ? 'قيمة RPO غير صالحة' : 'Valid RPO (hours) is required'); return; }
    const optS = (s?: string) => { const x = String(s ?? '').trim(); return x ? x : undefined; };
    const optN = (s?: string) => { const x = String(s ?? '').trim(); return x ? Number(x) : undefined; };
    try {
      await createDrPlan({
        titleEn, titleAr: optS(v.titleAr),
        descriptionEn: optS(v.descriptionEn), descriptionAr: optS(v.descriptionAr),
        linkedBcpCode: optS(v.linkedBcpCode), criticalSystemName: optS(v.criticalSystemName), owner: optS(v.owner),
        rtoHours: rto, rpoHours: rpo, mtpdHours: optN(v.mtpdHours),
        drSiteType,
        drSiteLocation: optS(v.drSiteLocation), drSiteProvider: optS(v.drSiteProvider),
        recoveryStrategyEn: optS(v.recoveryStrategyEn),
        dataBackupStrategyEn: optS(v.dataBackupStrategyEn),
      });
      toast.success(isAr ? 'تم إنشاء خطة التعافي (مسودة)' : 'DR plan created (Draft)');
      setOpenCreate(false);
      setTab('draft');
      await reload();
    } catch { toast.danger(t('drpActionFailed')); }
  }

  return (
    <Shell title={t('drpPageTitle')} sub={t('drpPageSub')}>
      <PageHeader eyebrow={t('navBcm')} title={t('drpPageTitle')} sub={t('drpPageSub')} />

      <div className="kpi-grid">
        <Kpi Icon={Server}       label={t('drpKpiApproved')}   value={stats.APPROVED + stats.ACTIVATED} tone="success"
             onClick={() => setTab('approved')} active={tab === 'approved'} />
        <Kpi Icon={Play}          label={t('drpKpiActivated')}  value={stats.ACTIVATED} tone="primary"
             onClick={() => setTab('activated')} active={tab === 'activated'} />
        <Kpi Icon={FlaskConical}  label={t('drpKpiPassed')}     value={stats.testedPassed} tone="success" to="/resilience-tests" />
        <Kpi Icon={Clock}         label={t('drpKpiOverdue')}    value={stats.overdue} tone="danger" to="/resilience-tests" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      <Card title={t('drpPageTitle')} Icon={Server}
        action={
          <Button size="sm" variant="primary" Icon={Plus} onClick={() => setOpenCreate(true)}>
            {isAr ? 'خطة تعافٍ جديدة' : 'New DR plan'}
          </Button>
        }
      >
        {loading
          ? <EmptyState Icon={Server} title={t('loading')} />
          : filtered.length === 0
            ? <EmptyState Icon={Server} title={t('drpEmpty')} sub={t('drpEmptyHint')} />
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th style={{ width: 28 }} />
                    <th>{t('drpColCode')}</th>
                    <th>{t('drpColTitle')}</th>
                    <th>{t('drpColSystem')}</th>
                    <th>{t('drpColSite')}</th>
                    <th style={{ textAlign: 'right' }}>RTO</th>
                    <th style={{ textAlign: 'right' }}>RPO</th>
                    <th>{t('drpColLastTest')}</th>
                    <th>{t('scnColStatus')}</th>
                    <th style={{ textAlign: 'right' }}>{t('scnColActions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(r => {
                    const open = expanded.has(r.id);
                    const failover = parseDrSteps(r.failoverStepsJson);
                    const failback = parseDrSteps(r.failbackStepsJson);
                    const deps = parseDrDependencies(r.dependenciesJson);
                    return (
                      <Fragment key={r.id}>
                        <tr>
                          <td>
                            <button onClick={() => toggle(r.id)}
                              style={{ background: 'none', border: 0, cursor: 'pointer', padding: 0 }}>
                              {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                            </button>
                          </td>
                          <td className="mono">{r.planCode}</td>
                          <td>{lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn}</td>
                          <td>{r.criticalSystemName ?? '—'}</td>
                          <td><Badge tone={DR_SITE_TONE[r.drSiteType]}>{r.drSiteType.replace('_',' ')}</Badge></td>
                          <td style={{ textAlign: 'right' }} className="mono">{r.rtoHours}h</td>
                          <td style={{ textAlign: 'right' }} className="mono">{r.rpoHours}h</td>
                          <td>
                            {r.lastTestedOutcome
                              ? <Badge tone={DR_TEST_TONE[r.lastTestedOutcome]}>{r.lastTestedOutcome}</Badge>
                              : <Badge tone="info">NOT TESTED</Badge>}
                          </td>
                          <td><Badge tone={DR_STATUS_TONE[r.status]}>{statusLabel(r.status, lang)}</Badge></td>
                          <td style={{ textAlign: 'right' }}>
                            {r.status === 'DRAFT' && (
                              <>
                                <Button size="sm" variant="primary" Icon={CheckCircle2}
                                  disabled={busyId === r.id} onClick={() => onApprove(r.id)}>
                                  {t('scnApprove')}
                                </Button>
                                {' '}
                                <Button size="sm" variant="danger" Icon={XCircle}
                                  disabled={busyId === r.id} onClick={() => onReject(r.id)}>
                                  {t('scnReject')}
                                </Button>
                              </>
                            )}
                            {r.status === 'APPROVED' && (
                              <Button size="sm" variant="primary" Icon={Play}
                                disabled={busyId === r.id} onClick={() => onActivate(r.id)}>
                                {t('drpActivate')}
                              </Button>
                            )}
                            {(r.status === 'APPROVED' || r.status === 'ACTIVATED' || r.status === 'DRAFT') && (
                              <>{' '}
                                <Button size="sm" variant="ghost" Icon={FlaskConical}
                                  disabled={busyId === r.id} onClick={() => onRecordTest(r.id)}>
                                  {t('drpRecordTest')}
                                </Button>
                              </>
                            )}
                            {r.status !== 'ARCHIVED' && r.status !== 'REJECTED' && (
                              <>{' '}
                                <Button size="sm" variant="ghost" Icon={Archive}
                                  disabled={busyId === r.id} onClick={() => onArchive(r.id)}>
                                  {t('scnArchive')}
                                </Button>
                              </>
                            )}
                          </td>
                        </tr>
                        {open && (
                          <tr>
                            <td colSpan={10} style={{ background: 'var(--surface-2)' }}>
                              <div style={{ padding: 12, fontSize: 13 }}>
                                {r.descriptionEn && (
                                  <p style={{ marginBottom: 6 }}>
                                    {lang === 'ar' && r.descriptionAr ? r.descriptionAr : r.descriptionEn}
                                  </p>
                                )}
                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                                  <div>
                                    <strong>{t('drpSite')}:</strong> {r.drSiteLocation ?? '—'}
                                    {' · '}
                                    <strong>{t('drpProvider')}:</strong> {r.drSiteProvider ?? '—'}
                                    <br />
                                    <strong>{t('drpOwner')}:</strong> {r.owner ?? '—'}
                                    {r.linkedBcpCode && <><br /><strong>{t('drpBcp')}:</strong> <span className="mono">{r.linkedBcpCode}</span></>}
                                    {r.mtpdHours && <><br /><strong>MTPD:</strong> <span className="mono">{r.mtpdHours}h</span></>}
                                  </div>
                                  <div>
                                    {r.nextTestDue && <><strong>{t('drpNextTest')}:</strong> <span className="mono">{r.nextTestDue}</span><br /></>}
                                    {r.lastTestedAt && (
                                      <><strong>{t('drpLastTested')}:</strong>{' '}
                                      <span className="mono">{new Date(r.lastTestedAt).toLocaleDateString()}</span><br /></>
                                    )}
                                    {r.lastTestNotesEn && (
                                      <span style={{ color: 'var(--ink-3)' }}>{r.lastTestNotesEn}</span>
                                    )}
                                  </div>
                                </div>

                                {/* B-C: DR backup / test evidence — real file upload via the generic AttachmentService. */}
                                <div style={{ marginTop: 10 }}>
                                  <strong>{lang === 'ar' ? 'أدلة النسخ الاحتياطي والاختبار' : 'Backup & test evidence'}</strong>
                                  <AttachmentsField entityType="DR_PLAN" entityId={r.id} />
                                </div>

                                {r.recoveryStrategyEn && (
                                  <p style={{ marginTop: 8 }}>
                                    <strong>{t('drpStrategy')}:</strong>{' '}
                                    {lang === 'ar' && r.recoveryStrategyAr ? r.recoveryStrategyAr : r.recoveryStrategyEn}
                                  </p>
                                )}

                                {deps.length > 0 && (
                                  <div style={{ marginTop: 8 }}>
                                    <strong>{t('drpDependencies')}</strong>
                                    <ul style={{ paddingInlineStart: 18, marginTop: 4 }}>
                                      {deps.map((d, i) => (
                                        <li key={i}>
                                          <Network size={12} style={{ display:'inline', marginInlineEnd:4 }}/>
                                          {d.system} {d.type && <span style={{ color:'var(--ink-3)' }}>— {d.type}</span>}
                                          {d.criticality && <Badge tone={d.criticality === 'CRITICAL' ? 'danger' : d.criticality === 'HIGH' ? 'warning' : 'info'}> {d.criticality}</Badge>}
                                        </li>
                                      ))}
                                    </ul>
                                  </div>
                                )}

                                {failover.length > 0 && (
                                  <div style={{ marginTop: 8 }}>
                                    <strong>{t('drpFailoverSteps')}</strong>
                                    <ol style={{ paddingInlineStart: 18, marginTop: 4 }}>
                                      {failover.map(s => (
                                        <li key={s.seq}>
                                          {lang === 'ar' && s.actionAr ? s.actionAr : s.actionEn}
                                          {s.owner && <span style={{ color:'var(--ink-3)' }}> — {s.owner}</span>}
                                          {s.minutesAllocated != null && <span className="mono"> ({s.minutesAllocated}m)</span>}
                                          {s.automated && <Badge tone="info"> AUTO</Badge>}
                                          {s.doneAt && <span style={{ color: 'var(--success)' }}> ✓ {new Date(s.doneAt).toLocaleString()}</span>}
                                        </li>
                                      ))}
                                    </ol>
                                  </div>
                                )}

                                {failback.length > 0 && (
                                  <div style={{ marginTop: 8 }}>
                                    <strong>{t('drpFailbackSteps')}</strong>
                                    <ol style={{ paddingInlineStart: 18, marginTop: 4 }}>
                                      {failback.map(s => (
                                        <li key={s.seq}>
                                          {lang === 'ar' && s.actionAr ? s.actionAr : s.actionEn}
                                          {s.owner && <span style={{ color:'var(--ink-3)' }}> — {s.owner}</span>}
                                          {s.minutesAllocated != null && <span className="mono"> ({s.minutesAllocated}m)</span>}
                                        </li>
                                      ))}
                                    </ol>
                                  </div>
                                )}

                                {r.dataBackupStrategyEn && (
                                  <p style={{ marginTop: 8 }}>
                                    <strong>{t('drpBackup')}:</strong>{' '}
                                    {lang === 'ar' && r.dataBackupStrategyAr ? r.dataBackupStrategyAr : r.dataBackupStrategyEn}
                                  </p>
                                )}

                                {r.activationReason && (
                                  <p style={{ marginTop: 6, color: 'var(--primary)' }}>
                                    <strong>{t('drpActivationReason')}:</strong> {r.activationReason}
                                  </p>
                                )}
                                {r.rejectionReason && (
                                  <p style={{ marginTop: 6, color: 'var(--danger)' }}>
                                    <strong>{t('scnRejectionReason')}:</strong> {r.rejectionReason}
                                  </p>
                                )}
                                {r.archiveReason && (
                                  <p style={{ marginTop: 6, color: 'var(--ink-3)' }}>
                                    <strong>{t('scnArchiveReason')}:</strong> {r.archiveReason}
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
            )
        }
      </Card>

      <FormModal
        open={openCreate}
        title={isAr ? 'خطة تعافٍ من الكوارث جديدة' : 'New disaster-recovery plan'}
        fields={createFields}
        initial={createInitial}
        submitLabel={isAr ? 'إنشاء' : 'Create'}
        onClose={() => setOpenCreate(false)}
        onSubmit={submitCreate}
      />
    </Shell>
  );
}
