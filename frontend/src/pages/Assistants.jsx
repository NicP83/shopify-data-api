import { useState, useEffect } from 'react'
import api from '../services/api'

/**
 * Manage named chatbot personas ("assistants"). Each persona has its own
 * prompt, model, and linked agents/workflows, and can be chatted with via
 * POST /api/v1/chat (with a "chat"-scoped API key) using its slug.
 * The storefront chatbot keeps using the global config and is unaffected.
 */
export default function Assistants() {
  const [profiles, setProfiles] = useState([])
  const [agents, setAgents] = useState([])
  const [workflows, setWorkflows] = useState([])
  const [editing, setEditing] = useState(null)
  const [message, setMessage] = useState(null)

  const load = async () => {
    try {
      const [p, a, w] = await Promise.all([
        api.getProfiles(),
        api.getAgents(true).catch(() => ({ data: [] })),
        api.getWorkflows(true).catch(() => ({ data: [] }))
      ])
      setProfiles(p.data || [])
      setAgents(a.data || [])
      setWorkflows(w.data || [])
    } catch (e) {
      setMessage({ type: 'error', text: 'Failed to load: ' + e.message })
    }
  }

  useEffect(() => { load() }, [])

  const blank = () => ({
    slug: '', displayName: '', customInstructions: '', modelName: '',
    linkedAgentIds: [], linkedWorkflowIds: [],
    includeCartLinks: true, includeProductLinks: true, showPrices: true, enableProductSearch: true
  })

  const save = async () => {
    if (!editing.slug || !/^[a-z0-9-]+$/.test(editing.slug)) {
      setMessage({ type: 'error', text: 'Slug is required (lowercase letters, numbers, hyphens).' }); return
    }
    try {
      await api.saveProfile(editing)
      setMessage({ type: 'success', text: `Saved "${editing.displayName || editing.slug}".` })
      setEditing(null); load()
    } catch (e) {
      setMessage({ type: 'error', text: e.response?.data?.error || e.message })
    }
  }

  const remove = async (slug) => {
    if (!confirm(`Delete assistant "${slug}"?`)) return
    await api.deleteProfile(slug); load()
  }

  const toggle = (list, id) =>
    list.includes(id) ? list.filter(x => x !== id) : [...list, id]

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <div className="flex justify-between items-center mb-4">
        <h1 className="text-2xl font-bold">Assistants (Personas)</h1>
        {!editing && <button onClick={() => setEditing(blank())}
          className="px-4 py-2 bg-primary-600 text-white rounded-lg">+ New assistant</button>}
      </div>

      {message && (
        <div className={`p-3 rounded-lg mb-4 ${message.type === 'error'
          ? 'bg-red-50 border border-red-200 text-red-700'
          : 'bg-green-50 border border-green-200 text-green-800'}`}>{message.text}</div>
      )}

      {editing ? (
        <div className="card space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium mb-1">Slug (URL id)</label>
              <input value={editing.slug} disabled={profiles.some(p => p.slug === editing.slug)}
                onChange={e => setEditing({ ...editing, slug: e.target.value })}
                placeholder="trade-support" className="w-full px-3 py-2 border border-gray-300 rounded-lg" />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Display name</label>
              <input value={editing.displayName || ''}
                onChange={e => setEditing({ ...editing, displayName: e.target.value })}
                placeholder="Trade Support Bot" className="w-full px-3 py-2 border border-gray-300 rounded-lg" />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium mb-1">Custom instructions (persona prompt)</label>
            <textarea rows="4" value={editing.customInstructions || ''}
              onChange={e => setEditing({ ...editing, customInstructions: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
              placeholder="You are a trade-desk assistant for wholesale customers..." />
          </div>

          <div>
            <label className="block text-sm font-medium mb-1">Model (optional)</label>
            <input value={editing.modelName || ''}
              onChange={e => setEditing({ ...editing, modelName: e.target.value })}
              placeholder="claude-sonnet-4-6" className="w-full px-3 py-2 border border-gray-300 rounded-lg" />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-2">Linked agents</label>
              <div className="max-h-48 overflow-y-auto border rounded-lg p-2 space-y-1">
                {agents.map(a => (
                  <label key={a.id} className="flex items-center gap-2 text-sm">
                    <input type="checkbox" checked={editing.linkedAgentIds?.includes(a.id) || false}
                      onChange={() => setEditing({ ...editing, linkedAgentIds: toggle(editing.linkedAgentIds || [], a.id) })} />
                    {a.name}
                  </label>
                ))}
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium mb-2">Linked workflows</label>
              <div className="max-h-48 overflow-y-auto border rounded-lg p-2 space-y-1">
                {workflows.map(w => (
                  <label key={w.id} className="flex items-center gap-2 text-sm">
                    <input type="checkbox" checked={editing.linkedWorkflowIds?.includes(w.id) || false}
                      onChange={() => setEditing({ ...editing, linkedWorkflowIds: toggle(editing.linkedWorkflowIds || [], w.id) })} />
                    {w.name}
                  </label>
                ))}
              </div>
            </div>
          </div>

          <div className="flex gap-2">
            <button onClick={save} className="px-4 py-2 bg-primary-600 text-white rounded-lg">Save</button>
            <button onClick={() => setEditing(null)} className="px-4 py-2 border rounded-lg">Cancel</button>
          </div>
        </div>
      ) : (
        <div className="card">
          {profiles.length === 0 ? (
            <p className="text-sm text-gray-500 italic">No assistants yet. Create one to run a second, differently-configured chatbot.</p>
          ) : (
            <table className="w-full text-sm">
              <thead><tr className="text-left border-b">
                <th className="py-2">Name</th><th>Slug</th><th>Agents</th><th>Workflows</th><th></th>
              </tr></thead>
              <tbody>
                {profiles.map(p => (
                  <tr key={p.slug} className="border-b">
                    <td className="py-2">{p.displayName || p.slug}</td>
                    <td><code>{p.slug}</code></td>
                    <td>{(p.linkedAgentIds || []).length}</td>
                    <td>{(p.linkedWorkflowIds || []).length}</td>
                    <td className="text-right">
                      <button onClick={() => setEditing(p)} className="text-primary-600 mr-3">Edit</button>
                      <button onClick={() => remove(p.slug)} className="text-red-600">Delete</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <p className="text-xs text-gray-500 mt-3">
            Chat with an assistant from any system: <code>POST /api/v1/chat</code> with a chat-scoped API key and
            <code> {'{'} "message": "...", "persona": "&lt;slug&gt;" {'}'}</code>.
          </p>
        </div>
      )}
    </div>
  )
}
