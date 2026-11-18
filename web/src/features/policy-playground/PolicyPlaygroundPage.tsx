import { useState } from 'react'
import type { BotReply, ChannelType } from '../../shared/types'
import { simulate } from './api'

const CHANNELS: ChannelType[] = ['WEB', 'SHOPEE', 'TIKTOK']

// Admin > Thử luật kênh. Owner: HungDQ (policy) + TuTM (admin).
export function PolicyPlaygroundPage() {
  const [text, setText] = useState('Web shop có rẻ hơn không? cho mình xin link')
  const [results, setResults] = useState<BotReply[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const run = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    try {
      setResults(await Promise.all(CHANNELS.map((c) => simulate(c, text))))
      setError(null)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <main id="main" tabIndex={-1} className="admin policy">
      <header className="page-head">
        <h1>Thử luật kênh</h1>
        <p className="page-lead">
          Cùng một câu hỏi gửi vào {CHANNELS.length} kênh. Cột "AI trả lời" là câu gốc, cột "Khách nhận" là câu sau khi qua luật của kênh.
        </p>
      </header>
      <form className="sim-form" onSubmit={run}>
        <label htmlFor="sim-text">Câu hỏi của khách</label>
        <input id="sim-text" value={text} onChange={(e) => setText(e.target.value)} />
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? 'Đang gửi…' : `Gửi vào ${CHANNELS.length} kênh`}
        </button>
      </form>
      {error && <p className="error" role="alert">{error}</p>}
      {results.length > 0 && (
        <div className={busy ? 'table-wrap is-loading' : 'table-wrap'} aria-busy={busy}>
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
                  <td className="policy-channel">{r.channel}</td>
                  <td>{r.rawText}</td>
                  <td>
                    <div className="policy-final">
                      {r.blocked && <span className="badge badge-warn">Bị chặn</span>}
                      <span>{r.finalText}</span>
                    </div>
                  </td>
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
