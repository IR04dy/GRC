import { Plus, X, Filter } from 'lucide-react';
import { useI18n } from '../../../lib/i18n';
import {
  type FilterCondition, type FilterGroup,
} from './useFilterState';
import { operatorsFor, type FieldType, type Operator } from './operators';

export interface FieldDef {
  /** Property key on the data row. */
  key: string;
  /** Bilingual label. Caller passes localized string. */
  label: string;
  type: FieldType;
  /** Static options for `select` / `reference` types. */
  options?: { value: string; label: string }[];
}

export interface FilterBuilderProps {
  fields: FieldDef[];
  group: FilterGroup;
  onChange: (g: FilterGroup) => void;
}

let nextId = 0;
const newId = () => `f-${++nextId}`;

/**
 * Visual filter builder. Supports nested AND/OR groups + per-field operators.
 *
 * Renders as a stack of rows with a "+ Add condition" / "+ Add group" footer.
 * State is owned by the caller via `useFilterState` so the builder can be
 * controlled externally (URL sync, reset to default, etc.).
 */
export function FilterBuilder({ fields, group, onChange }: FilterBuilderProps) {
  const { lang } = useI18n();

  const addCondition = (parent: FilterGroup) => {
    const f = fields[0];
    const ops = operatorsFor(f.type);
    const cond: FilterCondition = { id: newId(), field: f.key, type: f.type, op: ops[0].op };
    onChange(replaceGroup(group, parent.id, { ...parent, conditions: [...parent.conditions, cond] }));
  };

  const addGroup = (parent: FilterGroup) => {
    const sub: FilterGroup = { id: newId(), combinator: 'AND', conditions: [] };
    onChange(replaceGroup(group, parent.id, { ...parent, conditions: [...parent.conditions, sub] }));
  };

  const removeAt = (parentId: string, index: number) => {
    onChange(updateGroup(group, parentId, (g) => ({
      ...g, conditions: g.conditions.filter((_, i) => i !== index),
    })));
  };

  const updateCondition = (parentId: string, index: number, patch: Partial<FilterCondition>) => {
    onChange(updateGroup(group, parentId, (g) => ({
      ...g,
      conditions: g.conditions.map((c, i) => i === index ? { ...(c as FilterCondition), ...patch } : c),
    })));
  };

  const setCombinator = (parentId: string, combinator: 'AND' | 'OR') => {
    onChange(updateGroup(group, parentId, (g) => ({ ...g, combinator })));
  };

  return (
    <div className="filter-root">
      <div className="filter-head">
        <Filter size={14} />
        <strong>{lang === 'ar' ? 'تصفية' : 'Filter'}</strong>
      </div>
      <GroupView
        group={group}
        fields={fields}
        lang={lang}
        onAddCondition={addCondition}
        onAddGroup={addGroup}
        onRemove={removeAt}
        onUpdateCondition={updateCondition}
        onSetCombinator={setCombinator}
      />
    </div>
  );
}

function GroupView(props: {
  group: FilterGroup;
  fields: FieldDef[];
  lang: 'en' | 'ar';
  depth?: number;
  onAddCondition: (g: FilterGroup) => void;
  onAddGroup: (g: FilterGroup) => void;
  onRemove: (parentId: string, index: number) => void;
  onUpdateCondition: (parentId: string, index: number, patch: Partial<FilterCondition>) => void;
  onSetCombinator: (parentId: string, combinator: 'AND' | 'OR') => void;
}) {
  const { group, fields, lang, depth = 0 } = props;
  const indent = depth * 16;

  return (
    <div className="filter-group" style={{ marginInlineStart: indent }}>
      {group.conditions.length > 1 && (
        <div className="filter-combinator-row">
          <button
            type="button"
            onClick={() => props.onSetCombinator(group.id, group.combinator === 'AND' ? 'OR' : 'AND')}
            className="filter-combinator"
          >
            {group.combinator}
          </button>
        </div>
      )}

      {group.conditions.map((c, i) => (
        'combinator' in c ? (
          <div key={c.id} className="filter-subgroup">
            <GroupView {...props} group={c} depth={depth + 1} />
            <button type="button" onClick={() => props.onRemove(group.id, i)} className="filter-remove">
              <X size={12} />
            </button>
          </div>
        ) : (
          <ConditionRow
            key={c.id}
            condition={c}
            fields={fields}
            lang={lang}
            onChange={(patch) => props.onUpdateCondition(group.id, i, patch)}
            onRemove={() => props.onRemove(group.id, i)}
          />
        )
      ))}

      <div className="filter-actions">
        <button type="button" onClick={() => props.onAddCondition(group)} className="filter-action-btn">
          <Plus size={12} /> {lang === 'ar' ? 'شرط' : 'Condition'}
        </button>
        <button type="button" onClick={() => props.onAddGroup(group)} className="filter-action-btn">
          <Plus size={12} /> {lang === 'ar' ? 'مجموعة' : 'Group'}
        </button>
      </div>
    </div>
  );
}

function ConditionRow(props: {
  condition: FilterCondition;
  fields: FieldDef[];
  lang: 'en' | 'ar';
  onChange: (patch: Partial<FilterCondition>) => void;
  onRemove: () => void;
}) {
  const { condition: c, fields, lang } = props;
  const field = fields.find(f => f.key === c.field) ?? fields[0];
  const ops = operatorsFor(field.type);
  const opDef = ops.find(o => o.op === c.op) ?? ops[0];

  return (
    <div className="filter-row">
      <select
        value={c.field}
        onChange={(e) => {
          const newField = fields.find(f => f.key === e.target.value)!;
          const newOps = operatorsFor(newField.type);
          props.onChange({ field: newField.key, type: newField.type, op: newOps[0].op, value: undefined, value2: undefined, values: undefined });
        }}
      >
        {fields.map(f => <option key={f.key} value={f.key}>{f.label}</option>)}
      </select>

      <select
        value={c.op}
        onChange={(e) => props.onChange({ op: e.target.value as Operator, value: undefined, value2: undefined })}
      >
        {ops.map(o => <option key={o.op} value={o.op}>{lang === 'ar' ? o.labelAr : o.labelEn}</option>)}
      </select>

      {opDef.arity >= 1 && (
        <input
          type={field.type === 'date' ? 'date' : field.type === 'number' ? 'number' : 'text'}
          value={c.value ?? ''}
          onChange={(e) => props.onChange({ value: e.target.value })}
          placeholder={lang === 'ar' ? 'القيمة' : 'value'}
        />
      )}
      {opDef.arity === 2 && (
        <input
          type={field.type === 'date' ? 'date' : 'number'}
          value={c.value2 ?? ''}
          onChange={(e) => props.onChange({ value2: e.target.value })}
          placeholder={lang === 'ar' ? 'إلى' : 'to'}
        />
      )}

      <button type="button" onClick={props.onRemove} className="filter-remove">
        <X size={12} />
      </button>
    </div>
  );
}

// ─── Helpers ───────────────────────────────────────────────────────────────

function updateGroup(root: FilterGroup, targetId: string, fn: (g: FilterGroup) => FilterGroup): FilterGroup {
  if (root.id === targetId) return fn(root);
  return {
    ...root,
    conditions: root.conditions.map(c =>
      'combinator' in c ? updateGroup(c, targetId, fn) : c
    ),
  };
}

function replaceGroup(root: FilterGroup, targetId: string, replacement: FilterGroup): FilterGroup {
  return updateGroup(root, targetId, () => replacement);
}
