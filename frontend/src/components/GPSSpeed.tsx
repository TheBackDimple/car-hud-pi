import { useHudStore } from '../hooks/useHudStore';

export function GPSSpeed() {
  const gpsSpeed = useHudStore((s) => s.gpsSpeed);

  return (
    <div className="hud-widget hud-text">
      <span className="hud-value">{gpsSpeed}</span>
      <span className="hud-label">GPS MPH</span>
    </div>
  );
}
