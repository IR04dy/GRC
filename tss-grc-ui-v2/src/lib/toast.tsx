import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { CheckCircle2, AlertCircle, Info } from 'lucide-react';
import { useI18n } from './i18n';

type ToastVariant = 'default' | 'success' | 'danger';
interface ToastItem { id: number; message: string; variant: ToastVariant; }

interface ToastContextValue {
  show: (message: string, variant?: ToastVariant) => void;
  success: (message: string) => void;
  danger: (message: string) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const { dir } = useI18n();

  const show = useCallback((message: string, variant: ToastVariant = 'default') => {
    const id = Date.now() + Math.random();
    setItems(prev => [...prev, { id, message, variant }]);
    setTimeout(() => setItems(prev => prev.filter(t => t.id !== id)), 3500);
  }, []);

  const value = useMemo<ToastContextValue>(() => ({
    show,
    success: (m) => show(m, 'success'),
    danger:  (m) => show(m, 'danger'),
  }), [show]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-stack" dir={dir}>
        {items.map(t => (
          <div key={t.id} className={`toast ${t.variant}`}>
            {t.variant === 'success' && <CheckCircle2 size={16} color="var(--success)" />}
            {t.variant === 'danger'  && <AlertCircle  size={16} color="var(--danger)" />}
            {t.variant === 'default' && <Info         size={16} color="var(--primary)" />}
            <span>{t.message}</span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>');
  return ctx;
}
