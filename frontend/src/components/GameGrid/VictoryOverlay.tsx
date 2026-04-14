import { useGameStore } from '../../store/gameStore';
import styles from './VictoryOverlay.module.css';

export function VictoryOverlay() {
  const gameStatus = useGameStore((s) => s.gameStatus);
  const tick = useGameStore((s) => s.tick);

  if (gameStatus !== 'VICTORY') return null;

  return (
    <div className={styles.overlay}>
      <div className={styles.title}>The Longship is Built!</div>
      <div className={styles.subtitle}>The Vikings set sail for new lands</div>
      <div className={styles.tickCount}>Completed in {tick} ticks</div>
    </div>
  );
}
