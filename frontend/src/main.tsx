import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

// Only apply mirror transform if ?mirror=true is in the URL
// This way: http://localhost:8000 = normal, http://localhost:8000?mirror=true = mirrored for windshield
const params = new URLSearchParams(window.location.search);
if (params.get('mirror') === 'true') {
  import('./styles/mirror.css');
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
