import { postJson } from '../../shared/lib/http'
import type { BotReply, ChannelType } from '../../shared/types'

export function simulate(channel: ChannelType, text: string, messageId?: string): Promise<BotReply> {
  return postJson(`/api/simulator/${channel.toLowerCase()}/messages`, { userId: 'admin-tester', text, messageId })
}
