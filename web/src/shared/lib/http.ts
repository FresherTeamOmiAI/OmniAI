// Gọi API backend. Dev: /api được Vite proxy sang http://localhost:8080.

export async function getJson<T>(path: string): Promise<T> {
  const res = await fetch(path)
  if (!res.ok) throw new Error(`Lỗi tải dữ liệu (HTTP ${res.status})`)
  return res.json()
}

export async function postJson<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`Lỗi gửi dữ liệu (HTTP ${res.status})`)
  return res.json()
}

export function wsUrl(path: string): string {
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${protocol}://${location.host}${path}`
}
