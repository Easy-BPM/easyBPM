import React, { useEffect, useRef, useState } from 'react';
import type { FormDefinition, FormField } from '../types';

type PreviewValue = string | number | boolean;
const isDocument = (field: FormField) => ['fileUpload', 'fileDownload', 'pdfViewer'].includes(field.type);

const validate = (field: FormField, value: PreviewValue): string | undefined => {
  if (isDocument(field)) return;
  const text = String(value ?? '');
  if (field.required && text.trim() === '') return `${field.title} is required.`;
  if (text === '') return;
  if (field.type === 'number') {
    const number = Number(value);
    if (!Number.isFinite(number)) return `${field.title} must be a number.`;
    if (field.minimum !== undefined && number < field.minimum) return `${field.title} must be at least ${field.minimum}.`;
    if (field.maximum !== undefined && number > field.maximum) return `${field.title} must be at most ${field.maximum}.`;
    if (field.multipleOf !== undefined) {
      const ratio = number / field.multipleOf;
      if (field.multipleOf <= 0 || Math.abs(ratio - Math.round(ratio)) > 1e-9) return `${field.title} must be a multiple of ${field.multipleOf}.`;
    }
  } else if (['string', 'text'].includes(field.type)) {
    const length = [...text].length;
    if (field.minLength !== undefined && length < field.minLength) return `${field.title} needs at least ${field.minLength} characters.`;
    if (field.maxLength !== undefined && length > field.maxLength) return `${field.title} allows at most ${field.maxLength} characters.`;
    if (field.pattern) {
      try {
        if (!new RegExp(field.pattern).test(text)) return `${field.title} must match the configured pattern.`;
      } catch { return `${field.title} has an invalid pattern. Update the field settings.`; }
    }
  } else if (['select', 'radio'].includes(field.type)) {
    if (!field.options?.includes(text)) return `${field.title} must be one of the available options.`;
  } else if (field.type === 'date') {
    const date = new Date(text).getTime();
    if (!Number.isFinite(date)) return `${field.title} must be a valid date.`;
    if (field.minDate && date < new Date(field.minDate).getTime()) return `${field.title} must be on or after ${field.minDate}.`;
    if (field.maxDate && date > new Date(field.maxDate).getTime()) return `${field.title} must be on or before ${field.maxDate}.`;
  }
};

export const FormPreview: React.FC<{
  form: FormDefinition;
  activeTabId: string | null;
  onSelectTab: (id: string) => void;
}> = ({ form, activeTabId, onSelectTab }) => {
  const fields = form.tabs.flatMap(tab => tab.fields);
  const [values, setValues] = useState<Record<string, PreviewValue>>(() => Object.fromEntries(fields.map(field => [
    field.id, field.type === 'boolean' ? field.defaultValue === 'true' : field.defaultValue ?? ''
  ])));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [result, setResult] = useState<Record<string, PreviewValue> | null>(null);
  const [focusId, setFocusId] = useState<string | null>(null);
  const formRef = useRef<HTMLFormElement>(null);

  useEffect(() => {
    setValues(previous => Object.fromEntries(form.tabs.flatMap(tab => tab.fields).map(field => [field.id,
      previous[field.id] ?? (field.type === 'boolean' ? field.defaultValue === 'true' : field.defaultValue ?? '')
    ])));
    setErrors({});
    setResult(null);
  }, [form]);

  useEffect(() => {
    if (!focusId) return;
    formRef.current?.querySelector<HTMLElement>(`[id="preview-${CSS.escape(focusId)}"]`)?.focus();
    setFocusId(null);
  }, [focusId, activeTabId]);

  const update = (id: string, value: PreviewValue) => {
    setValues(previous => ({ ...previous, [id]: value }));
    setErrors(previous => { const next = { ...previous }; delete next[id]; return next; });
    setResult(null);
  };
  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    const nextErrors: Record<string, string> = {};
    fields.forEach(field => { const error = validate(field, values[field.id]); if (error) nextErrors[field.id] = error; });
    setErrors(nextErrors);
    setResult(null);
    const first = fields.find(field => nextErrors[field.id]);
    if (first) {
      onSelectTab(form.tabs.find(tab => tab.fields.some(field => field.id === first.id))!.id);
      setFocusId(first.id);
      return;
    }
    setResult(Object.fromEntries(fields.filter(field => !isDocument(field)).map(field => [field.name,
      field.type === 'number' && values[field.id] !== '' ? Number(values[field.id]) : values[field.id]
    ])));
  };

  return (
    <form ref={formRef} noValidate onSubmit={submit} className="max-w-2xl mx-auto bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
      <div className="flex border-b border-slate-100 bg-slate-50/50">
        {form.tabs.map(tab => <button key={tab.id} type="button" onClick={() => onSelectTab(tab.id)}
          className={`px-6 py-3 text-sm font-semibold ${activeTabId === tab.id ? 'text-blue-600 bg-white border-b-2 border-blue-600' : 'text-slate-500'}`}>
          {tab.name}{tab.fields.some(field => errors[field.id]) ? ' (errors)' : ''}
        </button>)}
      </div>
      <p className="px-8 pt-6 text-sm text-slate-500">Test your fields locally. Nothing is sent. File controls are display-only and are not validated.</p>
      {form.tabs.filter(tab => tab.id === activeTabId).map(tab => <div key={tab.id} className="p-8 space-y-6">
        <h2 className="text-2xl font-bold text-slate-800">{tab.name}</h2>
        {tab.fields.map(field => {
          const id = `preview-${field.id}`;
          const shared = { id, required: field.required, 'aria-invalid': !!errors[field.id], 'aria-describedby': errors[field.id] ? `${id}-error` : undefined };
          const className = `w-full px-3 py-2 border rounded-md ${errors[field.id] ? 'border-red-500' : 'border-slate-300'} ${field.readOnly ? 'bg-slate-50' : ''}`;
          return <div key={field.id} className="space-y-1.5">
            <label htmlFor={id} className="block text-sm font-semibold text-slate-700">{field.title} {field.required && <span aria-hidden="true" className="text-red-500">*</span>}</label>
            {field.type === 'text' ? <textarea {...shared} placeholder={field.title} readOnly={field.readOnly} className={className}
              value={String(values[field.id])} onChange={event => update(field.id, event.target.value)} />
            : field.type === 'select' ? <select {...shared} disabled={field.readOnly} className={className} value={String(values[field.id])} onChange={event => update(field.id, event.target.value)}>
              <option value="">Select an option...</option>{field.options?.map(option => <option key={option} value={option}>{option}</option>)}
            </select>
            : field.type === 'radio' ? <div role="radiogroup" aria-label={field.title} aria-required={field.required} aria-describedby={shared['aria-describedby']}>
              {field.options?.map((option, index) => <label key={option} className="flex items-center gap-2"><input id={index === 0 ? id : undefined} type="radio" name={id} required={field.required} disabled={field.readOnly}
                checked={values[field.id] === option} onChange={() => update(field.id, option)} />{option}</label>)}
            </div>
            : field.type === 'boolean' ? <input id={id} type="checkbox" disabled={field.readOnly} checked={values[field.id] === true}
              onChange={event => update(field.id, event.target.checked)} />
            : isDocument(field) ? <div id={id} className="p-4 bg-slate-50 text-slate-500 border rounded-md">{field.type === 'fileUpload' ? 'File upload preview' : field.type === 'fileDownload' ? 'File download preview' : 'PDF preview'}</div>
            : <input {...shared} placeholder={field.title} type={field.type === 'number' ? 'number' : field.type === 'date' ? field.includeTime ? 'datetime-local' : 'date' : 'text'}
              readOnly={field.readOnly} className={className} min={field.type === 'number' ? field.minimum : field.minDate} max={field.type === 'number' ? field.maximum : field.maxDate}
              step={field.type === 'number' ? field.multipleOf ?? 'any' : undefined} value={String(values[field.id])} onChange={event => update(field.id, event.target.value)} />}
            {errors[field.id] && <p id={`${id}-error`} role="alert" className="text-sm text-red-600">{errors[field.id]}</p>}
          </div>;
        })}
        {tab.fields.length === 0 && <p className="text-slate-500">No fields added to this tab yet.</p>}
      </div>)}
      <div className="p-8 pt-0"><button type="submit" className="w-full bg-blue-600 text-white py-2.5 rounded-md font-bold hover:bg-blue-700">Submit</button></div>
      {result && <div className="px-8 pb-8"><p role="status" className="text-green-700">Preview is valid. No data was sent.</p><pre aria-label="Preview values" className="mt-3 text-sm whitespace-pre-wrap">{JSON.stringify(result, null, 2)}</pre></div>}
    </form>
  );
};
