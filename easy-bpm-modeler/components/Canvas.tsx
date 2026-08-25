import React, { useMemo, useRef, useState } from 'react';
import { BpmnNode, BpmnEdge, NodeType, Position } from '../types';
import { getEdgePath, getEdgeRoutePoints, generateId, snapToGrid } from '../utils/geometry';
import { Bot, User, Settings, GitFork, Plus, Mail, Zap, Clock3, Layers, Code, Brain, ZoomIn, ZoomOut, RotateCcw } from 'lucide-react';

interface CanvasProps {
  nodes: BpmnNode[];
  edges: BpmnEdge[];
  selectedNodeUids: string[];
  selectedEdgeId: string | null;
  invalidNodeUids: string[];
  warningNodeUids: string[];
  invalidEdgeIds: string[];
  warningEdgeIds: string[];
  onSelectNodes: (uids: string[]) => void;
  onSelectEdge: (id: string | null) => void;
  onNodesChange: (nodes: BpmnNode[]) => void;
  onEdgesChange: (edges: BpmnEdge[]) => void;
  onDrop: (e: React.DragEvent, canvasPoint?: Position) => void;
}

type ResizeHandle = 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w' | 'nw';

interface ResizeState {
  uid: string;
  handle: ResizeHandle;
  start: Position;
  initial: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
}

interface WaypointDragState {
  edgeId: string;
  waypointIndex: number;
}

const POOL_MIN_WIDTH = 240;
const POOL_MIN_HEIGHT = 140;
const DOCUMENTATION_MIN_WIDTH = 120;
const DOCUMENTATION_MIN_HEIGHT = 72;
const MIN_ZOOM = 0.5;
const MAX_ZOOM = 1.5;
const ZOOM_STEP = 0.1;
const WORKSPACE_MIN_WIDTH = 2600;
const WORKSPACE_MIN_HEIGHT = 1800;
const WORKSPACE_PADDING = 600;
const BOUNDARY_TYPES: NodeType[] = ['error-boundary', 'message-boundary', 'timer-boundary'];
const ATTACHABLE_NODE_TYPES: NodeType[] = [
  'user-task',
  'service-task',
  'api-task',
  'code-task',
  'ai-task',
  'agent-process-call',
  'call-activity'
];
const SINGLE_OUTGOING_NODE_TYPES: NodeType[] = [
  'user-task',
  'service-task',
  'api-task',
  'code-task',
  'ai-task',
  'agent-process-call',
  'call-activity'
];

const isBoundaryNode = (node: BpmnNode) => BOUNDARY_TYPES.includes(node.type);
const isAttachableNode = (node: BpmnNode) => ATTACHABLE_NODE_TYPES.includes(node.type);
const isVisualNode = (node: BpmnNode) => node.type === 'pool' || node.type === 'documentation';
const isBoundaryAttachedToNode = (boundary: BpmnNode, parent: BpmnNode) =>
  boundary.attachedTo === parent.uid || boundary.attachedTo === parent.id;

export const Canvas: React.FC<CanvasProps> = ({
  nodes,
  edges,
  selectedNodeUids,
  selectedEdgeId,
  invalidNodeUids,
  warningNodeUids,
  invalidEdgeIds,
  warningEdgeIds,
  onSelectNodes,
  onSelectEdge,
  onNodesChange,
  onEdgesChange,
  onDrop,
}) => {
  const svgRef = useRef<SVGSVGElement>(null);
  
  // Dragging State
  const [isDraggingNodes, setIsDraggingNodes] = useState(false);
  const [dragStartPos, setDragStartPos] = useState<Position>({ x: 0, y: 0 });
  const [initialNodePositions, setInitialNodePositions] = useState<Map<string, Position>>(new Map());

  // Connection State
  const [connectingNodeUid, setConnectingNodeUid] = useState<string | null>(null);
  const [mousePos, setMousePos] = useState<Position>({ x: 0, y: 0 });
  
  // Interaction State
  const [hoveredNodeUid, setHoveredNodeUid] = useState<string | null>(null);
  
  // Selection Box State
  const [selectionBox, setSelectionBox] = useState<{ start: Position; current: Position } | null>(null);
  const [resizeState, setResizeState] = useState<ResizeState | null>(null);
  const [waypointDragState, setWaypointDragState] = useState<WaypointDragState | null>(null);
  const [zoom, setZoom] = useState(1);

  const getMousePosition = (e: Pick<MouseEvent, 'clientX' | 'clientY'> | Pick<React.MouseEvent, 'clientX' | 'clientY'> | Pick<React.DragEvent, 'clientX' | 'clientY'>): Position => {
    if (!svgRef.current) return { x: 0, y: 0 };
    const CTM = svgRef.current.getScreenCTM();
    if (!CTM) return { x: 0, y: 0 };
    return {
      x: ((e.clientX - CTM.e) / CTM.a) / zoom,
      y: ((e.clientY - CTM.f) / CTM.d) / zoom,
    };
  };

  const clampZoom = (value: number) => Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, Number(value.toFixed(2))));
  const zoomPercent = Math.round(zoom * 100);
  const workspace = useMemo(() => {
    const bounds = nodes.reduce(
      (acc, node) => ({
        maxX: Math.max(acc.maxX, node.position.x + node.width),
        maxY: Math.max(acc.maxY, node.position.y + node.height),
      }),
      { maxX: 0, maxY: 0 }
    );

    return {
      width: Math.max(WORKSPACE_MIN_WIDTH, bounds.maxX + WORKSPACE_PADDING),
      height: Math.max(WORKSPACE_MIN_HEIGHT, bounds.maxY + WORKSPACE_PADDING),
    };
  }, [nodes]);

  const handleMouseDownNode = (e: React.MouseEvent, node: BpmnNode) => {
    e.stopPropagation();
    const isMultiSelect = e.shiftKey || e.metaKey || e.ctrlKey;
    const isAlreadySelected = selectedNodeUids.includes(node.uid);

    let newSelectedUids = selectedNodeUids;

    if (isMultiSelect) {
        if (isAlreadySelected) {
            newSelectedUids = selectedNodeUids.filter(uid => uid !== node.uid);
        } else {
            newSelectedUids = [...selectedNodeUids, node.uid];
        }
        onSelectNodes(newSelectedUids);
    } else {
        if (!isAlreadySelected) {
            newSelectedUids = [node.uid];
            onSelectNodes(newSelectedUids);
        }
    }

    onSelectEdge(null);

    const pos = getMousePosition(e);
    setDragStartPos(pos);
    
    const initialPosMap = new Map<string, Position>();
    nodes.forEach(n => {
        if (newSelectedUids.includes(n.uid)) {
            initialPosMap.set(n.uid, { ...n.position });
            // Also add boundary events attached to this node
            nodes.forEach(b => {
              if (isBoundaryNode(b) && isBoundaryAttachedToNode(b, n)) {
                initialPosMap.set(b.uid, { ...b.position });
              }
            });
        }
    });
    setInitialNodePositions(initialPosMap);
    setIsDraggingNodes(true);
  };

  const handleMouseDownCanvas = (e: React.MouseEvent) => {
    const pos = getMousePosition(e);
    setSelectionBox({ start: pos, current: pos });
    onSelectNodes([]);
    onSelectEdge(null);
  };

  const handleConnectionStart = (e: React.MouseEvent, nodeUid: string) => {
      e.stopPropagation();
      setConnectingNodeUid(nodeUid);
      const pos = getMousePosition(e);
      setMousePos(pos);
  };

  const handleResizeStart = (e: React.MouseEvent, node: BpmnNode, handle: ResizeHandle) => {
    e.stopPropagation();
    onSelectNodes([node.uid]);
    onSelectEdge(null);
    setIsDraggingNodes(false);
    setSelectionBox(null);
    setResizeState({
      uid: node.uid,
      handle,
      start: getMousePosition(e),
      initial: {
        x: node.position.x,
        y: node.position.y,
        width: node.width,
        height: node.height,
      },
    });
  };

  const handleWaypointDragStart = (e: React.MouseEvent, edgeId: string, waypointIndex: number) => {
    e.stopPropagation();
    onSelectEdge(edgeId);
    onSelectNodes([]);
    setSelectionBox(null);
    setIsDraggingNodes(false);
    setWaypointDragState({ edgeId, waypointIndex });
  };

  const handleAddWaypoint = (e: React.MouseEvent, edge: BpmnEdge, point: Position, insertIndex: number) => {
    e.stopPropagation();
    const newWaypoint = { x: snapToGrid(point.x), y: snapToGrid(point.y) };
    const waypoints = [...(edge.waypoints || [])];
    waypoints.splice(insertIndex, 0, newWaypoint);
    onEdgesChange(edges.map(item => item.id === edge.id ? { ...item, waypoints } : item));
    onSelectEdge(edge.id);
    onSelectNodes([]);
    setWaypointDragState({ edgeId: edge.id, waypointIndex: insertIndex });
  };

  const handleRemoveWaypoint = (e: React.MouseEvent, edge: BpmnEdge, waypointIndex: number) => {
    e.stopPropagation();
    const waypoints = (edge.waypoints || []).filter((_, index) => index !== waypointIndex);
    onEdgesChange(edges.map(item => item.id === edge.id ? { ...item, waypoints: waypoints.length > 0 ? waypoints : undefined } : item));
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    const pos = getMousePosition(e);
    if (resizeState) {
      const dx = pos.x - resizeState.start.x;
      const dy = pos.y - resizeState.start.y;
      const initial = resizeState.initial;
      const resizingNode = nodes.find(node => node.uid === resizeState.uid);
      const minWidth = resizingNode?.type === 'documentation' ? DOCUMENTATION_MIN_WIDTH : POOL_MIN_WIDTH;
      const minHeight = resizingNode?.type === 'documentation' ? DOCUMENTATION_MIN_HEIGHT : POOL_MIN_HEIGHT;
      const right = initial.x + initial.width;
      const bottom = initial.y + initial.height;

      let x = initial.x;
      let y = initial.y;
      let width = initial.width;
      let height = initial.height;

      if (resizeState.handle.includes('e')) {
        width = snapToGrid(Math.max(minWidth, initial.width + dx));
      }
      if (resizeState.handle.includes('s')) {
        height = snapToGrid(Math.max(minHeight, initial.height + dy));
      }
      if (resizeState.handle.includes('w')) {
        x = snapToGrid(Math.min(initial.x + dx, right - minWidth));
        width = right - x;
      }
      if (resizeState.handle.includes('n')) {
        y = snapToGrid(Math.min(initial.y + dy, bottom - minHeight));
        height = bottom - y;
      }

      onNodesChange(nodes.map((node) => node.uid === resizeState.uid
        ? { ...node, position: { x, y }, width, height }
        : node
      ));
      return;
    }

    if (waypointDragState) {
      const updatedWaypoint = { x: snapToGrid(pos.x), y: snapToGrid(pos.y) };
      onEdgesChange(edges.map((edge) => {
        if (edge.id !== waypointDragState.edgeId) return edge;
        const waypoints = [...(edge.waypoints || [])];
        waypoints[waypointDragState.waypointIndex] = updatedWaypoint;
        return { ...edge, waypoints };
      }));
      return;
    }

    if (connectingNodeUid) setMousePos(pos);
    if (selectionBox) setSelectionBox({ ...selectionBox, current: pos });

    if (isDraggingNodes) {
      const dx = pos.x - dragStartPos.x;
      const dy = pos.y - dragStartPos.y;

      const updatedNodes = nodes.map((node) => {
        if (initialNodePositions.has(node.uid)) {
            const initialPos = initialNodePositions.get(node.uid)!;
            let newX = snapToGrid(initialPos.x + dx);
            let newY = snapToGrid(initialPos.y + dy);
            return { ...node, position: { x: newX, y: newY } };
        }
        return node;
      });
      onNodesChange(updatedNodes);
    }
  };

  const handleMouseUp = (e: React.MouseEvent) => {
    if (isDraggingNodes) {
        const updatedNodes = nodes.map(node => {
            if (isBoundaryNode(node) && initialNodePositions.has(node.uid)) {
                const attachedParent = node.attachedTo
                  ? nodes.find(candidate => candidate.uid === node.attachedTo || candidate.id === node.attachedTo)
                  : undefined;
                const movedWithParent = attachedParent &&
                  initialNodePositions.has(attachedParent.uid) &&
                  !selectedNodeUids.includes(node.uid);

                if (movedWithParent) {
                    return { ...node, attachedTo: attachedParent.uid };
                }

                // Try to snap to a task
                const parent = nodes.find(n => 
                    isAttachableNode(n) &&
                    node.position.x > n.position.x - 20 &&
                    node.position.x < n.position.x + n.width + 20 &&
                    node.position.y > n.position.y - 20 &&
                    node.position.y < n.position.y + n.height + 20
                );
                if (parent) {
                    // Snap to the closest edge
                    const distLeft = Math.abs(node.position.x - parent.position.x);
                    const distRight = Math.abs(node.position.x - (parent.position.x + parent.width));
                    const distTop = Math.abs(node.position.y - parent.position.y);
                    const distBottom = Math.abs(node.position.y - (parent.position.y + parent.height));
                    
                    const minDist = Math.min(distLeft, distRight, distTop, distBottom);
                    let nx = node.position.x;
                    let ny = node.position.y;
                    
                    if (minDist === distLeft) nx = parent.position.x - node.width / 2;
                    else if (minDist === distRight) nx = parent.position.x + parent.width - node.width / 2;
                    else if (minDist === distTop) ny = parent.position.y - node.height / 2;
                    else if (minDist === distBottom) ny = parent.position.y + parent.height - node.height / 2;

                    return { ...node, position: { x: nx, y: ny }, attachedTo: parent.uid };
                } else {
                    // If not attached to a valid task, we mark it for removal or just clear attachment
                    // The user said "only can be attached", so we'll enforce attachment by removing it if not snapped
                    return { ...node, attachedTo: 'REMOVE_ME' as any };
                }
            }
            return node;
        }).filter(n => n.attachedTo !== 'REMOVE_ME' as any);
        onNodesChange(updatedNodes);
    }

    setIsDraggingNodes(false);
    setInitialNodePositions(new Map());
    setConnectingNodeUid(null);
    setResizeState(null);
    setWaypointDragState(null);

    if (selectionBox) {
        const x = Math.min(selectionBox.start.x, selectionBox.current.x);
        const y = Math.min(selectionBox.start.y, selectionBox.current.y);
        const w = Math.abs(selectionBox.current.x - selectionBox.start.x);
        const h = Math.abs(selectionBox.current.y - selectionBox.start.y);

        if (w > 2 && h > 2) {
            const uidsInBox = nodes.filter(node => {
                return (
                    node.position.x < x + w &&
                    node.position.x + node.width > x &&
                    node.position.y < y + h &&
                    node.position.y + node.height > y
                );
            }).map(n => n.uid);
            onSelectNodes(uidsInBox);
        }
        setSelectionBox(null);
    }
  };

  const handleNodeMouseUp = (e: React.MouseEvent, targetNode: BpmnNode) => {
      if (targetNode.type === 'pool') {
          setConnectingNodeUid(null);
          return;
      }
      if (connectingNodeUid && connectingNodeUid !== targetNode.uid) {
          e.stopPropagation();
          const sourceNode = nodes.find((node) => node.uid === connectingNodeUid);
          const isDocumentationAssociation = sourceNode?.type === 'documentation' || targetNode.type === 'documentation';
          if (sourceNode?.type === 'pool') {
            setConnectingNodeUid(null);
            return;
          }
          if (!isDocumentationAssociation && sourceNode && SINGLE_OUTGOING_NODE_TYPES.includes(sourceNode.type) && edges.some(edge => edge.source === connectingNodeUid)) {
            setConnectingNodeUid(null);
            return;
          }
          const exists = edges.some(edge => edge.source === connectingNodeUid && edge.target === targetNode.uid);
          if (!exists) {
            const newEdge: BpmnEdge = {
                id: generateId('edge'),
                source: connectingNodeUid,
                target: targetNode.uid
            };
            onEdgesChange([...edges, newEdge]);
          }
          setConnectingNodeUid(null);
      }
  };

  const getLabelPosition = (pathString: string): Position | null => {
      const commands = pathString.split(' ');
      if (commands.length < 6) return null;
      const x1 = parseFloat(commands[1]);
      const y1 = parseFloat(commands[2]);
      const xLast = parseFloat(commands[commands.length - 2]);
      const yLast = parseFloat(commands[commands.length - 1]);
      return { x: (x1 + xLast) / 2, y: (y1 + yLast) / 2 };
  };

  return (
    <div className="modeler-canvas flex-1 relative overflow-hidden select-none">
      <div className="absolute inset-0 pointer-events-none bg-[radial-gradient(circle_at_50%_30%,rgba(37,99,235,0.08),transparent_42%)]"></div>
      <div className="absolute right-4 top-4 z-20 flex items-center gap-1 rounded-md border border-slate-200 bg-white/95 p-1 shadow-sm backdrop-blur-sm">
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
      <div
        className="absolute inset-0 overflow-auto"
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => onDrop(e, getMousePosition(e))}
      >
      <svg
        ref={svgRef}
        width={workspace.width * zoom}
        height={workspace.height * zoom}
        className="block"
        style={{
          backgroundImage: 'radial-gradient(circle, var(--modeler-canvas-grid) 1px, transparent 1.2px)',
          backgroundSize: `${24 * zoom}px ${24 * zoom}px`,
        }}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseDown={handleMouseDownCanvas}
      >
        <defs>
          <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto"><polygon points="0 0, 10 3.5, 0 7" fill="#94a3b8" /></marker>
          <marker id="arrowhead-selected" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto"><polygon points="0 0, 10 3.5, 0 7" fill="#3b82f6" /></marker>
          <marker id="arrowhead-error" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto"><polygon points="0 0, 10 3.5, 0 7" fill="#dc2626" /></marker>
          <filter id="shadow" x="-50%" y="-50%" width="200%" height="200%"><feDropShadow dx="0" dy="4" stdDeviation="6" floodColor="#020617" floodOpacity="0.55"/></filter>
        </defs>

        <g transform={`scale(${zoom})`}>
        {nodes.filter(node => node.type === 'pool').map((node) => {
          const isSelected = selectedNodeUids.includes(node.uid);
          const hasError = invalidNodeUids.includes(node.uid);
          const hasWarning = warningNodeUids.includes(node.uid);
          const label = node.data.label || node.id;

          return (
            <g
              key={node.uid}
              transform={`translate(${node.position.x + node.width / 2}, ${node.position.y + node.height / 2})`}
              onMouseDown={(e) => handleMouseDownNode(e, node)}
              onMouseUp={(e) => handleNodeMouseUp(e, node)}
              onMouseEnter={() => setHoveredNodeUid(node.uid)}
              onMouseLeave={() => setHoveredNodeUid(null)}
              className="cursor-move group"
            >
              {isSelected && <rect x={-node.width/2-4} y={-node.height/2-4} width={node.width+8} height={node.height+8} rx="10" fill="none" stroke="#3b82f6" strokeWidth="1.5" strokeDasharray="4 2" />}
              {!isSelected && hasError && <rect x={-node.width/2-6} y={-node.height/2-6} width={node.width+12} height={node.height+12} rx="12" fill="none" stroke="#dc2626" strokeWidth="2" strokeDasharray="5 3" />}
              {!isSelected && !hasError && hasWarning && <rect x={-node.width/2-6} y={-node.height/2-6} width={node.width+12} height={node.height+12} rx="12" fill="none" stroke="#d97706" strokeWidth="2" strokeDasharray="5 3" />}
              <rect x={-node.width/2} y={-node.height/2} width={node.width} height={node.height} rx="8" className="fill-[#111a21] stroke-sky-500 stroke-[2px] opacity-80" />
              <rect x={-node.width/2} y={-node.height/2} width="44" height={node.height} rx="8" className="fill-blue-500/10 stroke-sky-500 stroke-[1.5px]" />
              <line x1={-node.width/2 + 44} y1={-node.height/2} x2={-node.width/2 + 44} y2={node.height/2} className="stroke-sky-500 stroke-[1.5px]" />
              <line x1={-node.width/2 + 44} y1="0" x2={node.width/2} y2="0" className="stroke-sky-500/40 stroke-[1px]" strokeDasharray="6 4" />
              <text
                transform={`translate(${-node.width/2 + 22}, 0) rotate(-90)`}
                textAnchor="middle"
                className="fill-sky-300 text-[12px] font-semibold pointer-events-none"
              >
                {label}
              </text>
              <text
                x={-node.width/2 + 56}
                y={-node.height/2 + 22}
                className="fill-slate-400 text-[10px] font-mono pointer-events-none"
              >
                {node.id}
              </text>
              {(hasError || hasWarning) && (
                <g transform={`translate(${node.width/2 + 10}, ${-node.height/2 - 10})`}>
                  <circle r="9" fill={hasError ? '#dc2626' : '#d97706'} stroke="#ffffff" strokeWidth="2" />
                  <text y="3" textAnchor="middle" className="text-[10px] fill-white font-bold pointer-events-none">!</text>
                </g>
              )}
              {isSelected && ([
                { handle: 'nw' as ResizeHandle, x: -node.width/2, y: -node.height/2, cursor: 'nwse-resize' },
                { handle: 'n' as ResizeHandle, x: 0, y: -node.height/2, cursor: 'ns-resize' },
                { handle: 'ne' as ResizeHandle, x: node.width/2, y: -node.height/2, cursor: 'nesw-resize' },
                { handle: 'e' as ResizeHandle, x: node.width/2, y: 0, cursor: 'ew-resize' },
                { handle: 'se' as ResizeHandle, x: node.width/2, y: node.height/2, cursor: 'nwse-resize' },
                { handle: 's' as ResizeHandle, x: 0, y: node.height/2, cursor: 'ns-resize' },
                { handle: 'sw' as ResizeHandle, x: -node.width/2, y: node.height/2, cursor: 'nesw-resize' },
                { handle: 'w' as ResizeHandle, x: -node.width/2, y: 0, cursor: 'ew-resize' },
              ]).map(({ handle, x, y, cursor }) => (
                <rect
                  key={handle}
                  x={x - 5}
                  y={y - 5}
                  width="10"
                  height="10"
                  rx="2"
                  fill="#ffffff"
                  stroke="#3b82f6"
                  strokeWidth="1.5"
                  style={{ cursor }}
                  onMouseDown={(e) => handleResizeStart(e, node, handle)}
                />
              ))}
            </g>
          );
        })}

        {edges.map((edge) => {
          const source = nodes.find((n) => n.uid === edge.source);
          const target = nodes.find((n) => n.uid === edge.target);
          if (!source || !target) return null;
          const routePoints = getEdgeRoutePoints(source, target, edge.waypoints);
          const isDocumentationAssociation = source.type === 'documentation' || target.type === 'documentation';
          const sourceCenter = source.type === 'documentation'
            ? { x: source.position.x, y: source.position.y + source.height / 2 }
            : { x: source.position.x + source.width / 2, y: source.position.y + source.height / 2 };
          const targetCenter = target.type === 'documentation'
            ? { x: target.position.x, y: target.position.y + target.height / 2 }
            : { x: target.position.x + target.width / 2, y: target.position.y + target.height / 2 };
          const path = isDocumentationAssociation
            ? `M ${sourceCenter.x} ${sourceCenter.y} L ${targetCenter.x} ${targetCenter.y}`
            : getEdgePath(source, target, edge.waypoints);
          const isSelected = selectedEdgeId === edge.id;
          const hasError = invalidEdgeIds.includes(edge.id);
          const hasWarning = warningEdgeIds.includes(edge.id);
          const isBoundaryEdge = source?.type?.includes('boundary');
          const labelPos = getLabelPosition(path);
          const stroke = isDocumentationAssociation ? (isSelected ? '#3b82f6' : '#64748b') : isSelected ? '#3b82f6' : hasError ? '#ef4444' : isBoundaryEdge ? '#ef4444' : hasWarning ? '#f59e0b' : '#94a3b8';
          const markerId = isDocumentationAssociation ? undefined : isSelected ? 'url(#arrowhead-selected)' : isBoundaryEdge ? 'url(#arrowhead-error)' : 'url(#arrowhead)';
          return (
            <g key={edge.id} onClick={(e) => {e.stopPropagation(); onSelectEdge(edge.id); onSelectNodes([]);}} className="group cursor-pointer">
                <path d={path} stroke="transparent" strokeWidth="15" fill="none" />
              <path d={path} fill="none" stroke={stroke} strokeWidth={isDocumentationAssociation ? (isSelected ? "2" : "1.5") : isSelected ? "3" : isBoundaryEdge ? "2.5" : "2"} markerEnd={markerId} strokeLinejoin="round" strokeDasharray={isDocumentationAssociation ? '2 5' : isBoundaryEdge ? '5,3' : (hasWarning && !isSelected ? '6 4' : undefined)} opacity={isDocumentationAssociation && !isSelected ? 0.7 : 1} />
                {isSelected && !isDocumentationAssociation && (
                  <g>
                    {routePoints.slice(0, -1).map((point, index) => {
                      const nextPoint = routePoints[index + 1];
                      const midpoint = { x: (point.x + nextPoint.x) / 2, y: (point.y + nextPoint.y) / 2 };
                      return (
                        <circle
                          key={`add-${index}`}
                          cx={midpoint.x}
                          cy={midpoint.y}
                          r="5"
                          fill="#3b82f6"
                          stroke="#ffffff"
                          strokeWidth="1.5"
                          className="opacity-80 hover:opacity-100 cursor-copy"
                          onMouseDown={(event) => handleAddWaypoint(event, edge, midpoint, index)}
                        />
                      );
                    })}
                    {(edge.waypoints || []).map((point, index) => (
                      <rect
                        key={`waypoint-${index}`}
                        x={point.x - 6}
                        y={point.y - 6}
                        width="12"
                        height="12"
                        rx="2"
                        fill="#ffffff"
                        stroke="#3b82f6"
                        strokeWidth="1.5"
                        className="cursor-move"
                        onMouseDown={(event) => handleWaypointDragStart(event, edge.id, index)}
                        onDoubleClick={(event) => handleRemoveWaypoint(event, edge, index)}
                      />
                    ))}
                  </g>
                )}
                {edge.condition && labelPos && (
                    <g transform={`translate(${labelPos.x}, ${labelPos.y})`}>
                        <rect x="-10" y="-10" width="20" height="20" fill="#111827" className="opacity-90" />
                        <text y="4" textAnchor="middle" className="text-[10px] fill-slate-300 font-mono font-bold">♦</text>
                    </g>
                )}
            </g>
          );
        })}

        {connectingNodeUid && (
            <line 
                x1={(() => {
                  const node = nodes.find(n => n.uid === connectingNodeUid);
                  return node ? node.position.x + (node.type === 'documentation' ? 0 : node.width / 2) : 0;
                })()}
                y1={(() => {
                  const node = nodes.find(n => n.uid === connectingNodeUid);
                  return node ? node.position.y + node.height / 2 : 0;
                })()}
                x2={mousePos.x} y2={mousePos.y} stroke="#3b82f6" strokeWidth="2" strokeDasharray="5,5"
            />
        )}

        {nodes.filter(node => node.type !== 'pool').map((node) => {
          const isTask = node.type === 'user-task' || node.type === 'service-task' || node.type === 'api-task' || node.type === 'code-task' || node.type === 'ai-task' || node.type === 'agent-process-call' || node.type === 'call-activity';
          const isDocumentation = node.type === 'documentation';
          const isBoxMessageCatch = node.type === 'message-intermediate-catch';
          const isMessageEvent = ['message-start', 'message-intermediate-catch', 'message-intermediate-throw'].includes(node.type);
          const isSelected = selectedNodeUids.includes(node.uid);
          const hasError = invalidNodeUids.includes(node.uid);
          const hasWarning = warningNodeUids.includes(node.uid);
          return (
          <g key={node.uid} transform={`translate(${node.position.x + node.width / 2}, ${node.position.y + node.height / 2})`} onMouseDown={(e) => handleMouseDownNode(e, node)} onMouseUp={(e) => handleNodeMouseUp(e, node)} onMouseEnter={() => setHoveredNodeUid(node.uid)} onMouseLeave={() => setHoveredNodeUid(null)} className="cursor-move group">
            {isSelected && <rect x={-node.width/2-4} y={-node.height/2-4} width={node.width+8} height={node.height+8} rx={(isTask || isBoxMessageCatch || isDocumentation) ? 8 : (['gateway', 'parallel-gateway'].includes(node.type) ? 4 : '50%')} fill="none" stroke="#3b82f6" strokeWidth="1.5" strokeDasharray="4 2" />}
            {!isSelected && hasError && <rect x={-node.width/2-6} y={-node.height/2-6} width={node.width+12} height={node.height+12} rx={(isTask || isBoxMessageCatch || isDocumentation) ? 10 : (['gateway', 'parallel-gateway'].includes(node.type) ? 6 : '50%')} fill="none" stroke="#dc2626" strokeWidth="2" strokeDasharray="5 3" />}
            {!isSelected && !hasError && hasWarning && <rect x={-node.width/2-6} y={-node.height/2-6} width={node.width+12} height={node.height+12} rx={(isTask || isBoxMessageCatch || isDocumentation) ? 10 : (['gateway', 'parallel-gateway'].includes(node.type) ? 6 : '50%')} fill="none" stroke="#d97706" strokeWidth="2" strokeDasharray="5 3" />}
            {isDocumentation && (
              <g>
                <rect
                  x={-node.width/2}
                  y={-node.height/2}
                  width={node.width}
                  height={node.height}
                  fill="transparent"
                  stroke="none"
                />
                <line
                  x1={-node.width/2}
                  y1={-node.height/2}
                  x2={-node.width/2}
                  y2={node.height/2}
                  stroke="var(--modeler-text-muted)"
                  strokeWidth="1.5"
                  opacity="0.72"
                />
                <line
                  x1={-node.width/2}
                  y1={-node.height/2}
                  x2={-node.width/2 + 14}
                  y2={-node.height/2}
                  stroke="var(--modeler-text-muted)"
                  strokeWidth="1.5"
                  opacity="0.72"
                />
                <line
                  x1={-node.width/2}
                  y1={node.height/2}
                  x2={-node.width/2 + 14}
                  y2={node.height/2}
                  stroke="var(--modeler-text-muted)"
                  strokeWidth="1.5"
                  opacity="0.72"
                />
              </g>
            )}
            {node.type === 'start' && <circle r="20" filter="url(#shadow)" className="fill-[#111a21] stroke-green-500 stroke-[2px]" />}
            {node.type === 'message-start' && (
              <g>
                <circle r="20" filter="url(#shadow)" className="fill-[#111a21] stroke-green-500 stroke-[2px]" />
                <Mail x="-8" y="-8" className="w-4 h-4 text-green-600 pointer-events-none opacity-80" />
              </g>
            )}
            {node.type === 'timer-event' && (
              <g>
                <circle r="20" filter="url(#shadow)" className="fill-[#111a21] stroke-amber-500 stroke-[1px]" />
                <circle r="17" className="fill-none stroke-amber-600 stroke-[1px]" />
                <Clock3 x="-8" y="-8" className="w-4 h-4 text-amber-600 pointer-events-none opacity-80" />
              </g>
            )}
            {node.type === 'message-intermediate-catch' && (
              <g>
                <rect x={-node.width/2} y={-node.height/2} width={node.width} height={node.height} rx="6" filter="url(#shadow)" className="fill-[#111a21] stroke-blue-500 stroke-[2px]" />
                <rect x={-node.width/2+4} y={-node.height/2+4} width={node.width-8} height={node.height-8} rx="4" className="fill-none stroke-blue-500 stroke-[1px]" />
                <Mail x={-node.width/2+8} y={-node.height/2+8} className="w-4 h-4 text-blue-500 pointer-events-none opacity-80" />
              </g>
            )}
            {node.type === 'message-intermediate-throw' && (
              <g>
                <circle r="20" filter="url(#shadow)" className="fill-[#111a21] stroke-blue-500 stroke-[1px]" />
                <circle r="17" className="fill-none stroke-blue-500 stroke-[1px]" />
                <Mail x="-8" y="-8" className="w-4 h-4 text-blue-500 fill-current pointer-events-none opacity-80" />
              </g>
            )}
            {(isMessageEvent || isTask) && ((node.data.inputVariables?.length || 0) > 0 || (node.data.outputVariables?.length || 0) > 0) && (
              <g transform={`translate(${(isTask || isBoxMessageCatch) ? node.width/2 : 12}, ${(isTask || isBoxMessageCatch) ? -node.height/2 : -18})`}>
                <circle r="6" className="fill-blue-500 stroke-[#111a21] stroke-[1.5px]" />
                <text y="3" textAnchor="middle" className="text-[8px] fill-white font-bold pointer-events-none">{ (node.data.inputVariables?.length || 0) + (node.data.outputVariables?.length || 0) }</text>
              </g>
            )}
            {node.type === 'error-boundary' && (
              <g>
                <circle r="15" filter="url(#shadow)" className="fill-[#111a21] stroke-red-500 stroke-[1.5px]" strokeDasharray="3,2" />
                <Zap x="-7" y="-7" className="w-3.5 h-3.5 text-red-600 fill-current pointer-events-none" />
              </g>
            )}
            {node.type === 'message-boundary' && (
              <g>
                <circle r="15" filter="url(#shadow)" className="fill-[#111a21] stroke-blue-500 stroke-[1.5px]" strokeDasharray="3,2" />
                <Mail x="-7" y="-7" className="w-3.5 h-3.5 text-blue-500 pointer-events-none" />
              </g>
            )}
            {node.type === 'timer-boundary' && (
              <g>
                <circle r="15" filter="url(#shadow)" className="fill-[#111a21] stroke-amber-500 stroke-[1.5px]" strokeDasharray="3,2" />
                <Clock3 x="-7" y="-7" className="w-3.5 h-3.5 text-amber-600 pointer-events-none" />
              </g>
            )}
            {node.type === 'end' && <circle r="20" filter="url(#shadow)" className="fill-[#111a21] stroke-red-500 stroke-[4px]" />}
            {['gateway', 'parallel-gateway'].includes(node.type) && <rect width="28" height="28" transform="rotate(45)" x="-14" y="-14" filter="url(#shadow)" className="fill-[#111a21] stroke-orange-500 stroke-[2px]" />}
            {isTask && <rect x={-node.width/2} y={-node.height/2} width={node.width} height={node.height} rx="6" filter="url(#shadow)" className={`fill-[#111a21] stroke-[2px] ${node.type === 'user-task' ? 'stroke-blue-500' : (node.type === 'api-task' ? 'stroke-purple-500' : (node.type === 'code-task' ? 'stroke-indigo-500' : (node.type === 'ai-task' ? 'stroke-pink-500' : (node.type === 'agent-process-call' ? 'stroke-cyan-400' : (node.type === 'call-activity' ? 'stroke-cyan-500' : 'stroke-amber-500')))))}`} />}
            {isTask && (
              <rect
                x={-node.width/2 + 8}
                y={-node.height/2 + (node.height - 24) / 2}
                width="24"
                height="24"
                rx="5"
                className="fill-slate-950/70 stroke-white/10 stroke-[1px] pointer-events-none"
              />
            )}
            {node.type === 'user-task' && <User x={-node.width/2+12} y={-node.height/2 + node.height / 2 - 8} className="w-4 h-4 text-blue-500 pointer-events-none" />}
            {node.type === 'api-task' && <Settings x={-node.width/2+12} y={-node.height/2 + node.height / 2 - 8} className="w-4 h-4 text-purple-500 pointer-events-none" />}
            {node.type === 'service-task' && <Zap x={-node.width/2+12} y={-node.height/2 + node.height / 2 - 8} className="w-4 h-4 text-amber-500 pointer-events-none" />}
            {node.type === 'code-task' && <Code x={-node.width/2+12} y={-node.height/2 + node.height / 2 - 8} className="w-4 h-4 text-indigo-500 pointer-events-none" />}
            {node.type === 'ai-task' && <Brain x={-node.width/2+12} y={-node.height/2 + node.height / 2 - 8} className="w-4 h-4 text-pink-500 pointer-events-none" />}
            {node.type === 'agent-process-call' && <Bot x={-node.width/2+12} y={-node.height/2 + node.height / 2 - 8} className="w-4 h-4 text-cyan-400 pointer-events-none" />}
            {node.type === 'call-activity' && <Layers x={-node.width/2+12} y={-node.height/2 + node.height / 2 - 8} className="w-4 h-4 text-cyan-500 pointer-events-none" />}
            {node.type === 'gateway' && <GitFork x="-8" y="-8" className="w-4 h-4 text-orange-600 pointer-events-none opacity-80" />}
            {node.type === 'parallel-gateway' && <Plus x="-8" y="-8" className="w-4 h-4 text-orange-600 pointer-events-none opacity-80" />}
            <foreignObject
              x={isDocumentation ? -node.width/2 + 8 : (isTask ? -node.width/2 + 38 : (isBoxMessageCatch ? -node.width/2 + 30 : -(node.width+80)/2))}
              y={isDocumentation ? -node.height/2 + 5 : ((isTask || isBoxMessageCatch) ? -node.height/2 : node.height/2+1)}
              width={isDocumentation ? Math.max(40, node.width - 16) : (isTask ? Math.max(36, node.width - 46) : (isBoxMessageCatch ? Math.max(36, node.width - 36) : node.width+80))}
              height={isDocumentation ? Math.max(36, node.height - 10) : ((isTask || isBoxMessageCatch) ? node.height : 40)}
              style={{ pointerEvents: 'none' }}
            >
                <div className={`flex h-full ${isDocumentation ? 'flex-col items-start text-left' : `items-center ${isTask || isBoxMessageCatch ? 'justify-start pr-2 text-left' : 'justify-center px-1 text-center'}`}`}>
                  {isDocumentation ? (
                    <>
                      <div className="text-[10px] font-semibold leading-tight text-[var(--modeler-text-soft)]">{node.data.label}</div>
                      <div className="mt-1.5 line-clamp-4 whitespace-pre-wrap text-[9px] leading-snug text-[var(--modeler-text-muted)]">{node.data.description}</div>
                    </>
                  ) : (
                    <span className={`font-semibold leading-tight line-clamp-3 ${(isTask || isBoxMessageCatch) ? 'text-[11px] text-[var(--modeler-node-label)]' : 'text-[10px] text-[var(--modeler-text-soft)]'}`}>
                      {node.data.label}
                    </span>
                  )}
                </div>
            </foreignObject>
            {!isDocumentation && <g className="opacity-0 group-hover:opacity-100 transition-opacity">
                {[{x:0,y:-node.height/2},{x:node.width/2,y:0},{x:0,y:node.height/2},{x:-node.width/2,y:0}].map((h, i) => (
                    <circle key={i} cx={h.x} cy={h.y} r="6" className="fill-[#111a21] stroke-blue-500 stroke-2 hover:fill-blue-500 cursor-crosshair" onMouseDown={(e) => handleConnectionStart(e, node.uid)} />
                ))}
            </g>}
            {isDocumentation && (
              <g className={`${isSelected ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'} transition-opacity`}>
                {[{x: -node.width/2, y: 0}, {x: node.width/2, y: 0}].map((h, i) => (
                  <circle
                    key={i}
                    cx={h.x}
                    cy={h.y}
                    r="4"
                    className="fill-[var(--modeler-surface)] stroke-blue-500 stroke-[1.5px] hover:fill-blue-500 cursor-crosshair"
                    onMouseDown={(e) => handleConnectionStart(e, node.uid)}
                  />
                ))}
              </g>
            )}
            {isDocumentation && isSelected && ([
              { handle: 'nw' as ResizeHandle, x: -node.width/2, y: -node.height/2, cursor: 'nwse-resize' },
              { handle: 'ne' as ResizeHandle, x: node.width/2, y: -node.height/2, cursor: 'nesw-resize' },
              { handle: 'e' as ResizeHandle, x: node.width/2, y: 0, cursor: 'ew-resize' },
              { handle: 'se' as ResizeHandle, x: node.width/2, y: node.height/2, cursor: 'nwse-resize' },
              { handle: 's' as ResizeHandle, x: 0, y: node.height/2, cursor: 'ns-resize' },
              { handle: 'sw' as ResizeHandle, x: -node.width/2, y: node.height/2, cursor: 'nesw-resize' },
              { handle: 'w' as ResizeHandle, x: -node.width/2, y: 0, cursor: 'ew-resize' },
            ]).map(({ handle, x, y, cursor }) => (
              <rect
                key={handle}
                x={x - 5}
                y={y - 5}
                width="10"
                height="10"
                rx="2"
                fill="#ffffff"
                stroke="#3b82f6"
                strokeWidth="1.5"
                style={{ cursor }}
                onMouseDown={(e) => handleResizeStart(e, node, handle)}
              />
            ))}
            {(hasError || hasWarning) && (
              <g transform={`translate(${node.width/2 + 10}, ${-node.height/2 - 10})`}>
                <circle r="9" fill={hasError ? '#dc2626' : '#d97706'} stroke="#ffffff" strokeWidth="2" />
                <text y="3" textAnchor="middle" className="text-[10px] fill-white font-bold pointer-events-none">!</text>
              </g>
            )}
          </g>
        )})}
        {selectionBox && <rect x={Math.min(selectionBox.start.x, selectionBox.current.x)} y={Math.min(selectionBox.start.y, selectionBox.current.y)} width={Math.abs(selectionBox.current.x - selectionBox.start.x)} height={Math.abs(selectionBox.current.y - selectionBox.start.y)} fill="rgba(59, 130, 246, 0.1)" stroke="#3b82f6" strokeWidth="1" strokeDasharray="4,2" pointerEvents="none" />}
        </g>
      </svg>
      </div>
    </div>
  );
};
