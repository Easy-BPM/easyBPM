import React, { useState, useCallback, useEffect, useMemo } from 'react';
import { Palette } from './components/Palette';
import { Canvas } from './components/Canvas';
import { PropertiesPanel } from './components/PropertiesPanel';
import { Toolbar } from './components/Toolbar';
import { FormModeler } from './components/FormModeler';
import { BpmnNode, BpmnEdge, ProcessVariable, NodeType, AppView, ValidationIssue, ValidationSummary } from './types';
import { generateId, snapToGrid } from './utils/geometry';
import { validateId } from './utils/validation';
import { Toaster, toast } from 'sonner';
import { processService } from './services/processService';

const BOUNDARY_TYPES: NodeType[] = ['error-boundary', 'message-boundary', 'timer-boundary'];
const START_TYPES: NodeType[] = ['start', 'message-start'];
const END_TYPES: NodeType[] = ['end'];
const TASK_TYPES: NodeType[] = ['user-task', 'service-task', 'api-task', 'code-task'];
const FORM_KEY_PATTERN = /^[A-Za-z][A-Za-z0-9_-]*$/;

// Helper to safely convert values to strings, preventing "[object Object]"
const safeString = (value: any): string => {
  if (typeof value === 'string') return value;
  if (value === null || value === undefined) return '';
  if (typeof value === 'object') return '';
  return String(value);
};

const App: React.FC = () => {
   const [nodes, setNodes] = useState<BpmnNode[]>([]);
   const [edges, setEdges] = useState<BpmnEdge[]>([]);
   const [variablesRaw, setVariablesRaw] = useState<ProcessVariable[]>([]);
   const [processId, setProcessId] = useState<string>(`process_${Date.now()}`);
   const [processName, setProcessName] = useState<string>('');
   const [selectedNodeUids, setSelectedNodeUids] = useState<string[]>([]);
   const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null);
   const [currentView, setCurrentView] = useState<AppView>('bpmn');
   const [isDeploying, setIsDeploying] = useState(false);
   const [currentUser, setCurrentUser] = useState<string | null>(null);
   const [permissions, setPermissions] = useState<string[]>([]);
   const [authLoading, setAuthLoading] = useState(true);

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

      const unreachable = nodes.filter(node => !visited.has(node.uid) && !BOUNDARY_TYPES.includes(node.type));
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

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    const type = e.dataTransfer.getData('application/reactflow') as NodeType;
    if (!type) return;

    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    let width = 120; 
    let height = 60;
    if (['start', 'end', 'gateway', 'parallel-gateway', 'timer-event', 'message-start', 'message-intermediate-throw', 'error-boundary', 'message-boundary', 'timer-boundary'].includes(type)) {
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
        label: `New ${type.replace('-', ' ')}`,
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
      'gateway': 'ExclusiveGateway', 
      'parallel-gateway': 'ParallelGateway',
      'timer-event': 'TimerEvent',
      'message-start': 'MessageStartEvent',
      'message-intermediate-catch': 'MessageIntermediateCatchEvent',
      'message-intermediate-throw': 'MessageIntermediateThrowEvent',
      'error-boundary': 'ErrorBoundaryEvent',
      'message-boundary': 'MessageBoundaryEvent',
      'timer-boundary': 'TimerEvent'
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
              source: v.mappingType,
              sourceValue: String(v.name || ''),
              type: v.type,
              targetVariable: v.value
           }))
         };
       }

       if (node.type === 'api-task') {
         base.properties = {
           url: node.data.apiEndpoint,
           method: node.data.method || 'GET',
           outputs: (node.data.outputVariables || []).map(v => ({
              source: v.mappingType,
              sourceValue: String(v.name || ''),
              type: v.type,
              targetVariable: v.value
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
              source: v.mappingType,
              sourceValue: safeString(v.name),
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
        condition: e.condition || null 
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

  const handleDeploy = async () => {
    if (!validationState.isValid) {
      toast.error(validationState.errors[0] || 'Validation failed. Resolve BPM issues before deploy.');
      return;
    }
    if (isDeploying) return;

    setIsDeploying(true);
    try {
      await processService.deployProcess(buildExportObject());
      toast.success('Process deployed successfully.');
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unexpected deploy error';
      toast.error(message);
    } finally {
      setIsDeploying(false);
    }
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
      'MessageBoundaryEvent': 'message-boundary'
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
      if (['start', 'end', 'gateway', 'parallel-gateway', 'timer-event'].includes(type)) {
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
           mappingType: o.target,
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
             mappingType: o.source || o.target || 'variable',
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
             mappingType: o.source || o.target || 'variable',
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
               mappingType: o.source || o.target || 'variable',
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
             mappingType: o.target,
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
      condition: flow.condition
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
  
  const handleUpdateNodeId = (uid: string, newId: string) => {
    if (!newId.trim()) return;
    setNodes(nds => nds.map(n => n.uid === uid ? { ...n, id: newId } : n));
  };

  const handleUpdateEdge = (id: string, data: Partial<BpmnEdge>) => setEdges(eds => eds.map(e => e.id === id ? { ...e, ...data } : e));
  
  const handleDeleteNodes = useCallback((uids: string[]) => {
    setNodes(nds => nds.filter(n => !uids.includes(n.uid)));
    setEdges(eds => eds.filter(e => !uids.includes(e.source) && !uids.includes(e.target)));
  }, []);

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

  return (
    <div className="flex flex-col h-screen bg-slate-50">
      <Toaster position="top-right" richColors />
      <Toolbar 
        onClear={() => {setNodes([]); setEdges([]); setVariables([]); setProcessId(`process_${Date.now()}`); setSelectedNodeUids([]); setSelectedEdgeId(null);}} 
        onExport={handleExport} 
        onDeploy={handleDeploy}
        onImport={handleImport}
        isExportDisabled={!validationState.isValid}
        validationErrors={validationState.errors}
        validationWarnings={validationState.warnings}
        isDeploying={isDeploying}
        currentView={currentView}
        onViewChange={setCurrentView}
      />
      <div className="flex flex-1 overflow-hidden">
        {currentView === 'bpmn' ? (
          <>
            <Palette onDragStart={(e, type) => e.dataTransfer.setData('application/reactflow', type)} />
            <div className="flex-1 relative flex flex-col">
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
          </>
        ) : (
          <FormModeler />
        )}
      </div>
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
    <div className="min-h-screen flex items-center justify-center bg-slate-100 p-4">
      <div className="bg-white p-8 rounded-2xl shadow-xl w-full max-w-md border border-slate-200">
        <h1 className="text-2xl font-bold text-slate-800">Easy BPM Modeler</h1>
        <p className="text-slate-500 mt-1 mb-6">Sign in to design and deploy processes</p>
        <form onSubmit={submit} className="space-y-4">
          <input className="w-full rounded-lg border border-slate-300 px-3 py-2.5" placeholder="Username" value={username} onChange={e => setUsername(e.target.value)} />
          <input className="w-full rounded-lg border border-slate-300 px-3 py-2.5" type="password" placeholder="Password" value={password} onChange={e => setPassword(e.target.value)} />
          <button type="submit" disabled={loading} className="w-full bg-blue-600 hover:bg-blue-700 text-white py-2.5 rounded-lg">
            {loading ? 'Signing in...' : 'Sign In'}
          </button>
          {error && <p className="text-sm text-red-600">{error}</p>}
        </form>
      </div>
    </div>
  );
};

export default App;