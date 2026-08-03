/**
 * S5c — the two presentational atoms the detail and PIR panels share: a labelled inline
 * value and a labelled block of prose. Extracted rather than duplicated, because both
 * panels now live in separate files.
 */
export function Field({ label, value }: { label: string; value?: string | null }) {
  return <div>
    <div className="changes-field-label">{label}</div>
    <div className="changes-field-value">{value ?? '—'}</div>
  </div>;
}
export function Block({ label, body }: { label: string; body: string }) {
  return <div>
    <div className="changes-block-label">{label}</div>
    <div className="changes-block-body">{body}</div>
  </div>;
}
