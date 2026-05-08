import { useEffect, useRef, useState } from 'react';
import { Upload, FileText, Trash2, Download, ShieldCheck, AlertOctagon } from 'lucide-react';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { Badge } from './Badge';
import { Button } from './Button';
import {
  uploadFile, downloadFile, deleteFile, listFiles, subscribe,
  formatBytes, SCAN_TONE, SCAN_LABEL,
  type FileRecord, type UploadProgress, type DataResidency,
} from '../../lib/fileUpload';

/**
 * FileUploadField — drag-drop upload with progress, virus-scan badge,
 * download, delete. Reusable across Evidence / DSR / Breach / TPRM
 * vendor attestations.
 *
 * Props:
 *   attachedTo — logical owner (e.g. "evidence/EV-1078")
 *   uploadedBy — who is doing the upload (current user)
 *   crNumber   — vendor CR for third-party-uploaded files
 *   residency  — KSA / GCC / GLOBAL (drives cross-border review flag)
 *   retentionDays — overrides the default 1095 (3y) retention
 */
export function FileUploadField({
  attachedTo, uploadedBy, crNumber, residency = 'KSA', retentionDays = 1095,
}: {
  attachedTo: string;
  uploadedBy: string;
  crNumber?: string;
  residency?: DataResidency;
  retentionDays?: number;
}) {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [files, setFiles] = useState<FileRecord[]>(() => listFiles(attachedTo));
  const [progress, setProgress] = useState<UploadProgress | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const inputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => subscribe(() => setFiles(listFiles(attachedTo))), [attachedTo]);

  async function handleFiles(list: FileList | null) {
    if (!list) return;
    for (const f of Array.from(list)) {
      try {
        await uploadFile(f, { attachedTo, uploadedBy, crNumber, residency, retentionDays }, p => setProgress(p));
        toast.success(t('fuUploadOkToast').replace('{name}', f.name));
      } catch (e) {
        toast.danger(t('fuUploadFailToast').replace('{name}', f.name).replace('{e}', e instanceof Error ? e.message : '?'));
      }
    }
    setProgress(null);
    if (inputRef.current) inputRef.current.value = '';
  }

  function handleDrop(e: React.DragEvent<HTMLDivElement>) {
    e.preventDefault();
    setDragOver(false);
    handleFiles(e.dataTransfer.files);
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div
        onDragOver={e => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={handleDrop}
        onClick={() => inputRef.current?.click()}
        style={{
          padding: 24, borderRadius: 'var(--r-md)',
          border: `2px dashed ${dragOver ? 'var(--primary)' : 'var(--border)'}`,
          background: dragOver ? 'var(--primary-50)' : 'var(--surface-2)',
          textAlign: 'center', cursor: 'pointer',
          transition: 'background var(--dur-fast), border-color var(--dur-fast)',
        }}>
        <Upload size={28} style={{ color: dragOver ? 'var(--primary)' : 'var(--ink-3)', marginBottom: 8 }} />
        <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 4 }}>
          {t('fuDragOrClick')}
        </div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
          {t('fuMaxSize')}
        </div>
        <input ref={inputRef} type="file" multiple
          style={{ display: 'none' }}
          onChange={e => handleFiles(e.target.files)} />
      </div>

      {progress && progress.phase !== 'DONE' && (
        <div style={{
          padding: 10, background: 'var(--info-bg, var(--surface-2))',
          borderRadius: 'var(--r-sm)', fontSize: 12, color: 'var(--ink-2)',
        }}>
          <div style={{ marginBottom: 4 }}>{t(`fuPhase${progress.phase[0]}${progress.phase.slice(1).toLowerCase()}`)}…</div>
          <div style={{ height: 4, background: 'var(--surface-1)', borderRadius: 2, overflow: 'hidden' }}>
            <div style={{
              width: `${progress.pct}%`, height: '100%',
              background: progress.phase === 'FAILED' ? 'var(--danger)' : 'var(--primary)',
              transition: 'width var(--dur-fast)',
            }} />
          </div>
        </div>
      )}

      {files.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {files.map(f => (
            <div key={f.id} style={{
              padding: '10px 0', borderBottom: '1px dashed var(--border)',
              display: 'grid', gridTemplateColumns: '20px 1fr auto auto auto auto', gap: 8, alignItems: 'center',
            }}>
              <FileText size={16} color="var(--ink-3)" />
              <div style={{ minWidth: 0 }}>
                <div style={{ fontSize: 13, fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {f.filename}
                </div>
                <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }} className="mono">
                  {formatBytes(f.sizeBytes)} · {f.mime} · sha256:{f.sha256.slice(0, 12)}…
                </div>
              </div>
              <Badge tone={SCAN_TONE[f.scanStatus]}>
                {f.scanStatus === 'CLEAN' && <ShieldCheck size={10} style={{ marginInlineEnd: 4, verticalAlign: 'middle' }} />}
                {f.scanStatus === 'INFECTED' && <AlertOctagon size={10} style={{ marginInlineEnd: 4, verticalAlign: 'middle' }} />}
                {lang === 'ar' ? SCAN_LABEL[f.scanStatus].ar : SCAN_LABEL[f.scanStatus].en}
              </Badge>
              <Badge tone={f.residency === 'KSA' ? 'success' : f.residency === 'GCC' ? 'info' : 'warning'}>
                {f.residency}
              </Badge>
              <Button variant="ghost" Icon={Download}
                onClick={() => { try { downloadFile(f); } catch (e) { toast.danger(String(e)); } }}>
                {t('fuDownload')}
              </Button>
              <Button variant="ghost" Icon={Trash2}
                onClick={() => {
                  if (!confirm(t('fuDeleteConfirm').replace('{name}', f.filename))) return;
                  deleteFile(f.id);
                  toast.info(t('fuDeletedToast'));
                }}>
                {t('fuDelete')}
              </Button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
