import React, { useEffect } from 'react';
import { JsonSchema, JsonSchemaProperty } from '../types';
import { FileUploadField } from './FileUploadField';
import { FileDownloadField } from './FileDownloadField';
import { PdfViewerField } from './PdfViewerField';

interface DynamicFormProps {
  schema: JsonSchema;
  initialData: Record<string, any>;
  onChange: (data: Record<string, any>) => void;
  disabled?: boolean;
  taskId?: number;
  processInstanceId?: number;
}

const defaultValueForProperty = (prop: JsonSchemaProperty) => {
  if (prop.type === 'boolean') return false;
  return '';
};

const normalizeFormData = (schema: JsonSchema, data: Record<string, any>) => {
  const normalized = { ...(data || {}) };
  Object.entries(schema.properties || {}).forEach(([key, prop]) => {
    if (normalized[key] === undefined || normalized[key] === null) {
      normalized[key] = defaultValueForProperty(prop);
    }
  });
  return normalized;
};

const hasSameValues = (left: Record<string, any>, right: Record<string, any>) => {
  const keys = new Set([...Object.keys(left), ...Object.keys(right)]);
  for (const key of keys) {
    if (left[key] !== right[key]) return false;
  }
  return true;
};

const documentValueToString = (value: any) => {
  if (typeof value === 'string') return value;
  if (value && typeof value === 'object') {
    if (typeof value.id === 'string') return value.id;
    if (typeof value.value === 'string') return value.value;
  }
  return '';
};

export const DynamicForm: React.FC<DynamicFormProps> = ({
  schema,
  initialData,
  onChange,
  disabled,
  taskId,
  processInstanceId
}) => {
  const [formData, setFormData] = React.useState<Record<string, any>>(() => normalizeFormData(schema, initialData || {}));

  useEffect(() => {
    const normalized = normalizeFormData(schema, initialData || {});
    setFormData(normalized);
    if (!hasSameValues(normalized, initialData || {})) {
      onChange(normalized);
    }
  }, [initialData, onChange, schema]);

  const handleChange = (key: string, value: any) => {
    const updated = { ...formData, [key]: value };
    setFormData(updated);
    onChange(updated);
  };

  const properties = schema.properties || {};
  const requiredFields = schema.required || [];

  const renderField = (key: string, prop: JsonSchemaProperty) => {
    const value = formData[key] ?? '';
    const isRequired = requiredFields.includes(key);
    const isReadOnly = disabled || prop.readOnly;

    // ── Document field types ────────────────────────────────────────────────
    if (prop.format === 'fileUpload') {
      return (
        <FileUploadField
          fieldKey={key}
          label={prop.title || key}
          value={documentValueToString(value)}
          onChange={(newVal) => handleChange(key, newVal)}
          disabled={isReadOnly}
          taskId={taskId}
          processInstanceId={processInstanceId}
          allowedExtensions={prop.allowedExtensions}
          maxSizeMb={prop.maxSizeMb ?? 20}
          required={isRequired}
        />
      );
    }

    if (prop.format === 'fileDownload') {
      return (
        <FileDownloadField
          value={documentValueToString(value)}
          label={prop.title || key}
        />
      );
    }

    if (prop.format === 'pdfViewer') {
      return (
        <PdfViewerField
          value={documentValueToString(value)}
          label={prop.title || key}
        />
      );
    }

    // ── Standard field types ────────────────────────────────────────────────
    if (prop.enum) {
      return (
        <select
          className={`w-full rounded-lg border px-3 py-2.5 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all ${
            isReadOnly
              ? 'bg-slate-100 text-slate-500 border-slate-200 cursor-not-allowed'
              : 'bg-white border-slate-300 hover:border-slate-400'
          }`}
          value={value}
          onChange={(e) => handleChange(key, e.target.value)}
          disabled={isReadOnly}
          required={isRequired}
        >
          <option value="">Select option...</option>
          {prop.enum.map((opt) => (
            <option key={opt} value={opt}>
              {opt}
            </option>
          ))}
        </select>
      );
    }

    if (prop.type === 'boolean') {
      return (
        <label
          className={`dynamic-boolean-field flex items-center p-3 rounded-lg border cursor-pointer transition-all ${
            value ? 'bg-blue-50 border-blue-200' : 'bg-white border-slate-200 hover:border-slate-300'
          } ${value ? 'dynamic-boolean-selected' : ''} ${isReadOnly ? 'opacity-70 pointer-events-none' : ''}`}
        >
          <input
            type="checkbox"
            className="w-5 h-5 text-blue-600 rounded focus:ring-blue-500 border-gray-300"
            checked={!!value}
            onChange={(e) => handleChange(key, e.target.checked)}
            disabled={isReadOnly}
          />
          <span className="dynamic-boolean-label ml-3 text-sm text-slate-700 font-medium">
            {prop.description || 'Yes, ' + (prop.title || key)}
          </span>
        </label>
      );
    }

    if (prop.format === 'textarea') {
      return (
        <textarea
          className={`w-full rounded-lg border px-3 py-2.5 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all min-h-[100px] resize-y ${
            isReadOnly
              ? 'bg-slate-100 text-slate-500 border-slate-200'
              : 'bg-white border-slate-300 hover:border-slate-400'
          }`}
          value={value}
          onChange={(e) => handleChange(key, e.target.value)}
          disabled={isReadOnly}
          required={isRequired}
          minLength={prop.minLength}
          maxLength={prop.maxLength}
          placeholder={prop.description || `Enter ${prop.title || key}...`}
        />
      );
    }

    const isNumber = prop.type === 'number' || prop.type === 'integer';
    const isDate = prop.format === 'date';

    return (
      <input
        type={isNumber ? 'number' : isDate ? 'date' : 'text'}
        className={`w-full rounded-lg border px-3 py-2.5 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all ${
          isReadOnly
            ? 'bg-slate-100 text-slate-500 border-slate-200'
            : 'bg-white border-slate-300 hover:border-slate-400'
        }`}
        value={value}
        onChange={(e) => {
          const val = e.target.value;
          if (prop.type === 'number' || prop.type === 'integer') {
            handleChange(key, val === '' ? '' : Number(val));
          } else {
            handleChange(key, val);
          }
        }}
        disabled={isReadOnly}
        required={isRequired}
        minLength={prop.type === 'string' ? prop.minLength : undefined}
        maxLength={prop.type === 'string' ? prop.maxLength : undefined}
        pattern={prop.type === 'string' ? prop.pattern : undefined}
        min={isNumber ? prop.minimum : isDate ? prop.minDate ?? prop.formatMinimum : undefined}
        max={isNumber ? prop.maximum : isDate ? prop.maxDate ?? prop.formatMaximum : undefined}
        step={isNumber ? prop.multipleOf ?? (prop.type === 'integer' ? 1 : undefined) : undefined}
        placeholder={prop.description || `Enter ${prop.title || key}...`}
      />
    );
  };

  return (
    <div className="space-y-6">
      {schema.title && (
        <h3 className="text-xl font-semibold text-slate-800 border-b border-slate-200 pb-3 mb-4">
          {schema.title}
        </h3>
      )}

      <div className="space-y-5">
        {(Object.entries(properties) as [string, JsonSchemaProperty][]).map(([key, prop]) => {
          const isRequired = requiredFields.includes(key);
          return (
            <div key={key} className="flex flex-col group">
              <label className="mb-1.5 text-sm font-medium text-slate-700">
                {prop.title || key} {isRequired && <span className="text-red-500">*</span>}
              </label>
              {renderField(key, prop)}
            </div>
          );
        })}
      </div>
    </div>
  );
};
