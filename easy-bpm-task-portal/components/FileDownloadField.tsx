/**
 * FileDownloadField — form component for downloading a previously uploaded document.
 *
 * - Reads document UUID from field value
 * - Loads metadata (filename, size, type) from GET /api/documents/{id}
 * - Renders a download link using the download endpoint
 */

import React, { useEffect, useState } from 'react';
import { Download, FileText, Loader2, AlertCircle } from 'lucide-react';
import { bpmService } from '../services/bpmService';
import { DocumentMetadata } from '../types';

export interface FileDownloadFieldProps {
  value: string; // document UUID
  label: string;
}

export const FileDownloadField: React.FC<FileDownloadFieldProps> = ({ value, label }) => {
  const [meta, setMeta] = useState<DocumentMetadata | null>(null);
  const [loading, setLoading] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!value) return;
    setLoading(true);
    setError(null);
    bpmService
      .getDocumentMetadata(value)
      .then(setMeta)
      .catch((err: unknown) => setError((err as Error).message ?? 'Could not load file'))
      .finally(() => setLoading(false));
  }, [value]);

  const formatBytes = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  if (!value) {
    return (
      <div className="flex items-center gap-2 p-3 rounded-lg border border-slate-200 bg-slate-50 text-slate-400 text-sm">
        <FileText size={16} />
        No file available
      </div>
    );
  }

  if (loading) {
    return (
      <div className="flex items-center gap-2 p-3 text-slate-500 text-sm">
        <Loader2 size={16} className="animate-spin" />
        Loading…
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center gap-2 p-3 rounded-lg border border-red-200 bg-red-50 text-red-600 text-sm">
        <AlertCircle size={16} />
        {error}
      </div>
    );
  }

  const handleDownload = async () => {
    setDownloading(true);
    setError(null);
    try {
      await bpmService.downloadDocument(value, meta?.fileName ?? 'document');
    } catch (err) {
      setError((err as Error).message ?? 'Could not download file');
    } finally {
      setDownloading(false);
    }
  };

  return (
    <button
      type="button"
      onClick={handleDownload}
      disabled={downloading}
      className="flex items-center gap-3 p-3 rounded-lg border border-blue-200 bg-blue-50 hover:bg-blue-100 transition-colors group w-fit"
      aria-label={`Download ${label}`}
    >
      <FileText size={18} className="text-blue-500 flex-shrink-0" />
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-slate-800 truncate">{meta?.fileName ?? 'Download'}</p>
        {meta && (
          <p className="text-xs text-slate-500">{formatBytes(meta.fileSize)} · {meta.contentType}</p>
        )}
      </div>
      {downloading ? (
        <Loader2 size={16} className="text-blue-400 animate-spin flex-shrink-0" />
      ) : (
        <Download size={16} className="text-blue-400 group-hover:text-blue-600 transition-colors flex-shrink-0" />
      )}
    </button>
  );
};
