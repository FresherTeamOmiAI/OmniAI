import type { ProblemDetail } from '../../shared/types'

// Khớp com.omniai.identity.AuthController (T-13.1, docs/specs/api-contracts.md mục Đăng nhập người mua).

export interface CustomerView {
  id: number
  email: string
  displayName: string | null
}

export interface OtpIssued {
  expiresInSeconds: number
  resendAfterSeconds: number
}

export interface LoginView {
  token: string
  expiresAt: string
  customer: CustomerView
  newCustomer: boolean
}

export type AuthErrorCode = 'otp_invalid' | 'otp_expired' | 'otp_locked' | 'otp_rate_limited' | 'otp_send_failed'

/** ProblemDetail của /api/auth: có `code`; 401 sai mã có `attemptsLeft`; 423/429 có `retryAfterSeconds`. */
export interface AuthProblem extends ProblemDetail {
  code?: AuthErrorCode
  attemptsLeft?: number
  retryAfterSeconds?: number
}
