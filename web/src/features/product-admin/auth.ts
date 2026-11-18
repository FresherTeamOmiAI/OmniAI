// Tài khoản admin dạng HTTP Basic (SRS Q-01): ADMIN_USERNAME/ADMIN_PASSWORD của backend.
// Lưu trong sessionStorage nên đóng tab là mất. Tài khoản thật làm ở US-13.

const KEY = 'omniai.admin.basic'

/** Giá trị header Authorization. Mã hoá UTF-8 rồi base64 (btoa không nhận ký tự ngoài Latin-1). */
export function basicToken(username: string, password: string): string {
  const bytes = new TextEncoder().encode(`${username}:${password}`)
  let binary = ''
  bytes.forEach((b) => (binary += String.fromCharCode(b)))
  return `Basic ${btoa(binary)}`
}

export function loadToken(): string | null {
  try {
    return sessionStorage.getItem(KEY)
  } catch {
    return null
  }
}

export function saveToken(token: string | null): void {
  try {
    if (token) sessionStorage.setItem(KEY, token)
    else sessionStorage.removeItem(KEY)
  } catch {
    // Trình duyệt chặn storage: vẫn dùng được trong lần mở trang này.
  }
}
