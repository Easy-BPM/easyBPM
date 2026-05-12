import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { AdminGroup, AdminUser } from '../types';

const ALL_PERMISSIONS = [
  'ACCESS_BPM_ADMIN',
  'ACCESS_PROCESS_PORTAL',
  'ACCESS_BPM_MODELER',
  'MANAGE_USERS',
  'MANAGE_GROUPS',
  'MANAGE_PERMISSIONS'
];

export const SecurityAdminView: React.FC = () => {
  const [tab, setTab] = useState<'users' | 'groups'>('users');
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [groups, setGroups] = useState<AdminGroup[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [savingUserAccess, setSavingUserAccess] = useState(false);
  const [selectedGroupId, setSelectedGroupId] = useState<number | null>(null);
  const [selectedGroupUserIds, setSelectedGroupUserIds] = useState<number[]>([]);
  const [editingUser, setEditingUser] = useState<AdminUser | null>(null);
  const [editingGroupIds, setEditingGroupIds] = useState<number[]>([]);
  const [editingPermissionCodes, setEditingPermissionCodes] = useState<string[]>([]);
  const [userSearch, setUserSearch] = useState('');
  const [groupSearch, setGroupSearch] = useState('');
  const [editingGroup, setEditingGroup] = useState<AdminGroup | null>(null);
  const [editingGroupName, setEditingGroupName] = useState('');
  const [editingGroupPermissionCodes, setEditingGroupPermissionCodes] = useState<string[]>([]);
  const [savingGroupEdit, setSavingGroupEdit] = useState(false);

  const [newUser, setNewUser] = useState({
    username: '',
    password: '',
    enabled: true,
    groupIds: [] as number[],
    permissionCodes: [] as string[]
  });
  const [newGroup, setNewGroup] = useState({ code: '', name: '', permissionCodes: [] as string[] });

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [u, g] = await Promise.all([adminService.getUsers(), adminService.getGroups()]);
      setUsers(u);
      setGroups(g);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const createUser = async () => {
    try {
      await adminService.createUser({
        username: newUser.username,
        password: newUser.password,
        enabled: newUser.enabled,
        groupIds: newUser.groupIds,
        permissionCodes: newUser.permissionCodes
      });
      setNewUser({ username: '', password: '', enabled: true, groupIds: [], permissionCodes: [] });
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const toggleUserEnabled = async (user: AdminUser) => {
    try {
      const matchingGroupIds = groups
        .filter(group => user.groups.includes(group.code))
        .map(group => group.id);

      await adminService.updateUser(user.id, {
        enabled: !user.enabled,
        groupIds: matchingGroupIds,
        permissionCodes: user.permissions
      });
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const removeUser = async (id: number) => {
    try {
      await adminService.deleteUser(id);
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const resetPassword = async (id: number) => {
    const password = window.prompt('Enter new password');
    if (!password) return;
    try {
      await adminService.resetUserPassword(id, password);
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const createGroup = async () => {
    try {
      await adminService.createGroup(newGroup);
      setNewGroup({ code: '', name: '', permissionCodes: [] });
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const startEditGroup = (group: AdminGroup) => {
    setEditingGroup(group);
    setEditingGroupName(group.name);
    setEditingGroupPermissionCodes(group.permissions);
  };

  const closeEditGroup = () => {
    setEditingGroup(null);
    setEditingGroupName('');
    setEditingGroupPermissionCodes([]);
    setSavingGroupEdit(false);
  };

  const saveGroupEdit = async () => {
    if (!editingGroup) return;
    setSavingGroupEdit(true);
    try {
      await adminService.updateGroup(editingGroup.id, {
        name: editingGroupName,
        permissionCodes: editingGroupPermissionCodes
      });
      await load();
      closeEditGroup();
    } catch (e) {
      setError((e as Error).message);
      setSavingGroupEdit(false);
    }
  };

  const startManageGroupUsers = async (groupId: number) => {
    setSelectedGroupId(groupId);
    try {
      const groupUsers = await adminService.getGroupUsers(groupId);
      setSelectedGroupUserIds(groupUsers.map(user => user.id));
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const saveGroupUsers = async () => {
    if (selectedGroupId == null) return;
    try {
      await adminService.updateGroupUsers(selectedGroupId, selectedGroupUserIds);
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const toggleSelectedGroupUser = (userId: number, checked: boolean) => {
    setSelectedGroupUserIds(prev => checked ? [...prev, userId] : prev.filter(id => id !== userId));
  };

  const startEditUserAccess = (user: AdminUser) => {
    setEditingUser(user);
    setEditingGroupIds(
      groups
        .filter(group => user.groups.includes(group.code))
        .map(group => group.id)
    );
    setEditingPermissionCodes(user.permissions);
  };

  const closeEditUserAccess = () => {
    setEditingUser(null);
    setEditingGroupIds([]);
    setEditingPermissionCodes([]);
    setSavingUserAccess(false);
  };

  const saveUserAccess = async () => {
    if (!editingUser) return;
    setSavingUserAccess(true);
    try {
      await adminService.updateUser(editingUser.id, {
        enabled: editingUser.enabled,
        groupIds: editingGroupIds,
        permissionCodes: editingPermissionCodes
      });
      await load();
      closeEditUserAccess();
    } catch (e) {
      setError((e as Error).message);
      setSavingUserAccess(false);
    }
  };

  const removeGroup = async (id: number) => {
    try {
      await adminService.deleteGroup(id);
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const normalizedUserSearch = userSearch.trim().toLowerCase();
  const normalizedGroupSearch = groupSearch.trim().toLowerCase();

  const filteredUsers = users.filter(user => {
    if (!normalizedUserSearch) return true;
    return user.username.toLowerCase().includes(normalizedUserSearch);
  });

  const filteredGroups = groups.filter(group => {
    if (!normalizedGroupSearch) return true;
    const permissionsText = group.permissions.join(' ').toLowerCase();
    return (
      group.code.toLowerCase().includes(normalizedGroupSearch) ||
      group.name.toLowerCase().includes(normalizedGroupSearch) ||
      permissionsText.includes(normalizedGroupSearch)
    );
  });

  if (loading) return <p className="text-slate-500">Loading security administration...</p>;

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-slate-800">Security</h2>
        <p className="text-slate-500">Manage users, groups, direct permissions, and group membership.</p>
      </div>

      <div className="inline-flex rounded-lg border border-slate-200 bg-white p-1">
        <button
          onClick={() => setTab('users')}
          className={`px-4 py-2 rounded-md text-sm ${tab === 'users' ? 'bg-blue-600 text-white' : 'text-slate-600 hover:bg-slate-100'}`}
        >
          Users
        </button>
        <button
          onClick={() => setTab('groups')}
          className={`px-4 py-2 rounded-md text-sm ${tab === 'groups' ? 'bg-blue-600 text-white' : 'text-slate-600 hover:bg-slate-100'}`}
        >
          Groups
        </button>
      </div>

      {error && <div className="rounded-lg border border-red-200 bg-red-50 text-red-700 px-3 py-2 text-sm">{error}</div>}

      {tab === 'users' && (
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white border border-slate-200 rounded-xl p-4 space-y-3">
          <h3 className="font-semibold text-slate-700">Create User</h3>
          <input className="w-full border rounded px-3 py-2" placeholder="Username" value={newUser.username} onChange={e => setNewUser(prev => ({ ...prev, username: e.target.value }))} />
          <input className="w-full border rounded px-3 py-2" placeholder="Password" type="password" value={newUser.password} onChange={e => setNewUser(prev => ({ ...prev, password: e.target.value }))} />
          <label className="text-sm flex items-center gap-2">
            <input type="checkbox" checked={newUser.enabled} onChange={e => setNewUser(prev => ({ ...prev, enabled: e.target.checked }))} /> Enabled
          </label>
          <select
            multiple
            className="w-full border rounded px-3 py-2 h-36"
            value={newUser.groupIds.map(String)}
            onChange={e => {
              const values = Array.from(e.target.selectedOptions).map(option => Number(option.value));
              setNewUser(prev => ({ ...prev, groupIds: values }));
            }}
          >
            {groups.map(group => (
              <option key={group.id} value={group.id}>{group.code} - {group.name}</option>
            ))}
          </select>
          <select
            multiple
            className="w-full border rounded px-3 py-2 h-36"
            value={newUser.permissionCodes}
            onChange={e => {
              const values = Array.from(e.target.selectedOptions).map(option => option.value);
              setNewUser(prev => ({ ...prev, permissionCodes: values }));
            }}
          >
            {ALL_PERMISSIONS.map(permission => (
              <option key={permission} value={permission}>{permission}</option>
            ))}
          </select>
          <button onClick={createUser} className="bg-emerald-600 text-white px-4 py-2 rounded">Create User</button>
        </div>

        <div className="bg-white border border-slate-200 rounded-xl p-4">
          <div className="flex items-center justify-between gap-3 mb-3">
            <h3 className="font-semibold text-slate-700">Users</h3>
            <input
              className="w-64 border rounded px-3 py-2 text-sm"
              placeholder="Search by username"
              value={userSearch}
              onChange={e => setUserSearch(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            {filteredUsers.map(user => (
              <div key={user.id} className="border rounded p-3">
                <div className="font-medium">{user.username} {!user.enabled && <span className="text-red-600">(disabled)</span>}</div>
                <div className="text-xs text-slate-500">Groups: {user.groups.join(', ') || '-'}</div>
                <div className="text-xs text-slate-500">Direct permissions: {user.permissions.join(', ') || '-'}</div>
                <div className="mt-2 flex gap-2">
                  <button onClick={() => toggleUserEnabled(user)} className="text-xs px-2 py-1 border rounded">
                    {user.enabled ? 'Disable' : 'Enable'}
                  </button>
                  <button onClick={() => startEditUserAccess(user)} className="text-xs px-2 py-1 border rounded">Edit Access</button>
                  <button onClick={() => resetPassword(user.id)} className="text-xs px-2 py-1 border rounded">Reset Password</button>
                  <button onClick={() => removeUser(user.id)} className="text-xs px-2 py-1 border rounded text-red-600 border-red-200">Delete</button>
                </div>
              </div>
            ))}
            {filteredUsers.length === 0 && (
              <p className="text-sm text-slate-500 border rounded p-3 bg-slate-50">No users matched your search.</p>
            )}
          </div>
        </div>
      </div>
      )}

      {editingUser && (
        <div className="fixed inset-0 bg-slate-900/50 z-50 flex items-center justify-center p-4">
          <div className="w-full max-w-2xl bg-white rounded-xl border border-slate-200 shadow-2xl">
            <div className="px-5 py-4 border-b border-slate-200">
              <h3 className="text-lg font-semibold text-slate-800">Edit Access: {editingUser.username}</h3>
              <p className="text-sm text-slate-500">Manage group membership and direct permissions for this user.</p>
            </div>
            <div className="p-5 grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-2">Groups</label>
                <div className="border rounded-lg p-3 max-h-64 overflow-auto space-y-2">
                  {groups.map(group => {
                    const checked = editingGroupIds.includes(group.id);
                    return (
                      <label key={group.id} className="flex items-center gap-2 text-sm">
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={event => {
                            setEditingGroupIds(prev =>
                              event.target.checked ? [...prev, group.id] : prev.filter(id => id !== group.id)
                            );
                          }}
                        />
                        <span>{group.code} - {group.name}</span>
                      </label>
                    );
                  })}
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-2">Direct Permissions</label>
                <div className="border rounded-lg p-3 max-h-64 overflow-auto space-y-2">
                  {ALL_PERMISSIONS.map(permission => {
                    const checked = editingPermissionCodes.includes(permission);
                    return (
                      <label key={permission} className="flex items-center gap-2 text-sm">
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={event => {
                            setEditingPermissionCodes(prev =>
                              event.target.checked ? [...prev, permission] : prev.filter(code => code !== permission)
                            );
                          }}
                        />
                        <span>{permission}</span>
                      </label>
                    );
                  })}
                </div>
              </div>
            </div>
            <div className="px-5 py-4 border-t border-slate-200 flex justify-end gap-2">
              <button onClick={closeEditUserAccess} className="px-4 py-2 border rounded-lg">Cancel</button>
              <button
                onClick={saveUserAccess}
                disabled={savingUserAccess}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg disabled:opacity-60"
              >
                {savingUserAccess ? 'Saving...' : 'Save Access'}
              </button>
            </div>
          </div>
        </div>
      )}

      {tab === 'groups' && (
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white border border-slate-200 rounded-xl p-4 space-y-3">
          <h3 className="font-semibold text-slate-700">Create Group</h3>
          <input className="w-full border rounded px-3 py-2" placeholder="Code (e.g. OPS)" value={newGroup.code} onChange={e => setNewGroup(prev => ({ ...prev, code: e.target.value }))} />
          <input className="w-full border rounded px-3 py-2" placeholder="Name" value={newGroup.name} onChange={e => setNewGroup(prev => ({ ...prev, name: e.target.value }))} />
          <select
            multiple
            className="w-full border rounded px-3 py-2 h-36"
            value={newGroup.permissionCodes}
            onChange={e => {
              const values = Array.from(e.target.selectedOptions).map(option => option.value);
              setNewGroup(prev => ({ ...prev, permissionCodes: values }));
            }}
          >
            {ALL_PERMISSIONS.map(permission => (
              <option key={permission} value={permission}>{permission}</option>
            ))}
          </select>
          <button onClick={createGroup} className="bg-blue-600 text-white px-4 py-2 rounded">Create Group</button>
        </div>

        <div className="bg-white border border-slate-200 rounded-xl p-4">
          <div className="flex items-center justify-between gap-3 mb-3">
            <h3 className="font-semibold text-slate-700">Groups</h3>
            <input
              className="w-64 border rounded px-3 py-2 text-sm"
              placeholder="Search groups, names, permissions"
              value={groupSearch}
              onChange={e => setGroupSearch(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            {filteredGroups.map(group => (
              <div key={group.id} className="border rounded p-3">
                <div className="font-medium">{group.code} - {group.name}</div>
                <div className="text-xs text-slate-500">Permissions: {group.permissions.join(', ') || 'No permissions'}</div>
                <div className="mt-2 flex gap-2">
                  <button onClick={() => startEditGroup(group)} className="text-xs px-2 py-1 border rounded">Update</button>
                  <button onClick={() => startManageGroupUsers(group.id)} className="text-xs px-2 py-1 border rounded">Manage Users</button>
                  <button onClick={() => removeGroup(group.id)} className="text-xs px-2 py-1 border rounded text-red-600 border-red-200">Delete</button>
                </div>
              </div>
            ))}
            {filteredGroups.length === 0 && (
              <p className="text-sm text-slate-500 border rounded p-3 bg-slate-50">No groups matched your search.</p>
            )}
          </div>
        </div>

        {editingGroup && (
          <div className="fixed inset-0 bg-slate-900/50 z-50 flex items-center justify-center p-4">
            <div className="w-full max-w-2xl bg-white rounded-xl border border-slate-200 shadow-2xl">
              <div className="px-5 py-4 border-b border-slate-200">
                <h3 className="text-lg font-semibold text-slate-800">Edit Group: {editingGroup.code}</h3>
                <p className="text-sm text-slate-500">Update group name and permissions.</p>
              </div>
              <div className="p-5 space-y-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-2">Group Name</label>
                  <input
                    className="w-full border rounded px-3 py-2"
                    value={editingGroupName}
                    onChange={e => setEditingGroupName(e.target.value)}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-2">Permissions</label>
                  <div className="border rounded-lg p-3 max-h-64 overflow-auto space-y-2">
                    {ALL_PERMISSIONS.map(permission => {
                      const checked = editingGroupPermissionCodes.includes(permission);
                      return (
                        <label key={permission} className="flex items-center gap-2 text-sm">
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={event => {
                              setEditingGroupPermissionCodes(prev =>
                                event.target.checked ? [...prev, permission] : prev.filter(code => code !== permission)
                              );
                            }}
                          />
                          <span>{permission}</span>
                        </label>
                      );
                    })}
                  </div>
                </div>
              </div>
              <div className="px-5 py-4 border-t border-slate-200 flex justify-end gap-2">
                <button onClick={closeEditGroup} className="px-4 py-2 border rounded-lg">Cancel</button>
                <button
                  onClick={saveGroupEdit}
                  disabled={savingGroupEdit || editingGroupName.trim().length === 0}
                  className="px-4 py-2 bg-blue-600 text-white rounded-lg disabled:opacity-60"
                >
                  {savingGroupEdit ? 'Saving...' : 'Save Group'}
                </button>
              </div>
            </div>
          </div>
        )}

         {selectedGroupId != null && (
          <div className="bg-white border border-slate-200 rounded-xl p-4 lg:col-span-2">
            <h3 className="font-semibold text-slate-700 mb-3">Group Membership</h3>
            <p className="text-xs text-slate-500 mb-3">Select existing users that should belong to this group.</p>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2">
              {users.map(user => {
                const checked = selectedGroupUserIds.includes(user.id);
                return (
                  <label key={user.id} className="border rounded px-3 py-2 flex items-center gap-2 text-sm">
                    <input type="checkbox" checked={checked} onChange={e => toggleSelectedGroupUser(user.id, e.target.checked)} />
                    <span>{user.username}</span>
                  </label>
                );
              })}
            </div>
            <div className="mt-4 flex gap-2">
              <button onClick={saveGroupUsers} className="bg-emerald-600 text-white px-4 py-2 rounded">Save Membership</button>
              <button onClick={() => setSelectedGroupId(null)} className="px-4 py-2 border rounded">Close</button>
            </div>
          </div>
        )}
      </div>
      )}
    </div>
  );
};

