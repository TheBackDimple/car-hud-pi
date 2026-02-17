import { useHudStore } from '../hooks/useHudStore';
import { WidgetRenderer } from '../components/WidgetRenderer';

export function DynamicGrid() {
  const preset = useHudStore((s) => s.activePreset);

  if (!preset) {
    return <div className="awaiting-config hud-text">Awaiting config...</div>;
  }

  return (
    <div className="hud-canvas">
      {preset.components
        .filter((c) => c.enabled)
        .map((c) => (
          <div
            key={c.type}
            className="hud-widget"
            style={{
              position: 'absolute',
              left: `${c.x * 100}%`,
              top: `${c.y * 100}%`,
              width: `${c.width * 100}%`,
              height: `${c.height * 100}%`,
            }}
          >
            <WidgetRenderer type={c.type} />
          </div>
        ))}
    </div>
  );
}
