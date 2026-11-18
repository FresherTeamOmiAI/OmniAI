import { useSyncExternalStore } from 'react'
import { ApiError } from '../../shared/lib/http'
import { fetchMe } from './api'
import type { CustomerView, LoginView } from './types'

// Phiên đăng nhập của người mua (US-13). Lưu localStorage để mở tab mới vẫn còn đăng nhập.
// localStorage có thể bị chặn (chế độ riêng tư): khi đó phiên chỉ sống trong bộ nhớ của tab.

export interface CustomerSession {
  token: string
  expiresAt: string
  customer: CustomerView
}

const STORAGE_KEY = 'omniai.customer.session'
const listeners = new Set<() => void>()
let current: CustomerSession | null = load()

function isExpired(session: CustomerSession): boolean {
  return new Date(session.expiresAt).getTime() <= Date.now()
}

function load(): CustomerSession | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as CustomerSession
    return session.token && !isExpired(session) ? session : null
  } catch {
    return null
  }
}

function update(next: CustomerSession | null) {
  current = next
  try {
    if (next) localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Bỏ qua: không lưu được thì vẫn giữ trong bộ nhớ.
  }
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  // Đăng nhập/đăng xuất ở tab khác.
  const onStorage = (event: StorageEvent) => {
    if (event.key === STORAGE_KEY) {
      current = load()
      listener()
    }
  }
  window.addEventListener('storage', onStorage)
  return () => {
    listeners.delete(listener)
    window.removeEventListener('storage', onStorage)
  }
}

export function startSession(login: LoginView) {
  update({ token: login.token, expiresAt: login.expiresAt, customer: login.customer })
}

export function logout() {
  update(null)
}

export function useCustomerSession(): CustomerSession | null {
  return useSyncExternalStore(subscribe, () => current)
}

/** Kiểm lại token với backend khi mở trang: hết hạn hoặc khách không còn thì đăng xuất. Lỗi mạng thì giữ nguyên. */
export async function refreshSession(): Promise<void> {
  const session = current
  if (!session) return
  if (isExpired(session)) {
    logout()
    return
  }
  try {
    const customer = await fetchMe(session.token)
    if (current?.token === session.token) update({ ...session, customer })
  } catch (err) {
    if (err instanceof ApiError && err.status === 401 && current?.token === session.token) logout()
  }
}
