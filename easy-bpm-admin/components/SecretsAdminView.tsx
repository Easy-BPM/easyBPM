import React, { useEffect, useMemo, useState } from 'react';
import { Copy, KeyRound, Loader2, RefreshCw, Save, Trash2 } from 'lucide-react';
import { adminService } from '../services/adminService';
import { AdminSecret } from '../types';

const providerOptions = ['openai', 'azure-openai', 'anthropic', 'gemini', 'custom-api'];
const credentialTypeOptions = ['API_KEY', 'BEARER', 'BASIC_AUTH'];

export const SecretsAdminView: React.FC<{ permissions: string[] }> = ({ permissions }) => {
  const canManage = permissions.includes('MANAGE_SECRETS');
  const [secrets, setSecrets] = useState<AdminSecret[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [form, setForm] = useState({
    name: '',
    providerId: 'openai',
    credentialType: 'API_KEY',
    token: '',
    description: '',
    permissions: ''
  });
  const [rotationById, setRotationById] = useState<Record<string, string>>({});

  const sortedSecrets = useMemo(
    () => [...secrets].sort((left, right) => left.name.localeCompare(right.name)),
    [secrets]
  );

  const loadSecrets = async () => {
    setLoading(true);
    setError(null);
    try {
      setSecrets(await adminService.getSecrets());
    } catch (caught) {
      setError((caught as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadSecrets();
  }, []);

  const parsePermissions = (value: string) =>
    value.split(',').map(item => item.trim()).filter(Boolean);

  const createSecret = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!canManage || !form.name.trim() || !form.token.trim()) return;

    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const created = await adminService.createSecret({
        name: form.name.trim(),
        providerId: form.providerId,
        credentialType: form.credentialType,
        token: form.token,
        description: form.description.trim() || undefined,
        permissions: parsePermissions(form.permissions)
      });
      setSecrets(current => [created, ...current]);
      setForm({ name: '', providerId: 'openai', credentialType: 'API_KEY', token: '', description: '', permissions: '' });
      setMessage('Secret saved.');
    } catch (caught) {
      setError((caught as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const rotateSecret = async (secret: AdminSecret) => {
    const token = rotationById[secret.id]?.trim();
    if (!canManage || !token) return;

    setError(null);
    setMessage(null);
    try {
      const updated = await adminService.updateSecret(secret.id, { token });
      setSecrets(current => current.map(item => item.id === updated.id ? updated : item));
      setRotationById(current => ({ ...current, [secret.id]: '' }));
      setMessage(`${secret.name} rotated.`);
    } catch (caught) {
      setError((caught as Error).message);
    }
  };

  const deleteSecret = async (secret: AdminSecret) => {
    if (!canManage) return;
    if (!window.confirm(`Remove secret ${secret.name}? Existing processes using it may fail until updated.`)) return;

    setError(null);
    setMessage(null);
    try {
      await adminService.deleteSecret(secret.id);
      setSecrets(current => current.filter(item => item.id !== secret.id));
      setMessage(`${secret.name} removed.`);
    } catch (caught) {
      setError((caught as Error).message);
    }
  };

  const copyReference = async (reference: string) => {
    await navigator.clipboard?.writeText(reference);
    setMessage('Reference copied.');
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold text-slate-800">Workspace Secrets</h2>
          <p className="text-sm text-slate-500">Encrypted AI tokens and API secrets available to BPM Modeler runtime configuration.</p>
        </div>
        <button
          onClick={loadSecrets}
          className="flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
        >
          <RefreshCw size={16} /> Refresh
        </button>
      </div>

      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      {message && <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{message}</div>}

      {canManage && (
        <form onSubmit={createSecret} className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="mb-4 flex items-center gap-2">
            <KeyRound size={18} className="text-blue-600" />
            <h3 className="font-semibold text-slate-800">Add Secret</h3>
          </div>
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
            <label className="block">
              <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500">Name</span>
              <input value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" placeholder="OPENAI_PROD" />
            </label>
            <label className="block">
              <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500">Provider</span>
              <select value={form.providerId} onChange={event => setForm({ ...form, providerId: event.target.value })} className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm">
                {providerOptions.map(option => <option key={option} value={option}>{option}</option>)}
              </select>
            </label>
            <label className="block">
              <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500">Type</span>
              <select value={form.credentialType} onChange={event => setForm({ ...form, credentialType: event.target.value })} className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm">
                {credentialTypeOptions.map(option => <option key={option} value={option}>{option}</option>)}
              </select>
            </label>
            <label className="block">
              <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500">Allowed roles</span>
              <input value={form.permissions} onChange={event => setForm({ ...form, permissions: event.target.value })} className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" placeholder="Optional, comma separated" />
            </label>
            <label className="block md:col-span-2">
              <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500">Secret value</span>
              <input type="password" value={form.token} onChange={event => setForm({ ...form, token: event.target.value })} className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" placeholder="Paste token once. It will not be shown again." />
            </label>
            <label className="block md:col-span-2">
              <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500">Description</span>
              <input value={form.description} onChange={event => setForm({ ...form, description: event.target.value })} className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" placeholder="Used by production invoice agent" />
            </label>
          </div>
          <button disabled={saving || !form.name.trim() || !form.token.trim()} className="mt-4 flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-60">
            {saving ? <Loader2 className="animate-spin" size={16} /> : <Save size={16} />} Save secret
          </button>
        </form>
      )}

      <div className="rounded-xl border border-slate-200 bg-white shadow-sm">
        {loading ? (
          <div className="flex items-center justify-center gap-2 py-12 text-sm text-slate-500">
            <Loader2 className="animate-spin" size={18} /> Loading secrets...
          </div>
        ) : sortedSecrets.length === 0 ? (
          <div className="py-12 text-center text-sm text-slate-500">No workspace secrets have been added yet.</div>
        ) : (
          <div className="divide-y divide-slate-100">
            {sortedSecrets.map(secret => (
              <div key={secret.id} className="grid gap-3 p-4 lg:grid-cols-[1.4fr_1fr_1fr]">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-mono text-sm font-semibold text-slate-800">{secret.name}</p>
                    <span className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-600">{secret.providerId}</span>
                    <span className="rounded bg-blue-50 px-2 py-0.5 text-xs text-blue-700">{secret.credentialType}</span>
                  </div>
                  <p className="mt-1 text-sm text-slate-500">{secret.description || 'No description.'}</p>
                  <button onClick={() => copyReference(secret.reference)} className="mt-2 flex items-center gap-1 text-xs font-medium text-blue-700 hover:text-blue-900">
                    <Copy size={13} /> {secret.reference}
                  </button>
                </div>
                <div className="text-sm">
                  <p className="font-mono text-slate-700">{secret.maskedToken}</p>
                  <p className="mt-1 text-xs text-slate-500">Updated {new Date(secret.updatedAt).toLocaleString()}</p>
                </div>
                <div className="flex flex-col gap-2">
                  {canManage && (
                    <>
                      <div className="flex gap-2">
                        <input type="password" value={rotationById[secret.id] ?? ''} onChange={event => setRotationById({ ...rotationById, [secret.id]: event.target.value })} className="min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm" placeholder="New value" />
                        <button onClick={() => rotateSecret(secret)} className="rounded-lg border border-slate-300 px-3 py-2 text-slate-700 hover:bg-slate-50" title="Rotate secret">
                          <RefreshCw size={16} />
                        </button>
                      </div>
                      <button onClick={() => deleteSecret(secret)} className="flex items-center justify-center gap-2 rounded-lg border border-red-200 px-3 py-2 text-sm font-medium text-red-700 hover:bg-red-50">
                        <Trash2 size={16} /> Remove
                      </button>
                    </>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
