import { useHudStore } from '../hooks/useHudStore';

export function OBDPanel() {
  const rpm = useHudStore((s) => s.rpm);
  const coolantTemp = useHudStore((s) => s.coolantTemp);

  return (
    <div className="hud-widget hud-text">
      <div>
        <span className="hud-value">{rpm}</span>
        <span className="hud-label">RPM</span>
      </div>
      <div style={{ marginTop: '0.5vh' }}>
        <span className="hud-value">{coolantTemp}</span>
        <span className="hud-label">°F</span>
      </div>
    </div>
  );
}
