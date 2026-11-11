import { useState } from 'react'
import type { BotReply, ChannelType } from '../../shared/types'
import { simulate } from './api'

const CHANNELS: ChannelType[] = ['WEB', 'SHOPEE', 'TIKTOK']

// Admin > Thử luật kênh. Owner: HungDQ (policy) + TuTM (admin).
export function PolicyPlaygroundPage() {
  const [text, setText] = useState('Web shop có rẻ hơn không? cho mình xin link')
  const [results, setResults] = useState<BotReply[]>([])
  const [error, setError] = useState<string | null>(null)

  const run = async (event: React.FormEvent) => {
    event.preventDefault()
    try {
      setResults(await Promise.all(CHANNELS.map((c) => simulate(c, text))))
      setError(null)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <main className="admin">
      <h2>Thử luật kênh</h2>
      <p className="muted">Cùng một câu hỏi gửi vào 4 kênh. Cột "AI trả lời" là câu gốc, cột "Khách nhận" là câu sau khi qua luật của kênh.</p>
      <form className="sim-form" onSubmit={run}>
        <label htmlFor="sim-text">Câu hỏi của khách</label>
        <input id="sim-text" value={text} onChange={(e) => setText(e.target.value)} />
        <button type="submit">Gửi vào 4 kênh</button>
      </form>
      {error && <p className="error">{error}</p>}
      {results.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Kênh</th>
                <th>AI trả lời</th>
                <th>Khách nhận</th>
                <th>Luật kích hoạt</th>
              </tr>
            </thead>
            <tbody>
              {results.map((r) => (
                <tr key={r.channel} className={r.blocked ? 'row-blocked' : undefined}>
                  <td>{r.channel}</td>
                  <td>{r.rawText}</td>
                  <td>{r.finalText}</td>
                  <td>{r.violations.length === 0 ? '—' : r.violations.map((v) => `${v.ruleId} (${v.action})`).join(', ')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  )
}
