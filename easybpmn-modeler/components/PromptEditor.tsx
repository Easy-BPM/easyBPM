import React, { useState, useRef, useEffect } from 'react';
import { ChevronDown, HelpCircle } from 'lucide-react';
import { ProcessVariable } from '../types';

interface PromptEditorProps {
  userPrompt?: string;
  systemPrompt?: string;
  promptTemplate?: string;
  processVariables: ProcessVariable[];
  onUserPromptChange: (prompt: string) => void;
  onSystemPromptChange: (prompt: string) => void;
  onPromptTemplateChange: (template: string) => void;
}

/**
 * Prompt Editor with Variable Injection (BETA)
 * Supports {{variableName}} placeholders for workflow variable injection.
 */
export const PromptEditor: React.FC<PromptEditorProps> = ({
  userPrompt = '',
  systemPrompt = '',
  promptTemplate = '',
  processVariables,
  onUserPromptChange,
  onSystemPromptChange,
  onPromptTemplateChange,
}) => {
  const [showSystemPrompt, setShowSystemPrompt] = useState(!!systemPrompt);
  const [showAutocomplete, setShowAutocomplete] = useState(false);
  const [autocompletePosition, setAutocompletePosition] = useState<{ top: number; left: number } | null>(null);
  const promptInputRef = useRef<HTMLTextAreaElement>(null);
  const [cursorPosition, setCursorPosition] = useState(0);

  const handlePromptChange = (newPrompt: string) => {
    onPromptTemplateChange(newPrompt);
    setCursorPosition(promptInputRef.current?.selectionStart || 0);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === '{' && e.ctrlKey) {
      e.preventDefault();
      const input = promptInputRef.current;
      if (input) {
        const start = input.selectionStart;
        const text = input.value;
        const newText = text.slice(0, start) + '{{' + text.slice(start);
        onPromptTemplateChange(newText);
        setTimeout(() => {
          input.selectionStart = input.selectionEnd = start + 2;
        }, 0);
      }
    }

    // Show autocomplete on {{ trigger
    if (e.key === '{') {
      const input = e.currentTarget;
      const text = input.value;
      const pos = input.selectionStart;
      if (pos >= 2 && text[pos - 2] === '{' && text[pos - 1] === '{') {
        setShowAutocomplete(true);
        const rect = input.getBoundingClientRect();
        setAutocompletePosition({
          top: rect.top + 20,
          left: rect.left,
        });
      }
    }

    if (e.key === 'Escape') {
      setShowAutocomplete(false);
    }
  };

  const insertVariable = (varName: string) => {
    const input = promptInputRef.current;
    if (input) {
      const text = input.value;
      const pos = input.selectionStart;
      
      // Find the last {{ position
      let startPos = pos - 1;
      while (startPos >= 0 && text[startPos] !== '{') {
        startPos--;
      }
      startPos = Math.max(0, startPos - 1);

      const beforeText = text.slice(0, startPos + 1);
      const afterText = text.slice(pos);
      const newText = beforeText + '{{' + varName + '}}' + afterText;
      
      onPromptTemplateChange(newText);
      setShowAutocomplete(false);
      
      setTimeout(() => {
        input.selectionStart = input.selectionEnd = beforeText.length + varName.length + 4;
        input.focus();
      }, 0);
    }
  };

  const renderPreview = (template: string): string => {
    let preview = template;
    processVariables.forEach(v => {
      const placeholder = `{{${v.name}}}`;
      const replacement = `$${v.name}`;
      preview = preview.replace(new RegExp(placeholder, 'g'), `[${replacement}]`);
    });
    return preview;
  };

  return (
    <div className="bg-gradient-to-br from-pink-50 to-purple-50 border border-pink-200 rounded-lg p-4 space-y-3">
      <div className="flex items-center justify-between mb-2">
        <h3 className="font-semibold text-gray-800 text-sm flex items-center gap-2">
          <span>Prompt Template</span>
          <span className="px-1.5 py-0.5 bg-pink-600 text-white text-xs font-bold rounded">BETA</span>
        </h3>
        <button
          onClick={() => setShowSystemPrompt(!showSystemPrompt)}
          className="text-xs text-pink-600 hover:text-pink-700 font-semibold flex items-center gap-1"
        >
          {showSystemPrompt ? '▼' : '▶'} System Prompt
        </button>
      </div>

      {/* System Prompt (Optional) */}
      {showSystemPrompt && (
        <div>
          <label className="text-xs font-semibold text-gray-700 mb-1 block">System Prompt (AI Role)</label>
          <textarea
            value={systemPrompt}
            onChange={e => onSystemPromptChange(e.target.value)}
            placeholder="You are a helpful assistant specialized in..."
            className="w-full px-3 py-2 border border-pink-300 rounded-lg bg-white text-gray-800 text-xs font-mono focus:outline-none focus:ring-2 focus:ring-pink-500 resize-none"
            rows={2}
          />
          <p className="text-xs text-gray-600 mt-1">Define the AI's role and behavior</p>
        </div>
      )}

      {/* User Prompt / Template */}
      <div>
        <label className="text-xs font-semibold text-gray-700 mb-1 block">
          Message Template
          <span className="text-pink-600 ml-1">*</span>
        </label>
        <div className="relative">
          <textarea
            ref={promptInputRef}
            value={promptTemplate}
            onChange={e => handlePromptChange(e.target.value)}
            onKeyDown={handleKeyDown}
            onInput={e => setCursorPosition((e.target as HTMLTextAreaElement).selectionStart)}
            placeholder="Enter your prompt here. Use {{variableName}} to inject process variables."
            className="w-full px-3 py-2 border border-pink-300 rounded-lg bg-white text-gray-800 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-pink-500 resize-none"
            rows={4}
          />

          {/* Autocomplete Dropdown */}
          {showAutocomplete && processVariables.length > 0 && (
            <div className="absolute top-0 left-0 mt-1 bg-white border border-gray-300 rounded-lg shadow-lg z-50 max-h-48 overflow-y-auto">
              <div className="text-xs font-semibold text-gray-700 px-3 py-2 bg-gray-100 border-b">Variables</div>
              {processVariables.map(v => (
                <button
                  key={v.id}
                  onClick={() => insertVariable(v.name)}
                  className="w-full text-left px-3 py-2 text-xs text-gray-800 hover:bg-blue-100 transition-colors block"
                >
                  <span className="font-mono font-semibold text-pink-600">{{v.name}}</span>
                  <span className="text-gray-600 ml-2">({v.type})</span>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Variable Reference Guide */}
        <div className="mt-2 p-2 bg-blue-50 border border-blue-300 rounded text-blue-700">
          <p className="text-xs font-semibold mb-1">📌 Variable Injection</p>
          <p className="text-xs">Use <code className="bg-white px-1 rounded font-mono">{{'{'}variableName{'}'}}</code> syntax to inject values:</p>
          <div className="mt-1 space-y-1">
            {processVariables.slice(0, 3).map(v => (
              <div key={v.id} className="text-xs font-mono text-blue-600">
                • {`{{${v.name}}`} → Process variable
              </div>
            ))}
            {processVariables.length > 3 && (
              <div className="text-xs text-blue-600">+ {processVariables.length - 3} more variables</div>
            )}
          </div>
        </div>
      </div>

      {/* Preview */}
      {promptTemplate && (
        <div>
          <label className="text-xs font-semibold text-gray-700 mb-1 block">Preview (with sample values)</label>
          <div className="p-2 bg-gray-100 border border-gray-300 rounded text-xs text-gray-800 font-mono whitespace-pre-wrap break-words max-h-32 overflow-y-auto">
            {renderPreview(promptTemplate)}
          </div>
        </div>
      )}

      {/* Tips */}
      <div className="p-2 bg-yellow-50 border border-yellow-300 rounded text-yellow-700 text-xs">
        <p className="font-semibold">💡 Tips:</p>
        <ul className="mt-1 space-y-0.5 list-disc list-inside">
          <li>Use multi-line prompts for complex instructions</li>
          <li>Variables are replaced at runtime with actual values</li>
          <li>Ctrl+{ {'}{'} opens variable autocomplete</li>
        </ul>
      </div>
    </div>
  );
};
