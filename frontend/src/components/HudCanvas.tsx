import { DynamicGrid } from '../layouts/DynamicGrid';
import { useHudStore } from '../hooks/useHudStore';

export function HudCanvas() {
  const isConnected = useHudStore((s) => s.isConnected);

  return (
    <div className="hud-root">
      <div
        className={`connection-status ${!isConnected ? 'disconnected' : ''}`}
      >
        {isConnected ? '● Connected' : '○ Disconnected'}
      </div>
      <DynamicGrid />
    </div>
  );
}
