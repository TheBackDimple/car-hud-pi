import { useHudStore } from '../hooks/useHudStore';

export function FuelRange() {
  const mpg = useHudStore((s) => s.mpg);
  const range = useHudStore((s) => s.range);
  const fuelLevel = useHudStore((s) => s.fuelLevel);

  return (
    <div className="hud-widget hud-text">
      <div>
        <span className="hud-value">{mpg}</span>
        <span className="hud-label">MPG</span>
      </div>
      {fuelLevel && fuelLevel !== '--' && fuelLevel !== '' && (
        <div style={{ marginTop: '0.5vh' }}>
          <span className="hud-value">{fuelLevel}%</span>
          <span className="hud-label">Fuel</span>
        </div>
      )}
      <div style={{ marginTop: '0.5vh' }}>
        <span className="hud-value">{range}</span>
        <span className="hud-label">Mi Range</span>
      </div>
    </div>
  );
}
