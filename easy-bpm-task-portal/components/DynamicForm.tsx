import React, { useEffect } from 'react';
import { JsonSchema, JsonSchemaProperty } from '../types';

interface DynamicFormProps {
  schema: JsonSchema;
  initialData: Record<string, any>;
  onChange: (data: Record<string, any>) => void;
  disabled?: boolean;
}

export const DynamicForm: React.FC<DynamicFormProps> = ({ schema, initialData, onChange, disabled }) => {
  const [formData, setFormData] = React.useState<Record<string, any>>(initialData || {});

  useEffect(() => {
    setFormData(initialData || {});
  }, [initialData]);

  const handleChange = (key: string, value: any) => {
    const updated = { ...formData, [key]: value };
    setFormData(updated);
    onChange(updated);
  };

  const properties = schema.properties || {};
  const requiredFields = schema.required || [];

  return (
    <div className="space-y-6">
      {/* Top Level Form Title from JSON Schema */}
      {schema.title && (
        <h3 className="text-xl font-semibold text-slate-800 border-b border-slate-200 pb-3 mb-4">
          {schema.title}
        </h3>
      )}
      
      <div className="space-y-5">
        {(Object.entries(properties) as [string, JsonSchemaProperty][]).map(([key, prop]) => {
          const value = formData[key] ?? '';
          const isRequired = requiredFields.includes(key);
          const isReadOnly = disabled;
          
          return (
            <div key={key} className="flex flex-col group">
              {/* Field Label - Uses 'title' from property or falls back to key */}
              <label className="mb-1.5 text-sm font-medium text-slate-700">
                {prop.title || key} {isRequired && <span className="text-red-500">*</span>}
              </label>

              {prop.enum ? (
                 <select
                    className={`w-full rounded-lg border px-3 py-2.5 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all ${
                        isReadOnly ? 'bg-slate-100 text-slate-500 border-slate-200 cursor-not-allowed' : 'bg-white border-slate-300 hover:border-slate-400'
                    }`}
                    value={value}
                    onChange={(e) => handleChange(key, e.target.value)}
                    disabled={isReadOnly}
                  >
                    <option value="">Select option...</option>
                    {prop.enum.map((opt) => (
                      <option key={opt} value={opt}>
                        {opt}
                      </option>
                    ))}
                 </select>
              ) : prop.type === 'boolean' ? (
                <label className={`flex items-center p-3 rounded-lg border cursor-pointer transition-all ${
                    value ? 'bg-blue-50 border-blue-200' : 'bg-white border-slate-200 hover:border-slate-300'
                } ${isReadOnly ? 'opacity-70 pointer-events-none' : ''}`}>
                  <input
                    type="checkbox"
                    className="w-5 h-5 text-blue-600 rounded focus:ring-blue-500 border-gray-300"
                    checked={!!value}
                    onChange={(e) => handleChange(key, e.target.checked)}
                    disabled={isReadOnly}
                  />
                  <span className="ml-3 text-sm text-slate-700 font-medium">
                      {prop.description || "Yes, " + (prop.title || key)}
                  </span>
                </label>
              ) : (
                <input
                  type={prop.type === 'number' || prop.type === 'integer' ? 'number' : 'text'}
                  className={`w-full rounded-lg border px-3 py-2.5 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all ${
                      isReadOnly ? 'bg-slate-100 text-slate-500 border-slate-200' : 'bg-white border-slate-300 hover:border-slate-400'
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
                  placeholder={prop.description || `Enter ${prop.title || key}...`}
                />
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};