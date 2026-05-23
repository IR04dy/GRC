import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useI18n } from '../../lib/i18n';
import { getUser } from '../../lib/auth';
import { useToast } from '../../lib/toast';
import {
  fetchActiveCrisisSummary, ensurePushSubscription,
  formatCountdown, formatRelativeAgo,
  type ActiveCrisisSummaryRow,
} from '../../lib/mobileCrisis';
import { ApiError } from '../../lib/api';
import { formatHijri } from '../../lib/sla';
import '../../styles/mobile.css';

/**
 * Slice 9-F — Mobile Crisis Room.
 *
 * <p>Single-column, thumb-driven layout for the Crisis Commander on a
 * phone during a live incident. Renders without the desktop Shell — the
 * commander shouldn't have to dismiss a sidebar drawer in the middle of
 * a ransomware event.
 *
 * <p>Pieces:
 * <ul>
 *   <li>Sticky top bar — org name + Hijri/Gregorian date toggle</li>
 *   <li>"حالة طوارئ" banner if any CRITICAL (sev ≥ 4) crisis is active</li>
 *   <li>Push opt-in banner (dismissible) — first visit only</li>
 *   <li>Active crises stacked as cards (severity dot · code · title ·
 *       countdown chip · owner · 4-button action row)</li>
 *   <li>Bottom-sheet detail modal on card tap</li>
 *   <li>Sticky bottom "إعلان أزمة" action bar</li>
 * </ul>
 *
 * <p>Uses the SAME query function as the desktop CrisisRoomPage so the
 * react-query cache stays warm between desktop ↔ mobile. (No QueryClient
 * is wired in this project — we use plain effects + manual refetch.)
 */
export function MobileCrisisRoomPage() {
  const { lang, dir, toggleLang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const nav = useNavigate();

  const [rows, setRows] = useState<ActiveCrisisSummaryRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [hijri, setHijri] = useState(false);
  const [selected, setSelected] = useState<ActiveCrisisSummaryRow | null>(null);
  const [pushBannerDismissed, setPushBannerDismissed] = useState(false);
  const [now, setNow] = useState(() => Date.now());

  const user = useMemo(() => getUser(), []);
  const orgLabel = useMemo(() => {
    if (!user) return isAr ? 'منظمتي' : 'My Organisation';
    return isAr ? `المنظمة #${user.organisationId}` : `Org #${user.organisationId}`;
  }, [user, isAr]);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchActiveCrisisSummary();
      setRows(data ?? []);
    } catch (err) {
      const msg = err instanceof ApiError
        ? err.message
        : (isAr ? 'تعذّر تحميل الأزمات' : 'Could not load crises');
      toast.danger(msg);
    } finally {
      setLoading(false);
    }
  }, [isAr, toast]);

  useEffect(() => { void reload(); }, [reload]);

  // Heartbeat — re-render countdowns every 30 s without re-fetching from server.
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 30_000);
    return () => clearInterval(t);
  }, []);

  // Push opt-in: only show the banner if browser supports it AND the user
  // hasn't already granted or denied. Don't auto-prompt — that's hostile UX.
  const showPushBanner = useMemo(() => {
    if (pushBannerDismissed) return false;
    if (typeof window === 'undefined') return false;
    if (!('Notification' in window)) return false;
    if (!('serviceWorker' in navigator) || !('PushManager' in window)) return false;
    return Notification.permission === 'default';
  }, [pushBannerDismissed]);

  // Re-compute the live minutes-remaining off the deadline because the server
  // value was snapshotted at fetch time. Keeps the chip ticking down.
  const liveCountdownMinutes = useCallback((row: ActiveCrisisSummaryRow): number | null => {
    if (!row.regulatoryDeadlineAt) return null;
    const dl = new Date(row.regulatoryDeadlineAt).getTime();
    if (!isFinite(dl)) return row.regulatoryMinutesRemaining ?? null;
    return Math.floor((dl - now) / 60_000);
  }, [now]);

  const hasCritical = useMemo(() => rows.some(r => (r.severity ?? 0) >= 4), [rows]);

  const onAck = useCallback(async (row: ActiveCrisisSummaryRow) => {
    // Acknowledge = take ownership; backend reuses PATCH on crisis-events.
    try {
      // Minimal: assign current user as crisisManagerId via the existing PATCH endpoint.
      // (The desktop CrisisRoomPage uses the same wire shape.)
      if (!user) throw new Error('No user');
      const { api } = await import('../../lib/api');
      await api.patch<unknown>(`/api/v1/bcm/crisis-events/${row.id}`, { crisisManagerId: user.userId });
      toast.success(isAr ? 'تم الاستلام' : 'Acknowledged');
      await reload();
    } catch {
      toast.danger(isAr ? 'تعذّر الاستلام' : 'Could not acknowledge');
    }
  }, [user, toast, isAr, reload]);

  const onDispatchComms = useCallback((row: ActiveCrisisSummaryRow) => {
    nav(`/bcm/comms/v2/dispatch?crisisId=${row.id}`);
  }, [nav]);

  const onViewDesktop = useCallback((row: ActiveCrisisSummaryRow) => {
    nav(`/bcm/crisis-room?eventId=${row.id}`);
  }, [nav]);

  const onPushOptIn = useCallback(async () => {
    const r = await ensurePushSubscription();
    if (r.kind === 'ok') {
      toast.success(isAr ? 'تم تفعيل التنبيهات' : 'Notifications enabled');
    } else if (r.kind === 'denied') {
      toast.danger(isAr ? 'تم رفض الإذن' : 'Permission denied');
    } else {
      toast.info(isAr ? 'التنبيهات غير متاحة على هذا الجهاز' : 'Push not supported here');
    }
    setPushBannerDismissed(true);
  }, [isAr, toast]);

  const todayLabel = useMemo(() => {
    const d = new Date();
    if (hijri) return formatHijri(d, lang as 'ar' | 'en');
    return d.toLocaleDateString(isAr ? 'ar-SA' : 'en-GB');
  }, [hijri, lang, isAr]);

  return (
    <div className="mob-shell" dir={dir}>
      {/* ─── Top bar ────────────────────────────────────────── */}
      <div className="mob-topbar">
        <div>
          <div className="mob-topbar-title">
            {isAr ? 'غرفة إدارة الأزمات (محمول)' : 'Mobile Crisis Room'}
          </div>
          <div className="mob-topbar-sub">{orgLabel}</div>
        </div>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <button
            className="mob-refresh-btn"
            onClick={() => setHijri(h => !h)}
            aria-label={isAr ? 'تبديل التقويم' : 'Toggle calendar'}
            title={todayLabel}
          >
            {hijri ? (isAr ? 'هجري' : 'Hijri') : (isAr ? 'ميلادي' : 'Gregorian')}
          </button>
          <button
            className="mob-refresh-btn"
            onClick={toggleLang}
            aria-label="Toggle language"
          >
            {isAr ? 'EN' : 'ع'}
          </button>
        </div>
      </div>

      {/* ─── Critical-mode banner (only if a CRITICAL crisis is active) ─── */}
      {hasCritical && (
        <div className="mob-critical-banner" role="alert" aria-live="assertive">
          <span aria-hidden="true">🆘</span>
          <span>{isAr ? 'حالة طوارئ — أزمة حرجة نشطة' : 'CRITICAL — active crisis'}</span>
        </div>
      )}

      {/* ─── Push opt-in banner ───────────────────────────── */}
      {showPushBanner && (
        <div className="mob-banner">
          <span>{isAr
            ? 'تفعيل التنبيهات لاستلام إشعارات الأزمات الحرجة'
            : 'Enable notifications for critical crisis alerts'}</span>
          <button onClick={onPushOptIn}>{isAr ? 'تفعيل' : 'Enable'}</button>
          <button onClick={() => setPushBannerDismissed(true)} aria-label="Dismiss">
            {isAr ? '✕' : '✕'}
          </button>
        </div>
      )}

      {/* ─── Active crises ────────────────────────────────── */}
      <div className="mob-content">
        <div className="mob-refresh-row">
          <button
            className="mob-refresh-btn"
            onClick={() => void reload()}
            disabled={loading}
          >
            {loading
              ? (isAr ? 'جارٍ التحميل…' : 'Loading…')
              : (isAr ? 'تحديث' : 'Refresh')}
          </button>
        </div>

        {rows.length === 0 && !loading && (
          <div className="mob-empty">
            {isAr
              ? 'لا توجد أزمات نشطة حالياً. كل شيء تحت السيطرة.'
              : 'No active crises. All systems nominal.'}
          </div>
        )}

        {rows.map(row => (
          <CrisisCard
            key={row.id}
            row={row}
            isAr={isAr}
            liveMinutes={liveCountdownMinutes(row)}
            onTap={() => setSelected(row)}
            onAck={() => onAck(row)}
            onComms={() => onDispatchComms(row)}
            onView={() => onViewDesktop(row)}
          />
        ))}
      </div>

      {/* ─── Sticky bottom action bar ─────────────────────── */}
      <div className="mob-bottom-bar">
        <Link to="/bcm/crisis-room?declare=1" className="mob-btn mob-btn-danger" style={{ width: '100%' }}>
          <span aria-hidden="true">🆘</span>
          <span style={{ marginInlineStart: 8 }}>
            {isAr ? 'إعلان أزمة جديدة' : 'Declare new crisis'}
          </span>
        </Link>
      </div>

      {/* ─── Bottom-sheet detail modal ────────────────────── */}
      {selected && (
        <CrisisDetailSheet
          row={selected}
          isAr={isAr}
          onClose={() => setSelected(null)}
          onAck={() => { void onAck(selected); setSelected(null); }}
          onComms={() => onDispatchComms(selected)}
          onView={() => onViewDesktop(selected)}
        />
      )}
    </div>
  );
}

// ────────────────────────────────────────────────────────────
// Card
// ────────────────────────────────────────────────────────────

function CrisisCard({
  row, isAr, liveMinutes, onTap, onAck, onComms, onView,
}: {
  row: ActiveCrisisSummaryRow;
  isAr: boolean;
  liveMinutes: number | null;
  onTap: () => void;
  onAck: () => void;
  onComms: () => void;
  onView: () => void;
}) {
  const title = isAr ? (row.titleAr || row.titleEn) : (row.titleEn || row.titleAr || row.code);
  const ownerInitials = row.ownerUserId
    ? `#${row.ownerUserId}`.slice(0, 3)
    : null;

  // Countdown chip tone: danger if breached / ≤ 1h, warning if ≤ 25% of window left, info otherwise.
  const countdownTone =
    liveMinutes === null ? 'mob-chip' :
    liveMinutes <= 0 ? 'mob-chip mob-chip-danger' :
    liveMinutes <= 60 ? 'mob-chip mob-chip-danger' :
    liveMinutes <= 720 ? 'mob-chip mob-chip-warning' :
    'mob-chip mob-chip-info';

  return (
    <div className="mob-card" onClick={onTap} role="button" tabIndex={0}
         onKeyDown={(e) => { if (e.key === 'Enter') onTap(); }}>
      <div className="mob-card-header">
        <span className={`mob-sev-dot mob-sev-${row.severity}`} aria-hidden="true" />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="mob-card-title">{title}</div>
          <div className="mob-card-code">{row.code} · {row.escalationTier}</div>
        </div>
      </div>

      <div className="mob-chip-row">
        {liveMinutes !== null && row.regulatoryRegulator && (
          <span className={countdownTone}>
            <span style={{ fontWeight: 800 }}>{row.regulatoryRegulator.replace('_', ' ')}</span>
            <span style={{ marginInlineStart: 6 }}>{formatCountdown(liveMinutes, isAr ? 'ar' : 'en')}</span>
          </span>
        )}
        {row.personalDataAffected && (
          <span className="mob-chip mob-chip-danger">{isAr ? 'بيانات شخصية' : 'PII'}</span>
        )}
        {row.cyberRelated && (
          <span className="mob-chip mob-chip-warning">{isAr ? 'سيبراني' : 'Cyber'}</span>
        )}
        {row.supplierInvolved && (
          <span className="mob-chip">{isAr ? 'مورّد' : 'Supplier'}</span>
        )}
        {ownerInitials ? (
          <span className="mob-chip mob-chip-success">
            <span className="mob-avatar" aria-hidden="true">{ownerInitials}</span>
            <span style={{ marginInlineStart: 6 }}>{isAr ? 'مالك' : 'Owner'}</span>
          </span>
        ) : (
          <span className="mob-chip mob-chip-warning">{isAr ? 'بحاجة لمالك' : 'Needs owner'}</span>
        )}
      </div>

      <div className="mob-action-row" onClick={e => e.stopPropagation()}>
        <button className="mob-btn mob-btn-primary" onClick={onAck}
                aria-label={isAr ? 'استلم' : 'Acknowledge'}>
          {isAr ? 'استلم' : 'Ack'}
        </button>
        <button className="mob-btn" onClick={onAck}
                aria-label={isAr ? 'تكليف' : 'Assign'}>
          {isAr ? 'تكليف' : 'Assign'}
        </button>
        <button className="mob-btn" onClick={onComms}
                aria-label={isAr ? 'إعلام' : 'Dispatch comms'}>
          {isAr ? 'إعلام' : 'Comms'}
        </button>
        <button className="mob-btn" onClick={onView}
                aria-label={isAr ? 'عرض' : 'View'}>
          {isAr ? 'عرض' : 'View'}
        </button>
      </div>
    </div>
  );
}

// ────────────────────────────────────────────────────────────
// Detail sheet
// ────────────────────────────────────────────────────────────

function CrisisDetailSheet({
  row, isAr, onClose, onAck, onComms, onView,
}: {
  row: ActiveCrisisSummaryRow;
  isAr: boolean;
  onClose: () => void;
  onAck: () => void;
  onComms: () => void;
  onView: () => void;
}) {
  const title = isAr ? (row.titleAr || row.titleEn) : (row.titleEn || row.titleAr || row.code);
  // Tap-to-call key contacts. Until we wire a real role→user lookup, the
  // mobile sheet shows placeholder Saudi-native "غير مُعرَّف" tiles so the
  // Crisis Commander knows the slot exists and who to escalate to.
  const contacts = [
    { roleAr: 'كبير أمن المعلومات', roleEn: 'CISO',          phone: null },
    { roleAr: 'مسؤول الاتصالات',   roleEn: 'Comms Lead',    phone: null },
    { roleAr: 'رئيس لجنة المراجعة', roleEn: 'Audit Chair',  phone: null },
  ];

  return (
    <div className="mob-sheet-backdrop" onClick={onClose}>
      <div className="mob-sheet" role="dialog" aria-modal="true"
           onClick={(e) => e.stopPropagation()}>
        <div className="mob-sheet-handle" aria-hidden="true" />
        <div>
          <div className="mob-card-title">{title}</div>
          <div className="mob-card-code">
            {row.code} · {isAr ? 'فُتح ' : 'opened '}
            {formatRelativeAgo(row.openedAt, isAr ? 'ar' : 'en')}
          </div>
        </div>

        <button className="mob-btn mob-btn-primary" style={{ width: '100%' }} onClick={onAck}>
          {isAr ? 'استلم وتكليف' : 'Acknowledge & take ownership'}
        </button>

        <button className="mob-btn" style={{ width: '100%' }} onClick={onComms}>
          {isAr ? 'إعلام — بدء سلسلة الاتصالات' : 'Dispatch comms cascade'}
        </button>

        <button className="mob-btn" style={{ width: '100%' }} onClick={onView}>
          {isAr ? 'فتح غرفة الأزمات الكاملة' : 'Open full Crisis Room'}
        </button>

        <div style={{ fontWeight: 700, marginTop: 4 }}>
          {isAr ? 'جهات اتصال رئيسية' : 'Key contacts'}
        </div>
        <div className="mob-contact-list">
          {contacts.map(c => (
            <a key={c.roleEn}
               className="mob-contact"
               href={c.phone ? `tel:${c.phone}` : undefined}
               onClick={(e) => { if (!c.phone) e.preventDefault(); }}>
              <span className="mob-avatar" aria-hidden="true">{c.roleEn.slice(0, 1)}</span>
              <div>
                <div className="mob-contact-role">{isAr ? c.roleAr : c.roleEn}</div>
                <div className="mob-contact-name">
                  {c.phone ? (isAr ? 'متاح' : 'Available') : (isAr ? 'غير مُعرَّف' : 'Not configured')}
                </div>
              </div>
              {c.phone && <span className="mob-contact-phone">{c.phone}</span>}
            </a>
          ))}
        </div>

        <button className="mob-btn" style={{ width: '100%' }} onClick={onClose}>
          {isAr ? 'إغلاق' : 'Close'}
        </button>
      </div>
    </div>
  );
}
