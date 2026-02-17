import { useHudStore } from '../hooks/useHudStore';

export function MapTile() {
  const mapFrame = useHudStore((s) => s.mapFrame);

  if (!mapFrame) {
    return <div className="map-placeholder hud-text-dim">No Map</div>;
  }

  return (
    <img
      src={`data:image/jpeg;base64,${mapFrame}`}
      alt="Map"
      className="map-tile"
    />
  );
}
