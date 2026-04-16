import { useEffect, useRef } from 'react';
import { useHudStore } from './useHudStore';
import type { LayoutPreset } from '../types/layout';

function resolveWsUrl(): string {
  const override = import.meta.env.VITE_WS_URL as string | undefined;
  if (override?.trim()) {
    return override.trim();
  }
  return import.meta.env.DEV
    ? 'ws://localhost:8000/ws?role=hud'
    : window.location.protocol === 'https:'
      ? `wss://${window.location.host}/ws?role=hud`
      : `ws://${window.location.host}/ws?role=hud`;
}

const WS_URL = resolveWsUrl();

const MAX_BACKOFF_MS = 10000;

function applyHudMirror(mirrored: boolean) {
  document.body.classList.toggle('hud-mirror-on', mirrored);
}
const INITIAL_BACKOFF_MS = 1000;

function layoutRenderMode(payload: { renderMode?: unknown }): 'legacy' | 'refined' {
  return typeof payload.renderMode === 'string' &&
    payload.renderMode.toLowerCase() === 'legacy'
    ? 'legacy'
    : 'refined';
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
        ws.send(
          JSON.stringify({
            type: 'request_state',
            payload: {},
            timestamp: null,
          })
        );
      };

      ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);
          const { type, payload } = msg;

          switch (type) {
            case 'hud_data':
              useHudStore.setState({
                speed: payload.speed ?? '--',
                gpsSpeed: payload.gpsSpeed ?? '--',
                rpm: payload.rpm ?? '--',
                coolantTemp: payload.coolantTemp ?? '--',
                mpg: payload.mpg ?? '--',
                range: payload.range ?? '--',
                fuelLevel: payload.fuelLevel ?? '--',
                turn: payload.turn ?? '--',
                distance: payload.distance ?? '--',
                maneuver: payload.maneuver ?? '',
                eta: payload.eta ?? '--',
                speedLimit: payload.speedLimit ?? '',
              });
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
              break;
            case 'layout_config': {
              const preset = payload as LayoutPreset;
              useHudStore.setState({
                renderMode: layoutRenderMode(preset),
                activePreset: preset,
              });
              break;
            }
            case 'full_state':
              if (payload.hud_data) {
                useHudStore.setState({
                  speed: payload.hud_data.speed ?? '--',
                  gpsSpeed: payload.hud_data.gpsSpeed ?? '--',
                  rpm: payload.hud_data.rpm ?? '--',
                  coolantTemp: payload.hud_data.coolantTemp ?? '--',
                  mpg: payload.hud_data.mpg ?? '--',
                  range: payload.hud_data.range ?? '--',
                  fuelLevel: payload.hud_data.fuelLevel ?? '--',
                  turn: payload.hud_data.turn ?? '--',
                  distance: payload.hud_data.distance ?? '--',
                  maneuver: payload.hud_data.maneuver ?? '',
                  eta: payload.hud_data.eta ?? '--',
                  speedLimit: payload.hud_data.speedLimit ?? '',
                });
              }
              if (payload.map_frame !== undefined) {
                useHudStore.setState({
                  mapFrame: payload.map_frame?.image ?? null,
                });
              }
              if (payload.android_connected !== undefined) {
                useHudStore.setState({
                  phoneConnected: payload.android_connected,
                });
              }
              if (payload.layout_config) {
                const preset = payload.layout_config as LayoutPreset;
                useHudStore.setState({
                  renderMode: layoutRenderMode(preset),
                  activePreset: preset,
                });
              }
              if (typeof payload.hud_mirror === 'boolean') {
                applyHudMirror(payload.hud_mirror);
              }
              break;
            case 'theme_config':
              if (payload.color) {
                document.documentElement.style.setProperty(
                  '--hud-color',
                  payload.color
                );
                document.documentElement.style.setProperty(
                  '--hud-color-dim',
                  payload.color + 'aa'
                );
                document.documentElement.style.setProperty(
                  '--hud-glow',
                  payload.color
                );
              }
              break;
            case 'hud_mirror':
              if (typeof payload.mirror === 'boolean') {
                applyHudMirror(payload.mirror);
              }
              break;
            case 'connection_status':
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
