/**
 * S5c — the change board (kanban). §11's `ChangeBoardPage`, still rendered as a tab of
 * `ChangesPage`: CE-13 gives it a route, and giving it one now would be nav-to-nowhere.
 */
import { Badge } from '../../components/ui/Badge';
import type { ChangeRequestDto } from '../../lib/api';
import { KANBAN_LANES, TYPE_TONE, RISK_TONE } from './changeVocabulary';
import { mayTransitionTo, nextStatus } from './changeLifecycle';

// ─── Kanban ────────────────────────────────────────────────────────────────
export function KanbanBoard({ items, onTransition, onOpen, t, lang }: {
  items: ChangeRequestDto[];
  onTransition: (c: ChangeRequestDto, target: string) => void | Promise<void>;
  onOpen: (id: number) => void;
  t: (k: string) => string;
  lang: string;
}) {
  return (
    <div className="changes-kanban">
      {KANBAN_LANES.map(lane => {
        const lst = items.filter(c => lane.statuses.includes(c.status));
        return (
          <div key={lane.id} className="changes-kanban__lane">
            <div className="changes-kanban__lane-head">
              <span className="changes-kanban__lane-title">{lane.id}</span>
              <Badge tone="neutral">{lst.length}</Badge>
            </div>
            <div className="changes-kanban__cards">
              {lst.map(c => (
                <div key={c.id} onClick={() => onOpen(c.id)} className="changes-kanban__card">
                  <div className="changes-kanban__card-head">
                    <span className="mono changes-kanban__card-code">{c.changeCode ?? `#${c.id}`}</span>
                    <Badge tone={TYPE_TONE[c.changeType] ?? 'neutral'}>{c.changeType}</Badge>
                  </div>
                  <div className="changes-kanban__card-title">
                    {lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn}
                  </div>
                  <div className="changes-kanban__card-meta">
                    {c.riskLevel && <Badge tone={RISK_TONE[c.riskLevel] ?? 'neutral'} dot>{c.riskLevel}</Badge>}
                    {c.scheduledStart && <span className="mono changes-kanban__card-date">{new Date(c.scheduledStart).toLocaleDateString()}</span>}
                    <button onClick={(e) => { e.stopPropagation(); void onTransition(c, nextStatus(c) ?? c.status); }}
                            disabled={!mayTransitionTo(nextStatus(c))}
                            title={nextStatus(c) && !mayTransitionTo(nextStatus(c))
                              ? t('changeNeedsApprovePerm') : undefined}
                            className="changes-kanban__transition-btn">
                      {nextStatus(c) ? `→ ${nextStatus(c)?.replace(/_/g, ' ')}` : t('changeKanbanDone')}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        );
      })}
    </div>
  );
}
