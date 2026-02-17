import { useHudStore } from '../hooks/useHudStore';

export function NavigationPanel() {
  const turn = useHudStore((s) => s.turn);
  const distance = useHudStore((s) => s.distance);

  return (
    <div className="hud-widget hud-text">
      <span className="hud-value">{turn}</span>
      <span className="hud-label">{distance}</span>
    </div>
  );
}
