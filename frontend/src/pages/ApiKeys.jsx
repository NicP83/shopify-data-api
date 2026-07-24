import { useState, useEffect } from 'react'

/**
 * API Keys management. This page is itself protected by the API-key filter:
 * it needs an admin-scoped key (or the bootstrap key) to call
 * /api/v1/admin/keys. The admin key is held in localStorage and sent as the
 * X-API-Key header.
 */
export default function ApiKeys() {
  const [adminKey, setAdminKey] = useState(localStorage.getItem('hh_admin_key') || '')
  const [keys, setKeys] = useState([])
  const [error, setError] = useState(null)
  const [newlyCreated, setNewlyCreated] = useState(null)
  const [form, setForm] = useState({
    name: '', scopes: 'agents:run,workflows:run', rateLimitPerMin: 60,
    refererAllowlist: '', boundAgentId: '', boundWorkflowId: ''
  })

  const base = import.meta.env.VITE_API_URL || ''

  const headers = () => ({ 'Content-Type': 'application/json', 'X-API-Key': adminKey })

  const load = async () => {
    if (!adminKey) return
    setError(null)
    try {
      const r = await fetch(`${base}/api/v1/admin/keys`, { headers: headers() })
      if (!r.ok) { setError(`Failed to load (${r.status}). Is your admin key correct?`); return }
      setKeys(await r.json())
    } catch (e) { setError(String(e)) }
  }

  useEffect(() => { load() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const saveAdminKey = () => { localStorage.setItem('hh_admin_key', adminKey); load() }

  const create = async (e) => {
    e.preventDefault()
    setError(null); setNewlyCreated(null)
    const body = {
      name: form.name,
      scopes: form.scopes,
      rateLimitPerMin: Number(form.rateLimitPerMin) || 60,
      refererAllowlist: form.refererAllowlist || null,
      boundAgentId: form.boundAgentId ? Number(form.boundAgentId) : null,
      boundWorkflowId: form.boundWorkflowId ? Number(form.boundWorkflowId) : null
    }
    try {
      const r = await fetch(`${base}/api/v1/admin/keys`, {
        method: 'POST', headers: headers(), body: JSON.stringify(body)
      })
      const j = await r.json()
      if (!r.ok) { setError(j.error || `Create failed (${r.status})`); return }
      setNewlyCreated(j.rawKey)
      setForm({ ...form, name: '' })
      load()
    } catch (e) { setError(String(e)) }
  }

  const revoke = async (id) => {
    if (!confirm('Revoke this key? Any system using it will stop working.')) return
    await fetch(`${base}/api/v1/admin/keys/${id}`, { method: 'DELETE', headers: headers() })
    load()
  }

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <h1 className="text-2xl font-bold mb-4">API Keys</h1>

      <div className="card mb-6">
        <label className="block text-sm font-medium text-gray-700 mb-1">Admin key</label>
        <div className="flex gap-2">
          <input type="password" value={adminKey} onChange={e => setAdminKey(e.target.value)}
            placeholder="hh_... (admin-scoped or bootstrap key)"
            className="flex-1 px-3 py-2 border border-gray-300 rounded-lg" />
          <button onClick={saveAdminKey} className="px-4 py-2 bg-primary-600 text-white rounded-lg">Save & load</button>
        </div>
        <p className="text-xs text-gray-500 mt-1">Stored in this browser only. Used to authenticate key management calls.</p>
      </div>

      {error && <div className="bg-red-50 border border-red-200 text-red-700 p-3 rounded-lg mb-4">{error}</div>}

      {newlyCreated && (
        <div className="bg-green-50 border border-green-200 p-4 rounded-lg mb-4">
          <p className="font-medium text-green-800 mb-1">Key created — copy it now, it will not be shown again:</p>
          <code className="block bg-white border p-2 rounded break-all">{newlyCreated}</code>
        </div>
      )}

      <div className="card mb-6">
        <h2 className="text-lg font-semibold mb-3">Create a key</h2>
        <form onSubmit={create} className="grid grid-cols-2 gap-3">
          <input required placeholder="Name" value={form.name}
            onChange={e => setForm({ ...form, name: e.target.value })}
            className="px-3 py-2 border border-gray-300 rounded-lg col-span-2" />
          <input placeholder="Scopes (csv): agents:run,workflows:run,embed,admin" value={form.scopes}
            onChange={e => setForm({ ...form, scopes: e.target.value })}
            className="px-3 py-2 border border-gray-300 rounded-lg col-span-2" />
          <input type="number" placeholder="Rate limit / min" value={form.rateLimitPerMin}
            onChange={e => setForm({ ...form, rateLimitPerMin: e.target.value })}
            className="px-3 py-2 border border-gray-300 rounded-lg" />
          <input placeholder="Referer allowlist (csv, embed keys)" value={form.refererAllowlist}
            onChange={e => setForm({ ...form, refererAllowlist: e.target.value })}
            className="px-3 py-2 border border-gray-300 rounded-lg" />
          <input placeholder="Bound agent id (embed)" value={form.boundAgentId}
            onChange={e => setForm({ ...form, boundAgentId: e.target.value })}
            className="px-3 py-2 border border-gray-300 rounded-lg" />
          <input placeholder="Bound workflow id (embed)" value={form.boundWorkflowId}
            onChange={e => setForm({ ...form, boundWorkflowId: e.target.value })}
            className="px-3 py-2 border border-gray-300 rounded-lg" />
          <button type="submit" className="col-span-2 px-4 py-2 bg-primary-600 text-white rounded-lg">Create key</button>
        </form>
      </div>

      <div className="card">
        <h2 className="text-lg font-semibold mb-3">Existing keys</h2>
        {keys.length === 0 ? <p className="text-sm text-gray-500 italic">No keys (or admin key not loaded).</p> : (
          <table className="w-full text-sm">
            <thead><tr className="text-left border-b">
              <th className="py-2">Name</th><th>Prefix</th><th>Scopes</th><th>Active</th><th>Last used</th><th></th>
            </tr></thead>
            <tbody>
              {keys.map(k => (
                <tr key={k.id} className="border-b">
                  <td className="py-2">{k.name}</td>
                  <td><code>{k.keyPrefix}…</code></td>
                  <td>{(k.scopes || []).join(', ')}</td>
                  <td>{k.active ? '✅' : '—'}</td>
                  <td>{k.lastUsedAt ? new Date(k.lastUsedAt).toLocaleString() : 'never'}</td>
                  <td>{k.active && <button onClick={() => revoke(k.id)} className="text-red-600">Revoke</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
