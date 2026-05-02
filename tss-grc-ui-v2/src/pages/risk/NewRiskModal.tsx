import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { TrendingDown } from 'lucide-react';
import { Modal } from '../../components/ui/Modal';
import { Input, Textarea } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { riskApi, adminApi, ApiError, type RiskRequest, type RiskResponse, type RiskCategoryDto, type AdminUserDto } from '../../lib/api';
import { useToast } from '../../lib/toast';
import { isAuthenticated } from '../../lib/auth';
import { cn } from '../../lib/cn';

interface Props {
  open: boolean;
  /** When provided, the modal opens in edit mode and is pre-populated. */
  existing?: RiskResponse | null;
  onClose: () => void;
  onSaved: (risk: RiskResponse) => void;
  /** When true, no API call is made; a synthetic row is constructed locally. */
  demoMode?: boolean;
}

const EMPTY: RiskRequest = {
  titleEn: '', titleAr: '', descriptionEn: '',
  categoryId: undefined, ownerId: undefined,
  inherentLikelihood: 3, inherentImpact: 3,
  residualLikelihood: 2, residualImpact: 2,
  status: 'OPEN', reviewDate: '',
};

const STATUS_OPTIONS = [
  { value: 'OPEN',         label: 'Open' },
  { value: 'IN_PROGRESS',  label: 'In Progress' },
  { value: 'MITIGATE',     label: 'Mitigate' },
  { value: 'TREATED',      label: 'Treated' },
  { value: 'CLOSED',       label: 'Closed' },
];

function levelFromScore(s: number): { label: string; tone: 'success' | 'info' | 'warning' | 'danger' | 'neutral'; color: string } {
  if (s >= 16) return { label: 'CRITICAL', tone: 'danger', color: 'var(--risk-crit)' };
  if (s >= 12) return { label: 'HIGH',     tone: 'danger', color: 'var(--risk-vhigh)' };
  if (s >= 8)  return { label: 'MEDIUM',   tone: 'warning', color: 'var(--risk-high)' };
  if (s >= 4)  return { label: 'LOW',      tone: 'info', color: 'var(--risk-med)' };
  if (s >= 1)  return { label: 'VERY LOW', tone: 'success', color: 'var(--risk-low)' };
  return { label: '—', tone: 'neutral', color: 'var(--surface-3)' };
}

export function NewRiskModal({ open, existing, onClose, onSaved, demoMode }: Props) {
  const { t } = useI18n();
  const toast = useToast();

  const [form, setForm] = useState<RiskRequest>(EMPTY);
  const [submitting, setSubmitting] = useState(false);
  const [categories, setCategories] = useState<RiskCategoryDto[]>([]);
  const [users, setUsers] = useState<AdminUserDto[]>([]);
  const isEdit = !!existing;

  // Reset form on open + populate from `existing` when editing
  useEffect(() => {
    if (!open) return;
    if (existing) {
      setForm({
        titleEn: existing.titleEn,
        titleAr: existing.titleAr ?? '',
        descriptionEn: existing.descriptionEn ?? '',
        categoryId: existing.categoryId,
        ownerId: existing.ownerId,
        inherentLikelihood: existing.inherentLikelihood,
        inherentImpact: existing.inherentImpact,
        residualLikelihood: existing.residualLikelihood ?? Math.max(1, existing.inherentLikelihood - 1),
        residualImpact: existing.residualImpact ?? Math.max(1, existing.inherentImpact - 1),
        status: existing.status,
        reviewDate: existing.reviewDate ?? '',
      });
    } else {
      setForm(EMPTY);
    }
  }, [open, existing]);

  // Load category + owner options once when the modal first opens
  useEffect(() => {
    if (!open || !isAuthenticated()) return;
    riskApi.categories().then(setCategories).catch(() => setCategories([]));
    adminApi.users(0, 200).then(p => setUsers(p.content ?? [])).catch(() => setUsers([]));
  }, [open]);

  function update<K extends keyof RiskRequest>(key: K, value: RiskRequest[K]) {
    setForm(prev => ({ ...prev, [key]: value }));
  }

  const inherentScore = (form.inherentLikelihood ?? 0) * (form.inherentImpact ?? 0);
  const residualScore = (form.residualLikelihood ?? 0) * (form.residualImpact ?? 0);
  const inherentLevel = useMemo(() => levelFromScore(inherentScore), [inherentScore]);
  const residualLevel = useMemo(() => levelFromScore(residualScore), [residualScore]);
  const reduction = inherentScore > 0 ? Math.round(((inherentScore - residualScore) / inherentScore) * 100) : 0;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      let saved: RiskResponse;
      if (demoMode) {
        saved = {
          id: existing?.id ?? Date.now(),
          riskCode: existing?.riskCode ?? `R-${String(Math.floor(Math.random() * 9000) + 1000)}`,
          titleEn: form.titleEn,
          titleAr: form.titleAr,
          descriptionEn: form.descriptionEn,
          categoryId: form.categoryId,
          categoryNameEn: categories.find(c => c.id === form.categoryId)?.nameEn,
          categoryNameAr: categories.find(c => c.id === form.categoryId)?.nameAr,
          ownerId: form.ownerId,
          ownerFullNameEn: users.find(u => u.id === form.ownerId)?.fullNameEn ?? users.find(u => u.id === form.ownerId)?.username,
          status: form.status ?? 'OPEN',
          inherentLikelihood: form.inherentLikelihood,
          inherentImpact: form.inherentImpact,
          inherentScore,
          residualLikelihood: form.residualLikelihood,
          residualImpact: form.residualImpact,
          residualScore,
          riskLevel: inherentLevel.label,
          reviewDate: form.reviewDate,
        };
      } else if (isEdit) {
        saved = await riskApi.update(existing!.id, form);
      } else {
        saved = await riskApi.create(form);
      }
      toast.success(isEdit ? t('toastUpdated') : t('toastRiskAdded'));
      onSaved(saved);
      onClose();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : t('toastRiskFailed'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open={open}
      title={isEdit ? `${t('editPrefix')} · ${existing?.riskCode ?? ''}` : t('modalNewRisk')}
      size="lg"
      onClose={() => { if (!submitting) onClose(); }}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>{t('cancel')}</Button>
          <Button form="risk-form" type="submit" disabled={submitting}>
            {submitting ? t('saving') : t('save')}
          </Button>
        </>
      }
    >
      <form id="risk-form" onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Input id="titleEn" label={t('fldTitleEn')} required maxLength={300}
            value={form.titleEn}
            onChange={e => update('titleEn', e.currentTarget.value)} />
          <Input id="titleAr" label={t('fldTitleAr')} maxLength={300}
            value={form.titleAr ?? ''}
            onChange={e => update('titleAr', e.currentTarget.value)} />
        </div>

        <Textarea id="descriptionEn" label={t('fldDescription')} rows={2}
          value={form.descriptionEn ?? ''}
          onChange={e => update('descriptionEn', e.currentTarget.value)} />

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Select id="categoryId" label={t('fldCategory')}
            options={[{ value: '', label: t('selectNone') }, ...categories.map(c => ({ value: String(c.id), label: c.nameEn }))]}
            value={form.categoryId != null ? String(form.categoryId) : ''}
            onChange={e => update('categoryId', e.currentTarget.value ? Number(e.currentTarget.value) : undefined)} />
          <Select id="ownerId" label={t('fldOwnerLbl')}
            options={[{ value: '', label: t('selectNone') }, ...users.map(u => ({ value: String(u.id), label: u.fullNameEn ?? u.username }))]}
            value={form.ownerId != null ? String(form.ownerId) : ''}
            onChange={e => update('ownerId', e.currentTarget.value ? Number(e.currentTarget.value) : undefined)} />
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Select id="status" label={t('fldStatus')} options={STATUS_OPTIONS}
            value={form.status}
            onChange={e => update('status', e.currentTarget.value)} />
          <Input id="reviewDate" label={t('fldReviewDate')} type="date"
            value={form.reviewDate ?? ''}
            onChange={e => update('reviewDate', e.currentTarget.value)} />
        </div>

        {/* ── Risk evaluation: Before / After controls + computed score reveal ── */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginTop: 4 }}>
          <ControlsBlock
            eyebrow={t('riskBeforeCtrl')}
            likelihoodLabel={t('riskLikelihood')}
            impactLabel={t('riskImpact')}
            scoreLabel={t('riskScore')}
            likelihood={form.inherentLikelihood}
            impact={form.inherentImpact}
            score={inherentScore}
            level={inherentLevel}
            onLikelihood={v => update('inherentLikelihood', v)}
            onImpact={v => update('inherentImpact', v)}
          />
          <ControlsBlock
            eyebrow={t('riskAfterCtrl')}
            likelihoodLabel={t('riskLikelihood')}
            impactLabel={t('riskImpact')}
            scoreLabel={t('riskScore')}
            likelihood={form.residualLikelihood ?? 1}
            impact={form.residualImpact ?? 1}
            score={residualScore}
            level={residualLevel}
            onLikelihood={v => update('residualLikelihood', v)}
            onImpact={v => update('residualImpact', v)}
          />
        </div>

        {/* Reduction summary — only meaningful when both scores set */}
        {inherentScore > 0 && (
          <div style={{
            padding: '10px 14px',
            background: 'var(--surface-2)',
            border: '1px dashed var(--border-strong)',
            borderRadius: 'var(--r-md)',
            display: 'flex', alignItems: 'center', gap: 12,
          }}>
            <TrendingDown size={18} color={reduction > 0 ? 'var(--success-fg)' : 'var(--ink-3)'} />
            <div style={{ flex: 1, fontSize: 12, color: 'var(--ink-2)' }}>
              <b style={{ color: 'var(--ink-1)' }}>{t('riskReduction')}</b> · {t('riskReductionMsg')}{' '}
              <b style={{ color: 'var(--ink-1)' }}>{inherentScore}</b> {t('riskReductionTo')}{' '}
              <b style={{ color: 'var(--ink-1)' }}>{residualScore}</b>.
            </div>
            <Badge tone={reduction >= 50 ? 'success' : reduction > 0 ? 'primary' : 'neutral'}>
              {reduction >= 0 ? '−' : '+'}{Math.abs(reduction)}%
            </Badge>
          </div>
        )}
      </form>
    </Modal>
  );
}

// ─── 1-5 chip picker for likelihood / impact + a coloured score reveal ──────
function ControlsBlock({
  eyebrow, likelihoodLabel, impactLabel, scoreLabel,
  likelihood, impact, score, level, onLikelihood, onImpact,
}: {
  eyebrow: string;
  likelihoodLabel: string;
  impactLabel: string;
  scoreLabel: string;
  likelihood: number;
  impact: number;
  score: number;
  level: { label: string; color: string; tone: 'success' | 'info' | 'warning' | 'danger' | 'neutral' };
  onLikelihood: (v: number) => void;
  onImpact: (v: number) => void;
}) {
  return (
    <div style={{
      padding: '12px 14px',
      background: 'var(--surface-2)',
      border: '1px solid var(--border)',
      borderRadius: 'var(--r-md)',
      display: 'flex', flexDirection: 'column', gap: 10,
    }}>
      <div className="eyebrow" style={{ marginBottom: 0 }}>{eyebrow}</div>

      <PickerRow label={likelihoodLabel} value={likelihood} onChange={onLikelihood} />
      <PickerRow label={impactLabel}     value={impact}     onChange={onImpact} />

      <div style={{
        display: 'flex', alignItems: 'center', gap: 8,
        padding: '8px 10px', marginTop: 2,
        background: 'var(--surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--r-sm)',
      }}>
        <span style={{ fontSize: 11, color: 'var(--ink-3)', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '.08em' }}>{scoreLabel}</span>
        <span className="score-pill" style={{ background: level.color, color: score >= 12 ? '#fff' : 'var(--ink-1)', minWidth: 36 }}>{score}</span>
        <Badge tone={level.tone} dot>{level.label}</Badge>
      </div>
    </div>
  );
}

function PickerRow({ label, value, onChange }: { label: string; value: number; onChange: (v: number) => void }) {
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, fontWeight: 600, color: 'var(--ink-2)', marginBottom: 4 }}>
        <span>{label}</span>
        <span className="mono">{value}</span>
      </div>
      <div style={{ display: 'flex', gap: 4 }}>
        {[1, 2, 3, 4, 5].map(n => (
          <button
            key={n}
            type="button"
            onClick={() => onChange(n)}
            className={cn('chip-pick', value === n && 'chip-pick-active')}
          >
            {n}
          </button>
        ))}
      </div>
    </div>
  );
}
