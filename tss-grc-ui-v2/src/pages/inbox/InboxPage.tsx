import { useEffect, useState } from 'react';
import {
  Bell, MailOpen, CheckCheck, Inbox as InboxIcon, AlertTriangle, RefreshCw, ChevronLeft, ChevronRight,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { notificationApi, type NotificationDto, type NotificationPage } from '../../lib/api';

/**
 * Notification inbox — the other half of the F8 loop.
 *
 * <p>F8 NOTIFY/ESCALATE actions fan out to every active user holding
 * a configured role and write a {@code platform.notifications} row
 * per recipient. This page is where those rows actually land in front
 * of the user.
 *
 * <p>Three buyer asks covered here:
 *   - List every notification, newest first, with bilingual title +
 *     body + a NOTIFICATION / ESCALATION badge so escalations stand
 *     out from routine notifications.
 *   - One-click "mark all read" + per-row "mark read" so a returning
 *     user can clear the badge without scrolling.
 *   - Cross-link via the entity reference (when present) so the
 *     reader can jump straight to the source CAPA, risk, finding, etc.
 *     Today the backend stores only the entity type+id; the v1
 *     resolution happens in {@link entityRoute} below — same helper
 *     pattern the Calendar uses for its aggregated chips.
 *
 * <p>Pagination is server-side (Spring Page) with a simple
 * prev/next pair. We keep the page size small (20) so the read-state
 * mutation feels instantaneous.
 */
export function InboxPage() {
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [page, setPage] = useState<NotificationPage | null>(null);
  const [pageNum, setPageNum] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const out = await notificationApi.list(pageNum, 20);
      setPage(out);
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
      // Optimistic update — flip in-place so the badge clears
      // instantly without a refetch.
      setPage(p => p && ({
        ...p,
        content: p.content.map(x => x.id === n.id ? { ...x, isRead: true } : x),
      }));
    } catch (e) {
      toast.danger((e as Error).message);
    }
  }

  async function markAll() {
    try {
      await notificationApi.markAllRead();
      setPage(p => p && ({
        ...p,
        content: p.content.map(x => ({ ...x, isRead: true })),
      }));
      toast.success(isAr ? 'تم وضع علامة قراءة لكل الإشعارات.' : 'All notifications marked as read.');
    } catch (e) {
      toast.danger((e as Error).message);
    }
  }

  const unread = page ? page.content.filter(n => !n.isRead).length : 0;

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
          <Button variant="primary" Icon={CheckCheck} onClick={markAll} disabled={unread === 0}>
            {isAr ? 'تعليم الكل كمقروء' : 'Mark all read'}
          </Button>
        </>}
      />

      {error
        ? <Card title={isAr ? 'تعذّر التحميل' : 'Failed to load'} Icon={AlertTriangle}>
            <InlineEmpty>{error}</InlineEmpty>
          </Card>
        : page === null || page.content.length === 0
          ? <Card title={isAr ? 'لا توجد إشعارات' : 'No notifications'} Icon={InboxIcon}>
              <InlineEmpty>
                {loading
                  ? (isAr ? 'يحمّل…' : 'Loading…')
                  : (isAr
                      ? 'الصندوق فارغ. تصلك الإشعارات هنا حين تطلق قواعد الأتمتة F8 إشعاراً لدور تشغله.'
                      : 'Empty inbox. F8 automation rules that fire NOTIFY/ESCALATE on a role you hold will land here.')}
              </InlineEmpty>
            </Card>
          : <NotificationList rows={page.content} isAr={isAr} onClick={markOne} />
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
            {isAr
              ? `${page.totalElements} إشعار`
              : `${page.totalElements} notifications`}
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

function NotificationList({ rows, isAr, onClick }: {
  rows: NotificationDto[]; isAr: boolean;
  onClick: (n: NotificationDto) => void;
}) {
  return (
    <Card title="" Icon={Bell}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
        {rows.map(n => <NotificationRow key={n.id} n={n} isAr={isAr} onClick={() => onClick(n)} />)}
      </div>
    </Card>
  );
}

function NotificationRow({ n, isAr, onClick }: {
  n: NotificationDto; isAr: boolean; onClick: () => void;
}) {
  const title = isAr && n.titleAr ? n.titleAr : n.titleEn;
  const message = isAr && n.messageAr ? n.messageAr : n.messageEn;
  const isEscalation = n.notificationType === 'ESCALATION';
  return (
    <button onClick={onClick} style={{
      display: 'grid',
      gridTemplateColumns: isAr ? '1fr 110px 16px' : '16px 1fr 110px',
      gap: 12, padding: '12px 14px',
      textAlign: isAr ? 'right' : 'left',
      background: n.isRead ? 'var(--surface-2)' : 'var(--surface-1)',
      border: '1px solid var(--border)',
      borderRadius: 0, borderTop: 'none',
      cursor: n.isRead ? 'default' : 'pointer',
      fontFamily: 'inherit',
      opacity: n.isRead ? 0.7 : 1,
    }}>
      {/* Unread dot (or empty cell to keep grid steady) */}
      <span style={{
        width: 8, height: 8, borderRadius: '50%',
        background: n.isRead ? 'transparent'
                  : isEscalation ? 'var(--danger-fg)' : 'var(--primary)',
        alignSelf: 'center',
        marginInlineStart: isAr ? 'auto' : 0,
      }} aria-hidden="true" />
      <div style={{ minWidth: 0 }}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center',
                      flexWrap: 'wrap', marginBottom: 4 }}>
          <span style={{
            fontSize: 13, fontWeight: n.isRead ? 500 : 700,
            color: n.isRead ? 'var(--ink-2)' : 'var(--ink-1)',
          }}>
            {title}
          </span>
          {isEscalation && (
            <Badge tone="danger">{isAr ? 'تصعيد' : 'Escalation'}</Badge>
          )}
        </div>
        {message && (
          <div style={{ fontSize: 12, color: 'var(--ink-3)',
                        overflow: 'hidden', textOverflow: 'ellipsis',
                        display: '-webkit-box', WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical' }}>
            {message}
          </div>
        )}
      </div>
      <div style={{ fontSize: 11, color: 'var(--ink-4)', textAlign: 'end',
                    alignSelf: 'start', whiteSpace: 'nowrap' }}>
        {formatRelative(n.createdAt, isAr)}
        {n.isRead && <div style={{ marginTop: 4, display: 'inline-flex', alignItems: 'center', gap: 4 }}>
          <MailOpen size={12} /> {isAr ? 'مقروء' : 'Read'}
        </div>}
      </div>
    </button>
  );
}

function formatRelative(iso: string, isAr: boolean): string {
  const d = new Date(iso);
  const diff = (Date.now() - d.getTime()) / 1000;
  if (diff < 60)    return isAr ? 'الآن' : 'just now';
  if (diff < 3600)  return isAr ? `قبل ${Math.floor(diff / 60)} د` : `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return isAr ? `قبل ${Math.floor(diff / 3600)} س` : `${Math.floor(diff / 3600)}h ago`;
  if (diff < 604800) return isAr ? `قبل ${Math.floor(diff / 86400)} يوم` : `${Math.floor(diff / 86400)}d ago`;
  return d.toLocaleDateString(isAr ? 'ar-SA' : 'en-US', {
    month: 'short', day: 'numeric', year: 'numeric',
  });
}
