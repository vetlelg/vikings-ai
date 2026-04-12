import { memo } from 'react';
import type { EntitySnapshot } from '../../types/world';
import styles from './DragonToken.module.css';

const CELL = 39;

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
      title="Dragon"
    >
      <span className={styles.icon}>🐉</span>
    </div>
  );
});
