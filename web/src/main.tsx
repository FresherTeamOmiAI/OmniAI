// CSS nạp trước App để thứ tự là: font → token → nền chung → CSS của từng feature (US-56).
// Font tự host bằng @fontsource (có bộ chữ tiếng Việt), demo không cần mạng tới Google Fonts.
import '@fontsource/be-vietnam-pro/400.css'
import '@fontsource/be-vietnam-pro/500.css'
import '@fontsource/be-vietnam-pro/600.css'
import '@fontsource/be-vietnam-pro/700.css'
import '@fontsource-variable/playfair-display/wght.css'
import './shared/ui/tokens.css'
import './shared/ui/global.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './app/App'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
