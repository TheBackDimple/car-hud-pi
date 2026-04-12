/** A single HUD widget in the layout. */
export interface HudComponent {
  type: 'speed' | 'map' | 'nav' | 'obd' | 'fuel' | 'gpsSpeed';
  enabled: boolean;
  x: number;
  y: number;
  width: number;
  height: number;
}

/** Layout preset from Android. */
export interface LayoutPreset {
  presetId: number;
  name: string;
  components: HudComponent[];
}
