import { useEffect, useMemo, useState } from 'react';
import { useHudStore } from './useHudStore';

function toNumber(value: string | null | undefined): number | null {
  if (!value) return null;
  const trimmed = value.trim();
  if (trimmed === '' || trimmed === '--') return null;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}

export interface NumericTelemetry {
  speed: number | null;
  gpsSpeed: number | null;
  rpm: number | null;
  coolantTemp: number | null;
  speedLimit: number | null;
}

export function useNumericTelemetry(): NumericTelemetry {
  const speedRaw = useHudStore((s) => s.speed);
  const gpsSpeedRaw = useHudStore((s) => s.gpsSpeed);
  const rpmRaw = useHudStore((s) => s.rpm);
  const coolantRaw = useHudStore((s) => s.coolantTemp);
  const speedLimitRaw = useHudStore((s) => s.speedLimit);

  return useMemo(
    () => ({
      speed: toNumber(speedRaw),
      gpsSpeed: toNumber(gpsSpeedRaw),
      rpm: toNumber(rpmRaw),
      coolantTemp: toNumber(coolantRaw),
      speedLimit: toNumber(speedLimitRaw),
    }),
    [speedRaw, gpsSpeedRaw, rpmRaw, coolantRaw, speedLimitRaw]
  );
}

export function useTelemetrySeries(
  value: number | null,
  options?: { maxSamples?: number }
): number[] {
  const maxSamples = options?.maxSamples ?? 28;
  const [series, setSeries] = useState<number[]>([]);

  useEffect(() => {
    if (value == null) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setSeries((prev) => {
      const next = [...prev, value];
      return next.length > maxSamples ? next.slice(next.length - maxSamples) : next;
    });
  }, [maxSamples, value]);

  return series;
}
