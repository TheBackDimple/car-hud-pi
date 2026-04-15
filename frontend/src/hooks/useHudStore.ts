import { create } from 'zustand';
import type { LayoutPreset } from '../types/layout';

/** Default layout when no preset received — all components visible for testing. */
const DEFAULT_PRESET: LayoutPreset = {
  presetId: 0,
  name: 'Default',
  components: [
    /* Taller speed slot for digital gauge + ticks; stay left of map (x+w ≤ ~0.25) */
    { type: 'speed', enabled: true, x: 0.02, y: 0.05, width: 0.22, height: 0.34 },
    { type: 'map', enabled: true, x: 0.28, y: 0.05, width: 0.44, height: 0.6 },
    { type: 'nav', enabled: true, x: 0.75, y: 0.05, width: 0.22, height: 0.25 },
    { type: 'obd', enabled: true, x: 0.02, y: 0.41, width: 0.22, height: 0.26 },
    { type: 'fuel', enabled: true, x: 0.75, y: 0.65, width: 0.22, height: 0.28 },
    { type: 'gpsSpeed', enabled: false, x: 0.75, y: 0.35, width: 0.22, height: 0.2 },
  ],
};

export interface HudState {
  // Vehicle data
  speed: string;
  gpsSpeed: string;
  mpg: string;
  range: string;
  rpm: string;
  coolantTemp: string;
  fuelLevel: string;
  // Navigation
  turn: string;
  distance: string;
  maneuver: string;
  eta: string;
  speedLimit: string;
  // Map
  mapFrame: string | null;
  // Layout
  activePreset: LayoutPreset | null;
  // Connection
  isConnected: boolean;
  /** Android app WebSocket to Pi (GPS/map stream). */
  phoneConnected: boolean;
  /** Short-lived center overlay (e.g. Trip Ended). */
  hudNotice: string | null;
  /** Visual composition mode. Legacy keeps old look, refined applies Stitch styling. */
  renderMode: 'legacy' | 'refined';
}

/** Mock data for testing without backend (dev only). */
const MOCK_DATA = import.meta.env.DEV
  ? {
      speed: '65',
      gpsSpeed: '64',
      mpg: '28.5',
      range: '320',
      rpm: '2400',
      coolantTemp: '195',
      fuelLevel: '72',
      turn: 'Turn Right on Main St',
      distance: '0.5 mi',
      maneuver: '',
      eta: 'ETA 3:45 PM',
      speedLimit: '45',
    }
  : null;

export const useHudStore = create<HudState>(() => ({
  speed: MOCK_DATA?.speed ?? '--',
  gpsSpeed: MOCK_DATA?.gpsSpeed ?? '--',
  mpg: MOCK_DATA?.mpg ?? '--',
  range: MOCK_DATA?.range ?? '--',
  rpm: MOCK_DATA?.rpm ?? '--',
  coolantTemp: MOCK_DATA?.coolantTemp ?? '--',
  fuelLevel: MOCK_DATA?.fuelLevel ?? '--',
  turn: MOCK_DATA?.turn ?? '--',
  distance: MOCK_DATA?.distance ?? '--',
  maneuver: MOCK_DATA?.maneuver ?? '',
  eta: MOCK_DATA?.eta ?? '--',
  speedLimit: MOCK_DATA?.speedLimit ?? '',
  mapFrame: null,
  activePreset: DEFAULT_PRESET,
  isConnected: false,
  phoneConnected: false,
  hudNotice: null,
  renderMode: 'refined',
}));
