import { useEffect, useRef, useState } from 'react';
import { DynamicGrid } from '../layouts/DynamicGrid';
import { useHudStore } from '../hooks/useHudStore';

const HUD_NOTICE_MS = 2000;
const DISCONNECT_FLASH_MS = 2500;

export function HudCanvas() {
  const isConnected = useHudStore((s) => s.isConnected);
  const phoneConnected = useHudStore((s) => s.phoneConnected);
  const hudNotice = useHudStore((s) => s.hudNotice);
  const [showDisconnectFlash, setShowDisconnectFlash] = useState(false);
  const prevPhone = useRef<boolean | null>(null);
  const noticeDismissFlashRef = useRef<ReturnType<typeof setTimeout> | null>(
    null
  );

  /**
   * Phone disconnected — show flash only when no HUD notice is showing (avoids stale flash after Trip Ended).
   */
  useEffect(() => {
    if (prevPhone.current === true && phoneConnected === false) {
      if (!hudNotice) {
        setShowDisconnectFlash(true);
        const t = window.setTimeout(
          () => setShowDisconnectFlash(false),
          DISCONNECT_FLASH_MS
        );
        prevPhone.current = phoneConnected;
        return () => window.clearTimeout(t);
      }
    }
    prevPhone.current = phoneConnected;
  }, [phoneConnected, hudNotice]);

  /** New notice (e.g. Trip Ended) clears any disconnect flash from a race. */
  useEffect(() => {
    if (hudNotice) setShowDisconnectFlash(false);
  }, [hudNotice]);

  useEffect(() => {
    if (!hudNotice) return;
    const t = window.setTimeout(() => {
      useHudStore.setState({ hudNotice: null });
      setShowDisconnectFlash(false);
      // Real disconnect while notice was showing: phone still false after notice ends.
      if (!useHudStore.getState().phoneConnected) {
        setShowDisconnectFlash(true);
        noticeDismissFlashRef.current = window.setTimeout(() => {
          setShowDisconnectFlash(false);
          noticeDismissFlashRef.current = null;
        }, DISCONNECT_FLASH_MS);
      }
    }, HUD_NOTICE_MS);
    return () => {
      window.clearTimeout(t);
      if (noticeDismissFlashRef.current !== null) {
        window.clearTimeout(noticeDismissFlashRef.current);
        noticeDismissFlashRef.current = null;
      }
    };
  }, [hudNotice]);

  return (
    <div className="hud-root">
      <div
        className={`connection-status ${!isConnected ? 'disconnected' : ''}`}
      >
        <span className="connection-line">
          HUD {isConnected ? '● Connected' : '○ Disconnected'}
        </span>
        <span className="connection-line">
          Phone {phoneConnected ? '● Connected' : '○ Disconnected'}
        </span>
      </div>
      {showDisconnectFlash && !hudNotice && (
        <div className="hud-disconnect-flash" role="status">
          Phone Disconnected From Pi
        </div>
      )}
      {hudNotice && (
        <div className="hud-disconnect-flash" role="status">
          {hudNotice}
        </div>
      )}
      <DynamicGrid />
    </div>
  );
}
