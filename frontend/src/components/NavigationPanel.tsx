import { useHudStore } from '../hooks/useHudStore';

/** Map Google Directions maneuver ids to arrow symbols (when present on the step). */
function arrowFromManeuver(maneuver: string): string | null {
  const m = maneuver.trim();
  if (!m) return null;
  switch (m) {
    case 'turn-left':
      return '←';
    case 'turn-right':
      return '→';
    case 'turn-slight-left':
      return '↖';
    case 'turn-slight-right':
      return '↗';
    case 'turn-sharp-left':
      return '⬁';
    case 'turn-sharp-right':
      return '⬀';
    case 'uturn-left':
      return '↩';
    case 'uturn-right':
      return '↪';
    case 'merge':
      return '↗';
    case 'fork-left':
      return '↖';
    case 'fork-right':
      return '↗';
    case 'ramp-left':
      return '↙';
    case 'ramp-right':
      return '↘';
    case 'roundabout-left':
    case 'roundabout-right':
      return '↻';
    case 'straight':
      return '↑';
    case 'keep-left':
      return '↖';
    case 'keep-right':
      return '↗';
    default:
      return null;
  }
}

/** Map turn instruction keywords to arrow symbols (fallback when maneuver is absent). */
function arrowFromKeywords(turn: string): string {
  const t = turn.toLowerCase();
  if (t.includes('left') && t.includes('slight')) return '↖';
  if (t.includes('right') && t.includes('slight')) return '↗';
  if (t.includes('left') && t.includes('sharp')) return '⬁';
  if (t.includes('right') && t.includes('sharp')) return '⬀';
  if (t.includes('left')) return '←';
  if (t.includes('right')) return '→';
  if (t.includes('u-turn') || t.includes('uturn')) return '↩';
  if (t.includes('merge')) return '↗';
  if (t.includes('exit')) return '↘';
  if (t.includes('roundabout') || t.includes('rotary')) return '↻';
  if (t.includes('straight') || t.includes('continue') || t.includes('head')) return '↑';
  if (t === '--' || t === '') return '';
  return '↑'; // default
}

function getTurnArrow(turn: string, maneuver: string): string {
  const fromApi = arrowFromManeuver(maneuver);
  if (fromApi != null) return fromApi;
  if (maneuver.trim() !== '') return '';
  return arrowFromKeywords(turn);
}

export function NavigationPanel() {
  const turn = useHudStore((s) => s.turn);
  const distance = useHudStore((s) => s.distance);
  const eta = useHudStore((s) => s.eta);
  const maneuver = useHudStore((s) => s.maneuver);
  const arrow = getTurnArrow(turn, maneuver);

  // Clean up the instruction text — remove redundant parts for HUD display
  const cleanTurn = turn
    .replace(/^(Turn |Make a )/, '')
    .replace(/ onto /i, '\n')
    .replace(/ on /i, '\n');

  return (
    <div className="hud-widget nav-panel hud-text">
      <div className="nav-panel-layout">
        {arrow ? <span className="nav-arrow" aria-hidden>{arrow}</span> : null}
        <div className="nav-panel-copy">
          <span className="nav-instruction">{cleanTurn || '--'}</span>
          <span className="nav-distance">{distance || '--'}</span>
          {eta && eta !== '--' ? <span className="nav-eta">{eta}</span> : null}
        </div>
      </div>
    </div>
  );
}
