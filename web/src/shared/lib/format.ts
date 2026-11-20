export const formatVnd = (value: number) => value.toLocaleString('vi-VN') + 'đ'

/** Mốc giờ của hub (khuyến mãi, lịch sử giá) luôn theo giờ Việt Nam, kể cả khi máy admin đặt múi giờ khác (UC-21). */
const VN_PARTS = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  hourCycle: 'h23',
})

function vnParts(iso: string): Record<string, string> {
  return Object.fromEntries(VN_PARTS.formatToParts(new Date(iso)).map((p) => [p.type, p.value]))
}

/** "12/12/2026 20:00" theo giờ Việt Nam. */
export function formatVnDateTime(iso: string): string {
  const p = vnParts(iso)
  return `${p.day}/${p.month}/${p.year} ${p.hour}:${p.minute}`
}

/**
 * ISO từ backend ("2026-12-12T20:00:00+07:00") → giá trị cho ô datetime-local ("2026-12-12T20:00") theo giờ Việt Nam.
 * Backend hiểu chuỗi không offset là giờ Việt Nam, nên gửi lại nguyên giá trị ô là đúng.
 */
export function toDateTimeLocal(iso: string): string {
  const p = vnParts(iso)
  return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}`
}
