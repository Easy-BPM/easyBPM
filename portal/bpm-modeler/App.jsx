import React, { useState } from 'react';
import { DndProvider } from 'react-dnd';
import { HTML5Backend } from 'react-dnd-html5-backend';
import Sidebar from './components/Sidebar.jsx';
import Canvas from './components/Canvas.jsx';
import Toolbar from './components/Toolbar.jsx';

export default function App() {
  const [nodes, setNodes] = useState([]);   // nodes no canvas
  const [flows, setFlows] = useState([]);   // conexões entre nodes

  // função para adicionar node
  const addNode = (node) => {
    const id = node.id || `${node.type}_${Date.now()}_${Math.floor(Math.random() * 10000)}`;
    setNodes(prev => [
      ...prev,
      {
        ...node,
        id,
        x: node.x || 0,
        y: node.y || 0,
        width: node.width || 100,
        height: node.height || 50
      }
    ]);
  };

  // função para mover node
  const moveNode = (id, x, y) => {
    setNodes(prev => prev.map(n => (n.id === id ? { ...n, x, y } : n)));
  };

  return (
    <DndProvider backend={HTML5Backend}>
      <div style={{ display: 'flex', height: '100vh' }}>
        {/* Sidebar com nodes para arrastar */}
        <Sidebar />

        {/* Área principal do canvas */}
        <div style={{ flex: 1, position: 'relative' }}>
          {/* Toolbar opcional */}
          <Toolbar nodes={nodes} flows={flows} />

          {/* Canvas com drag, drop e flows */}
          <Canvas
            nodes={nodes}
            setNodes={setNodes}
            addNode={addNode}
            moveNode={moveNode}
            flows={flows}
            setFlows={setFlows}
          />
        </div>
      </div>
    </DndProvider>
  );
}
