import React, { useRef } from 'react';
import { Upload, Download, Trash2, Copy, FileText, Plus, X } from 'lucide-react';
import { toast } from 'sonner';
import { FormDefinition } from '../types';
import { 
  downloadForm, 
  downloadFormBundle, 
  parseFormFile, 
  importForm,
  getFormDisplayName 
} from '../utils/formUtils';

interface FormLibraryProps {
  forms: Map<string, FormDefinition>;
  onAddForm: (form: FormDefinition) => void;
  onRemoveForm: (formKey: string) => void;
  onSelectForm: (form: FormDefinition) => void;
  selectedFormKey?: string;
}

export const FormLibrary: React.FC<FormLibraryProps> = ({
  forms,
  onAddForm,
  onRemoveForm,
  onSelectForm,
  selectedFormKey
}) => {
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleImportForm = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    try {
      const result = await parseFormFile(file);
      if (!result.success || !result.data) {
        toast.error(result.error || 'Failed to parse form file');
        return;
      }

      const validationResult = importForm(result.data);
      if (!validationResult.success || !validationResult.form) {
        toast.error(validationResult.error || 'Invalid form data');
        return;
      }

      onAddForm(validationResult.form);
      toast.success(`Form "${getFormDisplayName(validationResult.form)}" imported successfully`);
    } catch (error) {
      toast.error(`Import failed: ${error instanceof Error ? error.message : 'Unknown error'}`);
    }

    // Reset file input
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handleExportForm = (form: FormDefinition) => {
    try {
      downloadForm(form);
      toast.success(`Form "${getFormDisplayName(form)}" exported`);
    } catch (error) {
      toast.error('Export failed');
    }
  };

  const handleExportAllForms = () => {
    if (forms.size === 0) {
      toast.error('No forms to export');
      return;
    }

    try {
      const formsList = Array.from(forms.values());
      downloadFormBundle(formsList);
      toast.success(`${formsList.length} form(s) exported`);
    } catch (error) {
      toast.error('Export failed');
    }
  };

  const handleDuplicateForm = (form: FormDefinition) => {
    const newFormKey = `${form.formKey}_copy_${Date.now()}`;
    const newForm: FormDefinition = {
      ...form,
      formKey: newFormKey,
      name: `${form.name} (Copy)`
    };

    onAddForm(newForm);
    toast.success(`Form duplicated as "${newForm.name}"`);
  };

  const formsList = Array.from(forms.values());

  return (
    <div className="flex flex-col h-full bg-white">
      {/* Header */}
      <div className="border-b border-slate-200 p-4 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <FileText className="w-5 h-5 text-slate-700" />
          <h2 className="text-lg font-semibold text-slate-900">Forms Library</h2>
          <span className="px-2 py-1 bg-slate-100 text-slate-700 text-xs font-semibold rounded">
            {formsList.length}
          </span>
        </div>
      </div>

      {/* Actions */}
      <div className="border-b border-slate-200 p-3 flex gap-2">
        <button
          onClick={() => fileInputRef.current?.click()}
          className="flex-1 flex items-center justify-center gap-2 px-3 py-2 bg-blue-50 hover:bg-blue-100 text-blue-700 font-medium rounded-lg transition-colors text-sm"
          title="Import form from JSON file"
        >
          <Upload className="w-4 h-4" />
          Import
        </button>
        <button
          onClick={handleExportAllForms}
          disabled={formsList.length === 0}
          className="flex-1 flex items-center justify-center gap-2 px-3 py-2 bg-green-50 hover:bg-green-100 disabled:bg-slate-50 disabled:text-slate-400 text-green-700 disabled:cursor-not-allowed font-medium rounded-lg transition-colors text-sm"
          title="Export all forms to JSON"
        >
          <Download className="w-4 h-4" />
          Export All
        </button>
      </div>

      {/* Forms List */}
      <div className="flex-1 overflow-y-auto">
        {formsList.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-slate-400 p-4">
            <FileText className="w-12 h-12 mb-2 opacity-50" />
            <p className="text-center text-sm">No forms created yet</p>
            <p className="text-center text-xs mt-1">Create a form in the Form Modeler to add it here</p>
          </div>
        ) : (
          <div className="divide-y divide-slate-200">
            {formsList.map((form) => (
              <div
                key={form.formKey}
                className={`p-3 hover:bg-slate-50 transition-colors cursor-pointer ${
                  selectedFormKey === form.formKey ? 'bg-blue-50 border-l-4 border-l-blue-500' : ''
                }`}
                onClick={() => onSelectForm(form)}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="flex-1 min-w-0">
                    <h3 className="font-semibold text-sm text-slate-900 truncate">
                      {getFormDisplayName(form)}
                    </h3>
                    <p className="text-xs text-slate-500 truncate">
                      Key: <code className="bg-slate-100 px-1.5 py-0.5 rounded">{form.formKey}</code>
                    </p>
                    <p className="text-xs text-slate-500 mt-1">
                      {form.fields?.length || 0} fields
                      {form.tabs && form.tabs.length > 1 && ` • ${form.tabs.length} tabs`}
                    </p>
                  </div>

                  {/* Actions */}
                  <div className="flex gap-1 flex-shrink-0">
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        handleExportForm(form);
                      }}
                      className="p-2 hover:bg-blue-100 text-blue-600 rounded transition-colors"
                      title="Export this form"
                    >
                      <Download className="w-4 h-4" />
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        handleDuplicateForm(form);
                      }}
                      className="p-2 hover:bg-slate-200 text-slate-600 rounded transition-colors"
                      title="Duplicate this form"
                    >
                      <Copy className="w-4 h-4" />
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        if (confirm(`Delete form "${getFormDisplayName(form)}"?`)) {
                          onRemoveForm(form.formKey);
                          toast.success('Form deleted');
                        }
                      }}
                      className="p-2 hover:bg-red-100 text-red-600 rounded transition-colors"
                      title="Delete this form"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Hidden file input */}
      <input
        ref={fileInputRef}
        type="file"
        accept=".json"
        onChange={handleImportForm}
        className="hidden"
        multiple={false}
      />
    </div>
  );
};
