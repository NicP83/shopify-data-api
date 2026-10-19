import { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { AUTH_REQUIRED_EVENT, getAdminKey, setAdminKey } from '../services/adminAuth'

/**
 * Banner asking for the admin API key. Appears when any admin API call returns
 * 401 (missing or wrong key); saving stores the key and reloads the page so
 * every view refetches with it.
 */
export default function AdminKeyPrompt() {
  const [visible, setVisible] = useState(false)
  const [value, setValue] = useState('')
  const { pathname } = useLocation()

  useEffect(() => {
    const show = () => setVisible(true)
    window.addEventListener(AUTH_REQUIRED_EVENT, show)
    return () => window.removeEventListener(AUTH_REQUIRED_EVENT, show)
  }, [])

  // Public workflow pages (/workflow/execute/:id, /workflow/chat/:id) are for
  // visitors without a key; the nav's admin calls 401 there harmlessly.
  if (!visible || pathname.startsWith('/workflow/')) return null

  const save = (e) => {
    e.preventDefault()
    if (!value.trim()) return
    setAdminKey(value.trim())
    window.location.reload()
  }

  return (
    <div className="bg-amber-50 border-b border-amber-300">
      <form onSubmit={save} className="container mx-auto px-4 py-3 flex flex-wrap items-center gap-3">
        <span className="text-sm text-amber-900">
          {getAdminKey() ? 'Your admin API key was rejected.' : 'An admin API key is required.'} Paste it to continue:
        </span>
        <input
          type="password"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder="hh_..."
          autoComplete="off"
          className="flex-1 min-w-[200px] px-3 py-1.5 border border-amber-300 rounded text-sm"
        />
        <button type="submit" className="px-3 py-1.5 bg-amber-600 text-white rounded text-sm hover:bg-amber-700">
          Save key
        </button>
      </form>
    </div>
  )
}
