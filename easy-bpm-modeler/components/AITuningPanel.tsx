import React, { useState } from 'react';

interface AITuningParams {
  temperature?: number;
  topP?: number;
  maxTokens?: number;
  frequencyPenalty?: number;
  presencePenalty?: number;
  retryCount?: number;
  backoffMultiplier?: number;
  initialDelayMs?: number;
}

interface AITuningPanelProps {
  tuningParams?: AITuningParams;
  onTuningParamsChange: (params: AITuningParams) => void;
}

type TuningParameterKey = keyof AITuningParams;

const defaultTuningParams: Required<AITuningParams> = {
  temperature: 0.7,
  topP: 1.0,
  maxTokens: 2000,
  frequencyPenalty: 0,
  presencePenalty: 0,
  retryCount: 0,
  backoffMultiplier: 2.0,
  initialDelayMs: 1000,
};

const quickPresets = {
  Accurate: { temperature: 0.2, topP: 0.9, maxTokens: 500 },
  Balanced: { temperature: 0.7, topP: 1.0, maxTokens: 2000 },
  Creative: { temperature: 1.5, topP: 0.95, maxTokens: 3000 },
} as const;

/**
 * AI Tuning Parameters Configuration Panel (BETA)
 * Allows fine-tuning of AI model behavior (temperature, penalties, tokens, etc.)
 */
export const AITuningPanel: React.FC<AITuningPanelProps> = ({
  tuningParams,
  onTuningParamsChange,
}) => {
  const [showAdvanced, setShowAdvanced] = useState(false);
  const params = { ...defaultTuningParams, ...tuningParams };

  const applyQuickPreset = (preset: keyof typeof quickPresets) => {
    onTuningParamsChange({
      ...params,
      ...quickPresets[preset],
    });
  };

  const isQuickPresetActive = (preset: keyof typeof quickPresets) => {
    const values = quickPresets[preset];
    return Object.entries(values).every(([parameter, value]) => (
      params[parameter as keyof typeof values] === value
    ));
  };

  const updateParam = (parameter: TuningParameterKey, value: number) => {
    onTuningParamsChange({
      ...params,
      [parameter]: value,
    });
  };

  const ParameterSlider: React.FC<{
    label: string;
    parameter: TuningParameterKey;
    min: number;
    max: number;
    step: number;
    value: number;
    description: string;
  }> = ({ label, parameter, min, max, step, value, description }) => {
    const percentage = Math.min(100, Math.max(0, ((value - min) / (max - min)) * 100));

    return <div className="space-y-1.5">
      <div className="flex items-center justify-between">
        <label className="text-xs font-semibold text-gray-700">{label}</label>
        <input
          type="number"
          min={min}
          max={max}
          step={step}
          value={value}
          onChange={event => {
            const nextValue = Number(event.target.value);
            if (Number.isFinite(nextValue)) updateParam(parameter, nextValue);
          }}
          className="w-20 rounded border border-pink-300 bg-white px-2 py-0.5 text-right text-xs font-mono font-semibold text-pink-700 outline-none focus:border-pink-500 focus:ring-2 focus:ring-pink-100"
          aria-label={`${label} value`}
        />
      </div>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={event => updateParam(parameter, Number(event.target.value))}
        className="ai-tuning-slider w-full cursor-pointer"
        style={{ background: `linear-gradient(to right, #db2777 0%, #db2777 ${percentage}%, #fbcfe8 ${percentage}%, #fbcfe8 100%)` }}
      />
      <p className="text-xs text-gray-600">{description}</p>
    </div>;
  };

  const ParameterInput: React.FC<{
    label: string;
    parameter: TuningParameterKey;
    min?: number;
    max?: number;
    value: number | string;
    description: string;
  }> = ({ label, parameter, min, max, value, description }) => (
    <div className="space-y-1">
      <label className="text-xs font-semibold text-gray-700">{label}</label>
      <input
        type="number"
        min={min}
        max={max}
        value={value}
        onChange={event => {
          const nextValue = Number(event.target.value);
          if (Number.isFinite(nextValue)) updateParam(parameter, nextValue);
        }}
        className="w-full px-2 py-1.5 border border-pink-300 rounded-lg bg-white text-gray-800 text-xs focus:outline-none focus:ring-2 focus:ring-pink-500"
      />
      <p className="text-xs text-gray-600">{description}</p>
    </div>
  );

  return (
    <div className="bg-gradient-to-br from-pink-50 to-purple-50 border border-pink-200 rounded-lg p-4 space-y-4">
      <div className="flex items-center justify-between mb-2">
        <h3 className="font-semibold text-gray-800 text-sm flex items-center gap-2">
          <span>Tuning Parameters</span>
          <span className="px-1.5 py-0.5 bg-pink-600 text-white text-xs font-bold rounded">BETA</span>
        </h3>
        <div className="text-xs text-gray-600">Control AI behavior and creativity</div>
      </div>

      <div className="space-y-4">
        {/* Core Parameters */}
        <div className="bg-white border border-pink-200 rounded-lg p-3 space-y-3">
          <h4 className="text-xs font-semibold text-gray-700">Core Parameters</h4>

          <ParameterSlider
            label="Temperature"
            parameter="temperature"
            min={0}
            max={2}
            step={0.1}
            value={params.temperature}
            description="Controls randomness (0=deterministic, 2=very random). Default: 0.7"
          />

          <ParameterSlider
            label="Top P"
            parameter="topP"
            min={0}
            max={1}
            step={0.05}
            value={params.topP}
            description="Nucleus sampling (0-1). Default: 1.0 (disabled)"
          />

          <ParameterInput
            label="Max Tokens"
            parameter="maxTokens"
            min={1}
            max={4000}
            value={params.maxTokens}
            description="Maximum output length in tokens. Default: 2000"
          />
        </div>

        {/* Penalties */}
        <div className="bg-white border border-pink-200 rounded-lg p-3 space-y-3">
          <h4 className="text-xs font-semibold text-gray-700">Penalties</h4>

          <ParameterSlider
            label="Frequency Penalty"
            parameter="frequencyPenalty"
            min={-2}
            max={2}
            step={0.1}
            value={params.frequencyPenalty}
            description="Reduces repeated tokens based on frequency (-2 to 2). Default: 0"
          />

          <ParameterSlider
            label="Presence Penalty"
            parameter="presencePenalty"
            min={-2}
            max={2}
            step={0.1}
            value={params.presencePenalty}
            description="Reduces repeated tokens based on presence (-2 to 2). Default: 0"
          />
        </div>

        {/* Advanced Settings */}
        <button
          type="button"
          onClick={() => setShowAdvanced(!showAdvanced)}
          className="w-full text-left px-3 py-2 bg-white border border-pink-200 rounded-lg text-xs font-semibold text-pink-600 hover:bg-pink-50 transition-colors flex items-center gap-2"
        >
          {showAdvanced ? '▼' : '▶'} Advanced Settings
        </button>

        {showAdvanced && (
          <div className="bg-white border border-pink-200 rounded-lg p-3 space-y-3">
            <h4 className="text-xs font-semibold text-gray-700">Retry & Resilience</h4>

            <ParameterInput
              label="Retry Count"
              parameter="retryCount"
              min={0}
              max={5}
              value={params.retryCount}
              description="Number of retries on transient failure (0-5)"
            />

            <ParameterInput
              label="Backoff Multiplier"
              parameter="backoffMultiplier"
              min={1}
              max={10}
              value={params.backoffMultiplier}
              description="Exponential backoff multiplier (e.g., 2.0 = double wait time)"
            />

            <ParameterInput
              label="Initial Delay (ms)"
              parameter="initialDelayMs"
              min={100}
              max={10000}
              value={params.initialDelayMs}
              description="Initial retry delay in milliseconds"
            />
          </div>
        )}
      </div>

      {/* Presets */}
      <div className="bg-blue-50 border border-blue-300 rounded-lg p-3 space-y-2">
        <p className="text-xs font-semibold text-blue-700">⚡ Quick Presets</p>
        <div className="grid grid-cols-3 gap-2">
          {(Object.keys(quickPresets) as Array<keyof typeof quickPresets>).map((preset) => (
            <button
              key={preset}
              type="button"
              onClick={() => applyQuickPreset(preset)}
              aria-pressed={isQuickPresetActive(preset)}
              className={`px-2 py-1 text-xs font-semibold rounded transition-colors ${
                isQuickPresetActive(preset)
                  ? 'bg-blue-600 text-white shadow-sm'
                  : 'bg-blue-100 text-blue-700 hover:bg-blue-200'
              }`}
            >
              {preset}
            </button>
          ))}
        </div>
      </div>

      {/* Info */}
      <div className="p-2 bg-yellow-50 border border-yellow-300 rounded text-yellow-700 text-xs">
        <p className="font-semibold mb-1">⚠️ Parameter Notes</p>
        <ul className="space-y-0.5 list-disc list-inside">
          <li>Higher temperature = more creative but less consistent</li>
          <li>Penalties reduce repetition in generated text</li>
          <li>Max tokens affects both output length and API cost</li>
          <li>Retry logic helps with rate limiting and transient errors</li>
        </ul>
      </div>
    </div>
  );
};
