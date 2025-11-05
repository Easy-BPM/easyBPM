import React, { useState, useRef } from 'react';
import { useDrop } from 'react-dnd';
import Node from './Node.jsx';

export default function Canvas({ nodes, setNodes, addNode, moveNode, flows, setFlows }) {
  const [nodeSelecionado, setNodeSelecionado] = useState(null);
  const canvasRef = useRef(null);

  // Drop para adicionar nodes da toolbox
  const [, drop] = useDrop(() => ({
    accept: ['node'], // apenas novos nodes da toolbox
    drop: (item, monitor) => {
      const offset = monitor.getClientOffset();
      if (!offset || !canvasRef.current) return;

      const rect = canvasRef.current.getBoundingClientRect();
      const x = offset.x - rect.left;
      const y = offset.y - rect.top;

      const newNode = {
        ...item,
        id: item.id || `${item.type}_${Date.now()}`,
        width: item.width || 100,
        height: item.height || 50,
        x,
        y
      };
      addNode(newNode);
    }
  }));

  // Clique para criar flow
  const handleNodeClick = (nodeId) => {
    if (!nodeSelecionado) {
      setNodeSelecionado(nodeId);
    } else {
      if (nodeSelecionado !== nodeId) {
        setFlows([...flows, { from: nodeSelecionado, to: nodeId }]);
      }
      setNodeSelecionado(null);
    }
  };

  // Renderizar linhas com flechas
  const renderFlows = () => {
    return flows.map((flow, idx) => {
      const fromNode = nodes.find(n => n.id === flow.from);
      const toNode = nodes.find(n => n.id === flow.to);
      if (!fromNode || !toNode) return null;

      const startX = (fromNode.x || 0) + (fromNode.width || 100) / 2;
      const startY = (fromNode.y || 0) + (fromNode.height || 50) / 2;
      const endX = (toNode.x || 0) + (toNode.width || 100) / 2;
      const endY = (toNode.y || 0) + (toNode.height || 50) / 2;
      const midX = (startX + endX) / 2;
      const path = `M ${startX} ${startY} C ${midX} ${startY}, ${midX} ${endY}, ${endX} ${endY}`;

      return (
        <path
          key={idx}
          d={path}
          stroke="black"
          strokeWidth="2"
          fill="none"
          markerEnd="url(#arrowhead)"
        />
      );
    });
  };

  return (
    <div
      ref={canvasRef}
      style={{ width: '100%', height: '100%', position: 'relative', background: '#f0f0f0' }}
    >
      <svg style={{ position: 'absolute', width: '100%', height: '100%' }}>
        <defs>
          <marker
            id="arrowhead"
            markerWidth="10"
            markerHeight="7"
            refX="10"
            refY="3.5"
            orient="auto"
          >
            <polygon points="0 0, 10 3.5, 0 7" fill="black" />
          </marker>
        </defs>
        {renderFlows()}
      </svg>

      {nodes.map(node => (
        <Node
          key={node.id}
          node={{
            ...node,
            x: node.x || 0,
            y: node.y || 0,
            width: node.width || 100,
            height: node.height || 50
          }}
          selected={nodeSelecionado === node.id}
          onClick={() => handleNodeClick(node.id)}
        />
      ))}

      {/* Drop container atrás dos nodes */}
      <div
        ref={drop}
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          width: '100%',
          height: '100%'
        }}
      />
    </div>
  );
}
