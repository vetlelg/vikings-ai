import type { Position } from '../../types/world';
import styles from './MovementTrail.module.css';

const CELL = 39;

interface Props {
  trail: Position[];
  color: string;
}

export function MovementTrail({ trail, color }: Props) {
  if (trail.length === 0) return null;

  return (
    <>
      {trail.map((pos, i) => {
        const opacity = ((i + 1) / (trail.length + 1)) * 0.5;
        const size = 4 + ((i + 1) / trail.length) * 3;

        return (
          <div
            key={`${pos.x}-${pos.y}-${i}`}
            className={styles.dot}
            style={{
              left: pos.x * CELL + CELL / 2,
              top: pos.y * CELL + CELL / 2,
              width: size,
              height: size,
              opacity,
              background: color,
            }}
          />
        );
      })}
    </>
  );
}
