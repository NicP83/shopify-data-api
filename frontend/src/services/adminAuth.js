/**
 * Admin API key for the backend's AdminAuthFilter.
 *
 * The key lives in localStorage (same slot the API Keys page already uses) and
 * is sent as X-API-Key on every request to our own /api/** routes, whether made
 * through axios (api.js) or plain fetch (adminApi.js and the pages that call
 * fetch directly). A 401 from the backend fires AUTH_REQUIRED_EVENT so the
 * AdminKeyPrompt banner can ask for the key.
 */

const STORAGE_KEY = 'hh_admin_key'
export const AUTH_REQUIRED_EVENT = 'hh-admin-auth-required'

export function getAdminKey() {
  try {
    return localStorage.getItem(STORAGE_KEY) || ''
  } catch {
    return ''
  }
}

export function setAdminKey(key) {
  try {
    if (key) localStorage.setItem(STORAGE_KEY, key)
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    // storage unavailable (private mode) - key just won't persist
  }
}

const API_BASE = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '')

/** True for requests to this backend's /api/** (relative, same origin, or VITE_API_URL). */
export function isOwnApiUrl(url) {
  if (!url) return false
  if (url.startsWith('/api/')) return true
  try {
    const u = new URL(url, window.location.origin)
    if (!u.pathname.startsWith('/api/')) return false
    if (u.origin === window.location.origin) return true
    if (API_BASE && url.startsWith(API_BASE)) return true
    return u.origin === 'http://localhost:8080' // dev backend
  } catch {
    return false
  }
}

export function notifyAuthRequired() {
  window.dispatchEvent(new Event(AUTH_REQUIRED_EVENT))
}

let fetchInstalled = false

/** Wrap window.fetch so own-API calls carry X-API-Key and 401s raise the prompt. */
export function installFetchAuth() {
  if (fetchInstalled) return
  fetchInstalled = true
  const originalFetch = window.fetch.bind(window)

  window.fetch = async (input, init = {}) => {
    const url = typeof input === 'string' ? input : input?.url
    if (!isOwnApiUrl(url)) return originalFetch(input, init)

    const key = getAdminKey()
    let nextInit = init
    if (key) {
      const headers = new Headers(init.headers || (typeof input !== 'string' ? input.headers : undefined))
      if (!headers.has('X-API-Key')) headers.set('X-API-Key', key)
      nextInit = { ...init, headers }
    }
    const response = await originalFetch(input, nextInit)
    if (response.status === 401) notifyAuthRequired()
    return response
  }
}

/** Attach the same behaviour to an axios instance. */
export function installAxiosAuth(axiosInstance) {
  axiosInstance.interceptors.request.use((config) => {
    const key = getAdminKey()
    if (key && !config.headers['X-API-Key']) config.headers['X-API-Key'] = key
    return config
  })
  axiosInstance.interceptors.response.use(
    (response) => response,
    (error) => {
      if (error?.response?.status === 401) notifyAuthRequired()
      return Promise.reject(error)
    }
  )
}
