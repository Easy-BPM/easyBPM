import React from 'react';
import type { ProcessVariable } from '../types';

export const RuntimeVariables: React.FC<{ variables: ProcessVariable[] }> = ({ variables }) => variables.length ? (
  <details className="rounded-lg border border-slate-200 bg-slate-50 p-3">
    <summary className="cursor-pointer text-sm font-medium text-slate-600">Runtime diagnostics ({variables.length})</summary>
    <p className="mt-2 text-xs text-slate-500">Engine-managed parallel-join counters. Read-only; these are not form or business variables.</p>
    <dl className="mt-2 space-y-1 text-xs">{variables.map(variable => <div key={variable.name} className="flex justify-between gap-3">
      <dt className="font-mono break-all">{variable.name}</dt><dd>{JSON.stringify(variable.value)}</dd>
    </div>)}</dl>
  </details>
) : null;
