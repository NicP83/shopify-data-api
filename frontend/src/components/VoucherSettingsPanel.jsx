import { useEffect, useState } from 'react'

/**
 * Settings -> Vouchers: closing-voucher settings, exclusions, kill switch, and the
 * day's ledger + report. All calls go to /api/vouchers (the admin X-API-Key is added
 * by installFetchAuth). Enabling or loosening a limit also needs the owner key, which
 * is typed here per session and never stored.
 */

const NUMBER_FIELDS = [
  { key: 'dailyPool', label: 'Daily pool (AUD)', step: '1', help: 'Resets at midnight Melbourne time; unused money does not carry over.' },
  { key: 'maxAmount', label: 'Max voucher (AUD)', step: '5', help: 'Largest single voucher, in $5 steps.' },
  { key: 'maxPct', label: 'Max share of cart', step: '0.01', help: '0.10 = 10% of the full-price subtotal (rounded down to $5).' },
  { key: 'minSubtotal', label: 'Minimum cart (AUD)', step: '1', help: 'Full-price items only; also set on the Shopify code.' },
  { key: 'validityMinutes', label: 'Valid for (minutes)', step: '1', help: 'Set as the code expiry in Shopify.' },
  { key: 'holdoutPct', label: 'Holdout (% of eligible chats)', step: '1', help: 'Never offered, to measure lift.' },
  { key: 'maxPerIpPerDay', label: 'Max per IP per day', step: '1', help: 'Weak signal: households and offices share IPs.' },
]

const EXCLUSION_FIELDS = [
  { key: 'vendors', label: 'Excluded vendors' },
  { key: 'productTypes', label: 'Excluded product types' },
  { key: 'tags', label: 'Excluded tags' },
]

const STATUS_STYLES = {
  ISSUED: 'bg-blue-100 text-blue-800',
  REDEEMED: 'bg-green-100 text-green-800',
  EXPIRED: 'bg-gray-100 text-gray-700',
  REVOKED: 'bg-yellow-100 text-yellow-800',
  PENDING: 'bg-purple-100 text-purple-800',
  FAILED: 'bg-red-100 text-red-800',
}

function todayMelbourne() {
  try {
    return new Intl.DateTimeFormat('en-CA', { timeZone: 'Australia/Melbourne' }).format(new Date())
  } catch {
    return new Date().toISOString().slice(0, 10)
  }
}

function toLines(list) {
  return (list || []).join('\n')
}

function fromLines(text) {
  return text.split(/[\n,]/).map((s) => s.trim()).filter(Boolean)
}

function formatDateTime(iso) {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleString('en-AU', { timeZone: 'Australia/Melbourne' })
  } catch {
    return iso
  }
}

function money(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(2) : v
}

function formatTime(iso) {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleTimeString('en-AU', { timeZone: 'Australia/Melbourne', hour: 'numeric', minute: '2-digit' })
  } catch {
    return iso
  }
}

async function call(path, { method = 'GET', body, ownerKey } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (ownerKey) headers['X-Owner-Key'] = ownerKey
  const res = await fetch(`/api/vouchers${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined })
  let data = null
  try {
    data = await res.json()
  } catch {
    data = null
  }
  if (!res.ok) {
    const err = new Error((data && data.error) || `Request failed (${res.status})`)
    err.status = res.status
    err.data = data
    throw err
  }
  return data
}

export default function VoucherSettingsPanel() {
  const [settings, setSettings] = useState(null)
  const [form, setForm] = useState(null)
  const [exclusions, setExclusions] = useState({ vendors: '', productTypes: '', tags: '' })
  const [ownerKey, setOwnerKey] = useState('')
  const [date, setDate] = useState(todayMelbourne())
  const [report, setReport] = useState(null)
  const [ledger, setLedger] = useState([])
  const [overrides, setOverrides] = useState([])
  const [message, setMessage] = useState(null)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)

  const applySettings = (s) => {
    setSettings(s)
    setForm({
      enabled: s.enabled,
      dailyPool: s.dailyPool,
      maxAmount: s.maxAmount,
      maxPct: s.maxPct,
      minSubtotal: s.minSubtotal,
      validityMinutes: s.validityMinutes,
      holdoutPct: s.holdoutPct,
      maxPerIpPerDay: s.maxPerIpPerDay,
      requireEmail: s.requireEmail,
    })
    setExclusions({
      vendors: toLines(s.exclusions?.vendors),
      productTypes: toLines(s.exclusions?.productTypes),
      tags: toLines(s.exclusions?.tags),
    })
  }

  const loadDay = async (day) => {
    const [rep, led] = await Promise.all([
      call(`/report?date=${day}&store=false`),
      call(`/ledger?date=${day}`),
    ])
    setReport(rep)
    setLedger(led.entries || [])
  }

  const loadAll = async () => {
    setLoading(true)
    try {
      const [s, o] = await Promise.all([call('/settings'), call('/overrides')])
      applySettings(s)
      setOverrides(o.overrides || [])
      await loadDay(date)
    } catch (e) {
      setMessage({ type: 'error', text: e.message })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadAll()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const changeDate = async (day) => {
    setDate(day)
    try {
      await loadDay(day)
    } catch (e) {
      setMessage({ type: 'error', text: e.message })
    }
  }

  const save = async () => {
    setBusy(true)
    setMessage(null)
    try {
      const body = { ...form }
      for (const f of NUMBER_FIELDS) body[f.key] = Number(body[f.key])
      body.exclusions = {
        vendors: fromLines(exclusions.vendors),
        productTypes: fromLines(exclusions.productTypes),
        tags: fromLines(exclusions.tags),
      }
      const saved = await call('/settings', { method: 'PUT', body, ownerKey: ownerKey.trim() || undefined })
      applySettings(saved)
      setMessage({ type: 'success', text: 'Voucher settings saved.' })
    } catch (e) {
      const needs = e.data?.needs_owner_key
      setMessage({
        type: 'error',
        text: needs ? `Owner key needed to change: ${needs.join(', ')}. Enter it above and save again.` : e.message,
      })
    } finally {
      setBusy(false)
    }
  }

  const kill = async () => {
    if (!window.confirm('Turn vouchers OFF now? Codes already given out stay valid until they expire.')) return
    setBusy(true)
    try {
      applySettings(await call('/kill', { method: 'POST' }))
      setMessage({ type: 'success', text: 'Vouchers are OFF. The bot stops offering them within 15 seconds.' })
    } catch (e) {
      setMessage({ type: 'error', text: e.message })
    } finally {
      setBusy(false)
    }
  }

  const revoke = async (entry) => {
    if (!window.confirm(`Cancel code ${entry.code} now and return $${entry.amount} to the pool?`)) return
    try {
      await call(`/ledger/${entry.id}/revoke`, { method: 'POST' })
      await loadDay(date)
    } catch (e) {
      setMessage({ type: 'error', text: e.message })
    }
  }

  const checkScope = async () => {
    try {
      const r = await call('/scope-check', { method: 'POST' })
      const s = await call('/settings')
      applySettings(s)
      const missing = []
      if (!r.write_discounts) missing.push('write_discounts')
      if (s.requireEmail && !r.write_customers) missing.push('write_customers')
      setMessage({
        type: missing.length === 0 ? 'success' : 'error',
        text: missing.length === 0
          ? 'Shopify token has the scopes vouchers need.'
          : `Shopify token is missing ${missing.join(' and ')} (or the check failed).`,
      })
    } catch (e) {
      setMessage({ type: 'error', text: e.message })
    }
  }

  if (loading) {
    return <div className="text-gray-500 py-10 text-center">Loading vouchers...</div>
  }
  if (!settings || !form) {
    return (
      <div className="bg-white rounded-lg shadow p-6">
        {message && <div className="p-3 rounded bg-red-100 text-red-800 mb-4">{message.text}</div>}
        <button onClick={loadAll} className="px-4 py-2 bg-primary-600 text-white rounded-lg">Retry</button>
      </div>
    )
  }

  const status = settings.status || {}
  const scopeLabel = status.writeDiscountsScope === true ? 'granted' : status.writeDiscountsScope === false ? 'MISSING' : 'not checked'

  return (
    <div className="space-y-6">
      {message && (
        <div className={`p-4 rounded-lg ${message.type === 'success' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
          {message.text}
        </div>
      )}

      {/* Status + kill switch */}
      <div className="bg-white rounded-lg shadow p-6">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <h2 className="text-xl font-semibold">Closing vouchers</h2>
            <p className="text-sm text-gray-600 mt-1">
              Small one-hour vouchers the storefront bot can offer to close a hesitating sale. Every limit is enforced by the server.
            </p>
          </div>
          <div className="flex items-center gap-3">
            <span className={`px-3 py-1 rounded-full text-sm font-medium ${settings.enabled ? 'bg-green-100 text-green-800' : 'bg-gray-200 text-gray-700'}`}>
              {settings.enabled ? 'ON' : 'OFF'}
            </span>
            {settings.enabled && (
              <button onClick={kill} disabled={busy} className="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50">
                Kill switch: turn off now
              </button>
            )}
          </div>
        </div>
        <dl className="grid grid-cols-2 md:grid-cols-6 gap-4 mt-6 text-sm">
          <div><dt className="text-gray-500">Shopify write_discounts</dt><dd className={status.writeDiscountsScope === false ? 'text-red-700 font-medium' : ''}>{scopeLabel} <button onClick={checkScope} className="text-primary-600 underline ml-1">check</button></dd></div>
          <div><dt className="text-gray-500">Shopify write_customers</dt><dd className={status.writeCustomersScope === false && settings.requireEmail ? 'text-red-700 font-medium' : ''}>{status.writeCustomersScope === true ? 'granted' : status.writeCustomersScope === false ? 'MISSING' : 'not checked'}</dd></div>
          <div><dt className="text-gray-500">Owner key on server</dt><dd>{status.ownerKeyConfigured ? 'configured' : 'not set (overrides disabled)'}</dd></div>
          <div><dt className="text-gray-500">Paused by owner</dt><dd>{status.paused ? 'yes' : 'no'}</dd></div>
          <div><dt className="text-gray-500">Pool raise today</dt><dd>${status.poolRaiseToday ?? 0}</dd></div>
          <div><dt className="text-gray-500">Env feature flag</dt><dd>{status.featureAllowed ? 'allowed' : 'BLOCKED (VOUCHER_FEATURE_ALLOWED=false)'}</dd></div>
        </dl>
        {settings.updatedBy && (
          <p className="text-xs text-gray-500 mt-4">Last changed by {settings.updatedBy} {settings.updatedAt ? `at ${formatDateTime(settings.updatedAt)}` : ''}</p>
        )}
      </div>

      {/* Settings */}
      <div className="bg-white rounded-lg shadow p-6">
        <h2 className="text-xl font-semibold mb-1">Settings</h2>
        <p className="text-sm text-gray-600 mb-6">
          Lowering limits or excluding more needs only your admin key. Turning vouchers on, or raising any limit, also needs the owner key.
        </p>

        <label className="flex items-center gap-3 mb-6">
          <input
            type="checkbox"
            checked={!!form.enabled}
            onChange={(e) => setForm({ ...form, enabled: e.target.checked })}
            className="h-5 w-5"
          />
          <span className="font-medium">Vouchers enabled</span>
        </label>

        <label className="flex items-start gap-3 mb-6">
          <input
            type="checkbox"
            checked={!!form.requireEmail}
            onChange={(e) => setForm({ ...form, requireEmail: e.target.checked })}
            className="h-5 w-5 mt-0.5"
          />
          <span>
            <span className="font-medium">Require the customer's email</span>
            <span className="block text-sm text-gray-600">
              The bot asks which email they'll check out with, and the code only works for that customer in Shopify.
              Needs the write_customers scope. Turning this off needs the owner key.
            </span>
          </span>
        </label>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {NUMBER_FIELDS.map((f) => (
            <div key={f.key}>
              <label className="block text-sm font-medium text-gray-700 mb-1">{f.label}</label>
              <input
                type="number"
                step={f.step}
                value={form[f.key] ?? ''}
                onChange={(e) => setForm({ ...form, [f.key]: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg"
              />
              <p className="text-xs text-gray-500 mt-1">{f.help}</p>
            </div>
          ))}
        </div>

        <h3 className="text-lg font-semibold mt-8 mb-1">Exclusions</h3>
        <p className="text-sm text-gray-600 mb-4">One per line. Matching items never count toward the minimum and are never discounted. Items on sale are always excluded.</p>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {EXCLUSION_FIELDS.map((f) => (
            <div key={f.key}>
              <label className="block text-sm font-medium text-gray-700 mb-1">{f.label}</label>
              <textarea
                rows={5}
                value={exclusions[f.key]}
                onChange={(e) => setExclusions({ ...exclusions, [f.key]: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg font-mono text-sm"
              />
            </div>
          ))}
        </div>

        <div className="mt-8 grid grid-cols-1 md:grid-cols-2 gap-6 items-end">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Owner key (only to enable or loosen)</label>
            <input
              type="password"
              autoComplete="off"
              value={ownerKey}
              onChange={(e) => setOwnerKey(e.target.value)}
              placeholder="Not stored; cleared when you leave the page"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            />
          </div>
          <div className="flex justify-end">
            <button onClick={save} disabled={busy} className="px-6 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 disabled:opacity-50">
              {busy ? 'Saving...' : 'Save voucher settings'}
            </button>
          </div>
        </div>
      </div>

      {/* Report */}
      <div className="bg-white rounded-lg shadow p-6">
        <div className="flex flex-wrap items-center justify-between gap-4 mb-6">
          <h2 className="text-xl font-semibold">Daily report</h2>
          <input type="date" value={date} onChange={(e) => changeDate(e.target.value)} className="px-3 py-2 border border-gray-300 rounded-lg" />
        </div>
        {report && (
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <Stat label="Pool left" value={`$${report.pool_remaining} of $${report.pool_limit}`} />
            <Stat label="Offered (chats)" value={report.offered_sessions} />
            <Stat label="Holdout (chats)" value={report.holdout_sessions} />
            <Stat label="Issued" value={report.issued} />
            <Stat label="Redeemed" value={report.redeemed} />
            <Stat label="Expired / revoked" value={`${report.expired} / ${report.revoked}`} />
            <Stat label="Dollars spent" value={`$${report.dollars_spent}`} />
            <Stat label="Avg order with voucher" value={report.aov_with_voucher ? `$${report.aov_with_voucher}` : '-'} />
          </div>
        )}
      </div>

      {/* Ledger */}
      <div className="bg-white rounded-lg shadow p-6">
        <h2 className="text-xl font-semibold mb-4">Vouchers on {date}</h2>
        {ledger.length === 0 ? (
          <p className="text-gray-500 text-sm">None.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead>
                <tr className="text-left text-gray-500 border-b">
                  <th className="py-2 pr-4">Issued</th>
                  <th className="py-2 pr-4">Code</th>
                  <th className="py-2 pr-4">Amount</th>
                  <th className="py-2 pr-4">Cart</th>
                  <th className="py-2 pr-4">Status</th>
                  <th className="py-2 pr-4">Order</th>
                  <th className="py-2 pr-4">Reason</th>
                  <th className="py-2"></th>
                </tr>
              </thead>
              <tbody>
                {ledger.map((e) => (
                  <tr key={e.id} className="border-b last:border-0">
                    <td className="py-2 pr-4 whitespace-nowrap">{formatTime(e.issuedAt)}</td>
                    <td className="py-2 pr-4 font-mono">{e.code}{e.lockedToCustomer && <span className="ml-1 text-xs text-gray-500" title="Only works for the customer's email">🔒</span>}</td>
                    <td className="py-2 pr-4">${money(e.amount)}</td>
                    <td className="py-2 pr-4">${money(e.cartSubtotal)}</td>
                    <td className="py-2 pr-4">
                      <span className={`px-2 py-0.5 rounded text-xs font-medium ${STATUS_STYLES[e.status] || ''}`}>{e.status}</span>
                    </td>
                    <td className="py-2 pr-4">{e.orderTotal ? `$${money(e.orderTotal)}` : ''}</td>
                    <td className="py-2 pr-4 max-w-xs truncate" title={e.reason || ''}>{e.reason}</td>
                    <td className="py-2">
                      {e.status === 'ISSUED' && (
                        <button onClick={() => revoke(e)} className="text-red-600 hover:underline">Cancel</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Overrides (read-only; created by the owner command) */}
      <div className="bg-white rounded-lg shadow p-6">
        <h2 className="text-xl font-semibold mb-1">Owner overrides</h2>
        <p className="text-sm text-gray-600 mb-4">
          Created only with the owner command (POST /api/vouchers/override with both keys). See docs/vouchers/VOUCHERS.md.
        </p>
        {overrides.length === 0 ? (
          <p className="text-gray-500 text-sm">None.</p>
        ) : (
          <ul className="divide-y text-sm">
            {overrides.map((o) => (
              <li key={o.id} className="py-2 flex flex-wrap gap-x-4">
                <span className="font-medium">{o.type}</span>
                {o.value != null && <span>${o.value}</span>}
                {o.target && <span className="font-mono">{o.target}</span>}
                <span className="text-gray-600">{o.reason}</span>
                <span className="text-gray-500">by {o.createdBy}</span>
                <span className={o.active ? 'text-green-700' : 'text-gray-400'}>
                  {o.active ? `active until ${formatDateTime(o.expiresAt)}` : 'ended'}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

function Stat({ label, value }) {
  return (
    <div className="border border-gray-200 rounded-lg p-4">
      <div className="text-xs text-gray-500">{label}</div>
      <div className="text-lg font-semibold mt-1">{value}</div>
    </div>
  )
}
