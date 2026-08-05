/**
 * S8g / CE-8 — the forward schedule of change (FSC), declared outages, and schedule collisions.
 *
 * **What this card refuses to do.** It never shows an empty collision list as "no collisions" without
 * also saying how many changes it could not check, and it never labels a change as causing an outage
 * on the strength of the flag alone.
 *
 * The first is `unscheduledCount`: a forward-status change with no window cannot collide with anything,
 * so a clean list over an incomplete population reads as a clean schedule. The second is
 * `outageDeclared` rather than `outageExpected` — a declaration needs a duration and a named service,
 * and the server refuses an incomplete one, so the UI must not render one either.
 *
 * And "never published" is distinguished from "published but expired", because a tenant that issued a
 * January schedule and stopped has a different problem from one that never issued anything.
 */
import { useEffect, useState } from 'react';
import { CalendarClock, Send, PowerOff, AlertTriangle, Snowflake, FileClock, ShieldCheck } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import {
  changeApi, ApiError,
  type ForwardScheduleDto, type ScheduleEntryDto, type ScheduleCollisionDto,
} from '../../lib/api';
import { canApprove } from '../../lib/auth';

/**
 * Tone by collision reason.
 *
 * ⚠ `OUTAGE_OVERLAP` is `danger` and `WINDOW_OVERLAP` is only `warning`. Two overlapping windows may
 * be perfectly fine; two overlapping *declared outages* is a business-visible incident being scheduled
 * on purpose, and flattening them to one colour loses the only distinction that matters here.
 */
export const COLLISION_TONE: Record<string, BadgeTone> = {
  OUTAGE_OVERLAP: 'danger',
  INSIDE_FREEZE_WINDOW: 'danger',
  WINDOW_OVERLAP: 'warning',
};

/** Days the default FSC window looks ahead. Mirrors the controller's default. */
export const FSC_DEFAULT_DAYS = 90;

/**
 * Whether an entry should be shown as taking service down.
 *
 * Exported so a test can pin it against the server's `hasDeclaredOutage()`: the raw `outageExpected`
 * flag is NOT sufficient, because `ck_change_pso` refuses a flag without a duration and named
 * services — so rendering on the flag alone would show an outage the database would not accept.
 */
export function showsOutage(e: ScheduleEntryDto): boolean {
  return e.outageDeclared;
}

function iso(d: Date): string {
  // Local-time ISO without the zone: the API takes LocalDateTime, and appending 'Z' would shift the
  // window by the operator's offset — in Riyadh that is three hours of schedule silently missing.
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:00`;
}

export function ForwardSchedulePanel({ toast }: { toast: ReturnType<typeof useToast> }) {
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const dialog = useDialog();

  const [fsc, setFsc] = useState<ForwardScheduleDto | null>(null);
  const [busy, setBusy] = useState(false);
  const canPublish = canApprove('CHANGE');

  async function load() {
    try { setFsc(await changeApi.forwardSchedule()); }
    catch { /* silent — a backend-down window surfaces elsewhere on this page */ }
  }
  useEffect(() => { void load(); }, []);

  async function publish() {
    if (!fsc) return;
    const notes = await dialog.prompt({
      title: t('fscPublishTitle'),
      label: t('fscPublishNote'),
      helpText: t('fscPublishHint'),
      type: 'textarea',
    });
    if (notes === null) return;
    setBusy(true);
    try {
      const from = new Date(); from.setDate(from.getDate() - 1);
      const to = new Date(); to.setDate(to.getDate() + FSC_DEFAULT_DAYS - 1);
      const p = await changeApi.publishSchedule(iso(from), iso(to), notes || undefined);
      toast.success(`${t('fscPublished')} — ${p.entryCount} ${t('fscEntries')}`);
      void load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    } finally { setBusy(false); }
  }

  async function verify(id: number) {
    try {
      const r = await changeApi.schedulePublication(id);
      if (r.digestMatches) toast.success(t('fscDigestOk'));
      else toast.danger(t('fscDigestMismatch'));
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const ref = (code?: string | null, id?: number) => code || `#${id}`;
  const title = (e: ScheduleEntryDto) => (isAr ? e.titleAr || e.titleEn : e.titleEn) || '—';
  const dt = (s?: string | null) => (s ? new Date(s).toLocaleString() : '—');

  function collisionLabel(c: ScheduleCollisionDto): string {
    if (c.reasonCode === 'INSIDE_FREEZE_WINDOW') {
      const name = isAr ? c.freezeNameAr || c.freezeNameEn : c.freezeNameEn;
      return `${ref(c.changeCode, c.changeId)} → ${name || c.freezeCode}`;
    }
    return `${ref(c.changeCode, c.changeId)} ↔ ${ref(c.otherChangeCode, c.otherChangeId ?? undefined)}`;
  }

  return (
    <Card title={t('fscTitle')} Icon={CalendarClock}>
      <p className="changes-gov-sub">{t('fscSub')}</p>

      {fsc && (
        <>
          <div className="changes-gov-actions" style={{ flexWrap: 'wrap', gap: 8 }}>
            <Badge tone="info">{t('fscEntriesLabel')}: {fsc.entries.length}</Badge>
            <Badge tone={fsc.outageCount > 0 ? 'warning' : 'neutral'}>
              {t('fscOutages')}: {fsc.outageCount}
              {fsc.totalOutageMinutes > 0 && ` (${fsc.totalOutageMinutes} ${t('fscMinutes')})`}
            </Badge>
            <Badge tone={fsc.collisions.length > 0 ? 'danger' : 'success'}>
              {t('fscCollisions')}: {fsc.collisions.length}
            </Badge>
            {fsc.freezeCount > 0 && (
              <Badge tone="danger">{t('fscInFreeze')}: {fsc.freezeCount}</Badge>
            )}
            {/* ⚠ Always rendered when non-zero: a clean collision list over an incomplete
                population is not a clean schedule. */}
            {fsc.unscheduledCount > 0 && (
              <Badge tone="warning">{t('fscUnscheduled')}: {fsc.unscheduledCount}</Badge>
            )}
          </div>
          {fsc.unscheduledCount > 0 && (
            <p className="changes-kpi-hint">{t('fscUnscheduledHint')}</p>
          )}

          {/* Publication standing. "Never" and "stale" are separate states on purpose. */}
          <div className="changes-empty-note" style={{ marginTop: 10 }}>
            {!fsc.latestPublication
              ? <><FileClock size={12} /> <strong>{t('fscNeverPublished')}</strong>
                  <div className="changes-kpi-hint">{t('fscNeverPublishedHint')}</div></>
              : (
                <>
                  <ShieldCheck size={12} />{' '}
                  <strong>{t('fscPublishedOn')} {dt(fsc.latestPublication.publishedAt)}</strong>
                  {' — '}{fsc.latestPublication.publishedByName}
                  {fsc.latestPublicationStale && (
                    <> <Badge tone="warning">{t('fscStale')}</Badge></>
                  )}
                  <div className="changes-kpi-hint">
                    {t('fscCovering')} {dt(fsc.latestPublication.windowFrom)} → {dt(fsc.latestPublication.windowTo)}
                    {' · '}{fsc.latestPublication.entryCount} {t('fscEntries')}
                    {' · '}<code>{fsc.latestPublication.contentSha256.slice(0, 12)}…</code>
                  </div>
                  <Button variant="ghost" onClick={() => void verify(fsc.latestPublication!.id)}>
                    {t('fscVerifyDigest')}
                  </Button>
                </>
              )}
          </div>

          {canPublish && (
            <div className="changes-gov-actions" style={{ marginTop: 10 }}>
              <Button variant="primary" Icon={Send} disabled={busy || fsc.entries.length === 0}
                      onClick={() => void publish()}>
                {busy ? t('fscPublishing') : t('fscPublish')}
              </Button>
              {fsc.entries.length === 0 && (
                <span className="changes-kpi-hint">{t('fscNothingToPublish')}</span>
              )}
            </div>
          )}
        </>
      )}

      {fsc && fsc.entries.length === 0 && (
        <EmptyState Icon={CalendarClock} title={t('fscEmptyTitle')} sub={t('fscEmptyHint')} />
      )}

      {fsc && fsc.entries.length > 0 && (
        <div className="changes-gov-table-wrap" style={{ marginTop: 12 }}>
          <table className="changes-gov-table">
            <thead>
              <tr>
                <th>{t('fscColChange')}</th>
                <th>{t('fscColWindow')}</th>
                <th>{t('fscColStatus')}</th>
                <th>{t('fscColOutage')}</th>
                <th>{t('fscColFlags')}</th>
              </tr>
            </thead>
            <tbody>
              {fsc.entries.map(e => (
                <tr key={e.changeId}>
                  <td>
                    <strong>{ref(e.changeCode, e.changeId)}</strong>
                    <div className="changes-kpi-hint">{title(e)}</div>
                  </td>
                  <td>
                    {dt(e.windowStart)}
                    <div className="changes-kpi-hint">→ {dt(e.windowEnd)}</div>
                  </td>
                  <td>{e.status}</td>
                  <td>
                    {showsOutage(e)
                      ? (
                        <>
                          <Badge tone="warning">
                            <PowerOff size={11} /> {e.outageMinutes} {t('fscMinutes')}
                          </Badge>
                          <div className="changes-kpi-hint">{e.servicesAffected}</div>
                        </>
                      )
                      /* Never blank: "no outage declared" and "nobody said" read the same in an
                         empty cell, and only one of them is reassuring. */
                      : <span className="changes-kpi-hint">{t('fscNoOutage')}</span>}
                  </td>
                  <td>
                    {e.freezeCode && (
                      <Badge tone="danger"><Snowflake size={11} /> {e.freezeCode}</Badge>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {fsc && fsc.collisions.length > 0 && (
        <div style={{ marginTop: 12 }}>
          <strong className="changes-gov-sub">
            <AlertTriangle size={12} /> {t('fscCollisionsTitle')}
          </strong>
          <ul style={{ margin: '6px 0 0', paddingInlineStart: 18 }}>
            {fsc.collisions.map((c, i) => (
              <li key={`${c.reasonCode}-${c.changeId}-${c.otherChangeId ?? c.freezeCode}-${i}`}
                  className="changes-kpi-hint">
                <Badge tone={COLLISION_TONE[c.reasonCode] ?? 'neutral'}>
                  {t(`fscCollision_${c.reasonCode}`)}
                </Badge>{' '}
                {collisionLabel(c)}
                {c.overlapFrom && <> — {dt(c.overlapFrom)} → {dt(c.overlapTo)}</>}
              </li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}
