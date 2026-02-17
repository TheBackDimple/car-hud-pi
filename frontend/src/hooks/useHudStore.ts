import { create } from 'zustand';
import type { LayoutPreset } from '../types/layout';

/** Default layout when no preset received — all components visible for testing. */
const DEFAULT_PRESET: LayoutPreset = {
  presetId: 0,
  name: 'Default',
  components: [
    { type: 'speed', enabled: true, x: 0.02, y: 0.05, width: 0.22, height: 0.25 },
    { type: 'map', enabled: true, x: 0.28, y: 0.05, width: 0.44, height: 0.6 },
    { type: 'nav', enabled: true, x: 0.75, y: 0.05, width: 0.22, height: 0.25 },
    { type: 'obd', enabled: true, x: 0.02, y: 0.35, width: 0.22, height: 0.25 },
    { type: 'fuel', enabled: true, x: 0.02, y: 0.65, width: 0.22, height: 0.25 },
    { type: 'gpsSpeed', enabled: true, x: 0.75, y: 0.35, width: 0.22, height: 0.2 },
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
  // Map
  mapFrame: string | null;
  // Layout
  activePreset: LayoutPreset | null;
  // Connection
  isConnected: boolean;
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
  mapFrame: null,
  activePreset: DEFAULT_PRESET,
  isConnected: false,
}));
