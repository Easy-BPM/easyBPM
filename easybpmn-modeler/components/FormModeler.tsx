import React, { useState, useEffect } from 'react';
import { 
  Plus, Trash2, GripVertical, Settings, Eye, Code, 
  ChevronRight, ChevronDown, Layout, Type, Hash, 
  ToggleLeft, List, Calendar, AlignLeft, CheckSquare,
  MoreVertical, X, Layers, Send, Globe, Copy, Check
} from 'lucide-react';
import { toast } from 'sonner';
import { 
  DndContext, 
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  DragOverlay,
  defaultDropAnimationSideEffects,
  DragStartEvent,
  DragOverEvent,
  DragEndEvent
} from '@dnd-kit/core';
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
  useSortable
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { FormDefinition, FormField, FormTab } from '../types';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:8080';
const FORM_KEY_PATTERN = /^[A-Za-z][A-Za-z0-9_-]*$/;

const FIELD_TYPES = [
  { type: 'string', label: 'Short Text', icon: <Type className="w-4 h-4" /> },
  { type: 'text', label: 'Long Text', icon: <AlignLeft className="w-4 h-4" /> },
  { type: 'number', label: 'Number', icon: <Hash className="w-4 h-4" /> },
  { type: 'boolean', label: 'Checkbox', icon: <CheckSquare className="w-4 h-4" /> },
  { type: 'radio', label: 'Radio Group', icon: <List className="w-4 h-4" /> },
  { type: 'select', label: 'Dropdown', icon: <ChevronDown className="w-4 h-4" /> },
  { type: 'date', label: 'Date Picker', icon: <Calendar className="w-4 h-4" /> },
];

// Sortable Field Item Component (Preview Style)
const SortableField: React.FC<{
  field: FormField;
  isSelected: boolean;
  onSelect: () => void;
}> = ({ field, isSelected, onSelect }) => {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging
  } = useSortable({ id: field.id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
    zIndex: isDragging ? 100 : 1
  };

  return (
    <div 
      ref={setNodeRef} 
      style={style}
      onClick={(e) => { e.stopPropagation(); onSelect(); }}
      className={`relative group p-4 rounded-lg border-2 transition-all cursor-pointer ${
        isSelected 
          ? 'border-blue-500 bg-blue-50/30 ring-2 ring-blue-100' 
          : 'border-transparent hover:border-slate-200 hover:bg-slate-50/50'
      }`}
    >
      <div 
        {...attributes} 
        {...listeners} 
        className="absolute -left-3 top-1/2 -translate-y-1/2 opacity-0 group-hover:opacity-100 cursor-grab active:cursor-grabbing p-1 bg-white border border-slate-200 rounded shadow-sm text-slate-400 z-10"
      >
        <GripVertical className="w-3 h-3" />
      </div>

      <div className="space-y-1.5">
        <label className="block text-sm font-semibold text-slate-700">
          {field.title || 'Untitled Field'} {field.required && <span className="text-red-500">*</span>}
        </label>
        
        {field.type === 'text' ? (
          <textarea 
            disabled 
            className="w-full px-3 py-2 border border-slate-300 rounded-md bg-white/50 min-h-[80px] pointer-events-none" 
            placeholder={field.title} 
          />
        ) : field.type === 'select' ? (
          <div className="relative">
            <div className="w-full px-3 py-2 border border-slate-300 rounded-md bg-white/50 flex items-center justify-between text-slate-400">
              <span>{field.options?.[0] || 'Select option...'}</span>
              <ChevronDown className="w-4 h-4" />
            </div>
          </div>
        ) : field.type === 'radio' ? (
          <div className="space-y-2">
            {(field.options?.length ? field.options : ['Option 1', 'Option 2']).map((opt, i) => (
              <div key={i} className="flex items-center space-x-2">
                <div className="w-4 h-4 rounded-full border border-slate-300 bg-white" />
                <span className="text-sm text-slate-600">{opt}</span>
              </div>
            ))}
          </div>
        ) : field.type === 'boolean' ? (
          <div className="flex items-center space-x-2">
            <div className="w-4 h-4 rounded border border-slate-300 bg-white" />
            <span className="text-sm text-slate-600">Yes / No</span>
          </div>
        ) : (
          <div className="w-full px-3 py-2 border border-slate-300 rounded-md bg-white/50 text-slate-400">
            {field.type === 'date' ? 'YYYY-MM-DD' : (field.type === 'number' ? '0.00' : field.title)}
          </div>
        )}
      </div>

      {isSelected && (
        <div className="absolute -right-2 -top-2 bg-blue-500 text-white p-1 rounded-full shadow-lg">
          <Settings className="w-3 h-3" />
        </div>
      )}
    </div>
  );
};

export const FormModeler: React.FC = () => {
  const [form, setForm] = useState<FormDefinition>(() => {
    const saved = localStorage.getItem('current_form');
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch (e) {
        console.error('Failed to parse saved form', e);
      }
    }
    return {
      id: `form_${Date.now()}`,
      name: 'New Form',
      tabs: [{ id: `tab_${Date.now()}`, name: 'General', fields: [] }]
    };
  });

  useEffect(() => {
    localStorage.setItem('current_form', JSON.stringify(form));
  }, [form]);

  const [activeTabId, setActiveTabId] = useState<string | null>(form.tabs[0].id);
  const [selectedFieldId, setSelectedFieldId] = useState<string | null>(null);
  const [showPreview, setShowPreview] = useState(false);
  const [showSchema, setShowSchema] = useState(false);
  const [isDeploying, setIsDeploying] = useState(false);
  const [activeDragId, setActiveDragId] = useState<string | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  );

  const activeTab = form.tabs.find(t => t.id === activeTabId);
  const selectedField = activeTab?.fields.find(f => f.id === selectedFieldId);

  const handleUpdateForm = (data: Partial<FormDefinition>) => {
    setForm(prev => ({ ...prev, ...data }));
  };

  const handleAddTab = () => {
    const newTab: FormTab = {
      id: `tab_${Date.now()}`,
      name: `Tab ${form.tabs.length + 1}`,
      fields: []
    };
    handleUpdateForm({ tabs: [...form.tabs, newTab] });
    setActiveTabId(newTab.id);
  };

  const handleDeleteTab = (tabId: string) => {
    if (form.tabs.length <= 1) return;
    const newTabs = form.tabs.filter(t => t.id !== tabId);
    handleUpdateForm({ tabs: newTabs });
    if (activeTabId === tabId) {
      setActiveTabId(newTabs[0].id);
    }
  };

  const handleUpdateTab = (tabId: string, data: Partial<FormTab>) => {
    handleUpdateForm({
      tabs: form.tabs.map(t => t.id === tabId ? { ...t, ...data } : t)
    });
  };

  const handleAddField = (type: string) => {
    if (!activeTab) return;
    const newField: FormField = {
      id: `field_${Date.now()}`,
      name: `field_${activeTab.fields.length + 1}`,
      title: `New ${type.charAt(0).toUpperCase() + type.slice(1)} Field`,
      type: type as any,
      required: false,
      readOnly: false
    };
    handleUpdateTab(activeTab.id, { fields: [...activeTab.fields, newField] });
    setSelectedFieldId(newField.id);
  };

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || !activeTab) return;

    if (active.id !== over.id) {
      const oldIndex = activeTab.fields.findIndex(f => f.id === active.id);
      const newIndex = activeTab.fields.findIndex(f => f.id === over.id);
      
      const newFields = arrayMove<FormField>(activeTab.fields, oldIndex, newIndex);
      handleUpdateTab(activeTab.id, { fields: newFields });
    }
    setActiveDragId(null);
  };

  const validateForm = (formDef: FormDefinition) => {
    const trimmedKey = formDef.id.trim();
    const trimmedName = formDef.name.trim();

    if (!trimmedName) {
      return 'Form name is required.';
    }

    if (!trimmedKey) {
      return 'Form Id is required.';
    }

    if (!FORM_KEY_PATTERN.test(trimmedKey)) {
      return 'Form Id must start with a letter and contain only letters, numbers, hyphens, or underscores.';
    }

    const fieldNames = formDef.tabs.flatMap(tab => tab.fields.map(field => field.name.trim()));
    if (fieldNames.some(name => name.length === 0)) {
      return 'All form fields must have a variable name.';
    }

    const duplicateFieldNames = fieldNames.filter((name, index) => fieldNames.indexOf(name) !== index);
    if (duplicateFieldNames.length > 0) {
      return `Duplicate field variable name detected: ${duplicateFieldNames[0]}.`;
    }

    return null;
  };

  const generateJsonSchema = (formDef: FormDefinition) => {
    const properties: any = {};
    const required: string[] = [];

    formDef.tabs.forEach(tab => {
      tab.fields.forEach(field => {
        const prop: any = {
          title: field.title,
          type: field.type === 'number' ? 'number' : (field.type === 'boolean' ? 'boolean' : 'string'),
          readOnly: field.readOnly
        };
        if (field.type === 'text') prop.format = 'textarea';
        if (field.type === 'date') prop.format = 'date';
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
        required
      }
    };
  };

  const handleDeploy = async () => {
    const validationError = validateForm(form);
    if (validationError) {
      toast.error(validationError);
      return;
    }

    setIsDeploying(true);
    const schema = generateJsonSchema(form);
    
    try {
      const response = await fetch(`${API_BASE_URL}/forms`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(schema),
      });

      if (response.ok) {
        toast.success(`Form "${form.name}" deployed successfully!`);
      } else {
        const errorData = await response.json().catch(() => ({}));
        toast.error(`Deployment failed: ${errorData.message || response.statusText}`);
      }
    } catch (error) {
      console.error('Deployment error:', error);
      toast.error(`Deployment failed: Could not connect to the API. Make sure your local server is running at ${API_BASE_URL}`);
    } finally {
      setIsDeploying(false);
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    toast.success('Schema copied to clipboard!');
  };

  return (
    <div className="flex flex-1 h-full overflow-hidden bg-slate-50">
      {/* Field Palette */}
      <div className="w-48 bg-white border-r border-slate-200 flex flex-col" style={{ boxShadow: '1px 0 3px 0 rgba(0,0,0,0.04)' }}>
        <div className="px-4 py-3.5 border-b border-slate-200">
          <h2 className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Field Types</h2>
        </div>
        <div className="p-3 space-y-1.5 overflow-y-auto flex-1">
          {FIELD_TYPES.map(ft => (
            <button
              key={ft.type}
              onClick={() => handleAddField(ft.type)}
              className="w-full flex items-center space-x-2.5 px-2.5 py-2 bg-slate-50 border border-slate-200 rounded-lg text-slate-600 hover:border-blue-300 hover:bg-blue-50/40 hover:text-blue-700 transition-all text-left group"
            >
              <span className="p-1 bg-white rounded shadow-sm border border-slate-100 group-hover:border-blue-200 transition-colors">{ft.icon}</span>
              <span className="text-xs font-medium">{ft.label}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Main Editor */}
      <div className="flex-1 flex flex-col overflow-hidden relative">
        <div className="h-14 bg-white border-b border-slate-200 flex items-center justify-between px-6">
          <div className="flex items-center space-x-6">
            <div className="flex flex-col">
              <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Form Name</label>
              <input 
                value={form.name}
                onChange={(e) => handleUpdateForm({ name: e.target.value })}
                className="text-sm font-semibold text-slate-800 bg-transparent border-none focus:ring-0 p-0 w-48"
                placeholder="Form Name"
              />
            </div>
            <div className="h-8 w-px bg-slate-200" />
            <div className="flex flex-col">
              <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Form Key (ID)</label>
              <input 
                value={form.id}
                onChange={(e) => handleUpdateForm({ id: e.target.value.replace(/\s+/g, '') })}
                className="text-sm font-mono text-blue-600 bg-transparent border-none focus:ring-0 p-0 w-48"
                placeholder="formApproval"
              />
            </div>
          </div>
          <div className="flex items-center space-x-2">
            <button 
              onClick={() => setShowPreview(!showPreview)}
              className={`flex items-center space-x-2 px-3 py-1.5 rounded-md text-sm font-medium transition-colors ${
                showPreview ? 'bg-blue-50 text-blue-700 border border-blue-200' : 'bg-slate-100 text-slate-600 hover:bg-slate-200 border border-transparent'
              }`}
            >
              {showPreview ? <Settings className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              <span>{showPreview ? 'Edit Fields' : 'Preview Form'}</span>
            </button>
            <button 
              onClick={() => setShowSchema(true)}
              className="flex items-center space-x-2 px-3 py-1.5 bg-slate-100 text-slate-600 rounded-md text-sm font-medium hover:bg-slate-200 transition-colors border border-transparent"
            >
              <Code className="w-4 h-4" />
              <span>Schema</span>
            </button>
            <button 
              onClick={handleDeploy}
              disabled={isDeploying}
              className={`flex items-center space-x-2 px-4 py-1.5 rounded-md text-sm font-semibold transition-all disabled:opacity-50 disabled:cursor-not-allowed ${
                isDeploying ? 'bg-blue-400 text-white' : 'bg-gradient-to-r from-blue-500 to-indigo-600 hover:from-blue-600 hover:to-indigo-700 text-white shadow-sm shadow-blue-200'
              }`}
            >
              {isDeploying ? (
                <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
              ) : (
                <Send className="w-4 h-4" />
              )}
              <span>{isDeploying ? 'Deploying...' : 'Deploy Form'}</span>
            </button>
          </div>
        </div>

        {/* Tabs Bar */}
        <div className="bg-white border-b border-slate-200 flex items-center px-6 overflow-x-auto scrollbar-hide">
          {form.tabs.map(tab => (
            <div 
              key={tab.id}
              className="flex items-center group"
            >
              <button
                onClick={() => setActiveTabId(tab.id)}
                className={`h-12 px-4 text-sm font-medium border-b-2 transition-all flex items-center gap-2 ${
                  activeTabId === tab.id 
                    ? 'border-blue-600 text-blue-600' 
                    : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300'
                }`}
              >
                <input 
                  value={tab.name}
                  onChange={(e) => handleUpdateTab(tab.id, { name: e.target.value })}
                  className="bg-transparent border-none focus:ring-0 p-0 w-24 text-center cursor-pointer"
                />
                {form.tabs.length > 1 && (
                  <X 
                    className="w-3 h-3 opacity-0 group-hover:opacity-100 hover:text-red-500 cursor-pointer" 
                    onClick={(e) => { e.stopPropagation(); handleDeleteTab(tab.id); }}
                  />
                )}
              </button>
            </div>
          ))}
          <button 
            onClick={handleAddTab}
            className="p-2 text-slate-400 hover:text-blue-600 transition-colors"
            title="Add Tab"
          >
            <Plus className="w-5 h-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-8 bg-slate-100/50" onClick={() => setSelectedFieldId(null)}>
          {showPreview ? (
            <div className="max-w-2xl mx-auto bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
              {/* Preview Tabs */}
              <div className="flex border-b border-slate-100 bg-slate-50/50">
                {form.tabs.map(tab => (
                  <button 
                    key={tab.id}
                    onClick={() => setActiveTabId(tab.id)}
                    className={`px-6 py-3 text-sm font-semibold transition-all ${
                      activeTabId === tab.id ? 'bg-white text-blue-600 border-b-2 border-blue-600' : 'text-slate-400 hover:text-slate-600'
                    }`}
                  >
                    {tab.name}
                  </button>
                ))}
              </div>
              
              <div className="p-8">
                <h2 className="text-2xl font-bold text-slate-800 mb-6">{activeTab?.name}</h2>
                <div className="space-y-6">
                  {activeTab?.fields.map(field => (
                    <div key={field.id} className="space-y-1.5">
                      <label className="block text-sm font-semibold text-slate-700">
                        {field.title} {field.required && <span className="text-red-500">*</span>}
                      </label>
                      {field.type === 'text' ? (
                        <textarea 
                          className={`w-full px-3 py-2 border border-slate-300 rounded-md focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none min-h-[100px] ${field.readOnly ? 'bg-slate-50 cursor-not-allowed' : ''}`} 
                          placeholder={field.title} 
                          readOnly={field.readOnly}
                        />
                      ) : field.type === 'select' ? (
                        <select 
                          className={`w-full px-3 py-2 border border-slate-300 rounded-md focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none ${field.readOnly ? 'bg-slate-50 cursor-not-allowed' : ''}`}
                          disabled={field.readOnly}
                        >
                          {field.options?.map(opt => <option key={opt} value={opt}>{opt}</option>)}
                        </select>
                      ) : field.type === 'radio' ? (
                        <div className="space-y-2">
                          {field.options?.map(opt => (
                            <label key={opt} className={`flex items-center space-x-2 ${field.readOnly ? 'cursor-not-allowed' : 'cursor-pointer'}`}>
                              <input 
                                type="radio" 
                                name={field.id} 
                                className="text-blue-600 focus:ring-blue-500" 
                                disabled={field.readOnly}
                              />
                              <span className={`text-sm ${field.readOnly ? 'text-slate-400' : 'text-slate-600'}`}>{opt}</span>
                            </label>
                          ))}
                        </div>
                      ) : field.type === 'boolean' ? (
                        <label className={`flex items-center space-x-2 ${field.readOnly ? 'cursor-not-allowed' : 'cursor-pointer'}`}>
                          <input 
                            type="checkbox" 
                            className="rounded text-blue-600 focus:ring-blue-500" 
                            disabled={field.readOnly}
                          />
                          <span className={`text-sm ${field.readOnly ? 'text-slate-400' : 'text-slate-600'}`}>Yes / No</span>
                        </label>
                      ) : (
                        <input 
                          type={field.type === 'number' ? 'number' : (field.type === 'date' ? 'date' : 'text')} 
                          className={`w-full px-3 py-2 border border-slate-300 rounded-md focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none ${field.readOnly ? 'bg-slate-50 cursor-not-allowed' : ''}`} 
                          placeholder={field.title}
                          readOnly={field.readOnly}
                        />
                      )}
                    </div>
                  ))}
                  {activeTab?.fields.length === 0 && (
                    <div className="text-center py-12 text-slate-400 italic">
                      No fields added to this tab yet.
                    </div>
                  )}
                  <div className="pt-4">
                    <button className="w-full bg-blue-600 text-white py-2.5 rounded-md font-bold hover:bg-blue-700 transition-colors">
                      Submit
                    </button>
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <div className="max-w-2xl mx-auto bg-white min-h-full rounded-xl shadow-sm border border-slate-200 p-8">
              <div className="mb-8 pb-4 border-b border-slate-100">
                <h2 className="text-2xl font-bold text-slate-800">{activeTab?.name || 'Form Canvas'}</h2>
                <p className="text-sm text-slate-400">Drag fields here to build your form</p>
                <p className="text-xs text-slate-400 mt-2">Form key is the stable identifier used for versioning and human-task attachment.</p>
              </div>

              <DndContext 
                sensors={sensors}
                collisionDetection={closestCenter}
                onDragStart={(e) => setActiveDragId(e.active.id as string)}
                onDragEnd={handleDragEnd}
              >
                <SortableContext 
                  items={activeTab?.fields.map(f => f.id) || []}
                  strategy={verticalListSortingStrategy}
                >
                  <div className="space-y-2">
                    {activeTab?.fields.map((field) => (
                      <SortableField 
                        key={field.id} 
                        field={field} 
                        isSelected={selectedFieldId === field.id}
                        onSelect={() => setSelectedFieldId(field.id)}
                      />
                    ))}
                    {(!activeTab || activeTab.fields.length === 0) && (
                      <div className="text-center py-20 border-2 border-dashed border-slate-200 rounded-xl text-slate-400">
                        <div className="bg-slate-100 w-12 h-12 rounded-full flex items-center justify-center mx-auto mb-4">
                          <Plus className="w-6 h-6" />
                        </div>
                        <p className="text-sm font-medium">Click fields from the palette to add to this tab</p>
                      </div>
                    )}
                  </div>
                </SortableContext>
              </DndContext>
            </div>
          )}
        </div>

        {/* Schema Modal */}
        {showSchema && (
          <div className="absolute inset-0 z-50 flex items-center justify-center p-8 bg-slate-900/40 backdrop-blur-sm">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-3xl flex flex-col max-h-full overflow-hidden">
              <div className="p-4 border-b border-slate-200 flex items-center justify-between">
                <h3 className="font-bold text-slate-800 flex items-center gap-2">
                  <Code className="w-4 h-4 text-blue-600" />
                  JSON Schema
                </h3>
                <button 
                  onClick={() => setShowSchema(false)}
                  className="p-1 hover:bg-slate-100 rounded text-slate-400"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
              <div className="flex-1 overflow-y-auto p-6 bg-slate-900">
                <pre className="text-blue-300 font-mono text-sm">
                  {JSON.stringify(generateJsonSchema(form), null, 2)}
                </pre>
              </div>
              <div className="p-4 border-t border-slate-200 flex justify-end space-x-3">
                <button 
                  onClick={() => copyToClipboard(JSON.stringify(generateJsonSchema(form), null, 2))}
                  className="flex items-center space-x-2 px-4 py-2 bg-slate-100 text-slate-600 rounded-md text-sm font-medium hover:bg-slate-200 transition-colors"
                >
                  <Copy className="w-4 h-4" />
                  <span>Copy to Clipboard</span>
                </button>
                <button 
                  onClick={() => setShowSchema(false)}
                  className="px-4 py-2 bg-blue-600 text-white rounded-md text-sm font-medium hover:bg-blue-700 transition-colors"
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Properties Panel */}
      <div className="w-80 bg-white border-l border-slate-200 flex flex-col">
        <div className="p-4 border-b border-slate-200">
          <h2 className="text-xs font-bold text-slate-400 uppercase tracking-widest">Properties</h2>
        </div>
        <div className="flex-1 overflow-y-auto p-4">
          {selectedField ? (
            <div className="space-y-6">
              <div className="space-y-4">
                <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
                  <Settings className="w-4 h-4 text-blue-600" />
                  Field Settings
                </h3>
                
                <div className="space-y-1.5">
                  <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Field Title</label>
                  <input 
                    value={selectedField.title}
                    onChange={(e) => handleUpdateField(selectedField.id, { title: e.target.value })}
                    className="w-full px-3 py-2 border border-slate-200 rounded-md text-sm focus:ring-2 focus:ring-blue-500 outline-none"
                    placeholder="e.g. Full Name"
                  />
                </div>

                <div className="space-y-1.5">
                  <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Variable Name (ID)</label>
                  <input 
                    value={selectedField.name}
                    onChange={(e) => handleUpdateField(selectedField.id, { name: e.target.value })}
                    className="w-full px-3 py-2 border border-slate-200 rounded-md text-sm font-mono focus:ring-2 focus:ring-blue-500 outline-none"
                    placeholder="e.g. fullName"
                  />
                </div>

                <div className="space-y-1.5">
                  <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Field Type</label>
                  <select 
                    value={selectedField.type}
                    onChange={(e) => handleUpdateField(selectedField.id, { type: e.target.value as any })}
                    className="w-full px-3 py-2 border border-slate-200 rounded-md text-sm focus:ring-2 focus:ring-blue-500 outline-none bg-white"
                  >
                    {FIELD_TYPES.map(ft => <option key={ft.type} value={ft.type}>{ft.label}</option>)}
                  </select>
                </div>

                <div className="flex items-center justify-between p-3 bg-slate-50 rounded-lg border border-slate-100">
                  <span className="text-xs font-bold text-slate-500 uppercase">Read Only</span>
                  <button 
                    onClick={() => {
                      const newReadOnly = !selectedField.readOnly;
                      handleUpdateField(selectedField.id, { 
                        readOnly: newReadOnly,
                        required: newReadOnly ? false : selectedField.required
                      });
                    }}
                    className={`w-10 h-5 rounded-full transition-colors relative ${selectedField.readOnly ? 'bg-blue-600' : 'bg-slate-300'}`}
                  >
                    <div className={`absolute top-1 w-3 h-3 bg-white rounded-full transition-all ${selectedField.readOnly ? 'left-6' : 'left-1'}`} />
                  </button>
                </div>

                <div className={`flex items-center justify-between p-3 bg-slate-50 rounded-lg border border-slate-100 ${selectedField.readOnly ? 'opacity-50' : ''}`}>
                  <span className="text-xs font-bold text-slate-500 uppercase">Required Field</span>
                  <button 
                    disabled={selectedField.readOnly}
                    onClick={() => handleUpdateField(selectedField.id, { required: !selectedField.required })}
                    className={`w-10 h-5 rounded-full transition-colors relative ${selectedField.required ? 'bg-blue-600' : 'bg-slate-300'} ${selectedField.readOnly ? 'cursor-not-allowed' : ''}`}
                  >
                    <div className={`absolute top-1 w-3 h-3 bg-white rounded-full transition-all ${selectedField.required ? 'left-6' : 'left-1'}`} />
                  </button>
                </div>
              </div>

              {(selectedField.type === 'radio' || selectedField.type === 'select') && (
                <div className="space-y-4 pt-4 border-t border-slate-100">
                  <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
                    <List className="w-4 h-4 text-blue-600" />
                    Options
                  </h3>
                  <div className="space-y-1.5">
                    <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Options (comma separated)</label>
                    <textarea 
                      value={selectedField.options?.join(', ') || ''}
                      onChange={(e) => handleUpdateField(selectedField.id, { options: e.target.value.split(',').map(s => s.trim()).filter(s => s !== '') })}
                      className="w-full px-3 py-2 border border-slate-200 rounded-md text-sm focus:ring-2 focus:ring-blue-500 outline-none min-h-[100px]"
                      placeholder="Option 1, Option 2, Option 3"
                    />
                  </div>
                </div>
              )}

              <div className="pt-6 border-t border-slate-100">
                <button 
                  onClick={() => {
                    handleDeleteField(selectedField.id);
                    setSelectedFieldId(null);
                  }}
                  className="w-full flex items-center justify-center gap-2 px-4 py-2 text-red-600 bg-red-50 hover:bg-red-100 rounded-md text-sm font-bold transition-colors"
                >
                  <Trash2 className="w-4 h-4" />
                  Delete Field
                </button>
              </div>
            </div>
          ) : (
            <div className="h-full flex flex-col items-center justify-center text-center space-y-3 opacity-40">
              <div className="p-4 bg-slate-100 rounded-full">
                <Settings className="w-8 h-8" />
              </div>
              <p className="text-xs font-medium text-slate-500">Select a field on the canvas to edit its properties</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );

  function handleUpdateField(fieldId: string, data: Partial<FormField>) {
    if (!activeTab) return;
    handleUpdateTab(activeTab.id, {
      fields: activeTab.fields.map(f => f.id === fieldId ? { ...f, ...data } : f)
    });
  }

  function handleDeleteField(fieldId: string) {
    if (!activeTab) return;
    handleUpdateTab(activeTab.id, {
      fields: activeTab.fields.filter(f => f.id !== fieldId)
    });
  }
};
