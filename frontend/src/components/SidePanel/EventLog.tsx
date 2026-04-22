import { useEffect, useRef } from 'react';
import { useGameStore } from '../../store/gameStore';
import styles from './EventLog.module.css';

export function EventLog() {
  const worldEvents = useGameStore((s) => s.worldEvents);
  const agentTasks = useGameStore((s) => s.agentTasks);
  const observations = useGameStore((s) => s.observations);
  const scrollRef = useRef<HTMLDivElement>(null);

  // Merge events, tasks, and observations, sorted by tick
  const entries = [
    ...worldEvents.map((e) => ({
      tick: e.tick,
      type: e.eventType,
      text: e.description,
      key: `e-${e.tick}-${e.eventType}-${e.description.slice(0, 20)}`,
    })),
    ...agentTasks.map((t) => ({
      tick: t.tick,
      type: t.taskType,
      text: `${t.agentName}: ${t.taskType}${t.targetResourceType ? ' (' + t.targetResourceType + ')' : ''} — ${t.reasoning}`,
      key: `t-${t.tick}-${t.agentName}`,
    })),
    ...observations.map((o) => ({
      tick: o.tick,
      type: `OBS_${o.type}`,
      text: `${o.agentName}: ${o.description}`,
      key: `o-${o.tick}-${o.agentName}-${o.type}`,
    })),
  ].sort((a, b) => a.tick - b.tick);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [entries.length]);

  return (
    <div className={styles.eventPanel} ref={scrollRef}>
      <div className={styles.heading}>Event Log</div>
      {entries.length === 0 ? (
        <div className={styles.empty}>No events yet...</div>
      ) : (
        entries.slice(-100).map((entry) => (
          <div
            key={entry.key}
            className={`${styles.entry} ${styles[entry.type] || ''}`}
          >
            <span className={styles.tick}>[{entry.tick}]</span>
            {entry.text}
          </div>
        ))
      )}
    </div>
  );
}
