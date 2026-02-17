import { useHudStore } from '../hooks/useHudStore';

export function SpeedGauge() {
  const speed = useHudStore((s) => s.speed);
  const gpsSpeed = useHudStore((s) => s.gpsSpeed);

  return (
    <div className="hud-widget speed-gauge hud-text">
      <span className="hud-value">{speed}</span>
      <span className="hud-label">MPH</span>
      {gpsSpeed !== '--' && (
        <span className="hud-text-dim" style={{ fontSize: '0.8em', marginTop: '0.25vh' }}>
          GPS: {gpsSpeed}
        </span>
      )}
    </div>
  );
}
