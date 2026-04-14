import { memo } from 'react';
import type { EntitySnapshot } from '../../types/world';
import styles from './WolfToken.module.css';

const CELL = 39;

interface Props {
  entity: EntitySnapshot;
}

function WolfIcon() {
  return (
    <svg viewBox="0 0 32 32" style={{ width: 26, height: 26 }}>
      {/* Body */}
      <ellipse cx="16" cy="20" rx="8" ry="5" fill="#6b6b6b" />
      {/* Head */}
      <circle cx="16" cy="13" r="5.5" fill="#7a7a7a" />
      {/* Ears */}
      <path d="M11 11 L10 5 L14 10 Z" fill="#8a8a8a" />
      <path d="M21 11 L22 5 L18 10 Z" fill="#8a8a8a" />
      <path d="M11.5 10 L11 6.5 L13.5 9.5 Z" fill="#4a3030" />
      <path d="M20.5 10 L21 6.5 L18.5 9.5 Z" fill="#4a3030" />
      {/* Snout */}
      <ellipse cx="16" cy="15.5" rx="3" ry="2" fill="#9a9a9a" />
      <ellipse cx="16" cy="14.8" rx="1.5" ry="0.8" fill="#2a2a2a" />
      {/* Eyes */}
      <circle cx="13.5" cy="12" r="1.2" fill="#f0c040" />
      <circle cx="18.5" cy="12" r="1.2" fill="#f0c040" />
      <circle cx="13.5" cy="12" r="0.5" fill="#1a1a1a" />
      <circle cx="18.5" cy="12" r="0.5" fill="#1a1a1a" />
      {/* Tail */}
      <path d="M24 19 Q28 14 26 10" stroke="#6b6b6b" strokeWidth="2" fill="none" strokeLinecap="round" />
    </svg>
  );
}

export const WolfToken = memo(function WolfToken({ entity }: Props) {
  const x = entity.position.x * CELL;
  const y = entity.position.y * CELL;

  return (
    <div
      className={styles.wolf}
      style={{ transform: `translate(${x}px, ${y}px)` }}
    >
      <WolfIcon />
    </div>
  );
});
