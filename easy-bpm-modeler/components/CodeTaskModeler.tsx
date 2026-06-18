import React, { useState, useRef } from 'react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@radix-ui/react-tabs';
import CodeTaskJarUploadPanel from './CodeTaskJarUploadPanel';
import { CodeTaskPropertyPanel } from './CodeTaskPropertyPanel';
import { CodeTaskNode, CodeTaskPaletteItem } from './CodeTaskNode';
import { AlertCircle } from 'lucide-react';

/**
 * CodeTaskModeler
 * 
 * Main component for Code Task design in the modeler
 * - JAR file upload and management
 * - Canvas with Code Task nodes
 * - Property panel for configuration
 * - Variable mapping UI
 */

interface CodeTaskNodeConfig {
  id: string;
  x: number;
  y: number;
  jarId?: number;
  className?: string;
  methodName?: string;
  inputMappings?: Record<string, string>;
  outputMappings?: Record<string, string>;
}

interface CodeTaskModelerProps {
  processDefinitionKey?: string;
  processVariables?: Array<{ name: string; type?: string }>;
  onNodeCreated?: (nodeConfig: CodeTaskNodeConfig) => void;
  onNodeUpdated?: (nodeConfig: CodeTaskNodeConfig) => void;
  existingNodes?: CodeTaskNodeConfig[];
}

export const CodeTaskModeler: React.FC<CodeTaskModelerProps> = ({
  processDefinitionKey,
  processVariables = [],
  onNodeCreated,
  onNodeUpdated,
  existingNodes = []
}) => {
  const canvasRef = useRef<SVGSVGElement>(null);
  const [nodes, setNodes] = useState<CodeTaskNodeConfig[]>(existingNodes);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [uploadedJars, setUploadedJars] = useState<any[]>([]);
  const [isDraggingFromPalette, setIsDraggingFromPalette] = useState(false);
  const [validationErrors, setValidationErrors] = useState<string[]>([]);

  const selectedNode = nodes.find(n => n.id === selectedNodeId);

  const handleCanvasDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'copy';
  };

  const handleCanvasDrop = (e: React.DragEvent) => {
    e.preventDefault();

    if (!isDraggingFromPalette) return;

    const rect = canvasRef.current?.getBoundingClientRect();
    if (!rect) return;

    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    // Create new Code Task node
    const newNodeId = `codeTask_${Date.now()}`;
    const newNode: CodeTaskNodeConfig = {
      id: newNodeId,
      x: Math.max(0, x - 60), // Center the node
      y: Math.max(0, y - 40)
    };

    setNodes([...nodes, newNode]);
    setSelectedNodeId(newNodeId);
    onNodeCreated?.(newNode);
  };

  const handlePaletteDragStart = () => {
    setIsDraggingFromPalette(true);
  };

  const handlePaletteDragEnd = () => {
    setIsDraggingFromPalette(false);
  };

  const handleNodePropertiesChange = (properties: any) => {
    if (!selectedNode) return;

    const updatedNode = { ...selectedNode, ...properties };
    const updatedNodes = nodes.map(n => n.id === selectedNode.id ? updatedNode : n);
    setNodes(updatedNodes);
    onNodeUpdated?.(updatedNode);

    // Validate
    validateNode(updatedNode);
  };

  const validateNode = (node: CodeTaskNodeConfig) => {
    const errors: string[] = [];

    if (!node.jarId) {
      errors.push('JAR file is required');
    }
    if (!node.className) {
      errors.push('Class is required');
    }
    if (!node.methodName) {
      errors.push('Method is required');
    }
    if (!node.inputMappings || Object.keys(node.inputMappings).length === 0) {
      errors.push('At least one input mapping is recommended');
    }

    setValidationErrors(errors);
  };

  const deleteSelectedNode = () => {
    if (!selectedNodeId) return;
    setNodes(nodes.filter(n => n.id !== selectedNodeId));
    setSelectedNodeId(null);
    setValidationErrors([]);
  };

  return (
    <div className="flex flex-col h-full gap-4 p-4 bg-gray-50">
      <Tabs defaultValue="canvas" className="flex-1 flex flex-col">
        <TabsList className="grid w-full grid-cols-3 bg-white border border-gray-200 rounded-lg">
          <TabsTrigger value="canvas" className="data-[state=active]:bg-blue-50 data-[state=active]:text-blue-700">
            Canvas
          </TabsTrigger>
          <TabsTrigger value="properties" className="data-[state=active]:bg-blue-50 data-[state=active]:text-blue-700">
            Properties
          </TabsTrigger>
          <TabsTrigger value="upload" className="data-[state=active]:bg-blue-50 data-[state=active]:text-blue-700">
            JAR Files
          </TabsTrigger>
        </TabsList>

        {/* Canvas Tab */}
        <TabsContent value="canvas" className="flex-1 flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <h3 className="text-lg font-semibold">Code Task Canvas</h3>
            {selectedNodeId && (
              <button
                onClick={deleteSelectedNode}
                className="px-3 py-1 text-sm bg-red-100 text-red-700 rounded hover:bg-red-200"
              >
                Delete Node
              </button>
            )}
          </div>

          <div className="flex gap-4 flex-1 min-h-0">
            {/* Palette */}
            <div className="w-48 bg-white p-4 rounded-lg border border-gray-200 overflow-y-auto">
              <h4 className="font-semibold text-gray-900 mb-4">Components</h4>
              <div
                onDragStart={handlePaletteDragStart}
                onDragEnd={handlePaletteDragEnd}
              >
                <CodeTaskPaletteItem
                  onDragStart={() => {
                    // Palette drag handling
                  }}
                />
              </div>
              <p className="text-xs text-gray-500 mt-4 p-2 bg-gray-50 rounded">
                Drag the Code Task component onto the canvas to create a new task.
              </p>
            </div>

            {/* Canvas */}
            <div className="flex-1 bg-white rounded-lg border border-gray-200 overflow-auto">
              <svg
                ref={canvasRef}
                width="100%"
                height="100%"
                className="bg-gray-50"
                onDragOver={handleCanvasDragOver}
                onDrop={handleCanvasDrop}
                style={{ minHeight: '500px' }}
              >
                {nodes.map(node => (
                  <CodeTaskNode
                    key={node.id}
                    {...node}
                    label="Code Task"
                    isSelected={selectedNodeId === node.id}
                    onClick={() => setSelectedNodeId(node.id)}
                    onDoubleClick={() => {
                      // Open properties tab
                      const propertiesTab = document.querySelector(
                        '[data-state=inactive]'
                      ) as HTMLElement;
                      propertiesTab?.click?.();
                    }}
                  />
                ))}
              </svg>
            </div>
          </div>
        </TabsContent>

        {/* Properties Tab */}
        <TabsContent value="properties" className="flex-1 overflow-y-auto">
          {selectedNode ? (
            <>
              {validationErrors.length > 0 && (
                <div className="mb-4 p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
                  <div className="flex gap-3">
                    <AlertCircle className="w-5 h-5 text-yellow-600 flex-shrink-0 mt-0.5" />
                    <div>
                      <h4 className="font-semibold text-yellow-900 mb-1">Validation Issues</h4>
                      <ul className="text-sm text-yellow-800 list-disc list-inside">
                        {validationErrors.map((error, i) => (
                          <li key={i}>{error}</li>
                        ))}
                      </ul>
                    </div>
                  </div>
                </div>
              )}

              <CodeTaskPropertyPanel
                nodeId={selectedNode.id}
                properties={selectedNode}
                onPropertiesChange={handleNodePropertiesChange}
                processVariables={processVariables}
                availableJars={uploadedJars}
              />
            </>
          ) : (
            <div className="text-center py-12 text-gray-500">
              <p>Select a Code Task node to configure its properties</p>
            </div>
          )}
        </TabsContent>

        {/* Upload Tab */}
        <TabsContent value="upload" className="flex-1 overflow-y-auto">
          <CodeTaskJarUploadPanel
            onJarSelected={(jarId, className, methodName) => {
              if (!selectedNode) return;
              handleNodePropertiesChange({
                jarId,
                className,
                methodName
              });
            }}
            onJarUpload={(response) => {
              setUploadedJars([...uploadedJars, response]);
            }}
          />
        </TabsContent>
      </Tabs>
    </div>
  );
};

export default CodeTaskModeler;
