import { useEffect, useRef } from 'react';
import { useHudStore } from './useHudStore';
import type { LayoutPreset } from '../types/layout';

const WS_URL = import.meta.env.DEV
  ? 'ws://localhost:8000/ws?role=hud'
  : `ws://${window.location.host}/ws?role=hud`;

const MAX_BACKOFF_MS = 10000;
const INITIAL_BACKOFF_MS = 1000;

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
              });
              break;
            case 'map_frame':
              useHudStore.setState({ mapFrame: payload.image ?? null });
              break;
            case 'layout_config':
              useHudStore.setState({
                activePreset: payload as LayoutPreset,
              });
              break;
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
                });
              }
              if (payload.map_frame?.image) {
                useHudStore.setState({ mapFrame: payload.map_frame.image });
              }
              if (payload.layout_config) {
                useHudStore.setState({ activePreset: payload.layout_config as LayoutPreset });
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
        useHudStore.setState({ isConnected: false });
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
