import { useEffect, useMemo, useState } from 'react';
import { ShieldCheck, Snowflake, CalendarDays, Plus, Trash2 } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import {
  changeApi, ApiError,
  type ChangeAuthMatrixCell, type ChangeFreezeWindowDto, type ChangeCalendarView,
} from '../../lib/api';

const GATE_TONE: Record<string, BadgeTone> = { AUTO: 'success', CAB: 'info', ECAB: 'warning' };
const RISK_TONE: Record<string, BadgeTone> = { LOW: 'success', MEDIUM: 'info', HIGH: 'warning', CRITICAL: 'danger' };

/**
 * CH-17c — Governance panel: surfaces the classification-driven authorisation
 * matrix, freeze/blackout windows (with create/delete), and the change calendar
 * for the current tenant. Read-mostly; freeze windows are manageable inline.
 */
export function GovernancePanel({ toast }: { toast: ReturnType<typeof useToast> }) {
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const dialog = useDialog();

  const [matrix, setMatrix] = useState<ChangeAuthMatrixCell[]>([]);
  const [freezes, setFreezes] = useState<ChangeFreezeWindowDto[]>([]);
  const [calendar, setCalendar] = useState<ChangeCalendarView | null>(null);
  const [freezeOpen, setFreezeOpen] = useState(false);

  async function load() {
    try {
      const [m, f, c] = await Promise.all([
        changeApi.authorisationMatrix(),
        changeApi.freezeWindows(),
        changeApi.calendar(),
      ]);
      setMatrix(m); setFreezes(f); setCalendar(c);
    } catch { /* silent — backend down surfaces elsewhere */ }
  }
  useEffect(() => { void load(); }, []);

  const freezeInitial = useMemo(() => ({
    nameEn: '', nameAr: '', startsAt: '', endsAt: '', allowEmergency: 'true',
  }), [freezeOpen]);

  async function submitFreeze(v: { nameEn: string; nameAr: string; startsAt: string; endsAt: string; allowEmergency: string }) {
    if (!v.startsAt || !v.endsAt) { toast.danger(t('govFreezeDatesReq')); return; }
    try {
      await changeApi.createFreeze({
        nameEn: v.nameEn, nameAr: v.nameAr || undefined,
        startsAt: `${v.startsAt}T00:00:00`, endsAt: `${v.endsAt}T23:59:59`,
        scope: 'ALL', allowEmergency: v.allowEmergency === 'true', active: true,
      });
      setFreezeOpen(false);
      toast.success(t('govFreezeCreated'));
      void load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function removeFreeze(w: ChangeFreezeWindowDto) {
    if (!w.id) return;
    const ok = await dialog.confirm({
      title: t('govFreezeDeleteTitle'),
      body: `${isAr ? w.nameAr || w.nameEn : w.nameEn}`,
      destructive: true, submitLabel: t('deleteBtn'),
    });
    if (!ok) return;
    try { await changeApi.deleteFreeze(w.id); toast.info(t('govFreezeRemoved')); void load(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const d = (s?: string) => (s ? new Date(s).toLocaleDateString() : '—');

  return (
    <div className="changes-gov-stack">
      {/* Authorisation matrix */}
      <Card title={t('govAuthMatrixTitle')} Icon={ShieldCheck}>
        <p className="changes-gov-sub">{t('govAuthMatrixSub')}</p>
        {matrix.length === 0
          ? <div className="changes-empty-note">{t('govAuthMatrixEmpty')}</div>
          : (
            <div className="changes-gov-table-wrap">
              <table className="changes-gov-table">
                <thead>
                  <tr>
                    <th>{t('govColType')}</th><th>{t('govColRisk')}</th>
                    <th>{t('govColGate')}</th><th>{t('govColMinApprovals')}</th>
                    <th>{t('govColRoles')}</th>
                  </tr>
                </thead>
                <tbody>
                  {matrix.map(c => (
                    <tr key={c.id}>
                      <td><Badge tone="neutral">{c.changeType}</Badge></td>
                      <td><Badge tone={RISK_TONE[c.riskLevel] ?? 'neutral'}>{c.riskLevel}</Badge></td>
                      <td><Badge tone={GATE_TONE[c.gate] ?? 'neutral'}>{c.gate}</Badge></td>
                      <td className="mono">{c.minApprovals}</td>
                      <td className="changes-gov-roles">{c.requiredRoles || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
      </Card>

      {/* Freeze windows */}
      <Card title={t('govFreezeTitle')} Icon={Snowflake}
        action={<Button variant="primary" Icon={Plus} onClick={() => setFreezeOpen(true)}>{t('govFreezeNew')}</Button>}>
        <p className="changes-gov-sub">{t('govFreezeSub')}</p>
        {freezes.length === 0
          ? <EmptyState Icon={Snowflake} title={t('govFreezeEmptyTitle')} sub={t('govFreezeEmptySub')} />
          : (
            <div className="changes-gov-freezes">
              {freezes.map(w => (
                <div key={w.id} className="changes-gov-freeze-row">
                  <div className="changes-gov-freeze-main">
                    <strong>{isAr && w.nameAr ? w.nameAr : w.nameEn}</strong>
                    <span className="changes-gov-freeze-dates mono">{d(w.startsAt)} → {d(w.endsAt)}</span>
                  </div>
                  <Badge tone={w.active ? 'primary' : 'neutral'}>{w.scope}</Badge>
                  {w.allowEmergency && <Badge tone="info">{t('govFreezeAllowEmg')}</Badge>}
                  <button onClick={() => removeFreeze(w)} title={t('deleteBtn')} className="changes-gov-freeze-del">
                    <Trash2 size={14} />
                  </button>
                </div>
              ))}
            </div>
          )}
      </Card>

      {/* Change calendar */}
      <Card title={t('govCalTitle')} Icon={CalendarDays}>
        <p className="changes-gov-sub">{t('govCalSub')}</p>
        {!calendar || calendar.changes.length === 0
          ? <div className="changes-empty-note">{t('govCalEmpty')}</div>
          : (
            <div className="changes-gov-table-wrap">
              <table className="changes-gov-table">
                <thead>
                  <tr>
                    <th>{t('govColChange')}</th><th>{t('govColType')}</th>
                    <th>{t('govColRisk')}</th><th>{t('govColWindow')}</th>
                    <th>{t('govColStatus')}</th>
                  </tr>
                </thead>
                <tbody>
                  {calendar.changes.map(c => (
                    <tr key={c.id}>
                      <td>
                        <span className="mono">{c.changeCode ?? `#${c.id}`}</span>
                        <div className="changes-gov-caltitle">{isAr && c.titleAr ? c.titleAr : c.titleEn}</div>
                      </td>
                      <td><Badge tone="neutral">{c.changeType}</Badge></td>
                      <td><Badge tone={RISK_TONE[c.riskLevel ?? ''] ?? 'neutral'}>{c.riskLevel}</Badge></td>
                      <td className="mono">{d(c.scheduledStart)}{c.scheduledEnd ? ` → ${d(c.scheduledEnd)}` : ''}</td>
                      <td>{(c.status ?? '').replace(/_/g, ' ')}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        {calendar && calendar.freezes.length > 0 && (
          <div className="changes-gov-freeze-note">
            <Snowflake size={13} /> {t('govCalFreezeNote')}: {calendar.freezes.map(f => isAr && f.nameAr ? f.nameAr : f.nameEn).join(' · ')}
          </div>
        )}
      </Card>

      <FormModal
        open={freezeOpen}
        title={t('govFreezeNew')}
        size="md"
        fields={[
          { kind: 'text', key: 'nameEn', label: t('govFreezeNameEn'), required: true, span: 6 },
          { kind: 'text', key: 'nameAr', label: t('govFreezeNameAr'), span: 6 },
          { kind: 'date', key: 'startsAt', label: t('govFreezeStart'), required: true, span: 6 },
          { kind: 'date', key: 'endsAt', label: t('govFreezeEnd'), required: true, span: 6 },
          { kind: 'select', key: 'allowEmergency', label: t('govFreezeEmgLabel'), span: 12, options: [
            { value: 'true', label: t('govFreezeEmgYes') },
            { value: 'false', label: t('govFreezeEmgNo') },
          ] },
        ] as FieldDef[]}
        initial={freezeInitial}
        submitLabel={t('govFreezeNew')}
        onClose={() => setFreezeOpen(false)}
        onSubmit={submitFreeze}
      />
    </div>
  );
}
