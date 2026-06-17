import React, { useState } from 'react';
import { Plus, Upload, Trash2, Code2 } from 'lucide-react';

/**
 * CodeTaskJarUploadPanel
 * 
 * Component for uploading JAR files and managing uploaded JARs
 * - Display uploaded JAR files
 * - Upload new JAR files
 * - View discovered classes
 * - Delete JAR files
 */
interface UploadedJar {
  jarId: number;
  fileName: string;
  fileHash: string;
  uploadedAt: string;
  classCount: number;
  methodCount: number;
  classes: string[];
}

interface CodeTaskJarUploadPanelProps {
  onJarSelected: (jarId: number, className: string, methodName: string) => void;
  onJarUpload?: (response: any) => void;
}

export const CodeTaskJarUploadPanel: React.FC<CodeTaskJarUploadPanelProps> = ({
  onJarSelected,
  onJarUpload
}) => {
  const [uploadedJars, setUploadedJars] = useState<UploadedJar[]>([]);
  const [uploading, setUploading] = useState(false);
  const [expandedJars, setExpandedJars] = useState<Set<number>>(new Set());
  const [expandedClasses, setExpandedClasses] = useState<Set<string>>(new Set());

  const handleFileUpload = async (file: File) => {
    setUploading(true);
    try {
      const formData = new FormData();
      formData.append('jarFile', file);
      formData.append('description', `Uploaded ${new Date().toISOString()}`);

      const response = await fetch('/code-tasks/upload', {
        method: 'POST',
        body: formData
      });

      if (!response.ok) {
        throw new Error('Failed to upload JAR');
      }

      const data: UploadedJar = await response.json();
      setUploadedJars([...uploadedJars, data]);
      onJarUpload?.(data);
    } catch (error) {
      console.error('Error uploading JAR:', error);
      alert('Failed to upload JAR file');
    } finally {
      setUploading(false);
    }
  };

  const toggleJarExpanded = (jarId: number) => {
    const newSet = new Set(expandedJars);
    if (newSet.has(jarId)) {
      newSet.delete(jarId);
    } else {
      newSet.add(jarId);
    }
    setExpandedJars(newSet);
  };

  const toggleClassExpanded = (classKey: string) => {
    const newSet = new Set(expandedClasses);
    if (newSet.has(classKey)) {
      newSet.delete(classKey);
    } else {
      newSet.add(classKey);
    }
    setExpandedClasses(newSet);
  };

  const deleteJar = (jarId: number) => {
    setUploadedJars(uploadedJars.filter(jar => jar.jarId !== jarId));
  };

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-6">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-2">
          <Code2 className="w-6 h-6 text-blue-600" />
          <h3 className="text-lg font-semibold">Code Task JAR Files</h3>
        </div>
        <label className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 cursor-pointer disabled:opacity-50">
          <Upload className="w-4 h-4" />
          <span>{uploading ? 'Uploading...' : 'Upload JAR'}</span>
          <input
            type="file"
            accept=".jar"
            onChange={(e) => {
              if (e.target.files?.[0]) {
                handleFileUpload(e.target.files[0]);
              }
            }}
            disabled={uploading}
            className="hidden"
          />
        </label>
      </div>

      {uploadedJars.length === 0 ? (
        <p className="text-gray-500 text-center py-8">No JAR files uploaded yet</p>
      ) : (
        <div className="space-y-4">
          {uploadedJars.map((jar) => (
            <div key={jar.jarId} className="border border-gray-200 rounded-lg">
              <div className="p-4 bg-gray-50 flex items-center justify-between cursor-pointer hover:bg-gray-100"
                   onClick={() => toggleJarExpanded(jar.jarId)}>
                <div>
                  <h4 className="font-semibold text-gray-900">{jar.fileName}</h4>
                  <p className="text-sm text-gray-500">
                    {jar.classCount} classes • {jar.methodCount} methods
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      deleteJar(jar.jarId);
                    }}
                    className="p-2 hover:bg-red-100 rounded text-red-600"
                    title="Delete JAR"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>

              {expandedJars.has(jar.jarId) && (
                <div className="p-4 border-t border-gray-200">
                  <div className="space-y-2">
                    {jar.classes.map((className) => (
                      <div key={className} className="border border-gray-200 rounded bg-white">
                        <div
                          className="p-3 flex items-center justify-between cursor-pointer hover:bg-gray-50"
                          onClick={() => toggleClassExpanded(`${jar.jarId}-${className}`)}
                        >
                          <code className="text-sm font-mono text-gray-900">{className}</code>
                          <span className="text-gray-400">▶</span>
                        </div>

                        {expandedClasses.has(`${jar.jarId}-${className}`) && (
                          <div className="p-3 border-t border-gray-200 bg-gray-50">
                            <p className="text-sm text-gray-600 mb-2">Methods:</p>
                            <div className="space-y-1">
                              <p className="text-xs text-gray-500">(Method list will load from API)</p>
                            </div>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default CodeTaskJarUploadPanel;
