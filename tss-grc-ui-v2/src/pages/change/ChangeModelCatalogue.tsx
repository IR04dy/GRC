/**
 * S8e / CE-2 — the change model catalogue.
 *
 * **What this card refuses to do.** It never shows a green "pre-approved" chip for a model whose
 * authorisation has lapsed, and it never offers "create and approve" as one action. Both would be
 * lies the server already refuses: `preApproved` is meaningless without `status === 'APPROVED'` and
 * an `authorisedUntil` in the future, and the author of a model may not approve it (SoD).
 *
 * The expiry is on screen for every approved model, because a pre-approval nobody can see the end of
 * is the F2-62 shape — a control that never comes due.
 */
import { useEffect, useState } from 'react';
import { BookCheck, Plus, ShieldCheck, PauseCircle, Archive } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import { changeApi, ApiError, type ChangeModelDto } from '../../lib/api';

/** Tone by status. SUSPENDED is a warning, not a danger: it is a deliberate, reversible act. */
export const MODEL_STATUS_TONE: Record<string, BadgeTone> = {
  DRAFT: 'neutral',
  APPROVED: 'success',
  SUSPENDED: 'warning',
  RETIRED: 'neutral',
};

/** Days a pre-approval may run for. Mirrors ChangeModelService.MIN/MAX_VALID_DAYS. */
export const MIN_VALID_DAYS = 30;
export const MAX_VALID_DAYS = 730;

/**
 * The only correct way to ask the question in the UI. Kept exported so a test can pin it: a model
 * that is APPROVED with a past expiry must NOT read as pre-approved.
 */
export function isLivePreApproval(m: ChangeModelDto, now = new Date()): boolean {
  return m.preApproved === true
    && m.status === 'APPROVED'
    && !!m.authorisedUntil
    && new Date(m.authorisedUntil) > now;
}

export function ChangeModelCatalogue({ toast }: { toast: ReturnType<typeof useToast> }) {
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const dialog = useDialog();

  const [models, setModels] = useState<ChangeModelDto[]>([]);
  const [open, setOpen] = useState(false);

  async function load() {
    try { setModels(await changeApi.models()); }
    catch { /* silent — a backend-down window surfaces elsewhere on this page */ }
  }
  useEffect(() => { void load(); }, []);

  const fields: FieldDef[] = [
    { key: 'code',   kind: 'text', label: t('cmodelCode'),   required: true, maxLength: 64, span: 4 },
    { key: 'nameEn', kind: 'text', label: t('cmodelNameEn'), required: true, span: 8 },
    // House rule 7: Arabic is backend-mandatory (name_ar is NOT NULL in V354), so the form gates it
    // client-side rather than letting the server answer 400 for a field nobody was asked to fill.
    { key: 'nameAr', kind: 'text', label: t('cmodelNameAr'), required: true, span: 12 },
    { key: 'changeType', kind: 'select', label: t('cmodelType'), required: true, span: 6,
      options: [{ value: 'STANDARD', label: 'STANDARD' }, { value: 'NORMAL', label: 'NORMAL' },
                { value: 'EMERGENCY', label: 'EMERGENCY' }] },
    { key: 'riskDefault', kind: 'select', label: t('cmodelRisk'), span: 6,
      options: [{ value: '', label: '—' }, { value: 'LOW', label: 'LOW' },
                { value: 'MEDIUM', label: 'MEDIUM' }, { value: 'HIGH', label: 'HIGH' },
                { value: 'CRITICAL', label: 'CRITICAL' }] },
    { key: 'standardSteps',    kind: 'textarea', label: t('cmodelSteps'),    rows: 4, span: 12 },
    { key: 'rollbackTemplate', kind: 'textarea', label: t('cmodelRollback'), rows: 3, span: 6 },
    { key: 'testTemplate',     kind: 'textarea', label: t('cmodelTest'),     rows: 3, span: 6 },
  ];

  async function submit(v: Record<string, string>) {
    try {
      await changeApi.createModel({
        code: v.code, nameEn: v.nameEn, nameAr: v.nameAr,
        changeType: v.changeType || 'STANDARD',
        riskDefault: v.riskDefault || undefined,
        standardSteps: v.standardSteps || undefined,
        rollbackTemplate: v.rollbackTemplate || undefined,
        testTemplate: v.testTemplate || undefined,
      });
      setOpen(false);
      toast.success(t('cmodelCreated'));
      void load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('cmodelFailed'));
    }
  }

  async function approve(m: ChangeModelDto) {
    const days = await dialog.prompt({
      title: t('cmodelApproveTitle'),
      label: t('cmodelApproveLabel'),
      // The bounds are in the helper text, not only in the validator: a user should learn the rule
      // before being refused by it. Same range the service enforces (30–730).
      helpText: t('cmodelApproveBody').replace('{min}', String(MIN_VALID_DAYS))
                                      .replace('{max}', String(MAX_VALID_DAYS)),
      type: 'number', initial: '180', required: true,
      min: MIN_VALID_DAYS, max: MAX_VALID_DAYS,
      submitLabel: t('cmodelApproveBtn'),
    });
    if (days == null) return;
    const n = Number(days);
    if (!Number.isFinite(n) || n < MIN_VALID_DAYS || n > MAX_VALID_DAYS) {
      toast.danger(t('cmodelApproveRange').replace('{min}', String(MIN_VALID_DAYS))
                                          .replace('{max}', String(MAX_VALID_DAYS)));
      return;
    }
    try {
      await changeApi.approveModel(m.id, n);
      toast.success(t('cmodelApproved'));
      void load();
    } catch (e) {
      // The server refuses the author's own approval with an SoD message — show it verbatim rather
      // than a generic failure, because "you cannot approve this" is the useful sentence.
      toast.danger(e instanceof ApiError ? e.message : t('cmodelFailed'));
    }
  }

  async function setStatus(m: ChangeModelDto, status: 'SUSPENDED' | 'RETIRED') {
    const ok = await dialog.confirm({
      title: status === 'RETIRED' ? t('cmodelRetireTitle') : t('cmodelSuspendTitle'),
      body: `${m.code} — ${isAr && m.nameAr ? m.nameAr : m.nameEn}`,
      destructive: status === 'RETIRED',
      submitLabel: status === 'RETIRED' ? t('cmodelRetireBtn') : t('cmodelSuspendBtn'),
    });
    if (!ok) return;
    try {
      await changeApi.setModelStatus(m.id, status);
      toast.info(t('cmodelStatusChanged'));
      void load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : t('cmodelFailed')); }
  }

  const live = models.filter(m => isLivePreApproval(m)).length;
  const expired = models.filter(m => m.status === 'APPROVED' && !isLivePreApproval(m)).length;

  return (
    <>
      <Card
        title={t('cmodelTitle')}
        Icon={BookCheck}
        action={<Button variant="primary" Icon={Plus} onClick={() => setOpen(true)}>{t('cmodelNew')}</Button>}
      >
        <p className="changes-gov-sub">{t('cmodelSub')}</p>
        {/* Stated before the form opens: a user who expects "save" to publish an authority would
            otherwise think the feature is broken when the new row appears as DRAFT. */}
        <p className="changes-kpi-hint">{t('cmodelFormHint')}</p>
        {models.length === 0 ? (
          // ⚠ The empty state says what the absence MEANS. V354 ships the catalogue empty on
          // purpose, and a bare "no models" would read as a feature nobody uses rather than as
          // "every STANDARD change here is pre-approved by nothing".
          <EmptyState Icon={BookCheck} title={t('cmodelEmptyTitle')} sub={t('cmodelEmptySub')} />
        ) : (
          <>
            <div className="changes-gov-model-kpis">
              <Badge tone="success" dot>{t('cmodelLive').replace('{n}', String(live))}</Badge>
              {expired > 0 && (
                <Badge tone="danger" dot>{t('cmodelExpired').replace('{n}', String(expired))}</Badge>
              )}
            </div>
            <div className="changes-gov-table-wrap">
              <table className="changes-gov-table">
                <thead>
                  <tr>
                    <th>{t('cmodelCode')}</th><th>{t('cmodelName')}</th><th>{t('govColType')}</th>
                    <th>{t('cmodelStatus')}</th><th>{t('cmodelAuthority')}</th>
                    <th>{t('cmodelUntil')}</th><th />
                  </tr>
                </thead>
                <tbody>
                  {models.map(m => {
                    const lapsed = m.status === 'APPROVED' && !isLivePreApproval(m);
                    return (
                      <tr key={m.id}>
                        <td className="mono">{m.code}</td>
                        <td>{isAr && m.nameAr ? m.nameAr : m.nameEn}</td>
                        <td><Badge tone="neutral">{m.changeType}</Badge></td>
                        <td>
                          <Badge tone={lapsed ? 'danger' : (MODEL_STATUS_TONE[m.status] ?? 'neutral')}>
                            {lapsed ? t('cmodelLapsed') : t('cmodelStatus_' + m.status)}
                          </Badge>
                        </td>
                        <td>{m.authorisedByName || '—'}</td>
                        <td className="mono">
                          {m.authorisedUntil ? new Date(m.authorisedUntil).toLocaleDateString() : '—'}
                        </td>
                        <td className="changes-gov-model-actions">
                          {m.status === 'DRAFT' && (
                            <Button variant="secondary" Icon={ShieldCheck} onClick={() => approve(m)}>
                              {t('cmodelApproveBtn')}
                            </Button>
                          )}
                          {m.status === 'APPROVED' && (
                            <Button variant="secondary" Icon={PauseCircle}
                                    onClick={() => setStatus(m, 'SUSPENDED')}>
                              {t('cmodelSuspendBtn')}
                            </Button>
                          )}
                          {m.status !== 'RETIRED' && (
                            <Button variant="ghost" Icon={Archive} onClick={() => setStatus(m, 'RETIRED')}>
                              {t('cmodelRetireBtn')}
                            </Button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </>
        )}
      </Card>

      <FormModal
        open={open}
        title={t('cmodelNew')}
        fields={fields}
        initial={{ code: '', nameEn: '', nameAr: '', changeType: 'STANDARD', riskDefault: '',
                   standardSteps: '', rollbackTemplate: '', testTemplate: '' }}
        onClose={() => setOpen(false)}
        onSubmit={submit}
        submitLabel={t('cmodelSaveDraft')}
      />
    </>
  );
}
