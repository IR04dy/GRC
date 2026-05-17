// Operator registry per field type — mirrors the industry pattern encoded-query operators
// (eq/ne/lt/lte/gt/gte/contains/in/...) and serializes to a URL-friendly JSON.

export type FieldType = 'string' | 'number' | 'date' | 'select' | 'reference' | 'boolean';

export type Operator =
  | 'eq' | 'ne'
  | 'contains' | 'starts_with' | 'ends_with'
  | 'lt' | 'lte' | 'gt' | 'gte'
  | 'between' | 'in' | 'not_in'
  | 'is_empty' | 'is_not_empty'
  | 'before' | 'after' | 'today' | 'last_n_days' | 'is_overdue'
  | 'assigned_to_me' | 'created_by_me';

export interface OperatorDef {
  /** Operator code used in serialization. */
  op: Operator;
  /** Bilingual labels — caller can localize via t(). */
  labelEn: string;
  labelAr: string;
  /** How many value inputs the operator needs (0 for `is_empty`, 1 for `eq`, 2 for `between`). */
  arity: 0 | 1 | 2;
}

const COMMON_NULL_OPS: OperatorDef[] = [
  { op: 'is_empty',     arity: 0, labelEn: 'is empty',     labelAr: 'فارغ' },
  { op: 'is_not_empty', arity: 0, labelEn: 'is not empty', labelAr: 'غير فارغ' },
];

const STRING_OPS: OperatorDef[] = [
  { op: 'contains',    arity: 1, labelEn: 'contains',    labelAr: 'يحتوي' },
  { op: 'eq',          arity: 1, labelEn: 'equals',      labelAr: 'يساوي' },
  { op: 'ne',          arity: 1, labelEn: 'not equals',  labelAr: 'لا يساوي' },
  { op: 'starts_with', arity: 1, labelEn: 'starts with', labelAr: 'يبدأ بـ' },
  { op: 'ends_with',   arity: 1, labelEn: 'ends with',   labelAr: 'ينتهي بـ' },
  ...COMMON_NULL_OPS,
];

const NUMBER_OPS: OperatorDef[] = [
  { op: 'eq',      arity: 1, labelEn: '=',          labelAr: '=' },
  { op: 'ne',      arity: 1, labelEn: '≠',          labelAr: '≠' },
  { op: 'lt',      arity: 1, labelEn: '<',          labelAr: '<' },
  { op: 'lte',     arity: 1, labelEn: '≤',          labelAr: '≤' },
  { op: 'gt',      arity: 1, labelEn: '>',          labelAr: '>' },
  { op: 'gte',     arity: 1, labelEn: '≥',          labelAr: '≥' },
  { op: 'between', arity: 2, labelEn: 'between',    labelAr: 'بين' },
  ...COMMON_NULL_OPS,
];

const DATE_OPS: OperatorDef[] = [
  { op: 'before',       arity: 1, labelEn: 'before',         labelAr: 'قبل' },
  { op: 'after',        arity: 1, labelEn: 'after',          labelAr: 'بعد' },
  { op: 'between',      arity: 2, labelEn: 'between',        labelAr: 'بين' },
  { op: 'today',        arity: 0, labelEn: 'today',          labelAr: 'اليوم' },
  { op: 'last_n_days',  arity: 1, labelEn: 'last N days',    labelAr: 'آخر N أيام' },
  { op: 'is_overdue',   arity: 0, labelEn: 'overdue',        labelAr: 'متأخر' },
  ...COMMON_NULL_OPS,
];

const SELECT_OPS: OperatorDef[] = [
  { op: 'eq',     arity: 1, labelEn: 'is',     labelAr: 'هو' },
  { op: 'ne',     arity: 1, labelEn: 'is not', labelAr: 'ليس' },
  { op: 'in',     arity: 1, labelEn: 'in',     labelAr: 'ضمن' },
  { op: 'not_in', arity: 1, labelEn: 'not in', labelAr: 'ليس ضمن' },
  ...COMMON_NULL_OPS,
];

const REFERENCE_OPS: OperatorDef[] = [
  { op: 'eq',                arity: 1, labelEn: 'is',              labelAr: 'هو' },
  { op: 'ne',                arity: 1, labelEn: 'is not',          labelAr: 'ليس' },
  { op: 'in',                arity: 1, labelEn: 'in',              labelAr: 'ضمن' },
  { op: 'assigned_to_me',    arity: 0, labelEn: 'assigned to me',  labelAr: 'مسنَدة لي' },
  { op: 'created_by_me',     arity: 0, labelEn: 'created by me',   labelAr: 'أنشأتها أنا' },
  ...COMMON_NULL_OPS,
];

const BOOLEAN_OPS: OperatorDef[] = [
  { op: 'eq', arity: 1, labelEn: 'is', labelAr: 'هو' },
];

export function operatorsFor(type: FieldType): OperatorDef[] {
  switch (type) {
    case 'string':    return STRING_OPS;
    case 'number':    return NUMBER_OPS;
    case 'date':      return DATE_OPS;
    case 'select':    return SELECT_OPS;
    case 'reference': return REFERENCE_OPS;
    case 'boolean':   return BOOLEAN_OPS;
  }
}
