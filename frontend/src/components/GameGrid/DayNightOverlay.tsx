import type { TimeOfDay } from '../../types/world';

const overlayColors: Record<TimeOfDay, string> = {
  DAWN: 'rgba(255, 183, 77, 0.08)',
  DAY: 'transparent',
  DUSK: 'rgba(255, 111, 0, 0.12)',
  NIGHT: 'rgba(10, 15, 40, 0.45)',
};

interface Props {
  timeOfDay: TimeOfDay;
}

export function DayNightOverlay({ timeOfDay }: Props) {
  return (
    <div
      style={{
        position: 'absolute',
        inset: 0,
        pointerEvents: 'none',
        background: overlayColors[timeOfDay],
        transition: 'background 2s ease',
        borderRadius: 4,
        zIndex: 30,
      }}
    />
  );
}
