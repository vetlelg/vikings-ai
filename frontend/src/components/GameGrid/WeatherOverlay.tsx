import { useMemo } from 'react';
import type { Weather } from '../../types/world';

interface Props {
  weather: Weather;
}

export function WeatherOverlay({ weather }: Props) {
  const flakes = useMemo(
    () =>
      Array.from({ length: 50 }, (_, i) => ({
        id: i,
        left: Math.random() * 100,
        delay: Math.random() * 6,
        duration: 4 + Math.random() * 4,
        size: 2 + Math.random() * 2,
      })),
    [],
  );

  if (weather === 'CLEAR') return null;

  const speedMultiplier = weather === 'STORM' ? 0.4 : 1;

  return (
    <div
      style={{
        position: 'absolute',
        inset: 0,
        pointerEvents: 'none',
        overflow: 'hidden',
        borderRadius: 4,
        zIndex: 35,
      }}
    >
      {flakes.map((f) => (
        <div
          key={f.id}
          style={{
            position: 'absolute',
            left: `${f.left}%`,
            top: -10,
            width: f.size,
            height: f.size,
            borderRadius: '50%',
            background: 'rgba(255, 255, 255, 0.8)',
            animation: `snowfall ${f.duration * speedMultiplier}s linear ${f.delay}s infinite`,
          }}
        />
      ))}
      {weather === 'STORM' && (
        <div
          style={{
            position: 'absolute',
            inset: 0,
            background: 'rgba(0, 0, 0, 0.15)',
          }}
        />
      )}
    </div>
  );
}
