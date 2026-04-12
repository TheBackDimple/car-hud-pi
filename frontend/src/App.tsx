import { HudCanvas } from './components/HudCanvas';
import { useWebSocket } from './hooks/useWebSocket';
import './styles/hud.css';

function App() {
  useWebSocket();

  return <HudCanvas />;
}

export default App;
