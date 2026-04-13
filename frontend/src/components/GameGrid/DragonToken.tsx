import { memo } from 'react';
import type { EntitySnapshot } from '../../types/world';
import styles from './DragonToken.module.css';

const CELL = 39;

function DragonIcon() {
  return (
    <svg viewBox="0 0 40 40" style={{ width: 44, height: 44 }}>
      {/* Wings */}
      <path d="M6 18 Q2 8 10 6 L14 14 Z" fill="#8b2500" opacity="0.8" />
      <path d="M34 18 Q38 8 30 6 L26 14 Z" fill="#8b2500" opacity="0.8" />
      {/* Body */}
      <ellipse cx="20" cy="24" rx="10" ry="7" fill="#b03000" />
      {/* Belly scales */}
      <ellipse cx="20" cy="26" rx="6" ry="4" fill="#d05020" />
      {/* Head */}
      <circle cx="20" cy="14" r="6.5" fill="#c03800" />
      {/* Horns */}
      <path d="M14 11 L11 4 L16 10" fill="#4a2000" />
      <path d="M26 11 L29 4 L24 10" fill="#4a2000" />
      {/* Snout */}
      <ellipse cx="20" cy="17" rx="3.5" ry="2.2" fill="#d04020" />
      {/* Nostrils — fire glow */}
      <circle cx="18.5" cy="17" r="0.8" fill="#ff6600" />
      <circle cx="21.5" cy="17" r="0.8" fill="#ff6600" />
      {/* Eyes */}
      <circle cx="17" cy="13" r="1.5" fill="#ff4400" />
      <circle cx="23" cy="13" r="1.5" fill="#ff4400" />
      <circle cx="17" cy="13" r="0.6" fill="#1a0000" />
      <circle cx="23" cy="13" r="0.6" fill="#1a0000" />
      {/* Teeth */}
      <path d="M17 19 L18 20.5 L19 19 M21 19 L22 20.5 L23 19" fill="#fff" />
      {/* Tail */}
      <path d="M30 24 Q36 28 38 22 Q39 18 36 16" stroke="#a02800" strokeWidth="2.5" fill="none" strokeLinecap="round" />
      <path d="M36 16 L38 14 L35 15 L37 12 L34 15" fill="#b03000" />
    </svg>
  );
}

interface Props {
  entity: EntitySnapshot;
}

export const DragonToken = memo(function DragonToken({ entity }: Props) {
  const x = entity.position.x * CELL;
  const y = entity.position.y * CELL;

  return (
    <div
      className={styles.dragon}
      style={{ transform: `translate(${x}px, ${y}px)` }}
    >
      <DragonIcon />
    </div>
  );
});
