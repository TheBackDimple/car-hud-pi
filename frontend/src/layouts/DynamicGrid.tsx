import { useHudStore } from '../hooks/useHudStore';
import { WidgetRenderer } from '../components/WidgetRenderer';

/** 4×3 grid cell size. Padding so blocks sit centered (matches Android). */
const CELL_W = 1 / 4;
const CELL_H = 1 / 3;
const PAD_X = 0.05 * CELL_W;
const PAD_Y = 0.05 * CELL_H;
/** Map uses slightly more padding (8%) so it appears a bit smaller than blocks. */
const MAP_PAD_X = 0.08 * CELL_W;
const MAP_PAD_Y = 0.08 * CELL_H;

/** Map always occupies middle 6 cells (cols 1–2, rows 0–2). Normalize in case of stale data. */
function normalizeComponent(c: { type: string; x: number; y: number; width: number; height: number }) {
  if (c.type === 'map') {
    return { ...c, x: CELL_W, y: 0, width: 2 * CELL_W, height: 3 * CELL_H };
  }
  return c;
}

function visualStyle(c: { type: string; x: number; y: number; width: number; height: number }) {
  const [padX, padY] = c.type === 'map' ? [MAP_PAD_X, MAP_PAD_Y] : [PAD_X, PAD_Y];
  return {
    left: `${(c.x + padX) * 100}%`,
    top: `${(c.y + padY) * 100}%`,
    width: `${(c.width - 2 * padX) * 100}%`,
    height: `${(c.height - 2 * padY) * 100}%`,
  };
}

export function DynamicGrid() {
  const preset = useHudStore((s) => s.activePreset);

  if (!preset) {
    return <div className="awaiting-config hud-text">Awaiting config...</div>;
  }

  return (
    <div className="hud-canvas">
      {preset.components
        .filter((c) => c.enabled)
        .map((c) => normalizeComponent(c))
        .map((c) => (
          <div
            key={c.type}
            className="hud-widget"
            style={{
              position: 'absolute',
              ...visualStyle(c),
            }}
          >
            <WidgetRenderer type={c.type} />
          </div>
        ))}
    </div>
  );
}
