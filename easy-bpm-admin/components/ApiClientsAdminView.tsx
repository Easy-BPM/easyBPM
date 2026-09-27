import React, { useEffect, useMemo, useState } from 'react';
import { Clipboard, Download, Eye, KeyRound, Pencil, Plus, RefreshCw, RotateCw, Search, ShieldOff, X } from 'lucide-react';
import { adminService } from '../services/adminService';
import { ApiClient, ApiClientAudit, ApiClientCredential, AssignablePermission } from '../types';

type Props = { permissions: string[]; embedded?: boolean };
type EditorState = { mode: 'create' | 'edit'; client?: ApiClient; name: string; description: string; expiresAt: string; permissionCodes: string[] };

const localDateTimeValue = (date: Date) => {
  const offset = date.getTimezoneOffset() * 60000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
};

const dateLabel = (value?: string | null) => value ? new Date(value).toLocaleString() : 'Never';

export const ApiClientsAdminView: React.FC<Props> = ({ permissions, embedded = false }) => {
  const canRead = permissions.includes('VIEW_API_CLIENTS') || permissions.includes('MANAGE_API_CLIENTS');
  const canManage = permissions.includes('MANAGE_API_CLIENTS');
  const [clients, setClients] = useState<ApiClient[]>([]);
  const [assignable, setAssignable] = useState<AssignablePermission[]>([]);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [editor, setEditor] = useState<EditorState | null>(null);
  const [credential, setCredential] = useState<ApiClientCredential | null>(null);
  const [credentialSaved, setCredentialSaved] = useState(false);
  const [auditClient, setAuditClient] = useState<ApiClient | null>(null);
  const [audit, setAudit] = useState<ApiClientAudit[]>([]);
  const [auditLoading, setAuditLoading] = useState(false);
  const [revokeClient, setRevokeClient] = useState<ApiClient | null>(null);
  const [revokeConfirmation, setRevokeConfirmation] = useState('');

  const load = async (targetPage = page) => {
    if (!canRead) return;
    setLoading(true);
    setError(null);
    try {
      const result = await adminService.getApiClients({ q: query || undefined, status: status || undefined, page: targetPage, size: 50 });
      setClients(result.content);
      setTotalPages(Math.max(result.totalPages, 1));
      setPage(result.number);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(0); }, [canRead, status]);
  useEffect(() => {
    if (!canManage) return;
    void adminService.getAssignableApiClientPermissions().then(setAssignable).catch(e => setError((e as Error).message));
  }, [canManage]);

  const openCreate = () => setEditor({
    mode: 'create', name: '', description: '',
    expiresAt: localDateTimeValue(new Date(Date.now() + 90 * 86400000)), permissionCodes: []
  });

  const openEdit = (client: ApiClient) => setEditor({
    mode: 'edit', client, name: client.name, description: client.description ?? '',
    expiresAt: localDateTimeValue(new Date(client.expiresAt)), permissionCodes: [...client.permissionCodes]
  });

  const save = async () => {
    if (!editor || !canManage) return;
    setWorking(true); setError(null); setSuccess(null);
    try {
      const payload = {
        name: editor.name,
        description: editor.description,
        permissionCodes: editor.permissionCodes,
        expiresAt: editor.expiresAt.length === 16 ? `${editor.expiresAt}:00` : editor.expiresAt
      };
      if (editor.mode === 'create') {
        const created = await adminService.createApiClient(payload);
        setCredential(created); setCredentialSaved(false);
        setSuccess(`API client “${created.client.name}” created.`);
      } else if (editor.client) {
        await adminService.updateApiClient(editor.client.id, editor.client.version, payload);
        setSuccess(`API client “${editor.name}” updated.`);
      }
      setEditor(null);
      await load(page);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setWorking(false);
    }
  };

  const rotate = async (client: ApiClient) => {
    if (!canManage || !window.confirm(`Rotate “${client.name}”? The current credential will stop working immediately.`)) return;
    setWorking(true); setError(null);
    try {
      const rotated = await adminService.rotateApiClient(client.id, client.version);
      setCredential(rotated); setCredentialSaved(false);
      setSuccess(`Credential for “${client.name}” rotated.`);
      await load(page);
    } catch (e) { setError((e as Error).message); }
    finally { setWorking(false); }
  };

  const revoke = async () => {
    if (!revokeClient || revokeConfirmation !== revokeClient.name) return;
    setWorking(true); setError(null);
    try {
      await adminService.revokeApiClient(revokeClient.id, revokeClient.version);
      setSuccess(`API client “${revokeClient.name}” revoked.`);
      setRevokeClient(null); setRevokeConfirmation('');
      await load(page);
    } catch (e) { setError((e as Error).message); }
    finally { setWorking(false); }
  };

  const showAudit = async (client: ApiClient) => {
    setAuditClient(client); setAudit([]); setAuditLoading(true); setError(null);
    try { setAudit((await adminService.getApiClientAudit(client.id)).content); }
    catch (e) { setError((e as Error).message); }
    finally { setAuditLoading(false); }
  };

  const downloadCredential = () => {
    if (!credential) return;
    const url = URL.createObjectURL(new Blob([credential.credential + '\n'], { type: 'text/plain' }));
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = `easybpm-${credential.client.name.replace(/\s+/g, '-').toLowerCase()}-credential.txt`;
    anchor.click(); URL.revokeObjectURL(url);
  };

  const selectedPermissions = useMemo(() => new Set(editor?.permissionCodes ?? []), [editor]);
  const togglePermission = (code: string) => editor && setEditor({
    ...editor,
    permissionCodes: selectedPermissions.has(code) ? editor.permissionCodes.filter(item => item !== code) : [...editor.permissionCodes, code]
  });

  if (!canRead) return <div className="p-8 text-sm text-slate-600">You do not have permission to view API clients.</div>;

  return (
    <div className={embedded ? 'space-y-5' : 'p-8 space-y-5'}>
      <div className="flex items-start justify-between gap-4">
        <div><h2 className="text-2xl font-bold text-slate-800">API Clients</h2><p className="mt-1 text-sm text-slate-500">Service identities for external EasyBPM API integrations.</p></div>
        <div className="flex gap-2">
          <button onClick={() => void load(page)} className="rounded border border-slate-300 px-3 py-2 text-sm text-slate-700 hover:bg-slate-50"><RefreshCw size={15} className="inline mr-2" />Refresh</button>
          {canManage && <button onClick={openCreate} className="rounded bg-blue-600 px-3 py-2 text-sm font-semibold text-white hover:bg-blue-700"><Plus size={15} className="inline mr-2" />Create API client</button>}
        </div>
      </div>
      {error && <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      {success && <div className="rounded border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{success}</div>}
      <div className="flex flex-wrap gap-3 rounded border border-slate-200 bg-white p-4">
        <label className="relative min-w-64 flex-1"><Search size={15} className="absolute left-3 top-3 text-slate-400" /><input value={query} onChange={e => setQuery(e.target.value)} onKeyDown={e => e.key === 'Enter' && void load(0)} placeholder="Search by name" className="w-full rounded border border-slate-300 py-2 pl-9 pr-3 text-sm" /></label>
        <select value={status} onChange={e => setStatus(e.target.value)} className="rounded border border-slate-300 px-3 py-2 text-sm"><option value="">All statuses</option><option>ACTIVE</option><option>EXPIRED</option><option>REVOKED</option></select>
      </div>
      <div className="overflow-hidden rounded border border-slate-200 bg-white">
        {loading ? <div className="p-10 text-center text-sm text-slate-500">Loading API clients…</div> : clients.length === 0 ? <div className="p-10 text-center text-sm text-slate-500">{query || status ? 'No API clients match these filters.' : 'No API clients have been created.'}</div> : (
          <table className="w-full text-left text-sm"><thead className="bg-slate-50 text-xs uppercase text-slate-500"><tr><th className="px-4 py-3">Client</th><th className="px-4 py-3">Status</th><th className="px-4 py-3">Permissions</th><th className="px-4 py-3">Expires</th><th className="px-4 py-3">Last used</th><th className="px-4 py-3 text-right">Actions</th></tr></thead>
          <tbody className="divide-y divide-slate-100">{clients.map(client => <tr key={client.id}>
            <td className="px-4 py-3"><div className="font-semibold text-slate-800">{client.name}</div><div className="max-w-xs truncate text-xs text-slate-500">{client.description || client.id}</div></td>
            <td className="px-4 py-3"><span className={`rounded px-2 py-1 text-xs font-semibold ${client.status === 'ACTIVE' ? 'bg-emerald-100 text-emerald-700' : client.status === 'EXPIRED' ? 'bg-amber-100 text-amber-700' : 'bg-slate-200 text-slate-600'}`}>{client.status}</span></td>
            <td className="px-4 py-3 text-xs text-slate-600">{client.permissionCodes.length ? client.permissionCodes.join(', ') : 'None'}</td>
            <td className="px-4 py-3 text-slate-600">{dateLabel(client.expiresAt)}</td><td className="px-4 py-3 text-slate-600">{dateLabel(client.lastUsedAt)}{client.lastUsedIp && <div className="text-xs">{client.lastUsedIp}</div>}</td>
            <td className="px-4 py-3"><div className="flex justify-end gap-1"><button title="View audit" onClick={() => void showAudit(client)} className="p-2 text-slate-500 hover:text-blue-600"><Eye size={16} /></button>{canManage && client.status === 'ACTIVE' && <button title="Edit" onClick={() => openEdit(client)} className="p-2 text-slate-500 hover:text-blue-600"><Pencil size={16} /></button>}{canManage && client.status !== 'REVOKED' && <button title="Rotate" disabled={working} onClick={() => void rotate(client)} className="p-2 text-slate-500 hover:text-blue-600"><RotateCw size={16} /></button>}{canManage && client.status !== 'REVOKED' && <button title="Revoke" onClick={() => { setRevokeClient(client); setRevokeConfirmation(''); }} className="p-2 text-slate-500 hover:text-red-600"><ShieldOff size={16} /></button>}</div></td>
          </tr>)}</tbody></table>
        )}
      </div>
      <div className="flex justify-end gap-2 text-sm"><button disabled={page === 0} onClick={() => void load(page - 1)} className="rounded border px-3 py-1 disabled:opacity-40">Previous</button><span className="px-2 py-1">Page {page + 1} of {totalPages}</span><button disabled={page + 1 >= totalPages} onClick={() => void load(page + 1)} className="rounded border px-3 py-1 disabled:opacity-40">Next</button></div>

      {editor && <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4"><div className="w-full max-w-2xl rounded-xl bg-white p-6 shadow-xl"><div className="flex justify-between"><h3 className="text-lg font-bold">{editor.mode === 'create' ? 'Create API client' : 'Edit API client'}</h3><button onClick={() => setEditor(null)}><X size={18} /></button></div>
        <div className="mt-5 grid gap-4"><label className="text-sm font-medium">Name<input value={editor.name} onChange={e => setEditor({...editor, name: e.target.value})} className="mt-1 w-full rounded border px-3 py-2" /></label><label className="text-sm font-medium">Description<textarea value={editor.description} onChange={e => setEditor({...editor, description: e.target.value})} className="mt-1 w-full rounded border px-3 py-2" /></label><label className="text-sm font-medium">Expires at<input type="datetime-local" value={editor.expiresAt} onChange={e => setEditor({...editor, expiresAt: e.target.value})} className="mt-1 w-full rounded border px-3 py-2" /></label>
        <fieldset><legend className="text-sm font-medium">Permissions</legend><div className="mt-2 grid max-h-48 grid-cols-2 gap-2 overflow-auto rounded border p-3">{assignable.map(item => <label key={item.code} className="flex gap-2 text-xs"><input type="checkbox" checked={selectedPermissions.has(item.code)} onChange={() => togglePermission(item.code)} /><span><b>{item.code}</b><br />{item.name}</span></label>)}</div></fieldset></div>
        <div className="mt-6 flex justify-end gap-2"><button onClick={() => setEditor(null)} className="rounded border px-4 py-2 text-sm">Cancel</button><button disabled={working || !editor.name || !editor.expiresAt} onClick={() => void save()} className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">{working ? 'Saving…' : 'Save'}</button></div></div></div>}

      {credential && <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4"><div className="w-full max-w-2xl rounded-xl bg-white p-6 shadow-xl"><div className="flex items-center gap-2"><KeyRound className="text-amber-600" /><h3 className="text-lg font-bold">Save this credential now</h3></div><p className="mt-3 text-sm text-amber-800">This is the only time EasyBPM will show this credential. Store it in a secure secret manager.</p><pre className="mt-4 overflow-auto rounded bg-slate-950 p-4 text-sm text-emerald-300">{credential.credential}</pre><div className="mt-3 flex gap-2"><button onClick={() => void navigator.clipboard.writeText(credential.credential)} className="rounded border px-3 py-2 text-sm"><Clipboard size={15} className="inline mr-2" />Copy</button><button onClick={downloadCredential} className="rounded border px-3 py-2 text-sm"><Download size={15} className="inline mr-2" />Download</button></div><label className="mt-5 flex gap-2 text-sm"><input type="checkbox" checked={credentialSaved} onChange={e => setCredentialSaved(e.target.checked)} />I have saved this credential securely.</label><div className="mt-5 flex justify-end"><button disabled={!credentialSaved} onClick={() => { setCredential(null); setCredentialSaved(false); }} className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-40">Close</button></div></div></div>}

      {revokeClient && <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4"><div className="w-full max-w-lg rounded-xl bg-white p-6"><h3 className="text-lg font-bold text-red-700">Revoke API client</h3><p className="mt-2 text-sm text-slate-600">Revocation is immediate and permanent. Type <b>{revokeClient.name}</b> to confirm.</p><input autoFocus value={revokeConfirmation} onChange={e => setRevokeConfirmation(e.target.value)} className="mt-4 w-full rounded border px-3 py-2" /><div className="mt-5 flex justify-end gap-2"><button onClick={() => setRevokeClient(null)} className="rounded border px-4 py-2 text-sm">Cancel</button><button disabled={working || revokeConfirmation !== revokeClient.name} onClick={() => void revoke()} className="rounded bg-red-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-40">Revoke permanently</button></div></div></div>}

      {auditClient && <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4"><div className="max-h-[80vh] w-full max-w-4xl overflow-auto rounded-xl bg-white p-6"><div className="flex justify-between"><div><h3 className="text-lg font-bold">Audit · {auditClient.name}</h3><p className="text-xs text-slate-500">Lifecycle and authenticated API use. Credentials are never recorded.</p></div><button onClick={() => setAuditClient(null)}><X size={18} /></button></div>{auditLoading ? <p className="py-8 text-center text-sm">Loading…</p> : audit.length === 0 ? <p className="py-8 text-center text-sm text-slate-500">No audit records.</p> : <div className="mt-4 divide-y rounded border">{audit.map(item => <div key={item.id} className="p-3 text-sm"><div className="flex justify-between"><b>{item.action} · {item.outcome}</b><span className="text-slate-500">{dateLabel(item.createdAt)}</span></div><div className="mt-1 text-xs text-slate-600">{item.actor || 'System'}{item.httpMethod && ` · ${item.httpMethod} ${item.requestPath}`}{item.httpStatus && ` · HTTP ${item.httpStatus}`}{item.remoteIp && ` · ${item.remoteIp}`}</div></div>)}</div>}</div></div>}
    </div>
  );
};
