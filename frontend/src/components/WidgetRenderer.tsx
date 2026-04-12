import type { HudComponent } from '../types/layout';
import { FuelRange } from './FuelRange';
import { GPSSpeed } from './GPSSpeed';
import { MapTile } from './MapTile';
import { NavigationPanel } from './NavigationPanel';
import { OBDPanel } from './OBDPanel';
import { SpeedGauge } from './SpeedGauge';

const COMPONENT_MAP = {
  speed: SpeedGauge,
  map: MapTile,
  nav: NavigationPanel,
  obd: OBDPanel,
  fuel: FuelRange,
  gpsSpeed: GPSSpeed,
} as const;

interface WidgetRendererProps {
  type: HudComponent['type'];
}

export function WidgetRenderer({ type }: WidgetRendererProps) {
  const Component = COMPONENT_MAP[type];
  if (!Component) return null;
  return <Component />;
}
