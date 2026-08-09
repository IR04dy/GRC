import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { CheckCircle2, ArrowRight, Copy, ArrowLeft, FileText } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  useIntake, INTAKE_CATALOG, type IntakeKind,
} from '../../lib/intake';
import { INTAKE_FORM_FIELDS } from '../../lib/intakeForms';
import { getUser } from '../../lib/auth';
import { ApiError } from '../../lib/api';
import { IntakeFormRenderer } from '../../components/intake/IntakeFormRenderer';
import { computeChangeRisk, type ChangeRiskBreakdown } from '../../lib/changeRisk';
import { ChangeRiskPanel } from '../../components/intake/ChangeRiskPanel';

/**
 * IntakeFormPage — DD-59.
 *
 * Authenticated employee-side intake. Walks any IntakeKind that has a
 * registered form schema. Mounts inside the Shell (full sidebar +
 * header), pre-fills submitter from the signed-in user, and submits via
 * useIntake().submit() -> POST /api/v1/intake (F2-172; it wrote to
 * localStorage until then, so the triager Inbox and the public Tracker
 * were reading the submitter's own browser).
 *
 * Routes: /intake/:kind  where :kind is the lowercase IntakeKind.
 */
export function IntakeFormPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const { kind: kindParam } = useParams<{ kind: string }>();
  const navigate = useNavigate();
  const { submit } = useIntake();
  const [submitted, setSubmitted] = useState<{ refCode: string; slaDueAt: string; risk?: ChangeRiskBreakdown } | null>(null);
  // DD-73: live form-state mirror so we can recompute the change-risk
  // panel on every keystroke. Only meaningful when kind === 'CHANGE_REQUEST'.
  const [liveValues, setLiveValues] = useState<Record<string, string>>({});

  const kind = (kindParam ?? '').toUpperCase() as IntakeKind;
  const catalog = INTAKE_CATALOG[kind];
  const fields = INTAKE_FORM_FIELDS[kind];

  // Prefill from the URL — e.g. the "Raise emergency change" process launcher
  // deep-links to /intake/change_request?type=EMERGENCY so the type is preselected.
  const [searchParams] = useSearchParams();
  const initialValues = useMemo<Record<string, string>>(() => {
    const seed: Record<string, string> = {};
    const type = (searchParams.get('type') ?? '').toUpperCase();
    if (kind === 'CHANGE_REQUEST' && ['STANDARD', 'NORMAL', 'EMERGENCY'].includes(type)) {
      seed.changeType = type;
    }
    return seed;
  }, [kind, searchParams]);

  if (!catalog || !fields) {
    return (
      <Shell title={t('intakeFormUnknownTitle')}>
        <div style={{ padding: 32, textAlign: 'center' }}>
          <div style={{ fontSize: 14, color: 'var(--danger-fg)', marginBottom: 12 }}>{t('intakeFormUnknownBody')}</div>
          <Link to="/intake" style={{ color: 'var(--primary)', fontSize: 13 }}>{t('intakeFormBackToCatalog')}</Link>
        </div>
      </Shell>
    );
  }

  /**
   * F2-172 — this now files a real server row and awaits it.
   *
   * Two changes worth naming. The submitter fields were `'Demo Employee'` /
   * `'employee@tss.sa'` under a comment saying "Real auth would pull from JWT"; they are now
   * the signed-in user's, and the *attribution* no longer depends on them at all — the server
   * binds `submitter_user_id` from the token and ignores whatever the body claims.
   *
   * And the acknowledgement screen is shown only **after** the server confirms. It used to
   * render a reference code from a record that had been written to `localStorage`, so a
   * submitter was handed a tracking code for a report nobody had received. A failure now
   * surfaces as a toast and the form stays open with the answers intact.
   */
  async function onSubmit(payload: Record<string, string>) {
    const me = getUser();
    const severityCandidate = payload.severitySelfAssessed as 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | undefined;
    try {
      const rec = await submit({
        kind,
        channel: 'WEB_FORM',
        submitterIsAnonymous: false,
        submitterRole: 'EMPLOYEE',
        ...(me?.username ? { submitterName: me.username } : {}),
        payload,
        severity: severityCandidate ?? 'MEDIUM',
        locale: lang as 'en' | 'ar',
      });
      // DD-70: for CHANGE_REQUEST, compute the risk score and CAB gate so
      // the submitter sees the classification immediately on the ack screen.
      const risk = kind === 'CHANGE_REQUEST' ? computeChangeRisk(payload) : undefined;
      setSubmitted({ refCode: rec.referenceCode, slaDueAt: rec.slaDueAt ?? '', risk });
      toast.success(t('intakeFormToastSubmitted'));
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Could not file the submission');
    }
  }

  if (submitted) {
    return (
      <Shell title={lang === 'ar' ? catalog.titleAr : catalog.titleEn}>
        <PageHeader
          eyebrow={catalog.reference}
          title={t('intakeFormAckTitle')}
          sub={t('intakeFormAckSub')}
        />
        <Card>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: 24, textAlign: 'center' }}>
            <div style={{
              width: 72, height: 72, borderRadius: '50%',
              background: 'var(--success-bg)', color: 'var(--success-fg)',
              display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 16,
            }}>
              <CheckCircle2 size={40} strokeWidth={2} />
            </div>
            <h2 style={{ margin: 0, fontSize: 20, fontWeight: 700, color: 'var(--ink-1)' }}>
              {t('intakeFormAckHeading')}
            </h2>
            <p style={{ marginTop: 8, fontSize: 14, color: 'var(--ink-2)', maxWidth: 520, lineHeight: 1.6 }}>
              {t('intakeFormAckBody').replace('{assignee}', catalog.defaultAssignee)}
            </p>
          </div>

          <div style={{
            background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
            padding: 16, margin: '8px 0', border: '1px dashed var(--border)',
          }}>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
              {t('intakeFormAckRefCode')}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 6 }}>
              <code className="mono" style={{
                fontSize: 22, fontWeight: 700, color: 'var(--primary)', letterSpacing: '0.05em',
              }}>{submitted.refCode}</code>
              <Button variant="ghost" size="sm" Icon={Copy} onClick={() => {
                navigator.clipboard.writeText(submitted.refCode);
                toast.success(t('portalDsrCopied'));
              }}>{t('actionCopy')}</Button>
            </div>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 12 }}>
              {t('intakeFormAckSlaPrefix')}: <span className="mono" style={{ color: 'var(--ink-1)', fontWeight: 600 }}>{submitted.slaDueAt.slice(0, 10)}</span>
            </div>
          </div>

          {submitted.risk && <ChangeRiskPanel risk={submitted.risk} t={t} lang={lang} />}

          <div style={{ display: 'flex', gap: 8, justifyContent: 'center', marginTop: 16, flexWrap: 'wrap' }}>
            <Button variant="primary" Icon={ArrowRight} onClick={() => navigate('/intake-inbox')}>
              {t('intakeFormAckOpenInbox')}
            </Button>
            <Button variant="ghost" onClick={() => { setSubmitted(null); }}>
              {t('intakeFormAckAnother')}
            </Button>
          </div>
        </Card>
      </Shell>
    );
  }

  return (
    <Shell title={lang === 'ar' ? catalog.titleAr : catalog.titleEn} sub={catalog.reference}>
      <PageHeader
        eyebrow={catalog.reference}
        title={lang === 'ar' ? catalog.titleAr : catalog.titleEn}
        sub={lang === 'ar' ? catalog.blurbAr : catalog.blurbEn}
      />

      <div style={{ marginBottom: 12 }}>
        <Link to="/intake" style={{
          display: 'inline-flex', alignItems: 'center', gap: 4,
          fontSize: 12, color: 'var(--ink-3)', textDecoration: 'none',
        }}>
          <ArrowLeft size={12} /> {t('intakeFormBackToCatalog')}
        </Link>
      </div>

      <Card title={t('intakeFormCardTitle')} Icon={FileText}
        action={<Badge tone="info">{catalog.slaHours >= 24 ? `${Math.round(catalog.slaHours / 24)} ${t('intakeCatalogDaysSla')}` : `${catalog.slaHours} ${t('intakeCatalogHoursSla')}`}</Badge>}>
        <div style={{
          background: 'var(--info-bg)', padding: 12, borderRadius: 'var(--r-sm)',
          fontSize: 12, color: 'var(--ink-2)', marginBottom: 16, lineHeight: 1.5,
        }}>
          {t('intakeFormReroutingHint').replace('{assignee}', catalog.defaultAssignee).replace('{ref}', catalog.reference)}
        </div>
        {/* DD-73: live change-risk preview rendered above the form when
            the user is filling a CHANGE_REQUEST. Recomputes on every change
            so submitters self-correct (pick MONTH_END_FREEZE → see CRITICAL
            → switch to OFF_HOURS) before submitting. */}
        {kind === 'CHANGE_REQUEST' && Object.keys(liveValues).length > 0 && (
          <ChangeRiskPanel risk={computeChangeRisk(liveValues)} t={t} lang={lang} />
        )}

        <IntakeFormRenderer
          fields={fields}
          submitLabel={t('intakeFormSubmit')}
          onSubmit={onSubmit}
          onChange={kind === 'CHANGE_REQUEST' ? setLiveValues : undefined}
          initial={initialValues}
        />
      </Card>
    </Shell>
  );
}

