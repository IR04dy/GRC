import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Bell, MailOpen, CheckCheck, Inbox as InboxIcon, AlertTriangle, RefreshCw,
  ChevronLeft, ChevronRight, X as XIcon,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { DirectionalArrow } from '../../components/rtl/DirectionalArrow';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { notificationApi, type NotificationDto, type NotificationPage } from '../../lib/api';

/**
 * Notification inbox — the other half of the F8 loop.
 *
 * <p>F8 NOTIFY/ESCALATE actions fan out to every active user holding a
 * configured role and write a {@code platform.notifications} row per
 * recipient. This page is where those rows land in front of the user.
 *
 * <p>Redesign (market-leader inbox idiom — ServiceNow / Linear / Gmail):
 *   - An interactive KPI summary strip (Total / Unread / Escalations) that
 *     doubles as a filter control — clicking a tile filters the list and the
 *     active tile shows a focus ring. Tiles are real drill handles, matching
 *     the rest of the platform where every stat card is clickable.
 *   - Rows grouped by recency bucket (Today / Yesterday / Earlier) with a
 *     type-icon chip, an unread accent bar, a 2-line message clamp and a
 *     relative timestamp.
 *   - Rows are now genuinely actionable: clicking marks-read AND, when the
 *     notification carries a {@code link} to its source entity (CAPA, risk,
 *     finding…), navigates straight there. Previously the link was ignored.
 *   - One-click "mark all read" + per-row mark-read keep the badge clearable.
 *
 * <p>The list endpoint is server-paginated (Spring Page); the Unread headline
 * uses the dedicated {@code unread-count} endpoint so it is accurate across
 * pages, while the filter pills operate on the loaded page (size 50, so the
 * page is representative for routine inbox volumes).
 */

type InboxFilter = 'all' | 'unread' | 'escalation';
const PAGE_SIZE = 50;

export function InboxPage() {
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [page, setPage] = useState<NotificationPage | null>(null);
  const [pageNum, setPageNum] = useState(0);
  const [unreadTotal, setUnreadTotal] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<InboxFilter>('all');

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const [out, counts] = await Promise.all([
        notificationApi.list(pageNum, PAGE_SIZE),
        notificationApi.unreadCount().catch(() => null),
      ]);
      setPage(out);
      if (counts) setUnreadTotal(counts.count);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [pageNum]);

  async function markOne(n: NotificationDto) {
    if (n.isRead) return;
    try {
      await notificationApi.markRead(n.id);
      // Optimistic — flip in-place so the badge clears without a refetch.
      setPage(p => p && ({ ...p, content: p.content.map(x => x.id === n.id ? { ...x, isRead: true } : x) }));
      setUnreadTotal(c => (c == null ? c : Math.max(0, c - 1)));
    } catch (e) {
      toast.danger((e as Error).message);
    }
  }

  async function markAll() {
    try {
      await notificationApi.markAllRead();
      setPage(p => p && ({ ...p, content: p.content.map(x => ({ ...x, isRead: true })) }));
      setUnreadTotal(0);
      toast.success(isAr ? 'تم وضع علامة قراءة لكل الإشعارات.' : 'All notifications marked as read.');
    } catch (e) {
      toast.danger((e as Error).message);
    }
  }

  // Clicking a row marks it read and, when it carries a source link, jumps there.
  function openRow(n: NotificationDto) {
    void markOne(n);
    if (n.link && n.link.startsWith('/')) navigate(n.link);
  }

  const rows = page?.content ?? [];
  const pageUnread = rows.filter(n => !n.isRead).length;
  const pageEscalations = rows.filter(n => n.notificationType === 'ESCALATION').length;
  const headlineUnread = unreadTotal ?? (page ? pageUnread : null);

  const filtered = useMemo(() => rows.filter(n =>
    filter === 'unread' ? !n.isRead
    : filter === 'escalation' ? n.notificationType === 'ESCALATION'
    : true,
  ), [rows, filter]);

  const grouped = useMemo(() => groupByBucket(filtered, isAr), [filtered, isAr]);

  return (
    <Shell title={t('inboxNotifTitle')} sub={t('inboxNotifSub')}>
      <PageHeader
        eyebrow={t('inboxNotifEyebrow')}
        title={t('inboxNotifTitle')}
        sub={t('inboxNotifSub')}
        actions={<>
          <Button variant="ghost" Icon={RefreshCw} onClick={load}>
            {t('actionRefresh')}
          </Button>
          <Button variant="primary" Icon={CheckCheck} onClick={markAll} disabled={(headlineUnread ?? 0) === 0}>
            {isAr ? 'تعليم الكل كمقروء' : 'Mark all read'}
          </Button>
        </>}
      />

      {/* Interactive summary strip — the platform-standard `kpi-grid` + `Kpi`
          tiles (same component/markup as every other module page). Each tile is
          a clickable drill that filters the list; click the active one to clear. */}
      <div className="kpi-grid">
        <Kpi Icon={InboxIcon} tone="primary"
          label={isAr ? 'الإجمالي' : 'Total'} value={page?.totalElements ?? null}
          emptyHint={isAr ? 'لم يُحمّل بعد' : 'Not loaded yet'}
          onClick={() => setFilter('all')} />
        <Kpi Icon={Bell} tone="warning"
          label={isAr ? 'غير المقروءة' : 'Unread'} value={headlineUnread} zeroConfirm
          emptyHint={isAr ? 'لم يُحتسب بعد' : 'Not computed yet'}
          onClick={() => setFilter(filter === 'unread' ? 'all' : 'unread')} />
        <Kpi Icon={AlertTriangle} tone="danger"
          label={isAr ? 'التصعيدات' : 'Escalations'} value={page ? pageEscalations : null} zeroConfirm
          emptyHint={isAr ? 'لا تصعيدات' : 'No escalations'}
          onClick={() => setFilter(filter === 'escalation' ? 'all' : 'escalation')} />
      </div>

      {filter !== 'all' && (
        <div style={{ marginBottom: 10 }}>
          <button type="button" onClick={() => setFilter('all')}
            style={{
              display: 'inline-flex', alignItems: 'center', gap: 6,
              background: 'var(--surface-2)', border: '1px solid var(--border)',
              borderRadius: 'var(--r-pill, 999px)', cursor: 'pointer',
              color: 'var(--ink-2)', fontSize: 12, fontWeight: 600, padding: '4px 12px',
              fontFamily: 'inherit',
            }}>
            <XIcon size={12} />
            {isAr ? 'إزالة التصفية' : 'Clear filter'}
            <span style={{ color: 'var(--ink-4)' }}>
              ({filter === 'unread' ? (isAr ? 'غير المقروءة' : 'Unread') : (isAr ? 'التصعيدات' : 'Escalations')})
            </span>
          </button>
        </div>
      )}

      {error
        ? <Card title={isAr ? 'تعذّر التحميل' : 'Failed to load'} Icon={AlertTriangle}>
            <InlineEmpty>{error}</InlineEmpty>
          </Card>
        : !page || rows.length === 0
          ? <Card title={isAr ? 'لا توجد إشعارات' : 'No notifications'} Icon={InboxIcon}>
              <InlineEmpty>
                {loading
                  ? (isAr ? 'يحمّل…' : 'Loading…')
                  : (isAr
                      ? 'الصندوق فارغ. تصلك الإشعارات هنا حين تطلق قواعد الأتمتة F8 إشعاراً لدور تشغله.'
                      : 'Empty inbox. F8 automation rules that fire NOTIFY/ESCALATE on a role you hold will land here.')}
              </InlineEmpty>
            </Card>
          : filtered.length === 0
            ? <Card title="" Icon={Bell}>
                <InlineEmpty>
                  {isAr ? 'لا عناصر مطابقة للتصفية في هذه الصفحة.' : 'No items match this filter on the current page.'}
                </InlineEmpty>
              </Card>
            : <Card padded={false}>
                {grouped.map((g, gi) => (
                  <div key={g.bucket}>
                    <div style={{
                      fontSize: 11, fontWeight: 700, color: 'var(--ink-3)',
                      textTransform: 'uppercase', letterSpacing: '.06em',
                      padding: '12px 14px 6px',
                      background: 'var(--surface-2)',
                      borderTop: gi === 0 ? 'none' : '1px solid var(--border)',
                    }}>{g.bucket}</div>
                    {g.items.map((n, i) => (
                      <NotificationRow key={n.id} n={n} isAr={isAr}
                        last={gi === grouped.length - 1 && i === g.items.length - 1}
                        onOpen={() => openRow(n)} />
                    ))}
                  </div>
                ))}
              </Card>
      }

      {page && page.totalPages > 1 && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 10,
                      marginTop: 12, fontSize: 12, color: 'var(--ink-3)' }}>
          <Button variant="ghost" size="sm" Icon={ChevronLeft}
            onClick={() => setPageNum(Math.max(0, pageNum - 1))}
            disabled={pageNum === 0}>
            {isAr ? 'السابق' : 'Previous'}
          </Button>
          <span>
            {isAr
              ? `صفحة ${page.number + 1} من ${page.totalPages}`
              : `Page ${page.number + 1} of ${page.totalPages}`}
            {' · '}
            {isAr ? `${page.totalElements} إشعار` : `${page.totalElements} notifications`}
          </span>
          <Button variant="ghost" size="sm" Icon={ChevronRight}
            onClick={() => setPageNum(Math.min(page.totalPages - 1, pageNum + 1))}
            disabled={pageNum >= page.totalPages - 1}>
            {isAr ? 'التالي' : 'Next'}
          </Button>
        </div>
      )}
    </Shell>
  );
}

function NotificationRow({ n, isAr, last, onOpen }: {
  n: NotificationDto; isAr: boolean; last: boolean; onOpen: () => void;
}) {
  const title = isAr && n.titleAr ? n.titleAr : n.titleEn;
  const message = isAr && n.messageAr ? n.messageAr : n.messageEn;
  const isEscalation = n.notificationType === 'ESCALATION';
  const hasLink = !!(n.link && n.link.startsWith('/'));

  return (
    <button onClick={onOpen}
      onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.background = 'var(--surface-2)'; }}
      onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.background = n.isRead ? 'transparent' : 'var(--primary-50, var(--surface-2))'; }}
      style={{
        position: 'relative',
        width: '100%',
        display: 'grid',
        gridTemplateColumns: '34px 1fr auto',
        gap: 12, padding: '12px 14px',
        textAlign: 'start',
        background: n.isRead ? 'transparent' : 'var(--primary-50, var(--surface-2))',
        border: 'none',
        borderBottom: last ? 'none' : '1px solid var(--border)',
        cursor: 'pointer',
        fontFamily: 'inherit',
        transition: 'background var(--dur-fast) var(--ease-standard)',
      }}>
      {/* Unread accent bar on the inline-start edge */}
      {!n.isRead && (
        <span aria-hidden style={{
          position: 'absolute', insetInlineStart: 0, top: 0, bottom: 0, width: 3,
          background: isEscalation ? 'var(--danger)' : 'var(--primary)',
        }} />
      )}

      {/* Type-icon chip */}
      <span aria-hidden style={{
        width: 32, height: 32, borderRadius: 'var(--r-sm)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        background: isEscalation ? 'var(--danger-bg)' : 'var(--primary-50)',
        color: isEscalation ? 'var(--danger-fg)' : 'var(--primary-700)',
        alignSelf: 'start',
      }}>
        {isEscalation ? <AlertTriangle size={16} /> : <Bell size={16} />}
      </span>

      {/* Content */}
      <div style={{ minWidth: 0 }}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginBottom: 4 }}>
          <span style={{
            fontSize: 13, fontWeight: n.isRead ? 500 : 700,
            color: n.isRead ? 'var(--ink-2)' : 'var(--ink-1)',
          }}>{title}</span>
          {isEscalation && <Badge tone="danger">{isAr ? 'تصعيد' : 'Escalation'}</Badge>}
          {!n.isRead && <Badge tone="primary">{isAr ? 'جديد' : 'New'}</Badge>}
        </div>
        {message && (
          <div style={{
            fontSize: 12, color: 'var(--ink-3)',
            overflow: 'hidden', textOverflow: 'ellipsis',
            display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical',
          }}>{message}</div>
        )}
        {hasLink && (
          <div style={{
            marginTop: 6, display: 'inline-flex', alignItems: 'center', gap: 4,
            fontSize: 11, fontWeight: 600, color: 'var(--primary)',
          }}>
            {isAr ? 'الانتقال إلى المصدر' : 'Open source'}
            <DirectionalArrow size={12} />
          </div>
        )}
      </div>

      {/* Meta column */}
      <div style={{ fontSize: 11, color: 'var(--ink-4)', textAlign: 'end',
                    alignSelf: 'start', whiteSpace: 'nowrap' }}>
        {formatRelative(n.createdAt, isAr)}
        {n.isRead && (
          <div style={{ marginTop: 4, display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <MailOpen size={12} /> {isAr ? 'مقروء' : 'Read'}
          </div>
        )}
      </div>
    </button>
  );
}

/** Group notifications (already newest-first) into recency buckets, order preserved. */
function groupByBucket(rows: NotificationDto[], isAr: boolean): { bucket: string; items: NotificationDto[] }[] {
  const out: { bucket: string; items: NotificationDto[] }[] = [];
  const byBucket = new Map<string, NotificationDto[]>();
  for (const n of rows) {
    const b = dateBucket(n.createdAt, isAr);
    if (!byBucket.has(b)) { byBucket.set(b, []); out.push({ bucket: b, items: byBucket.get(b)! }); }
    byBucket.get(b)!.push(n);
  }
  return out;
}

function dateBucket(iso: string, isAr: boolean): string {
  const d = new Date(iso);
  const now = new Date();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const dayMs = 86400000;
  if (d.getTime() >= startOfToday) return isAr ? 'اليوم' : 'Today';
  if (d.getTime() >= startOfToday - dayMs) return isAr ? 'الأمس' : 'Yesterday';
  if (d.getTime() >= startOfToday - 7 * dayMs) return isAr ? 'هذا الأسبوع' : 'Earlier this week';
  return isAr ? 'أقدم' : 'Older';
}

function formatRelative(iso: string, isAr: boolean): string {
  const d = new Date(iso);
  const diff = (Date.now() - d.getTime()) / 1000;
  if (diff < 60)    return isAr ? 'الآن' : 'just now';
  if (diff < 3600)  return isAr ? `قبل ${Math.floor(diff / 60)} د` : `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return isAr ? `قبل ${Math.floor(diff / 3600)} س` : `${Math.floor(diff / 3600)}h ago`;
  if (diff < 604800) return isAr ? `قبل ${Math.floor(diff / 86400)} يوم` : `${Math.floor(diff / 86400)}d ago`;
  return d.toLocaleDateString(isAr ? 'ar-SA' : 'en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}
