import React, { useState } from 'react';
import { Zap, Mail, Clock3, ZoomIn, ZoomOut, RotateCcw } from 'lucide-react';
import { WorkflowDefinition, WorkflowNode } from '../types';

type Props = {
  definition: WorkflowDefinition;
  nodeHistory: string[];
  currentNodes: string[];
};

type Edge = {
  from: string;
  to: string;
};

const MIN_ZOOM = 0.5;
const MAX_ZOOM = 1.5;
const ZOOM_STEP = 0.1;

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

const nodeSizeByType = (type: string): { width: number; height: number } => {
  const normalized = type.toLowerCase();
  if (type === 'Participant' || type === 'Pool') return { width: 640, height: 260 };
  if (normalized.includes('start') || normalized.includes('endevent') || type === 'EndEvent') return { width: 40, height: 40 };
  if (normalized.includes('gateway')) return { width: 40, height: 40 };
  if (normalized.includes('boundary')) return { width: 30, height: 30 };
  return { width: 120, height: 60 };
};

const getNodeSize = (node: WorkflowNode): { width: number; height: number } => {
  const fixedSize = nodeSizeByType(node.type);
  const normalized = node.type.toLowerCase();
  if (
    normalized.includes('start') ||
    normalized.includes('end') ||
    normalized.includes('gateway') ||
    normalized.includes('boundary')
  ) {
    return fixedSize;
  }
  if (node.width && node.height) return { width: node.width, height: node.height };
  return fixedSize;
};

const getNodeStyle = (type: string, visited: boolean, current: boolean): string => {
  const normalized = type.toLowerCase();
  if (current) return 'fill-emerald-100 stroke-emerald-600';
  if (visited) return 'fill-blue-50 stroke-blue-500';

  if (normalized.includes('start')) return 'fill-white stroke-green-600';
  if (normalized.includes('end')) return 'fill-white stroke-red-600';
  if (normalized.includes('gateway')) return 'fill-white stroke-orange-600';
  if (type === 'ErrorBoundaryEvent') return 'fill-white stroke-red-600';
  if (type === 'MessageBoundaryEvent') return 'fill-white stroke-blue-600';
  if (type === 'TimerEvent' && type.toLowerCase().includes('boundary')) return 'fill-white stroke-amber-600';
  if (type === 'HumanTask' || type === 'UserTask' || type === 'humanTask' || type === 'userTask') return 'fill-white stroke-blue-700';
  if (type === 'ServiceTask') return 'fill-white stroke-amber-600';
  if (type === 'APITask') return 'fill-white stroke-purple-600';
  if (type === 'Participant' || type === 'Pool') return 'fill-white stroke-sky-500';
  return 'fill-white stroke-slate-400';
};

const getCenter = (node: WorkflowNode, size: { width: number; height: number }) => ({
  x: (node.position?.x ?? 0) + size.width / 2,
  y: (node.position?.y ?? 0) + size.height / 2
});

const getOrthogonalPath = (source: WorkflowNode, target: WorkflowNode): string => {
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
  return node.type.toLowerCase().includes('boundary') || !!node.attachedTo;
};

const isParticipant = (node: WorkflowNode): boolean => {
  return node.type === 'Participant' || node.type === 'Pool';
};

const hasDegenerateLayout = (nodes: WorkflowNode[]): boolean => {
  const drawableNodes = nodes.filter((node) => !isParticipant(node));
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
    .filter((node) => !isParticipant(node) && (incoming.get(node.id) ?? 0) === 0)
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
    if (!levels.has(node.id) && !isParticipant(node)) {
      levels.set(node.id, levels.size);
    }
  });

  const rowsByLevel = new Map<number, WorkflowNode[]>();
  nodes.filter((node) => !isParticipant(node) && !isBoundaryEvent(node)).forEach((node) => {
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
    if (isParticipant(node)) return node;
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

export const WorkflowCanvas: React.FC<Props> = ({ definition, nodeHistory, currentNodes }) => {
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
  
  // Separate regular nodes and boundary events
  const poolNodes = nodes.filter(isParticipant);
  const regularNodes = nodes.filter(n => !isBoundaryEvent(n) && !isParticipant(n));
  const boundaryNodes = nodes.filter(n => isBoundaryEvent(n));

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

  const padding = 80;
  const width = Math.max(700, bounds.maxX - bounds.minX + padding * 2);
  const height = Math.max(320, bounds.maxY - bounds.minY + padding * 2);
  const offsetX = padding - bounds.minX;
  const offsetY = padding - bounds.minY;
  const clampZoom = (value: number) => Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, Number(value.toFixed(2))));
  const zoomPercent = Math.round(zoom * 100);

  return (
    <div className="relative w-full rounded-xl border border-slate-200 bg-slate-50">
      <div className="absolute right-3 top-3 z-10 flex items-center gap-1 rounded-md border border-slate-200 bg-white/95 p-1 shadow-sm backdrop-blur-sm">
        <button
          type="button"
          onClick={() => setZoom((current) => clampZoom(current - ZOOM_STEP))}
          className="flex h-8 w-8 items-center justify-center rounded text-slate-600 hover:bg-slate-100 hover:text-slate-900 disabled:opacity-40"
          disabled={zoom <= MIN_ZOOM}
          title="Zoom out"
          aria-label="Zoom out"
        >
          <ZoomOut className="h-4 w-4" />
        </button>
        <span className="w-12 text-center text-xs font-semibold tabular-nums text-slate-600">{zoomPercent}%</span>
        <button
          type="button"
          onClick={() => setZoom((current) => clampZoom(current + ZOOM_STEP))}
          className="flex h-8 w-8 items-center justify-center rounded text-slate-600 hover:bg-slate-100 hover:text-slate-900 disabled:opacity-40"
          disabled={zoom >= MAX_ZOOM}
          title="Zoom in"
          aria-label="Zoom in"
        >
          <ZoomIn className="h-4 w-4" />
        </button>
        <button
          type="button"
          onClick={() => setZoom(1)}
          className="flex h-8 w-8 items-center justify-center rounded text-slate-600 hover:bg-slate-100 hover:text-slate-900"
          title="Reset zoom"
          aria-label="Reset zoom"
        >
          <RotateCcw className="h-4 w-4" />
        </button>
      </div>
      <div className="max-h-[620px] overflow-auto">
      <svg width={width * zoom} height={height * zoom} className="block min-w-full">
        <defs>
          {/* Standard flow arrow (inactive) */}
          <marker id="wf-arrow" markerWidth="12" markerHeight="12" refX="11" refY="6" orient="auto" markerUnits="strokeWidth">
            <path d="M 0 0 L 12 6 L 0 12 Z" fill="#64748b" stroke="none" />
          </marker>
          {/* Active flow arrow (visited) */}
          <marker id="wf-arrow-active" markerWidth="12" markerHeight="12" refX="11" refY="6" orient="auto" markerUnits="strokeWidth">
            <path d="M 0 0 L 12 6 L 0 12 Z" fill="#2563eb" stroke="none" />
          </marker>
          {/* Boundary event exception arrow */}
          <marker id="wf-arrow-boundary" markerWidth="12" markerHeight="12" refX="11" refY="6" orient="auto" markerUnits="strokeWidth">
            <path d="M 0 0 L 12 6 L 0 12 Z" fill="#dc2626" stroke="none" />
          </marker>
        </defs>

        <g transform={`scale(${zoom})`}>
        {/* Draw BPMN participants/pools behind the executable process path */}
        {poolNodes.map((node) => {
          const size = getNodeSize(node);
          const x = (node.position?.x ?? 0) + offsetX;
          const y = (node.position?.y ?? 0) + offsetY;

          return (
            <g key={node.id}>
              <rect x={x} y={y} width={size.width} height={size.height} rx={8} className="fill-white stroke-sky-500 stroke-[2] opacity-80" />
              <rect x={x} y={y} width={44} height={size.height} rx={8} className="fill-blue-50 stroke-sky-500 stroke-[1.5]" />
              <line x1={x + 44} y1={y} x2={x + 44} y2={y + size.height} className="stroke-sky-500 stroke-[1.5]" />
              <line x1={x + 44} y1={y + size.height / 2} x2={x + size.width} y2={y + size.height / 2} className="stroke-sky-500/40 stroke-[1]" strokeDasharray="6 4" />
              <text
                transform={`translate(${x + 22}, ${y + size.height / 2}) rotate(-90)`}
                textAnchor="middle"
                className="fill-sky-700 text-[12px] font-semibold"
              >
                {labelForNode(node)}
              </text>
              <text x={x + 56} y={y + 22} className="fill-slate-500 text-[10px] font-mono">
                {node.id}
              </text>
            </g>
          );
        })}

        {/* Draw regular edges and boundary event exception arrows */}
        {edges.map((edge) => {
          const source = nodeById.get(edge.from);
          const target = nodeById.get(edge.to);
          if (!source || !target) return null;
          if (isParticipant(source) || isParticipant(target)) return null;

          // Skip edges TO boundary events (they're drawn separately in the boundary connection section)
          if (isBoundaryEvent(target)) return null;

          const edgeKey = `${edge.from}::${edge.to}`;
          const isVisitedEdge = visitedEdges.has(edgeKey);
          const isBoundaryEdge = isBoundaryEvent(source);
          const path = getOrthogonalPath(
            { ...source, position: { x: (source.position?.x ?? 0) + offsetX, y: (source.position?.y ?? 0) + offsetY } },
            { ...target, position: { x: (target.position?.x ?? 0) + offsetX, y: (target.position?.y ?? 0) + offsetY } }
          );

          return (
            <path
              key={edgeKey}
              d={path}
              fill="none"
              stroke={isVisitedEdge ? '#2563eb' : isBoundaryEdge ? '#dc2626' : '#94a3b8'}
              strokeWidth={isVisitedEdge ? '3' : '2'}
              markerEnd={isVisitedEdge ? 'url(#wf-arrow-active)' : isBoundaryEdge ? 'url(#wf-arrow-boundary)' : 'url(#wf-arrow)'}
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeDasharray={isBoundaryEdge ? '4,3' : undefined}
              vectorEffect="non-scaling-stroke"
            />
          );
        })}

        {/* Draw boundary event connections */}
        {boundaryNodes.map((boundaryNode) => {
          if (!boundaryNode.attachedTo) return null;
          
          const parentNode = nodeById.get(boundaryNode.attachedTo);
          if (!parentNode) return null;

          const parentSize = getNodeSize(parentNode);
          const boundarySize = getNodeSize(boundaryNode);
          
          // Create offsetted node copies for path calculation
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

        {/* Draw regular nodes */}
        {regularNodes.map((node) => {
          const size = getNodeSize(node);
          const x = (node.position?.x ?? 0) + offsetX;
          const y = (node.position?.y ?? 0) + offsetY;
          const visited = visitedSet.has(node.id);
          const current = currentSet.has(node.id);
          const className = getNodeStyle(node.type, visited, current);

          const normalizedType = node.type.toLowerCase();
          if (normalizedType.includes('start') || normalizedType.includes('end')) {
            return (
              <g key={node.id}>
                <circle
                  cx={x + size.width / 2}
                  cy={y + size.height / 2}
                  r={size.width / 2}
                  className={`${className} stroke-[3]`}
                />
                <text
                  x={x + size.width / 2}
                  y={y + size.height + 22}
                  textAnchor="middle"
                  className="fill-slate-700 text-xs font-medium"
                >
                  {labelForNode(node)}
                </text>
                {node.name && node.name.trim().length > 0 && (
                  <text
                    x={x + size.width / 2}
                    y={y + size.height + 36}
                    textAnchor="middle"
                    className="fill-slate-500 text-[10px] font-mono"
                  >
                    {node.id}
                  </text>
                )}
                {current && <CurrentTokenPin x={x + size.width + 8} y={y - 4} />}
              </g>
            );
          }

          if (node.type.toLowerCase().includes('gateway')) {
            return (
              <g key={node.id}>
                <rect
                  x={x + 8}
                  y={y + 8}
                  width={size.width - 16}
                  height={size.height - 16}
                  transform={`rotate(45 ${x + size.width / 2} ${y + size.height / 2})`}
                  className={`${className} stroke-[3]`}
                />
                <text
                  x={x + size.width / 2}
                  y={y + size.height + 22}
                  textAnchor="middle"
                  className="fill-slate-700 text-xs font-medium"
                >
                  {labelForNode(node)}
                </text>
                {node.name && node.name.trim().length > 0 && (
                  <text
                    x={x + size.width / 2}
                    y={y + size.height + 36}
                    textAnchor="middle"
                    className="fill-slate-500 text-[10px] font-mono"
                  >
                    {node.id}
                  </text>
                )}
                {current && <CurrentTokenPin x={x + size.width + 8} y={y - 4} />}
              </g>
            );
          }

          return (
            <g key={node.id}>
              <rect x={x} y={y} width={size.width} height={size.height} rx={8} className={`${className} stroke-[3]`} />
              <text
                x={x + size.width / 2}
                y={y + size.height / 2 - 12}
                textAnchor="middle"
                className="fill-slate-800 text-xs font-semibold"
              >
                {labelForNode(node)}
              </text>
              <text
                x={x + size.width / 2}
                y={y + size.height / 2 + 2}
                textAnchor="middle"
                className="fill-slate-500 text-[10px] font-mono"
              >
                {node.id}
              </text>
              <text
                x={x + size.width / 2}
                y={y + size.height / 2 + 16}
                textAnchor="middle"
                className="fill-slate-500 text-[10px] font-mono"
              >
                {node.type}
              </text>
              {current && <CurrentTokenPin x={x + size.width + 8} y={y - 4} />}
            </g>
          );
        })}

        {/* Draw boundary event nodes */}
        {boundaryNodes.map((node) => {
          const size = getNodeSize(node);
          const x = (node.position?.x ?? 0) + offsetX;
          const y = (node.position?.y ?? 0) + offsetY;
          const visited = visitedSet.has(node.id);
          const current = currentSet.has(node.id);
          const className = getNodeStyle(node.type, visited, current);
          const cx = x + size.width / 2;
          const cy = y + size.height / 2;
          const radius = size.width / 2;

          return (
            <g key={node.id}>
              <circle cx={cx} cy={cy} r={radius} className={`${className} stroke-[2.5]`} />
              
              {/* Error boundary event - lightning bolt */}
              {node.type === 'ErrorBoundaryEvent' && (
                <g transform={`translate(${cx - 7}, ${cy - 7})`}>
                  <Zap className="w-3.5 h-3.5 text-red-600 fill-current pointer-events-none" />
                </g>
              )}
              
              {/* Message boundary event - envelope */}
              {node.type === 'MessageBoundaryEvent' && (
                <g transform={`translate(${cx - 7}, ${cy - 7})`}>
                  <Mail className="w-3.5 h-3.5 text-blue-500 pointer-events-none" />
                </g>
              )}
              
              {/* Timer boundary event - clock */}
              {node.type.toLowerCase().includes('boundary') && node.type.toLowerCase().includes('timer') && (
                <g transform={`translate(${cx - 7}, ${cy - 7})`}>
                  <Clock3 className="w-3.5 h-3.5 text-amber-600 pointer-events-none" />
                </g>
              )}
              
              <text
                x={cx}
                y={y + size.height + 18}
                textAnchor="middle"
                className="fill-slate-700 text-[10px] font-medium"
              >
                {labelForNode(node)}
              </text>
              {node.name && node.name.trim().length > 0 && (
                <text
                  x={cx}
                  y={y + size.height + 28}
                  textAnchor="middle"
                  className="fill-slate-500 text-[9px] font-mono"
                >
                  {node.id}
                </text>
              )}
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
