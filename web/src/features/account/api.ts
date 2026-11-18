import { postJson, requestJson } from '../../shared/lib/http'
import type { CustomerView, LoginView, OtpIssued } from './types'

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
