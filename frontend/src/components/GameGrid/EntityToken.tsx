import { memo } from 'react';
import type { EntitySnapshot } from '../../types/world';
import styles from './EntityToken.module.css';

const CELL = 39;

function TreeIcon({ depleted }: { depleted: number }) {
  // depleted: 0 = full, 1 = empty
  const trunkH = 6 + (1 - depleted) * 2;
  const canopyR = 5 + (1 - depleted) * 3;
  const opacity = 0.5 + (1 - depleted) * 0.5;
  return (
    <svg viewBox="0 0 28 28" style={{ width: 26, height: 26, opacity }}>
      {/* Trunk */}
      <rect x="12" y={18 - trunkH} width="4" height={trunkH + 2} rx="1" fill="#6b4226" />
      {/* Canopy layers */}
      <circle cx="14" cy={12 - depleted * 2} r={canopyR} fill="#2d5a1e" />
      <circle cx="11" cy={14 - depleted * 2} r={canopyR * 0.7} fill="#1e4a14" />
      <circle cx="17" cy={14 - depleted * 2} r={canopyR * 0.65} fill="#3a6b2c" />
    </svg>
  );
}

function MineIcon({ depleted }: { depleted: number }) {
  const opacity = 0.5 + (1 - depleted) * 0.5;
  const scale = 0.7 + (1 - depleted) * 0.3;
  return (
    <svg viewBox="0 0 28 28" style={{ width: 26, height: 26, opacity }}>
      <g transform={`translate(14,14) scale(${scale}) translate(-14,-14)`}>
        {/* Rock pile */}
        <ellipse cx="14" cy="20" rx="10" ry="5" fill="#5a5a62" />
        <ellipse cx="10" cy="16" rx="6" ry="4" fill="#6a6a72" />
        <ellipse cx="18" cy="15" rx="5" ry="4" fill="#7a7a82" />
        <circle cx="14" cy="12" r="4" fill="#8a8a92" />
        {/* Sparkle for minerals */}
        <circle cx="11" cy="14" r="1" fill="#c0c8d0" />
        <circle cx="17" cy="13" r="0.8" fill="#d0d8e0" />
      </g>
    </svg>
  );
}

function FishingSpotIcon({ depleted }: { depleted: number }) {
  const opacity = 0.5 + (1 - depleted) * 0.5;
  return (
    <svg viewBox="0 0 28 28" style={{ width: 26, height: 26, opacity }}>
      {/* Water ripples */}
      <ellipse cx="14" cy="16" rx="10" ry="3" fill="none" stroke="#4a8abc" strokeWidth="1" opacity="0.6" />
      <ellipse cx="14" cy="16" rx="6" ry="2" fill="none" stroke="#5a9acc" strokeWidth="1" opacity="0.8" />
      {/* Fish */}
      <ellipse cx="14" cy="14" rx="5" ry="2.5" fill="#3498db" />
      <path d="M19 14L23 11V17L19 14Z" fill="#3498db" />
      <circle cx="11" cy="13.5" r="0.8" fill="#1a1a2a" />
    </svg>
  );
}

function HuntingGroundIcon({ depleted }: { depleted: number }) {
  const opacity = 0.5 + (1 - depleted) * 0.5;
  return (
    <svg viewBox="0 0 28 28" style={{ width: 26, height: 26, opacity }}>
      {/* Fur/pelt shape */}
      <ellipse cx="14" cy="16" rx="9" ry="6" fill="#8b5e3c" />
      <ellipse cx="14" cy="15" rx="6" ry="4" fill="#a0724a" />
      {/* Paw prints */}
      <circle cx="9" cy="12" r="1.2" fill="#6b4226" />
      <circle cx="8" cy="10" r="0.7" fill="#6b4226" />
      <circle cx="10.5" cy="10.5" r="0.7" fill="#6b4226" />
    </svg>
  );
}

const subtypeIcons: Record<string, (depleted: number) => JSX.Element> = {
  tree: (d) => <TreeIcon depleted={d} />,
  mine: (d) => <MineIcon depleted={d} />,
  fishing_spot: (d) => <FishingSpotIcon depleted={d} />,
  hunting_ground: (d) => <HuntingGroundIcon depleted={d} />,
};

interface Props {
  entity: EntitySnapshot;
}

export const EntityToken = memo(function EntityToken({ entity }: Props) {
  const x = entity.position.x * CELL;
  const y = entity.position.y * CELL;

  const remaining = entity.remaining ?? 0;
  const capacity = entity.capacity ?? 1;
  const depleted = capacity > 0 ? 1 - remaining / capacity : 1;
  const pct = capacity > 0 ? (remaining / capacity) * 100 : 0;

  const renderIcon = entity.subtype ? subtypeIcons[entity.subtype] : null;
  const barColor = pct > 50 ? '#2ecc71' : pct > 25 ? '#e8a627' : '#c0392b';

  return (
    <div
      className={styles.token}
      style={{ transform: `translate(${x}px, ${y}px)` }}
    >
      {renderIcon ? renderIcon(depleted) : <span style={{ fontSize: 14 }}>{'◆'}</span>}
      {capacity > 0 && (
        <div className={styles.capacityBar}>
          <div
            className={styles.capacityFill}
            style={{ width: `${pct}%`, backgroundColor: barColor }}
          />
        </div>
      )}
    </div>
  );
}, (prev, next) =>
  prev.entity.position.x === next.entity.position.x &&
  prev.entity.position.y === next.entity.position.y &&
  prev.entity.remaining === next.entity.remaining &&
  prev.entity.id === next.entity.id
);
