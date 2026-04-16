import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import './styles/mirror.css'
import App from './App.tsx'

// Default: ?mirror=true = windshield (matches Pi start.sh). Android can override via WebSocket.
const params = new URLSearchParams(window.location.search)
document.body.classList.toggle(
  'hud-mirror-on',
  params.get('mirror') === 'true'
)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
