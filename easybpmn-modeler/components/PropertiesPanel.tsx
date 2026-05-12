import React, { useState, useEffect } from 'react';
import { BpmnNode, BpmnEdge, ProcessVariable, TaskVariable, ValidationIssue } from '../types';
import { Trash2, Plus, LogIn, LogOut, Layers, Database, Hash, Type, ToggleLeft, Braces, Fingerprint, AlertCircle, FileCode, Mail, Zap, FileText, Code, Info } from 'lucide-react';
import { validateId } from '../utils/validation';

interface PropertiesPanelProps {
  selectedNodeUids: string[];
  nodes: BpmnNode[];
  selectedEdge: BpmnEdge | null;
  processVariables: ProcessVariable[];
  processId: string;
  processName: string;
  onUpdateProcessId: (id: string) => void;
  onUpdateProcessName: (name: string) => void;
  onUpdateNode: (uid: string, data: Partial<BpmnNode['data']>) => void;
  onUpdateNodeId: (uid: string, newId: string) => void;
  onUpdateEdge: (edgeId: string, data: Partial<BpmnEdge>) => void;
  onUpdateVariables: (variables: ProcessVariable[]) => void;
  onDeleteNode: (uid: string) => void;
  onDeleteEdge: (edgeId: string) => void;
  onFocusValidationIssue: (issue: ValidationIssue) => void;
  validation: {
    duplicateNodeIds: string[];
    duplicateGlobalVars: string[];
    issues: ValidationIssue[];
  };
}

export const PropertiesPanel: React.FC<PropertiesPanelProps> = ({
  selectedNodeUids,
  nodes,
  selectedEdge,
  processVariables,
  processId,
  processName,
  onUpdateProcessId,
  onUpdateProcessName,
  onUpdateNode,
  onUpdateNodeId,
  onUpdateEdge,
  onUpdateVariables,
  onDeleteNode,
  onDeleteEdge,
  onFocusValidationIssue,
  validation,
}) => {
  const inputClassName = "w-full text-sm bg-white text-slate-800 border border-slate-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-blue-500 focus:border-blue-400 focus:outline-none placeholder-slate-400 transition-colors";
  const smallInputClassName = "text-xs bg-white text-slate-800 border border-slate-300 rounded px-2 py-1.5 focus:ring-2 focus:ring-blue-500 focus:border-blue-400 focus:outline-none placeholder-slate-400 disabled:opacity-50 disabled:bg-slate-50 transition-colors";

  const selectedNode = nodes.find(n => n.uid === selectedNodeUids[0]);
  const [localNodeId, setLocalNodeId] = useState(selectedNode?.id || '');
  const [processIdError, setProcessIdError] = useState<string | null>(null);
  const [formKeyError, setFormKeyError] = useState<string | null>(null);

  useEffect(() => {
    if (selectedNode) {
      setLocalNodeId(selectedNode.id);
    }
  }, [selectedNode?.uid]);

  const handleNodeIdBlur = () => {
    if (selectedNode && localNodeId !== selectedNode.id) {
      onUpdateNodeId(selectedNode.uid, localNodeId);
    }
  };

   const handleGlobalVarChange = (id: string, field: keyof ProcessVariable, value: string) => {
     onUpdateVariables(processVariables.map(v => v.id === id ? { ...v, [field]: typeof value === 'string' ? value : '' } : v));
   };

   const addGlobalVar = (name?: string) => {
     const resolvedName = typeof name === 'string' && name.trim() !== ''
       ? name
       : `var_${processVariables.length + 1}`;
     const newVar: ProcessVariable = {
       id: Math.random().toString(36).substr(2, 9),
       name: resolvedName,
       type: 'string',
       defaultValue: ''
     };
     onUpdateVariables([...processVariables, newVar]);
     return newVar;
   };

  const handleTaskVarChange = (
    uid: string, 
    collection: 'inputVariables' | 'outputVariables', 
    varId: string, 
    field: keyof TaskVariable, 
    value: string
  ) => {
    const node = nodes.find(n => n.uid === uid);
    if (!node) return;

   const currentList = node.data[collection] || [];
     const updatedList = currentList.map(v => {
       if (v.id === varId) {
         const updated = { ...v, [field]: value };
         if (field === 'value' && updated.mappingType === 'variable') {
            const match = processVariables.find(pv => (pv.name || '') === value);
            if (match) updated.type = match.type;
         }
         if (field === 'mappingType') {
            updated.value = '';
         }
         return updated;
       }
       return v;
     });
     onUpdateNode(uid, { [collection]: updatedList });
  };

  const getTypeIcon = (type: string) => {
    switch (type) {
      case 'number': return <Hash className="w-3 h-3" />;
      case 'boolean': return <ToggleLeft className="w-3 h-3" />;
      case 'json': return <Braces className="w-3 h-3" />;
      default: return <Type className="w-3 h-3" />;
    }
  };

  const renderTaskVarItem = (nodeUid: string, collection: 'inputVariables' | 'outputVariables', v: TaskVariable, namePlaceholder?: string) => {
    const isInput = collection === 'inputVariables';
    const isOutput = !isInput;
    const isVariable = v.mappingType === 'variable';
    const globalMatch = isVariable ? processVariables.find(pv => pv.name === v.value) : null;
    const currentList = nodes.find(n => n.uid === nodeUid)?.data[collection] || [];
    const isDuplicate = v.name !== '' && currentList.filter(item => item.name === v.name).length > 1;

    return (
      <div key={v.id} className={`p-3 border rounded-md shadow-sm space-y-3 transition-all ${isVariable ? 'bg-blue-50/30 border-blue-200' : 'bg-white border-slate-200'} ${isDuplicate ? 'border-red-400 bg-red-50/20' : ''}`}>
        <div className="flex gap-2 items-center">
          <div className={`flex-1 flex items-center gap-1.5 px-2 py-1 rounded border transition-colors ${isDuplicate ? 'bg-red-50 border-red-300' : 'bg-slate-100 border-slate-200'}`}>
            {getTypeIcon(v.type)}
            <input 
              className="bg-transparent border-none focus:ring-0 text-xs font-semibold text-slate-700 w-full placeholder-slate-400" 
              value={v.name} 
              onChange={e => handleTaskVarChange(nodeUid, collection, v.id, 'name', e.target.value)} 
              placeholder={namePlaceholder || (isInput ? "Internal Task Name" : "Source (Result/Static)")}
            />
          </div>
          <button onClick={() => {
            const node = nodes.find(n => n.uid === nodeUid);
            if (node) {
              const list = node.data[collection] || [];
              onUpdateNode(nodeUid, { [collection]: list.filter(item => item.id !== v.id) });
            }
          }} className="text-red-400 hover:text-red-600 transition-colors p-1">
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        </div>
        {isDuplicate && isInput && <p className="text-[10px] text-red-500 font-medium flex items-center gap-1"><AlertCircle className="w-3 h-3" /> Duplicate variable name</p>}
        
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <label className="text-[9px] font-bold text-slate-400 uppercase tracking-tighter">
              {isInput ? 'Source' : 'Target Global Variable'}
            </label>
            {isOutput && (
              <button 
                onClick={() => addGlobalVar()}
                className="text-[9px] text-blue-500 hover:text-blue-600 font-bold uppercase"
              >
                + New Variable
              </button>
            )}
          </div>
          
          <div className="flex items-center gap-2">
            <select 
              className="text-[10px] bg-slate-100 border border-slate-200 rounded px-1.5 py-0.5 text-slate-600 focus:ring-0" 
              value={v.mappingType} 
              onChange={e => handleTaskVarChange(nodeUid, collection, v.id, 'mappingType', e.target.value as any)}
            >
              <option value="variable">{isInput ? 'From Global' : 'From Task Result'}</option>
              <option value="static">Static Value</option>
            </select>
            {!isVariable && (
              <select className="text-[10px] bg-slate-100 border border-slate-200 rounded px-1.5 py-0.5 text-slate-600 focus:ring-0" value={v.type} onChange={e => handleTaskVarChange(nodeUid, collection, v.id, 'type', e.target.value as any)}>
                <option value="string">String</option><option value="number">Number</option><option value="boolean">Boolean</option><option value="json">JSON</option>
              </select>
            )}
          </div>

          <div className="relative">
             {/* For Inputs: Source can be Global Var or Static. For Outputs: Target is ALWAYS Global Var */}
             {isInput ? (
               v.mappingType === 'variable' ? (
                 <select className={`${smallInputClassName} w-full`} value={v.value} onChange={e => handleTaskVarChange(nodeUid, collection, v.id, 'value', e.target.value)}>
                   <option value="">Select process variable...</option>
                   {processVariables.map(pv => (<option key={pv.id} value={pv.name || ''}>{pv.name || ''} ({pv.type})</option>))}
                 </select>
               ) : (
                 <input className={`${smallInputClassName} w-full`} value={v.value} onChange={e => handleTaskVarChange(nodeUid, collection, v.id, 'value', e.target.value)} placeholder="Enter static value..." />
               )
             ) : (
               <select className={`${smallInputClassName} w-full`} value={v.value} onChange={e => handleTaskVarChange(nodeUid, collection, v.id, 'value', e.target.value)}>
                 <option value="">Select target process variable...</option>
                 {processVariables.map(pv => (<option key={pv.id} value={pv.name || ''}>{pv.name || ''} ({pv.type})</option>))}
               </select>
             )}
            {((isInput && v.mappingType === 'variable') || isOutput) && globalMatch && <div className="absolute right-2 top-1/2 -translate-y-1/2 text-blue-500 pointer-events-none" title="Type inherited from global"><Database className="w-3 h-3" /></div>}
          </div>
        </div>
      </div>
    );
  };

  const renderVarList = (title: string, collection: 'inputVariables' | 'outputVariables', icon: React.ReactNode, namePlaceholder?: string) => (
    <div className="space-y-3 mt-4">
      <div className="flex items-center justify-between text-slate-600">
        <div className="flex items-center gap-2"><span className="p-1 bg-slate-100 rounded">{icon}</span><span className="text-[11px] font-bold uppercase tracking-tight">{title}</span></div>
        <button onClick={() => {
          if (!selectedNode) return;
          const newVar: TaskVariable = { id: Math.random().toString(36).substr(2, 9), name: '', type: 'string', mappingType: 'variable', value: '' };
          const currentList = selectedNode.data[collection] || [];
          onUpdateNode(selectedNode.uid, { [collection]: [...currentList, newVar] });
        }} className="text-blue-600 hover:text-blue-700 transition-colors"><Plus className="w-4 h-4" /></button>
      </div>
      <div className="space-y-3">
        {selectedNode?.data[collection]?.map(v => renderTaskVarItem(selectedNode.uid, collection, v, namePlaceholder))}
        {(!selectedNode?.data[collection] || selectedNode?.data[collection]!.length === 0) && <p className="text-[10px] text-slate-400 italic text-center py-2 bg-slate-50 border border-dashed border-slate-200 rounded">No {title.toLowerCase()} configured.</p>}
      </div>
    </div>
  );

  const renderValidationSection = (issues: ValidationIssue[], title = 'Validation') => {
    const errors = issues.filter(issue => issue.severity === 'error');
    const warnings = issues.filter(issue => issue.severity === 'warning');

    return (
      <div className="space-y-4">
        <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider">{title}</label>
        {errors.length === 0 && warnings.length === 0 ? (
          <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-xs text-emerald-700">
            No validation issues in the current scope.
          </div>
        ) : (
          <div className="space-y-3">
            {errors.length > 0 && (
              <div className="space-y-2">
                <p className="text-[10px] font-bold uppercase tracking-widest text-red-600">Errors</p>
                {errors.map(issue => (
                  <button
                    key={issue.id}
                    onClick={() => onFocusValidationIssue(issue)}
                    className="w-full rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-left text-xs text-red-700 hover:bg-red-100 transition-colors"
                  >
                    {issue.message}
                  </button>
                ))}
              </div>
            )}
            {warnings.length > 0 && (
              <div className="space-y-2">
                <p className="text-[10px] font-bold uppercase tracking-widest text-amber-600">Warnings</p>
                {warnings.map(issue => (
                  <button
                    key={issue.id}
                    onClick={() => onFocusValidationIssue(issue)}
                    className="w-full rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-left text-xs text-amber-700 hover:bg-amber-100 transition-colors"
                  >
                    {issue.message}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    );
  };

  if (selectedEdge) {
    return (
      <div className="w-80 bg-white border-l border-slate-200 h-full flex flex-col z-10 overflow-hidden" style={{ boxShadow: '-1px 0 3px 0 rgba(0,0,0,0.04)' }}>
        <div className="px-4 py-3.5 border-b border-slate-200 flex items-center justify-between bg-white"><h2 className="text-sm font-semibold text-slate-800">Connection</h2><button onClick={() => onDeleteEdge(selectedEdge.id)} className="text-slate-400 hover:text-red-500 hover:bg-red-50 p-1.5 rounded-md transition-colors"><Trash2 className="w-4 h-4" /></button></div>
        <div className="p-4 space-y-4"><label className="block text-[10px] font-bold text-slate-500 uppercase tracking-wider">Condition Expression</label><input type="text" value={selectedEdge.condition || ''} onChange={(e) => onUpdateEdge(selectedEdge.id, { condition: e.target.value })} placeholder="${orderTotal} > 100" className={`${inputClassName} font-mono`} /><p className="text-[11px] text-slate-400 leading-tight">Use variables defined in the Global Process Data.</p></div>
      </div>
    );
  }

  if (selectedNodeUids.length === 0) {
    return (
      <div className="w-80 bg-white border-l border-slate-200 h-full flex flex-col z-10 overflow-hidden" style={{ boxShadow: '-1px 0 3px 0 rgba(0,0,0,0.04)' }}>
        <div className="px-4 py-3.5 border-b border-slate-200 bg-white flex items-center gap-2.5"><Database className="w-4 h-4 text-slate-400" /><h2 className="text-sm font-semibold text-slate-800">Process State</h2></div>
        <div className="p-4 flex-1 overflow-y-auto space-y-6">
          {renderValidationSection(validation.issues, 'Validation Summary')}

          <div className="space-y-4">
            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider">Configuration</label>
            <div>
                <label className="block text-[10px] text-slate-400 mb-1 font-bold flex items-center gap-1"><FileCode className="w-3 h-3" /> PROCESS ID</label>
                <input 
                  type="text" 
                  value={processId} 
                  onChange={e => {
                    const newId = e.target.value;
                    onUpdateProcessId(newId);
                    setProcessIdError(validateId(newId));
                  }}
                  onBlur={() => setProcessIdError(validateId(processId))}
                  className={`${inputClassName} font-mono ${processIdError ? '!border-red-500' : ''}`} 
                  placeholder="e.g. order_processing_01" 
                />
                {processIdError && <p className="text-[10px] text-red-500 mt-1 flex items-center gap-1"><AlertCircle className="w-3 h-3" /> {processIdError}</p>}
              </div>

              <div className="mt-3">
                <label className="block text-[10px] text-slate-400 mb-1 font-bold flex items-center gap-1"><FileText className="w-3 h-3" /> PROCESS NAME (Title)</label>
                <input
                  type="text"
                  value={processName}
                  onChange={e => onUpdateProcessName(e.target.value)}
                  className={`${inputClassName}`}
                  placeholder="Human-friendly title e.g. Order Processing"
                />
              </div>
          </div>

          <div className="border-t border-slate-100 pt-6">
            <div className="flex items-center justify-between mb-4"><label className="text-xs font-semibold text-slate-500 uppercase">Global Variables</label><button onClick={() => addGlobalVar()} className="text-blue-600 hover:text-blue-700 transition-colors"><Plus className="w-4 h-4" /></button></div>
            <div className="space-y-3">
                {processVariables.map(v => {
                  const isDuplicate = validation.duplicateGlobalVars.includes(v.name || '');
                  return (
                    <div key={v.id} className={`p-3 border rounded-lg space-y-2 transition-colors ${isDuplicate ? 'bg-red-50 border-red-300' : 'bg-slate-50 border-slate-200'}`}>
                      <div className="flex gap-2">
                        <input className={`flex-1 ${smallInputClassName} ${isDuplicate ? '!border-red-400' : ''}`} value={v.name || ''} onChange={e => handleGlobalVarChange(v.id, 'name', e.target.value)} placeholder="Variable Name" />
                        <button onClick={() => onUpdateVariables(processVariables.filter(pv => pv.id !== v.id))} className="text-red-400 hover:text-red-600 transition-colors"><Trash2 className="w-4 h-4" /></button>
                      </div>
                    {isDuplicate && <p className="text-[10px] text-red-500 font-medium flex items-center gap-1"><AlertCircle className="w-3 h-3" /> Duplicate name</p>}
                    <div className="flex gap-2">
                      <select className={`${smallInputClassName} w-24`} value={v.type} onChange={e => handleGlobalVarChange(v.id, 'type', e.target.value as any)}>
                        <option value="string">String</option><option value="number">Number</option><option value="boolean">Boolean</option><option value="json">JSON</option>
                      </select>
                      <input className={`flex-1 ${smallInputClassName}`} value={v.defaultValue} onChange={e => handleGlobalVarChange(v.id, 'defaultValue', e.target.value)} placeholder="Initial Value" />
                    </div>
                  </div>
                );
              })}
              {processVariables.length === 0 && <p className="text-xs text-slate-400 text-center py-8">Define global variables to share data between tasks.</p>}
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (!selectedNode || selectedNodeUids.length > 1) {
    return (
      <div className="w-80 bg-white border-l border-slate-200 h-full flex items-center justify-center p-8 text-center text-slate-400" style={{ boxShadow: '-1px 0 3px 0 rgba(0,0,0,0.04)' }}>
        <div><Layers className="w-12 h-12 mx-auto mb-4 opacity-20" /><p className="text-sm">{selectedNodeUids.length} items selected</p></div>
      </div>
    );
  }

  const isNodeIdDuplicate = validation.duplicateNodeIds.includes(localNodeId);
  const isMessageEvent = ['message-start', 'message-intermediate-catch', 'message-intermediate-throw'].includes(selectedNode.type);
  const nodeScopedIssues = validation.issues.filter(issue => issue.nodeUid === selectedNode.uid);

  return (
    <div className="w-80 bg-white border-l border-slate-200 h-full flex flex-col z-10 overflow-hidden" style={{ boxShadow: '-1px 0 3px 0 rgba(0,0,0,0.04)' }}>
      <div className="px-4 py-3.5 border-b border-slate-200 flex items-center justify-between bg-white">
        <div><h2 className="text-sm font-semibold text-slate-800">Node Properties</h2><p className="text-[10px] text-slate-400 uppercase font-mono tracking-widest mt-0.5">{selectedNode.type.replace(/-/g, ' ')}</p></div>
        <button onClick={() => onDeleteNode(selectedNode.uid)} className="text-slate-400 hover:text-red-500 hover:bg-red-50 p-1.5 rounded-md transition-colors"><Trash2 className="w-4 h-4" /></button>
      </div>
      <div className="p-4 flex-1 overflow-y-auto space-y-8">
        {renderValidationSection(nodeScopedIssues, 'Node Validation')}

        <div className="space-y-4">
          <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider">General</label>
          <div>
            <label className="block text-[10px] text-slate-400 mb-1 font-bold flex items-center gap-1"><Fingerprint className="w-3 h-3" /> ELEMENT ID</label>
            <input type="text" value={localNodeId} onChange={e => setLocalNodeId(e.target.value)} onBlur={handleNodeIdBlur} className={`${inputClassName} font-mono ${isNodeIdDuplicate ? '!border-red-500 focus:!ring-red-500' : ''}`} placeholder="e.g. process_step_1" />
            {isNodeIdDuplicate && <p className="text-[10px] text-red-500 mt-1 font-medium flex items-center gap-1"><AlertCircle className="w-3 h-3" /> Duplicate ID in process</p>}
          </div>
          <div><label className="block text-[10px] text-slate-400 mb-1 font-bold">LABEL</label><input type="text" value={selectedNode.data.label} onChange={e => onUpdateNode(selectedNode.uid, { label: e.target.value })} className={inputClassName} placeholder="Task Label" /></div>
          <div><label className="block text-[10px] text-slate-400 mb-1 font-bold">DESCRIPTION</label><textarea value={selectedNode.data.description || ''} onChange={e => onUpdateNode(selectedNode.uid, { description: e.target.value })} className={`${inputClassName} h-20 resize-none`} placeholder="Describe this step..." /></div>
        </div>
        {isMessageEvent && (
          <div className="border-t border-slate-100 pt-6 space-y-6">
            <div className="px-4 space-y-4">
              <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider flex items-center gap-2">
                <Mail className={`w-4 h-4 ${['message-start', 'message-intermediate-catch'].includes(selectedNode.type) ? 'text-green-500' : 'text-blue-500'}`} /> 
                {['message-start', 'message-intermediate-catch'].includes(selectedNode.type) ? 'Receiving Message' : 'Sending Message'}
              </label>
              <div>
                <label className="block text-[10px] text-slate-400 mb-1 font-bold">MESSAGE NAME (UCA)</label>
                <input 
                  type="text" 
                  value={selectedNode.data.messageName || ''} 
                  onChange={e => onUpdateNode(selectedNode.uid, { messageName: e.target.value })} 
                  className={inputClassName} 
                  placeholder="e.g. OrderReceived" 
                />
              </div>
              {['message-start', 'message-intermediate-catch'].includes(selectedNode.type) && (
                <div>
                  <label className="block text-[10px] text-slate-400 mb-1 font-bold">TIMEOUT (seconds)</label>
                  <input
                    type="number"
                    min={0}
                    value={selectedNode.data.timeoutSeconds ?? ''}
                    onChange={e => onUpdateNode(selectedNode.uid, { timeoutSeconds: e.target.value ? Number(e.target.value) : null })}
                    className={inputClassName}
                    placeholder="e.g. 30"
                  />
                  <p className="text-[10px] text-slate-400 mt-1 leading-tight">Optional timer for message wait. Empty means no timeout.</p>
                </div>
              )}
              {['message-intermediate-catch', 'message-intermediate-throw'].includes(selectedNode.type) && (
                <div>
                  <label className="block text-[10px] text-slate-400 mb-1 font-bold uppercase">Correlation Keys (CSV)</label>
                  <input 
                    type="text" 
                    value={selectedNode.data.correlationKeys || ''} 
                    onChange={e => onUpdateNode(selectedNode.uid, { correlationKeys: e.target.value })} 
                    className={inputClassName} 
                    placeholder="e.g. orderId, customerId" 
                  />
                  <p className="text-[10px] text-slate-400 mt-1 leading-tight">Comma-separated keys used to match the message to a process instance.</p>
                </div>
              )}
            </div>

            <div className="px-4 border-t border-slate-50 pt-4">
              {['message-start', 'message-intermediate-catch'].includes(selectedNode.type) ? (
                renderVarList('Message Payload to Process', 'outputVariables', <LogOut className="w-3.5 h-3.5 text-green-500" />)
              ) : (
                renderVarList('Process to Message Payload', 'inputVariables', <LogIn className="w-3.5 h-3.5 text-blue-500" />)
              )}
            </div>
          </div>
        )}
        {selectedNode.type === 'timer-event' && (
          <div className="border-t border-slate-100 pt-6 space-y-4 px-4">
            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider">Timer Configuration</label>
            <div>
              <label className="block text-[10px] text-slate-400 mb-1 font-bold">TIMEOUT (seconds)</label>
              <input
                type="number"
                min={1}
                value={selectedNode.data.timeoutSeconds ?? ''}
                onChange={e => onUpdateNode(selectedNode.uid, { timeoutSeconds: e.target.value ? Number(e.target.value) : null })}
                className={inputClassName}
                placeholder="e.g. 30"
              />
              <p className="text-[10px] text-slate-400 mt-1 leading-tight">Process will wait this duration before moving to the next node.</p>
            </div>
          </div>
        )}
        {(selectedNode.type === 'error-boundary' || selectedNode.type === 'message-boundary' || selectedNode.type === 'timer-boundary') && (
          <div className="border-t border-slate-100 pt-6 space-y-4 px-4">
            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider flex items-center gap-2">
              {selectedNode.type === 'error-boundary' ? <Zap className="w-4 h-4 text-red-500" /> : selectedNode.type === 'message-boundary' ? <Mail className="w-4 h-4 text-blue-500" /> : <Zap className="w-4 h-4 text-amber-600" />} 
              {selectedNode.type === 'error-boundary' ? 'Error Configuration' : selectedNode.type === 'message-boundary' ? 'Message Configuration' : 'Timer Configuration'}
            </label>
            
            {selectedNode.type === 'error-boundary' ? (
              <div className="space-y-4">
                <div>
                  <label className="block text-[10px] text-slate-400 mb-1 font-bold">ERROR CODE</label>
                  <input 
                    type="text" 
                    value={selectedNode.data.errorCode || ''} 
                    onChange={e => onUpdateNode(selectedNode.uid, { errorCode: e.target.value })} 
                    className={inputClassName} 
                    placeholder="e.g. ERR_TIMEOUT" 
                  />
                  <p className="text-[10px] text-slate-400 mt-1 leading-tight">The code of the exception to catch from the parent task.</p>
                </div>
                <div>
                  <label className="block text-[10px] text-slate-400 mb-1 font-bold">EXCEPTION VARIABLE (OPTIONAL)</label>
                  <input 
                    type="text" 
                    value={selectedNode.data.exceptionVariable || ''} 
                    onChange={e => {
                      const newValue = e.target.value;
                      onUpdateNode(selectedNode.uid, { exceptionVariable: newValue });
                      setFormKeyError(validateId(newValue));
                    }}
                    onBlur={() => setFormKeyError(validateId(selectedNode.data.exceptionVariable || ''))}
                    className={`${inputClassName} ${formKeyError ? '!border-red-500' : ''}`} 
                    placeholder="e.g. errorMessage" 
                  />
                  {formKeyError && <p className="text-[10px] text-red-500 mt-1 flex items-center gap-1"><AlertCircle className="w-3 h-3" /> {formKeyError}</p>}
                  <p className="text-[10px] text-slate-400 mt-1 leading-tight">Optional process variable name to capture error message. If set, the exception message will be stored in this variable.</p>
                </div>
              </div>
            ) : selectedNode.type === 'message-boundary' ? (
              <div className="space-y-4">
                <div>
                  <label className="block text-[10px] text-slate-400 mb-1 font-bold">MESSAGE NAME (UCA)</label>
                  <input 
                    type="text" 
                    value={selectedNode.data.messageName || ''} 
                    onChange={e => onUpdateNode(selectedNode.uid, { messageName: e.target.value })} 
                    className={inputClassName} 
                    placeholder="e.g. OrderReceived" 
                  />
                </div>
                <div>
                  <label className="block text-[10px] text-slate-400 mb-1 font-bold uppercase">Correlation Keys (CSV)</label>
                  <input 
                    type="text" 
                    value={selectedNode.data.correlationKeys || ''} 
                    onChange={e => onUpdateNode(selectedNode.uid, { correlationKeys: e.target.value })} 
                    className={inputClassName} 
                    placeholder="e.g. orderId, customerId" 
                  />
                </div>
                {renderVarList('Message Payload to Process', 'outputVariables', <LogOut className="w-3.5 h-3.5 text-green-500" />)}
              </div>
            ) : (
              <div className="space-y-4">
                <div>
                  <label className="block text-[10px] text-slate-400 mb-1 font-bold">TIMEOUT (seconds)</label>
                  <input
                    type="number"
                    min={1}
                    value={selectedNode.data.timeoutSeconds ?? ''}
                    onChange={e => onUpdateNode(selectedNode.uid, { timeoutSeconds: e.target.value ? Number(e.target.value) : null })}
                    className={inputClassName}
                    placeholder="e.g. 30"
                  />
                </div>
                <div>
                  <label className="block text-[10px] text-slate-400 mb-1 font-bold">INTERRUPTING</label>
                  <select
                    className={inputClassName}
                    value={selectedNode.data.interrupting === false ? 'false' : 'true'}
                    onChange={e => onUpdateNode(selectedNode.uid, { interrupting: e.target.value === 'true' })}
                  >
                    <option value="true">Yes (interrupt task)</option>
                    <option value="false">No (non-interrupting)</option>
                  </select>
                </div>
              </div>
            )}

            {selectedNode.attachedTo ? (
              <div className="bg-green-50 border border-green-100 p-2 rounded flex items-center gap-2">
                <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse" />
                <span className="text-[10px] text-green-700 font-medium uppercase tracking-tight">Attached to parent task</span>
              </div>
            ) : (
              <div className="bg-amber-50 border border-amber-100 p-2 rounded flex items-center gap-2">
                <AlertCircle className="w-3 h-3 text-amber-500" />
                <span className="text-[10px] text-amber-700 font-medium uppercase tracking-tight">Drag onto a task to attach</span>
              </div>
            )}
          </div>
        )}
        {selectedNode.type === 'user-task' && (
          <div className="border-t border-slate-100 pt-6">
             <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-4">Task Definition</label>
             <div className="space-y-4 mb-6">
                <div>
                  <label className="text-[10px] text-slate-400 uppercase font-bold mb-1 block flex items-center gap-1">
                    <FileText className="w-3 h-3" /> FORM KEY
                  </label>
                  <input 
                    type="text"
                    className={`${smallInputClassName} ${formKeyError ? '!border-red-500' : ''}`}
                    value={selectedNode.data.formId || ''} 
                    onChange={e => {
                      const newKey = e.target.value;
                      onUpdateNode(selectedNode.uid, { formId: newKey });
                      setFormKeyError(validateId(newKey));
                    }}
                    onBlur={() => setFormKeyError(validateId(selectedNode.data.formId || ''))}
                    placeholder="e.g. formApproval"
                  />
                  {formKeyError && <p className="text-[10px] text-red-500 mt-1 flex items-center gap-1"><AlertCircle className="w-3 h-3" /> {formKeyError}</p>}
                  <p className="text-[10px] text-slate-400 mt-1 leading-tight">Attach the deployed form key used for versioning, not the numeric database id.</p>
                </div>
                <div><label className="text-[10px] text-slate-400 uppercase font-bold mb-1 block">Assignee(s)</label><input className={smallInputClassName} value={selectedNode.data.assignee || ''} onChange={e => onUpdateNode(selectedNode.uid, { assignee: e.target.value })} placeholder="e.g. manager1, manager2" /></div>
                <div><label className="text-[10px] text-slate-400 uppercase font-bold mb-1 block">Candidate Groups</label><input className={smallInputClassName} value={selectedNode.data.candidateGroups || ''} onChange={e => onUpdateNode(selectedNode.uid, { candidateGroups: e.target.value })} placeholder="e.g. FINANCE, OPERATIONS" /></div>
             </div>
             {renderVarList('Task Inputs', 'inputVariables', <LogIn className="w-3.5 h-3.5 text-blue-500" />)}
             {renderVarList('Task Outputs', 'outputVariables', <LogOut className="w-3.5 h-3.5 text-green-500" />)}
          </div>
        )}
        {selectedNode.type === 'api-task' && (
           <div className="border-t border-slate-100 pt-6 space-y-6">
             <div className="px-4 space-y-4">
               <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider">System Endpoint</label>
               <div><label className="text-[10px] text-slate-400 uppercase font-bold mb-1 block">URL</label><input className={`${inputClassName} font-mono`} value={selectedNode.data.apiEndpoint || ''} onChange={e => onUpdateNode(selectedNode.uid, { apiEndpoint: e.target.value })} placeholder="https://api..." /></div>
               <div className="grid grid-cols-2 gap-3"><div><label className="text-[10px] text-slate-400 uppercase font-bold mb-1 block">Method</label><select className={inputClassName} value={selectedNode.data.method || 'GET'} onChange={e => onUpdateNode(selectedNode.uid, { method: e.target.value as any })}><option>GET</option><option>POST</option><option>PUT</option><option>DELETE</option></select></div></div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-[10px] text-slate-400 uppercase font-bold mb-1 block">Auth Type</label>
                  <select
                    className={inputClassName}
                    value={selectedNode.data.apiAuthType || 'none'}
                    onChange={e => onUpdateNode(selectedNode.uid, {
                      apiAuthType: e.target.value as any,
                      apiAuthIn: e.target.value === 'apikey' ? (selectedNode.data.apiAuthIn || 'header') : undefined,
                      apiAuthKey: e.target.value === 'apikey' ? (selectedNode.data.apiAuthKey || 'X-API-Key') : undefined
                    })}
                  >
                    <option value="none">None</option>
                    <option value="bearer">Bearer</option>
                    <option value="basic">Basic</option>
                    <option value="apikey">API Key</option>
                  </select>
                </div>
                {(selectedNode.data.apiAuthType || 'none') !== 'none' && (
                  <div>
                    <label className="text-[10px] text-slate-400 uppercase font-bold mb-1 block">Auth Ref</label>
                    <input
                      className={`${inputClassName} font-mono`}
                      value={selectedNode.data.apiAuthRef || ''}
                      onChange={e => onUpdateNode(selectedNode.uid, { apiAuthRef: e.target.value })}
                      placeholder="e.g. EXT_API_AUTH"
                    />
                  </div>
                )}
              </div>
              {(selectedNode.data.apiAuthType || 'none') === 'apikey' && (
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-[10px] text-slate-400 uppercase font-bold mb-1 block">API Key In</label>
                    <select
                      className={inputClassName}
                      value={selectedNode.data.apiAuthIn || 'header'}
                      onChange={e => onUpdateNode(selectedNode.uid, { apiAuthIn: e.target.value as any })}
                    >
                      <option value="header">Header</option>
                      <option value="query">Query</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-[10px] text-slate-400 uppercase font-bold mb-1 block">API Key Name</label>
                    <input
                      className={`${inputClassName} font-mono`}
                      value={selectedNode.data.apiAuthKey || 'X-API-Key'}
                      onChange={e => onUpdateNode(selectedNode.uid, { apiAuthKey: e.target.value })}
                      placeholder="X-API-Key"
                    />
                  </div>
                </div>
              )}
               <div><label className="text-[10px] text-slate-400 uppercase font-bold mb-1 block">Request Body (JSON)</label><textarea className={`${inputClassName} h-28 font-mono`} value={selectedNode.data.body || ''} onChange={e => onUpdateNode(selectedNode.uid, { body: e.target.value })} placeholder='{ "id": "${userId}" }' /></div>
             </div>
             
             <div className="px-4 border-t border-slate-50 pt-4">
               {renderVarList('API Output Mapping', 'outputVariables', <LogOut className="w-3.5 h-3.5 text-green-500" />, "JSON Path (e.g. data.id)")}
             </div>
           </div>
        )}
        {selectedNode.type === 'service-task' && (
           <div className="border-t border-slate-100 pt-6 space-y-6">
             <div className="px-4">
               <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider">Variable Attribution</label>
               <p className="text-[10px] text-slate-400 mt-1">Map and assign values to process variables.</p>
             </div>
             <div className="px-4 border-t border-slate-50 pt-4">
               {renderVarList('Input Mappings', 'inputVariables', <LogIn className="w-3.5 h-3.5 text-blue-500" />)}
               {renderVarList('Output Mappings', 'outputVariables', <LogOut className="w-3.5 h-3.5 text-green-500" />)}
             </div>
           </div>
        )}
        {selectedNode.type === 'call-activity' && (
           <div className="border-t border-slate-100 pt-6 space-y-6">
             <div className="px-4">
               <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider flex items-center gap-2">
                 <Layers className="w-4 h-4 text-cyan-600" /> Call Activity Configuration
               </label>
               <p className="text-[10px] text-slate-400 mt-1">Invoke a subprocess and map variables between parent and child.</p>
             </div>
             
             <div className="px-4 space-y-4">
               <div>
                 <label className="block text-[10px] text-slate-400 mb-2 font-bold">TARGET PROCESS KEY</label>
                 <input 
                   type="text" 
                   value={selectedNode.data.callActivityProcessKey || ''} 
                   onChange={e => onUpdateNode(selectedNode.uid, { callActivityProcessKey: e.target.value })} 
                   className={inputClassName} 
                   placeholder="e.g. child-process" 
                 />
                 <p className="text-[10px] text-slate-400 mt-1 leading-tight">The key of the process definition to invoke as a subprocess.</p>
               </div>
               
               <div className="flex items-center gap-2 bg-slate-50 border border-slate-200 rounded-md px-3 py-2.5">
                 <input 
                   type="checkbox" 
                   id="propagate-all" 
                   checked={selectedNode.data.propagateAllVariables || false}
                   onChange={e => onUpdateNode(selectedNode.uid, { propagateAllVariables: e.target.checked })}
                   className="w-4 h-4 cursor-pointer rounded border-slate-300 text-blue-600"
                 />
                 <label htmlFor="propagate-all" className="text-xs text-slate-700 cursor-pointer flex-1">Propagate All Variables</label>
                 <FileCode className="w-3.5 h-3.5 text-slate-400" />
               </div>
               <p className="text-[10px] text-slate-400 leading-tight">If enabled, all process variables will be copied to the child process (explicit mappings are ignored).</p>
             </div>

             <div className="px-4 border-t border-slate-50 pt-4">
               {renderVarList('Input Variable Mapping', 'inputVariables', <LogIn className="w-3.5 h-3.5 text-blue-500" />, 'Parent Variable Name')}
               {renderVarList('Output Variable Mapping', 'outputVariables', <LogOut className="w-3.5 h-3.5 text-green-500" />, 'Child Variable Name')}
             </div>
           </div>
        )}
        {selectedNode.type === 'code-task' && (
           <div className="border-t border-slate-100 pt-6 space-y-6">
             <div className="px-4">
               <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider flex items-center gap-2">
                 <Code className="w-4 h-4 text-indigo-600" /> Code Task Configuration
               </label>
               <p className="text-[10px] text-slate-400 mt-1">Execute Java methods from uploaded JAR files. Configure JAR and method selection separately.</p>
             </div>
             
             <div className="px-4 space-y-4">
               <div className="bg-indigo-50 border border-indigo-100 p-2 rounded flex items-center gap-2">
                 <Info className="w-3 h-3 text-indigo-500" />
                 <span className="text-[10px] text-indigo-700 font-medium">Code Task configuration is managed via the Code Task panel in the Admin UI.</span>
               </div>
             </div>

             <div className="px-4 border-t border-slate-50 pt-4">
               {renderVarList('Input Variable Mapping', 'inputVariables', <LogIn className="w-3.5 h-3.5 text-blue-500" />)}
               {renderVarList('Output Variable Mapping', 'outputVariables', <LogOut className="w-3.5 h-3.5 text-green-500" />)}
             </div>
           </div>
        )}
      </div>
    </div>
  );
};