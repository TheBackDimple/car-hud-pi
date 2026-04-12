import { useHudStore } from '../hooks/useHudStore';

export function FuelRange() {
  const mpg = useHudStore((s) => s.mpg);
  const range = useHudStore((s) => s.range);

  return (
    <div className="hud-widget hud-text">
      <div>
        <span className="hud-value">{mpg}</span>
        <span className="hud-label">MPG</span>
      </div>
      <div style={{ marginTop: '0.5vh' }}>
        <span className="hud-value">{range}</span>
        <span className="hud-label">Mi Range</span>
      </div>
    </div>
  );
}
