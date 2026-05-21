/**
 * FileUploadField — form component for single-file upload.
 *
 * - Accepts drag-and-drop or click-to-browse
 * - Validates allowed extensions and max size client-side before upload
 * - Uploads via POST /api/documents and stores the resulting UUID as the field value
 * - Shows upload progress, filename, and a remove button
 * - Replace semantics: a new upload removes the previous document for the same field
 */

import React, { useRef, useState, useEffect } from 'react';
import { Upload, X, CheckCircle2, Loader2, AlertCircle, FileText } from 'lucide-react';
import { bpmService } from '../services/bpmService';
import { DocumentMetadata } from '../types';

export interface FileUploadFieldProps {
  fieldKey: string;
  label: string;
  value: string; // document UUID (empty string = no upload yet)
  onChange: (newValue: string) => void;
  disabled?: boolean;
  taskId?: number;
  processInstanceId?: number;
  allowedExtensions?: string[];
  maxSizeMb?: number;
  required?: boolean;
}

export const FileUploadField: React.FC<FileUploadFieldProps> = ({
  fieldKey,
  label,
  value,
  onChange,
  disabled = false,
  taskId,
  processInstanceId,
  allowedExtensions,
  maxSizeMb = 20,
  required = false
}) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [meta, setMeta] = useState<DocumentMetadata | null>(null);
  const [isDragOver, setIsDragOver] = useState(false);

  // Load metadata for already-uploaded document when component mounts / value changes
  useEffect(() => {
    if (!value) {
      setMeta(null);
      return;
    }
    let cancelled = false;
    bpmService.getDocumentMetadata(value).then((m) => {
      if (!cancelled) setMeta(m);
    }).catch(() => {
      if (!cancelled) setMeta(null);
    });
    return () => { cancelled = true; };
  }, [value]);

  const validateFile = (file: File): string | null => {
    if (allowedExtensions && allowedExtensions.length > 0) {
      const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
      if (!allowedExtensions.includes(ext)) {
        return `File type .${ext} is not allowed. Allowed: ${allowedExtensions.join(', ')}`;
      }
    }
    if (file.size > maxSizeMb * 1024 * 1024) {
      return `File is too large (max ${maxSizeMb} MB)`;
    }
    if (file.size === 0) {
      return 'File is empty';
    }
    return null;
  };

  const handleFile = async (file: File) => {
    const validationError = validateFile(file);
    if (validationError) {
      setError(validationError);
      return;
    }

    setError(null);
    setUploading(true);
    try {
      const uploaded = await bpmService.uploadDocument(file, taskId, processInstanceId, fieldKey);
      setMeta(uploaded);
      onChange(uploaded.id);
    } catch (err) {
      setError((err as Error).message ?? 'Upload failed');
    } finally {
      setUploading(false);
    }
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) void handleFile(file);
    // Reset input so same file can be re-uploaded
    e.target.value = '';
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragOver(false);
    if (disabled || uploading) return;
    const file = e.dataTransfer.files?.[0];
    if (file) void handleFile(file);
  };

  const handleRemove = async () => {
    if (!value) return;
    try {
      await bpmService.deleteDocument(value);
    } catch {
      // Best-effort; remove from form regardless
    }
    setMeta(null);
    onChange('');
  };

  const formatBytes = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  return (
    <div className="space-y-2">
      {value && meta ? (
        // Uploaded state
        <div className="flex items-center gap-3 p-3 rounded-lg border border-emerald-200 bg-emerald-50">
          <FileText size={18} className="text-emerald-600 flex-shrink-0" />
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-slate-800 truncate">{meta.fileName}</p>
            <p className="text-xs text-slate-500">{formatBytes(meta.fileSize)} · {meta.contentType}</p>
          </div>
          <CheckCircle2 size={16} className="text-emerald-500 flex-shrink-0" />
          {!disabled && (
            <button
              type="button"
              onClick={handleRemove}
              className="p-1 rounded-md text-slate-400 hover:text-red-500 hover:bg-red-50 transition-colors flex-shrink-0"
              aria-label="Remove file"
            >
              <X size={14} />
            </button>
          )}
        </div>
      ) : (
        // Drop zone / upload area
        <div
          onDragOver={(e) => { e.preventDefault(); if (!disabled) setIsDragOver(true); }}
          onDragLeave={() => setIsDragOver(false)}
          onDrop={handleDrop}
          onClick={() => !disabled && !uploading && inputRef.current?.click()}
          className={`flex flex-col items-center justify-center gap-2 p-5 rounded-lg border-2 border-dashed transition-all ${
            disabled
              ? 'border-slate-200 bg-slate-50 cursor-not-allowed opacity-60'
              : isDragOver
              ? 'border-blue-400 bg-blue-50 cursor-copy'
              : 'border-slate-300 bg-white cursor-pointer hover:border-blue-400 hover:bg-blue-50/30'
          }`}
        >
          {uploading ? (
            <>
              <Loader2 size={24} className="animate-spin text-blue-500" />
              <p className="text-sm text-slate-500">Uploading…</p>
            </>
          ) : (
            <>
              <Upload size={24} className={isDragOver ? 'text-blue-500' : 'text-slate-400'} />
              <p className="text-sm font-medium text-slate-600">
                {isDragOver ? 'Drop to upload' : 'Click to browse or drag & drop'}
              </p>
              <p className="text-xs text-slate-400">
                {allowedExtensions?.length
                  ? `${allowedExtensions.map(e => e.toUpperCase()).join(', ')} · max ${maxSizeMb} MB`
                  : `Max ${maxSizeMb} MB`}
              </p>
            </>
          )}
          <input
            ref={inputRef}
            type="file"
            className="hidden"
            onChange={handleInputChange}
            disabled={disabled || uploading}
            accept={allowedExtensions?.map(e => `.${e}`).join(',') || undefined}
            aria-label={label}
            required={required && !value}
          />
        </div>
      )}

      {error && (
        <div className="flex items-center gap-1.5 text-xs text-red-600">
          <AlertCircle size={12} />
          {error}
        </div>
      )}
    </div>
  );
};
