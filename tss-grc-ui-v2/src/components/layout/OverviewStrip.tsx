import { useState, useEffect } from 'react';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { ProcessLifecycleBar, type ProcessLifecycle } from '../workflows/ProcessLifecycleBar';
import { RegulatorPostureCard } from '../authority/RegulatorPostureCard';
import type { RegulatorAnchor } from '../../lib/evidenceAnchors';

/**
 * OverviewStrip — collapsible upper area on every module page.
 *
 * Default: compact (single-row of process-launcher chips). Saves vertical
 * space so the actual register / data table sits above the fold.
 *
 * Click "Show overview" to expand into the full ProcessLifecycleBar cards
 * + RegulatorPostureCard breakdown. Preference persists per-module via
 * localStorage so a user who always wants the full overview gets it back
 * on next visit.
 *
 * ServiceNow IRM pattern: list view stays primary; analytics live in an
 * explicit dashboard or a togglable upper drawer.
 */
export function OverviewStrip({
  storageKey,
  processBarTitle, processBarSubtitle, lifecycles,
  postureTitle, anchors,
  lang,
}: {
  /** localStorage key per module (e.g. "overview.risk", "overview.cyber"). */
  storageKey: string;
  processBarTitle: string;
  processBarSubtitle?: string;
  lifecycles: ProcessLifecycle[];
  postureTitle: string;
  anchors: RegulatorAnchor[];
  lang: string;
}) {
  const [expanded, setExpanded] = useState<boolean>(() => {
    try { return localStorage.getItem(storageKey) === '1'; } catch { return false; }
  });
  useEffect(() => {
    try { localStorage.setItem(storageKey, expanded ? '1' : '0'); } catch { /* ignore */ }
  }, [storageKey, expanded]);

  return (
    <div style={{ marginBottom: 12 }}>
      {!expanded
        ? <ProcessLifecycleBar title={processBarTitle} subtitle={processBarSubtitle} lifecycles={lifecycles} lang={lang} compact />
        : <>
            <ProcessLifecycleBar title={processBarTitle} subtitle={processBarSubtitle} lifecycles={lifecycles} lang={lang} />
            <div style={{ marginTop: 12 }}>
              <RegulatorPostureCard title={postureTitle} anchors={anchors} />
            </div>
          </>
      }
      <button type="button"
        onClick={() => setExpanded(v => !v)}
        style={{
          marginTop: 6,
          background: 'transparent', border: 'none', cursor: 'pointer',
          color: 'var(--ink-3)', fontSize: 11, fontWeight: 600,
          display: 'inline-flex', alignItems: 'center', gap: 4,
          padding: '4px 8px',
          borderRadius: 'var(--r-sm)',
          transition: 'background var(--dur-fast) var(--ease-standard), color var(--dur-fast) var(--ease-standard)',
        }}
        onMouseEnter={(e) => {
          const el = e.currentTarget as HTMLElement;
          el.style.background = 'var(--surface-2)';
          el.style.color = 'var(--ink-1)';
        }}
        onMouseLeave={(e) => {
          const el = e.currentTarget as HTMLElement;
          el.style.background = 'transparent';
          el.style.color = 'var(--ink-3)';
        }}
      >
        {expanded ? <><ChevronUp size={12} /> Hide overview</> : <><ChevronDown size={12} /> Show overview</>}
      </button>
    </div>
  );
}
