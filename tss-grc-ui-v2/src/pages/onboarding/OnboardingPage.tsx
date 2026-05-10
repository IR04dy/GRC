import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Building2, ListChecks, Users, Compass, Check, ChevronLeft, ChevronRight,
  Trash2, UserPlus, Sparkles, RefreshCw, type LucideIcon,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  loadOnboarding, saveOnboarding, resetOnboarding, subscribeOnboarding,
  FRAMEWORK_OPTIONS, recommendedFrameworks, TOUR_TARGETS,
  type OnboardingState, type Sector, type OrgSize, type InviteRow,
} from '../../lib/onboarding';
import { loadWhiteLabel, saveWhiteLabel } from '../../lib/whiteLabel';

/**
 * OnboardingPage — Track A (sales-readiness).
 *
 * Four-step wizard that takes a fresh tenant from blank-slate to ready-to-use:
 *
 *   1. Organisation profile  (name, sector, size)
 *   2. Framework picker      (pre-checked from sector)
 *   3. Invite teammates      (email + role rows)
 *   4. First-run tour        (deep-link cards into the rest of the platform)
 *
 * State is persisted on every step so the wizard can be paused + resumed.
 * On final confirm, the tenant's organisation name is also pushed into
 * the white-label config so it shows up on the board pack cover and in
 * outbound emails immediately.
 */
export function OnboardingPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [state, setState] = useState<OnboardingState>(() => loadOnboarding());
  useEffect(() => subscribeOnboarding(() => setState(loadOnboarding())), []);

  function patch(p: Partial<OnboardingState>) {
    setState(s => {
      const next = { ...s, ...p };
      saveOnboarding(next);
      return next;
    });
  }

  function next() {
    if (state.step === 0) {
      if (!state.organisationNameEn.trim() || !state.organisationNameAr.trim()) {
        toast.danger(t('onbErrNeedOrgName'));
        return;
      }
      // Auto-pre-check recommended frameworks when stepping into picker.
      if (state.selectedFrameworks.length === 0) {
        patch({ step: 1, selectedFrameworks: recommendedFrameworks(state.sector) });
        return;
      }
    }
    if (state.step === 1 && state.selectedFrameworks.length === 0) {
      toast.danger(t('onbErrNeedFramework'));
      return;
    }
    patch({ step: state.step + 1 });
  }

  function back() {
    if (state.step > 0) patch({ step: state.step - 1 });
  }

  function finish() {
    const installedAt = new Date().toISOString();
    patch({ installedAt });
    // Sync the org name into white-label so the board pack cover + topbar
    // pick it up immediately.
    const wl = loadWhiteLabel();
    saveWhiteLabel({
      ...wl,
      organisationNameEn: state.organisationNameEn,
      organisationNameAr: state.organisationNameAr,
    });
    toast.success(t('onbFinishToast'));
  }

  function reset() {
    if (!confirm(t('onbResetConfirm'))) return;
    resetOnboarding();
    setState(loadOnboarding());
    toast.info(t('onbResetToast'));
  }

  const installed = !!state.installedAt;

  return (
    <Shell title={t('onbTitle')} sub={t('onbSub')}>
      <PageHeader
        eyebrow={t('onbEyebrow')}
        title={t('onbTitle')}
        sub={installed ? t('onbInstalledSub') : t('onbPageSub')}
        actions={installed
          ? <Button variant="secondary" Icon={RefreshCw} onClick={reset}>{t('onbStartOver')}</Button>
          : null}
      />

      {/* Step indicator */}
      <Card>
        <div style={{
          display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12,
        }}>
          {[
            { idx: 0, key: 'onbStep1', Icon: Building2 },
            { idx: 1, key: 'onbStep2', Icon: ListChecks },
            { idx: 2, key: 'onbStep3', Icon: Users },
            { idx: 3, key: 'onbStep4', Icon: Compass },
          ].map(s => (
            <StepDot key={s.idx} idx={s.idx} active={state.step === s.idx}
              done={state.step > s.idx || installed} Icon={s.Icon}
              label={t(s.key)} onClick={() => patch({ step: s.idx })} />
          ))}
        </div>
      </Card>

      {installed ? <DoneCard state={state} /> : (
        <div style={{ marginTop: 16 }}>
          {state.step === 0 && <Step1OrgProfile state={state} patch={patch} lang={lang} t={t} />}
          {state.step === 1 && <Step2Frameworks state={state} patch={patch} lang={lang} t={t} />}
          {state.step === 2 && <Step3Invites state={state} patch={patch} t={t} />}
          {state.step === 3 && <Step4Tour t={t} onFinish={finish} />}
        </div>
      )}

      {/* Nav buttons */}
      {!installed && (
        <div style={{ marginTop: 16, display: 'flex', justifyContent: 'space-between' }}>
          <Button variant="secondary" Icon={ChevronLeft} onClick={back} disabled={state.step === 0}>
            {t('onbBack')}
          </Button>
          {state.step < 3
            ? <Button variant="primary" Icon={ChevronRight} onClick={next}>{t('onbNext')}</Button>
            : <Button variant="primary" Icon={Sparkles} onClick={finish}>{t('onbFinish')}</Button>}
        </div>
      )}
    </Shell>
  );
}

// ─── Step indicator dot ─────────────────────────────────────────────────────

function StepDot({ idx, active, done, Icon, label, onClick }: {
  idx: number; active: boolean; done: boolean; Icon: LucideIcon;
  label: string; onClick: () => void;
}) {
  const colour = done
    ? 'var(--success)'
    : active ? 'var(--primary)' : 'var(--ink-3)';
  return (
    <button onClick={onClick} style={{
      display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 6,
      padding: 10, border: `1px solid ${active ? 'var(--primary)' : 'var(--border)'}`,
      borderRadius: 'var(--r-md)', background: active ? 'var(--primary-50)' : 'var(--surface-1)',
      cursor: 'pointer', textAlign: 'start',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <div style={{
          width: 28, height: 28, borderRadius: '50%',
          background: colour, color: 'white',
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        }}>
          {done ? <Check size={14} /> : <Icon size={14} />}
        </div>
        <span style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase' }}>
          {idx + 1}
        </span>
      </div>
      <div style={{ fontSize: 12, fontWeight: 600 }}>{label}</div>
    </button>
  );
}

// ─── STEP 1: Organisation profile ────────────────────────────────────────────

function Step1OrgProfile({ state, patch, lang, t }: {
  state: OnboardingState; patch: (p: Partial<OnboardingState>) => void;
  lang: 'en' | 'ar'; t: (k: string) => string;
}) {
  const sectors: { code: Sector; nameKey: string }[] = [
    { code: 'banking',         nameKey: 'onbSectorBanking' },
    { code: 'insurance',       nameKey: 'onbSectorInsurance' },
    { code: 'capital-markets', nameKey: 'onbSectorCapitalMarkets' },
    { code: 'telecom',         nameKey: 'onbSectorTelecom' },
    { code: 'healthcare',      nameKey: 'onbSectorHealthcare' },
    { code: 'government',      nameKey: 'onbSectorGovernment' },
    { code: 'energy',          nameKey: 'onbSectorEnergy' },
    { code: 'retail-other',    nameKey: 'onbSectorRetailOther' },
  ];
  const sizes: { code: OrgSize; labelKey: string; descKey: string }[] = [
    { code: 'sme',        labelKey: 'onbSizeSme',    descKey: 'onbSizeSmeSub' },
    { code: 'mid',        labelKey: 'onbSizeMid',    descKey: 'onbSizeMidSub' },
    { code: 'enterprise', labelKey: 'onbSizeEnt',    descKey: 'onbSizeEntSub' },
    { code: 'group',      labelKey: 'onbSizeGroup',  descKey: 'onbSizeGroupSub' },
  ];

  return (
    <Card title={t('onbStep1Title')} Icon={Building2}>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 16 }}>{t('onbStep1Sub')}</div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
        <Field label={t('onbOrgNameEn')}>
          <input value={state.organisationNameEn} onChange={e => patch({ organisationNameEn: e.target.value })}
            placeholder="Saudi Telecom Company" style={inputStyle} />
        </Field>
        <Field label={t('onbOrgNameAr')}>
          <input value={state.organisationNameAr} onChange={e => patch({ organisationNameAr: e.target.value })}
            placeholder="شركة الاتصالات السعودية" style={inputStyle} dir="rtl" />
        </Field>
      </div>

      <div style={{ marginTop: 16 }}>
        <Field label={t('onbSector')}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 6 }}>
            {sectors.map(s => (
              <button key={s.code} onClick={() => patch({ sector: s.code })}
                style={{
                  padding: '10px 12px', borderRadius: 'var(--r-sm)',
                  border: `1px solid ${state.sector === s.code ? 'var(--primary)' : 'var(--border)'}`,
                  background: state.sector === s.code ? 'var(--primary-50)' : 'var(--surface-1)',
                  cursor: 'pointer', fontSize: 12, textAlign: 'start', fontWeight: 600,
                }}>
                {t(s.nameKey)}
              </button>
            ))}
          </div>
        </Field>
      </div>

      <div style={{ marginTop: 16 }}>
        <Field label={t('onbSize')}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 6 }}>
            {sizes.map(s => (
              <button key={s.code} onClick={() => patch({ size: s.code })}
                style={{
                  padding: '10px 12px', borderRadius: 'var(--r-sm)',
                  border: `1px solid ${state.size === s.code ? 'var(--primary)' : 'var(--border)'}`,
                  background: state.size === s.code ? 'var(--primary-50)' : 'var(--surface-1)',
                  cursor: 'pointer', textAlign: 'start',
                  display: 'flex', flexDirection: 'column', gap: 4,
                }}>
                <span style={{ fontSize: 12, fontWeight: 600 }}>{t(s.labelKey)}</span>
                <span style={{ fontSize: 10, color: 'var(--ink-3)' }}>{t(s.descKey)}</span>
              </button>
            ))}
          </div>
        </Field>
        {/* lang exists in scope — kept to suppress lint by aliasing. */}
        <span style={{ display: 'none' }}>{lang}</span>
      </div>
    </Card>
  );
}

// ─── STEP 2: Frameworks ─────────────────────────────────────────────────────

function Step2Frameworks({ state, patch, lang, t }: {
  state: OnboardingState; patch: (p: Partial<OnboardingState>) => void;
  lang: 'en' | 'ar'; t: (k: string) => string;
}) {
  const recommended = useMemo(() => recommendedFrameworks(state.sector), [state.sector]);
  function toggle(code: string) {
    const has = state.selectedFrameworks.includes(code);
    patch({
      selectedFrameworks: has
        ? state.selectedFrameworks.filter(c => c !== code)
        : [...state.selectedFrameworks, code],
    });
  }
  function selectAll() {
    patch({ selectedFrameworks: FRAMEWORK_OPTIONS.map(f => f.code) });
  }
  function selectRecommended() {
    patch({ selectedFrameworks: recommended });
  }
  function clearAll() {
    patch({ selectedFrameworks: [] });
  }

  return (
    <Card title={t('onbStep2Title')} Icon={ListChecks}>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>{t('onbStep2Sub')}</div>

      <div style={{ display: 'flex', gap: 6, marginBottom: 12 }}>
        <Button variant="secondary" onClick={selectRecommended}>{t('onbPickRecommended')}</Button>
        <Button variant="ghost" onClick={selectAll}>{t('onbPickAll')}</Button>
        <Button variant="ghost" onClick={clearAll}>{t('onbPickClear')}</Button>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: 8 }}>
        {FRAMEWORK_OPTIONS.map(f => {
          const isOn = state.selectedFrameworks.includes(f.code);
          const isRec = recommended.includes(f.code);
          return (
            <button key={f.code} onClick={() => toggle(f.code)}
              style={{
                padding: 12, borderRadius: 'var(--r-md)',
                border: `2px solid ${isOn ? 'var(--primary)' : 'var(--border)'}`,
                background: isOn ? 'var(--primary-50)' : 'var(--surface-1)',
                cursor: 'pointer', textAlign: 'start',
              }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                <div style={{
                  width: 20, height: 20, borderRadius: 4,
                  background: isOn ? 'var(--primary)' : 'transparent',
                  border: `1.5px solid ${isOn ? 'var(--primary)' : 'var(--border)'}`,
                  display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                  color: 'white',
                }}>
                  {isOn && <Check size={12} strokeWidth={3} />}
                </div>
                <span style={{ fontSize: 13, fontWeight: 600, flex: 1 }}>
                  {lang === 'ar' ? f.nameAr : f.nameEn}
                </span>
                {isRec && <Badge tone="primary">{t('onbRecommended')}</Badge>}
              </div>
              <div style={{ fontSize: 11, color: 'var(--ink-3)', marginInlineStart: 28, lineHeight: 1.5 }}>
                {lang === 'ar' ? f.blurbAr : f.blurbEn}
              </div>
              <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 4, marginInlineStart: 28 }}>
                <Badge tone="neutral">{f.regulator}</Badge>
              </div>
            </button>
          );
        })}
      </div>
    </Card>
  );
}

// ─── STEP 3: Invites ────────────────────────────────────────────────────────

function Step3Invites({ state, patch, t }: {
  state: OnboardingState; patch: (p: Partial<OnboardingState>) => void;
  t: (k: string) => string;
}) {
  function addRow() {
    patch({ invites: [...state.invites, { email: '', role: 'risk-manager' }] });
  }
  function updateRow(i: number, row: Partial<InviteRow>) {
    patch({ invites: state.invites.map((r, idx) => idx === i ? { ...r, ...row } : r) });
  }
  function removeRow(i: number) {
    patch({ invites: state.invites.filter((_, idx) => idx !== i) });
  }

  const roles: { code: InviteRow['role']; labelKey: string }[] = [
    { code: 'admin',              labelKey: 'onbRoleAdmin' },
    { code: 'risk-manager',       labelKey: 'onbRoleRiskMgr' },
    { code: 'compliance-officer', labelKey: 'onbRoleCompOfc' },
    { code: 'auditor',            labelKey: 'onbRoleAuditor' },
    { code: 'viewer',             labelKey: 'onbRoleViewer' },
  ];

  return (
    <Card title={t('onbStep3Title')} Icon={Users}
      action={<Button variant="secondary" Icon={UserPlus} onClick={addRow}>{t('onbAddInvite')}</Button>}>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>{t('onbStep3Sub')}</div>

      {state.invites.length === 0
        ? <EmptyInvites t={t} />
        : (
          <table className="tbl" style={{ width: '100%' }}>
            <thead>
              <tr>
                <th>{t('onbInviteEmail')}</th>
                <th>{t('onbInviteRole')}</th>
                <th style={{ width: 60 }}></th>
              </tr>
            </thead>
            <tbody>
              {state.invites.map((inv, i) => (
                <tr key={i}>
                  <td>
                    <input value={inv.email} onChange={e => updateRow(i, { email: e.target.value })}
                      type="email" placeholder="user@example.sa" style={{ ...inputStyle, width: '100%' }} />
                  </td>
                  <td>
                    <select value={inv.role} onChange={e => updateRow(i, { role: e.target.value as InviteRow['role'] })}
                      style={{ ...inputStyle, width: '100%' }}>
                      {roles.map(r => <option key={r.code} value={r.code}>{t(r.labelKey)}</option>)}
                    </select>
                  </td>
                  <td>
                    <Button variant="ghost" Icon={Trash2} onClick={() => removeRow(i)}>{''}</Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

      <div style={{ marginTop: 12, fontSize: 11, color: 'var(--ink-3)', lineHeight: 1.5 }}>
        {t('onbInviteHint')}
      </div>
    </Card>
  );
}

function EmptyInvites({ t }: { t: (k: string) => string }) {
  return (
    <div style={{
      padding: 28, textAlign: 'center', border: '1px dashed var(--border)',
      borderRadius: 'var(--r-md)', color: 'var(--ink-3)', fontSize: 12,
    }}>
      {t('onbInviteEmpty')}
    </div>
  );
}

// ─── STEP 4: First-run tour ─────────────────────────────────────────────────

function Step4Tour({ t, onFinish }: {
  t: (k: string) => string;
  onFinish: () => void;
}) {
  return (
    <Card title={t('onbStep4Title')} Icon={Compass}>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>{t('onbStep4Sub')}</div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 8 }}>
        {TOUR_TARGETS.map(tg => (
          <Link key={tg.code} to={tg.to} onClick={onFinish}
            style={{
              padding: 14, borderRadius: 'var(--r-md)',
              border: '1px solid var(--border)', background: 'var(--surface-1)',
              textDecoration: 'none', color: 'var(--ink-1)',
              display: 'flex', flexDirection: 'column', gap: 4,
            }}>
            <span style={{ fontSize: 13, fontWeight: 600 }}>{t(tg.labelKey)}</span>
            <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{t(tg.descriptionKey)}</span>
          </Link>
        ))}
      </div>
    </Card>
  );
}

// ─── DONE state ──────────────────────────────────────────────────────────────

function DoneCard({ state }: { state: OnboardingState }) {
  const { t, lang } = useI18n();
  const installedDate = state.installedAt ? new Date(state.installedAt) : null;
  return (
    <div style={{ marginTop: 16 }}>
      <Card title={t('onbInstalledTitle')} Icon={Sparkles}>
        <div style={{ fontSize: 13, color: 'var(--ink-2)', marginBottom: 12, lineHeight: 1.6 }}>
          {t('onbInstalledBody')}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 8 }}>
          <Stat label={t('onbInstalledOrg')} value={lang === 'ar' ? state.organisationNameAr : state.organisationNameEn} />
          <Stat label={t('onbInstalledFrameworks')} value={String(state.selectedFrameworks.length)} />
          <Stat label={t('onbInstalledInvites')} value={String(state.invites.length)} />
          <Stat label={t('onbInstalledAt')} value={installedDate?.toLocaleString() ?? ''} />
        </div>
        <div style={{ marginTop: 12, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {TOUR_TARGETS.map(tg => (
            <Link key={tg.code} to={tg.to}>
              <Button variant="secondary">{t(tg.labelKey)}</Button>
            </Link>
          ))}
        </div>
      </Card>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div style={{
      padding: 10, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
      background: 'var(--surface-1)',
    }}>
      <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{label}</div>
      <div style={{ fontSize: 14, fontWeight: 600, marginTop: 2 }}>{value || '—'}</div>
    </div>
  );
}

// ─── Shared field ────────────────────────────────────────────────────────────

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4, marginBottom: 6 }}>
      <span style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{label}</span>
      {children}
    </label>
  );
}

const inputStyle: React.CSSProperties = {
  padding: '8px 10px', fontSize: 13,
  border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
  background: 'var(--surface-1)',
};
