import React from 'react';
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
  if (type === 'StartEvent' || type === 'EndEvent') return { width: 44, height: 44 };
  if (type.toLowerCase().includes('gateway')) return { width: 54, height: 54 };
  if (type.toLowerCase().includes('boundary')) return { width: 36, height: 36 };
  return { width: 150, height: 72 };
};

const getNodeStyle = (type: string, visited: boolean, current: boolean): string => {
  if (current) return 'fill-emerald-100 stroke-emerald-600';
  if (visited) return 'fill-blue-50 stroke-blue-500';

  if (type === 'StartEvent') return 'fill-white stroke-green-600';
  if (type === 'EndEvent') return 'fill-white stroke-red-600';
  if (type.toLowerCase().includes('gateway')) return 'fill-white stroke-orange-600';
  if (type === 'ErrorBoundaryEvent') return 'fill-white stroke-red-600';
  if (type === 'MessageBoundaryEvent') return 'fill-white stroke-blue-600';
  if (type === 'TimerEvent' && type.toLowerCase().includes('boundary')) return 'fill-white stroke-amber-600';
  if (type === 'HumanTask' || type === 'UserTask' || type === 'humanTask' || type === 'userTask') return 'fill-white stroke-blue-700';
  if (type === 'ServiceTask') return 'fill-white stroke-amber-600';
  if (type === 'APITask') return 'fill-white stroke-purple-600';
  return 'fill-white stroke-slate-400';
};

const getCenter = (node: WorkflowNode, size: { width: number; height: number }) => ({
  x: (node.position?.x ?? 0) + size.width / 2,
  y: (node.position?.y ?? 0) + size.height / 2
});

const getOrthogonalPath = (source: WorkflowNode, target: WorkflowNode): string => {
  const sourceSize = nodeSizeByType(source.type);
  const targetSize = nodeSizeByType(target.type);
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
    return definition.flows.map((flow) => ({ from: flow.from, to: flow.to }));
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

export const WorkflowCanvas: React.FC<Props> = ({ definition, nodeHistory, currentNodes }) => {
  const nodes = definition.nodes ?? [];
  if (nodes.length === 0) {
    return <p className="text-sm text-slate-500">No workflow nodes available for this definition.</p>;
  }

  const edges = collectEdges(definition);
  const visitedSet = new Set(nodeHistory);
  const currentSet = new Set(currentNodes);
  const visitedEdges = new Set<string>();

  for (let i = 0; i < nodeHistory.length - 1; i += 1) {
    visitedEdges.add(`${nodeHistory[i]}::${nodeHistory[i + 1]}`);
  }

  const nodeById = new Map(nodes.map((n) => [n.id, n]));
  
  // Separate regular nodes and boundary events
  const regularNodes = nodes.filter(n => !isBoundaryEvent(n));
  const boundaryNodes = nodes.filter(n => isBoundaryEvent(n));

  const bounds = regularNodes.reduce(
    (acc, node) => {
      const size = nodeSizeByType(node.type);
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

  return (
    <div className="w-full overflow-auto rounded-xl border border-slate-200 bg-slate-50">
      <svg width={width} height={height} className="min-w-full">
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

        {/* Draw regular edges */}
        {edges.map((edge) => {
          const source = nodeById.get(edge.from);
          const target = nodeById.get(edge.to);
          if (!source || !target || isBoundaryEvent(source) || isBoundaryEvent(target)) return null;

          const edgeKey = `${edge.from}::${edge.to}`;
          const isVisitedEdge = visitedEdges.has(edgeKey);
          const path = getOrthogonalPath(
            { ...source, position: { x: (source.position?.x ?? 0) + offsetX, y: (source.position?.y ?? 0) + offsetY } },
            { ...target, position: { x: (target.position?.x ?? 0) + offsetX, y: (target.position?.y ?? 0) + offsetY } }
          );

          return (
            <path
              key={edgeKey}
              d={path}
              fill="none"
              stroke={isVisitedEdge ? '#2563eb' : '#94a3b8'}
              strokeWidth={isVisitedEdge ? '3' : '2'}
              markerEnd={isVisitedEdge ? 'url(#wf-arrow-active)' : 'url(#wf-arrow)'}
              strokeLinecap="round"
              strokeLinejoin="round"
              vectorEffect="non-scaling-stroke"
            />
          );
        })}

        {/* Draw boundary event connections */}
        {boundaryNodes.map((boundaryNode) => {
          if (!boundaryNode.attachedTo) return null;
          
          const parentNode = nodeById.get(boundaryNode.attachedTo);
          if (!parentNode) return null;

          const parentSize = nodeSizeByType(parentNode.type);
          const boundarySize = nodeSizeByType(boundaryNode.type);
          
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
          const size = nodeSizeByType(node.type);
          const x = (node.position?.x ?? 0) + offsetX;
          const y = (node.position?.y ?? 0) + offsetY;
          const visited = visitedSet.has(node.id);
          const current = currentSet.has(node.id);
          const className = getNodeStyle(node.type, visited, current);

          if (node.type === 'StartEvent' || node.type === 'EndEvent') {
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
          const size = nodeSizeByType(node.type);
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
                <path
                  d={`M ${cx} ${cy - 6} L ${cx - 3} ${cy - 2} L ${cx} ${cy + 2} L ${cx - 3} ${cy + 6} L ${cx + 2} ${cy + 2} L ${cx + 2} ${cy - 2} Z`}
                  fill="#dc2626"
                  stroke="none"
                />
              )}
              
              {/* Message boundary event - envelope */}
              {node.type === 'MessageBoundaryEvent' && (
                <g>
                  <rect x={cx - 5} y={cy - 4} width="10" height="8" fill="none" stroke="#2563eb" strokeWidth="1" />
                  <path d={`M ${cx - 5} ${cy - 4} L ${cx} ${cy} L ${cx + 5} ${cy - 4}`} fill="none" stroke="#2563eb" strokeWidth="1" />
                </g>
              )}
              
              {/* Timer boundary event - clock */}
              {node.type.toLowerCase().includes('boundary') && node.type.toLowerCase().includes('timer') && (
                <g>
                  <circle cx={cx} cy={cy} r="5" fill="none" stroke="#f59e0b" strokeWidth="1" />
                  <line x1={cx} y1={cy - 4} x2={cx} y2={cy - 2} stroke="#f59e0b" strokeWidth="1" />
                  <line x1={cx + 3} y1={cy} x2={cx + 4} y2={cy} stroke="#f59e0b" strokeWidth="1" />
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
      </svg>
    </div>
  );
};
