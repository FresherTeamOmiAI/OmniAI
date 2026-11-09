// Gọi API backend. Dev: /api được Vite proxy sang http://localhost:8080.
import type { ProblemDetail } from '../types'

/** Lỗi HTTP. `problem` có khi backend trả ProblemDetail (application/problem+json). */
export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail | null

  constructor(status: number, problem: ProblemDetail | null, fallback: string) {
    super(problem?.detail ?? problem?.title ?? fallback)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH'
  body?: unknown
  headers?: Record<string, string>
}

export async function requestJson<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, headers = {} } = options
  const res = await fetch(path, {
    method,
    headers: body === undefined ? headers : { 'Content-Type': 'application/json', ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!res.ok) {
    const problem = await readProblem(res)
    const fallback = method === 'GET' ? `Lỗi tải dữ liệu (HTTP ${res.status})` : `Lỗi gửi dữ liệu (HTTP ${res.status})`
    throw new ApiError(res.status, problem, fallback)
  }
  return res.json()
}

export function getJson<T>(path: string): Promise<T> {
  return requestJson(path)
}

export function postJson<T>(path: string, body: unknown): Promise<T> {
  return requestJson(path, { method: 'POST', body })
}

async function readProblem(res: Response): Promise<ProblemDetail | null> {
  if (!(res.headers.get('Content-Type') ?? '').includes('json')) return null
  try {
    return (await res.json()) as ProblemDetail
  } catch {
    return null
  }
}

export function wsUrl(path: string): string {
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${protocol}://${location.host}${path}`
}
