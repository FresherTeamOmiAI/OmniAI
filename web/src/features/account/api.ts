import { postJson, requestJson } from '../../shared/lib/http'
import type { CustomerView, LinkCodeIssued, LinkedView, LinkRequest, LoginView, OtpIssued } from './types'

/** Gửi OTP 6 số tới email (US-13). Form bắt buộc tick đồng ý xử lý dữ liệu cá nhân nên luôn gửi consent=true. */
export function requestOtp(email: string): Promise<OtpIssued> {
  return postJson('/api/auth/otp', { email, consent: true })
}

export function verifyOtp(email: string, code: string): Promise<LoginView> {
  return postJson('/api/auth/otp/verify', { email, code })
}

export function fetchMe(token: string): Promise<CustomerView> {
  return requestJson('/api/me', { headers: { Authorization: `Bearer ${token}` } })
}

const bearer = (token: string) => ({ Authorization: `Bearer ${token}` })

/** Lấy mã OMNI-XXXXXX (T-22.2). Mã cũ chưa ai gõ bị thu hồi. */
export function issueLinkCode(token: string): Promise<LinkCodeIssued> {
  return requestJson('/api/me/link-codes', { method: 'POST', headers: bearer(token) })
}

/** Danh tính sàn đã gõ mã, đang chờ khách xác nhận (T-22.4). */
export function fetchLinkRequests(token: string): Promise<LinkRequest[]> {
  return requestJson('/api/me/link-requests', { headers: bearer(token) })
}

/** Chỉ gọi khi khách đã tick đồng ý trên trang nên luôn gửi consent=true. */
export function confirmLink(token: string, id: number): Promise<LinkedView> {
  return requestJson(`/api/me/link-requests/${id}/confirm`, { method: 'POST', headers: bearer(token), body: { consent: true } })
}

export function rejectLink(token: string, id: number): Promise<void> {
  return requestJson(`/api/me/link-requests/${id}/reject`, { method: 'POST', headers: bearer(token) })
}
