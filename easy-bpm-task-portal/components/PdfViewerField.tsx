/**
 * PdfViewerField — inline PDF viewer component for forms.
 *
 * - Reads document UUID from field value
 * - Renders a native <iframe> pointing to GET /api/documents/{id}/preview
 * - For non-PDF content types the preview URL falls back to an attachment
 *   download link (browser-native behaviour).
 * - Provides "Open in new tab" and "Download" fallback buttons
 */

import React, { useEffect, useState } from 'react';
import { ExternalLink, Download, Loader2, FileText, AlertCircle, Maximize2 } from 'lucide-react';
import { bpmService } from '../services/bpmService';
import { DocumentMetadata } from '../types';

export interface PdfViewerFieldProps {
  value: string; // document UUID
  label: string;
}

export const PdfViewerField: React.FC<PdfViewerFieldProps> = ({ value, label }) => {
  const [meta, setMeta] = useState<DocumentMetadata | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fullscreen, setFullscreen] = useState(false);

  useEffect(() => {
    if (!value) return;
    setLoading(true);
    setError(null);
    bpmService
      .getDocumentMetadata(value)
      .then(setMeta)
      .catch((err: unknown) => setError((err as Error).message ?? 'Could not load document'))
      .finally(() => setLoading(false));
  }, [value]);

  if (!value) {
    return (
      <div className="flex items-center gap-2 p-3 rounded-lg border border-slate-200 bg-slate-50 text-slate-400 text-sm">
        <FileText size={16} />
        No document available
      </div>
    );
  }

  if (loading) {
    return (
      <div className="flex items-center gap-2 p-3 text-slate-500 text-sm">
        <Loader2 size={16} className="animate-spin" />
        Loading document…
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

  const isPdf = meta?.contentType === 'application/pdf';
  const previewUrl = bpmService.getDocumentPreviewUrl(value);
  const downloadUrl = bpmService.getDocumentDownloadUrl(value);

  if (!isPdf) {
    // Fallback for non-PDF: show download link instead
    return (
      <div className="space-y-2">
        <div className="flex items-center gap-2 p-3 rounded-lg border border-amber-200 bg-amber-50 text-amber-700 text-sm">
          <AlertCircle size={16} className="flex-shrink-0" />
          Inline preview is only available for PDF files. Use the download button below.
        </div>
        <a
          href={downloadUrl}
          download={meta?.fileName ?? 'document'}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 text-sm font-medium transition-colors"
          aria-label={`Download ${label}`}
        >
          <Download size={14} />
          {meta?.fileName ?? 'Download file'}
        </a>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      {/* Toolbar */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2 text-sm text-slate-500">
          <FileText size={14} />
          <span className="truncate max-w-[200px]">{meta?.fileName}</span>
        </div>
        <div className="flex items-center gap-2">
          <a
            href={previewUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded text-xs font-medium bg-slate-100 hover:bg-slate-200 text-slate-600 transition-colors"
            aria-label="Open in new tab"
          >
            <ExternalLink size={12} />
            New tab
          </a>
          <a
            href={downloadUrl}
            download={meta?.fileName ?? 'document.pdf'}
            className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded text-xs font-medium bg-slate-100 hover:bg-slate-200 text-slate-600 transition-colors"
            aria-label="Download document"
          >
            <Download size={12} />
            Download
          </a>
          <button
            type="button"
            onClick={() => setFullscreen(!fullscreen)}
            className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded text-xs font-medium bg-slate-100 hover:bg-slate-200 text-slate-600 transition-colors"
            aria-label={fullscreen ? 'Exit fullscreen' : 'Fullscreen'}
          >
            <Maximize2 size={12} />
            {fullscreen ? 'Exit' : 'Fullscreen'}
          </button>
        </div>
      </div>

      {/* PDF iframe */}
      {fullscreen ? (
        <div className="fixed inset-0 z-50 bg-black/80 flex flex-col" role="dialog" aria-label="PDF fullscreen viewer">
          <div className="flex items-center justify-between px-4 py-3 bg-slate-900 text-white">
            <span className="text-sm font-medium truncate">{meta?.fileName}</span>
            <button
              type="button"
              onClick={() => setFullscreen(false)}
              className="px-3 py-1 rounded bg-slate-700 hover:bg-slate-600 text-sm"
            >
              Close
            </button>
          </div>
          <iframe
            src={previewUrl}
            title={`PDF preview of ${label}`}
            className="flex-1 w-full bg-white"
            style={{ border: 'none' }}
          />
        </div>
      ) : (
        <iframe
          src={previewUrl}
          title={`PDF preview of ${label}`}
          className="w-full rounded-lg border border-slate-200"
          style={{ height: '500px', border: 'none' }}
        />
      )}
    </div>
  );
};
