import { useState, type FormEvent } from 'react';
import { Modal } from '../../components/ui/Modal';
import { Input, Textarea } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { riskApi, type RiskRequest, type RiskResponse, ApiError } from '../../lib/api';
import { useToast } from '../../lib/toast';

interface Props {
  open: boolean;
  onClose: () => void;
  onCreated: (risk: RiskResponse) => void;
  /** When true, skip the API call and synthesize a local row instead. */
  demoMode?: boolean;
}

const DEFAULT: RiskRequest = {
  titleEn: '',
  titleAr: '',
  descriptionEn: '',
  inherentLikelihood: 3,
  inherentImpact: 3,
  status: 'OPEN',
};

export function NewRiskModal({ open, onClose, onCreated, demoMode }: Props) {
  const { t } = useI18n();
  const toast = useToast();
  const [form, setForm] = useState<RiskRequest>(DEFAULT);
  const [submitting, setSubmitting] = useState(false);

  function update<K extends keyof RiskRequest>(key: K, value: RiskRequest[K]) {
    setForm(prev => ({ ...prev, [key]: value }));
  }

  function reset() { setForm(DEFAULT); }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      let created: RiskResponse;
      if (demoMode) {
        const score = (form.inherentLikelihood ?? 3) * (form.inherentImpact ?? 3);
        created = {
          id: Date.now(),
          riskCode: `R-${String(Math.floor(Math.random() * 9000) + 1000)}`,
          titleEn: form.titleEn,
          titleAr: form.titleAr,
          status: form.status ?? 'OPEN',
          inherentLikelihood: form.inherentLikelihood,
          inherentImpact: form.inherentImpact,
          inherentScore: score,
          categoryNameEn: 'Operational',
          categoryNameAr: 'تشغيلي',
          ownerFullNameEn: 'You',
        };
      } else {
        created = await riskApi.create(form);
      }
      toast.success(t('toastRiskAdded'));
      onCreated(created);
      reset();
      onClose();
    } catch (err) {
      const message = err instanceof ApiError ? err.message : t('toastRiskFailed');
      toast.danger(message);
    } finally {
      setSubmitting(false);
    }
  }

  const statusOptions = [
    { value: 'OPEN',        label: 'Open' },
    { value: 'IN_PROGRESS', label: 'In Progress' },
    { value: 'MITIGATE',    label: 'Mitigate' },
    { value: 'TREATED',     label: 'Treated' },
    { value: 'CLOSED',      label: 'Closed' },
  ];

  return (
    <Modal
      open={open}
      title={t('modalNewRisk')}
      onClose={() => { reset(); onClose(); }}
      footer={
        <>
          <Button variant="ghost" onClick={() => { reset(); onClose(); }}>{t('cancel')}</Button>
          <Button form="new-risk-form" type="submit" disabled={submitting}>
            {submitting ? t('saving') : t('save')}
          </Button>
        </>
      }
    >
      <form id="new-risk-form" onSubmit={onSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <Input
          id="titleEn"
          label={t('fldTitleEn')}
          required
          maxLength={300}
          value={form.titleEn}
          onChange={e => update('titleEn', e.currentTarget.value)}
        />
        <Input
          id="titleAr"
          label={t('fldTitleAr')}
          maxLength={300}
          value={form.titleAr ?? ''}
          onChange={e => update('titleAr', e.currentTarget.value)}
        />
        <Textarea
          id="descriptionEn"
          label={t('fldDescription')}
          rows={3}
          value={form.descriptionEn ?? ''}
          onChange={e => update('descriptionEn', e.currentTarget.value)}
        />
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Input
            id="likelihood"
            label={t('fldInherentLikelihood')}
            type="number" min={1} max={5} required
            value={form.inherentLikelihood}
            onChange={e => update('inherentLikelihood', Number(e.currentTarget.value))}
          />
          <Input
            id="impact"
            label={t('fldInherentImpact')}
            type="number" min={1} max={5} required
            value={form.inherentImpact}
            onChange={e => update('inherentImpact', Number(e.currentTarget.value))}
          />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Select
            id="status"
            label={t('fldStatus')}
            options={statusOptions}
            value={form.status}
            onChange={e => update('status', e.currentTarget.value)}
          />
          <Input
            id="reviewDate"
            label={t('fldReviewDate')}
            type="date"
            value={form.reviewDate ?? ''}
            onChange={e => update('reviewDate', e.currentTarget.value)}
          />
        </div>
      </form>
    </Modal>
  );
}
