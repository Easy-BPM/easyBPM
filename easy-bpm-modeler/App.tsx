import React, { useState, useCallback, useEffect, useMemo } from 'react';
import {
  ShieldCheck,
  User,
  Lock,
  Loader2,
  PanelRightClose,
  PanelRightOpen
} from 'lucide-react';
import { Palette } from './components/Palette';
import { Canvas } from './components/Canvas';
import { PropertiesPanel } from './components/PropertiesPanel';
import { Toolbar } from './components/Toolbar';
import { FormModeler } from './components/FormModeler';
import { FormLibrary } from './components/FormLibrary';
import { WelcomeScreen } from './components/WelcomeScreen';
import { ModelerNavbar } from './components/ModelerNavbar';
import { ThemeMode } from './components/ThemeToggle';
import { BpmnNode, BpmnEdge, ProcessVariable, NodeType, AppView, ValidationIssue, ValidationSummary, FormDefinition, Position } from './types';
import { generateId, snapToGrid } from './utils/geometry';
import { validateId } from './utils/validation';
import { Toaster, toast } from 'sonner';
import { isAuthRequiredError, processService, fetchWithAuth } from './services/processService';
import { formService } from './services/formService';
import { downloadForm, importForm, generateJsonSchema } from './utils/formUtils';

const API_BASE_URL = (import.meta.env.EASY_BPM_MODELER_API_BASE_URL as string | undefined) ?? 'http://localhost:8080';
const BOUNDARY_TYPES: NodeType[] = ['error-boundary', 'message-boundary', 'timer-boundary'];
const START_TYPES: NodeType[] = ['start', 'message-start'];
const END_TYPES: NodeType[] = ['end'];
const TASK_TYPES: NodeType[] = ['user-task', 'service-task', 'api-task', 'code-task', 'ai-task'];
const CONTAINER_TYPES: NodeType[] = ['pool'];
const FORM_KEY_PATTERN = /^[A-Za-z][A-Za-z0-9_-]*$/;

// Helper to safely convert values to strings, preventing "[object Object]"
const safeString = (value: any): string => {
  if (typeof value === 'string') return value;
  if (value === null || value === undefined) return '';
  if (typeof value === 'object') return '';
  return String(value);
};

type EditorMode = 'welcome' | 'process-editor' | 'form-editor';

const App: React.FC = () => {
   const [theme, setTheme] = useState<ThemeMode>(() => {
     const storedTheme = localStorage.getItem('easyBpmModelerTheme');
     if (storedTheme === 'light' || storedTheme === 'dark') return storedTheme;
     return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
   });

   // Navigation state
   const [editorMode, setEditorMode] = useState<EditorMode>('welcome');

   // Process editor state
   const [nodes, setNodes] = useState<BpmnNode[]>([]);
   const [edges, setEdges] = useState<BpmnEdge[]>([]);
   const [variablesRaw, setVariablesRaw] = useState<ProcessVariable[]>([]);
   const [processId, setProcessId] = useState<string>(`process_${Date.now()}`);
   const [processName, setProcessName] = useState<string>('');
   const [selectedNodeUids, setSelectedNodeUids] = useState<string[]>([]);
   const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null);
   const [isDeployingProcess, setIsDeployingProcess] = useState(false);
   const [isPropertiesPanelVisible, setIsPropertiesPanelVisible] = useState(true);

   // Form editor state
   const [formLibrary, setFormLibrary] = useState<Map<string, FormDefinition>>(new Map());
   const [selectedFormKey, setSelectedFormKey] = useState<string | null>(null);
   const [currentEditingForm, setCurrentEditingForm] = useState<FormDefinition | null>(null);
   const [isDeployingForm, setIsDeployingForm] = useState(false);

   // Auth state
   const [currentUser, setCurrentUser] = useState<string | null>(null);
   const [permissions, setPermissions] = useState<string[]>([]);
   const [authLoading, setAuthLoading] = useState(true);

   useEffect(() => {
     localStorage.setItem('easyBpmModelerTheme', theme);
     document.documentElement.dataset.modelerTheme = theme;
   }, [theme]);

   const toggleTheme = () => setTheme(current => current === 'dark' ? 'light' : 'dark');

   useEffect(() => {
     const session = processService.getSession();
     if (!session) {
       setAuthLoading(false);
       return;
     }

     setCurrentUser(session.username);
     setPermissions(session.permissions);
     processService.me()
       .then(me => setPermissions(me.permissions))
       .catch(() => {
         processService.clearSession();
         setCurrentUser(null);
         setPermissions([]);
       })
       .finally(() => setAuthLoading(false));
   }, []);

   // Wrapper for setVariables that ALWAYS sanitizes before storing
   const setVariables = (vars: ProcessVariable[] | ((prev: ProcessVariable[]) => ProcessVariable[])) => {
     setVariablesRaw(prev => {
       const newVars = typeof vars === 'function' ? vars(prev) : vars;
       return newVars.map(v => ({
         ...v,
         name: typeof v.name === 'string' ? v.name : '',
         defaultValue: typeof v.defaultValue === 'string' ? v.defaultValue : ''
       }));
     });
   };

   // Always-clean getter for variables
   const variables = useMemo(() =>
     variablesRaw.map(v => ({
       ...v,
       name: typeof v.name === 'string' ? v.name : '',
       defaultValue: typeof v.defaultValue === 'string' ? v.defaultValue : ''
     })),
     [variablesRaw]
   );

  // Validation Logic
  const validationState = useMemo<ValidationSummary>(() => {
    const issues: ValidationIssue[] = [];
    const seenIssueKeys = new Set<string>();
    let issueCounter = 0;

    const addIssue = (severity: 'error' | 'warning', message: string, target?: { nodeUid?: string; edgeId?: string; nodeId?: string }) => {
      const dedupeKey = `${severity}:${message}:${target?.nodeUid || ''}:${target?.edgeId || ''}`;
      if (seenIssueKeys.has(dedupeKey)) return;
      seenIssueKeys.add(dedupeKey);
      issues.push({
        id: `issue_${issueCounter += 1}`,
        severity,
        message,
        nodeUid: target?.nodeUid,
        edgeId: target?.edgeId,
        nodeId: target?.nodeId
      });
    };

    if (nodes.length === 0) {
      addIssue('error', 'Process must contain at least one node.');
    }

    const nodeIds = nodes.map(n => n.id.trim());
    const duplicateNodeIds = nodeIds.filter((id, index) => nodeIds.indexOf(id) !== index);
    Array.from(new Set(duplicateNodeIds)).forEach(duplicateId => {
      nodes.filter(node => node.id.trim() === duplicateId).forEach(node => {
        addIssue('error', `Duplicate node ID found: ${duplicateId}`, { nodeUid: node.uid, nodeId: node.id });
      });
    });

    nodes.filter(n => n.id.trim() === '').forEach(node => {
       addIssue('error', 'All nodes must have a non-empty element ID.', { nodeUid: node.uid, nodeId: node.id });
     });

      const globalVarNames = variables.map(v => safeString(v.name).trim()).filter(n => n !== '');
      const duplicateGlobalVars = globalVarNames.filter((name, index) => globalVarNames.indexOf(name) !== index);
      if (duplicateGlobalVars.length > 0) {
        addIssue('error', `Duplicate global variables found: ${Array.from(new Set(duplicateGlobalVars)).join(', ')}`);
      }
      if (variables.some(v => safeString(v.name).trim() === '')) {
        addIssue('error', 'Global variables cannot have empty names.');
      }

    const processIdError = validateId(processId);
    if (processIdError) {
      addIssue('error', `Process ID error: ${processIdError}`);
    }

     let hasTaskVarDuplicates = false;
     nodes.forEach(node => {
       if (node.data.inputVariables) {
          const names = node.data.inputVariables.map(v => safeString(v.name).trim()).filter(n => n !== '');
          if (new Set(names).size !== names.length) {
            hasTaskVarDuplicates = true;
            addIssue('error', `Task input variables have duplicate names in node ${node.id}.`, { nodeUid: node.uid, nodeId: node.id });
          }
          if (node.data.inputVariables.some(v => safeString(v.name).trim() === '')) {
            addIssue('error', `Task input variables cannot have empty names in node ${node.id}.`, { nodeUid: node.uid, nodeId: node.id });
          }
        }
        if (node.data.outputVariables) {
          const names = node.data.outputVariables.map(v => safeString(v.name).trim()).filter(n => n !== '');
          if (new Set(names).size !== names.length) {
            hasTaskVarDuplicates = true;
            addIssue('error', `Task output variables have duplicate names in node ${node.id}.`, { nodeUid: node.uid, nodeId: node.id });
          }
          if (node.data.outputVariables.some(v => safeString(v.name).trim() === '')) {
            addIssue('error', `Task output variables cannot have empty names in node ${node.id}.`, { nodeUid: node.uid, nodeId: node.id });
          }
       }
     });

    const nodesByUid = new Map(nodes.map(node => [node.uid, node]));
    const incomingByUid = new Map<string, number>();
    const outgoingByUid = new Map<string, number>();
    const adjacency = new Map<string, string[]>();
    const duplicateEdgeKeys = new Set<string>();
    const seenEdgeKeys = new Set<string>();

    nodes.forEach(node => {
      incomingByUid.set(node.uid, 0);
      outgoingByUid.set(node.uid, 0);
      adjacency.set(node.uid, []);
    });

    edges.forEach(edge => {
      const edgeKey = `${edge.source}->${edge.target}`;
      if (seenEdgeKeys.has(edgeKey)) duplicateEdgeKeys.add(edgeKey);
      seenEdgeKeys.add(edgeKey);

      if (!nodesByUid.has(edge.source)) {
        addIssue('error', `Flow ${edge.id} source node does not exist.`, { edgeId: edge.id });
        return;
      }
      if (!nodesByUid.has(edge.target)) {
        addIssue('error', `Flow ${edge.id} target node does not exist.`, { edgeId: edge.id });
        return;
      }
      if (nodesByUid.get(edge.source)?.type === 'pool' || nodesByUid.get(edge.target)?.type === 'pool') {
        addIssue('error', `Flow ${edge.id} cannot connect to a pool/participant.`, { edgeId: edge.id });
        return;
      }

      outgoingByUid.set(edge.source, (outgoingByUid.get(edge.source) || 0) + 1);
      incomingByUid.set(edge.target, (incomingByUid.get(edge.target) || 0) + 1);
      adjacency.set(edge.source, [...(adjacency.get(edge.source) || []), edge.target]);
    });

    if (duplicateEdgeKeys.size > 0) {
      Array.from(duplicateEdgeKeys).forEach(edgeKey => {
        const [sourceUid, targetUid] = edgeKey.split('->');
        edges.filter(edge => edge.source === sourceUid && edge.target === targetUid).forEach(edge => {
          addIssue('warning', `Duplicate flow found between connected nodes (${edge.id}).`, { edgeId: edge.id });
        });
      });
    }

    const startNodes = nodes.filter(node => START_TYPES.includes(node.type));
    const endNodes = nodes.filter(node => END_TYPES.includes(node.type));

    if (startNodes.length === 0) addIssue('error', 'Process must have at least one start event.');
    if (endNodes.length === 0) addIssue('error', 'Process must have at least one end event.');

    nodes.forEach(node => {
      const incoming = incomingByUid.get(node.uid) || 0;
      const outgoing = outgoingByUid.get(node.uid) || 0;

      if (CONTAINER_TYPES.includes(node.type)) {
        if (incoming > 0 || outgoing > 0) {
          addIssue('error', `Pool ${node.id} is a visual participant and cannot have sequence flows.`, { nodeUid: node.uid, nodeId: node.id });
        }
        return;
      }

      if (START_TYPES.includes(node.type)) {
        if (incoming > 0) addIssue('error', `Start node ${node.id} cannot have incoming flows.`, { nodeUid: node.uid, nodeId: node.id });
        if (outgoing < 1) addIssue('error', `Start node ${node.id} must have at least one outgoing flow.`, { nodeUid: node.uid, nodeId: node.id });
      }

      if (END_TYPES.includes(node.type)) {
        if (incoming < 1) addIssue('error', `End node ${node.id} must have at least one incoming flow.`, { nodeUid: node.uid, nodeId: node.id });
        if (outgoing > 0) addIssue('error', `End node ${node.id} cannot have outgoing flows.`, { nodeUid: node.uid, nodeId: node.id });
      }

      if (TASK_TYPES.includes(node.type) || node.type === 'timer-event') {
        if (incoming < 1) addIssue('error', `Task node ${node.id} must have at least one incoming flow.`, { nodeUid: node.uid, nodeId: node.id });
        if (outgoing < 1) addIssue('error', `Task node ${node.id} must have at least one outgoing flow.`, { nodeUid: node.uid, nodeId: node.id });
      }

      if (node.type === 'timer-event') {
        const timeout = Number(node.data.timeoutSeconds);
        if (!Number.isFinite(timeout) || timeout <= 0) {
          addIssue('error', `Timer event ${node.id} must define timeoutSeconds > 0.`, { nodeUid: node.uid, nodeId: node.id });
        }
      }

      if (node.type === 'user-task') {
        const configuredFormKey = node.data.formId?.trim();
        if (configuredFormKey && !FORM_KEY_PATTERN.test(configuredFormKey)) {
          addIssue('error', `Human task ${node.id} has an invalid form key. Use letters, numbers, hyphens, or underscores, starting with a letter.`, { nodeUid: node.uid, nodeId: node.id });
        }
      }

      if (node.type === 'gateway') {
        if (incoming < 1 || outgoing < 1) {
          addIssue('error', `Exclusive gateway ${node.id} must have at least one incoming and one outgoing flow.`, { nodeUid: node.uid, nodeId: node.id });
        }
        if (outgoing > 1) {
          const outgoingEdges = edges.filter(edge => edge.source === node.uid);
          const hasAnyCondition = outgoingEdges.some(edge => (edge.condition || '').trim() !== '');
          if (!hasAnyCondition) {
            addIssue('warning', `Exclusive gateway ${node.id} has multiple outgoing flows without conditions.`, { nodeUid: node.uid, nodeId: node.id });
          }
        }
      }

      if (node.type === 'parallel-gateway') {
        const isFork = incoming === 1 && outgoing >= 2;
        const isJoin = incoming >= 2 && outgoing === 1;
        if (!isFork && !isJoin) {
          addIssue('error', `Parallel gateway ${node.id} must be either fork (1 in, 2+ out) or join (2+ in, 1 out).`, { nodeUid: node.uid, nodeId: node.id });
        }
        const outgoingEdges = edges.filter(edge => edge.source === node.uid);
        const hasConditionalFlow = outgoingEdges.some(edge => (edge.condition || '').trim() !== '');
        if (hasConditionalFlow) {
          addIssue('warning', `Parallel gateway ${node.id} has conditional flow(s); parallel branches should typically be unconditional.`, { nodeUid: node.uid, nodeId: node.id });
        }
      }

      if (BOUNDARY_TYPES.includes(node.type)) {
        const parent = node.attachedTo
          ? (nodesByUid.get(node.attachedTo) || nodes.find(candidate => candidate.id === node.attachedTo))
          : undefined;
        if (!parent || !TASK_TYPES.includes(parent.type)) {
          addIssue('error', `Boundary event ${node.id} must be attached to a valid task node.`, { nodeUid: node.uid, nodeId: node.id });
        }
        if (incoming > 0) {
          addIssue('error', `Boundary event ${node.id} cannot have incoming flows.`, { nodeUid: node.uid, nodeId: node.id });
        }
        if (outgoing < 1) {
          addIssue('error', `Boundary event ${node.id} must have at least one outgoing flow.`, { nodeUid: node.uid, nodeId: node.id });
        }
        if (node.type === 'timer-boundary') {
          const timeout = Number(node.data.timeoutSeconds);
          if (!Number.isFinite(timeout) || timeout <= 0) {
            addIssue('error', `Timer boundary ${node.id} must define timeoutSeconds > 0.`, { nodeUid: node.uid, nodeId: node.id });
          }
        }
      }
    });

    if (startNodes.length > 0) {
      const queue = [...startNodes.map(node => node.uid)];
      const visited = new Set(queue);
      while (queue.length > 0) {
        const current = queue.shift()!;
        const next = adjacency.get(current) || [];
        next.forEach(target => {
          if (!visited.has(target)) {
            visited.add(target);
            queue.push(target);
          }
        });

        // Boundary branches become reachable when their attached parent task is reachable.
        nodes
          .filter(node => BOUNDARY_TYPES.includes(node.type))
          .forEach(boundaryNode => {
            const parent = boundaryNode.attachedTo
              ? (nodesByUid.get(boundaryNode.attachedTo) || nodes.find(candidate => candidate.id === boundaryNode.attachedTo))
              : undefined;
            if (parent?.uid === current && !visited.has(boundaryNode.uid)) {
              visited.add(boundaryNode.uid);
              queue.push(boundaryNode.uid);
            }
          });
      }

      const unreachable = nodes.filter(node => !visited.has(node.uid) && !BOUNDARY_TYPES.includes(node.type) && !CONTAINER_TYPES.includes(node.type));
      unreachable.forEach(node => {
        addIssue('warning', `Unreachable node detected: ${node.id}`, { nodeUid: node.uid, nodeId: node.id });
      });
    }

    const errors = issues.filter(issue => issue.severity === 'error').map(issue => issue.message);
    const warnings = issues.filter(issue => issue.severity === 'warning').map(issue => issue.message);

    return {
      isValid: errors.length === 0,
      duplicateNodeIds,
      duplicateGlobalVars,
      hasTaskVarDuplicates,
      errors,
      warnings,
      issues
    };
  }, [nodes, variables, processId]);

  const invalidNodeUids = useMemo(
    () => Array.from(new Set(validationState.issues.filter(issue => issue.severity === 'error' && issue.nodeUid).map(issue => issue.nodeUid!))),
    [validationState.issues]
  );

  const warningNodeUids = useMemo(
    () => Array.from(new Set(validationState.issues.filter(issue => issue.severity === 'warning' && issue.nodeUid).map(issue => issue.nodeUid!))),
    [validationState.issues]
  );

  const invalidEdgeIds = useMemo(
    () => Array.from(new Set(validationState.issues.filter(issue => issue.severity === 'error' && issue.edgeId).map(issue => issue.edgeId!))),
    [validationState.issues]
  );

  const warningEdgeIds = useMemo(
    () => Array.from(new Set(validationState.issues.filter(issue => issue.severity === 'warning' && issue.edgeId).map(issue => issue.edgeId!))),
    [validationState.issues]
  );

  const handleFocusValidationIssue = useCallback((issue: ValidationIssue) => {
    if (issue.nodeUid) {
      setSelectedNodeUids([issue.nodeUid]);
      setSelectedEdgeId(null);
      return;
    }
    if (issue.edgeId) {
      setSelectedEdgeId(issue.edgeId);
      setSelectedNodeUids([]);
    }
  }, []);

  const handleDrop = (e: React.DragEvent, canvasPoint?: { x: number; y: number }) => {
    e.preventDefault();
    const type = e.dataTransfer.getData('application/reactflow') as NodeType;
    if (!type) return;

    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    const x = canvasPoint?.x ?? e.clientX - rect.left;
    const y = canvasPoint?.y ?? e.clientY - rect.top;

    let width = 120; 
    let height = 60;
    if (type === 'pool') {
      width = 640; height = 260;
    } else if (['start', 'end', 'gateway', 'parallel-gateway', 'timer-event', 'message-start', 'message-intermediate-throw', 'error-boundary', 'message-boundary', 'timer-boundary'].includes(type)) {
      width = 40; height = 40;
    } else {
      width = 120; height = 60;
    }
    if (type === 'error-boundary' || type === 'message-boundary' || type === 'timer-boundary') {
      width = 30; height = 30;
    }

    const initialId = generateId(type);
    let attachedTo: string | undefined = undefined;
    let finalPosition = { x: snapToGrid(x - width/2), y: snapToGrid(y - height/2) };

    if (type === 'error-boundary' || type === 'message-boundary' || type === 'timer-boundary') {
      const parent = nodes.find(n => 
        (n.type === 'user-task' || n.type === 'service-task' || n.type === 'api-task') &&
        x > n.position.x && x < n.position.x + n.width &&
        y > n.position.y && y < n.position.y + n.height
      );
      if (!parent) return; // Cannot be alone
      attachedTo = parent.uid;
      // Snap to bottom right by default on drop
      finalPosition = { 
        x: parent.position.x + parent.width - width/2, 
        y: parent.position.y + parent.height - height/2 
      };
    }

    const newNode: BpmnNode = {
      uid: Math.random().toString(36).substr(2, 12), // Truly unique internal ID
      id: initialId,
      type,
      position: finalPosition,
      width,
      height,
      attachedTo,
      data: {
        label: type === 'pool' ? 'Participant' : `New ${type.replace('-', ' ')}`,
        inputVariables: [],
        outputVariables: [],
      }
    };
    setNodes(nds => [...nds, newNode]);
    setSelectedNodeUids([newNode.uid]);
    setSelectedEdgeId(null);
  };

  const buildExportObject = () => {
    const typeMapping: Record<NodeType, string> = {
      'start': 'StartEvent', 
      'end': 'EndEvent', 
      'user-task': 'HumanTask',
      'service-task': 'ServiceTask',
      'api-task': 'APITask',
      'ai-task': 'AiTask',
      'code-task': 'CodeTask',
      'call-activity': 'CallActivity',
      'gateway': 'ExclusiveGateway', 
      'parallel-gateway': 'ParallelGateway',
      'timer-event': 'TimerEvent',
      'message-start': 'MessageStartEvent',
      'message-intermediate-catch': 'MessageIntermediateCatchEvent',
      'message-intermediate-throw': 'MessageIntermediateThrowEvent',
      'error-boundary': 'ErrorBoundaryEvent',
      'message-boundary': 'MessageBoundaryEvent',
      'timer-boundary': 'TimerEvent',
      'pool': 'Participant'
    };

    const nodesData = nodes.map(node => {
      const nextIds = edges
        .filter(e => e.source === node.uid)
        .map(e => nodes.find(n => n.uid === e.target)?.id)
        .filter(Boolean);

      const base: any = {
        id: node.id,
        name: node.data.label,
        type: typeMapping[node.type] || node.type,
        position: node.position,
        width: node.width,
        height: node.height,
        next: nextIds
      };

       if (node.type === 'user-task') {
         base.config = {
           formId: node.data.formId,
           assignee: node.data.assignee,
            candidateGroups: node.data.candidateGroups,
           inputs: (node.data.inputVariables || []).map(v => ({
              targetName: String(v.name || ''),
              type: v.type,
              source: v.mappingType,
              value: v.value
           })),
           outputs: (node.data.outputVariables || []).map(v => ({
              target: 'variable',
              sourceName: String(v.name || ''),
              type: v.type,
              value: v.value
           }))
         };
       }

       if (node.type === 'api-task') {
         base.properties = {
           url: node.data.apiEndpoint,
           method: node.data.method || 'GET',
           outputs: (node.data.outputVariables || []).map(v => ({
              target: 'variable',
              sourceName: String(v.name || ''),
              type: v.type,
              value: v.value
           }))
         };
        const authType = node.data.apiAuthType || 'none';
        const authRef = (node.data.apiAuthRef || '').trim();
        if (authType !== 'none' && authRef) {
          const auth: any = {
            type: authType,
            ref: authRef
          };
          if (authType === 'apikey') {
            auth.in = node.data.apiAuthIn || 'header';
            auth.key = (node.data.apiAuthKey || 'X-API-Key').trim() || 'X-API-Key';
          }
          base.properties.auth = auth;
        }
        if (node.data.body) { 
          try { base.properties.body = JSON.parse(node.data.body); } 
          catch(e) { base.properties.body = node.data.body; } 
        }
      }

       if (node.type === 'service-task') {
         base.config = {
           inputs: (node.data.inputVariables || []).map(v => ({
              targetName: String(v.name || ''),
              type: v.type,
              source: v.mappingType,
              value: v.value
           })),
           outputs: (node.data.outputVariables || []).map(v => ({
              target: 'variable',
              sourceName: safeString(v.name),
              type: v.type,
              value: v.value
           }))
         };
       }

       if (node.type === 'ai-task') {
         base.properties = {
           providerId: node.data.aiProviderId || 'openai',
           modelName: node.data.aiModelName || 'gpt-3.5-turbo',
           credentialId: node.data.aiCredentialId || undefined,
           credentialRef: node.data.aiCredentialRefName || undefined,
           endpoint: node.data.aiEndpoint || undefined,
           promptTemplate: node.data.aiPromptTemplate || '',
           systemPrompt: node.data.aiSystemPrompt || undefined,
           userPrompt: node.data.aiUserPrompt || undefined,
           outputVariable: node.data.aiOutputVariable || '',
           tuningParams: node.data.aiTuningParams || {
             temperature: 0.7,
             topP: 1.0,
             maxTokens: 2000,
             frequencyPenalty: 0,
             presencePenalty: 0,
             retryCount: 0,
             backoffMultiplier: 2.0,
             initialDelayMs: 1000
           }
         };
       }

       if (node.type === 'call-activity') {
         base.config = {
           processKey: node.data.callActivityProcessKey || '',
           propagateAllVariables: node.data.propagateAllVariables || false,
           inputs: (node.data.inputVariables || []).map(v => ({
              targetName: String(v.name || ''),
              type: v.type,
              source: v.mappingType,
              value: v.value
           })),
           outputs: (node.data.outputVariables || []).map(v => ({
              source: v.mappingType,
              sourceValue: String(v.name || ''),
              type: v.type,
              targetVariable: v.value
           }))
         };
       }

       if (node.type === 'code-task') {
         base.properties = {
           inputs: (node.data.inputVariables || []).map(v => ({
              targetName: String(v.name || ''),
              type: v.type,
              source: v.mappingType,
              value: v.value
           })),
           outputs: (node.data.outputVariables || []).map(v => ({
              source: v.mappingType,
              sourceValue: String(v.name || ''),
              type: v.type,
              targetVariable: v.value
           }))
         };
       }

      if (node.type === 'timer-event') {
        base.properties = {
          timeoutSeconds: node.data.timeoutSeconds ?? null
        };
      }

       if (['message-start', 'message-intermediate-catch', 'message-intermediate-throw'].includes(node.type)) {
         const isCatch = ['message-start', 'message-intermediate-catch'].includes(node.type);
         base.message = {
           name: node.data.messageName,
           correlationKeys: node.data.correlationKeys ? node.data.correlationKeys.split(',').map(k => k.trim()) : [],
           timeoutSeconds: node.data.timeoutSeconds ?? null,
           payload: isCatch
             ? (node.data.outputVariables || []).map(v => ({
                 source: v.mappingType,
                 sourceValue: String(v.name || ''),
                 type: v.type,
                 targetVariable: v.value
               }))
             : (node.data.inputVariables || []).map(v => ({
                 targetName: String(v.name || ''),
                 type: v.type,
                 source: v.mappingType,
                 value: v.value
               }))
         };

        // Backward-compatible shape used by backend MessageEvent handler
        if (isCatch) {
          base.properties = {
            messageName: node.data.messageName,
            correlationKey: node.data.correlationKeys || '',
            timeoutSeconds: node.data.timeoutSeconds ?? null
          };
        }
      }

      if (node.type === 'error-boundary') {
        base.config = {
          errorCode: node.data.errorCode,
          exceptionVariable: node.data.exceptionVariable || undefined
        };
        base.attachedTo = node.attachedTo
          ? (nodes.find(candidate => candidate.uid === node.attachedTo)?.id || node.attachedTo)
          : undefined;
      }

       if (node.type === 'message-boundary') {
         base.message = {
           name: node.data.messageName,
           correlationKeys: node.data.correlationKeys ? node.data.correlationKeys.split(',').map(k => k.trim()) : [],
           payload: (node.data.outputVariables || []).map(v => ({
             source: v.mappingType,
             sourceValue: String(v.name || ''),
             type: v.type,
             targetVariable: v.value
           }))
         };
         base.attachedTo = node.attachedTo
           ? (nodes.find(candidate => candidate.uid === node.attachedTo)?.id || node.attachedTo)
           : undefined;
       }

      if (node.type === 'timer-boundary') {
        base.properties = {
          timeoutSeconds: node.data.timeoutSeconds ?? null,
          interrupting: node.data.interrupting !== false
        };
        base.attachedTo = node.attachedTo
          ? (nodes.find(candidate => candidate.uid === node.attachedTo)?.id || node.attachedTo)
          : undefined;
      }
      return base;
    });

    return {
      processId: processId,
      processName: processName,
      metadata: {
        exportedAt: new Date().toISOString(),
         version: "1.0"
       },
       variables: variables.map(v => ({
         name: safeString(v.name),
         type: v.type,
         initialValue: v.defaultValue
       })),
       nodes: nodesData,
      flows: edges.map(e => ({
        from: nodes.find(n => n.uid === e.source)?.id, 
        to: nodes.find(n => n.uid === e.target)?.id, 
        condition: e.condition || null,
        waypoints: e.waypoints && e.waypoints.length > 0 ? e.waypoints : undefined
      }))
    };
  };

  const handleExport = () => {
    if (!validationState.isValid) {
      toast.error(validationState.errors[0] || 'Validation failed. Resolve BPM issues before export.');
      return;
    }

    const exportObject = buildExportObject();

    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(exportObject, null, 2));
    const link = document.createElement('a');
    link.href = dataStr;
    link.download = `${processId || 'process'}.json`;
    link.click();
  };

  const handleDeployProcess = async () => {
    if (!validationState.isValid) {
      toast.error(validationState.errors[0] || 'Validation failed. Resolve BPM issues before deploy.');
      return;
    }
    if (isDeployingProcess) return;

    setIsDeployingProcess(true);
    try {
      await processService.deployProcess(buildExportObject());
      toast.success('Process deployed successfully.');
    } catch (error) {
      if (isAuthRequiredError(error)) {
        toast.error(error.message);
        return;
      }
      const message = error instanceof Error ? error.message : 'Unexpected deploy error';
      toast.error(message);
    } finally {
      setIsDeployingProcess(false);
    }
  };

  // Navigation handlers
  const handleCreateProcess = () => {
    // Reset process editor state
    setNodes([]);
    setEdges([]);
    setVariablesRaw([]);
    setProcessId(`process_${Date.now()}`);
    setProcessName('');
    setSelectedNodeUids([]);
    setSelectedEdgeId(null);
    setEditorMode('process-editor');
  };

  const handleCreateForm = () => {
    // Form modeler will initialize its own state
    setSelectedFormKey(null);
    setEditorMode('form-editor');
  };

  const handleBackToWelcome = () => {
    setEditorMode('welcome');
    setCurrentEditingForm(null);
  };

  const handleImport = (data: any) => {
    if (!data || !data.nodes) return;

    const reverseTypeMapping: Record<string, NodeType> = {
      'StartEvent': 'start',
      'EndEvent': 'end',
      'HumanTask': 'user-task',
      'UserTask': 'user-task',
      'ServiceTask': 'service-task',
      'APITask': 'api-task',
      'ExclusiveGateway': 'gateway',
      'ParallelGateway': 'parallel-gateway',
      'TimerEvent': 'timer-event',
      'MessageStartEvent': 'message-start',
      'MessageIntermediateCatchEvent': 'message-intermediate-catch',
      'MessageIntermediateThrowEvent': 'message-intermediate-throw',
      'ErrorBoundaryEvent': 'error-boundary',
      'MessageBoundaryEvent': 'message-boundary',
      'Participant': 'pool',
      'Pool': 'pool'
    };

     // 1. Import Variables
     const importedVariables: ProcessVariable[] = (data.variables || []).map((v: any) => ({
       id: Math.random().toString(36).substr(2, 9),
       name: String(v.name || ''),
       type: v.type,
       defaultValue: v.initialValue
     }));

    // 2. Import Nodes
    const idToUidMap = new Map<string, string>();
    const importedNodes: BpmnNode[] = data.nodes.map((node: any) => {
      const uid = Math.random().toString(36).substr(2, 12);
      idToUidMap.set(node.id, uid);

      let width = 140; 
      let height = 80;
      let type = reverseTypeMapping[node.type] || node.type as NodeType;
      if (type === 'timer-event' && node.attachedTo) {
        type = 'timer-boundary';
      }
      if (type === 'pool') {
        width = Number(node.width) || 640;
        height = Number(node.height) || 260;
      } else if (['start', 'end', 'gateway', 'parallel-gateway', 'timer-event'].includes(type)) {
        width = 40; height = 40;
      } else {
        width = 120; height = 60;
      }
      if (type === 'timer-boundary') {
        width = 30; height = 30;
      }

      const newNode: BpmnNode = {
        uid,
        id: node.id,
        type,
        position: node.position || { x: 100, y: 100 },
        width,
        height,
        data: {
          label: node.name || node.id,
          inputVariables: [],
          outputVariables: [],
        }
      };

       if (type === 'user-task' && node.config) {
         newNode.data.formId = node.config.formId;
         newNode.data.assignee = node.config.assignee;
          newNode.data.candidateGroups = node.config.candidateGroups;
         newNode.data.inputVariables = (node.config.inputs || []).map((i: any) => ({
           id: Math.random().toString(36).substr(2, 9),
           name: String(i.targetName || ''),
           type: i.type,
           mappingType: i.source,
           value: i.value
         }));
         newNode.data.outputVariables = (node.config.outputs || []).map((o: any) => ({
           id: Math.random().toString(36).substr(2, 9),
           name: String(o.sourceName || ''),
           type: o.type,
           mappingType: 'variable',
           value: o.value
         }));
       }

      if (type === 'api-task' && (node.properties || node.service)) {
        const apiConfig = node.properties || node.service;
        newNode.data.apiEndpoint = apiConfig.url;
        newNode.data.method = apiConfig.method;
        newNode.data.body = typeof apiConfig.body === 'object' ? JSON.stringify(apiConfig.body, null, 2) : apiConfig.body;

        if (apiConfig.auth && typeof apiConfig.auth === 'object') {
          newNode.data.apiAuthType = apiConfig.auth.type || 'none';
          newNode.data.apiAuthRef = apiConfig.auth.ref || '';
          if (apiConfig.auth.type === 'apikey') {
            newNode.data.apiAuthIn = apiConfig.auth.in || 'header';
            newNode.data.apiAuthKey = apiConfig.auth.key || 'X-API-Key';
          }
        }
        
         if (apiConfig.outputs) {
           newNode.data.outputVariables = (apiConfig.outputs || []).map((o: any) => ({
             id: Math.random().toString(36).substr(2, 9),
             name: String(o.sourceValue || o.sourceName || ''),
             type: o.type,
             mappingType: 'variable',
             value: o.targetVariable || o.value
           }));
         }
      }

       if (type === 'service-task' && node.config) {
         if (node.config.inputs) {
           newNode.data.inputVariables = (node.config.inputs || []).map((i: any) => ({
             id: Math.random().toString(36).substr(2, 9),
             name: String(i.targetName || ''),
             type: i.type,
             mappingType: i.source,
             value: i.value
           }));
         }
         if (node.config.outputs) {
           newNode.data.outputVariables = (node.config.outputs || []).map((o: any) => ({
             id: Math.random().toString(36).substr(2, 9),
             name: String(o.sourceValue || o.sourceName || ''),
             type: o.type,
             mappingType: 'variable',
             value: o.targetVariable || o.value
           }));
         }
       }

      if (type === 'timer-event') {
        if (node.properties?.timeoutSeconds !== undefined && node.properties?.timeoutSeconds !== null) {
          newNode.data.timeoutSeconds = Number(node.properties.timeoutSeconds);
        }
      }

      if (['message-start', 'message-intermediate-catch', 'message-intermediate-throw'].includes(type) && node.message) {
        newNode.data.messageName = node.message.name;
        newNode.data.correlationKeys = Array.isArray(node.message.correlationKeys) ? node.message.correlationKeys.join(', ') : node.message.correlationKeys;
        if (node.message.timeoutSeconds !== undefined && node.message.timeoutSeconds !== null) {
          newNode.data.timeoutSeconds = Number(node.message.timeoutSeconds);
        } else if (node.properties?.timeoutSeconds !== undefined && node.properties?.timeoutSeconds !== null) {
          newNode.data.timeoutSeconds = Number(node.properties.timeoutSeconds);
        }
        
         const isCatch = ['message-start', 'message-intermediate-catch'].includes(type);
         if (node.message.payload) {
           if (isCatch) {
             newNode.data.outputVariables = (node.message.payload || []).map((o: any) => ({
               id: Math.random().toString(36).substr(2, 9),
               name: String(o.sourceValue || o.sourceName || ''),
               type: o.type,
               mappingType: 'variable',
               value: o.targetVariable || o.value
             }));
           } else {
             newNode.data.inputVariables = (node.message.payload || []).map((i: any) => ({
               id: Math.random().toString(36).substr(2, 9),
               name: String(i.targetName || ''),
               type: i.type,
               mappingType: i.source,
               value: i.value
             }));
           }
         }
      }

      if (type === 'error-boundary' && node.error) {
        newNode.data.errorCode = node.error.code;
        newNode.attachedTo = idToUidMap.get(node.attachedTo) || node.attachedTo;
      }

       if (type === 'message-boundary' && node.message) {
         newNode.data.messageName = node.message.name;
         newNode.data.correlationKeys = Array.isArray(node.message.correlationKeys) ? node.message.correlationKeys.join(', ') : node.message.correlationKeys;
         newNode.attachedTo = idToUidMap.get(node.attachedTo) || node.attachedTo;
         if (node.message.payload) {
           newNode.data.outputVariables = (node.message.payload || []).map((o: any) => ({
             id: Math.random().toString(36).substr(2, 9),
             name: String(o.sourceName || ''),
             type: o.type,
             mappingType: 'variable',
             value: o.value
           }));
         }
       }

      if (type === 'timer-boundary') {
        if (node.properties?.timeoutSeconds !== undefined && node.properties?.timeoutSeconds !== null) {
          newNode.data.timeoutSeconds = Number(node.properties.timeoutSeconds);
        }
        newNode.data.interrupting = node.properties?.interrupting !== false;
        newNode.attachedTo = idToUidMap.get(node.attachedTo) || node.attachedTo;
      }

      return newNode;
    });

    // 3. Import Edges (Flows)
    const importedEdges: BpmnEdge[] = (data.flows || []).map((flow: any) => ({
      id: `edge_${Math.random().toString(36).substr(2, 9)}`,
      source: idToUidMap.get(flow.from) || '',
      target: idToUidMap.get(flow.to) || '',
      condition: flow.condition,
      waypoints: Array.isArray(flow.waypoints)
        ? flow.waypoints
            .map((point: any) => ({ x: Number(point.x), y: Number(point.y) }))
            .filter((point: Position) => Number.isFinite(point.x) && Number.isFinite(point.y))
        : undefined
    })).filter((e: BpmnEdge) => e.source && e.target);

    // Update State
    setProcessId(data.processId || `process_${Date.now()}`);
    setProcessName(data.processName || data.name || '');
    setVariables(importedVariables);
    setNodes(importedNodes);
    setEdges(importedEdges);
    setSelectedNodeUids([]);
    setSelectedEdgeId(null);
  };

  const handleUpdateNode = (uid: string, data: Partial<BpmnNode['data']>) => setNodes(nds => nds.map(n => n.uid === uid ? { ...n, data: { ...n.data, ...data } } : n));
  const handleUpdateNodeFrame = (uid: string, frame: Partial<Pick<BpmnNode, 'width' | 'height'>>) => setNodes(nds => nds.map(n => n.uid === uid ? { ...n, ...frame } : n));
  
  const handleUpdateNodeId = (uid: string, newId: string) => {
    if (!newId.trim()) return;
    setNodes(nds => nds.map(n => n.uid === uid ? { ...n, id: newId } : n));
  };

  const handleUpdateEdge = (id: string, data: Partial<BpmnEdge>) => setEdges(eds => eds.map(e => e.id === id ? { ...e, ...data } : e));
  
  const handleDeleteNodes = useCallback((uids: string[]) => {
    setNodes(nds => nds.filter(n => !uids.includes(n.uid)));
    setEdges(eds => eds.filter(e => !uids.includes(e.source) && !uids.includes(e.target)));
  }, []);

  // Form library management methods
  const handleAddForm = useCallback((form: FormDefinition) => {
    setFormLibrary(lib => new Map(lib).set(form.formKey, form));
    setSelectedFormKey(form.formKey);
    toast.success(`Form "${form.name || form.formKey}" added to library`);
  }, []);

  const handleFormChange = useCallback((form: FormDefinition) => {
    // Update form library in real-time as user edits
    setFormLibrary(lib => new Map(lib).set(form.formKey, form));
    // Also track the current form being edited
    setCurrentEditingForm(form);
  }, []);

  const handleRemoveForm = useCallback((formKey: string) => {
    setFormLibrary(lib => {
      const newLib = new Map(lib);
      newLib.delete(formKey);
      return newLib;
    });
    if (selectedFormKey === formKey) {
      setSelectedFormKey(null);
    }
  }, [selectedFormKey]);

  const handleSelectForm = useCallback((form: FormDefinition) => {
    setSelectedFormKey(form.formKey);
  }, []);

  const handleExportForm = () => {
    const formToExport = currentEditingForm;
    if (!formToExport) {
      toast.error('No form to export');
      return;
    }
    downloadForm(formToExport, `form-${formToExport.formKey}.json`);
    toast.success('Form exported successfully');
  };

  const handleImportForm = (data: any) => {
    const result = importForm(data);
    if (!result.success || !result.form) {
      toast.error(result.error || 'Failed to import form');
      return;
    }
    handleAddForm(result.form);
    toast.success(`Form "${result.form.name}" imported successfully`);
  };

  const handleDeployForm = async () => {
    const formToDeploy = currentEditingForm;
    if (!formToDeploy) {
      toast.error('No form to deploy');
      return;
    }
    setIsDeployingForm(true);
    try {
      const schema = generateJsonSchema(formToDeploy);
      const response = await fetchWithAuth(`${API_BASE_URL}/forms`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(schema)
      });

      if (response.ok) {
        toast.success(`Form "${formToDeploy.name}" deployed successfully`);
      } else {
        const errorData = await response.json().catch(() => ({}));
        toast.error(`Deployment failed: ${errorData.message || response.statusText}`);
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unexpected deploy error';
      toast.error(message);
    } finally {
      setIsDeployingForm(false);
    }
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (['INPUT', 'TEXTAREA', 'SELECT'].includes((e.target as HTMLElement).tagName)) return;
      if ((e.key === 'Delete' || e.key === 'Backspace') && selectedNodeUids.length > 0) {
        handleDeleteNodes(selectedNodeUids);
        setSelectedNodeUids([]);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [selectedNodeUids, handleDeleteNodes]);

  const handleLogout = () => {
    processService.clearSession();
    setCurrentUser(null);
    setPermissions([]);
  };

  if (authLoading) {
    return <div className="min-h-screen flex items-center justify-center text-slate-600">Loading session...</div>;
  }

  if (!currentUser) {
    return <ModelerLoginView onLogin={(username, perms) => {
      setCurrentUser(username);
      setPermissions(perms);
    }} />;
  }

  if (!permissions.includes('ACCESS_BPM_MODELER')) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-100">
        <div className="bg-white border border-slate-200 rounded-xl p-6 text-center">
          <h2 className="text-lg font-semibold text-slate-800">Access denied</h2>
          <p className="text-slate-500 mt-1">Your account does not have BPM Modeler access.</p>
          <button onClick={handleLogout} className="mt-4 px-4 py-2 bg-slate-900 text-white rounded-lg">Sign out</button>
        </div>
      </div>
    );
  }

  // Render based on editor mode
  if (editorMode === 'welcome') {
    return (
      <div className="h-screen overflow-hidden">
        <Toaster position="top-right" richColors />
        <WelcomeScreen
          onCreateProcess={handleCreateProcess}
          onCreateForm={handleCreateForm}
          currentUser={currentUser}
          onLogout={handleLogout}
          theme={theme}
          onToggleTheme={toggleTheme}
        />
      </div>
    );
  }

  if (editorMode === 'process-editor') {
    return (
      <div className="process-modeler flex flex-col h-screen" data-theme={theme}>
        <Toaster position="top-right" richColors />
        
        {/* Process Editor Navbar */}
        <ModelerNavbar
          title={processName || 'New Process'}
          subtitle={processId}
          resourceType="process"
          onBack={handleBackToWelcome}
          onSave={handleDeployProcess}
          onExport={handleExport}
          onImport={handleImport}
          isSaving={isDeployingProcess}
          currentUser={currentUser}
          onLogout={handleLogout}
          theme={theme}
          onToggleTheme={toggleTheme}
        />

        {/* Toolbar */}
        <Toolbar 
          onClear={() => {setNodes([]); setEdges([]); setVariables([]); setProcessId(`process_${Date.now()}`); setSelectedNodeUids([]); setSelectedEdgeId(null);}} 
          isExportDisabled={!validationState.isValid}
          validationErrors={validationState.errors}
          validationWarnings={validationState.warnings}
          currentView="bpmn"
          onViewChange={() => {}}
          theme={theme}
          onToggleTheme={toggleTheme}
        />

        {/* Canvas */}
        <div className="flex flex-1 overflow-hidden">
          <Palette onDragStart={(e, type) => e.dataTransfer.setData('application/reactflow', type)} />
          <div className="flex-1 relative flex flex-col">
            <button
              type="button"
              onClick={() => setIsPropertiesPanelVisible((visible) => !visible)}
              className="absolute right-4 top-16 z-30 flex h-9 w-9 items-center justify-center rounded-md border border-slate-200 bg-white/95 text-slate-600 shadow-sm backdrop-blur-sm transition-colors hover:bg-slate-100 hover:text-slate-900"
              title={isPropertiesPanelVisible ? 'Hide properties panel' : 'Show properties panel'}
              aria-label={isPropertiesPanelVisible ? 'Hide properties panel' : 'Show properties panel'}
            >
              {isPropertiesPanelVisible ? <PanelRightClose className="h-4 w-4" /> : <PanelRightOpen className="h-4 w-4" />}
            </button>
            <Canvas 
              nodes={nodes} edges={edges} selectedNodeUids={selectedNodeUids} selectedEdgeId={selectedEdgeId}
              invalidNodeUids={invalidNodeUids}
              warningNodeUids={warningNodeUids}
              invalidEdgeIds={invalidEdgeIds}
              warningEdgeIds={warningEdgeIds}
              onSelectNodes={setSelectedNodeUids} onSelectEdge={setSelectedEdgeId}
              onNodesChange={setNodes} onEdgesChange={setEdges} onDrop={handleDrop}
            />
          </div>
          {isPropertiesPanelVisible && (
            <PropertiesPanel
              selectedNodeUids={selectedNodeUids}
              nodes={nodes}
              selectedEdge={edges.find(e => e.id === selectedEdgeId) || null}
              processVariables={variables}
              processId={processId}
              processName={processName}
              onUpdateProcessId={setProcessId}
              onUpdateProcessName={setProcessName}
              onUpdateNode={handleUpdateNode}
              onUpdateNodeFrame={handleUpdateNodeFrame}
              onUpdateNodeId={handleUpdateNodeId}
              onUpdateEdge={handleUpdateEdge}
              onUpdateVariables={setVariables}
              onDeleteNode={uid => handleDeleteNodes([uid])}
              onDeleteEdge={id => setEdges(eds => eds.filter(e => e.id !== id))}
              onFocusValidationIssue={handleFocusValidationIssue}
              validation={{
                duplicateNodeIds: validationState.duplicateNodeIds,
                duplicateGlobalVars: validationState.duplicateGlobalVars,
                issues: validationState.issues
              }}
            />
          )}
        </div>
      </div>
    );
  }

  if (editorMode === 'form-editor') {
    return (
      <div className="form-modeler flex flex-col h-screen" data-theme={theme}>
        <Toaster position="top-right" richColors />
        
        {/* Form Editor Navbar */}
        <ModelerNavbar
          title={currentEditingForm?.name || 'New Form'}
          resourceType="form"
          onBack={handleBackToWelcome}
          onSave={handleDeployForm}
          onExport={handleExportForm}
          onImport={handleImportForm}
          isSaving={isDeployingForm}
          currentUser={currentUser}
          onLogout={handleLogout}
          theme={theme}
          onToggleTheme={toggleTheme}
        />

        {/* Form Editor */}
        <div className="flex flex-1 overflow-hidden">
          <FormModeler 
            formLibrary={formLibrary}
            selectedFormKey={selectedFormKey}
            onFormSave={handleAddForm}
            onFormChange={handleFormChange}
          />
        </div>
      </div>
    );
  }

  // Default: welcome screen
  return (
    <div className="h-screen overflow-hidden">
      <Toaster position="top-right" richColors />
      <WelcomeScreen
        onCreateProcess={handleCreateProcess}
        onCreateForm={handleCreateForm}
        theme={theme}
        onToggleTheme={toggleTheme}
      />
    </div>
  );
};

const ModelerLoginView: React.FC<{ onLogin: (username: string, permissions: string[]) => void }> = ({ onLogin }) => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const session = await processService.login(username.trim(), password);
      onLogin(session.username, session.permissions);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-950 via-slate-900 to-blue-950 p-4">
      <div className="w-full max-w-md">
        {/* Card */}
        <div className="bg-white/5 backdrop-blur-sm border border-white/10 rounded-2xl p-8 shadow-2xl">
          <div className="flex flex-col items-center mb-8">
            <div className="bg-blue-600 p-3 rounded-xl mb-4 shadow-lg shadow-blue-600/40 ring-4 ring-blue-600/20">
              <ShieldCheck className="text-white" size={28} />
            </div>
            <h1 className="text-2xl font-bold text-white">Easy BPM Modeler</h1>
            <p className="text-slate-400 mt-1 text-sm">Sign in to design and deploy processes</p>
          </div>

          <form onSubmit={submit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-1.5">Username</label>
              <div className="relative">
                <User className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" size={16} />
                <input
                  type="text"
                  className="w-full pl-9 pr-4 py-2.5 rounded-lg border border-white/10 bg-white/5 text-white placeholder-slate-500 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all text-sm"
                  placeholder="Enter username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-1.5">Password</label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" size={16} />
                <input
                  type="password"
                  className="w-full pl-9 pr-4 py-2.5 rounded-lg border border-white/10 bg-white/5 text-white placeholder-slate-500 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all text-sm"
                  placeholder="Enter password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-blue-600 hover:bg-blue-500 text-white font-semibold py-2.5 rounded-lg transition-colors shadow-lg shadow-blue-600/30 flex items-center justify-center gap-2 mt-2 text-sm"
            >
              {loading ? <Loader2 className="animate-spin" size={18} /> : 'Sign In'}
            </button>
            {error && <p className="text-sm text-red-400 mt-2">{error}</p>}
          </form>
        </div>
        <p className="text-center text-[11px] text-slate-600 mt-4">Easy BPM · Process Design & Deployment</p>
      </div>
    </div>
  );
};

export default App;
