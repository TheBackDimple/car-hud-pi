import { useLayoutEffect, useRef, useState } from 'react';
import { useHudStore } from '../hooks/useHudStore';
import { useNumericTelemetry } from '../hooks/useTelemetry';

const MPH_TICKS = [0, 20, 40, 60, 80, 100, 120, 140, 160] as const;
const MPH_MAX = 160;

/** ViewBox center and arc geometry (270° sweep, opening toward bottom). */
const VB = 100;
const CX = 50;
const CY = 50;
const R = 40;
const STROKE = 3.25;
/** Arc from 135° to 45° (large arc) — ring passes over the top. */
function mphArcPath(r: number): string {
  const rad = (deg: number) => (deg * Math.PI) / 180;
  const x1 = CX + r * Math.cos(rad(135));
  const y1 = CY + r * Math.sin(rad(135));
  const x2 = CX + r * Math.cos(rad(45));
  const y2 = CY + r * Math.sin(rad(45));
  return `M ${x1} ${y1} A ${r} ${r} 0 1 1 ${x2} ${y2}`;
}

const ARC_D = mphArcPath(R);

function mphToAngleDeg(mph: number): number {
  return 135 + 270 * (mph / MPH_MAX);
}

/** 5, 10, 15, … — excludes majors (multiples of 20); no labels. */
const MPH_MINOR_TICKS: number[] = Array.from(
  { length: 31 },
  (_, i) => 5 * (i + 1)
).filter((m) => m < MPH_MAX && m % 20 !== 0);

export function SpeedGauge() {
  const speed = useHudStore((s) => s.speed);
  const { speed: speedNum } = useNumericTelemetry();
  const arcMeasureRef = useRef<SVGPathElement>(null);
  const [arcLen, setArcLen] = useState(0);

  const fillPct =
    speedNum != null
      ? Math.max(0, Math.min(100, (speedNum / MPH_MAX) * 100))
      : 0;

  useLayoutEffect(() => {
    const el = arcMeasureRef.current;
    if (!el) return;
    setArcLen(el.getTotalLength());
  }, []);

  const filledLen = arcLen > 0 ? (fillPct / 100) * arcLen : 0;

  return (
    <div className="hud-widget speed-gauge hud-text">
      <div
        className="speed-gauge-digital"
        role="meter"
        aria-valuemin={0}
        aria-valuemax={MPH_MAX}
        aria-valuenow={speedNum ?? 0}
        aria-label="Speed in miles per hour"
      >
        <div className="speed-gauge-ring-wrap">
          <svg
            className="speed-gauge-svg"
            viewBox={`0 0 ${VB} ${VB}`}
            aria-hidden
          >
            <defs>
              <linearGradient
                id="speed-gauge-arc-gradient"
                x1="0%"
                y1="0%"
                x2="100%"
                y2="0%"
              >
                <stop
                  offset="0%"
                  stopColor="color-mix(in srgb, var(--hud-glow) 88%, transparent)"
                />
                <stop offset="100%" stopColor="var(--hud-color-dim)" />
              </linearGradient>
            </defs>
            <path
              ref={arcMeasureRef}
              className="speed-gauge-track"
              d={ARC_D}
              fill="none"
              strokeWidth={STROKE}
              strokeLinecap="round"
            />
            <path
              className="speed-gauge-arc"
              d={ARC_D}
              fill="none"
              stroke="url(#speed-gauge-arc-gradient)"
              strokeWidth={STROKE}
              strokeLinecap="round"
              strokeDasharray={
                arcLen > 0 ? `${filledLen} ${arcLen}` : '0'
              }
              style={{
                opacity: arcLen > 0 ? 1 : 0,
                transition: 'stroke-dasharray 220ms ease-out, opacity 80ms ease-out',
              }}
            />
            {MPH_MINOR_TICKS.map((mph) => {
              const deg = mphToAngleDeg(mph);
              const rad = (deg * Math.PI) / 180;
              const cos = Math.cos(rad);
              const sin = Math.sin(rad);
              const x1 = CX + (R - 4.2) * cos;
              const y1 = CY + (R - 4.2) * sin;
              const x2 = CX + (R + 0.55) * cos;
              const y2 = CY + (R + 0.55) * sin;
              return (
                <line
                  key={`minor-${mph}`}
                  className="speed-gauge-arc-tick speed-gauge-arc-tick--minor"
                  x1={x1}
                  y1={y1}
                  x2={x2}
                  y2={y2}
                />
              );
            })}
            {MPH_TICKS.map((mph) => {
              const deg = mphToAngleDeg(mph);
              const rad = (deg * Math.PI) / 180;
              const cos = Math.cos(rad);
              const sin = Math.sin(rad);
              const x1 = CX + (R - 7.25) * cos;
              const y1 = CY + (R - 7.25) * sin;
              const x2 = CX + (R + 2) * cos;
              const y2 = CY + (R + 2) * sin;
              const lx = CX + (R + 7.75) * cos;
              const ly = CY + (R + 7.75) * sin;
              return (
                <g key={mph} className="speed-gauge-tick-group">
                  <line
                    className="speed-gauge-arc-tick speed-gauge-arc-tick--major"
                    x1={x1}
                    y1={y1}
                    x2={x2}
                    y2={y2}
                  />
                  <text
                    className="speed-gauge-arc-tick-num"
                    x={lx}
                    y={ly}
                    textAnchor="middle"
                    dominantBaseline="middle"
                  >
                    {mph}
                  </text>
                </g>
              );
            })}
          </svg>
          <div className="speed-readout speed-readout--in-gauge">
            <span className="speed-value">{speed}</span>
            <span className="speed-label">MPH</span>
          </div>
        </div>
      </div>
    </div>
  );
}
