// Kiểu dùng chung nhiều feature, khớp backend (com.omniai.common, policy).

export type ChannelType = 'WEB' | 'TELEGRAM' | 'MESSENGER' | 'SHOPEE' | 'TIKTOK'

export interface PolicyViolation {
  ruleId: string
  action: 'BLOCK' | 'REDACT' | 'REPLACE'
  matched: string
}

export interface BotReply {
  messageId: string
  channel: ChannelType
  rawText: string
  finalText: string
  violations: PolicyViolation[]
  blocked: boolean
  handoff: boolean
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
}
