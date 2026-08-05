import { useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { stableContext } from './stableContext';
import { CheckCircle2, AlertCircle, Info, RotateCcw } from 'lucide-react';
import { useI18n } from './i18n';

type ToastVariant = 'default' | 'success' | 'danger' | 'warning';

interface ToastItem {
  id: number;
  message: string;
  variant: ToastVariant;
  /** Undo action — when provided, the toast renders an "Undo" button. */
  undo?: () => void;
  /** Time-to-live in ms (default 3500; undoable defaults to 5000). */
  ttl: number;
  /** When this toast was shown. */
  createdAt: number;
}

interface ToastContextValue {
  show: (message: string, variant?: ToastVariant) => void;
  success: (message: string) => void;
  danger: (message: string) => void;
  warning: (message: string) => void;
  /** Neutral info-toast — renders the default variant. */
  info: (message: string) => void;
  /**
   * Undoable action — Gmail-style. Shows toast with Undo button for 5s.
   * If user clicks Undo, the undo callback fires; otherwise the action commits.
   */
  undoable: (message: string, undo: () => void, opts?: { variant?: ToastVariant; ttl?: number }) => void;
}

const ToastContext = stableContext<ToastContextValue | null>('grc.toast', null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const { t, dir } = useI18n();
  const counter = useRef(0);

  const show = useCallback((message: string, variant: ToastVariant = 'default') => {
    counter.current += 1;
    const id = counter.current;
    const ttl = 3500;
    setItems(prev => [...prev, { id, message, variant, ttl, createdAt: Date.now() }]);
    setTimeout(() => setItems(prev => prev.filter(t => t.id !== id)), ttl);
  }, []);

  const undoable = useCallback((message: string, undo: () => void, opts?: { variant?: ToastVariant; ttl?: number }) => {
    counter.current += 1;
    const id = counter.current;
    const ttl = opts?.ttl ?? 5000;
    const variant = opts?.variant ?? 'success';
    setItems(prev => [...prev, { id, message, variant, undo, ttl, createdAt: Date.now() }]);
    setTimeout(() => setItems(prev => prev.filter(t => t.id !== id)), ttl);
  }, []);

  const triggerUndo = useCallback((id: number) => {
    setItems(prev => {
      const toast = prev.find(t => t.id === id);
      if (toast?.undo) toast.undo();
      return prev.filter(t => t.id !== id);
    });
  }, []);

  const value = useMemo<ToastContextValue>(() => ({
    show,
    success: (m) => show(m, 'success'),
    danger:  (m) => show(m, 'danger'),
    warning: (m) => show(m, 'warning'),
    info:    (m) => show(m, 'default'),
    undoable,
  }), [show, undoable]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-stack" dir={dir}>
        {items.map(item => <ToastRow key={item.id} item={item} onUndo={triggerUndo} t={t} />)}
      </div>
    </ToastContext.Provider>
  );
}

function ToastRow({ item, onUndo, t }: { item: ToastItem; onUndo: (id: number) => void; t: (k: string) => string }) {
  // Countdown progress bar (only for undoable).
  const [progress, setProgress] = useState(100);
  useEffect(() => {
    if (!item.undo) return;
    const start = item.createdAt;
    const interval = setInterval(() => {
      const elapsed = Date.now() - start;
      const pct = Math.max(0, 100 - (elapsed / item.ttl) * 100);
      setProgress(pct);
      if (pct <= 0) clearInterval(interval);
    }, 50);
    return () => clearInterval(interval);
  }, [item]);

  return (
    <div className={`toast ${item.variant}`} style={{ position: 'relative', overflow: 'hidden' }}>
      {item.variant === 'success' && <CheckCircle2 size={16} color="var(--success)" />}
      {item.variant === 'danger'  && <AlertCircle  size={16} color="var(--danger)" />}
      {item.variant === 'warning' && <AlertCircle  size={16} color="var(--warning)" />}
      {item.variant === 'default' && <Info         size={16} color="var(--primary)" />}
      <span style={{ flex: 1 }}>{item.message}</span>
      {item.undo && (
        <button onClick={() => onUndo(item.id)}
          style={{
            display: 'inline-flex', alignItems: 'center', gap: 4,
            padding: '4px 10px', fontSize: 11, fontWeight: 700,
            border: '1px solid var(--primary)', borderRadius: 4,
            background: 'var(--primary)', color: 'white',
            cursor: 'pointer', marginInlineStart: 8,
          }}>
          <RotateCcw size={11} />
          {t('undoBtn')}
        </button>
      )}
      {item.undo && (
        <div style={{
          position: 'absolute', bottom: 0, insetInlineStart: 0,
          height: 2, width: `${progress}%`,
          background: 'var(--primary)', transition: 'width 50ms linear',
        }} />
      )}
    </div>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>');
  return ctx;
}
