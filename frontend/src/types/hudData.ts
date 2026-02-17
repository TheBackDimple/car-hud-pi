/** HUD vehicle data from backend (OBD + GPS + nav). */
export interface HudData {
  speed: string;
  gpsSpeed: string;
  rpm: string;
  coolantTemp: string;
  mpg: string;
  range: string;
  fuelLevel: string;
  turn: string;
  distance: string;
  timestamp?: number;
}
