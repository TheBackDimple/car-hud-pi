import { useNumericTelemetry } from '../hooks/useTelemetry';

export function OBDPanel() {
  const { rpm: rpmNum } = useNumericTelemetry();
  const maxRpm = 8000;
  const normalizedRpm = Math.max(0, Math.min(maxRpm, rpmNum ?? 0)) / maxRpm;
  const rpmCompact = rpmNum != null ? `${(rpmNum / 1000).toFixed(1)}k` : '--';
  const barCount = 10;
  const activeBars = Math.round(normalizedRpm * barCount);
  const minHeight = 26;
  const maxHeight = 100;
  /** Steeper than linear so short bars read clearly vs tall ones. */
  const heightCurve = 2;

  return (
    <div className="hud-widget hud-text obd-panel">
      <div className="obd-row">
        <span className="hud-label">RPM</span>
        <span className="obd-rpm-value">{rpmCompact}</span>
      </div>
      <div
        className="obd-rpm-bars"
        role="meter"
        aria-label="RPM, thousands of RPM from 0 to 10 by twos"
        aria-valuemin={0}
        aria-valuemax={maxRpm}
        aria-valuenow={rpmNum ?? 0}
      >
        {Array.from({ length: barCount }).map((_, index) => {
          const progress = index / (barCount - 1);
          const height =
            minHeight +
            (maxHeight - minHeight) * Math.pow(progress, heightCurve);
          const isActive = index < activeBars;
          const barNo = index + 1;
          const scaleMark = barNo % 2 === 0 ? barNo : null;
          const isHot = scaleMark != null && scaleMark >= 8;
          return (
            <div key={index} className="obd-rpm-col">
              <div className="obd-rpm-bar-cell">
                <span
                  className={`obd-rpm-bar-step${isActive ? ' obd-rpm-bar-step--active' : ''}`}
                  style={{ height: `${height.toFixed(1)}%` }}
                />
              </div>
              <span
                className={`obd-rpm-scale-num${isHot ? ' obd-rpm-scale-hot' : ''}`}
                aria-hidden
              >
                {scaleMark ?? ''}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
