/**
 * S5c — cross-module links from a selected change (§13's generic `change_links`:
 * 12 entity types x 7 relationship types). Kept as a panel: CE-13 folds it into the
 * change detail page rather than giving it a route of its own.
 */
import { useEffect, useMemo, useState } from 'react';
import { Plus, Trash2, Link2 } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { FormModal } from '../../components/ui/FormModal';
import { changeApi, ApiError, type ChangeRequestDto, type ChangeLinkDto } from '../../lib/api';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import { ENTITY_TYPE_OPTIONS, LINK_TYPE_OPTIONS } from './changeVocabulary';

// ─── Integrations (links from a selected change to other modules) ──────────
export function IntegrationsPanel({ items, detailId, setDetailId, onChanged, t, toast }: {
  items: ChangeRequestDto[];
  detailId: number | null; setDetailId: (id: number | null) => void;
  onChanged: () => void | Promise<void>;
  t: (k: string) => string;
  toast: ReturnType<typeof useToast>;
}) {
  const change = items.find(c => c.id === detailId) ?? null;
  const [links, setLinks] = useState<ChangeLinkDto[]>([]);
  const dialog = useDialog();
  const [linkOpen, setLinkOpen] = useState(false);
  const linkInitial = useMemo(() => ({ entityType: 'RISK', entityId: undefined as number | undefined, linkType: 'AFFECTS' }), []);

  async function reload() {
    if (!change) { setLinks([]); return; }
    try { setLinks(await changeApi.links(change.id)); } catch { /* silent */ }
  }
  useEffect(() => { void reload(); /* eslint-disable-next-line */ }, [detailId]);

  function addLink() { if (change) setLinkOpen(true); }
  // CH-8: a real modal with proper <select> controls for entity/link type —
  // replaces three chained window.prompt calls that also allowed invalid free-text.
  async function submitLink(v: { entityType: string; entityId?: number; linkType: string }) {
    if (!change) return;
    const entityId = Number(v.entityId);
    if (!Number.isFinite(entityId)) { toast.danger(t('changeLinkInvalidId')); return; }
    try {
      await changeApi.addLink(change.id, { entityType: v.entityType, entityId, linkType: v.linkType });
      setLinkOpen(false); void reload(); void onChanged();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function deleteLink(l: ChangeLinkDto) {
    if (!l.id) return;
    const ok = await dialog.confirm({ title: t('changeLinkRemove'), body: t('changeLinkDeleteConfirm'),
      destructive: true, submitLabel: t('changeLinkRemove') });
    if (!ok) return;
    try { await changeApi.deleteLink(l.id); void reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  if (!change) {
    return (
      <Card title={t('changeTabIntegrations')}>
        <EmptyState Icon={Link2} title={t('changeIntegrationsSelectTitle')} sub={t('changeIntegrationsSelectSub')} />
        <div className="changes-integration-tags">
          {items.slice(0, 12).map(c => (
            <button key={c.id} onClick={() => setDetailId(c.id)} className="changes-integration-pill">
              {c.changeCode ?? `#${c.id}`}
            </button>
          ))}
        </div>
      </Card>
    );
  }

  return (
    <>
      <Card title={`${t('changeIntegrationsFor')} · ${change.changeCode ?? '#' + change.id}`}
          Icon={Link2}
          action={<Button variant="primary" Icon={Plus} onClick={addLink}>{t('changeLinkAdd')}</Button>}>
      {links.length === 0
        ? <div className="changes-empty-note">{t('changeNoLinks')}</div>
        : (
          <div className="changes-integrations-list">
            {links.map(l => (
              <div key={l.id} className="changes-integration-row">
                <Badge tone="primary">{l.entityType.replace(/_/g, ' ')}</Badge>
                <span className="mono changes-integration-row__id">#{l.entityId}</span>
                <Badge tone="info">{l.linkType.replace(/_/g, ' ')}</Badge>
                {l.notes && <span className="changes-integration-row__notes">{l.notes}</span>}
                <button onClick={() => deleteLink(l)} title={t('deleteBtn')} className="changes-integration-row__del">
                  <Trash2 size={14} />
                </button>
              </div>
            ))}
          </div>
        )
      }
      </Card>
      <FormModal
        open={linkOpen}
        title={t('changeLinkAdd')}
        size="md"
        fields={[
          { kind: 'select', key: 'entityType', label: t('changeLinkEntityType'), required: true, span: 6, options: ENTITY_TYPE_OPTIONS },
          { kind: 'number', key: 'entityId', label: t('changeLinkEntityId'), required: true, span: 6, min: 1 },
          { kind: 'select', key: 'linkType', label: t('changeLinkType'), required: true, span: 12, options: LINK_TYPE_OPTIONS },
        ]}
        initial={linkInitial}
        submitLabel={t('changeLinkAdd')}
        onClose={() => setLinkOpen(false)}
        onSubmit={submitLink}
      />
    </>
  );
}
