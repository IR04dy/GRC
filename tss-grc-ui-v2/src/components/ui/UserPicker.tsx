import { useEffect, useRef, useState } from 'react';
import { Search, X, Phone } from 'lucide-react';
import { searchUsers, getUser, recentUsers, type User } from '../../lib/userDirectory';
import { useI18n } from '../../lib/i18n';

/**
 * UserPicker — type-ahead user picker with avatar + role + recent.
 * Modeled after ServiceNow `sys_user` field UI + Linear's assignee picker.
 *
 * Usage:
 *   <UserPicker value={ownerId} onChange={setOwnerId} />
 *
 * The value is the user id (string). For backwards compatibility with the
 * existing seeded-string-shaped data, you can also pass a name string —
 * the picker resolves it via lookupUserByName.
 */
export function UserPicker({ value, onChange, placeholder, allowClear = true, size = 'md' }: {
  value: string | undefined;
  onChange: (userId: string | undefined) => void;
  placeholder?: string;
  allowClear?: boolean;
  size?: 'sm' | 'md';
}) {
  const { t, lang } = useI18n();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const ref = useRef<HTMLDivElement>(null);

  const selected = value ? getUser(value) : undefined;

  // Close on outside click.
  useEffect(() => {
    function onDocClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    if (open) document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [open]);

  const matches = query.trim() ? searchUsers(query, 8) : recentUsers(6);

  function pick(u: User) {
    onChange(u.id);
    setOpen(false);
    setQuery('');
  }

  const padding = size === 'sm' ? '4px 8px' : '6px 10px';
  const fontSize = size === 'sm' ? 11 : 12;
  const avatarSize = size === 'sm' ? 18 : 22;

  return (
    <div ref={ref} style={{ position: 'relative', display: 'inline-block', minWidth: 180 }}>
      {selected ? (
        <button onClick={() => setOpen(o => !o)}
          style={{
            display: 'inline-flex', alignItems: 'center', gap: 6,
            padding, fontSize,
            border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
            background: 'var(--surface-1)', cursor: 'pointer',
            width: '100%', justifyContent: 'flex-start',
          }}>
          <Avatar user={selected} size={avatarSize} />
          <span style={{ fontWeight: 600, flex: 1, textAlign: 'start', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {lang === 'ar' ? selected.nameAr : selected.nameEn}
          </span>
          {allowClear && (
            <span onClick={(e) => { e.stopPropagation(); onChange(undefined); }}
              style={{ cursor: 'pointer', color: 'var(--ink-3)', padding: 2 }}>
              <X size={12} />
            </span>
          )}
        </button>
      ) : (
        <button onClick={() => setOpen(o => !o)}
          style={{
            display: 'inline-flex', alignItems: 'center', gap: 6,
            padding, fontSize,
            border: '1px dashed var(--border)', borderRadius: 'var(--r-sm)',
            background: 'transparent', cursor: 'pointer', color: 'var(--ink-3)',
            width: '100%', justifyContent: 'flex-start',
          }}>
          <Search size={12} />
          <span>{placeholder ?? t('upPickAssignee')}</span>
        </button>
      )}

      {open && (
        <div style={{
          position: 'absolute', insetInlineStart: 0, top: '100%', marginTop: 4,
          minWidth: 280, zIndex: 100,
          background: 'var(--surface-1)', borderRadius: 'var(--r-md)',
          border: '1px solid var(--border)',
          boxShadow: '0 8px 24px rgba(0,0,0,0.12)',
          overflow: 'hidden',
        }}>
          {/* Search input */}
          <div style={{ padding: 8, borderBottom: '1px solid var(--border)', position: 'relative' }}>
            <Search size={12} style={{ position: 'absolute', insetInlineStart: 16, top: 14, color: 'var(--ink-3)' }} />
            <input value={query} onChange={e => setQuery(e.target.value)}
              autoFocus
              placeholder={t('upSearchPlaceholder')}
              style={{
                width: '100%', padding: '6px 10px 6px 28px', fontSize: 11,
                border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                background: 'var(--surface-2)',
              }} />
          </div>

          {/* Results */}
          <div style={{ maxHeight: 280, overflow: 'auto' }}>
            {!query.trim() && (
              <div style={{ padding: '6px 10px', fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                {t('upRecent')}
              </div>
            )}
            {matches.length === 0 ? (
              <div style={{ padding: 12, textAlign: 'center', fontSize: 11, color: 'var(--ink-3)' }}>
                {t('upNoMatches')}
              </div>
            ) : matches.map(u => (
              <button key={u.id} onClick={() => pick(u)}
                style={{
                  width: '100%', padding: '8px 10px',
                  display: 'flex', alignItems: 'center', gap: 8,
                  border: 'none', background: 'transparent',
                  cursor: 'pointer', textAlign: 'start',
                }}
                onMouseEnter={e => e.currentTarget.style.background = 'var(--primary-50)'}
                onMouseLeave={e => e.currentTarget.style.background = 'transparent'}>
                <Avatar user={u} size={28} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 12, fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {lang === 'ar' ? u.nameAr : u.nameEn}
                  </div>
                  <div style={{ fontSize: 10, color: 'var(--ink-3)', display: 'flex', gap: 6 }}>
                    <span>{u.role}</span>
                    <span>·</span>
                    <span>{u.department}</span>
                    {u.onCall && (
                      <>
                        <span>·</span>
                        <span style={{ color: 'var(--success-fg)' }}>● {t('upOnCall')}</span>
                      </>
                    )}
                  </div>
                </div>
                <a href={`tel:${u.phone}`} onClick={e => e.stopPropagation()}
                  style={{ color: 'var(--ink-3)', padding: 2 }}>
                  <Phone size={12} />
                </a>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

/** Avatar — circular initials, deterministic color from user. */
export function Avatar({ user, size = 28, showOnCall = true }: { user: User; size?: number; showOnCall?: boolean }) {
  return (
    <div style={{
      position: 'relative',
      width: size, height: size, borderRadius: '50%',
      background: user.avatarColor,
      color: 'white', fontWeight: 700,
      fontSize: Math.max(9, size * 0.42),
      display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
      flexShrink: 0,
    }} title={user.nameEn}>
      {user.initials}
      {showOnCall && user.onCall && (
        <span style={{
          position: 'absolute', bottom: -1, insetInlineEnd: -1,
          width: Math.max(6, size * 0.3), height: Math.max(6, size * 0.3),
          borderRadius: '50%', background: 'var(--success)',
          border: '1.5px solid var(--surface-1)',
        }} />
      )}
    </div>
  );
}
