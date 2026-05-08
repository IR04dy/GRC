import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Briefcase, ShieldCheck, AlertTriangle, FileCheck2, Upload,
  CheckCircle2, Clock, FileText, Building2, Landmark,
} from 'lucide-react';
import { PublicShell } from '../../components/layout/PublicShell';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Progress } from '../../components/ui/Progress';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  findVendorByAccessCode, onboardingProgressPct,
  VENDOR_QUESTIONNAIRE, STAGE_LABEL, TIER_LABEL,
  type VendorProfile, type OnboardingStage,
} from '../../lib/vendorPortal';
import { formatHijri } from '../../lib/sla';

const STAGES: OnboardingStage[] = ['PROFILE', 'QUESTIONNAIRE', 'ATTESTATIONS', 'CONTRACT', 'COMPLETE'];

/**
 * VendorPortalPage — Sprint 5 public-facing vendor portal.
 *
 * Pattern: PublicShell + token entry → vendor dashboard.
 *
 * Entry: vendor pastes their access code (issued by the buyer's TPRM
 * team); local lookup against DEMO_VENDORS. Real production resolves
 * against backend + email/OTP step.
 *
 * Dashboard: 5-stage progress strip, KPIs, action items, questionnaire
 * sections (per-section answered counts), tier-driven attestation list
 * with upload buttons, periodic-review banner, Sharia-compliance
 * confirmation when the buyer requires it.
 */
export function VendorPortalPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const [code, setCode] = useState(params.get('code') ?? '');
  const [profile, setProfile] = useState<VendorProfile | null | 'not-found'>(null);

  useEffect(() => {
    const initial = params.get('code');
    if (initial) {
      const found = findVendorByAccessCode(initial);
      setProfile(found ?? 'not-found');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function login(e?: React.FormEvent) {
    e?.preventDefault();
    if (!code.trim()) return;
    const found = findVendorByAccessCode(code.trim());
    setProfile(found ?? 'not-found');
    setParams({ code: code.trim() });
  }

  function logout() {
    setProfile(null);
    setCode('');
    setParams({});
  }

  // ─── Login screen ────────────────────────────────────────────────
  if (!profile || profile === 'not-found') {
    return (
      <PublicShell title={t('vpLoginTitle')} sub={t('vpLoginSub')}>
        <Card title={t('vpLoginTitle')} Icon={Briefcase}>
          <div style={{ fontSize: 13, color: 'var(--ink-2)', marginBottom: 16, lineHeight: 1.6 }}>
            {t('vpLoginSub')}
          </div>
          <form onSubmit={login} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <label style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
              {t('vpLoginAccessCode')}
            </label>
            <input value={code} onChange={e => setCode(e.target.value)}
              placeholder="e.g. NEOM-2026-A4Z9 / TABUK-LOG-2026-X7M2 / JEDDAH-CON-2026-B2K8"
              style={{
                padding: '10px 12px', fontSize: 13,
                border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                background: 'var(--surface-1)',
              }} />
            <Button variant="primary" onClick={() => login()}>{t('vpLoginSubmit')}</Button>
            {profile === 'not-found' && (
              <div style={{ padding: 8, background: 'var(--danger-bg)', color: 'var(--danger-fg)', borderRadius: 'var(--r-sm)', fontSize: 12 }}>
                <AlertTriangle size={14} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 6 }} />
                {t('vpLoginNotFound')}
              </div>
            )}
          </form>
          <div style={{ marginTop: 24, fontSize: 11, color: 'var(--ink-3)', lineHeight: 1.6 }}>
            <strong>{t('vpDemoAccountsLabel')}</strong>
            <ul style={{ marginTop: 6, paddingInlineStart: 18 }}>
              <li><span className="mono">NEOM-2026-A4Z9</span> — {lang === 'ar' ? 'نيوم لخدمات التقنية (الفئة 1، قيد الاستلام)' : 'NEOM Tech Services (Tier 1, in onboarding)'}</li>
              <li><span className="mono">TABUK-LOG-2026-X7M2</span> — {lang === 'ar' ? 'مجموعة تبوك (الفئة 2، مراجعة سنوية مستحقة)' : 'Tabuk Logistics (Tier 2, annual review due)'}</li>
              <li><span className="mono">JEDDAH-CON-2026-B2K8</span> — {lang === 'ar' ? 'بيت جدة للاستشارات (الفئة 4، استشارية)' : 'Jeddah Consulting (Tier 4, advisory)'}</li>
            </ul>
          </div>
        </Card>
      </PublicShell>
    );
  }

  // ─── Logged-in dashboard ─────────────────────────────────────────
  return <VendorDashboard profile={profile} setProfile={setProfile} onLogout={logout} t={t} lang={lang} toast={toast} />;
}

function VendorDashboard({
  profile, setProfile, onLogout, t, lang, toast,
}: {
  profile: VendorProfile;
  setProfile: (p: VendorProfile) => void;
  onLogout: () => void;
  t: (k: string) => string;
  lang: 'en' | 'ar';
  toast: ReturnType<typeof useToast>;
}) {
  const pct = onboardingProgressPct(profile);
  const tierMeta = TIER_LABEL[profile.tier];
  const stageIdx = STAGES.indexOf(profile.currentStage);
  const openActions = profile.actionItems.filter(a => a.status !== 'DONE');
  const mandatoryAttestations = profile.attestations.filter(a => a.mandatory);
  const uploadedCount = mandatoryAttestations.filter(a => a.uploadedAt).length;

  function uploadAttestation(code: string) {
    setProfile({
      ...profile,
      attestations: profile.attestations.map(a => a.code === code
        ? {
          ...a,
          uploadedAt: new Date().toISOString().slice(0, 10),
          expiresAt: new Date(Date.now() + a.validityDays * 86_400_000).toISOString().slice(0, 10),
          fileName: `${code.toLowerCase()}-2026-uploaded.pdf`,
        }
        : a,
      ),
    });
    toast.success(t('vpUploadToast'));
  }

  function toggleSharia() {
    setProfile({ ...profile, shariaCompliantConfirmed: !profile.shariaCompliantConfirmed });
    toast.success(t('vpShariaToast'));
  }

  return (
    <PublicShell title={profile.legalNameEn} sub={profile.crNumber}>
      {/* ── Vendor identity header ────────────────────────── */}
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12, flexWrap: 'wrap' }}>
          <div>
            <div style={{ fontSize: 12, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
              {t('vpVendorPortal')}
            </div>
            <div style={{ fontSize: 22, fontWeight: 700, color: 'var(--ink-1)', marginTop: 4 }}>
              {lang === 'ar' ? profile.legalNameAr : profile.legalNameEn}
            </div>
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 8 }}>
              <Badge tone={tierMeta.tone}>{lang === 'ar' ? tierMeta.ar : tierMeta.en}</Badge>
              <Badge tone="info">{t('vpStage')} {lang === 'ar' ? STAGE_LABEL[profile.currentStage].ar : STAGE_LABEL[profile.currentStage].en}</Badge>
              <Badge tone="neutral"><span className="mono">{profile.vendorCode}</span></Badge>
              {profile.crNumber && <Badge tone="neutral">{t('vpCrNumber')} {profile.crNumber}</Badge>}
            </div>
          </div>
          <Button variant="secondary" onClick={onLogout}>{t('vpLogout')}</Button>
        </div>
      </Card>

      {/* ── KPIs ─────────────────────────────────────────────── */}
      <div className="kpi-grid" style={{ marginTop: 16 }}>
        <Kpi Icon={CheckCircle2}  label={t('vpKpiProgress')}        value={`${pct}%`} tone={pct >= 80 ? 'success' : pct >= 40 ? 'info' : 'warning'} />
        <Kpi Icon={Clock}          label={t('vpKpiOpenActions')}     value={String(openActions.length)} tone={openActions.length > 0 ? 'warning' : 'success'} />
        <Kpi Icon={FileCheck2}     label={t('vpKpiAttestations')}    value={`${uploadedCount}/${mandatoryAttestations.length}`} tone={uploadedCount === mandatoryAttestations.length ? 'success' : 'warning'} />
        <Kpi Icon={Building2}      label={t('vpKpiNextReview')}      value={profile.nextReviewDate ?? '—'} tone="info" />
      </div>

      {/* ── Stage progress strip ──────────────────────────── */}
      <Card title={t('vpProgressTitle')} Icon={Briefcase}>
        <Progress label={t('vpOverallProgress')} value={pct} tone={pct >= 80 ? 'success' : pct >= 40 ? 'primary' : 'accent'} />
        <div style={{ display: 'grid', gridTemplateColumns: `repeat(${STAGES.length}, 1fr)`, gap: 8, marginTop: 16 }}>
          {STAGES.map((s, i) => {
            const passed = i < stageIdx;
            const current = i === stageIdx;
            return (
              <div key={s} style={{
                padding: 10, borderRadius: 'var(--r-sm)',
                background: current ? 'var(--primary-50)' : passed ? 'var(--success-bg)' : 'var(--surface-2)',
                border: `1px solid ${current ? 'var(--primary)' : passed ? 'var(--success-fg)' : 'var(--border)'}`,
                fontSize: 11, textAlign: 'center',
              }}>
                <div style={{ fontWeight: 700, color: current ? 'var(--primary)' : passed ? 'var(--success-fg)' : 'var(--ink-3)' }}>
                  {i + 1}
                </div>
                <div style={{ marginTop: 4, fontSize: 11, color: 'var(--ink-1)' }}>
                  {lang === 'ar' ? STAGE_LABEL[s].ar : STAGE_LABEL[s].en}
                </div>
              </div>
            );
          })}
        </div>
      </Card>

      {/* ── Action items ───────────────────────────────────── */}
      {openActions.length > 0 && (
        <Card title={t('vpActionsTitle')} Icon={AlertTriangle}
          action={<Badge tone="warning">{openActions.length}</Badge>}>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 8 }}>
            {t('vpActionsSub')}
          </div>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {openActions.map(a => (
              <div key={a.code} style={{
                padding: '10px 0', borderBottom: '1px dashed var(--border)',
                display: 'grid', gridTemplateColumns: '1fr auto', gap: 8, alignItems: 'center',
              }}>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>{lang === 'ar' ? a.titleAr : a.titleEn}</div>
                  {a.dueDate && (
                    <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
                      {t('vpDue')} {a.dueDate} · {formatHijri(a.dueDate, lang)}
                    </div>
                  )}
                </div>
                <Badge tone={a.status === 'IN_PROGRESS' ? 'info' : 'warning'}>
                  {a.status === 'IN_PROGRESS' ? t('vpInProgress') : t('vpOpen')}
                </Badge>
              </div>
            ))}
          </div>
        </Card>
      )}

      {/* ── Sharia compliance ──────────────────────────────── */}
      {profile.shariaCompliantRequired && (
        <Card title={t('vpShariaTitle')} Icon={Landmark}>
          <div style={{ fontSize: 12, color: 'var(--ink-2)', marginBottom: 10, lineHeight: 1.6 }}>
            {t('vpShariaSub')}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Badge tone={profile.shariaCompliantConfirmed ? 'success' : 'warning'} dot>
              {profile.shariaCompliantConfirmed ? t('vpShariaConfirmed') : t('vpShariaPending')}
            </Badge>
            <Button variant="secondary" onClick={toggleSharia}>
              {profile.shariaCompliantConfirmed ? t('vpShariaUnconfirm') : t('vpShariaConfirm')}
            </Button>
          </div>
        </Card>
      )}

      {/* ── Attestations ───────────────────────────────────── */}
      <Card title={t('vpAttestationsTitle')} Icon={FileCheck2}
        action={<Badge tone="primary">{uploadedCount} / {mandatoryAttestations.length}</Badge>}>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 8 }}>
          {t('vpAttestationsSub').replace('{tier}', lang === 'ar' ? tierMeta.ar : tierMeta.en)}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {profile.attestations.map(a => {
            const uploaded = !!a.uploadedAt;
            const expiresSoon = a.expiresAt && new Date(a.expiresAt).getTime() - Date.now() < 60 * 86_400_000;
            return (
              <div key={a.code} style={{
                padding: '12px 0', borderBottom: '1px dashed var(--border)',
                display: 'grid', gridTemplateColumns: '1fr auto auto', gap: 12, alignItems: 'center',
              }}>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
                    {lang === 'ar' ? a.nameAr : a.nameEn}
                    {a.mandatory && <Badge tone="danger">{t('vpMandatory')}</Badge>}
                  </div>
                  {uploaded
                    ? <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>
                        <FileText size={11} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 4 }} />
                        {a.fileName} · {t('vpExpires')} {a.expiresAt}
                      </div>
                    : <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>
                        {t('vpValidity').replace('{n}', String(a.validityDays))}
                      </div>}
                </div>
                <Badge tone={uploaded ? (expiresSoon ? 'warning' : 'success') : 'neutral'}>
                  {uploaded ? (expiresSoon ? t('vpExpiringSoon') : t('vpUploaded')) : t('vpNotUploaded')}
                </Badge>
                <Button variant="secondary" Icon={Upload} onClick={() => uploadAttestation(a.code)}>
                  {uploaded ? t('vpReupload') : t('vpUpload')}
                </Button>
              </div>
            );
          })}
        </div>
      </Card>

      {/* ── Questionnaire ──────────────────────────────────── */}
      <Card title={t('vpQuestionnaireTitle')} Icon={ShieldCheck}>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 8 }}>
          {t('vpQuestionnaireSub')}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {VENDOR_QUESTIONNAIRE.map(section => {
            const answered = section.questions.filter(q =>
              profile.questionnaireAnswers.some(a => a.questionId === q.id)).length;
            const total = section.questions.length;
            const complete = answered === total;
            return (
              <div key={section.id} style={{
                padding: '12px 0', borderBottom: '1px dashed var(--border)',
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>
                    {lang === 'ar' ? section.titleAr : section.titleEn}
                  </div>
                  <Badge tone={complete ? 'success' : 'warning'}>{answered}/{total}</Badge>
                </div>
                <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4, lineHeight: 1.5 }}>
                  {section.questions.slice(0, 1).map(q => (
                    <span key={q.id}>{lang === 'ar' ? q.promptAr : q.promptEn}{section.questions.length > 1 ? '…' : ''}</span>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      </Card>
    </PublicShell>
  );
}
