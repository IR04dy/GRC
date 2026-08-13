import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  Calendar as CalendarIcon, ChevronLeft, ChevronRight, Plus, X,
  ListChecks, LayoutGrid, AlertTriangle, Sparkles, RefreshCw, Download,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { calendarApi, type CalendarEventDto, type CalendarEventCreateRequest } from '../../lib/api';
import {
  buildMonthGrid, buildWeekDays, dowShort, hijriDate, hijriDayOnly,
  monthYearLabel, startOfSaudiWeek, toIsoDate, isSameDay, isSaudiWeekend,
  weekRangeLabel,
  EVENT_TYPE_LABEL, EVENT_TYPE_TONE, type CalendarEventType,
} from '../../lib/saudiCalendar';

type ViewMode = 'month' | 'week' | 'agenda';

/**
 * Compliance Calendar — Outlook-equivalent. Saudi-workdays-aware
 * (Sun-Thu working, Fri-Sat tinted in the grid, public holidays as
 * full-row pills). One feed of every dated thing across the platform
 * — audit days, CAPA dues, finding remediations, risk attestations,
 * AAR reviews, public holidays, plus user-created reminders.
 *
 * <p>Data flows through {@code /api/v1/calendar/events?from&to&types}
 * (PR #60). Each event carries a server-computed {@code onWorkday}
 * flag so the "Falls on weekend" warning chip is consistent with the
 * F8 reminder scheduler.
 *
 * <p>Routes via {@code entityType+entityId} on event click — a CAPA
 * deadline opens {@code /capa/<id>}; a finding opens
 * {@code /findings/<id>}; etc.
 */
export function CalendarPage() {
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();
  const [search, setSearch] = useSearchParams();

  // URL-backed state — month/year + view mode + active type filters.
  // Pasteable links: someone shares /calendar?y=2026&m=5&v=agenda
  // and the recipient sees the same view.
  const now = new Date();
  const year  = parseInt(search.get('y') ?? String(now.getFullYear()), 10);
  const month = parseInt(search.get('m') ?? String(now.getMonth()),    10);
  // Day-of-month anchor for week view. Defaults to today's day if the
  // URL has y/m matching now, otherwise the 1st of the visible month.
  const day = parseInt(
    search.get('d') ?? (year === now.getFullYear() && month === now.getMonth()
                        ? String(now.getDate()) : '1'),
    10);
  const view  = (search.get('v') ?? 'month') as ViewMode;
  const activeTypes = useMemo(() =>
    (search.get('types') ?? '').split(',').filter(Boolean) as CalendarEventType[],
    [search]);
  const includeHolidays = search.get('hol') !== '0';
  const showHijri       = search.get('hijri') !== '0';

  const [events, setEvents] = useState<CalendarEventDto[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [reload, setReload] = useState(0);

  // Compute window [from, to). For month view: 1st of month → 1st
  // of next month + a few days of overflow so the 42-cell grid
  // shows neighbouring days. For week view: Sun → next Sun (7-day
  // window). For agenda view: today → 60 days out.
  const { fromIso, toIso, gridCells, weekDays } = useMemo(() => {
    if (view === 'agenda') {
      const start = new Date(now.getFullYear(), now.getMonth(), now.getDate());
      const end   = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 60);
      return {
        fromIso: toIsoDate(start),
        toIso:   toIsoDate(end),
        gridCells: [] as Date[],
        weekDays: [] as Date[],
      };
    }
    if (view === 'week') {
      const anchor = new Date(year, month, day);
      const days = buildWeekDays(anchor);
      const lastPlus1 = new Date(days[6].getFullYear(), days[6].getMonth(), days[6].getDate() + 1);
      return {
        fromIso: toIsoDate(days[0]),
        toIso:   toIsoDate(lastPlus1),
        gridCells: [] as Date[],
        weekDays: days,
      };
    }
    const cells = buildMonthGrid(year, month);
    return {
      fromIso: toIsoDate(cells[0]),
      toIso:   toIsoDate(new Date(cells[41].getFullYear(), cells[41].getMonth(), cells[41].getDate() + 1)),
      gridCells: cells,
      weekDays: [] as Date[],
    };
  }, [year, month, day, view, now]);

  // Load events whenever the window or type filter changes.
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    const typesParam = activeTypes.length ? activeTypes.join(',') : undefined;
    calendarApi.list(fromIso, toIso, typesParam)
      .then(rows => {
        if (cancelled) return;
        // Apply holiday include/exclude on the client — server still
        // returns them so the workday helper has the holiday set.
        const filtered = includeHolidays
          ? rows
          : rows.filter(r => r.eventType !== 'PUBLIC_HOLIDAY');
        setEvents(filtered);
      })
      .catch(e => { if (!cancelled) setError((e as Error).message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [fromIso, toIso, activeTypes, includeHolidays, reload]);

  function setParam(key: string, value: string | undefined) {
    const next = new URLSearchParams(search);
    if (value == null || value === '') next.delete(key);
    else next.set(key, value);
    setSearch(next, { replace: true });
  }

  function goPrev() {
    // Week view shifts by 7 days; month/agenda shift by one month.
    if (view === 'week') {
      const anchor = startOfSaudiWeek(new Date(year, month, day));
      const prev = new Date(anchor.getFullYear(), anchor.getMonth(), anchor.getDate() - 7);
      setSearch({
        ...Object.fromEntries(search),
        y: String(prev.getFullYear()),
        m: String(prev.getMonth()),
        d: String(prev.getDate()),
      }, { replace: true });
      return;
    }
    const d2 = new Date(year, month - 1, 1);
    setSearch({
      ...Object.fromEntries(search),
      y: String(d2.getFullYear()),
      m: String(d2.getMonth()),
    }, { replace: true });
  }
  function goNext() {
    if (view === 'week') {
      const anchor = startOfSaudiWeek(new Date(year, month, day));
      const next = new Date(anchor.getFullYear(), anchor.getMonth(), anchor.getDate() + 7);
      setSearch({
        ...Object.fromEntries(search),
        y: String(next.getFullYear()),
        m: String(next.getMonth()),
        d: String(next.getDate()),
      }, { replace: true });
      return;
    }
    const d2 = new Date(year, month + 1, 1);
    setSearch({
      ...Object.fromEntries(search),
      y: String(d2.getFullYear()),
      m: String(d2.getMonth()),
    }, { replace: true });
  }
  function goToday() {
    setSearch({
      ...Object.fromEntries(search),
      y: String(now.getFullYear()),
      m: String(now.getMonth()),
      d: String(now.getDate()),
    }, { replace: true });
  }
  function cycleView() {
    const next: ViewMode = view === 'month' ? 'week'
                         : view === 'week'  ? 'agenda'
                         : 'month';
    setParam('v', next === 'month' ? undefined : next);
  }

  function toggleType(t: CalendarEventType) {
    const next = activeTypes.includes(t)
      ? activeTypes.filter(x => x !== t)
      : [...activeTypes, t];
    setParam('types', next.length ? next.join(',') : undefined);
  }

  function clickEvent(e: CalendarEventDto) {
    // Aggregated rows navigate to the source entity detail page.
    if (e.source === 'AGGREGATED' && e.entityType && e.entityId) {
      const route = entityRoute(e.entityType, e.entityId);
      if (route) { navigate(route); return; }
    }
    // User events — navigate to a future detail editor; for v1
    // just toast the metadata so the click is visible.
    toast.info(`${e.titleEn} — ${e.startsAt.slice(0, 10)}`);
  }

  function refreshNow() {
    setReload(reload + 1);
  }

  /**
   * Trigger an iCal (.ics) download for the current window + type
   * filter. We use a token-bearing fetch (the .ics endpoint sits behind
   * the same JWT as the JSON feed) and feed the response into a Blob +
   * synthetic <a download> so the file lands in the user's Downloads
   * folder with a stable name. Buyers point Outlook (Internet Calendars)
   * or Google Calendar (Add from URL) at the same endpoint for a live
   * subscription — this button is the one-off "give me a snapshot" path.
   */
  async function downloadIcs() {
    try {
      const typesParam = activeTypes.length ? `&types=${encodeURIComponent(activeTypes.join(','))}` : '';
      // Token key is `grc.accessToken` — set by lib/auth.ts and used by
      // every other authenticated fetch (api.ts, kb.ts, ndiReports.ts,
      // realtime.ts). The previous code looked
      // up the unprefixed `accessToken` key which is never written by
      // auth.ts, so every iCal download was sent unauthenticated and
      // bounced to the login page.
      const token = sessionStorage.getItem('grc.accessToken') ?? '';
      const url = `/api/v1/calendar/events.ics?from=${fromIso}&to=${toIso}${typesParam}`;
      // Resolve the API base the same way lib/api.ts does:
      //   - VITE_API_BASE env var if set (e.g. https://api.example.com)
      //   - window.__GRC_API__ runtime override (set in index.html by ops)
      //   - empty string = same-origin (Vite proxy in dev, nginx in prod)
      // The previous fallback was a hardcoded http://localhost:8084 which
      // would 404/CORS in prod whenever neither override was set.
      const apiBase = (import.meta.env.VITE_API_BASE as string | undefined)
                   ?? (window as { __GRC_API__?: string }).__GRC_API__
                   ?? '';
      const resp = await fetch(`${apiBase}${url}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!resp.ok) {
        toast.danger(isAr ? 'تعذر تصدير ملف iCal' : 'iCal export failed');
        return;
      }
      const blob = await resp.blob();
      const objUrl = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = objUrl;
      a.download = `tss-grc-calendar-${fromIso}-to-${toIso}.ics`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(objUrl);
      toast.success(isAr ? 'تم تنزيل ملف iCal' : 'iCal downloaded');
    } catch (e) {
      toast.danger((e as Error).message);
    }
  }

  const heading = view === 'month'
    ? monthYearLabel(year, month, isAr)
    : view === 'week'
      ? weekRangeLabel(new Date(year, month, day), isAr)
      : isAr ? 'الجدول القادم (٦٠ يوماً)' : 'Upcoming (60 days)';
  const switchLabel = view === 'month'
    ? (isAr ? 'عرض أسبوعي' : 'Week view')
    : view === 'week'
      ? t('calendarSwitchAgenda')
      : t('calendarSwitchMonth');

  return (
    <Shell title={t('calendarTitle')} sub={t('calendarSub')}>
      <PageHeader
        eyebrow={t('calendarEyebrow')}
        title={t('calendarTitle')}
        sub={t('calendarSub')}
        actions={<>
          <Button variant="ghost" Icon={RefreshCw} onClick={refreshNow}>
            {t('actionRefresh')}
          </Button>
          <Button variant="ghost" Icon={Download} onClick={downloadIcs}
            title={isAr ? 'تصدير ملف iCal (.ics) — يصلح للاشتراك من Outlook/Google'
                        : 'Export iCal (.ics) — subscribe from Outlook / Google'}>
            {isAr ? 'iCal' : 'iCal'}
          </Button>
          <Button variant="ghost" Icon={LayoutGrid} onClick={cycleView}>
            {switchLabel}
          </Button>
          <Button variant="primary" Icon={Plus} onClick={() => setAddOpen(true)}>
            {t('calendarNewEvent')}
          </Button>
        </>}
      />

      {/* Filter bar */}
      <Card title={isAr ? 'تصفية' : 'Filters'} Icon={Sparkles}
        action={activeTypes.length > 0 && (
          <Button variant="ghost" size="sm"
            onClick={() => setParam('types', undefined)}>
            {isAr ? 'مسح الكل' : 'Clear all'}
          </Button>
        )}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, padding: 4 }}>
          {(Object.keys(EVENT_TYPE_LABEL) as CalendarEventType[]).map(et => {
            const active = activeTypes.includes(et);
            const lbl = EVENT_TYPE_LABEL[et];
            return (
              <button key={et} onClick={() => toggleType(et)}
                style={{
                  padding: '4px 10px', fontSize: 12,
                  borderRadius: 'var(--r-sm)',
                  border: active ? '1px solid var(--primary)' : '1px solid var(--border)',
                  background: active ? 'var(--primary-50)' : 'var(--surface-2)',
                  color: active ? 'var(--primary)' : 'var(--ink-2)',
                  cursor: 'pointer',
                  fontWeight: active ? 600 : 500,
                }}>
                {isAr ? lbl.ar : lbl.en}
              </button>
            );
          })}
          <div style={{ marginInlineStart: 'auto', display: 'flex', gap: 8, alignItems: 'center' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--ink-2)' }}>
              <input type="checkbox" checked={includeHolidays}
                onChange={e => setParam('hol', e.target.checked ? undefined : '0')} />
              {isAr ? 'إظهار العطل' : 'Show holidays'}
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--ink-2)' }}>
              <input type="checkbox" checked={showHijri}
                onChange={e => setParam('hijri', e.target.checked ? undefined : '0')} />
              {isAr ? 'إظهار التاريخ الهجري' : 'Show Hijri date'}
            </label>
          </div>
        </div>
      </Card>

      {/* Navigation */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBlock: 12 }}>
        <Button variant="ghost" Icon={ChevronLeft} onClick={goPrev} aria-label="Previous">
          {isAr ? 'التالي' : 'Previous'}
        </Button>
        <Button variant="ghost" onClick={goToday}>{isAr ? 'اليوم' : 'Today'}</Button>
        <Button variant="ghost" Icon={ChevronRight} onClick={goNext} aria-label="Next">
          {isAr ? 'السابق' : 'Next'}
        </Button>
        <h2 style={{ margin: 0, fontSize: 16, fontWeight: 600, color: 'var(--ink-1)', marginInlineStart: 8 }}>
          {heading}
        </h2>
        {loading && <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{t('calendarLoading')}</span>}
      </div>

      {/* Body */}
      {error
        ? <Card title={isAr ? 'تعذّر التحميل' : 'Failed to load'} Icon={AlertTriangle}>
            <InlineEmpty>{error}</InlineEmpty>
          </Card>
        : view === 'month'
          ? <MonthGrid cells={gridCells} year={year} month={month}
                       events={events ?? []} showHijri={showHijri}
                       isAr={isAr} onEventClick={clickEvent} />
          : view === 'week'
            ? <WeekView days={weekDays} events={events ?? []}
                        showHijri={showHijri} isAr={isAr}
                        onEventClick={clickEvent} />
            : <AgendaList events={events ?? []} isAr={isAr}
                          showHijri={showHijri} onEventClick={clickEvent} />
      }

      {addOpen && <AddEventModal
        isAr={isAr}
        onClose={() => setAddOpen(false)}
        onCreated={(e) => {
          toast.success(t('calendarEventCreated'));
          setAddOpen(false);
          setReload(reload + 1);
          // Surfaces the new event in the current view if it lands
          // in the window — no need to navigate.
          void e;
        }} />}
    </Shell>
  );
}

// ─── Month grid ──────────────────────────────────────────────────────

function MonthGrid({ cells, year, month, events, showHijri, isAr, onEventClick }: {
  cells: Date[]; year: number; month: number;
  events: CalendarEventDto[]; showHijri: boolean; isAr: boolean;
  onEventClick: (e: CalendarEventDto) => void;
}) {
  const today = new Date();
  return (
    <Card title="" Icon={CalendarIcon}>
      {/* Day-of-week header */}
      <div style={{
        display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)',
        gap: 1, fontSize: 11, fontWeight: 700, color: 'var(--ink-3)',
        textTransform: 'uppercase', letterSpacing: '0.06em',
        paddingBlock: 8, borderBottom: '1px solid var(--border)',
      }}>
        {[0, 1, 2, 3, 4, 5, 6].map(d => (
          <div key={d} style={{
            padding: '4px 8px', textAlign: isAr ? 'right' : 'left',
            // Saudi weekend headers subtly muted.
            color: (d === 5 || d === 6) ? 'var(--ink-4)' : 'var(--ink-3)',
          }}>
            {dowShort(d, isAr)}
          </div>
        ))}
      </div>

      {/* 6×7 grid */}
      <div style={{
        display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)',
        gap: 1, background: 'var(--border)',
      }}>
        {cells.map(d => {
          const dayEvents = events.filter(e => isSameDay(new Date(e.startsAt), d));
          const inMonth = d.getMonth() === month && d.getFullYear() === year;
          const isToday = isSameDay(d, today);
          const weekend = isSaudiWeekend(d);
          const hasHoliday = dayEvents.some(e => e.eventType === 'PUBLIC_HOLIDAY');
          return (
            <div key={`${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`}
              style={{
                background: hasHoliday ? 'var(--accent-50)'
                          : weekend ? 'var(--surface-2)'
                          : 'var(--surface-1)',
                minHeight: 110,
                padding: 6,
                opacity: inMonth ? 1 : 0.5,
                borderInlineStart: isToday ? '3px solid var(--primary)' : undefined,
              }}>
              {/* Date number + Hijri */}
              <div style={{ display: 'flex', justifyContent: 'space-between',
                            alignItems: 'baseline', marginBottom: 4 }}>
                <span style={{
                  fontSize: 13, fontWeight: isToday ? 700 : 500,
                  color: isToday ? 'var(--primary)' : 'var(--ink-1)',
                }}>{d.getDate()}</span>
                {showHijri && (
                  <span style={{ fontSize: 10, color: 'var(--ink-4)' }}>
                    {hijriDayOnly(d)}
                  </span>
                )}
              </div>
              {/* Events */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                {dayEvents.slice(0, 3).map(e => (
                  <EventChip key={e.externalId} event={e} isAr={isAr}
                             onClick={() => onEventClick(e)} />
                ))}
                {dayEvents.length > 3 && (
                  <span style={{ fontSize: 10, color: 'var(--ink-3)', fontStyle: 'italic' }}>
                    {isAr ? `+${dayEvents.length - 3} المزيد` : `+${dayEvents.length - 3} more`}
                  </span>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </Card>
  );
}

// ─── Week view ────────────────────────────────────────────────────────

/**
 * Saudi week (Sun-Thu working, Fri-Sat tinted). Seven columns, each
 * column shows that day's events stacked. No hour grid in v1 — most
 * GRC events are all-day deadlines; the rare timed meeting gets its
 * time printed in the chip. Today's column is bordered. Holiday
 * columns get the same accent tint as the month grid uses.
 */
function WeekView({ days, events, showHijri, isAr, onEventClick }: {
  days: Date[]; events: CalendarEventDto[];
  showHijri: boolean; isAr: boolean;
  onEventClick: (e: CalendarEventDto) => void;
}) {
  const today = new Date();
  return (
    <Card title="" Icon={CalendarIcon}>
      <div style={{
        display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)',
        gap: 1, background: 'var(--border)',
      }}>
        {days.map(d => {
          const dayEvents = events.filter(e => isSameDay(new Date(e.startsAt), d));
          const isToday = isSameDay(d, today);
          const weekend = isSaudiWeekend(d);
          const hasHoliday = dayEvents.some(e => e.eventType === 'PUBLIC_HOLIDAY');
          return (
            <div key={`${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`}
              style={{
                background: hasHoliday ? 'var(--accent-50)'
                          : weekend ? 'var(--surface-2)'
                          : 'var(--surface-1)',
                minHeight: 320,
                padding: 8,
                borderInlineStart: isToday ? '3px solid var(--primary)' : undefined,
                display: 'flex', flexDirection: 'column',
              }}>
              {/* Header: day-of-week + date + Hijri */}
              <div style={{ display: 'flex', justifyContent: 'space-between',
                            alignItems: 'baseline', marginBottom: 8,
                            borderBottom: '1px solid var(--border)', paddingBottom: 6 }}>
                <div>
                  <div style={{
                    fontSize: 11, fontWeight: 700,
                    color: weekend ? 'var(--ink-4)' : 'var(--ink-3)',
                    textTransform: 'uppercase', letterSpacing: '0.06em',
                  }}>
                    {dowShort(d.getDay(), isAr)}
                  </div>
                  <div style={{
                    fontSize: 18, fontWeight: isToday ? 700 : 500,
                    color: isToday ? 'var(--primary)' : 'var(--ink-1)',
                  }}>
                    {d.getDate()}
                  </div>
                </div>
                {showHijri && (
                  <span style={{ fontSize: 10, color: 'var(--ink-4)' }}>
                    {hijriDayOnly(d)}
                  </span>
                )}
              </div>
              {/* Stacked events */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4, flex: 1 }}>
                {dayEvents.length === 0 && (
                  <span style={{ fontSize: 10, color: 'var(--ink-4)', fontStyle: 'italic' }}>
                    {isAr ? '—' : '—'}
                  </span>
                )}
                {dayEvents.map(e => (
                  <WeekChip key={e.externalId} event={e} isAr={isAr}
                            onClick={() => onEventClick(e)} />
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </Card>
  );
}

/**
 * Compact chip for the week view — taller than the month chip because
 * columns are wider, so we can show type + title + (optional) time on
 * a second line. Tone matches the month-view chip palette.
 */
function WeekChip({ event, isAr, onClick }: {
  event: CalendarEventDto; isAr: boolean; onClick: () => void;
}) {
  const tone = EVENT_TYPE_TONE[event.eventType as CalendarEventType] ?? 'neutral';
  const title = isAr && event.titleAr ? event.titleAr : event.titleEn;
  const typeLbl = EVENT_TYPE_LABEL[event.eventType as CalendarEventType];
  const toneFg = ({
    primary: 'var(--primary)',     info: 'var(--info-fg)',
    warning: 'var(--warning-fg)',  danger:  'var(--danger-fg)',
    success: 'var(--success-fg)',  accent:  'var(--accent-700)',
    neutral: 'var(--ink-2)',
  } as const)[tone];
  const timeLabel = event.allDay
    ? null
    : new Date(event.startsAt).toLocaleTimeString(isAr ? 'ar-SA' : 'en-US',
        { hour: '2-digit', minute: '2-digit' });
  return (
    <button onClick={onClick}
      title={title}
      style={{
        display: 'block', width: '100%',
        padding: '4px 6px', fontSize: 11,
        textAlign: isAr ? 'right' : 'left',
        background: 'var(--surface-1)',
        border: '1px solid var(--border)',
        borderInlineStart: `3px solid ${toneFg}`,
        borderRadius: 'var(--r-sm)',
        cursor: 'pointer',
        color: 'var(--ink-1)', fontFamily: 'inherit',
      }}>
      <div style={{
        fontSize: 9, fontWeight: 700, color: toneFg,
        textTransform: 'uppercase', letterSpacing: '0.05em',
        marginBottom: 1,
      }}>
        {typeLbl ? (isAr ? typeLbl.ar : typeLbl.en) : event.eventType}
        {timeLabel && <span style={{ color: 'var(--ink-4)', marginInlineStart: 4 }}>{timeLabel}</span>}
      </div>
      <div style={{
        fontSize: 11, fontWeight: 500,
        overflow: 'hidden', textOverflow: 'ellipsis',
        display: '-webkit-box', WebkitLineClamp: 2,
        WebkitBoxOrient: 'vertical',
      }}>
        {title}
      </div>
    </button>
  );
}

// ─── Agenda list ──────────────────────────────────────────────────────

function AgendaList({ events, isAr, showHijri, onEventClick }: {
  events: CalendarEventDto[]; isAr: boolean; showHijri: boolean;
  onEventClick: (e: CalendarEventDto) => void;
}) {
  if (events.length === 0) {
    return (
      <Card title={isAr ? 'لا توجد أحداث' : 'No events'} Icon={CalendarIcon}>
        <InlineEmpty>
          {isAr ? 'الجدول فارغ خلال الستين يوماً القادمة.'
                : 'Nothing scheduled in the next 60 days.'}
        </InlineEmpty>
      </Card>
    );
  }
  // Group events by ISO date.
  const byDate = new Map<string, CalendarEventDto[]>();
  for (const e of events) {
    const key = e.startsAt.slice(0, 10);
    if (!byDate.has(key)) byDate.set(key, []);
    byDate.get(key)!.push(e);
  }
  const sortedDays = Array.from(byDate.keys()).sort();
  return (
    <Card title={isAr ? 'الجدول القادم' : 'Upcoming events'} Icon={ListChecks}
      action={<Badge tone="primary">{events.length}</Badge>}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {sortedDays.map(iso => {
          const d = new Date(iso + 'T00:00:00');
          const dayEvents = byDate.get(iso)!;
          const weekend = isSaudiWeekend(d);
          return (
            <div key={iso} style={{
              display: 'grid', gridTemplateColumns: '120px 1fr',
              gap: 12, padding: '8px 0',
              borderBottom: '1px dashed var(--border)',
            }}>
              <div>
                <div style={{ fontSize: 14, fontWeight: 600,
                              color: weekend ? 'var(--ink-3)' : 'var(--ink-1)' }}>
                  {d.toLocaleDateString(isAr ? 'ar-SA' : 'en-US', {
                    weekday: 'short', month: 'short', day: 'numeric',
                  })}
                </div>
                {showHijri && (
                  <div style={{ fontSize: 11, color: 'var(--ink-4)' }}>
                    {hijriDate(d, isAr)}
                  </div>
                )}
                {weekend && (
                  <Badge tone="neutral">{isAr ? 'عطلة نهاية الأسبوع' : 'Weekend'}</Badge>
                )}
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {dayEvents.map(e => (
                  <AgendaRow key={e.externalId} event={e} isAr={isAr}
                             onClick={() => onEventClick(e)} />
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </Card>
  );
}

function AgendaRow({ event, isAr, onClick }: {
  event: CalendarEventDto; isAr: boolean; onClick: () => void;
}) {
  const tone = EVENT_TYPE_TONE[event.eventType as CalendarEventType] ?? 'neutral';
  const typeLbl = EVENT_TYPE_LABEL[event.eventType as CalendarEventType];
  const title = isAr && event.titleAr ? event.titleAr : event.titleEn;
  const onWeekend = event.onWorkday === false;
  return (
    <button onClick={onClick} style={{
      display: 'grid', gridTemplateColumns: '140px 1fr 100px',
      gap: 10, padding: '8px 10px', textAlign: isAr ? 'right' : 'left',
      background: 'var(--surface-2)', border: '1px solid var(--border)',
      borderRadius: 'var(--r-sm)', cursor: 'pointer',
      fontFamily: 'inherit',
    }}>
      <Badge tone={tone}>{typeLbl ? (isAr ? typeLbl.ar : typeLbl.en) : event.eventType}</Badge>
      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)',
                      overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {title}
        </div>
        {event.ownerLabel && (
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
            {isAr ? 'صاحب' : 'Owner'}: {event.ownerLabel}
          </div>
        )}
        {onWeekend && (
          <Badge tone="warning">
            {isAr ? 'يقع في عطلة نهاية الأسبوع' : 'Falls on weekend'}
          </Badge>
        )}
      </div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', textAlign: 'end' }}>
        {event.source === 'AGGREGATED'
          ? <Badge tone="info">{isAr ? 'مُجمَّع' : 'Auto'}</Badge>
          : <Badge tone="neutral">{isAr ? 'مستخدم' : 'User'}</Badge>}
      </div>
    </button>
  );
}

function EventChip({ event, isAr, onClick }: {
  event: CalendarEventDto; isAr: boolean; onClick: () => void;
}) {
  const tone = EVENT_TYPE_TONE[event.eventType as CalendarEventType] ?? 'neutral';
  const title = isAr && event.titleAr ? event.titleAr : event.titleEn;
  const titleTruncated = title.length > 28 ? title.slice(0, 28) + '…' : title;
  // Inline tone color map — keeps the chip dense (no Badge wrapper padding).
  const toneFg = ({
    primary: 'var(--primary)',     info: 'var(--info-fg)',
    warning: 'var(--warning-fg)',  danger:  'var(--danger-fg)',
    success: 'var(--success-fg)',  accent:  'var(--accent-700)',
    neutral: 'var(--ink-2)',
  } as const)[tone];
  return (
    <button onClick={onClick}
      title={title}
      style={{
        display: 'block', width: '100%',
        padding: '2px 6px', fontSize: 11,
        textAlign: isAr ? 'right' : 'left',
        background: 'var(--surface-1)',
        border: `1px solid var(--border)`,
        borderInlineStart: `3px solid ${toneFg}`,
        borderRadius: 'var(--r-sm)',
        cursor: 'pointer',
        color: 'var(--ink-1)', fontFamily: 'inherit',
        overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
      }}>
      {titleTruncated}
    </button>
  );
}

// ─── Add-event modal ──────────────────────────────────────────────────

type Recurrence = 'NONE' | 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'ANNUAL';

const RECURRENCE_LABEL: Record<Recurrence, { en: string; ar: string }> = {
  NONE:      { en: 'Does not repeat',         ar: 'لا يتكرر' },
  DAILY:     { en: 'Every day',               ar: 'يومياً' },
  WEEKLY:    { en: 'Every week',              ar: 'أسبوعياً' },
  MONTHLY:   { en: 'Every month',             ar: 'شهرياً' },
  QUARTERLY: { en: 'Every quarter (3 months)', ar: 'كل ربع (٣ أشهر)' },
  ANNUAL:    { en: 'Every year',              ar: 'سنوياً' },
};

/** Reminder-ladder presets buyers actually use. Number = days before
 *  the event fires. 0 = day-of; -1 = sentinel for "1 hour before" which
 *  we send as `0` and rely on time-of-day for the actual offset (server
 *  treats it as same-day notify). */
const REMINDER_PRESETS: { days: number; en: string; ar: string }[] = [
  { days: 0,  en: 'Day of',    ar: 'يوم الحدث' },
  { days: 1,  en: '1 day',     ar: 'يوم واحد' },
  { days: 3,  en: '3 days',    ar: '٣ أيام' },
  { days: 7,  en: '1 week',    ar: 'أسبوع' },
  { days: 14, en: '2 weeks',   ar: 'أسبوعان' },
  { days: 30, en: '1 month',   ar: 'شهر' },
];

function parseLadder(raw: string): number[] {
  return raw.split(',').map(s => s.trim()).filter(Boolean)
    .map(t => Number(t)).filter(n => Number.isFinite(n));
}
function ladderToString(days: number[]): string {
  return Array.from(new Set(days)).sort((a, b) => b - a).join(',');
}

function AddEventModal({ isAr, onClose, onCreated }: {
  isAr: boolean; onClose: () => void;
  onCreated: (e: CalendarEventDto) => void;
}) {
  const { t } = useI18n();
  const toast = useToast();
  const [titleEn, setTitleEn] = useState('');
  const [titleAr, setTitleAr] = useState('');
  const [eventType, setEventType] = useState<CalendarEventType>('MEETING');
  const [startsAt, setStartsAt] = useState(() => {
    const d = new Date(); d.setMinutes(0); d.setSeconds(0);
    return d.toISOString().slice(0, 16);
  });
  const [allDay, setAllDay] = useState(true);
  const [location, setLocation] = useState('');
  // Reminder ladder is now chip-driven (preset toggle) with a free-text
  // escape hatch for advanced users.
  const [ladder, setLadder] = useState<number[]>([7, 1]);
  const [advanced, setAdvanced] = useState(false);
  const [advancedText, setAdvancedText] = useState('7,1');
  // Recurrence drives the new RRULE editor.
  const [recurrence, setRecurrence] = useState<Recurrence>('NONE');
  const [recurrenceUntil, setRecurrenceUntil] = useState<string>('');
  const [saving, setSaving] = useState(false);

  function toggleLadder(days: number) {
    setLadder(prev => prev.includes(days)
      ? prev.filter(x => x !== days)
      : [...prev, days].sort((a, b) => b - a));
  }

  async function submit() {
    if (!titleEn.trim()) {
      toast.danger(isAr ? 'العنوان مطلوب' : 'Title is required');
      return;
    }
    const reminderDays = advanced
      ? ladderToString(parseLadder(advancedText))
      : ladderToString(ladder);
    setSaving(true);
    const req: CalendarEventCreateRequest = {
      titleEn: titleEn.trim(),
      titleAr: titleAr.trim() || undefined,
      eventType,
      startsAt: startsAt.length === 16 ? startsAt + ':00' : startsAt,
      allDay,
      location: location.trim() || undefined,
      reminderDays: reminderDays || undefined,
      recurrence: recurrence === 'NONE' ? undefined : recurrence,
      recurrenceUntil: recurrence !== 'NONE' && recurrenceUntil
        ? recurrenceUntil
        : undefined,
    };
    try {
      const out = await calendarApi.create(req);
      onCreated(out);
    } catch (e) {
      toast.danger((e as Error).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal open onClose={onClose} title={t('calendarNewEvent')} size="md"
      footer={<>
        <Button variant="ghost" onClick={onClose}>{t('actionCancel')}</Button>
        <Button variant="primary" Icon={Plus} onClick={submit} disabled={saving}>
          {saving ? (isAr ? 'يحفظ…' : 'Saving…') : t('actionSave')}
        </Button>
      </>}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <Field label={isAr ? 'العنوان (إنجليزي)' : 'Title (English)'}>
          <input value={titleEn} onChange={e => setTitleEn(e.target.value)}
            placeholder={isAr ? 'مثال: مراجعة ربعية لإطار النضج' : 'e.g. Quarterly maturity review'}
            style={inputStyle} />
        </Field>
        <Field label={isAr ? 'العنوان (عربي)' : 'Title (Arabic)'}>
          <input value={titleAr} onChange={e => setTitleAr(e.target.value)}
            placeholder={isAr ? 'مثال: مراجعة الإطار' : 'مثال: مراجعة الإطار'}
            style={inputStyle} dir="rtl" />
        </Field>
        <Field label={isAr ? 'النوع' : 'Type'}>
          <select value={eventType}
            onChange={e => setEventType(e.target.value as CalendarEventType)}
            style={inputStyle}>
            {(['MEETING', 'REMINDER', 'TRAINING', 'AUDIT_DAY',
               'REPORT_DEADLINE', 'CUSTOM'] as CalendarEventType[]).map(et =>
              <option key={et} value={et}>
                {(isAr ? EVENT_TYPE_LABEL[et].ar : EVENT_TYPE_LABEL[et].en)}
              </option>)}
          </select>
        </Field>
        <Field label={isAr ? 'التاريخ والوقت' : 'Date and time'}>
          <input type="datetime-local" value={startsAt}
            onChange={e => setStartsAt(e.target.value)} style={inputStyle} />
        </Field>
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12 }}>
          <input type="checkbox" checked={allDay}
            onChange={e => setAllDay(e.target.checked)} />
          {isAr ? 'يوم كامل' : 'All day'}
        </label>
        <Field label={isAr ? 'الموقع' : 'Location'}>
          <input value={location} onChange={e => setLocation(e.target.value)}
            placeholder={isAr ? 'اختياري' : 'Optional'} style={inputStyle} />
        </Field>

        {/* Recurrence */}
        <Field label={isAr ? 'التكرار' : 'Repeat'}>
          <select value={recurrence}
            onChange={e => setRecurrence(e.target.value as Recurrence)}
            style={inputStyle}>
            {(Object.keys(RECURRENCE_LABEL) as Recurrence[]).map(r =>
              <option key={r} value={r}>
                {isAr ? RECURRENCE_LABEL[r].ar : RECURRENCE_LABEL[r].en}
              </option>)}
          </select>
        </Field>
        {recurrence !== 'NONE' && (
          <Field label={isAr ? 'ينتهي في (اختياري)' : 'Ends on (optional)'}>
            <input type="date" value={recurrenceUntil}
              onChange={e => setRecurrenceUntil(e.target.value)}
              style={inputStyle} />
          </Field>
        )}

        {/* Reminder ladder */}
        <Field label={isAr ? 'تذكيرات' : 'Reminders'}>
          {!advanced ? (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {REMINDER_PRESETS.map(p => {
                const active = ladder.includes(p.days);
                return (
                  <button type="button" key={p.days} onClick={() => toggleLadder(p.days)}
                    style={{
                      padding: '4px 10px', fontSize: 12,
                      borderRadius: 'var(--r-sm)',
                      border: active ? '1px solid var(--primary)' : '1px solid var(--border)',
                      background: active ? 'var(--primary-50)' : 'var(--surface-2)',
                      color:      active ? 'var(--primary)' : 'var(--ink-2)',
                      cursor: 'pointer',
                      fontWeight: active ? 600 : 500,
                      fontFamily: 'inherit',
                    }}>
                    {isAr ? p.ar : p.en}
                  </button>
                );
              })}
              <button type="button"
                onClick={() => { setAdvanced(true); setAdvancedText(ladderToString(ladder)); }}
                style={{
                  padding: '4px 10px', fontSize: 12,
                  borderRadius: 'var(--r-sm)',
                  border: '1px dashed var(--border)',
                  background: 'transparent', color: 'var(--ink-3)',
                  cursor: 'pointer', fontFamily: 'inherit',
                }}>
                {isAr ? 'مخصص…' : 'Custom…'}
              </button>
            </div>
          ) : (
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <input value={advancedText}
                onChange={e => setAdvancedText(e.target.value)}
                placeholder="7,3,1,0" style={inputStyle} />
              <button type="button"
                onClick={() => { setAdvanced(false); setLadder(parseLadder(advancedText)); }}
                style={{
                  padding: '4px 10px', fontSize: 12,
                  borderRadius: 'var(--r-sm)',
                  border: '1px solid var(--border)',
                  background: 'var(--surface-2)', color: 'var(--ink-2)',
                  cursor: 'pointer', fontFamily: 'inherit', whiteSpace: 'nowrap',
                }}>
                {isAr ? 'رجوع' : 'Presets'}
              </button>
            </div>
          )}
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>
            {isAr
              ? 'أيام قبل تاريخ الحدث. مثال: ٧ يعني تنبيه قبل ٧ أيام.'
              : 'Days before the event. e.g. 7 = notify 7 days ahead.'}
          </div>
        </Field>
      </div>
    </Modal>
  );
}

const inputStyle: React.CSSProperties = {
  padding: '6px 8px', fontSize: 13, width: '100%',
  border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
  background: 'var(--surface-2)', color: 'var(--ink-1)',
  fontFamily: 'inherit',
};

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)',
                    textTransform: 'uppercase', letterSpacing: '0.06em',
                    marginBottom: 4 }}>
        {label}
      </div>
      {children}
    </div>
  );
}

// ─── Helpers ──────────────────────────────────────────────────────────

/** Map an entityType to its v2 detail-page route. Returns undefined
 *  when no route is wired yet (the calendar still lists the row;
 *  click-through just no-ops with a toast). */
function entityRoute(entityType: string, entityId: number): string | undefined {
  switch (entityType) {
    case 'CapaTask':           return `/capa/${entityId}`;
    case 'Finding':
    case 'AuditFinding':       return `/findings/${entityId}`;
    case 'Risk':               return `/risk/${entityId}`;
    case 'CrisisAar':          return `/bcm/aars/${entityId}`;
    case 'WorkflowSubmission': return `/workflows/submissions/${entityId}`;
    // NDI assessment cycles surface as REPORT_DEADLINE rows. Open
    // the Cycles tab — the page handles cycle selection client-side
    // via the cycle dropdown on each tab, so passing the id is
    // best-effort and ignored if no `?id` listener is wired yet.
    case 'NdiAssessmentCycle': return `/ndi?tab=cycles&id=${entityId}`;
    default: return undefined;
  }
}

// Suppress unused warnings on Link + X — kept available for future
// affordances (entity link, dismiss icon in agenda rows).
const _unused: ReadonlyArray<unknown> = [Link, X];
void _unused;
