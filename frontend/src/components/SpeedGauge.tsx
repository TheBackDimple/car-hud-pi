import { useHudStore } from '../hooks/useHudStore';

export function SpeedGauge() {
  const speed = useHudStore((s) => s.speed);
  const gpsSpeed = useHudStore((s) => s.gpsSpeed);
  const speedLimit = useHudStore((s) => s.speedLimit);

  const speedNum = parseInt(speed, 10);
  const limitNum = parseInt(speedLimit, 10);
  const overLimit =
    Boolean(speedLimit) &&
    !Number.isNaN(speedNum) &&
    !Number.isNaN(limitNum) &&
    speedNum > limitNum;

  return (
    <div
      className={`hud-widget speed-gauge hud-text${overLimit ? ' speed-over-limit' : ''}`}
    >
      <span className="speed-value">{speed}</span>
      <span className="speed-label">MPH</span>
      {gpsSpeed && gpsSpeed !== '--' && gpsSpeed !== speed && (
        <span className="speed-gps">GPS: {gpsSpeed}</span>
      )}
      {speedLimit && speedLimit !== '' && (
        <span className="speed-limit">LIMIT {speedLimit}</span>
      )}
    </div>
  );
}
