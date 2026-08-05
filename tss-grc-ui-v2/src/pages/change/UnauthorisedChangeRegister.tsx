/**
 * S8f / CE-11 — the unauthorised-change register.
 *
 * **What this card refuses to do.** It never renders an empty findings list as a clean bill of
 * health, and it never styles an unattributed-but-approved change like an unauthorised one.
 *
 * The first refusal is why `undetectable` is on screen rather than only in the payload: a register
 * that quietly covers eight of ten failure modes reads exactly like one that covers ten, and this
 * one cannot see a production change that was never ticketed (no drift feed exists) or an
 * implementation inside a freeze window (`implementation_started` is NULL on every change).
 *
 * The second is the measurement that shaped the whole slice: six of dev's eleven findings satisfy
 * their cell's `min_approvals` in full and lack only `cab_decision_at`. Those are a bookkeeping gap,
 * not a breach, so they carry their own tone and sit outside the headline count.
 */
import { useEffect, useState } from 'react';
import { ScanSearch, ShieldAlert, FileCheck2, EyeOff } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import { changeApi, ApiError, type UnauthorisedRegisterDto, type UnauthorisedFindingDto } from '../../lib/api';
import { canApprove } from '../../lib/auth';

/**
 * Tone by reason.
 *
 * ⚠ `UNATTRIBUTED_AUTHORISATION` is `info`, never `danger`. Authority was present and recorded; only
 * the platform's own stamp is missing. Colouring it like a breach is how a register loses its reader.
 */
export const REASON_TONE: Record<string, BadgeTone> = {
  NO_AUTHORISATION_RECORD: 'danger',
  INSUFFICIENT_APPROVALS: 'danger',
  NO_AUTHORISATION_POLICY: 'danger',
  EXPIRED_MODEL_APPLIED: 'warning',
  RATIFICATION_BREACHED: 'warning',
  UNATTRIBUTED_AUTHORISATION: 'info',
};

/** Every disposition outcome. Mirrors `UnauthorisedChangeDisposition.STATES` — no "CLOSED". */
export const DISPOSITION_STATES = [
  'ACCEPTED_RISK', 'RETROSPECTIVELY_AUTHORISED', 'REVERSED', 'FALSE_POSITIVE',
] as const;

/**
 * Whether a finding is a genuine authority gap.
 *
 * Kept exported so a test can pin it against the server's own `isAuthorityGap()`. Two copies of one
 * rule drift, and the direction this one would drift in — counting the unattributed — overstates a
 * compliance breach.
 */
export function isAuthorityGap(f: UnauthorisedFindingDto): boolean {
  return f.reasonCode !== 'UNATTRIBUTED_AUTHORISATION';
}

export function UnauthorisedChangeRegister({ toast }: { toast: ReturnType<typeof useToast> }) {
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const dialog = useDialog();

  const [reg, setReg] = useState<UnauthorisedRegisterDto | null>(null);
  /* F2-29's helper, and CHANGE is the case its own comment measures: 42 of 57 roles hold
     CHANGE:WRITE against 9 holding CHANGE:APPROVE, so 33 roles can raise a change and must not be
     offered a control that signs one off. */
  const canDispose = canApprove('CHANGE');

  async function load() {
    try { setReg(await changeApi.unauthorisedRegister()); }
    catch { /* silent — a backend-down window surfaces elsewhere on this page */ }
  }
  useEffect(() => { void load(); }, []);

  async function dispose(f: UnauthorisedFindingDto) {
    const state = await dialog.prompt({
      title: t('unauthDisposeTitle'),
      label: t('unauthDisposeState'),
      helpText: DISPOSITION_STATES.map(s => t(`unauthState_${s}`)).join(' · '),
      initial: 'ACCEPTED_RISK',
      required: true,
      validate: v => (DISPOSITION_STATES as readonly string[]).includes(v.trim().toUpperCase())
        ? null : t('unauthDisposeStateInvalid'),
    });
    if (state === null) return;
    const note = await dialog.prompt({
      title: t('unauthDisposeTitle'),
      label: t('unauthDisposeNote'),
      helpText: t('unauthDisposeNoteHint'),
      type: 'textarea',
    });
    if (note === null) return;
    try {
      await changeApi.disposeFinding(f.changeId, f.reasonCode, state.trim().toUpperCase(), note || undefined);
      toast.success(t('unauthDisposed'));
      void load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const ref = (f: UnauthorisedFindingDto) => f.changeCode || `#${f.changeId}`;
  const title = (f: UnauthorisedFindingDto) => (isAr ? f.titleAr || f.titleEn : f.titleEn) || '—';

  return (
    <Card title={t('unauthTitle')} Icon={ScanSearch}>
      <p className="changes-gov-sub">{t('unauthSub')}</p>

      {reg && (
        <>
          {/* The headline. `authorityGaps` and `unattributed` are separate numbers on purpose — see
              REASON_TONE. And the gap count includes disposed findings, so it cannot be cleared by
              signing things off. */}
          <div className="changes-gov-actions" style={{ flexWrap: 'wrap', gap: 8 }}>
            <Badge tone={reg.authorityGaps > 0 ? 'danger' : 'success'}>
              {t('unauthGaps')}: {reg.authorityGaps} / {reg.population}
            </Badge>
            <Badge tone={reg.unattributed > 0 ? 'info' : 'neutral'}>
              {t('unauthUnattributed')}: {reg.unattributed}
            </Badge>
            <Badge tone="neutral">{t('unauthOpen')}: {reg.open}</Badge>
            <Badge tone="neutral">{t('unauthDisposedCount')}: {reg.disposed}</Badge>
            {reg.autoAuthorisedEffective > 0 && (
              <Badge tone="warning">
                {t('unauthAutoOnly')}: {reg.autoAuthorisedEffective}
              </Badge>
            )}
          </div>
          <p className="changes-kpi-hint">{t('unauthGapsHint')}</p>
        </>
      )}

      {reg && reg.findings.length === 0 && (
        <EmptyState Icon={FileCheck2}
                    title={t('unauthNoneTitle')}
                    sub={t('unauthNoneHint')} />
      )}

      {reg && reg.findings.length > 0 && (
        <div className="changes-gov-table-wrap">
          <table className="changes-gov-table">
            <thead>
              <tr>
                <th>{t('unauthColChange')}</th>
                <th>{t('unauthColReason')}</th>
                <th>{t('unauthColRequired')}</th>
                <th>{t('unauthColRecorded')}</th>
                <th>{t('unauthColStatus')}</th>
                <th>{t('unauthColDisposition')}</th>
                {canDispose && <th />}
              </tr>
            </thead>
            <tbody>
              {reg.findings.map(f => (
                <tr key={`${f.changeId}:${f.reasonCode}`}>
                  <td>
                    <strong>{ref(f)}</strong>
                    <div className="changes-kpi-hint">{title(f)}</div>
                  </td>
                  <td>
                    <Badge tone={REASON_TONE[f.reasonCode] ?? 'neutral'}>
                      {t(`unauthReason_${f.reasonCode}`)}
                    </Badge>
                    {f.effective && isAuthorityGap(f) && (
                      <div className="changes-kpi-hint">
                        <ShieldAlert size={12} /> {t('unauthLiveInProduction')}
                      </div>
                    )}
                  </td>
                  <td>
                    {f.gateRequired ?? '—'}
                    {f.minApprovalsRequired != null && ` / ${f.minApprovalsRequired}`}
                  </td>
                  <td>
                    {f.approvalsRecorded ?? 0}
                    {!f.cabDecisionStamped && (
                      <div className="changes-kpi-hint">{t('unauthNoStamp')}</div>
                    )}
                  </td>
                  <td>{f.status}</td>
                  <td>
                    {f.dispositionState
                      ? (
                        <>
                          <Badge tone="neutral">{t(`unauthState_${f.dispositionState}`)}</Badge>
                          <div className="changes-kpi-hint">
                            {f.dispositionByName}
                            {f.dispositionAt ? ` · ${new Date(f.dispositionAt).toLocaleDateString()}` : ''}
                          </div>
                        </>
                      )
                      /* Never blank: an empty cell in a findings register reads as "no concern",
                         which is the opposite of what an undisposed finding means (S8d's rule). */
                      : <span className="changes-kpi-hint">{t('unauthNotInvestigated')}</span>}
                  </td>
                  {canDispose && (
                    <td>
                      <Button variant="ghost" onClick={() => void dispose(f)}>
                        {f.dispositionState ? t('unauthRedispose') : t('unauthDispose')}
                      </Button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* ⚠ On screen, not just in the payload. This is the difference between a register that closes
          G-11 honestly and one that claims coverage it hasn't got. */}
      {reg && reg.undetectable.length > 0 && (
        <div className="changes-empty-note" style={{ marginTop: 12 }}>
          <strong><EyeOff size={12} /> {t('unauthBlindTitle')}</strong>
          <ul style={{ margin: '6px 0 0', paddingInlineStart: 18 }}>
            {reg.undetectable.map(u => (
              <li key={u} className="changes-kpi-hint">{t(`unauthBlind_${u}`)}</li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}
