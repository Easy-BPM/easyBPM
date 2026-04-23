import React, { useRef, useState, useEffect } from 'react';
import { BpmnNode, BpmnEdge, NodeType, Position } from '../types';
import { getEdgePath, generateId, snapToGrid } from '../utils/geometry';
import { User, Settings, GitFork, Plus, Mail, Zap, Clock3, Layers, Code } from 'lucide-react';

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
  onDrop: (e: React.DragEvent) => void;
}

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

  const getMousePosition = (e: React.MouseEvent | MouseEvent): Position => {
    if (!svgRef.current) return { x: 0, y: 0 };
    const CTM = svgRef.current.getScreenCTM();
    if (!CTM) return { x: 0, y: 0 };
    return {
      x: (e.clientX - CTM.e) / CTM.a,
      y: (e.clientY - CTM.f) / CTM.d,
    };
  };

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
                if (b.attachedTo === n.uid) {
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

  const handleMouseMove = (e: React.MouseEvent) => {
    const pos = getMousePosition(e);
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
            if ((node.type === 'error-boundary' || node.type === 'message-boundary' || node.type === 'timer-boundary') && initialNodePositions.has(node.uid)) {
                // Try to snap to a task
                const parent = nodes.find(n => 
                    (n.type === 'user-task' || n.type === 'service-task' || n.type === 'api-task' || n.type === 'code-task') &&
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
      if (connectingNodeUid && connectingNodeUid !== targetNode.uid) {
          e.stopPropagation();
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
    <div className="flex-1 bg-slate-50 relative overflow-hidden select-none" onDragOver={(e) => e.preventDefault()} onDrop={onDrop}>
        <div className="absolute inset-0 pointer-events-none opacity-20" style={{ backgroundImage: 'linear-gradient(#94a3b8 1px, transparent 1px), linear-gradient(90deg, #94a3b8 1px, transparent 1px)', backgroundSize: '10px 10px' }}></div>
      <svg ref={svgRef} className="w-full h-full" onMouseMove={handleMouseMove} onMouseUp={handleMouseUp} onMouseDown={handleMouseDownCanvas}>
        <defs>
          <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto"><polygon points="0 0, 10 3.5, 0 7" fill="#334155" /></marker>
          <marker id="arrowhead-selected" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto"><polygon points="0 0, 10 3.5, 0 7" fill="#3b82f6" /></marker>
          <filter id="shadow" x="-50%" y="-50%" width="200%" height="200%"><feDropShadow dx="0" dy="1" stdDeviation="2" floodColor="#cbd5e1" floodOpacity="0.5"/></filter>
        </defs>

        {edges.map((edge) => {
          const source = nodes.find((n) => n.uid === edge.source);
          const target = nodes.find((n) => n.uid === edge.target);
          if (!source || !target) return null;
          const path = getEdgePath(source, target);
          const isSelected = selectedEdgeId === edge.id;
            const hasError = invalidEdgeIds.includes(edge.id);
            const hasWarning = warningEdgeIds.includes(edge.id);
          const labelPos = getLabelPosition(path);
            const stroke = isSelected ? '#3b82f6' : hasError ? '#dc2626' : hasWarning ? '#d97706' : '#334155';
            const markerId = isSelected ? 'url(#arrowhead-selected)' : 'url(#arrowhead)';
          return (
            <g key={edge.id} onClick={(e) => {e.stopPropagation(); onSelectEdge(edge.id); onSelectNodes([]);}} className="group cursor-pointer">
                <path d={path} stroke="transparent" strokeWidth="15" fill="none" />
              <path d={path} fill="none" stroke={stroke} strokeWidth={isSelected ? "3" : hasError ? "3" : "2"} markerEnd={markerId} strokeLinejoin="round" strokeDasharray={hasWarning && !isSelected ? '6 4' : undefined} />
                {edge.condition && labelPos && (
                    <g transform={`translate(${labelPos.x}, ${labelPos.y})`}>
                        <rect x="-10" y="-10" width="20" height="20" fill="white" className="opacity-80" />
                        <text y="4" textAnchor="middle" className="text-[10px] fill-slate-600 font-mono font-bold">♦</text>
                    </g>
                )}
            </g>
          );
        })}

        {connectingNodeUid && (
            <line 
                x1={nodes.find(n => n.uid === connectingNodeUid)?.position.x! + (nodes.find(n => n.uid === connectingNodeUid)?.width! / 2)}
                y1={nodes.find(n => n.uid === connectingNodeUid)?.position.y! + (nodes.find(n => n.uid === connectingNodeUid)?.height! / 2)}
                x2={mousePos.x} y2={mousePos.y} stroke="#3b82f6" strokeWidth="2" strokeDasharray="5,5"
            />
        )}

        {nodes.map((node) => {
          const isTask = node.type === 'user-task' || node.type === 'service-task' || node.type === 'api-task' || node.type === 'code-task' || node.type === 'call-activity';
          const isBoxMessageCatch = node.type === 'message-intermediate-catch';
          const isMessageEvent = ['message-start', 'message-intermediate-catch', 'message-intermediate-throw'].includes(node.type);
          const isSelected = selectedNodeUids.includes(node.uid);
          const hasError = invalidNodeUids.includes(node.uid);
          const hasWarning = warningNodeUids.includes(node.uid);
          return (
          <g key={node.uid} transform={`translate(${node.position.x + node.width / 2}, ${node.position.y + node.height / 2})`} onMouseDown={(e) => handleMouseDownNode(e, node)} onMouseUp={(e) => handleNodeMouseUp(e, node)} onMouseEnter={() => setHoveredNodeUid(node.uid)} onMouseLeave={() => setHoveredNodeUid(null)} className="cursor-move group">
            {isSelected && <rect x={-node.width/2-4} y={-node.height/2-4} width={node.width+8} height={node.height+8} rx={(isTask || isBoxMessageCatch) ? 8 : (['gateway', 'parallel-gateway'].includes(node.type) ? 4 : '50%')} fill="none" stroke="#3b82f6" strokeWidth="1.5" strokeDasharray="4 2" />}
            {!isSelected && hasError && <rect x={-node.width/2-6} y={-node.height/2-6} width={node.width+12} height={node.height+12} rx={(isTask || isBoxMessageCatch) ? 10 : (['gateway', 'parallel-gateway'].includes(node.type) ? 6 : '50%')} fill="none" stroke="#dc2626" strokeWidth="2" strokeDasharray="5 3" />}
            {!isSelected && !hasError && hasWarning && <rect x={-node.width/2-6} y={-node.height/2-6} width={node.width+12} height={node.height+12} rx={(isTask || isBoxMessageCatch) ? 10 : (['gateway', 'parallel-gateway'].includes(node.type) ? 6 : '50%')} fill="none" stroke="#d97706" strokeWidth="2" strokeDasharray="5 3" />}
            {node.type === 'start' && <circle r="20" filter="url(#shadow)" className="fill-white stroke-green-500 stroke-[2px]" />}
            {node.type === 'message-start' && (
              <g>
                <circle r="20" filter="url(#shadow)" className="fill-white stroke-green-600 stroke-[2px]" />
                <Mail x="-8" y="-8" className="w-4 h-4 text-green-600 pointer-events-none opacity-80" />
              </g>
            )}
            {node.type === 'timer-event' && (
              <g>
                <circle r="20" filter="url(#shadow)" className="fill-white stroke-amber-600 stroke-[1px]" />
                <circle r="17" className="fill-none stroke-amber-600 stroke-[1px]" />
                <Clock3 x="-8" y="-8" className="w-4 h-4 text-amber-600 pointer-events-none opacity-80" />
              </g>
            )}
            {node.type === 'message-intermediate-catch' && (
              <g>
                <rect x={-node.width/2} y={-node.height/2} width={node.width} height={node.height} rx="6" filter="url(#shadow)" className="fill-white stroke-blue-500 stroke-[2px]" />
                <rect x={-node.width/2+4} y={-node.height/2+4} width={node.width-8} height={node.height-8} rx="4" className="fill-none stroke-blue-500 stroke-[1px]" />
                <Mail x={-node.width/2+8} y={-node.height/2+8} className="w-4 h-4 text-blue-500 pointer-events-none opacity-80" />
              </g>
            )}
            {node.type === 'message-intermediate-throw' && (
              <g>
                <circle r="20" filter="url(#shadow)" className="fill-white stroke-blue-500 stroke-[1px]" />
                <circle r="17" className="fill-none stroke-blue-500 stroke-[1px]" />
                <Mail x="-8" y="-8" className="w-4 h-4 text-blue-500 fill-current pointer-events-none opacity-80" />
              </g>
            )}
            {(isMessageEvent || isTask) && ((node.data.inputVariables?.length || 0) > 0 || (node.data.outputVariables?.length || 0) > 0) && (
              <g transform={`translate(${(isTask || isBoxMessageCatch) ? node.width/2 : 12}, ${(isTask || isBoxMessageCatch) ? -node.height/2 : -18})`}>
                <circle r="6" className="fill-blue-500 stroke-white stroke-[1.5px]" />
                <text y="3" textAnchor="middle" className="text-[8px] fill-white font-bold pointer-events-none">{ (node.data.inputVariables?.length || 0) + (node.data.outputVariables?.length || 0) }</text>
              </g>
            )}
            {node.type === 'error-boundary' && (
              <g>
                <circle r="15" filter="url(#shadow)" className="fill-white stroke-slate-500 stroke-[1px] stroke-dasharray-[2,2]" />
                <Zap x="-6" y="-6" className="w-3 h-3 text-red-500 fill-current pointer-events-none" />
              </g>
            )}
            {node.type === 'message-boundary' && (
              <g>
                <circle r="15" filter="url(#shadow)" className="fill-white stroke-slate-500 stroke-[1px] stroke-dasharray-[2,2]" />
                <Mail x="-6" y="-6" className="w-3 h-3 text-blue-500 pointer-events-none" />
              </g>
            )}
            {node.type === 'timer-boundary' && (
              <g>
                <circle r="15" filter="url(#shadow)" className="fill-white stroke-slate-500 stroke-[1px] stroke-dasharray-[2,2]" />
                <Clock3 x="-6" y="-6" className="w-3 h-3 text-amber-600 pointer-events-none" />
              </g>
            )}
            {node.type === 'end' && <circle r="20" filter="url(#shadow)" className="fill-white stroke-red-500 stroke-[4px]" />}
            {['gateway', 'parallel-gateway'].includes(node.type) && <rect width="28" height="28" transform="rotate(45)" x="-14" y="-14" filter="url(#shadow)" className="fill-white stroke-orange-500 stroke-[2px]" />}
            {isTask && <rect x={-node.width/2} y={-node.height/2} width={node.width} height={node.height} rx="6" filter="url(#shadow)" className={`fill-white stroke-[2px] ${node.type === 'user-task' ? 'stroke-blue-600' : (node.type === 'api-task' ? 'stroke-purple-600' : (node.type === 'code-task' ? 'stroke-indigo-600' : (node.type === 'call-activity' ? 'stroke-cyan-600' : 'stroke-amber-600')))}`} />}
            {node.type === 'user-task' && <User x={-node.width/2+8} y={-node.height/2+8} className="w-4 h-4 text-blue-600 pointer-events-none opacity-80" />}
            {node.type === 'api-task' && <Settings x={-node.width/2+8} y={-node.height/2+8} className="w-4 h-4 text-purple-600 pointer-events-none opacity-80" />}
            {node.type === 'service-task' && <Zap x={-node.width/2+8} y={-node.height/2+8} className="w-4 h-4 text-amber-600 pointer-events-none opacity-80" />}
            {node.type === 'code-task' && <Code x={-node.width/2+8} y={-node.height/2+8} className="w-4 h-4 text-indigo-600 pointer-events-none opacity-80" />}
            {node.type === 'call-activity' && <Layers x={-node.width/2+8} y={-node.height/2+8} className="w-4 h-4 text-cyan-600 pointer-events-none opacity-80" />}
            {node.type === 'gateway' && <GitFork x="-8" y="-8" className="w-4 h-4 text-orange-600 pointer-events-none opacity-80" />}
            {node.type === 'parallel-gateway' && <Plus x="-8" y="-8" className="w-4 h-4 text-orange-600 pointer-events-none opacity-80" />}
            <foreignObject 
              x={(isTask || isBoxMessageCatch) ? -node.width/2 : -(node.width+80)/2} 
              y={(isTask || isBoxMessageCatch) ? -node.height/2 : node.height/2+1} 
              width={(isTask || isBoxMessageCatch) ? node.width : node.width+80} 
              height={(isTask || isBoxMessageCatch) ? node.height : 40} 
              style={{ pointerEvents: 'none' }}
            >
                <div className="flex items-center justify-center h-full px-1 text-center">
                  <span className={`font-medium leading-tight line-clamp-3 ${(isTask || isBoxMessageCatch) ? 'text-[11px] text-slate-700' : 'text-[10px] text-slate-600'}`}>
                    {node.data.label}
                  </span>
                </div>
            </foreignObject>
            <g className="opacity-0 group-hover:opacity-100 transition-opacity">
                {[{x:0,y:-node.height/2},{x:node.width/2,y:0},{x:0,y:node.height/2},{x:-node.width/2,y:0}].map((h, i) => (
                    <circle key={i} cx={h.x} cy={h.y} r="6" className="fill-white stroke-blue-500 stroke-2 hover:fill-blue-500 cursor-crosshair" onMouseDown={(e) => handleConnectionStart(e, node.uid)} />
                ))}
            </g>
            {(hasError || hasWarning) && (
              <g transform={`translate(${node.width/2 + 10}, ${-node.height/2 - 10})`}>
                <circle r="9" fill={hasError ? '#dc2626' : '#d97706'} stroke="#ffffff" strokeWidth="2" />
                <text y="3" textAnchor="middle" className="text-[10px] fill-white font-bold pointer-events-none">!</text>
              </g>
            )}
          </g>
        )})}
        {selectionBox && <rect x={Math.min(selectionBox.start.x, selectionBox.current.x)} y={Math.min(selectionBox.start.y, selectionBox.current.y)} width={Math.abs(selectionBox.current.x - selectionBox.start.x)} height={Math.abs(selectionBox.current.y - selectionBox.start.y)} fill="rgba(59, 130, 246, 0.1)" stroke="#3b82f6" strokeWidth="1" strokeDasharray="4,2" pointerEvents="none" />}
      </svg>
    </div>
  );
};