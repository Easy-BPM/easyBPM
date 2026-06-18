import React, { useState, useEffect } from 'react';
import { ArrowRight, Plus, Trash2, Code2 } from 'lucide-react';

/**
 * CodeTaskPropertyPanel
 * 
 * Component for editing Code Task properties in the modeler
 * - Select JAR, class, and method
 * - Configure input variable mappings
 * - Configure output variable mappings
 * - Display method signature and parameters
 */

interface VariableMapping {
  processVar: string;
  methodParam: string;
}

interface OutputMapping {
  methodReturn: string;
  processVar: string;
}

interface CodeTaskPropertyPanelProps {
  nodeId: string;
  properties: {
    jarId?: number;
    className?: string;
    methodName?: string;
    inputMappings?: Record<string, string>;
    outputMappings?: Record<string, string>;
  };
  onPropertiesChange: (properties: any) => void;
  processVariables: Array<{ name: string; type?: string }>;
  availableJars: Array<{ jarId: number; fileName: string; classCount: number }>;
}

export const CodeTaskPropertyPanel: React.FC<CodeTaskPropertyPanelProps> = ({
  nodeId,
  properties,
  onPropertiesChange,
  processVariables,
  availableJars
}) => {
  const [selectedJar, setSelectedJar] = useState<number | undefined>(properties.jarId);
  const [selectedClass, setSelectedClass] = useState<string | undefined>(properties.className);
  const [selectedMethod, setSelectedMethod] = useState<string | undefined>(properties.methodName);
  const [classes, setClasses] = useState<string[]>([]);
  const [methods, setMethods] = useState<any[]>([]);
  const [loadingClasses, setLoadingClasses] = useState(false);
  const [loadingMethods, setLoadingMethods] = useState(false);

  const [inputMappings, setInputMappings] = useState<VariableMapping[]>([
    ...(Object.entries(properties.inputMappings || {}).map(([k, v]) => ({
      processVar: k,
      methodParam: v
    })) || [])
  ]);

  const [outputMappings, setOutputMappings] = useState<OutputMapping[]>([
    ...(Object.entries(properties.outputMappings || {}).map(([k, v]) => ({
      methodReturn: k,
      processVar: v
    })) || [])
  ]);

  // Load classes when JAR changes
  useEffect(() => {
    if (selectedJar) {
      setLoadingClasses(true);
      fetch(`/code-tasks/jar/${selectedJar}/classes`)
        .then(res => res.json())
        .then(data => {
          setClasses(data.classes);
          setSelectedClass(undefined);
          setMethods([]);
        })
        .catch(err => console.error('Error loading classes:', err))
        .finally(() => setLoadingClasses(false));
    }
  }, [selectedJar]);

  // Load methods when class changes
  useEffect(() => {
    if (selectedJar && selectedClass) {
      setLoadingMethods(true);
      fetch(`/code-tasks/jar/${selectedJar}/classes/${encodeURIComponent(selectedClass)}/methods`)
        .then(res => res.json())
        .then(data => {
          setMethods(data.methods);
          setSelectedMethod(undefined);
        })
        .catch(err => console.error('Error loading methods:', err))
        .finally(() => setLoadingMethods(false));
    }
  }, [selectedJar, selectedClass]);

  const handleJarChange = (jarId: number) => {
    setSelectedJar(jarId);
    onPropertiesChange({
      ...properties,
      jarId,
      className: undefined,
      methodName: undefined
    });
  };

  const handleClassChange = (className: string) => {
    setSelectedClass(className);
    onPropertiesChange({
      ...properties,
      className,
      methodName: undefined
    });
  };

  const handleMethodChange = (methodName: string) => {
    setSelectedMethod(methodName);
    onPropertiesChange({
      ...properties,
      methodName
    });
  };

  const updateInputMapping = (index: number, mapping: VariableMapping) => {
    const newMappings = [...inputMappings];
    newMappings[index] = mapping;
    setInputMappings(newMappings);

    const mappingsObject = Object.fromEntries(
      newMappings.map(m => [m.processVar, m.methodParam])
    );
    onPropertiesChange({
      ...properties,
      inputMappings: mappingsObject
    });
  };

  const removeInputMapping = (index: number) => {
    const newMappings = inputMappings.filter((_, i) => i !== index);
    setInputMappings(newMappings);

    const mappingsObject = Object.fromEntries(
      newMappings.map(m => [m.processVar, m.methodParam])
    );
    onPropertiesChange({
      ...properties,
      inputMappings: mappingsObject
    });
  };

  const addInputMapping = () => {
    setInputMappings([...inputMappings, { processVar: '', methodParam: '' }]);
  };

  const updateOutputMapping = (index: number, mapping: OutputMapping) => {
    const newMappings = [...outputMappings];
    newMappings[index] = mapping;
    setOutputMappings(newMappings);

    const mappingsObject = Object.fromEntries(
      newMappings.map(m => [m.methodReturn, m.processVar])
    );
    onPropertiesChange({
      ...properties,
      outputMappings: mappingsObject
    });
  };

  const removeOutputMapping = (index: number) => {
    const newMappings = outputMappings.filter((_, i) => i !== index);
    setOutputMappings(newMappings);

    const mappingsObject = Object.fromEntries(
      newMappings.map(m => [m.methodReturn, m.processVar])
    );
    onPropertiesChange({
      ...properties,
      outputMappings: mappingsObject
    });
  };

  const addOutputMapping = () => {
    setOutputMappings([...outputMappings, { methodReturn: '', processVar: '' }]);
  };

  const selectedMethodDetails = methods.find(m => m.methodName === selectedMethod);

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-6 space-y-6">
      <h3 className="text-lg font-semibold flex items-center gap-2">
        <Code2 className="w-5 h-5 text-blue-600" />
        Code Task Configuration
      </h3>

      {/* JAR Selection */}
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-2">JAR File</label>
        <select
          value={selectedJar || ''}
          onChange={(e) => handleJarChange(Number(e.target.value))}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">Select a JAR file</option>
          {availableJars.map(jar => (
            <option key={jar.jarId} value={jar.jarId}>
              {jar.fileName} ({jar.classCount} classes)
            </option>
          ))}
        </select>
      </div>

      {/* Class Selection */}
      {selectedJar && (
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">Class</label>
          <select
            value={selectedClass || ''}
            onChange={(e) => handleClassChange(e.target.value)}
            disabled={loadingClasses}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
          >
            <option value="">{loadingClasses ? 'Loading classes...' : 'Select a class'}</option>
            {classes.map(className => (
              <option key={className} value={className}>
                {className}
              </option>
            ))}
          </select>
        </div>
      )}

      {/* Method Selection */}
      {selectedClass && (
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">Method</label>
          <select
            value={selectedMethod || ''}
            onChange={(e) => handleMethodChange(e.target.value)}
            disabled={loadingMethods}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
          >
            <option value="">{loadingMethods ? 'Loading methods...' : 'Select a method'}</option>
            {methods.map(method => (
              <option key={method.methodName} value={method.methodName}>
                {method.methodName} - {method.signature}
              </option>
            ))}
          </select>
        </div>
      )}

      {/* Method Signature */}
      {selectedMethodDetails && (
        <div className="bg-gray-50 p-4 rounded-lg">
          <p className="text-sm font-mono text-gray-700 break-all">
            {selectedMethodDetails.signature}
          </p>
          <p className="text-xs text-gray-500 mt-2">
            Returns: <code>{selectedMethodDetails.returnType}</code>
          </p>
        </div>
      )}

      {/* Input Variable Mappings */}
      <div className="border-t pt-6">
        <div className="flex items-center justify-between mb-4">
          <h4 className="font-semibold text-gray-900">Input Mappings</h4>
          <button
            onClick={addInputMapping}
            className="flex items-center gap-1 px-3 py-1 text-sm bg-blue-100 text-blue-700 rounded hover:bg-blue-200"
          >
            <Plus className="w-4 h-4" />
            Add
          </button>
        </div>

        <div className="space-y-3">
          {inputMappings.map((mapping, index) => (
            <div key={index} className="flex items-center gap-2">
              <select
                value={mapping.processVar}
                onChange={(e) => updateInputMapping(index, { ...mapping, processVar: e.target.value })}
                className="flex-1 px-3 py-2 border border-gray-300 rounded text-sm"
                placeholder="Process variable"
              >
                <option value="">Process variable</option>
                {processVariables.map(v => (
                  <option key={v.name} value={v.name}>{v.name}</option>
                ))}
              </select>
              <ArrowRight className="w-4 h-4 text-gray-400" />
              <input
                type="text"
                value={mapping.methodParam}
                onChange={(e) => updateInputMapping(index, { ...mapping, methodParam: e.target.value })}
                placeholder="param0, param1, ..."
                className="flex-1 px-3 py-2 border border-gray-300 rounded text-sm"
              />
              <button
                onClick={() => removeInputMapping(index)}
                className="p-1 text-red-600 hover:bg-red-50 rounded"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          ))}
        </div>
      </div>

      {/* Output Variable Mappings */}
      <div className="border-t pt-6">
        <div className="flex items-center justify-between mb-4">
          <h4 className="font-semibold text-gray-900">Output Mappings</h4>
          <button
            onClick={addOutputMapping}
            className="flex items-center gap-1 px-3 py-1 text-sm bg-blue-100 text-blue-700 rounded hover:bg-blue-200"
          >
            <Plus className="w-4 h-4" />
            Add
          </button>
        </div>

        <div className="space-y-3">
          {outputMappings.map((mapping, index) => (
            <div key={index} className="flex items-center gap-2">
              <input
                type="text"
                value={mapping.methodReturn}
                onChange={(e) => updateOutputMapping(index, { ...mapping, methodReturn: e.target.value })}
                placeholder="return.field or total"
                className="flex-1 px-3 py-2 border border-gray-300 rounded text-sm"
              />
              <ArrowRight className="w-4 h-4 text-gray-400" />
              <select
                value={mapping.processVar}
                onChange={(e) => updateOutputMapping(index, { ...mapping, processVar: e.target.value })}
                className="flex-1 px-3 py-2 border border-gray-300 rounded text-sm"
              >
                <option value="">Process variable</option>
                {processVariables.map(v => (
                  <option key={v.name} value={v.name}>{v.name}</option>
                ))}
              </select>
              <button
                onClick={() => removeOutputMapping(index)}
                className="p-1 text-red-600 hover:bg-red-50 rounded"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default CodeTaskPropertyPanel;
