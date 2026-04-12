import { useEffect, useRef } from 'react';
import { useGameStore } from '../../store/gameStore';
import styles from './SagaLog.module.css';

export function SagaLog() {
  const sagaEntries = useGameStore((s) => s.sagaEntries);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [sagaEntries.length]);

  return (
    <div className={styles.sagaPanel} ref={scrollRef}>
      <div className={styles.heading}>The Saga</div>
      {sagaEntries.length === 0 ? (
        <div className={styles.empty}>The Skald awaits events to narrate...</div>
      ) : (
        sagaEntries.map((entry, i) => (
          <div key={i} className={styles.entry}>
            <span className={styles.tick}>[{entry.tick}]</span>
            {entry.text}
          </div>
        ))
      )}
    </div>
  );
}
