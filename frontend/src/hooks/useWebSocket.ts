import { useEffect, useRef } from 'react';
import { useHudStore } from './useHudStore';
import type { LayoutPreset } from '../types/layout';

const WS_URL = import.meta.env.DEV
  ? 'ws://localhost:8000/ws?role=hud'
  : window.location.protocol === 'https:'
    ? `wss://${window.location.host}/ws?role=hud`
    : `ws://${window.location.host}/ws?role=hud`;

const MAX_BACKOFF_MS = 10000;
const INITIAL_BACKOFF_MS = 1000;
const PLACEHOLDER_HUD_DATA = {
  speed: '74',
  gpsSpeed: '73',
  rpm: '4200',
  coolantTemp: '194',
  mpg: '28.9',
  range: '312',
  fuelLevel: '76',
  turn: 'Turn Right on Shinjuku Expy',
  distance: '0.8 mi',
  maneuver: 'turn-right',
  eta: 'ETA 3:45 PM',
  speedLimit: '45',
} as const;

function applyHudData(payload: Record<string, unknown>) {
  useHudStore.setState({
    speed: (payload.speed as string) ?? '--',
    gpsSpeed: (payload.gpsSpeed as string) ?? '--',
    rpm: (payload.rpm as string) ?? '--',
    coolantTemp: (payload.coolantTemp as string) ?? '--',
    mpg: (payload.mpg as string) ?? '--',
    range: (payload.range as string) ?? '--',
    fuelLevel: (payload.fuelLevel as string) ?? '--',
    turn: (payload.turn as string) ?? '--',
    distance: (payload.distance as string) ?? '--',
    maneuver: (payload.maneuver as string) ?? '',
    eta: (payload.eta as string) ?? '--',
    speedLimit: (payload.speedLimit as string) ?? '',
  });
}

export function useWebSocket() {
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimeoutRef = useRef<number | null>(null);
  const backoffRef = useRef(INITIAL_BACKOFF_MS);

  useEffect(() => {
    function connect() {
      const ws = new WebSocket(WS_URL);

      ws.onopen = () => {
        backoffRef.current = INITIAL_BACKOFF_MS;
        useHudStore.setState({ isConnected: true });
        // Request last known state on connect (handles reconnection)
        ws.send(JSON.stringify({ type: 'request_state', payload: {}, timestamp: null }));
      };

      ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);
          const { type, payload } = msg;

          switch (type) {
            case 'hud_data':
              applyHudData(payload as Record<string, unknown>);
              break;
            case 'map_frame':
              useHudStore.setState({ mapFrame: payload.image ?? null });
              break;
            case 'hud_notice': {
              const text =
                typeof payload.message === 'string' ? payload.message : null;
              if (text) {
                useHudStore.setState({ hudNotice: text });
              }
              break;
            }
            case 'android_status':
              useHudStore.setState({ phoneConnected: payload.connected ?? false });
              if (!(payload.connected ?? false)) {
                applyHudData(PLACEHOLDER_HUD_DATA as unknown as Record<string, unknown>);
              }
              break;
            case 'layout_config':
              useHudStore.setState({
                renderMode:
                  typeof payload?.renderMode === 'string' &&
                  payload.renderMode.toLowerCase() === 'legacy'
                    ? 'legacy'
                    : 'refined',
              });
              useHudStore.setState({
                activePreset: payload as LayoutPreset,
              });
              break;
            case 'full_state':
              if (payload.hud_data) {
                applyHudData(payload.hud_data as Record<string, unknown>);
              }
              if (payload.map_frame !== undefined) {
                useHudStore.setState({
                  mapFrame: payload.map_frame?.image ?? null,
                });
              }
              if (payload.android_connected !== undefined) {
                useHudStore.setState({ phoneConnected: payload.android_connected });
                if (!payload.android_connected) {
                  applyHudData(PLACEHOLDER_HUD_DATA as unknown as Record<string, unknown>);
                }
              }
              if (payload.layout_config) {
                useHudStore.setState({
                  renderMode:
                    typeof payload.layout_config?.renderMode === 'string' &&
                    payload.layout_config.renderMode.toLowerCase() === 'legacy'
                      ? 'legacy'
                      : 'refined',
                });
                useHudStore.setState({ activePreset: payload.layout_config as LayoutPreset });
              }
              break;
            case 'theme_config':
              if (payload.color) {
                document.documentElement.style.setProperty('--hud-color', payload.color);
                // Dimmer version for secondary text
                document.documentElement.style.setProperty('--hud-color-dim', payload.color + 'aa');
                // Glow color
                document.documentElement.style.setProperty('--hud-glow', payload.color);
              }
              break;
            case 'connection_status':
              // Heartbeat — no state change needed
              break;
            default:
              break;
          }
        } catch {
          // Ignore parse errors
        }
      };

      ws.onclose = () => {
        useHudStore.setState({
          isConnected: false,
          mapFrame: null,
          phoneConnected: false,
          hudNotice: null,
        });
        applyHudData(PLACEHOLDER_HUD_DATA as unknown as Record<string, unknown>);
        wsRef.current = null;

        const delay = Math.min(backoffRef.current, MAX_BACKOFF_MS);
        backoffRef.current *= 2;

        reconnectTimeoutRef.current = window.setTimeout(() => {
          connect();
        }, delay);
      };

      ws.onerror = () => {
        ws.close();
      };

      wsRef.current = ws;
    }

    connect();

    return () => {
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
    };
  }, []);
}
