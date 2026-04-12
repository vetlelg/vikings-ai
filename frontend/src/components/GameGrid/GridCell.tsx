import { memo } from 'react';
import type { TerrainType } from '../../types/world';
import styles from './GridCell.module.css';

interface Props {
  terrain: TerrainType;
}

export const GridCell = memo(function GridCell({ terrain }: Props) {
  return <div className={`${styles.cell} ${styles[terrain]}`} />;
});
