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

// Mã liên kết OMNI (US-22, T-22.1): khớp com.omniai.identity.LinkCodeService, api-contracts.md mục "Mã liên kết OMNI".

export type LinkChannel = 'SHOPEE' | 'TIKTOK'

export interface LinkCodeIssued {
  code: string
  expiresAt: string
  expiresInSeconds: number
}

/** Danh tính sàn đã gõ mã, chờ khách xác nhận. `channelUser` đã che, vd. `***abc`. */
export interface LinkRequest {
  id: number
  channel: LinkChannel
  channelUser: string
  claimedAt: string
  confirmBefore: string
}

export interface LinkedView {
  channel: LinkChannel
  channelUser: string
  linkedAt: string
}

export type LinkErrorCode = 'link_code_invalid' | 'link_code_expired' | 'link_locked' | 'identity_already_linked'

export interface LinkProblem extends ProblemDetail {
  code?: LinkErrorCode
}

/** ProblemDetail của /api/auth: có `code`; 401 sai mã có `attemptsLeft`; 423/429 có `retryAfterSeconds`. */
export interface AuthProblem extends ProblemDetail {
  code?: AuthErrorCode
  attemptsLeft?: number
  retryAfterSeconds?: number
}
