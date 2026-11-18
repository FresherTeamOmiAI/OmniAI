import type { ChannelType, PolicyViolation } from '../../shared/types'

// Khớp backend conversation/ConversationView.java và MessageView.java (docs/specs/api-contracts.md, mục Hội thoại).

export interface ConversationView {
  id: number
  channel: ChannelType
  /** Id hội thoại phía kênh, vd. web:<session>, tiktok:<conversation_id>. */
  conversationId: string
  channelUserId: string
  customerId: number | null
  startedAt: string
  lastMessageAt: string
  messageCount: number
  /** Số câu bot bị policy can thiệp (có luật vi phạm hoặc bị chặn). */
  flaggedCount: number
  /** Số câu bot chuyển nhân viên. */
  handoffCount: number
  lastText: string | null
}

/** Chỉ kênh gửi async (TikTok) mới có; null với kênh đồng bộ. */
export type DeliveryStatus = 'RETRYING' | 'SENT' | 'FAILED'

export interface MessageView {
  id: number
  direction: 'IN' | 'OUT'
  messageId: string
  /** IN: tin khách. OUT: finalText, câu khách thực nhận. */
  text: string
  /** Chỉ OUT: câu gốc của AI Brain trước policy. */
  rawText: string | null
  violations: PolicyViolation[]
  blocked: boolean
  handoff: boolean
  deliveryStatus: DeliveryStatus | null
  deliveryAttempts: number
  createdAt: string
}

/** Bộ lọc danh sách, giữ trên URL. Ngày dạng yyyy-MM-dd (giờ VN), '' = không lọc. */
export interface InboxFilter {
  channel: '' | ChannelType
  from: string
  to: string
  hasViolation: boolean
  handoff: boolean
  page: number
}
