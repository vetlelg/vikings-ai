import { useEffect, useRef } from 'react';
import { useGameStore } from '../../store/gameStore';
import styles from './DirectiveLog.module.css';

export function DirectiveLog() {
  const directives = useGameStore((s) => s.directives);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [directives.length]);

  return (
    <div className={styles.directivePanel} ref={scrollRef}>
      <div className={styles.heading}>
        <span className={styles.crownIcon}>&#9813;</span>
        Jarl&apos;s Directives
      </div>
      {directives.length === 0 ? (
        <div className={styles.empty}>Bjorn has not yet issued orders...</div>
      ) : (
        directives.slice(-5).map((d, i) => (
          <div key={`${d.tick}-${i}`} className={styles.entry}>
            <div className={styles.assessment}>
              <span className={styles.tick}>[{d.tick}]</span>
              {d.assessment}
            </div>
            {d.assignments.map((a) => (
              <div key={a.agentName} className={styles.assignment}>
                <span className={styles.assigneeName}>{a.agentName}</span>: {a.directive}
              </div>
            ))}
          </div>
        ))
      )}
    </div>
  );
}
