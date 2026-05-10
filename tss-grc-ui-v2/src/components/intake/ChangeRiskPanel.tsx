// Shared change-risk panel — DD-72.
//
// Originally inline in IntakeFormPage's acknowledgement screen so the
// submitter saw the score, classification, CAB gate and hard-stop
// warnings produced by computeChangeRisk(). DD-72 lifts it into a
// shared component so the same panel can be reused inside the triager
// inbox modal — the CAB triager now sees the same automated assessment
// the submitter saw, without having to re-derive it from the raw JSON
// payload. Keeps the audit narrative consistent end-to-end.

import { ShieldAlert } from 'lucide-react';
import { Badge } from '../ui/Badge';
import { changeRiskTone, type ChangeRiskBreakdown } from '../../lib/changeRisk';

export function ChangeRiskPanel({ risk, t, lang }: {
  risk: ChangeRiskBreakdown;
  t: (k: string) => string;
  lang: 'en' | 'ar';
}) {
  const tone = changeRiskTone(risk.classification);
  const toneVar = (s: 'success' | 'info' | 'warning' | 'danger') =>
    s === 'success' ? 'var(--success-fg)' :
    s === 'info'    ? 'var(--info-fg)' :
    s === 'warning' ? 'var(--warning-fg)' : 'var(--danger-fg)';
  const bgVar = (s: 'success' | 'info' | 'warning' | 'danger') =>
    s === 'success' ? 'var(--success-bg)' :
    s === 'info'    ? 'var(--info-bg)' :
    s === 'warning' ? 'var(--warning-bg)' : 'var(--danger-bg)';

  const classLabel = t(`changeRiskClass${risk.classification}`);
  const gateLabel = t(`changeRiskGate${risk.cabGate}`);

  return (
    <div style={{
      marginTop: 16, padding: 16, borderRadius: 'var(--r-sm)',
      border: `1px solid var(--border)`, background: 'var(--surface-2)',
      textAlign: lang === 'ar' ? 'right' : 'left',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
        <ShieldAlert size={18} style={{ color: toneVar(tone) }} />
        <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--ink-1)' }}>{t('changeRiskPanelTitle')}</div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12, marginBottom: 12 }}>
        <div>
          <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{t('changeRiskScore')}</div>
          <div style={{ fontSize: 24, fontWeight: 700, color: toneVar(tone), marginTop: 4 }}>{risk.score}<span style={{ fontSize: 13, color: 'var(--ink-3)', fontWeight: 500 }}>/100</span></div>
        </div>
        <div>
          <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{t('changeRiskClassification')}</div>
          <div style={{ marginTop: 6 }}>
            <Badge tone={tone} dot>{classLabel}</Badge>
          </div>
        </div>
        <div>
          <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{t('changeRiskApprovalPath')}</div>
          <div style={{ marginTop: 6 }}>
            <Badge tone={risk.cabGate === 'AUTO_APPROVED' ? 'success' : risk.cabGate === 'CAB_REQUIRED' ? 'info' : 'warning'}>
              {gateLabel}
            </Badge>
          </div>
        </div>
      </div>

      {risk.warnings.length > 0 && (
        <div style={{
          padding: 10, borderRadius: 'var(--r-sm)',
          background: bgVar('warning'),
          color: toneVar('warning'),
          fontSize: 12, lineHeight: 1.6, marginBottom: 12,
        }}>
          <div style={{ fontWeight: 700, marginBottom: 4 }}>{t('changeRiskWarningsTitle')}</div>
          <ul style={{ margin: 0, paddingInlineStart: 16 }}>
            {risk.warnings.map(w => (
              <li key={w}>{t(`changeRiskWarning${w}`)}</li>
            ))}
          </ul>
        </div>
      )}

      <details>
        <summary style={{ fontSize: 11, color: 'var(--ink-3)', cursor: 'pointer' }}>{t('changeRiskBreakdownToggle')}</summary>
        <table style={{ width: '100%', marginTop: 8, fontSize: 11, borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
              <th style={{ textAlign: lang === 'ar' ? 'right' : 'left', padding: '4px 8px' }}>{t('changeRiskFactor')}</th>
              <th style={{ textAlign: lang === 'ar' ? 'left'  : 'right', padding: '4px 8px' }}>{t('changeRiskWeight')}</th>
            </tr>
          </thead>
          <tbody>
            {risk.factors.map(f => (
              <tr key={f.key} style={{ borderTop: '1px dashed var(--border)' }}>
                <td style={{ padding: '4px 8px', color: 'var(--ink-2)' }}>{f.reason}</td>
                <td style={{ padding: '4px 8px', textAlign: 'end', fontFamily: 'var(--font-mono)', color: 'var(--ink-1)' }}>+{f.weight}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  );
}
