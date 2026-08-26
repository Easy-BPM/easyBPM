import React, { useState } from 'react';
import {
  Bot,
  Brain,
  Clock3,
  Code,
  GitFork,
  Layers,
  Mail,
  Plus,
  RotateCcw,
  Settings,
  User,
  Zap,
  ZoomIn,
  ZoomOut
} from 'lucide-react';
import { WorkflowDefinition, WorkflowNode } from '../types';

type Props = {
  definition: WorkflowDefinition;
  nodeHistory: string[];
  currentNodes: string[];
  expanded?: boolean;
};

type Edge = {
  from: string;
  to: string;
};

type NodeTone = {
  stroke: string;
  icon: string;
  label: string;
};

const MIN_ZOOM = 0.5;
const MAX_ZOOM = 1.5;
const ZOOM_STEP = 0.1;

const DEFAULT_NODE_TONE: NodeTone = {
  stroke: '#94a3b8',
  icon: '#64748b',
  label: 'Task'
};

const TASK_TONES: Record<string, NodeTone> = {
  human: { stroke: '#3b82f6', icon: '#60a5fa', label: 'Human task' },
  service: { stroke: '#f59e0b', icon: '#f59e0b', label: 'Service task' },
  api: { stroke: '#a855f7', icon: '#c084fc', label: 'API task' },
  code: { stroke: '#6366f1', icon: '#818cf8', label: 'Code task' },
  ai: { stroke: '#ec4899', icon: '#f472b6', label: 'AI task' },
  agent: { stroke: '#22d3ee', icon: '#67e8f9', label: 'Agent process' },
  call: { stroke: '#06b6d4', icon: '#22d3ee', label: 'Call activity' }
};

const CurrentTokenPin: React.FC<{ x: number; y: number }> = ({ x, y }) => (
  <g transform={`translate(${x}, ${y})`}>
    <circle cx="0" cy="0" r="7" fill="#0f766e" stroke="#ffffff" strokeWidth="2" />
    <circle cx="0" cy="0" r="2.2" fill="#ffffff" />
    <path d="M -3 6 L 0 12 L 3 6 Z" fill="#0f766e" stroke="#ffffff" strokeWidth="1" />
  </g>
);

const labelForNode = (node: WorkflowNode): string => {
  if (node.name && node.name.trim().length > 0) return node.name;
  return node.id;
};

const normalizedType = (type: string): string => type.toLowerCase().replace(/[^a-z0-9]/g, '');
const isMessageStart = (type: string): boolean => normalizedType(type).includes('messagestart');
const isMessageCatch = (type: string): boolean => normalizedType(type).includes('messageintermediatecatch');
const isMessageThrow = (type: string): boolean => normalizedType(type).includes('messageintermediatethrow');
const isMessageEvent = (type: string): boolean => isMessageStart(type) || isMessageCatch(type) || isMessageThrow(type);
const isTimerEvent = (type: string): boolean => normalizedType(type).includes('timer') && !normalizedType(type).includes('boundary');
const isCircularEvent = (type: string): boolean =>
  normalizedType(type).includes('start') ||
  normalizedType(type).includes('endevent') ||
  type === 'EndEvent' ||
  isTimerEvent(type) ||
  isMessageThrow(type);

const isGateway = (type: string): boolean => normalizedType(type).includes('gateway');
const isDocumentation = (node: WorkflowNode): boolean => normalizedType(node.type).includes('documentation');

const nodeSizeByType = (type: string): { width: number; height: number } => {
  const normalized = normalizedType(type);
  if (type === 'Participant' || type === 'Pool') return { width: 640, height: 260 };
  if (isCircularEvent(type)) return { width: 40, height: 40 };
  if (isGateway(type)) return { width: 40, height: 40 };
  if (normalized.includes('boundary')) return { width: 30, height: 30 };
  if (normalized.includes('documentation')) return { width: 180, height: 82 };
  return { width: 132, height: 62 };
};

const getNodeSize = (node: WorkflowNode): { width: number; height: number } => {
  const fixedSize = nodeSizeByType(node.type);
  const normalized = normalizedType(node.type);
  if (isCircularEvent(node.type) || isGateway(node.type) || normalized.includes('boundary')) return fixedSize;
  if (isParticipant(node) || isDocumentation(node)) {
    if (node.width && node.height) return { width: node.width, height: node.height };
  }
  return fixedSize;
};

const getCenter = (node: WorkflowNode, size: { width: number; height: number }) => ({
  x: (node.position?.x ?? 0) + size.width / 2,
  y: (node.position?.y ?? 0) + size.height / 2
});

const getDocumentationAnchor = (node: WorkflowNode) => {
  const size = getNodeSize(node);
  return {
    x: node.position?.x ?? 0,
    y: (node.position?.y ?? 0) + size.height / 2
  };
};

const getOrthogonalPath = (source: WorkflowNode, target: WorkflowNode): string => {
  if (isDocumentation(source) || isDocumentation(target)) {
    const start = isDocumentation(source) ? getDocumentationAnchor(source) : getCenter(source, getNodeSize(source));
    const end = isDocumentation(target) ? getDocumentationAnchor(target) : getCenter(target, getNodeSize(target));
    return `M ${start.x} ${start.y} L ${end.x} ${end.y}`;
  }

  const sourceSize = getNodeSize(source);
  const targetSize = getNodeSize(target);
  const start = getCenter(source, sourceSize);
  const end = getCenter(target, targetSize);

  const dx = end.x - start.x;
  const dy = end.y - start.y;

  if (Math.abs(dx) > Math.abs(dy)) {
    const fromX = dx >= 0 ? (source.position?.x ?? 0) + sourceSize.width : (source.position?.x ?? 0);
    const toX = dx >= 0 ? (target.position?.x ?? 0) : (target.position?.x ?? 0) + targetSize.width;
    const midX = (fromX + toX) / 2;
    return `M ${fromX} ${start.y} L ${midX} ${start.y} L ${midX} ${end.y} L ${toX} ${end.y}`;
  }

  const fromY = dy >= 0 ? (source.position?.y ?? 0) + sourceSize.height : (source.position?.y ?? 0);
  const toY = dy >= 0 ? (target.position?.y ?? 0) : (target.position?.y ?? 0) + targetSize.height;
  const midY = (fromY + toY) / 2;
  return `M ${start.x} ${fromY} L ${start.x} ${midY} L ${end.x} ${midY} L ${end.x} ${toY}`;
};

const collectEdges = (definition: WorkflowDefinition): Edge[] => {
  if (definition.flows && definition.flows.length > 0) {
    return definition.flows
      .map((flow) => ({ from: flow.from || flow.source || '', to: flow.to || flow.target || '' }))
      .filter((flow) => flow.from.length > 0 && flow.to.length > 0);
  }

  const edges: Edge[] = [];
  for (const node of definition.nodes) {
    for (const nextNode of node.next ?? []) {
      edges.push({ from: node.id, to: nextNode });
    }
  }
  return edges;
};

const isBoundaryEvent = (node: WorkflowNode): boolean => {
  return normalizedType(node.type).includes('boundary') || !!node.attachedTo;
};

const isParticipant = (node: WorkflowNode): boolean => {
  return node.type === 'Participant' || node.type === 'Pool';
};

const hasDegenerateLayout = (nodes: WorkflowNode[]): boolean => {
  const drawableNodes = nodes.filter((node) => !isParticipant(node) && !isDocumentation(node));
  if (drawableNodes.length <= 1) return false;

  const positionKeys = new Set(drawableNodes.map((node) => `${Math.round(node.position?.x ?? 0)}:${Math.round(node.position?.y ?? 0)}`));
  if (positionKeys.size <= Math.max(1, Math.floor(drawableNodes.length / 2))) return true;

  const xs = drawableNodes.map((node) => node.position?.x ?? 0);
  const ys = drawableNodes.map((node) => node.position?.y ?? 0);
  const spreadX = Math.max(...xs) - Math.min(...xs);
  const spreadY = Math.max(...ys) - Math.min(...ys);
  return spreadX < 120 && spreadY < 90;
};

const autoLayoutNodes = (nodes: WorkflowNode[], edges: Edge[]): WorkflowNode[] => {
  if (!hasDegenerateLayout(nodes)) return nodes;

  const incoming = new Map<string, number>();
  const outgoing = new Map<string, string[]>();
  nodes.forEach((node) => {
    incoming.set(node.id, 0);
    outgoing.set(node.id, []);
  });
  edges.forEach((edge) => {
    if (!incoming.has(edge.to) || !outgoing.has(edge.from)) return;
    incoming.set(edge.to, (incoming.get(edge.to) ?? 0) + 1);
    outgoing.set(edge.from, [...(outgoing.get(edge.from) ?? []), edge.to]);
  });

  const levels = new Map<string, number>();
  const queue = nodes
    .filter((node) => !isParticipant(node) && !isDocumentation(node) && (incoming.get(node.id) ?? 0) === 0)
    .map((node) => node.id);

  if (queue.length === 0 && nodes[0]) queue.push(nodes[0].id);
  queue.forEach((id) => levels.set(id, 0));

  while (queue.length > 0) {
    const id = queue.shift()!;
    const nextLevel = (levels.get(id) ?? 0) + 1;
    for (const targetId of outgoing.get(id) ?? []) {
      if ((levels.get(targetId) ?? -1) >= nextLevel) continue;
      levels.set(targetId, nextLevel);
      queue.push(targetId);
    }
  }

  nodes.forEach((node) => {
    if (!levels.has(node.id) && !isParticipant(node) && !isDocumentation(node)) {
      levels.set(node.id, levels.size);
    }
  });

  const rowsByLevel = new Map<number, WorkflowNode[]>();
  nodes.filter((node) => !isParticipant(node) && !isBoundaryEvent(node) && !isDocumentation(node)).forEach((node) => {
    const level = levels.get(node.id) ?? 0;
    rowsByLevel.set(level, [...(rowsByLevel.get(level) ?? []), node]);
  });

  const positions = new Map<string, { x: number; y: number }>();
  Array.from(rowsByLevel.entries()).forEach(([level, levelNodes]) => {
    const totalHeight = (levelNodes.length - 1) * 120;
    levelNodes.forEach((node, row) => {
      positions.set(node.id, {
        x: 100 + level * 220,
        y: 120 + row * 120 - totalHeight / 2
      });
    });
  });

  return nodes.map((node) => {
    if (isParticipant(node) || isDocumentation(node)) return node;
    if (isBoundaryEvent(node) && node.attachedTo) {
      const parentPosition = positions.get(node.attachedTo);
      if (parentPosition) {
        const parentSize = getNodeSize(nodes.find((candidate) => candidate.id === node.attachedTo) ?? node);
        return { ...node, position: { x: parentPosition.x + parentSize.width - 10, y: parentPosition.y + parentSize.height - 8 } };
      }
    }
    return { ...node, position: positions.get(node.id) ?? node.position ?? { x: 100, y: 120 } };
  });
};

const taskToneForType = (type: string): NodeTone => {
  const normalized = normalizedType(type);
  if (normalized.includes('human') || normalized.includes('user')) return TASK_TONES.human;
  if (normalized.includes('api')) return TASK_TONES.api;
  if (normalized.includes('code')) return TASK_TONES.code;
  if (normalized.includes('aitask') || normalized === 'ai') return TASK_TONES.ai;
  if (normalized.includes('agent')) return TASK_TONES.agent;
  if (normalized.includes('callactivity')) return TASK_TONES.call;
  if (normalized.includes('service')) return TASK_TONES.service;
  return DEFAULT_NODE_TONE;
};

const taskIconForType = (type: string, color: string) => {
  const normalized = normalizedType(type);
  const className = 'h-4 w-4 pointer-events-none';
  if (normalized.includes('human') || normalized.includes('user')) return <User className={className} color={color} />;
  if (normalized.includes('api')) return <Settings className={className} color={color} />;
  if (normalized.includes('code')) return <Code className={className} color={color} />;
  if (normalized.includes('aitask') || normalized === 'ai') return <Brain className={className} color={color} />;
  if (normalized.includes('agent')) return <Bot className={className} color={color} />;
  if (normalized.includes('callactivity')) return <Layers className={className} color={color} />;
  return <Zap className={className} color={color} />;
};

const renderNodeText = (
  node: WorkflowNode,
  options: { x: number; y: number; width: number; height: number; task?: boolean; documentation?: boolean }
) => (
  <foreignObject x={options.x} y={options.y} width={options.width} height={options.height} style={{ pointerEvents: 'none' }}>
    <div
      className={`workflow-node-copy ${options.task ? 'workflow-node-copy-task' : ''} ${options.documentation ? 'workflow-node-copy-documentation' : ''}`}
      title={`${labelForNode(node)} (${node.id})`}
    >
      <div className="workflow-node-label">{labelForNode(node)}</div>
      {!options.documentation && node.name && node.name.trim().length > 0 && (
        <div className="workflow-node-id">{node.id}</div>
      )}
      {options.documentation && (
        <div className="workflow-node-id">{String(node.config?.description ?? node.id ?? '')}</div>
      )}
    </div>
  </foreignObject>
);

export const WorkflowCanvas: React.FC<Props> = ({ definition, nodeHistory, currentNodes, expanded = false }) => {
  const [zoom, setZoom] = useState(1);
  const rawNodes = definition.nodes ?? [];
  if (rawNodes.length === 0) {
    return <p className="text-sm text-slate-500">No workflow nodes available for this definition.</p>;
  }

  const edges = collectEdges(definition);
  const nodes = autoLayoutNodes(rawNodes, edges);
  const visitedSet = new Set(nodeHistory);
  const currentSet = new Set(currentNodes);
  const visitedEdges = new Set<string>();

  for (let i = 0; i < nodeHistory.length - 1; i += 1) {
    visitedEdges.add(`${nodeHistory[i]}::${nodeHistory[i + 1]}`);
  }

  const nodeById = new Map(nodes.map((n) => [n.id, n]));
  const poolNodes = nodes.filter(isParticipant);
  const regularNodes = nodes.filter((node) => !isBoundaryEvent(node) && !isParticipant(node) && !isDocumentation(node));
  const boundaryNodes = nodes.filter((node) => isBoundaryEvent(node) && !isDocumentation(node));
  const documentationNodes = nodes.filter(isDocumentation);

  const bounds = nodes.reduce(
    (acc, node) => {
      const size = getNodeSize(node);
      const x = node.position?.x ?? 0;
      const y = node.position?.y ?? 0;
      return {
        minX: Math.min(acc.minX, x),
        minY: Math.min(acc.minY, y),
        maxX: Math.max(acc.maxX, x + size.width),
        maxY: Math.max(acc.maxY, y + size.height)
      };
    },
    { minX: Number.POSITIVE_INFINITY, minY: Number.POSITIVE_INFINITY, maxX: 0, maxY: 0 }
  );

  const padding = expanded ? 120 : 90;
  const contentWidth = bounds.maxX - bounds.minX;
  const contentHeight = bounds.maxY - bounds.minY;
  const width = Math.max(expanded ? 1180 : 740, contentWidth + padding * 2);
  const height = Math.max(expanded ? 620 : 360, contentHeight + padding * 2);
  const offsetX = padding + Math.max(0, (width - contentWidth - padding * 2) / 2) - bounds.minX;
  const offsetY = padding + Math.max(0, (height - contentHeight - padding * 2) / 2) - bounds.minY;
  const clampZoom = (value: number) => Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, Number(value.toFixed(2))));
  const zoomPercent = Math.round(zoom * 100);

  return (
    <div className={`workflow-canvas relative w-full overflow-hidden rounded-xl border shadow-sm ${expanded ? 'workflow-canvas-expanded' : ''}`}>
      <div className="workflow-canvas-glow pointer-events-none absolute inset-0" />
      <div className="workflow-canvas-controls absolute right-3 top-3 z-10 flex items-center gap-1 rounded-md border p-1 shadow-sm backdrop-blur-sm">
        <button
          type="button"
          onClick={() => setZoom((current) => clampZoom(current - ZOOM_STEP))}
          className="flex h-8 w-8 items-center justify-center rounded disabled:opacity-40"
          disabled={zoom <= MIN_ZOOM}
          title="Zoom out"
          aria-label="Zoom out"
        >
          <ZoomOut className="h-4 w-4" />
        </button>
        <span className="w-12 text-center text-xs font-semibold tabular-nums">{zoomPercent}%</span>
        <button
          type="button"
          onClick={() => setZoom((current) => clampZoom(current + ZOOM_STEP))}
          className="flex h-8 w-8 items-center justify-center rounded disabled:opacity-40"
          disabled={zoom >= MAX_ZOOM}
          title="Zoom in"
          aria-label="Zoom in"
        >
          <ZoomIn className="h-4 w-4" />
        </button>
        <button
          type="button"
          onClick={() => setZoom(1)}
          className="flex h-8 w-8 items-center justify-center rounded"
          title="Reset zoom"
          aria-label="Reset zoom"
        >
          <RotateCcw className="h-4 w-4" />
        </button>
      </div>
      <div className={expanded ? 'max-h-[calc(100vh-280px)] min-h-[620px] overflow-auto' : 'max-h-[620px] overflow-auto'}>
        <svg
          width={width * zoom}
          height={height * zoom}
          className="block min-w-full"
          style={{
            backgroundImage: 'radial-gradient(circle, var(--workflow-canvas-grid) 1px, transparent 1.2px)',
            backgroundSize: `${24 * zoom}px ${24 * zoom}px`
          }}
        >
          <defs>
            <marker id="wf-arrow" markerWidth="12" markerHeight="12" refX="11" refY="6" orient="auto" markerUnits="strokeWidth">
              <path d="M 0 0 L 12 6 L 0 12 Z" fill="#64748b" stroke="none" />
            </marker>
            <marker id="wf-arrow-active" markerWidth="12" markerHeight="12" refX="11" refY="6" orient="auto" markerUnits="strokeWidth">
              <path d="M 0 0 L 12 6 L 0 12 Z" fill="#2563eb" stroke="none" />
            </marker>
            <marker id="wf-arrow-boundary" markerWidth="12" markerHeight="12" refX="11" refY="6" orient="auto" markerUnits="strokeWidth">
              <path d="M 0 0 L 12 6 L 0 12 Z" fill="#dc2626" stroke="none" />
            </marker>
            <filter id="wf-shadow" x="-50%" y="-50%" width="200%" height="200%">
              <feDropShadow dx="0" dy="4" stdDeviation="6" floodColor="#020617" floodOpacity="0.28" />
            </filter>
          </defs>

          <g transform={`scale(${zoom})`}>
            {poolNodes.map((node) => {
              const size = getNodeSize(node);
              const x = (node.position?.x ?? 0) + offsetX;
              const y = (node.position?.y ?? 0) + offsetY;

              return (
                <g key={node.id}>
                  <rect x={x} y={y} width={size.width} height={size.height} rx={8} className="workflow-pool-fill" />
                  <rect x={x} y={y} width={44} height={size.height} rx={8} className="workflow-pool-lane" />
                  <line x1={x + 44} y1={y} x2={x + 44} y2={y + size.height} className="workflow-pool-line" />
                  <line x1={x + 44} y1={y + size.height / 2} x2={x + size.width} y2={y + size.height / 2} className="workflow-pool-divider" strokeDasharray="6 4" />
                  <text transform={`translate(${x + 22}, ${y + size.height / 2}) rotate(-90)`} textAnchor="middle" className="workflow-pool-label">
                    {labelForNode(node)}
                  </text>
                </g>
              );
            })}

            {edges.map((edge) => {
              const source = nodeById.get(edge.from);
              const target = nodeById.get(edge.to);
              if (!source || !target) return null;
              if (isParticipant(source) || isParticipant(target)) return null;
              if (isBoundaryEvent(target)) return null;

              const edgeKey = `${edge.from}::${edge.to}`;
              const isVisitedEdge = visitedEdges.has(edgeKey);
              const isBoundaryEdge = isBoundaryEvent(source);
              const isDocumentationEdge = isDocumentation(source) || isDocumentation(target);
              const path = getOrthogonalPath(
                { ...source, position: { x: (source.position?.x ?? 0) + offsetX, y: (source.position?.y ?? 0) + offsetY } },
                { ...target, position: { x: (target.position?.x ?? 0) + offsetX, y: (target.position?.y ?? 0) + offsetY } }
              );

              return (
                <path
                  key={edgeKey}
                  d={path}
                  fill="none"
                  stroke={isDocumentationEdge ? 'var(--workflow-doc-line)' : isVisitedEdge ? '#2563eb' : isBoundaryEdge ? '#dc2626' : 'var(--workflow-edge)'}
                  strokeWidth={isDocumentationEdge ? '1.5' : isVisitedEdge ? '3' : '2'}
                  markerEnd={isDocumentationEdge ? undefined : isVisitedEdge ? 'url(#wf-arrow-active)' : isBoundaryEdge ? 'url(#wf-arrow-boundary)' : 'url(#wf-arrow)'}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeDasharray={isDocumentationEdge ? '2 5' : isBoundaryEdge ? '4,3' : undefined}
                  opacity={isDocumentationEdge ? '0.72' : '1'}
                  vectorEffect="non-scaling-stroke"
                />
              );
            })}

            {boundaryNodes.map((boundaryNode) => {
              if (!boundaryNode.attachedTo) return null;
              const parentNode = nodeById.get(boundaryNode.attachedTo);
              if (!parentNode) return null;

              const parentSize = getNodeSize(parentNode);
              const boundarySize = getNodeSize(boundaryNode);
              const parentOffsetted = {
                ...parentNode,
                position: {
                  x: (parentNode.position?.x ?? 0) + offsetX,
                  y: (parentNode.position?.y ?? 0) + offsetY
                }
              };
              const boundaryOffsetted = {
                ...boundaryNode,
                position: {
                  x: (boundaryNode.position?.x ?? 0) + offsetX,
                  y: (boundaryNode.position?.y ?? 0) + offsetY
                }
              };

              const parentCenter = getCenter(parentOffsetted, parentSize);
              const boundaryCenter = getCenter(boundaryOffsetted, boundarySize);

              return (
                <path
                  key={`boundary-${boundaryNode.id}`}
                  d={`M ${parentCenter.x} ${parentCenter.y} L ${boundaryCenter.x} ${boundaryCenter.y}`}
                  fill="none"
                  stroke="#dc2626"
                  strokeWidth="1.5"
                  strokeDasharray="4,3"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  markerEnd="url(#wf-arrow-boundary)"
                  vectorEffect="non-scaling-stroke"
                />
              );
            })}

            {documentationNodes.map((node) => {
              const size = getNodeSize(node);
              const x = (node.position?.x ?? 0) + offsetX;
              const y = (node.position?.y ?? 0) + offsetY;

              return (
                <g key={node.id}>
                  <rect x={x} y={y} width={size.width} height={size.height} fill="transparent" stroke="none" />
                  <line x1={x} y1={y} x2={x} y2={y + size.height} className="workflow-doc-bracket" />
                  <line x1={x} y1={y} x2={x + 14} y2={y} className="workflow-doc-bracket" />
                  <line x1={x} y1={y + size.height} x2={x + 14} y2={y + size.height} className="workflow-doc-bracket" />
                  {renderNodeText(node, { x: x + 8, y: y + 5, width: Math.max(40, size.width - 16), height: Math.max(36, size.height - 10), documentation: true })}
                </g>
              );
            })}

            {regularNodes.map((node) => {
              const size = getNodeSize(node);
              const x = (node.position?.x ?? 0) + offsetX;
              const y = (node.position?.y ?? 0) + offsetY;
              const visited = visitedSet.has(node.id);
              const current = currentSet.has(node.id);
              const typeKey = normalizedType(node.type);

              if (isCircularEvent(node.type)) {
                const centerX = x + size.width / 2;
                const centerY = y + size.height / 2;
                const isEnd = typeKey.includes('end');
                const isTimer = isTimerEvent(node.type);
                const isMessage = isMessageEvent(node.type);
                const stroke = isEnd ? '#ef4444' : isTimer ? '#f59e0b' : isMessage ? '#3b82f6' : '#22c55e';

                return (
                  <g key={node.id}>
                    <circle cx={centerX} cy={centerY} r={size.width / 2} fill="var(--workflow-node-fill)" stroke={current ? '#10b981' : stroke} strokeWidth={isEnd ? 4 : 2.5} filter="url(#wf-shadow)" />
                    {visited && !current && <circle cx={centerX} cy={centerY} r={size.width / 2 + 5} className="workflow-visited-ring" />}
                    {isTimer && <Clock3 x={centerX - 8} y={centerY - 8} className="h-4 w-4 pointer-events-none" color="#f59e0b" />}
                    {isMessage && <Mail x={centerX - 8} y={centerY - 8} className="h-4 w-4 pointer-events-none" color={isMessageStart(node.type) ? '#22c55e' : '#60a5fa'} />}
                    <text x={centerX} y={y + size.height + 22} textAnchor="middle" className="workflow-floating-label">
                      {labelForNode(node)}
                    </text>
                    {current && <CurrentTokenPin x={x + size.width + 8} y={y - 4} />}
                  </g>
                );
              }

              if (isGateway(node.type)) {
                return (
                  <g key={node.id}>
                    <rect x={x + 8} y={y + 8} width={size.width - 16} height={size.height - 16} transform={`rotate(45 ${x + size.width / 2} ${y + size.height / 2})`} fill="var(--workflow-node-fill)" stroke={current ? '#10b981' : '#f97316'} strokeWidth="2.5" filter="url(#wf-shadow)" />
                    {typeKey.includes('parallel') ? (
                      <Plus x={x + size.width / 2 - 8} y={y + size.height / 2 - 8} className="h-4 w-4 pointer-events-none" color="#fb923c" />
                    ) : (
                      <GitFork x={x + size.width / 2 - 8} y={y + size.height / 2 - 8} className="h-4 w-4 pointer-events-none" color="#fb923c" />
                    )}
                    {visited && !current && <rect x={x + 3} y={y + 3} width={size.width - 6} height={size.height - 6} rx="4" className="workflow-visited-ring" />}
                    <text x={x + size.width / 2} y={y + size.height + 22} textAnchor="middle" className="workflow-floating-label">
                      {labelForNode(node)}
                    </text>
                    {current && <CurrentTokenPin x={x + size.width + 8} y={y - 4} />}
                  </g>
                );
              }

              const tone = taskToneForType(node.type);
              const isMessageBox = isMessageCatch(node.type);

              return (
                <g key={node.id}>
                  <rect
                    x={x}
                    y={y}
                    width={size.width}
                    height={size.height}
                    rx={6}
                    fill="var(--workflow-node-fill)"
                    stroke={current ? '#10b981' : tone.stroke}
                    strokeWidth="2.5"
                    filter="url(#wf-shadow)"
                  />
                  {visited && !current && <rect x={x - 5} y={y - 5} width={size.width + 10} height={size.height + 10} rx={10} className="workflow-visited-ring" />}
                  <rect x={x + 8} y={y + (size.height - 24) / 2} width="24" height="24" rx="5" className="workflow-icon-chip" />
                  <g transform={`translate(${x + 12}, ${y + size.height / 2 - 8})`}>
                    {isMessageBox ? <Mail className="h-4 w-4 pointer-events-none" color="#60a5fa" /> : taskIconForType(node.type, tone.icon)}
                  </g>
                  {renderNodeText(node, { x: x + 38, y, width: Math.max(36, size.width - 46), height: size.height, task: true })}
                  {current && <CurrentTokenPin x={x + size.width + 8} y={y - 4} />}
                </g>
              );
            })}

            {boundaryNodes.map((node) => {
              const size = getNodeSize(node);
              const x = (node.position?.x ?? 0) + offsetX;
              const y = (node.position?.y ?? 0) + offsetY;
              const current = currentSet.has(node.id);
              const cx = x + size.width / 2;
              const cy = y + size.height / 2;
              const radius = size.width / 2;
              const normalized = normalizedType(node.type);
              const stroke = normalized.includes('message') ? '#3b82f6' : normalized.includes('timer') ? '#f59e0b' : '#ef4444';

              return (
                <g key={node.id}>
                  <circle cx={cx} cy={cy} r={radius} fill="var(--workflow-node-fill)" stroke={current ? '#10b981' : stroke} strokeWidth="2" strokeDasharray="3,2" filter="url(#wf-shadow)" />
                  {normalized.includes('message') && <Mail x={cx - 7} y={cy - 7} className="h-3.5 w-3.5 pointer-events-none" color="#60a5fa" />}
                  {normalized.includes('timer') && <Clock3 x={cx - 7} y={cy - 7} className="h-3.5 w-3.5 pointer-events-none" color="#f59e0b" />}
                  {!normalized.includes('message') && !normalized.includes('timer') && <Zap x={cx - 7} y={cy - 7} className="h-3.5 w-3.5 pointer-events-none" color="#ef4444" fill="#ef4444" />}
                  <text x={cx} y={y + size.height + 18} textAnchor="middle" className="workflow-floating-label workflow-floating-label-small">
                    {labelForNode(node)}
                  </text>
                  {current && <CurrentTokenPin x={cx + radius + 4} y={cy - 4} />}
                </g>
              );
            })}
          </g>
        </svg>
      </div>
    </div>
  );
};
