import { useEffect, useRef, useState } from 'react';
import { useHudStore } from '../hooks/useHudStore';

const PHONE_CONNECTED_HINT_MS = 1800;

export function MapTile() {
  const mapFrame = useHudStore((s) => s.mapFrame);
  const phoneConnected = useHudStore((s) => s.phoneConnected);
  const [showPhoneConnectedHint, setShowPhoneConnectedHint] = useState(false);
  const prevPhoneConnected = useRef(false);

  /**
   * Show the hint only when the phone WebSocket newly connects (e.g. Connect on the main menu).
   * Do not show when the map clears after a trip while the phone stays connected.
   */
  useEffect(() => {
    if (phoneConnected && !prevPhoneConnected.current) {
      setShowPhoneConnectedHint(true);
      const t = window.setTimeout(
        () => setShowPhoneConnectedHint(false),
        PHONE_CONNECTED_HINT_MS
      );
      prevPhoneConnected.current = true;
      return () => window.clearTimeout(t);
    }
    prevPhoneConnected.current = phoneConnected;
  }, [phoneConnected]);

  if (!mapFrame) {
    if (!phoneConnected) {
      return (
        <div className="map-placeholder map-placeholder--silent" aria-hidden />
      );
    }
    if (showPhoneConnectedHint) {
      return (
        <div className="map-placeholder map-placeholder--transient hud-text-dim">
          Phone Connected
        </div>
      );
    }
    return <div className="map-placeholder map-placeholder--silent" aria-hidden />;
  }

  return (
    <img
      src={`data:image/jpeg;base64,${mapFrame}`}
      alt="Map"
      className="map-tile"
    />
  );
}
