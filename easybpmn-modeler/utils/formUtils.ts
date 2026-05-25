import { FormDefinition } from '../types';

export interface FormExportData {
  version: string;
  exportedAt: string;
  form: FormDefinition;
}

/**
 * Export a form as JSON for backup/sharing
 */
export const exportForm = (form: FormDefinition): FormExportData => {
  return {
    version: '1.0.0',
    exportedAt: new Date().toISOString(),
    form
  };
};

/**
 * Export form to JSON string
 */
export const exportFormToString = (form: FormDefinition): string => {
  return JSON.stringify(exportForm(form), null, 2);
};

/**
 * Download form as JSON file
 */
export const downloadForm = (form: FormDefinition, filename?: string): void => {
  const exportData = exportForm(form);
  const json = JSON.stringify(exportData, null, 2);
  const blob = new Blob([json], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename || `form-${form.formKey || 'export'}-${Date.now()}.json`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};

/**
 * Validate and import form from exported data
 */
export const importForm = (data: FormExportData): { success: boolean; form?: FormDefinition; error?: string } => {
  try {
    if (!data.form) {
      return { success: false, error: 'Invalid form data: missing form object' };
    }

    const form = data.form;

    // Validate required fields
    if (!form.formKey || typeof form.formKey !== 'string') {
      return { success: false, error: 'Invalid form: missing or invalid formKey' };
    }

    if (!form.name || typeof form.name !== 'string') {
      return { success: false, error: 'Invalid form: missing or invalid name' };
    }

    if (!Array.isArray(form.fields)) {
      return { success: false, error: 'Invalid form: fields must be an array' };
    }

    // Return validated form
    return {
      success: true,
      form
    };
  } catch (error) {
    return {
      success: false,
      error: `Failed to parse form data: ${error instanceof Error ? error.message : 'Unknown error'}`
    };
  }
};

/**
 * Parse uploaded form file
 */
export const parseFormFile = (file: File): Promise<{ success: boolean; data?: FormExportData; error?: string }> => {
  return new Promise((resolve) => {
    const reader = new FileReader();

    reader.onload = (event) => {
      try {
        const content = event.target?.result as string;
        const data = JSON.parse(content) as FormExportData;
        resolve({ success: true, data });
      } catch (error) {
        resolve({
          success: false,
          error: `Failed to parse file: ${error instanceof Error ? error.message : 'Invalid JSON'}`
        });
      }
    };

    reader.onerror = () => {
      resolve({ success: false, error: 'Failed to read file' });
    };

    reader.readAsText(file);
  });
};

/**
 * Export multiple forms as a bundle
 */
export const exportFormBundle = (forms: FormDefinition[]): string => {
  const bundle = {
    version: '1.0.0',
    exportedAt: new Date().toISOString(),
    forms: forms.map(f => ({ version: '1.0.0', form: f }))
  };
  return JSON.stringify(bundle, null, 2);
};

/**
 * Download form bundle
 */
export const downloadFormBundle = (forms: FormDefinition[], filename: string = `forms-${Date.now()}.json`): void => {
  const json = exportFormBundle(forms);
  const blob = new Blob([json], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};

/**
 * Get display name for form (name or formKey)
 */
export const getFormDisplayName = (form: FormDefinition): string => {
  return form.name || form.formKey || 'Unnamed Form';
};

/**
 * Generate JSON schema from form definition for API deployment
 */
export const generateJsonSchema = (formDef: FormDefinition) => {
  const properties: any = {};
  const required: string[] = [];

  formDef.tabs.forEach(tab => {
    tab.fields.forEach(field => {
      const prop: any = {
        title: field.title,
        type: 'string',
        readOnly: field.readOnly
      };

      // Map internal types to JSON-Schema type + format + extras
      switch (field.type) {
        case 'number':
          prop.type = 'number';
          break;
        case 'boolean':
          prop.type = 'boolean';
          break;
        case 'text':
          prop.format = 'textarea';
          break;
        case 'date':
          prop.format = 'date';
          break;
        case 'fileUpload':
          prop.format = 'fileUpload';
          if (field.allowedExtensions?.length) prop.allowedExtensions = field.allowedExtensions;
          if (field.maxSizeMb) prop.maxSizeMb = field.maxSizeMb;
          break;
        case 'fileDownload':
          prop.format = 'fileDownload';
          break;
        case 'pdfViewer':
          prop.format = 'pdfViewer';
          break;
        default:
          // string, radio, select
          break;
      }

      if (field.options && field.options.length > 0) prop.enum = field.options;

      properties[field.name] = prop;
      if (field.required) required.push(field.name);
    });
  });

  return {
    formId: formDef.id.trim(),
    name: formDef.name.trim(),
    schema: {
      title: formDef.name.trim(),
      type: 'object',
      properties,
      ...(required.length > 0 && { required })
    }
  };
};
