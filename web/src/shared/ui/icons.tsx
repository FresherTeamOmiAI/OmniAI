// Icon SVG dùng chung (ui-ux-pro-max: dùng SVG, không dùng emoji làm icon).
// Luôn aria-hidden: nút chứa icon phải có chữ hoặc aria-label.
const base = {
  width: 24,
  height: 24,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
  focusable: false,
} as const

export function ChatIcon() {
  return (
    <svg {...base}>
      <path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1.1-4.6A8 8 0 1 1 21 12Z" />
      <path d="M8.5 12h.01M12 12h.01M15.5 12h.01" />
    </svg>
  )
}

export function SparkleIcon() {
  return (
    <svg {...base}>
      <path d="M12 3.5 13.9 8l4.6 1.9-4.6 1.9L12 16.3l-1.9-4.5L5.5 9.9 10.1 8 12 3.5Z" />
      <path d="M18.5 15.5 19.3 17.5l2 .8-2 .8-.8 2-.8-2-2-.8 2-.8.8-2Z" />
    </svg>
  )
}

export function ChevronDownIcon() {
  return (
    <svg {...base}>
      <path d="m6 9 6 6 6-6" />
    </svg>
  )
}

export function BottleIcon() {
  return (
    <svg {...base}>
      <path d="M10 2.5h4v3h-4z" />
      <path d="M8.5 5.5h7l1.5 3.5v10a2.5 2.5 0 0 1-2.5 2.5h-5A2.5 2.5 0 0 1 7 19V9l1.5-3.5Z" />
      <path d="M9.5 13h5" />
    </svg>
  )
}
