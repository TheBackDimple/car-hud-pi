import { useHudStore } from '../hooks/useHudStore';

export function FuelRange() {
  const mpg = useHudStore((s) => s.mpg);
  const range = useHudStore((s) => s.range);
  const fuelLevel = useHudStore((s) => s.fuelLevel);
  const parsedFuel = fuelLevel && fuelLevel !== '--' ? Number(fuelLevel) : null;
  const fuelPct =
    parsedFuel != null && Number.isFinite(parsedFuel)
      ? Math.max(0, Math.min(100, parsedFuel))
      : null;
  const efficiencyFill = fuelPct != null ? `${fuelPct}%` : '0%';
  const rangeNum = Number(range);
  const filledRangeSegments = Number.isFinite(rangeNum)
    ? Math.max(0, Math.min(4, Math.round((rangeNum / 400) * 4)))
    : 0;

  return (
    <div className="hud-widget hud-text fuel-card">
      <div className="fuel-block">
        <div className="fuel-row">
          <span className="hud-label">Efficiency</span>
          <span className="hud-label">%</span>
        </div>
        <div className="fuel-row fuel-row--value">
          <span className="hud-value">{mpg}</span>
          <span className="hud-label">MPG</span>
        </div>
        <div
          className="fuel-efficiency-bar"
          role="meter"
          aria-label="Efficiency"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={fuelPct ?? 0}
        >
          <div className="fuel-efficiency-fill" style={{ width: efficiencyFill }} />
        </div>
      </div>

      <div className="fuel-block">
        <div className="fuel-row">
          <span className="hud-label">Est Range</span>
        </div>
        <div className="fuel-row fuel-row--value">
          <span className="hud-value">{range}</span>
          <span className="hud-label">Miles</span>
        </div>
        <div className="fuel-range-segments" aria-hidden>
          {[0, 1, 2, 3].map((segment) => (
            <div
              key={segment}
              className={`fuel-range-segment ${
                segment < filledRangeSegments ? 'fuel-range-segment--filled' : ''
              }`}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
