import React, { useState, useEffect } from 'react';
import { AlertCircle, ChevronDown } from 'lucide-react';

interface AIProviderConfigFormProps {
  providerId?: string;
  modelName?: string;
  credentialId?: string;
  credentialRefName?: string;
  endpoint?: string;
  onProviderChange: (providerId: string) => void;
  onModelChange: (modelName: string) => void;
  onCredentialChange: (credentialId: string) => void;
  onEndpointChange: (endpoint: string) => void;
  availableCredentials: Array<{ id: string; providerId: string; maskedToken: string }>;
}

/**
 * AI Provider Configuration Form (BETA)
 * Allows users to configure which AI provider and model to use for Ask AI task.
 */
export const AIProviderConfigForm: React.FC<AIProviderConfigFormProps> = ({
  providerId = 'openai',
  modelName = 'gpt-3.5-turbo',
  credentialId = '',
  credentialRefName = '',
  endpoint = '',
  onProviderChange,
  onModelChange,
  onCredentialChange,
  onEndpointChange,
  availableCredentials,
}) => {
  const [validationError, setValidationError] = useState<string | null>(null);

  // Provider metadata (statically defined for now)
  const providers: Record<string, { label: string; models: string[]; defaultModel: string; supportsCustomEndpoint: boolean }> = {
    openai: {
      label: 'OpenAI (GPT)',
      models: ['gpt-4', 'gpt-4-turbo-preview', 'gpt-3.5-turbo', 'gpt-3.5-turbo-16k'],
      defaultModel: 'gpt-3.5-turbo',
      supportsCustomEndpoint: true,
    },
    anthropic: {
      label: 'Anthropic (Claude)',
      models: ['claude-3-opus', 'claude-3-sonnet', 'claude-3-haiku'],
      defaultModel: 'claude-3-sonnet',
      supportsCustomEndpoint: false,
    },
    gemini: {
      label: 'Google Gemini',
      models: ['gemini-3.5-flash', 'gemini-3-flash', 'gemini-3.1-flash-lite', 'gemini-2.5-flash', 'gemini-2.5-flash-lite', 'gemini-flash-latest'],
      defaultModel: 'gemini-3.5-flash',
      supportsCustomEndpoint: false,
    },
    ollama: {
      label: 'Ollama (Local)',
      models: ['llama3.2', 'llama3.1', 'mistral', 'qwen2.5', 'phi3'],
      defaultModel: 'llama3.2',
      supportsCustomEndpoint: true,
    },
    'azure-openai': {
      label: 'Azure OpenAI',
      models: ['gpt-4', 'gpt-35-turbo'],
      defaultModel: 'gpt-35-turbo',
      supportsCustomEndpoint: true,
    },
  };

  const currentProvider = providers[providerId] || providers.openai;
  const credentialsForProvider = availableCredentials.filter(c => c.providerId === providerId);

  const handleProviderChange = (newProviderId: string) => {
    onProviderChange(newProviderId);
    const newProvider = providers[newProviderId] || providers.openai;
    onModelChange(newProvider.defaultModel);
    setValidationError(null);
  };

  const handleCredentialChange = (newCredentialId: string) => {
    onCredentialChange(newCredentialId);
    if (newCredentialId.startsWith('$')) {
      // Environment variable reference
      onCredentialChange('');
      onEndpointChange(''); // Clear endpoint if using env var
    }
  };

  return (
    <div className="bg-gradient-to-br from-pink-50 to-purple-50 border border-pink-200 rounded-lg p-4 space-y-4">
      {/* BETA Badge */}
      <div className="flex items-center gap-2 mb-2">
        <span className="px-2 py-1 bg-pink-600 text-white text-xs font-bold rounded-full">BETA</span>
        <p className="text-xs text-pink-700 font-semibold">AI Task (Experimental)</p>
      </div>

      {validationError && (
        <div className="flex items-center gap-2 p-2 bg-red-100 border border-red-300 rounded text-red-700 text-sm">
          <AlertCircle size={16} />
          {validationError}
        </div>
      )}

      {/* Provider Selection */}
      <div>
        <label className="block text-sm font-semibold text-gray-700 mb-1.5">AI Provider</label>
        <select
          value={providerId}
          onChange={e => handleProviderChange(e.target.value)}
          className="w-full px-3 py-2 border border-pink-300 rounded-lg bg-white text-gray-800 text-sm focus:outline-none focus:ring-2 focus:ring-pink-500"
        >
          {Object.entries(providers).map(([key, provider]) => (
            <option key={key} value={key}>{provider.label}</option>
          ))}
        </select>
        <p className="text-xs text-gray-600 mt-1">Select the AI provider to use</p>
      </div>

      {/* Model Selection */}
      <div>
        <label className="block text-sm font-semibold text-gray-700 mb-1.5">Model / Engine</label>
        <select
          value={modelName}
          onChange={e => onModelChange(e.target.value)}
          className="w-full px-3 py-2 border border-pink-300 rounded-lg bg-white text-gray-800 text-sm focus:outline-none focus:ring-2 focus:ring-pink-500"
        >
          {currentProvider.models.map(model => (
            <option key={model} value={model}>{model}</option>
          ))}
        </select>
        <p className="text-xs text-gray-600 mt-1">Choose the model variant for this provider</p>
      </div>

      {/* Credential Selection */}
      <div>
        <label className="block text-sm font-semibold text-gray-700 mb-1.5">API Credential</label>
        {credentialsForProvider.length > 0 ? (
          <select
            value={credentialId}
            onChange={e => handleCredentialChange(e.target.value)}
            className="w-full px-3 py-2 border border-pink-300 rounded-lg bg-white text-gray-800 text-sm focus:outline-none focus:ring-2 focus:ring-pink-500"
          >
            <option value="">-- Select credential --</option>
            {credentialsForProvider.map(cred => (
              <option key={cred.id} value={cred.id}>{cred.maskedToken}</option>
            ))}
          </select>
        ) : (
          <div className="p-3 bg-yellow-50 border border-yellow-300 rounded text-yellow-700 text-sm">
            <p className="font-semibold">No credentials stored for {currentProvider.label}</p>
            <p className="text-xs mt-1">You can use environment variables with $ENV_VAR syntax in the prompt editor.</p>
          </div>
        )}
        <p className="text-xs text-gray-600 mt-1">API key or token for authentication</p>
      </div>

      {/* Custom Endpoint (for OpenAI/Azure/Ollama) */}
      {currentProvider.supportsCustomEndpoint && (
        <div>
          <label className="block text-sm font-semibold text-gray-700 mb-1.5">Custom Endpoint (Optional)</label>
          <input
            type="text"
            value={endpoint}
            onChange={e => onEndpointChange(e.target.value)}
            placeholder={providerId === 'ollama' ? 'http://localhost:11434' : 'https://api.openai.com/v1/chat/completions'}
            className="w-full px-3 py-2 border border-pink-300 rounded-lg bg-white text-gray-800 text-sm focus:outline-none focus:ring-2 focus:ring-pink-500"
          />
          <p className="text-xs text-gray-600 mt-1">{providerId === 'ollama' ? 'Leave blank to use the local default http://localhost:11434.' : 'For Azure or proxy endpoints. Leave blank for default.'}</p>
        </div>
      )}

      {/* Info */}
      <div className="p-3 bg-blue-50 border border-blue-300 rounded text-blue-700 text-sm">
        <p className="font-semibold text-xs mb-1">⚠️ Experimental Feature</p>
        <ul className="text-xs space-y-1">
          <li>• This feature is in BETA and may change</li>
          <li>• Token consumption will be charged to your account</li>
          <li>• Responses are stored in process variables</li>
        </ul>
      </div>
    </div>
  );
};
