import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Upload, FileSpreadsheet, Database, AlertTriangle, CheckCircle2,
  Download, RefreshCw, ChevronRight, ChevronLeft, Trash2, Eye,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  SCHEMAS, parseCsv, autoMap, validateRows,
  appendImported, appendImportLog, loadImportLog, loadImported, clearImported,
  subscribeCsvImport, buildSampleCsv,
  type TargetType, type RowValidation, type ImportLog,
} from '../../lib/csvImport';

/**
 * CsvImportPage — Track A2 (sales-readiness).
 *
 * Three-stage flow:
 *   1. Pick a target type (risks / controls / vendors) and upload CSV
 *   2. Map columns + see row-level preview with validation
 *   3. Import successful rows; failures stay in the preview for fix+retry
 *
 * Imported items are stored in target-keyed localStorage overlays so the
 * rest of the platform's modules can pick them up via loadImported(...).
 */
export function CsvImportPage() {
  const { t, lang } = useI18n();
  const toast = useToast();

  const [target, setTarget] = useState<TargetType>('risks');
  const [csvText, setCsvText] = useState<string>('');
  const [fileName, setFileName] = useState<string>('');
  const [mapping, setMapping] = useState<Record<string, string>>({});
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const schema = SCHEMAS.find(s => s.target === target)!;
  const rows = useMemo(() => csvText ? parseCsv(csvText) : [], [csvText]);
  const headers = rows[0] ?? [];

  // Auto-map when CSV is first loaded.
  useEffect(() => {
    if (headers.length > 0) {
      setMapping(autoMap(headers, schema));
    } else {
      setMapping({});
    }
  }, [csvText, target]);

  const validations = useMemo<RowValidation[]>(() =>
    rows.length > 1 ? validateRows(rows, headers, mapping, schema) : [],
  [rows, headers, mapping, schema]);

  const okCount = validations.filter(v => v.ok).length;
  const errCount = validations.length - okCount;

  // Reactive log + counts
  const [log, setLog] = useState<ImportLog[]>(() => loadImportLog());
  useEffect(() => subscribeCsvImport(() => setLog(loadImportLog())), []);

  function onPickFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setFileName(file.name);
    const reader = new FileReader();
    reader.onload = () => setCsvText(reader.result as string);
    reader.readAsText(file);
  }

  function downloadTemplate() {
    const csv = buildSampleCsv(schema);
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${target}-template.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  function loadSample() {
    setFileName(`${target}-sample.csv`);
    setCsvText(buildSampleCsv(schema));
  }

  function clearAll() {
    setCsvText('');
    setFileName('');
    setMapping({});
    if (fileInputRef.current) fileInputRef.current.value = '';
  }

  function runImport() {
    const ok = validations.filter(v => v.ok);
    if (ok.length === 0) {
      toast.danger(t('csvImpErrNothingToImport'));
      return;
    }
    const items = ok.map(v => ({
      id: `${target}-${Date.now()}-${v.rowIndex}-${Math.random().toString(36).slice(2, 7)}`,
      importedAt: new Date().toISOString(),
      data: v.data,
    }));
    appendImported(target, items);
    appendImportLog({
      importedAt: new Date().toISOString(),
      target, fileName,
      rowsImported: ok.length, rowsRejected: errCount,
    });
    toast.success(`${t('csvImpImportedToast')} (${ok.length})`);
    clearAll();
  }

  return (
    <Shell title={t('csvImpTitle')} sub={t('csvImpSub')}>
      <PageHeader
        eyebrow={t('csvImpEyebrow')}
        title={t('csvImpTitle')}
        sub={t('csvImpPageSub')}
        actions={
          <>
            <Button variant="secondary" Icon={Download} onClick={downloadTemplate}>
              {t('csvImpTemplate')}
            </Button>
            <Button variant="ghost" onClick={loadSample}>
              {t('csvImpLoadSample')}
            </Button>
          </>
        }
      />

      {/* Target picker */}
      <Card title={t('csvImpStep1')} Icon={Database}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
          {t('csvImpStep1Sub')}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 8 }}>
          {SCHEMAS.map(s => {
            const count = loadImported(s.target).length;
            const isOn = target === s.target;
            return (
              <button key={s.target} onClick={() => { setTarget(s.target); clearAll(); }}
                style={{
                  padding: 12, borderRadius: 'var(--r-md)',
                  border: `2px solid ${isOn ? 'var(--primary)' : 'var(--border)'}`,
                  background: isOn ? 'var(--primary-50)' : 'var(--surface-1)',
                  cursor: 'pointer', textAlign: 'start',
                }}>
                <div style={{ fontSize: 13, fontWeight: 600 }}>{lang === 'ar' ? s.nameAr : s.nameEn}</div>
                <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>
                  {t('csvImpFields')}: {s.fields.length} · {t('csvImpAlreadyImported')}: {count}
                </div>
              </button>
            );
          })}
        </div>
      </Card>

      {/* File picker */}
      <div style={{ marginTop: 16 }}>
        <Card title={t('csvImpStep2')} Icon={Upload}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
            {t('csvImpStep2Sub')}
          </div>
          <div style={{
            padding: 24, border: '2px dashed var(--border)', borderRadius: 'var(--r-md)',
            background: 'var(--surface-2)', textAlign: 'center',
          }}>
            <FileSpreadsheet size={32} color="var(--ink-3)" style={{ marginBottom: 8 }} />
            <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
              {fileName || t('csvImpNoFile')}
            </div>
            <div style={{ display: 'flex', gap: 6, justifyContent: 'center' }}>
              <Button variant="secondary" Icon={Upload} onClick={() => fileInputRef.current?.click()}>
                {t('csvImpPickFile')}
              </Button>
              {csvText && <Button variant="ghost" Icon={Trash2} onClick={clearAll}>{t('csvImpClear')}</Button>}
              <input ref={fileInputRef} type="file" accept=".csv,text/csv"
                onChange={onPickFile} style={{ display: 'none' }} />
            </div>
            {!fileName && (
              <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 12 }}>
                {t('csvImpHint')}
              </div>
            )}
          </div>
        </Card>
      </div>

      {/* Mapping + preview — only shown after CSV loaded */}
      {csvText && headers.length > 0 && (
        <>
          <div style={{ marginTop: 16 }}>
            <Card title={t('csvImpStep3')} Icon={ChevronRight}>
              <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
                {t('csvImpStep3Sub')}
              </div>
              <table className="tbl" style={{ width: '100%' }}>
                <thead>
                  <tr>
                    <th>{t('csvImpField')}</th>
                    <th>{t('csvImpRequired')}</th>
                    <th>{t('csvImpSourceColumn')}</th>
                  </tr>
                </thead>
                <tbody>
                  {schema.fields.map(f => {
                    const mapped = mapping[f.name];
                    return (
                      <tr key={f.name}>
                        <td>
                          <div style={{ fontWeight: 600 }}>{lang === 'ar' ? f.labelAr : f.labelEn}</div>
                          {(f.hintEn || f.hintAr) && (
                            <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }}>
                              {lang === 'ar' ? f.hintAr : f.hintEn}
                            </div>
                          )}
                        </td>
                        <td>
                          {f.required
                            ? <Badge tone="warning">{t('csvImpRequiredYes')}</Badge>
                            : <Badge tone="neutral">{t('csvImpRequiredNo')}</Badge>}
                        </td>
                        <td>
                          <select value={mapped ?? ''} onChange={e => setMapping(m => ({ ...m, [f.name]: e.target.value }))}
                            style={{ ...inputStyle, width: '100%' }}>
                            <option value="">— {t('csvImpUnmapped')} —</option>
                            {headers.map(h => (
                              <option key={h} value={h}>{h}</option>
                            ))}
                          </select>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </Card>
          </div>

          <div style={{ marginTop: 16 }}>
            <Card title={t('csvImpStep4')} Icon={Eye}>
              <div style={{
                display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12,
                fontSize: 12, color: 'var(--ink-3)',
              }}>
                <Badge tone="success" dot>
                  {okCount} {t('csvImpReady')}
                </Badge>
                {errCount > 0 && (
                  <Badge tone="danger" dot>
                    {errCount} {t('csvImpRejected')}
                  </Badge>
                )}
                <span>· {validations.length} {t('csvImpRowsTotal')}</span>
              </div>

              <div style={{ maxHeight: 360, overflow: 'auto', border: '1px solid var(--border)', borderRadius: 'var(--r-sm)' }}>
                <table className="tbl" style={{ width: '100%' }}>
                  <thead>
                    <tr>
                      <th style={{ width: 50 }}>{t('csvImpRow')}</th>
                      <th style={{ width: 80 }}>{t('csvImpStatus')}</th>
                      {schema.fields.map(f => (
                        <th key={f.name}>{lang === 'ar' ? f.labelAr : f.labelEn}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {validations.map(v => (
                      <tr key={v.rowIndex} style={{
                        background: v.ok ? undefined : 'rgba(220, 38, 38, 0.04)',
                      }}>
                        <td className="mono">{v.rowIndex}</td>
                        <td>
                          {v.ok
                            ? <Badge tone="success"><CheckCircle2 size={11} /> {t('csvImpRowOk')}</Badge>
                            : <Badge tone="danger" title={v.errors.join('\n')}>
                                <AlertTriangle size={11} /> {v.errors.length} {t('csvImpRowErr')}
                              </Badge>}
                        </td>
                        {schema.fields.map(f => (
                          <td key={f.name} style={{ fontSize: 11, maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {v.data[f.name] || <span style={{ color: 'var(--ink-3)' }}>—</span>}
                          </td>
                        ))}
                      </tr>
                    ))}
                    {validations.length === 0 && (
                      <tr><td colSpan={schema.fields.length + 2} style={{ textAlign: 'center', padding: 20, color: 'var(--ink-3)' }}>
                        {t('csvImpNoRows')}
                      </td></tr>
                    )}
                  </tbody>
                </table>
              </div>

              {/* Per-row errors */}
              {errCount > 0 && (
                <div style={{ marginTop: 12, fontSize: 11, color: 'var(--ink-3)' }}>
                  <div style={{ fontWeight: 600, marginBottom: 4 }}>{t('csvImpErrorsTitle')}:</div>
                  <ul style={{ margin: 0, paddingInlineStart: 18 }}>
                    {validations.filter(v => !v.ok).slice(0, 10).map(v => (
                      <li key={v.rowIndex}>
                        <span className="mono">{t('csvImpRow')} {v.rowIndex}</span>: {v.errors.join('; ')}
                      </li>
                    ))}
                    {validations.filter(v => !v.ok).length > 10 && (
                      <li>… ({validations.filter(v => !v.ok).length - 10} {t('csvImpMoreErrs')})</li>
                    )}
                  </ul>
                </div>
              )}

              <div style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                <Button variant="secondary" Icon={ChevronLeft} onClick={clearAll}>
                  {t('csvImpStartOver')}
                </Button>
                <Button variant="primary" Icon={CheckCircle2} onClick={runImport} disabled={okCount === 0}>
                  {t('csvImpRun')} ({okCount})
                </Button>
              </div>
            </Card>
          </div>
        </>
      )}

      {/* Import log */}
      <div style={{ marginTop: 16 }}>
        <Card title={t('csvImpLogTitle')} Icon={Database}
          action={<Button variant="ghost" Icon={RefreshCw} onClick={() => { clearImported('risks'); clearImported('controls'); clearImported('vendors'); toast.info(t('csvImpClearedToast')); }}>{t('csvImpClearAll')}</Button>}>
          {log.length === 0
            ? <div style={{ fontSize: 12, color: 'var(--ink-3)', textAlign: 'center', padding: 12 }}>
                {t('csvImpLogEmpty')}
              </div>
            : (
              <table className="tbl" style={{ width: '100%' }}>
                <thead>
                  <tr>
                    <th>{t('csvImpLogWhen')}</th>
                    <th>{t('csvImpLogTarget')}</th>
                    <th>{t('csvImpLogFile')}</th>
                    <th>{t('csvImpLogImported')}</th>
                    <th>{t('csvImpLogRejected')}</th>
                  </tr>
                </thead>
                <tbody>
                  {log.map((l, i) => (
                    <tr key={i}>
                      <td className="mono" style={{ fontSize: 11 }}>
                        {new Date(l.importedAt).toLocaleString()}
                      </td>
                      <td>
                        {(() => {
                          const s = SCHEMAS.find(x => x.target === l.target);
                          return s ? (lang === 'ar' ? s.nameAr : s.nameEn) : l.target;
                        })()}
                      </td>
                      <td className="mono" style={{ fontSize: 11 }}>{l.fileName || '—'}</td>
                      <td><Badge tone="success">{l.rowsImported}</Badge></td>
                      <td>
                        {l.rowsRejected > 0
                          ? <Badge tone="danger">{l.rowsRejected}</Badge>
                          : <Badge tone="neutral">0</Badge>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
        </Card>
      </div>
    </Shell>
  );
}

const inputStyle: React.CSSProperties = {
  padding: '8px 10px', fontSize: 13,
  border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
  background: 'var(--surface-1)',
};
