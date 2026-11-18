import { requestJson } from '../../shared/lib/http'
import type { Page } from '../../shared/types'
import type { ConversationView, InboxFilter, MessageView } from './types'

// API xem hội thoại (US-11, T-14.2). Cần Basic ADMIN như /api/admin/**.
const BASE = '/api/conversations'
export const LIST_SIZE = 20
export const MESSAGE_SIZE = 100

function call<T>(token: string, path: string): Promise<T> {
  // X-Requested-With: backend trả 401 không kèm WWW-Authenticate nên trình duyệt không bật hộp đăng nhập riêng.
  return requestJson(path, { headers: { Authorization: token, 'X-Requested-With': 'XMLHttpRequest' } })
}

export function listConversations(token: string, filter: InboxFilter): Promise<Page<ConversationView>> {
  const params = new URLSearchParams({ page: String(filter.page), size: String(LIST_SIZE) })
  if (filter.channel) params.set('channel', filter.channel)
  if (filter.from) params.set('from', filter.from)
  if (filter.to) params.set('to', filter.to)
  if (filter.hasViolation) params.set('hasViolation', 'true')
  if (filter.handoff) params.set('handoff', 'true')
  return call(token, `${BASE}?${params}`)
}

export function listMessages(token: string, conversationId: number, page: number): Promise<Page<MessageView>> {
  return call(token, `${BASE}/${conversationId}/messages?page=${page}&size=${MESSAGE_SIZE}`)
}
