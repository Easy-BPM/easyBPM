import { FormDefinition } from '../types';

export interface FormExportData {
  version: string;
  exportedAt: string;
  form: FormDefinition;
}

const fieldTypeFromSchema = (property: any): FormDefinition['tabs'][number]['fields'][number]['type'] => {
  if (property.format === 'textarea') return 'text';
  if (property.format === 'date') return 'date';
  if (property.format === 'fileUpload') return 'fileUpload';
  if (property.format === 'fileDownload') return 'fileDownload';
  if (property.format === 'pdfViewer') return 'pdfViewer';
  if (property.enum?.length) return 'select';
  if (property.type === 'number' || property.type === 'integer') return 'number';
  if (property.type === 'boolean') return 'boolean';
  return 'string';
};

const formFromBackendSchema = (data: any): FormDefinition | null => {
  const schema = data.schema && typeof data.schema === 'object' ? data.schema : null;
  if (!schema?.properties || typeof schema.properties !== 'object') return null;

  const requiredFields = Array.isArray(schema.required) ? schema.required : [];
  const fields = Object.entries(schema.properties).map(([name, rawProperty], index) => {
    const property = rawProperty as any;
    return {
      id: `field_${name || index}`,
      name,
      title: property.title || name,
      type: fieldTypeFromSchema(property),
      required: requiredFields.includes(name),
      readOnly: Boolean(property.readOnly),
      options: Array.isArray(property.enum) ? property.enum.map(String) : undefined,
      minLength: typeof property.minLength === 'number' ? property.minLength : undefined,
      maxLength: typeof property.maxLength === 'number' ? property.maxLength : undefined,
      pattern: typeof property.pattern === 'string' ? property.pattern : undefined,
      minimum: typeof property.minimum === 'number' ? property.minimum : undefined,
      maximum: typeof property.maximum === 'number' ? property.maximum : undefined,
      multipleOf: typeof property.multipleOf === 'number' ? property.multipleOf : undefined,
      allowedExtensions: Array.isArray(property.allowedExtensions) ? property.allowedExtensions : undefined,
      maxSizeMb: property.maxSizeMb
    };
  });

  const formKey = String(data.formId || data.id || `form_${Date.now()}`);
  return {
    id: formKey,
    formKey,
    name: String(data.name || schema.title || formKey),
    tabs: [
      {
        id: `tab_${Date.now()}`,
        name: 'General',
        fields
      }
    ]
  };
};

const normalizeImportedForm = (data: any): FormDefinition | null => {
  const candidate = data.form || formFromBackendSchema(data);
  if (!candidate) return null;

  const flatFields = Array.isArray(candidate.fields) ? candidate.fields : [];
  const tabs = Array.isArray(candidate.tabs) && candidate.tabs.length > 0
    ? candidate.tabs
    : [{ id: `tab_${Date.now()}`, name: 'General', fields: flatFields }];

  const formKey = String(candidate.formKey || candidate.id || `form_${Date.now()}`);
  return {
    ...candidate,
    id: String(candidate.id || formKey),
    formKey,
    name: String(candidate.name || formKey),
    tabs: tabs.map((tab, tabIndex) => ({
      id: String(tab.id || `tab_${tabIndex + 1}`),
      name: String(tab.name || `Tab ${tabIndex + 1}`),
      fields: Array.isArray(tab.fields) ? tab.fields.map((field, fieldIndex) => ({
        id: String(field.id || `field_${tabIndex + 1}_${fieldIndex + 1}`),
        name: String(field.name || `field_${fieldIndex + 1}`),
        title: String(field.title || field.name || `Field ${fieldIndex + 1}`),
        type: field.type || 'string',
        required: Boolean(field.required),
        readOnly: Boolean(field.readOnly),
        options: field.options,
        defaultValue: field.defaultValue,
        minLength: typeof field.minLength === 'number' ? field.minLength : undefined,
        maxLength: typeof field.maxLength === 'number' ? field.maxLength : undefined,
        pattern: typeof field.pattern === 'string' ? field.pattern : undefined,
        minimum: typeof field.minimum === 'number' ? field.minimum : undefined,
        maximum: typeof field.maximum === 'number' ? field.maximum : undefined,
        multipleOf: typeof field.multipleOf === 'number' ? field.multipleOf : undefined,
        allowedExtensions: field.allowedExtensions,
        maxSizeMb: field.maxSizeMb
      })) : []
    }))
  };
};

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
    const form = normalizeImportedForm(data);
    if (!form) {
      return { success: false, error: 'Invalid form data: missing form object' };
    }

    // Validate required fields
    if (!form.formKey || typeof form.formKey !== 'string') {
      return { success: false, error: 'Invalid form: missing or invalid formKey' };
    }

    if (!form.name || typeof form.name !== 'string') {
      return { success: false, error: 'Invalid form: missing or invalid name' };
    }

    if (!Array.isArray(form.tabs)) {
      return { success: false, error: 'Invalid form: tabs must be an array' };
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
      if (field.minLength !== undefined) prop.minLength = field.minLength;
      if (field.maxLength !== undefined) prop.maxLength = field.maxLength;
      if (field.pattern) prop.pattern = field.pattern;
      if (field.minimum !== undefined) prop.minimum = field.minimum;
      if (field.maximum !== undefined) prop.maximum = field.maximum;
      if (field.multipleOf !== undefined) prop.multipleOf = field.multipleOf;

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
